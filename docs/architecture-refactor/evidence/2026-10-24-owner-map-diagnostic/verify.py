"""Recalculate derived map measurements and verify exact restoration bindings."""
from collections import Counter
from datetime import datetime
import hashlib
import json
from pathlib import Path
import statistics
import zipfile

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
payload = (root / 'evidence.zip').read_bytes()
assert len(payload) == manifest['bytes'] and hashlib.sha256(payload).hexdigest() == manifest['sha256']
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    names = archive.namelist()
    entries = json.loads(archive.read('entries.json'))
    assert len(names) == len(set(names)) == manifest['entries']
    assert set(names) == set(entries) | {'entries.json'}
    for name, digest in entries.items():
        assert hashlib.sha256(archive.read(name)).hexdigest() == digest
    def read(name):
        return json.loads(archive.read(name).decode('utf-8-sig'))
    provenance = read('provenance.json')
    analysis = read('run/analysis.json')
    binding = read('run/binding.json')
    restored = read('run/restoration.json')
    instrumented = read('run/instrumented-artifact.json')
    events = read('run/events.json')['recording']['events']
    observations = read('session/observations.json')
    session = read('session/session.json')
    assert read('run/exit.json')['exitCode'] == 0
    assert provenance['sourceRevision'] == analysis['sourceRevision'] == binding['revision'] == restored['revision'] == manifest['revision']
    assert restored['sourceClean'] and restored['classesMatch'] and restored['jarMatches']
    assert restored['classes'] == len(restored['classBindings']) == 1642
    assert instrumented['classCount'] == len(instrumented['classBindings']) == 1643
    assert analysis['diagnosticOnly'] and provenance['diagnosticOnly'] and manifest['diagnosticOnly']
    assert provenance['rawJfrArchived'] is manifest['rawJfrArchived'] is False
    assert b'BUILD SUCCESSFUL' in archive.read('run/build-v2.log')
    assert b'BUILD FAILED' in archive.read('run/build.log')
    assert b'BUILD SUCCESSFUL' in archive.read('run/restore-build.log')
    assert not session['forcedKill'] and not session['extra']['killed']
    assert session['extra']['stopSent'] and session['extra']['stopChannel'] == 'rcon'
    output = [archive.read(name) for name in names if name.endswith('-stdout.log')]
    assert len(output) == 1 and b'Stopping server' in output[0] and b'BUILD SUCCESSFUL' in output[0]
    assert hashlib.sha256(archive.read('run/events.json')).hexdigest() == analysis['rawEventsSha256']
    assert hashlib.sha256(archive.read('run/SourceMapRegistry.java.original')).hexdigest() == binding['bindings'][0]['originalSha256']
reloads = [row for row in observations if row['command'] == 'nekojs reload']
assert len(observations) == 20 and len(reloads) == analysis['successfulReloads'] == analysis['controlQueries'] == 10
assert len(events) == analysis['mapEvents'] == 60
seen, keys, sums, normals = set(), Counter(), [], []
for observation, recorded in zip(reloads, analysis['rows'], strict=True):
    assert 'phase=COMMIT - no errors.' in observation['response'] and observation['markerMs'] is not None
    start, end = datetime.fromisoformat(observation['startedUtc']), datetime.fromisoformat(observation['returnedUtc'])
    current = []
    for index, event in enumerate(events):
        values = event['values']
        if start <= datetime.fromisoformat(values['startTime']) <= end:
            assert index not in seen and event['type'] == 'neko.SourceMapPreparation6e'
            seen.add(index)
            current.append(values)
            assert values['eventThread']['javaName'] == 'Server thread'
            keys[(values['generatedPath'], values['rawSha256'], values['cacheRevision'], values['prependedLines'])] += 1
    assert len(current) == recorded['events'] == 6
    parse = sum(value['parseNanos'] for value in current) / 1e6
    normalize = sum(value['normalizeNanos'] for value in current) / 1e6
    assert recorded['parseMs'] == parse and recorded['normalizeMs'] == normalize
    assert recorded['pair'] == observation['pair'] and recorded['markerMs'] == observation['markerMs']
    sums.append(parse)
    normals.append(normalize)
assert len(seen) == 60 and len(keys) == analysis['distinctMapKeys'] == 6
assert set(keys.values()) == {10}
assert statistics.mean(sums) == analysis['parseMeanMs'] and max(sums) == analysis['parseMaxMs']
assert statistics.mean(normals) == analysis['normalizeMeanMs']
formal = root.parent / '2026-10-24-owner-stamp-performance/evidence.zip'
assert hashlib.sha256(formal.read_bytes()).hexdigest() == provenance['formalEvidenceSha256']
with zipfile.ZipFile(formal) as archive:
    preparation = json.loads(archive.read('run/preparation.json'))
assert restored['classBindings'] == preparation['classBindings']
assert restored['artifactSha256'] == preparation['artifactSha256'] == provenance['originalArtifactSha256']
print(json.dumps(dict(derivedEvidence='PASS', mapEvents=60, successfulReloads=10, normalStops=1,
    parseMeanMs=statistics.mean(sums), parseMaxMs=max(sums), restoredClasses=1642, formalGate='NOT REPLACED')))
