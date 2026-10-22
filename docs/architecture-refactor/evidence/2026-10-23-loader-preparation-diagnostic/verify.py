"""Verify diagnostic records without reclassifying the failed formal performance gate."""
import datetime
import hashlib
import json
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
sha = lambda data: hashlib.sha256(data).hexdigest()
assert sha((root / 'evidence.zip').read_bytes()) == manifest['archiveSha256']
assert manifest['diagnosticOnly'] and manifest['formalPerformanceStillFailed']
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    assert len(archive.namelist()) == len(set(archive.namelist())) == len(manifest['entries'])
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, row in manifest['entries'].items():
        data = archive.read(name)
        assert len(data) == row['size'] and sha(data) == row['sha256'], name
    def read(name):
        return json.loads(archive.read(name).decode('utf-8-sig'))
    parse = datetime.datetime.fromisoformat
    for prefix in ['7bea6d6d-loader-spans', '7bea6d6d-preparation-spans']:
        binding = read(prefix + '/binding.json')
        assert binding['revision'] == manifest['sourceRevision'] and binding['diagnosticOnly']
        for row in binding['paths']:
            if row['originalSha256'] is not None:
                name = prefix + '/originals/' + row['path']
                original = manifest['redactedOriginalSha256'].get(name, sha(archive.read(name)))
                assert original == row['originalSha256']
        assert read(prefix + '/exit.json')['exitCode'] == 0
        session = read(prefix + '/session/session.json')
        assert not session['forcedKill'] and not session['extra']['killed']
        assert session['extra']['stopChannel'] == 'rcon' and session['extra']['stopSent']
        observations = read(prefix + '/session/observations.json')
        assert len(observations) == 5 and all('phase=COMMIT - no errors.' in r['response'] for r in observations)
        stages = read(prefix + '/analysis.json')['stages']
        windows = [r for r in stages if r['stage'] == 'command']
        assert len(windows) == 5
        groups = {}
        for row in stages:
            if any(parse(w['startUtc']) <= parse(row['startUtc']) <= parse(w['endUtc']) for w in windows):
                groups.setdefault(row['stage'], []).append(row)
        calculated = {k: dict(count=len(v), totalMs=round(sum(r['wallNs'] for r in v) / 1e6, 3),
                             perReloadMs=round(sum(r['wallNs'] for r in v) / 5e6, 3)) for k, v in groups.items()}
        assert calculated == read(prefix + '/reload-only-summary.json')
        assert calculated['root']['count'] == calculated['recipe']['count'] == 5
    assert read('7bea6d6d-loader-spans/restoration.json')['ownedSourcesRestored']
    assert read('7bea6d6d-preparation-spans/restoration.json')['ownedSourcesRestored']
    assert read('7bea6d6d-preparation-spans/restored-candidate.json') == manifest['restoration']
with zipfile.ZipFile(root.parent / '2026-10-22-source-facts-performance/evidence.zip') as formal:
    measured = json.loads(formal.read('run/preparation.json'))
    assert measured['revision'] == manifest['sourceRevision']
    assert measured['artifactSha256'] == manifest['restoration']['jarSha256']
    assert len(measured['classBindings']) == manifest['restoration']['classCount'] == 1640
print(json.dumps(dict(archiveVerification='PASS', diagnosticSessions=2, committedReloads=10,
                     normalStops=2, sourceAndArtifactRestored=True, formalPerformance='FAIL')))
