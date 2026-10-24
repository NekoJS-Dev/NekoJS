"""Recount every recipe diagnostic pass and bind restoration to the formal candidate."""
from collections import Counter
from datetime import datetime, timedelta
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
    redactions = read('redactions.json')
    assert set(redactions['originalSha256']) == set(redactions['ranges'])
    local_root = root.parents[3] / 'build/priority-closeout/6e43b827-recipe-diagnostic'
    originals_checked = 0
    for name, parts in redactions['ranges'].items():
        assert name in entries and name.startswith(('run/', 'session/'))
        relative = name.removeprefix('run/') if name.startswith('run/') else name
        local = (local_root / relative).resolve()
        assert local.is_relative_to(local_root.resolve())
        if not local.is_file():
            continue
        original = local.read_bytes()
        assert hashlib.sha256(original).hexdigest() == redactions['originalSha256'][name]
        chunks, end = [], 0
        for part in parts:
            start = part['offset']
            assert start >= end and part['length'] > 0 and part['encoding'] in ['utf-8', 'utf-16-le', 'utf-16-be']
            chunks.extend([original[end:start], '[REDACTED]'.encode(part['encoding'])])
            end = start + part['length']
            assert end <= len(original)
        chunks.append(original[end:])
        assert b''.join(chunks) == archive.read(name)
        originals_checked += 1
    def original_digest(name):
        return redactions['originalSha256'].get(name, hashlib.sha256(archive.read(name)).hexdigest())
    provenance, analysis, binding = read('provenance.json'), read('run/analysis.json'), read('run/binding.json')
    restored, instrumented = read('run/restoration.json'), read('run/instrumented-artifact.json')
    events = read('run/events.json')['recording']['events']
    cpu_events = read('run/cpu-events.json')['recording']['events']
    observations, session = read('session/observations.json'), read('session/session.json')
    assert read('run/exit.json')['exitCode'] == 0
    assert provenance['sourceRevision'] == analysis['sourceRevision'] == binding['revision'] == restored['revision'] == manifest['revision']
    assert restored['sourceClean'] and restored['classesMatch'] and restored['jarMatches']
    assert restored['classes'] == len(restored['classBindings']) == 1642
    assert instrumented['classCount'] == len(instrumented['classBindings']) == 1643
    assert analysis['diagnosticOnly'] and provenance['diagnosticOnly'] and manifest['diagnosticOnly']
    assert provenance['rawJfrArchived'] is manifest['rawJfrArchived'] is False
    assert b'BUILD SUCCESSFUL' in archive.read('run/build.log')
    assert b'BUILD SUCCESSFUL' in archive.read('run/restore-build.log')
    assert not session['forcedKill'] and not session['extra']['killed']
    assert session['extra']['stopSent'] and session['extra']['stopChannel'] == 'rcon'
    output = [archive.read(name) for name in names if name.endswith('-stdout.log')]
    assert len(output) == 1 and b'Stopping server' in output[0] and b'BUILD SUCCESSFUL' in output[0]
    assert original_digest('run/events.json') == analysis['rawEventsSha256']
    assert original_digest('run/cpu-events.json') == analysis['rawCpuEventsSha256']
    assert hashlib.sha256(archive.read('run/RecipeManagerMixin.java.original')).hexdigest() == binding['bindings'][0]['originalSha256']
    assert hashlib.sha256(archive.read('run/RecipePassDiagnostic.java')).hexdigest() == binding['addedSha256']
    selected = read('run/selected-stacks.json')
    assert len(selected) == analysis['sampledStacks'] == 26
reloads = [row for row in observations if row['command'] == 'nekojs reload']
assert len(observations) == 20 and len(reloads) == analysis['successfulReloads'] == analysis['controlQueries'] == 10
assert len(events) == analysis['recipePasses'] == 10
fields = ['filterNanos', 'copyNanos', 'recipeHooksNanos', 'decodeNanos', 'mapBuildNanos', 'afterHooksNanos']
seen, recalculated_stacks = set(), []
for observation, recorded in zip(reloads, analysis['rows'], strict=True):
    assert 'phase=COMMIT - no errors.' in observation['response'] and observation['markerMs'] is not None
    start, end = datetime.fromisoformat(observation['startedUtc']), datetime.fromisoformat(observation['returnedUtc'])
    current = [(index, event) for index, event in enumerate(events)
               if start <= datetime.fromisoformat(event['values']['startTime']) <= end]
    assert len(current) == 1
    index, event = current[0]
    assert index not in seen and event['type'] == 'neko.RecipePass6e'
    seen.add(index)
    values = event['values']
    assert values['eventThread']['javaName'] == 'Server thread'
    for field in fields:
        assert values[field] == recorded[field] and values[field] >= 0
    for field in ['beforeCount', 'afterFilterCount', 'finalJsonCount', 'decodedCount']:
        assert values[field] == recorded[field] == analysis['recipesPerPass'] == 1515
    assert recorded['pair'] == observation['pair'] and recorded['markerMs'] == observation['markerMs']
    assert recorded['durationMs'] == float(values['duration'].removeprefix('PT').removesuffix('S')) * 1000
    pass_start = datetime.fromisoformat(values['startTime'])
    pass_end = pass_start + timedelta(milliseconds=recorded['durationMs'])
    for sample_event in cpu_events:
        sample = sample_event['values']
        thread = sample.get('sampledThread') or sample.get('eventThread') or {}
        if thread.get('javaThreadId') != values['eventThread']['javaThreadId']:
            continue
        if not pass_start <= datetime.fromisoformat(sample['startTime']) <= pass_end:
            continue
        frames = (sample.get('stackTrace') or {}).get('frames') or []
        methods = [frame['method']['type']['name'].replace('/', '.') + '.' + frame['method']['name'] for frame in frames]
        recalculated_stacks.append(dict(pair=observation['pair'], type=sample_event['type'],
            startTime=sample['startTime'], methods=methods))
assert len(seen) == 10
assert recalculated_stacks == selected
inclusive, leaves = Counter(), Counter()
for sample in selected:
    inclusive.update(set(sample['methods']))
    if sample['methods']:
        leaves[sample['methods'][0]] += 1
assert len(dict(analysis['inclusiveMethods'])) == len(analysis['inclusiveMethods'])
assert len(dict(analysis['leafMethods'])) == len(analysis['leafMethods'])
assert dict(inclusive) == dict(analysis['inclusiveMethods'])
assert dict(leaves) == dict(analysis['leafMethods'])
for field in fields:
    assert analysis['meanMs'][field] == statistics.mean(event['values'][field] for event in events) / 1e6
formal = root.parent / '2026-10-24-owner-stamp-performance/evidence.zip'
assert hashlib.sha256(formal.read_bytes()).hexdigest() == provenance['formalEvidenceSha256']
with zipfile.ZipFile(formal) as archive:
    preparation = json.loads(archive.read('run/preparation.json'))
assert restored['classBindings'] == preparation['classBindings']
assert restored['artifactSha256'] == preparation['artifactSha256'] == provenance['originalArtifactSha256']
print(json.dumps(dict(derivedEvidence='PASS', recipePasses=10, recipesPerPass=1515, normalStops=1,
    meanMs=analysis['meanMs'], restoredClasses=1642, localRedactedOriginalsChecked=originals_checked,
    formalGate='NOT REPLACED')))
