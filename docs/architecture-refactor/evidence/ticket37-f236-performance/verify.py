import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parent
summary = json.loads((root/'summary.json').read_text(encoding='utf-8'))
archive = root/summary['archive']['file']
assert hashlib.sha256(archive.read_bytes()).hexdigest()==summary['archive']['sha256']
assert archive.stat().st_size==summary['archive']['bytes']
assert summary['sourceRevision']=='f236a983b915023283b2baf200ddec4074844712'
assert summary['directSamplerExits']==[0,0,0] and summary['directOuterRunnerExit']==0
assert summary['finalOwnedGuardExit']==0 and len(summary['compiledClasses'])==1592
with ZipFile(archive) as source:
    assert sorted(source.namelist())==sorted(summary['entries'])
    for name, expected in summary['entries'].items():
        assert hashlib.sha256(source.read(name)).hexdigest()==expected,name
    assert 'EXACT_PERF_GUARD=PASS' in source.read('runner/final-owned-guard.txt').decode('utf-8-sig')
    assert json.loads(source.read('runner/formal-runner-exit.json'))['exitCode']==0
    for group in summary['groups']:
        raw=source.read('samples/'+group['folder']+'/samples.jsonl').decode('utf-8-sig')
        samples=[json.loads(line) for line in raw.splitlines() if line.strip()]
        assert samples==group['samples']
        formal=[row for row in samples if row['kind']=='formal']
        assert len(formal)==5
        assert [row['index'] for row in formal]==([3,4,5,6,7] if group['mode']=='startup' else [1,2,3,4,5])
        assert all(row['rev']=='f236a983' and row['stop_channel']=='rcon' and not row['killed'] and not row['forced_kill'] and not row.get('timeout') for row in samples)
        key='wall_done_ms' if group['mode']=='startup' else 'marker_ms'
        assert group['formalValuesMs']==[row[key] for row in formal]
        assert group['meanMs']==sum(group['formalValuesMs'])/5
        assert group['thresholdMs']==(41173 if group['mode']=='startup' else 285.3)
        assert group['acceptanceVerdict']==('PASS' if group['meanMs']<=group['thresholdMs'] else 'FAIL')
    assert [row['acceptanceVerdict'] for row in summary['groups']]==['FAIL','FAIL','PASS']
    assert summary['overall']=='FAIL'
print('PASS: immutable samples/source/exit evidence; actual performance remains FAIL')
