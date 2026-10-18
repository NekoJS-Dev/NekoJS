"""Verify the frozen resource assertions and independently recompute memory observations."""
import hashlib
import io
import json
import re
import zipfile
from pathlib import Path

root=Path(__file__).resolve().parent
manifest=json.loads((root/'manifest.json').read_text())
assert manifest['observationsOnly'] and manifest['wholeProcessCounters'] and not manifest['perContextAllocationClaimed']
packed=root/'evidence.zip'
assert hashlib.sha256(packed.read_bytes()).hexdigest()==manifest['archiveSha256']
with zipfile.ZipFile(packed) as archive:
    assert len(archive.namelist())==len(set(archive.namelist()))
    assert set(archive.namelist())==set(manifest['entries'])
    for name,entry in manifest['entries'].items():
        raw=archive.read(name)
        assert len(raw)==entry['bytes'] and hashlib.sha256(raw).hexdigest()==entry['sha256'],name
    load=lambda name:json.loads(archive.read(name).decode('utf-8-sig'))
    prefix='observation/'
    binding=load(prefix+'source-binding.json')
    original=archive.read('inputs/PackResourceStabilityTest.java')
    shadow=archive.read(prefix+'PackNativeObservationTest.java')
    assert hashlib.sha256(original).hexdigest()==binding['originalSha256']
    assert hashlib.sha256(shadow).hexdigest()==binding['shadowSha256']
    text=original.decode('utf-8').replace('\r\n','\n').replace('class PackResourceStabilityTest {','class PackNativeObservationTest {')
    for before,after in [
        ('            long legacyHeap = observedHeap();','            long legacyHeap = observedHeap();\n            NativePackMemory.snapshot("legacy", registeredContexts());'),
        ('            long activeHeap = observedHeap();','            long activeHeap = observedHeap();\n            NativePackMemory.snapshot("active", registeredContexts());'),
        ('                    long heap = observedHeap();','                    long heap = observedHeap();\n                    NativePackMemory.snapshot("reload-" + reload, registeredContexts());'),
        ('            assertEquals(baselineContexts, registeredContexts());','            assertEquals(baselineContexts, registeredContexts());\n            NativePackMemory.snapshot("root-closed", registeredContexts());'),
        ('                if (measureHeap) candidateHeap = observedHeap();','                if (measureHeap) {\n                    candidateHeap = observedHeap();\n                    NativePackMemory.snapshot("candidate", peakRegistered);\n                }')]:
        assert before in text
        text=text.replace(before,after)
    assert text==shadow.decode('utf-8').replace('\r\n','\n'),'Only the declared observation calls and class name may differ'
    assert load(prefix+'execution.json')==[dict(stage='compile',exitCode=0),dict(stage='run',exitCode=0)]
    events=[json.loads(line) for line in archive.read(prefix+'test-events.jsonl').decode().splitlines()]
    assert len(events)==10
    for start,end in zip(events[::2],events[1::2],strict=True):
        assert start['id']==end['id'] and start['outcome']=='START' and end['outcome']=='SUCCESSFUL' and not end['detail']
    jars=zipfile.ZipFile(io.BytesIO(archive.read('binaries/common-main.jar')))
    classes=load(prefix+'production-class-bindings.json')
    assert len(classes)==1152 and set(jars.namelist())==set(classes)
    for name,sha in classes.items():assert hashlib.sha256(jars.read(name)).hexdigest()==sha
    assert load('post-run-bindings.json')==dict(originalTestUnchanged=True,productionClassesMatchBeforeRun=True,classCount=1152)
    rows=[json.loads(line) for line in archive.read(prefix+'memory.jsonl').decode().splitlines()]
    assert len(rows)==29 and len({r['nmtSnapshot'] for r in rows})==29
    summary=load(prefix+'summary.json')
    assert summary['testsPassed']==5 and summary['skipped']==0 and summary['snapshots']==29
    for row in rows:
        text=archive.read(prefix+row['nmtSnapshot']).decode()
        total=re.search(r'Total: reserved=(\d+)KB, committed=(\d+)KB',text)
        assert total
        categories={name.strip():dict(reservedKiB=int(reserved),committedKiB=int(committed)) for name,reserved,committed in re.findall(r'^\s*-\s+(.*?)\s+\(reserved=(\d+)KB, committed=(\d+)KB\)',text,re.M)}
        assert 'Java Heap' in categories and 'Code' in categories
        row['nmt']=dict(reservedKiB=int(total[1]),committedKiB=int(total[2]),categories=categories,vmNonHeapCommittedKiB=int(total[2])-categories['Java Heap']['committedKiB'])
        assert all(row[field]>0 for field in ['privateCommitBytes','workingSetBytes','javaHeapUsedBytes','liveJavaThreads'])
        assert row['peakWorkingSetBytes']>=row['workingSetBytes']
    pack_results=[]
    for case,phases in summary['cases'].items():
        measured={r['stage']:r for r in rows if r['case']==case}
        assert all(measured[stage]==row for stage,row in phases.items())
        packs=int(case.rsplit(' ',1)[1])
        assert packs in [1,4,8]
        assert [measured[p]['contexts'] for p in ['legacy','active','candidate','root-closed']]==[1,packs+1,2*(packs+1),0]
        pack_results.append(dict(packs=packs,privateCommitDeltaBytes=measured['active']['privateCommitBytes']-measured['legacy']['privateCommitBytes'],vmNonHeapCommitDeltaKiB=measured['active']['nmt']['vmNonHeapCommittedKiB']-measured['legacy']['nmt']['vmNonHeapCommittedKiB']))
    windows=[r for r in rows if r['stage'].startswith('reload-')]
    assert [r['stage'] for r in windows]==['reload-10','reload-20','reload-30','reload-40','reload-50']
    assert all(r['contexts']==5 and r['liveJavaThreads']==13 for r in windows)
    assert windows==summary['reloadWindows']
    first,last=windows[0],windows[-1]
    delta=dict(privateCommitDeltaBytes=last['privateCommitBytes']-first['privateCommitBytes'],workingSetDeltaBytes=last['workingSetBytes']-first['workingSetBytes'],vmNonHeapCommitDeltaKiB=last['nmt']['vmNonHeapCommittedKiB']-first['nmt']['vmNonHeapCommittedKiB'],vmCodeCommitDeltaKiB=last['nmt']['categories']['Code']['committedKiB']-first['nmt']['categories']['Code']['committedKiB'])
    assert delta==summary['reload10To50']
    log=archive.read(prefix+'run.log').decode()
    for count in [50,100,150,200,250]:assert f'retiredContexts={count} observedCollected={count}' in log
print(json.dumps(dict(integrity='PASS',resourceTestsPassed=5,snapshots=29,packObservations=pack_results,reload10To50=delta,fullGameAcceptance='NOT_RUN')))
