"""Verify legacy installed-client observations, ownership results and frame hashes offline."""
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
    success = manifest['successful'] + '/'
    run, review = doc(success + 'run-result.json'), doc(success + 'pixel-review.json')
    assert run['mechanicalVerdict'] == review['verdict'] == 'PASS'
    assert run['clientExit'] == run['serverExit'] == 0 and not run['errors'] and not run['forced']
    assert review['sourceBindingVerified'] and review['screenshotsReviewed']
    assert review['packagedClassCount'] == 1567 and len(run['stages']) == len(review['rows']) == 16
    stages = {value['step']: value['request'] for value in run['stages']}
    assert stages['retained-resource']['active'] and stages['retained-rejected']['active']
    assert not stages['retained-retired']['active'] and not stages['retained-inactive']['active']
    assert stages['retained-native']['active'] and stages['retained-native']['hasDefinition']
    assert stages['retained-native-retired']['active'] and not stages['retained-native-retired']['hasDefinition']
    assert review['nativeRetirementImageDifference'] < 1
    for stage, row in zip(run['stages'], review['rows'], strict=True):
        assert stage['step'] == row['step'] and row['passed']
        assert hashlib.sha256(z.read(success + stage['image'])).hexdigest() == stage['sha256']
    source, artifact = doc(success + 'client/installed-source.json'), doc(success + 'artifact-manifest.json')
    assert source['artifactSha256'] == review['artifactSha256'] == artifact['artifacts']['client']['sha256']
    for name, value in source['classes'].items():
        assert value['sha256'] == artifact['nodeClassBindings'][name.replace('.', '/') + '.class']
    log = z.read(success + 'client/stdout.log').decode('utf8', errors='replace')
    assert log.count('OWNED-F3T-NATIVE-PRESS-BEGIN') == log.count('OWNED-F3T-NATIVE-PRESS-END held=false') == 7
    failed = doc(manifest['failed'] + '/run-result.json')
    assert failed['mechanicalVerdict'] == 'NOT GREEN' and failed['clientExit'] == 1 and failed['serverExit'] == 0
    assert not failed['forced']
print(json.dumps(dict(archive='PASS', entries=len(manifest['entries']), frames=16, normalProcessExits=2,
    retainedFailedAttempts=1, nativeOwnershipPreserved=True)))
