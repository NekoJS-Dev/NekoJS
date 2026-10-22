import hashlib
import json
import statistics
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(root / 'evidence.zip') as z:
    assert len(z.namelist()) == len(set(z.namelist())) == len(manifest['entries'])
    assert set(z.namelist()) == set(manifest['entries'])
    for name, sha in manifest['entries'].items():
        assert hashlib.sha256(z.read(name)).hexdigest() == sha, name
    def read(name):
        return json.loads(z.read(name).decode('utf-8-sig'))
    prefix = '7bea6d6d-stage-formal-cadence-v3/'
    assert read(prefix + 'exit.json')['exitCode'] == 0
    session = read(prefix + 'session/session.json')
    assert not session['forcedKill'] and not session['extra']['killed'] and session['extra']['stopChannel'] == 'rcon'
    observations = read(prefix + 'session/observations.json')
    assert len(observations) == 5
    for row in observations:
        assert row['command'] == 'nekojs reload' and row['markerMs'] is not None
        assert 'phase=COMMIT - no errors.' in row['response']
    assert read(prefix + 'restoration.json')['sourceRestored']
    restored = read(prefix + 'restored-candidate.json')
    assert restored['allClassesRestored'] and restored['classCount'] == 1640 and restored['jarByteIdentical']
    stages = read(prefix + 'analysis.json')['stages']
    windows = [row for row in stages if row['stage'] == 'command']
    assert len(windows) == 5
    groups = {}
    for row in stages:
        if any(row['thread'] == w['thread'] and w['startUtc'] <= row['startUtc']
               and row['endUtc'] <= w['endUtc'] for w in windows):
            groups.setdefault(row['stage'], []).append(row)
    summary = read(prefix + 'reload-only-summary.json')
    assert set(groups) == set(summary)
    for stage, rows in groups.items():
        assert len(rows) == summary[stage]['count']
        assert round(statistics.mean(row['wallNs'] / 1e6 for row in rows), 2) == summary[stage]['meanWallMs']
        assert round(sum(row['wallNs'] / 1e6 for row in rows), 2) == summary[stage]['sumWallMs']
    probe = read('cjs-source-reuse-probe-v2/result.json')
    assert len(probe['fixtures']) == 6
    for shared, field in [(False, 'functionConstructor'), (True, 'sharedSource')]:
        values = [r['compileMs'] for r in probe['rows'] if r['sharedSource'] == shared and not r['warmup']]
        assert len(values) == 20
        assert abs(statistics.mean(values) - probe[field + 'MeanMs']) < 1e-9
        assert abs(statistics.median(values) - probe[field + 'MedianMs']) < 1e-9
    assert read('7bea6d6d-stage-formal-cadence/attempt.json')['result'] == 'FAILED_BEFORE_GAME_START'
    assert read('7bea6d6d-stage-formal-cadence-v2/attempt.json')['result'] == 'STARTUP_FAILED'
    assert not any(name.endswith('.jfr') for name in z.namelist())
formal = root.parent / '2026-10-22-source-facts-performance/evidence.zip'
assert hashlib.sha256(formal.read_bytes()).hexdigest() == manifest['formalEvidenceSha256']
print(json.dumps(dict(archiveVerification='PASS', diagnosticOnly=True, normalServerStops=1,
    meanStageMs={stage: row['meanWallMs'] for stage, row in summary.items()}, formalPerformanceVerdict='NOT_PASS')))
