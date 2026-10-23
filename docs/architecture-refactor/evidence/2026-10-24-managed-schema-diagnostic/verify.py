"""Verify the failed fixture, six actual-snapshot measurements and the unchanged installed artifact."""
import hashlib
import io
import json
import math
from pathlib import Path
import statistics
import zipfile

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    assert len(archive.namelist()) == len(set(archive.namelist()))
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert not name.startswith(('/', '\\')) and '..' not in Path(name).parts
        data = archive.read(name)
        assert len(data) == expected['size'] and hashlib.sha256(data).hexdigest() == expected['sha256']
    def doc(name):
        return json.loads(archive.read(name))
    failed = doc('v1/result.json')
    assert failed['verdict'] == 'NOT GREEN' and failed['errors']
    assert 'Owned schema request is invalid' in archive.read('v1/server-stdout.log').decode('utf8', errors='replace')
    result = doc('v2/result.json')
    assert result['verdict'] == 'DIAGNOSTIC COMPLETE' and result['exitCode'] == 0 and not result['forced'] and not result['errors']
    assert not result['productionChanged'] and result['productionRevision'] == manifest['sourceRevision']
    commands = doc('v2/commands.json')
    assert len(commands) == 6 and commands[-1]['command'] == 'stop'
    assert all(row['command'] == 'nekojs reload' and 'reload committed:' in row['response'] and 'no errors.' in row['response'] for row in commands[:-1])
    assert len(result['measurements']) == 6
    identities = set()
    means = []
    for index in range(6):
        row = doc(f'v2/schema-measure-{index:02}.json')
        assert row['sameThread'] and row['request'] == f'{index:02}'
        assert row['warmupPasses'] == 100
        assert row['symbolKinds'] == {'global': 7, 'member': 141}
        assert len(row['symbols']) == 148 and len(row['selectedGlobalMembers']) == 7 and row['memberChecksum'] == 79
        assert sum(len(values) for values in row['selectedGlobalMembers'].values()) == row['memberChecksum']
        values = row['measuredPassesMillis']
        assert len(values) == 1000 and all(math.isfinite(value) and value >= 0 for value in values)
        ordered = sorted(values)
        assert abs(statistics.mean(values) - row['meanMillis']) < 1e-9
        assert ordered[500] == row['medianMillis'] and ordered[950] == row['p95Millis'] and ordered[-1] == row['maxMillis']
        assert all(result['measurements'][index][key] == row[key] for key in result['measurements'][index])
        identities.add(row['surfaceIdentity'])
        means.append(row['meanMillis'])
    assert len(identities) == 1
    node = next(row for row in doc('installed-candidate.json')['nodes'] if row['node'] == '26.1.2')
    reference = manifest['artifactReference']
    assert reference['path'] == '../2026-10-24-method-bounds-performance/evidence.zip'
    external_file = root.parent / '2026-10-24-method-bounds-performance/evidence.zip'
    assert hashlib.sha256(external_file.read_bytes()).hexdigest() == reference['archiveSha256']
    with zipfile.ZipFile(external_file) as external:
        data = external.read(reference['entry'])
    assert hashlib.sha256(data).hexdigest() == node['sha256'] == result['productionSha256']
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        for name, expected in node['classBindings'].items():
            assert hashlib.sha256(jar.read(name)).hexdigest() == expected
    proofs = [name for name in archive.namelist() if name.startswith('v2/installed-source-')]
    assert len(proofs) == 1
    proof = doc(proofs[0])
    assert proof['pid'] == doc('v2/owned-process.json')['pid'] and proof['artifactSha256'] == node['sha256']
    assert len(proof['classes']) == 5
    for fqn, loaded in proof['classes'].items():
        assert loaded['sha256'] == node['classBindings'][fqn.replace('.', '/') + '.class']
        assert node['artifact'] in loaded['codeSource'] and node['artifact'] in loaded['resource']
    assert hashlib.sha256(archive.read('v2/helper.jar')).hexdigest() == result['helperSha256']
print(json.dumps(dict(archive='PASS', actualSymbols=148, selectedGlobals=7, rawSamples=6000,
    meanMillisRange=[min(means), max(means)], successfulNormalStops=1, failedAttemptRetained=True,
    productionChanged=False, scope='pure metadata re-extraction; not BINDING phase or formal performance')))
