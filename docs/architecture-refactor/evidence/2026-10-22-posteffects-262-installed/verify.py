"""Verify installed-client observations and original frame hashes offline."""
import hashlib
import json
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(root / 'evidence.zip') as z:
    assert len(z.namelist()) == len(set(z.namelist()))
    assert set(z.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert not name.startswith(('/', '\\')) and '..' not in Path(name).parts
        data = z.read(name)
        assert len(data) == expected['size'] and hashlib.sha256(data).hexdigest() == expected['sha256'], name
    def doc(name):
        return json.loads(z.read(name))
    run, review = doc('run-result.json'), doc('pixel-review.json')
    assert run['mechanicalVerdict'] == review['verdict'] == 'PASS'
    assert run['clientExit'] == run['serverExit'] == 0 and not run['errors'] and not run['forced']
    assert review['sourceBindingVerified'] and review['screenshotsReviewed']
    assert review['packagedClassCount'] == 1635 and len(run['stages']) == 7
    for stage, row in zip(run['stages'], review['rows'], strict=True):
        assert stage['step'] == row['step'] and row['passed']
        assert hashlib.sha256(z.read(stage['image'])).hexdigest() == stage['sha256']
        score = row['gradientSquaredMean']
        assert score > 100 if row['expected'] == 'clear' else 0.5 < score < 50
    source, artifact = doc('client/installed-source.json'), doc('artifact-manifest.json')
    assert source['artifactSha256'] == review['artifactSha256']
    assert any(value['sha256'] == source['artifactSha256'] for value in artifact['artifacts'])
    for name, value in source['classes'].items():
        assert value['sha256'] == artifact['nodeClassBindings'][name.replace('.', '/') + '.class']
    log = z.read('client/stdout.log').decode('utf8', errors='replace')
    assert log.count('OWNED-F3T-NATIVE-PRESS-BEGIN') == log.count('OWNED-F3T-NATIVE-PRESS-END held=false') == 3
print(json.dumps(dict(archive='PASS', entries=len(manifest['entries']), frames=7, normalProcessExits=2)))
