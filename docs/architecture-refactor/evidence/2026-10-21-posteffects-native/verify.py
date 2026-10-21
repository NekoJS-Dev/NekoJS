"""Recheck sealed frame evidence; does not run games or grant release acceptance."""
import hashlib
import io
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

import numpy as np
from PIL import Image

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf8'))
summary = json.loads((root / 'summary.json').read_text(encoding='utf8'))
archive = root / 'evidence.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']

with zipfile.ZipFile(archive) as z:
    def read(name):
        return json.loads(z.read(name))

    assert len(z.namelist()) == len(set(z.namelist()))
    assert set(z.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        data = z.read(name)
        assert len(data) == expected['size'], name
        assert hashlib.sha256(data).hexdigest() == expected['sha256'], name
        assert not name.endswith(('server.properties', '.jar', 'owned-java-args.txt'))
    captures = 0
    for case in summary['accepted']:
        prefix = case['attempt'] + '/'
        result = read(prefix + 'run-result.json')
        review = read(prefix + 'pixel-review.json')
        binding = read(prefix + 'source-binding.json')
        assert result['mechanicalVerdict'] == 'PASS' and not result['errors'] and not result['forced']
        assert result['clientExit'] == result['serverExit'] == 0
        assert len(result['stages']) == case['images']
        assert review['screenshotsReviewed'] and all(x['passed'] for x in review['rows'])
        assert binding['sourceRevision'].startswith(case['sourceRevision'])
        if 'productionClassHashes' in binding:
            assert binding['compiledClassesMatchArtifact'] and binding['productionClassCount'] >= 1559
            assert len(binding['productionClassHashes']) == binding['productionClassCount']
        else:
            assert binding['compiledClassesMatchInstalledCandidate'] and len(binding['classBindings']) == 1630
        for name, digest in binding.get('originalFixtureHashes', binding.get('fixtureHashes', {})).items():
            assert hashlib.sha256(z.read(prefix + name.replace('\\', '/'))).hexdigest() == digest, name
        log = z.read(prefix + 'client/stdout.log').decode('utf8', errors='replace')
        assert log.count('OWNED-F3T-NATIVE-PRESS-BEGIN') == case['nativeKeyboardDispatches']
        assert log.count('OWNED-F3T-NATIVE-PRESS-END held=false') == case['nativeKeyboardDispatches']
        previous = -1
        images = {}
        for stage in result['stages']:
            data = z.read(prefix + stage['image'])
            assert hashlib.sha256(data).hexdigest() == stage['sha256']
            request = stage['request']
            assert request['sameThread'] and not request['overlayPresent'] and request['frame'] > previous
            previous = request['frame']
            image = Image.open(io.BytesIO(data)).crop(tuple(review['roi']))
            images[stage['step']] = np.asarray(image).astype(float)
            if case['node'] != '1.21.1':
                pixels = np.asarray(image.convert('L')).astype(float)
                score = float(np.mean(np.diff(pixels, axis=1) ** 2))
                assert score > 100 if stage['step'].startswith('clear') else .5 < score < 50
        if case['node'] == '1.21.1':
            clear, effect = images['clear-after'], images['effect-after']
            for row in review['rows']:
                pixels = images[row['step']]
                if row['expected'] == 'native-creeper':
                    assert np.mean(pixels[:, :, 1] - pixels[:, :, 0]) > 30
                else:
                    dc, de = np.mean(np.abs(pixels - clear)), np.mean(np.abs(pixels - effect))
                    assert dc < de if row['expected'] == 'clear' else de < dc
            assert np.mean(np.abs(images['retained-native'] - images['retained-native-retired'])) < 1
            stages = {x['step']: x['request'] for x in result['stages']}
            assert stages['retained-native-retired']['active'] and not stages['retained-native-retired']['hasDefinition']
            assert not stages['retained-inactive']['active']
        captures += len(images)
    # Preserve the two actual regressions, including the old mechanically green but visually failed run.
    old = read('posteffects-121-v2/retention-review.json')
    assert old['verdict'] == 'FAIL' and old['retainedToClear'] < old['retainedToEffect']
    red = read('posteffects-121-native-retire-red/run-result.json')
    assert red['mechanicalVerdict'] == 'NOT GREEN' and red['errors']
    assert not red['stages'][-1]['request']['active']
    assert read('posteffects-262-f3t-v2/run-result.json')['mechanicalVerdict'] == 'NOT GREEN'
    totals = dict(tests=0, skipped=0, failures=0, errors=0)
    for name in z.namelist():
        if name.startswith('checks/xml/'):
            suite = ET.fromstring(z.read(name))
            for key in totals:
                totals[key] += int(suite.attrib.get(key, 0))
    assert totals == summary['tests'], totals
    assert 'BUILD SUCCESSFUL' in z.read('tooling/legacy-posteffect-final-build.log').decode('utf8', errors='replace')
    assert captures == 30 and not summary['maintainerAcceptance'] and not summary['installedClientJarLaunch']

print('PASS: sealed hashes, 30 reviewed world-target frames, native dispatches, source bindings and 447 legacy checks.')
print('Retained: pre-fix visual/retirement failures and startup failures. No installed-client, physical-input or release sign-off.')
