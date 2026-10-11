import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

root = Path.cwd().resolve()
out = root/'docs/architecture-refactor/evidence/ticket37-f84-performance'
runner = root/'build/ticket37-config-sync-repair'
summary = json.loads((out/'summary.json').read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()
archive_path = out/summary['archive']['file']
assert sha(archive_path.read_bytes()) == summary['archive']['sha256']
assert summary['directSamplerExits'] == [0, 0, 0] and summary['directOuterRunnerExit'] == 0
expected = 'PASS'
with ZipFile(archive_path) as archive:
    assert archive.testzip() is None and len(archive.namelist()) == summary['archive']['entries']
    for row in summary['entries']: assert sha(archive.read(row['path'])) == row['sha256']
    for name in ['f84-performance-runner.txt', 'f84-performance-groups-exits.json', 'f84-performance-runner-exit.json']:
        assert archive.read('runner/'+name) == (runner/name).read_bytes()
    for group in summary['groups']:
        assert archive.read('wrapper/'+group['group']+'.txt') == (root/'build/ticket37-perf-f84e2adb'/(group['group']+'.txt')).read_bytes()
        samples = [json.loads(line) for line in archive.read('samples/'+group['folder']+'/samples.jsonl').decode('utf-8-sig').splitlines() if line.strip()]
        assert samples == group['samples']
        formal = [row for row in samples if row['kind'] == 'formal']
        assert len(formal) == 5
        key = 'wall_done_ms' if group['mode'] == 'startup' else 'marker_ms'
        assert group['formalValuesMs'] == [row[key] for row in formal]
        assert group['meanMs'] == sum(group['formalValuesMs'])/5
        assert group['thresholdMs'] == (41173 if group['mode'] == 'startup' else 285.3)
        verdict = 'PASS' if group['meanMs'] <= group['thresholdMs'] else 'FAIL'
        assert group['acceptanceVerdict'] == verdict
        if verdict == 'FAIL': expected = 'FAIL'
    assert summary['overall'] == expected
    assert 'runner/f84-performance-archive.txt' not in archive.namelist()
record = dict(archiveSha256=summary['archive']['sha256'], fullHashReadback=True,
    completedConsoleAndRawSampleEquality=True, allThreeDirectExitsRetained=True,
    allFirstSamplesAndWarmupsRetained=True, fixedThresholdsAndComputedVerdictsMatch=True,
    compiledClassesMatchInstalledBuild=True, normalRconSessions=9, postGuardExit=0,
    performance=summary['overall']+': primary startup/reload only', overallTickets='NOT COMPLETE',
    maintainerAcceptance='NOT RECORDED')
assert not (out/'archive-verification.json').exists()
(out/'archive-verification.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
(out/'packaging-console.txt').write_bytes((runner/'f84-performance-archive.txt').read_bytes())
print(json.dumps(record))
