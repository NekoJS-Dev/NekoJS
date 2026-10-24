"""Recalculate recorded source phases and verify exact candidate restoration."""
from collections import Counter
from datetime import datetime
import hashlib
import json
from pathlib import Path
import statistics
import zipfile

HERE = Path(__file__).resolve().parent
manifest = json.loads((HERE / 'manifest.json').read_text(encoding='utf-8'))
payload = (HERE / 'evidence.zip').read_bytes()
assert len(payload) == manifest['bytes'] == 332302
assert hashlib.sha256(payload).hexdigest() == manifest['sha256'] == 'e3cd70ba76ce18f22f97d49e28fe6c9d61e9c980d2f1ff4d8aee51780fb43a36'
with zipfile.ZipFile(HERE / 'evidence.zip') as jar:
    files = {name: jar.read(name) for name in jar.namelist()}
assert len(files) == manifest['entries'] == 29
entries = json.loads(files['entries.json'])
assert set(files) == set(entries) | {'entries.json'}
for name, expected in entries.items():
    assert hashlib.sha256(files[name]).hexdigest() == expected
def read(name):
    return json.loads(files[name].decode('utf-8-sig'))
analysis = read('run/analysis.json')
binding = read('run/binding.json')
restored = read('run/restoration.json')
instrumented = read('run/instrumented-artifact.json')
provenance = read('provenance.json')
events = read('run/events.json')['recording']['events']
observations = read('session/observations.json')
session = read('session/session.json')
assert manifest['revision'] == binding['revision'] == restored['revision'] == provenance['sourceRevision'] == analysis['sourceRevision'] == '6e43b82789a7dbf7b9659dc1c21fefecac662649'
assert analysis['diagnosticOnly'] and analysis['successfulPreparationsOnly'] and binding['successfulPreparationsOnly']
assert provenance['rawJfrArchived'] is manifest['rawJfrArchived'] is False
assert restored['sourceClean'] and restored['classesMatch'] and restored['jarMatches']
assert restored['classes'] == len(restored['classBindings']) == 1642
assert instrumented['classCount'] == len(instrumented['classBindings']) == 1643
assert read('run/exit.json')['exitCode'] == 0
assert not session['forcedKill'] and not session['extra']['killed']
assert session['extra']['stopSent'] and session['extra']['stopChannel'] == 'rcon'
assert b'BUILD SUCCESSFUL' in files['run/build.log'] and b'BUILD SUCCESSFUL' in files['run/restore-build.log']
logs = [data for name, data in files.items() if name.endswith('-stdout.log')]
assert len(logs) == 1 and b'Stopping server' in logs[0] and b'BUILD SUCCESSFUL' in logs[0]
assert hashlib.sha256(files['run/NekoModulePipelineCache.java.original']).hexdigest() == binding['bindings'][0]['originalSha256']
assert hashlib.sha256(files['run/events.json']).hexdigest() == analysis['rawEventsSha256']
phases = ['keyNanos', 'approvalNanos', 'readNanos', 'bindingNanos', 'attributesNanos', 'hashNanos', 'computeNanos', 'pipelineNanos', 'publishNanos']
reloads = [row for row in observations if row['command'] == 'nekojs reload']
assert len(observations) == 20 and len(reloads) == analysis['successfulReloads'] == analysis['controlQueries'] == 10
assert len(events) == analysis['preparationEvents'] == 60
seen = set()
module_calls = Counter()
rows = []
for observation, recorded in zip(reloads, analysis['rows'], strict=True):
    assert 'phase=COMMIT - no errors.' in observation['response'] and observation['markerMs'] is not None
    start, end = datetime.fromisoformat(observation['startedUtc']), datetime.fromisoformat(observation['returnedUtc'])
    current = []
    for index, event in enumerate(events):
        values = event['values']
        if start <= datetime.fromisoformat(values['startTime']) <= end:
            assert index not in seen and event['type'] == 'neko.SourcePreparation6e'
            seen.add(index)
            current.append(values)
            assert values['eventThread']['javaName'] == 'Server thread'
            assert all(values[name] >= 0 for name in phases)
            assert values['pipelineNanos'] <= values['computeNanos']
            module_calls[values['modulePathSha256']] += 1
    assert len(current) == recorded['preparationEvents'] == 6
    phase_ms = {name.removesuffix('Nanos'): sum(value[name] for value in current) / 1e6 for name in phases}
    assert phase_ms == recorded['phaseMs']
    assert sum(value['cacheHit'] for value in current) == recorded['cacheHits'] == 0
    assert recorded['pair'] == observation['pair'] and recorded['markerMs'] == observation['markerMs']
    rows.append(phase_ms)
assert len(seen) == 60 and len(module_calls) == analysis['distinctModulePaths'] == 6
assert set(module_calls.values()) == {10} and dict(module_calls) == analysis['moduleCalls']
means = {name.removesuffix('Nanos'): statistics.mean(row[name.removesuffix('Nanos')] for row in rows) for name in phases}
assert means == analysis['phaseMeanMs'] and analysis['cacheHits'] == 0 and analysis['eventsPerReload'] == {'6': 10}
redactions = read('redactions.json')
assert len(redactions['ranges']) == len(redactions['originalSha256']) == 6
assert sum(len(values) for values in redactions['ranges'].values()) == 97
formal = HERE.parent / '2026-10-24-owner-stamp-performance/evidence.zip'
assert hashlib.sha256(formal.read_bytes()).hexdigest() == provenance['formalEvidenceSha256'] == '37a1e62646398c6998ffd36b0c59e4b97cc2dbaec56f2de3e9885ce735d39d82'
with zipfile.ZipFile(formal) as jar:
    preparation = json.loads(jar.read('run/preparation.json'))
assert restored['classBindings'] == preparation['classBindings']
assert restored['artifactSha256'] == preparation['artifactSha256'] == provenance['originalArtifactSha256']
print(json.dumps(dict(derivedEvidence='PASS', successfulReloads=10, preparationEvents=60, cacheHits=0,
    phaseMeanMs=means, exclusiveSumMeanMs=sum(value for name, value in means.items() if name != 'pipeline'),
    restoredClasses=1642, normalStops=1, formalGate='NOT REPLACED', rawJfr='LOCAL ONLY'), indent=2))
