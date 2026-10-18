"""Recompute both location cohorts against the same immutable artifact."""
import hashlib
import io
import json
import statistics
import zipfile
from pathlib import Path

root=Path(__file__).resolve().parent
manifest=json.loads((root/'manifest.json').read_text())
packed=root/'evidence.zip'
assert packed.stat().st_size==manifest['archiveBytes']
assert hashlib.sha256(packed.read_bytes()).hexdigest()==manifest['archiveSha256']
assert not manifest['samplerChanged'] and not manifest['externalQosController']
reference=root/manifest['referenceArchive']
assert hashlib.sha256(reference.read_bytes()).hexdigest()==manifest['referenceArchiveSha256']
def decode(data):
    if data.startswith((b'\xff\xfe',b'\xfe\xff')):return data.decode('utf-16')
    try:return data.decode('utf-8-sig')
    except UnicodeDecodeError:return data.decode('gb18030')
with zipfile.ZipFile(packed) as archive,zipfile.ZipFile(reference) as previous:
    assert set(archive.namelist())==set(manifest['entries'])
    for name,expected in manifest['entries'].items():
        assert hashlib.sha256(archive.read(name)).hexdigest()==expected,name
        assert not name.endswith(('server.properties','.pk8','.jfr','.dat','.mca'))
    load=lambda name:json.loads(decode(archive.read(name)))
    prep=load('formal/metadata/preparation.json')
    cprep=load('c-reference/preparation.json')
    post=load('post-run-bindings.json')
    location=load('tools/location-control-preparation.json')
    assert prep['revision']==cprep['revision']==location['revision']==manifest['sourceRevision']
    assert prep['classBindings']==cprep['classBindings']==post['classBindings']
    assert len(post['classBindings'])==location['classCount']==1625
    assert post['trackedSourcesClean'] and post['normalRunConfiguration'] and location['compiledClassesIdentical']
    assert len(location['copiedRunFileHashes'])==42
    assert prep['fixtureHashes']==cprep['fixtureHashes']==post['fixtureHashes']==location['fixtureHashes']
    assert prep['samplerSha256']==cprep['samplerSha256']==location['samplerSha256']=='1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4'
    artifact=previous.read('binaries/neoforge-26.1.2.jar')
    assert hashlib.sha256(artifact).hexdigest()==prep['artifactSha256']==cprep['artifactSha256']
    with zipfile.ZipFile(io.BytesIO(artifact)) as jar:
        for name,expected in prep['classBindings'].items():assert hashlib.sha256(jar.read(name)).hexdigest()==expected,name
    redactions=load('redactions.json')
    for name,expected in {**prep['fixtureHashes'],'bench/perf/sample.ps1':prep['samplerSha256']}.items():
        key='harness/'+name
        actual=redactions[key]['originalSha256'] if key in redactions else hashlib.sha256(archive.read(key)).hexdigest()
        assert actual==expected,name
    original=decode(archive.read('tools/formal-performance.py')).replace('\r\n','\n')
    located=decode(archive.read('tools/formal-location-performance.py')).replace('\r\n','\n')
    assert original.replace("worktree = Path('C:/Users/11515/.codex/worktrees/domain-perf-0965/NekoJS-mult')", "worktree = Path('D:/mcmodDemo/NekoJS-perf-606d1fff')")==located
    groups=load('formal/metadata/groups.json')
    judgment=load('formal/metadata/judgment.json')
    assert [g['group'] for g in groups]==['reload-1','reload-2','startup']
    results,sessions=[],0
    for group,recorded in zip(groups,judgment['groups'],strict=True):
        name=group['group'];startup=name=='startup'
        assert group['exitCode']==0 and len(group['outputDirectories'])==1
        rows=[json.loads(line) for line in decode(archive.read('formal/'+name+'/samples.jsonl')).splitlines() if line.strip()]
        assert len(rows)==(7 if startup else 5)
        assert [r['index'] for r in rows]==list(range(1,len(rows)+1))
        assert [r['kind'] for r in rows]==(['warmup']*2+['formal']*5 if startup else ['formal']*5)
        for row in rows:
            assert row['rev']==prep['revision'][:8] and row['node']=='26.1.2'
            assert row['killed'] is False and row['forced_kill'] is False and not row.get('timeout')
            assert row['stop_channel']=='rcon' and row['done_s'] is not None and row['wall_done_ms'] is not None
            if not startup:
                assert 'phase=COMMIT - no errors.' in row['rcon_response']
                assert 'PERF02-SERVER tick-bench load' in row['marker_line']
        for row in rows if startup else rows[:1]:
            stdout=decode(archive.read('formal/'+name+'/'+row['tag']+'-stdout.log'))
            assert 'Done (' in stdout and 'BUILD SUCCESSFUL' in stdout
            sessions+=1
        field,limit=('wall_done_ms',41173) if startup else ('marker_ms',285.3)
        values=[r[field] for r in rows if r['kind']=='formal'];mean=statistics.mean(values)
        verdict='PASS' if mean<=limit else 'FAIL'
        assert recorded['valid'] and recorded['invalidIndices']==[]
        assert (values,mean,limit,verdict)==(recorded['values'],recorded['mean'],recorded['limit'],recorded['verdict'])
        results.append(dict(group=name,values=values,mean=mean,limit=limit,verdict=verdict))
    assert sessions==9 and judgment['collectionComplete'] and judgment['valid'] and judgment['verdict']=='NOT_PASS'
    for name in ['preparation.json','groups.json','judgment.json']:
        assert json.loads(decode(previous.read('formal/metadata/'+name)))==load('c-reference/'+name)
print(json.dumps(dict(archive='PASS',artifactAndClassesIdentical=True,formal=results,overall='NOT_PASS'),indent=2))
