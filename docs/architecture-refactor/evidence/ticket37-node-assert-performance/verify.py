import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

root=Path(__file__).resolve().parent
summary=json.loads((root/'summary.json').read_text())
sha=lambda data:hashlib.sha256(data).hexdigest()
archive=root/summary['archive']['file']
assert sha(archive.read_bytes())==summary['archive']['sha256']
assert archive.stat().st_size==summary['archive']['bytes']
assert summary['sourceRevision']=='7d77308bfa400721304b20940ada91092180a1ed'
assert summary['directSamplerExits']==[0,0,0] and summary['directOuterRunnerExit']==0
assert summary['samplerSha256']=='1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4'
with ZipFile(archive) as source:
    assert source.testzip() is None and len(source.namelist())==summary['archive']['entries']
    assert set(source.namelist())=={row['path'] for row in summary['entries']}
    for row in summary['entries']:
        data=source.read(row['path']); assert len(data)==row['bytes'] and sha(data)==row['sha256']
    groups=json.loads(source.read('runner/node-assert-performance-groups-exits.json'))
    assert len(groups)==3 and all(row['samplerExit']==0 for row in groups)
    assert json.loads(source.read('runner/node-assert-performance-runner-exit.json'))['exitCode']==0
    assert json.loads(source.read('wrapper/final-owned-guard-exit.json'))['exitCode']==0
    assert 'EXACT_PERF_GUARD=PASS' in source.read('wrapper/final-owned-guard.txt').decode()
    for group in summary['groups']:
        samples=[json.loads(line) for line in source.read('samples/'+group['folder']+'/samples.jsonl').decode('utf-8-sig').splitlines() if line.strip()]
        assert samples==group['samples']
        formal=[row for row in samples if row['kind']=='formal']
        assert len(formal)==5
        assert [row['index'] for row in formal]==([3,4,5,6,7] if group['mode']=='startup' else [1,2,3,4,5])
        assert all(row['stop_channel']=='rcon' and not row['forced_kill'] and not row['killed'] and not row.get('timeout') for row in samples)
        key='wall_done_ms' if group['mode']=='startup' else 'marker_ms'
        assert group['formalValuesMs']==[row[key] for row in formal]
        assert group['meanMs']==sum(group['formalValuesMs'])/5
        assert group['thresholdMs']==(41173 if group['mode']=='startup' else 285.3)
        assert group['acceptanceVerdict']==('PASS' if group['meanMs']<=group['thresholdMs'] else 'FAIL')
    assert summary['overall']=='FAIL' and [row['acceptanceVerdict'] for row in summary['groups']]==['FAIL','FAIL','PASS']
print(json.dumps(dict(result='PASS',entries=summary['archive']['entries'],performance=summary['overall'],
    compiledClasses=len(summary['compiledClasses']),sha256=summary['archive']['sha256'],overallTickets='NOT_COMPLETE')))
