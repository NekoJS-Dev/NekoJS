"""Recalculate the unchanged performance gates and verify the frozen artifact."""
import hashlib
import io
import json
import statistics
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
expected = json.loads((root / 'verification.json').read_text(encoding='utf-8'))
archive = root / 'evidence.zip'
assert archive.stat().st_size == expected['bytes']
assert hashlib.sha256(archive.read_bytes()).hexdigest() == expected['sha256']
with zipfile.ZipFile(archive) as evidence:
    names = evidence.namelist()
    manifest = json.loads(evidence.read('manifest.json'))
    assert len(names) == len(set(names)) == expected['entries']
    assert set(names) == set(manifest) | {'manifest.json'}
    for name, digest in manifest.items():
        assert hashlib.sha256(evidence.read(name)).hexdigest() == digest, name
    preparation = json.loads(evidence.read('run/preparation.json'))
    judgment = json.loads(evidence.read('run/judgment.json'))
    groups = json.loads(evidence.read('run/groups.json'))
    assert preparation['revision'] == expected['revision']
    assert preparation['plannedGroups'] == [group['group'] for group in groups] == ['reload-1', 'reload-2', 'startup']
    assert all(group['exitCode'] == 0 for group in groups)
    assert hashlib.sha256(evidence.read('harness/bench/perf/sample.ps1')).hexdigest() == preparation['samplerSha256'] == '1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4'
    for name, digest in preparation['fixtureHashes'].items():
        assert hashlib.sha256(evidence.read('harness/' + name)).hexdigest() == digest
    artifact_name = preparation['artifact'].replace('\\', '/').rsplit('/', 1)[-1]
    artifact = evidence.read('artifact/' + artifact_name)
    assert hashlib.sha256(artifact).hexdigest() == preparation['artifactSha256']
    with zipfile.ZipFile(io.BytesIO(artifact)) as jar:
        for name, digest in preparation['classBindings'].items():
            assert hashlib.sha256(jar.read(name)).hexdigest() == digest, name
        source_map_sha = hashlib.sha256(jar.read('com/tkisor/nekojs/core/error/SourceMapRegistry.class')).hexdigest()
    assert len(preparation['classBindings']) == expected['classes'] == 1626
    reference = root.parent / '2026-10-20-source-map-storage-checks/evidence.zip'
    assert hashlib.sha256(reference.read_bytes()).hexdigest() == json.loads(evidence.read('references.json'))['implementationChecks']
    with zipfile.ZipFile(reference) as checks:
        binding = json.loads(checks.read('binding.json'))
        component = json.loads(checks.read('control/compact-source-map-24d35a5d-v2/summary.json'))
        assert component['currentClassSha256'] == source_map_sha
        assert binding['revision'] == expected['revision']
        assert binding['artifactDigests']['26.1.2'][artifact_name]['sha256'] == preparation['artifactSha256']
    recalculated = []
    total_rows = normal_stops = 0
    for group, recorded in zip(groups, judgment['groups'], strict=True):
        prefix = 'samples/' + group['group'] + '/'
        rows = [json.loads(line) for line in evidence.read(prefix + 'samples.jsonl').decode('utf-8-sig').splitlines()]
        startup = group['group'] == 'startup'
        key, limit = ('wall_done_ms', 41173) if startup else ('marker_ms', 285.3)
        assert len(rows) == (7 if startup else 5)
        assert sum(row['kind'] == 'warmup' for row in rows) == (2 if startup else 0)
        for row in rows:
            assert row[key] is not None and row['wall_done_ms'] is not None and row['done_s'] is not None
            assert row['killed'] is False and row['forced_kill'] is False and not row.get('timeout')
            assert row['stop_channel'] == 'rcon'
            if not startup:
                assert 'phase=COMMIT - no errors.' in row['rcon_response']
        formal = [row[key] for row in rows if row['kind'] == 'formal']
        mean = statistics.mean(formal)
        verdict = 'PASS' if mean <= limit else 'FAIL'
        assert recorded['valid'] and recorded['values'] == formal
        assert recorded['mean'] == mean and recorded['limit'] == limit and recorded['verdict'] == verdict
        recalculated.append(dict(group=group['group'], values=formal, mean=mean, verdict=verdict))
        total_rows += len(rows)
        for name in names:
            if name.startswith(prefix) and name.endswith('-stdout.log'):
                log = evidence.read(name)
                assert b'BUILD SUCCESSFUL' in log and b'Stopping server' in log, name
                normal_stops += 1
    assert total_rows == 17 and normal_stops == 9
    assert judgment['collectionComplete'] and judgment['valid']
    verdict = 'PASS' if all(row['verdict'] == 'PASS' for row in recalculated) else 'NOT_PASS'
    assert judgment['verdict'] == expected['verdict'] == verdict
print(json.dumps(dict(evidenceVerification='PASS', normalStops=normal_stops, groups=recalculated)))
