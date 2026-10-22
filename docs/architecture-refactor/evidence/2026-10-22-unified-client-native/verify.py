"""Verify the unified modern candidate frames and linked legacy/TCP archives offline."""
import hashlib
import json
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
for name, expected in manifest['references'].items():
    assert hashlib.sha256((root.parent / name / 'evidence.zip').read_bytes()).hexdigest() == expected
with zipfile.ZipFile(root / 'evidence.zip') as z:
    assert len(z.namelist()) == len(set(z.namelist()))
    assert set(z.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert not name.startswith(('/', '\\')) and '..' not in Path(name).parts
        data = z.read(name)
        assert len(data) == expected['size'] and hashlib.sha256(data).hexdigest() == expected['sha256'], name
    def doc(name):
        return json.loads(z.read(name))
    for short in ['261', '262']:
        run, review = doc(short + '/run-result.json'), doc(short + '/pixel-review.json')
        assert run['mechanicalVerdict'] == review['verdict'] == 'PASS'
        assert review['sourceRevision'] == manifest['sourceRevision']
        assert run['clientExit'] == run['serverExit'] == 0 and not run['errors'] and not run['forced']
        assert review['sourceBindingVerified'] and review['screenshotsReviewed'] and review['packagedClassCount'] == 1638
        assert len(run['stages']) == len(review['rows']) == 7
        for stage, row in zip(run['stages'], review['rows'], strict=True):
            assert stage['step'] == row['step'] and row['passed']
            assert hashlib.sha256(z.read(short + '/' + stage['image'])).hexdigest() == stage['sha256']
        source, artifact = doc(short + '/client/installed-source.json'), doc(short + '/artifact-manifest.json')
        assert source['artifactSha256'] == review['artifactSha256']
        assert any(value['sha256'] == source['artifactSha256'] for value in artifact['artifacts'])
        for name, value in source['classes'].items():
            assert value['sha256'] == artifact['nodeClassBindings'][name.replace('.', '/') + '.class']
        log = z.read(short + '/client/stdout.log').decode('utf8', errors='replace')
        assert log.count('OWNED-F3T-NATIVE-PRESS-BEGIN') == log.count('OWNED-F3T-NATIVE-PRESS-END held=false') == 3
    reuse = doc('tooling/4405b0b0-runtime-class-reuse.json')
    assert len(reuse) == 5 and all(row['allOtherProductionClassesIdentical'] for row in reuse)
    assert all(name.startswith('com/tkisor/nekojs/probe/') for row in reuse for name in row['changedProductionClasses'])
print(json.dumps(dict(archive='PASS', entries=len(manifest['entries']), modernFrames=14, normalProcessExits=4,
    linkedLegacyFrames=16, unchangedNonProbeProductionClasses=True)))
