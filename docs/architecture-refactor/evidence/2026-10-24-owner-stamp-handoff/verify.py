"""Verify the inventory and, when present, each exact local artifact and production class."""
import hashlib
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent
expected = json.loads((root / 'verification.json').read_text())
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == expected['sha256']
assert (root / 'evidence.zip').stat().st_size == expected['bytes']
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    entries = json.loads(archive.read('entries.json'))
    assert set(archive.namelist()) == set(entries) | {'entries.json'}
    assert len(archive.namelist()) == expected['entries']
    for name, digest in entries.items():
        assert hashlib.sha256(archive.read(name)).hexdigest() == digest
    manifest = json.loads(archive.read('manifest.json'))
    bindings = json.loads(archive.read('class-bindings.json'))
    sums = archive.read('SHA256SUMS').decode()
assert manifest['productionRevision'] == expected['revision']
assert manifest['releaseReady'] is manifest['remoteUploadPerformed'] is manifest['sourceVersionRepackaged'] is False
assert len(manifest['artifacts']) == expected['artifacts'] == 6
assert len({row['node'] for row in manifest['artifacts']}) == 5
assert sum(row['bytes'] for row in manifest['artifacts']) == expected['artifactBytes']
assert sums.splitlines() == [row['sha256'] + '  ' + row['path'] for row in manifest['artifacts']]
repo = root.parents[3]
for name, digest in manifest['evidenceArchives'].items():
    assert hashlib.sha256((root.parent / name / 'evidence.zip').read_bytes()).hexdigest() == digest
candidate = repo / 'build/release-candidate-6e43b827'
checked = 0
if candidate.exists():
    assert json.loads((candidate / 'manifest.json').read_text()) == manifest
    for row in manifest['artifacts']:
        path = candidate / row['path']
        assert path.stat().st_size == row['bytes']
        assert hashlib.sha256(path.read_bytes()).hexdigest() == row['sha256']
        with zipfile.ZipFile(path) as jar:
            classes = {name for name in jar.namelist() if name.startswith('com/tkisor/nekojs/') and name.endswith('.class')}
            assert classes == set(bindings[row['node']])
            assert len(classes) == row['productionClasses']
            for name, digest in bindings[row['node']].items():
                assert hashlib.sha256(jar.read(name)).hexdigest() == digest
        checked += 1
print(json.dumps(dict(inventory='PASS', localArtifactsChecked=checked,
    localPayloads='PASS' if checked == 6 else 'NOT PRESENT', releaseReady=False)))
