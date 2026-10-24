"""Recalculate path-operation observations; does not replace the original performance policy."""
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import re
import statistics
import sys
import zipfile

HERE = Path(__file__).resolve().parent
manifest = json.loads((HERE / 'manifest.json').read_text(encoding='utf-8'))
payload = (HERE / 'evidence.zip').read_bytes()
assert len(payload) == manifest['bytes'] == 618514
assert hashlib.sha256(payload).hexdigest() == manifest['sha256'] == '24995beb88dbeab542cd2a4e11114541b613e2cd09b82cd43a37243908791704'
with zipfile.ZipFile(HERE / 'evidence.zip') as jar:
    files = {name: jar.read(name) for name in jar.namelist()}
entries = json.loads(files['entries.json'])
assert len(files) == manifest['entries'] == 32
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
assert analysis['diagnosticOnly'] and provenance['rawJfrArchived'] is manifest['rawJfrArchived'] is False
assert restored['sourceClean'] and restored['classesMatch'] and restored['jarMatches']
assert restored['classes'] == len(restored['classBindings']) == 1642
assert instrumented['classCount'] == len(instrumented['classBindings']) == 1643
assert read('run/exit.json')['exitCode'] == 0
assert not session['forcedKill'] and not session['extra']['killed']
assert session['extra']['stopSent'] and session['extra']['stopChannel'] == 'rcon'
assert b'BUILD SUCCESSFUL' in files['run/build.log'] and b'BUILD SUCCESSFUL' in files['run/restore-build.log']
logs = [data for name, data in files.items() if name.endswith('-stdout.log')]
assert len(logs) == 1 and b'Stopping server' in logs[0] and b'BUILD SUCCESSFUL' in logs[0]
for row in binding['bindings']:
    assert hashlib.sha256(files['run/' + row['saved']]).hexdigest() == row['originalSha256']
assert hashlib.sha256(files['run/PathOperationDiagnostic.java']).hexdigest() == binding['addedSha256']
redactions = read('redactions.json')
assert len(redactions['ranges']) == 7 and sum(len(rows) for rows in redactions['ranges'].values()) == 154
assert redactions['originalSha256']['run/events.json'] == analysis['rawEventsSha256']
assert len(redactions['ranges']['run/events.json']) == 60
formal = HERE.parent / '2026-10-24-owner-stamp-performance/evidence.zip'
assert hashlib.sha256(formal.read_bytes()).hexdigest() == provenance['formalEvidenceSha256'] == '37a1e62646398c6998ffd36b0c59e4b97cc2dbaec56f2de3e9885ce735d39d82'
with zipfile.ZipFile(formal) as jar:
    preparation = json.loads(jar.read('run/preparation.json'))
assert restored['classBindings'] == preparation['classBindings']
assert restored['artifactSha256'] == preparation['artifactSha256'] == provenance['originalArtifactSha256']

def instant_ns(value):
    found = re.fullmatch(r'(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:\.(\d{1,9}))?(Z|[+-]\d\d:\d\d)', value)
    assert found, value
    seconds = int(datetime.fromisoformat(found[1] + found[3].replace('Z', '+00:00')).timestamp())
    return seconds * 1_000_000_000 + int((found[2] or '').ljust(9, '0'))

def duration_ns(value):
    found = re.fullmatch(r'PT([\d.]+)S', value)
    assert found, value
    return int(Decimal(found[1]) * 1_000_000_000)

def caller(value):
    own = {'com/tkisor/nekojs/core/fs/NekoJSPaths', 'com/tkisor/nekojs/core/module/NekoCanonicalPath',
           'com/tkisor/nekojs/core/fs/NekoModuleReadService'}
    for frame in (value.get('stackTrace') or {}).get('frames', []):
        method = frame['method']
        if method['type']['name'] not in own:
            return method['type']['name'].replace('/', '.') + '.' + method['name']
    return 'UNAVAILABLE_OR_TRUNCATED'

rows = []
seen = set()
caller_stats = defaultdict(lambda: dict(calls=0, nanos=0))
exists_stats = defaultdict(lambda: dict(calls=0, nanos=0))
for observation in [row for row in observations if row['command'] == 'nekojs reload']:
    assert 'phase=COMMIT - no errors.' in observation['response'] and observation['markerMs'] is not None
    start, end = instant_ns(observation['startedUtc']), instant_ns(observation['returnedUtc'])
    counts, nanos, intervals = Counter(), Counter(), []
    for index, event in enumerate(events):
        value = event['values']
        entered = instant_ns(value['startTime'])
        if not start <= entered <= end:
            continue
        assert index not in seen and event['type'] == 'neko.PathOperation6e'
        assert value['eventThread']['javaName'] == 'Server thread'
        seen.add(index)
        elapsed = duration_ns(value['duration'])
        operation = value['operation']
        counts[operation] += 1
        nanos[operation] += elapsed
        intervals.append((entered, entered + elapsed))
        stats = caller_stats[(operation, caller(value))]
        stats['calls'] += 1
        stats['nanos'] += elapsed
        if operation == 'legacy-original-exists':
            stats = exists_stats[(value['suffix'], value['existing'])]
            stats['calls'] += 1
            stats['nanos'] += elapsed
    assert sum(counts.values()) == 44
    merged = []
    for entered, exited in sorted(intervals):
        if merged and entered <= merged[-1][1]:
            merged[-1][1] = max(merged[-1][1], exited)
        else:
            merged.append([entered, exited])
    rows.append(dict(pair=observation['pair'], markerMs=observation['markerMs'], calls=dict(counts),
        inclusiveMs={name: value / 1e6 for name, value in sorted(nanos.items())},
        measuredIntervalUnionMs=sum(end - start for start, end in merged) / 1e6))
assert len(rows) == 10 and len(seen) == len(events)
operations = sorted({event['values']['operation'] for event in events})
result = dict(sourceRevision=binding['revision'],
    diagnosticOnly=True, successfulReloads=10, controlQueries=10, normalStops=1, eventCount=len(events), rows=rows,
    operationCalls={name: sum(row['calls'].get(name, 0) for row in rows) for name in operations},
    inclusiveMeanMs={name: statistics.mean(row['inclusiveMs'].get(name, 0) for row in rows) for name in operations},
    measuredIntervalUnionMeanMs=statistics.mean(row['measuredIntervalUnionMs'] for row in rows),
    callers=[dict(operation=operation, caller=who, **value) for (operation, who), value in sorted(caller_stats.items())],
    existsProbes=[dict(suffix=suffix, existing=existing, **value) for (suffix, existing), value in sorted(exists_stats.items())],
    rawEventsSha256=redactions['originalSha256']['run/events.json'],
    limits=['Instrumented boundaries and JFR stack capture may change cost; no formal policy replacement or causal savings.',
        'Operation totals are inclusive and nested; the separately computed interval union avoids double-counting.',
        'Only the four named boundaries are measured; other resolver and IO costs are not excluded.',
        'The exists outcome is meaningful only for legacy-original-exists; default boolean fields on other events are not observations.'])

assert result == analysis
assert result['operationCalls'] == {'fs-canonical-spliced': 130, 'fs-verify-root': 60, 'legacy-original-exists': 10, 'module-canonical-key': 240}
assert result['existsProbes'] == [dict(suffix='other', existing=True, calls=10, nanos=54700)]
local_originals = 0
if '--check-local' in sys.argv:
    source_root = HERE.parents[3] / 'build/priority-closeout/6e43b827-path-diagnostic'
    for name, ranges in redactions['ranges'].items():
        source = source_root / (name[4:] if name.startswith('run/') else name)
        original = source.read_bytes()
        assert hashlib.sha256(original).hexdigest() == redactions['originalSha256'][name]
        chunks, offset = [], 0
        for row in ranges:
            assert row['offset'] >= offset
            chunks.extend([original[offset:row['offset']], '[REDACTED]'.encode(row['encoding'])])
            offset = row['offset'] + row['length']
        chunks.append(original[offset:])
        assert b''.join(chunks) == files[name]
        local_originals += 1
print(json.dumps(dict(derivedEvidence='PASS', reloads=10, events=440, normalStops=1, restoredClasses=1642,
    inclusiveMeanMs=result['inclusiveMeanMs'], measuredIntervalUnionMeanMs=result['measuredIntervalUnionMeanMs'],
    localOriginalsChecked=local_originals, formalGate='NOT REPLACED', rawJfr='LOCAL ONLY'), indent=2))
