"""Verify current installed-client identity, normal exits and the original rendered pixels."""
import hashlib
import io
import json
from pathlib import Path
import subprocess
import zipfile

import numpy as np
from PIL import Image

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
frames = presses = 0
source_bindings = None
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    assert len(archive.namelist()) == len(set(archive.namelist()))
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert not name.startswith(('/', '\\')) and '..' not in Path(name).parts
        data = archive.read(name)
        assert len(data) == expected['size'] and hashlib.sha256(data).hexdigest() == expected['sha256'], name
    def doc(name):
        return json.loads(archive.read(name))
    external_artifacts = {}
    for short, reference in manifest['artifactReferences'].items():
        folder = '2026-10-24-owner-stamp-performance' if short == '261' else '2026-10-24-owner-stamp-tcp'
        assert reference['path'] == '../' + folder + '/evidence.zip'
        file = root.parent / folder / 'evidence.zip'
        assert hashlib.sha256(file.read_bytes()).hexdigest() == reference['archiveSha256']
        with zipfile.ZipFile(file) as external:
            external_artifacts[short] = external.read(reference['entry'])
    for short in ['121', '261', '262']:
        prefix = short + '/'
        result = doc(prefix + 'run-result.json')
        review = doc(prefix + 'pixel-review.json')
        binding = doc(prefix + 'candidate-binding.json')
        proof = doc(prefix + 'client/installed-source.json')
        agent_review = doc(prefix + 'agent-image-review.json')
        assert agent_review['candidateSourceRevision'] == manifest['sourceRevision']
        assert agent_review['maintainerAcceptance'] is False and agent_review['originalRunScreenshotsReviewed'] is False
        assert result['screenshotsReviewed'] is False
        assert agent_review['images'] == [dict(name=stage['image'], sha256=stage['sha256']) for stage in result['stages']]
        assert binding['sourceRevision'] == review['sourceRevision'] == manifest['sourceRevision']
        assert result['mechanicalVerdict'] == review['verdict'] == 'PASS'
        assert not result['errors'] and not result['forced'] and result['clientExit'] == result['serverExit'] == 0
        assert review['screenshotsReviewed'] and review['sourceBindingVerified']
        assert len(result['stages']) == len(review['rows']) == (16 if short == '121' else 7)
        assert len(binding['sourceBindings']) == 1101
        if source_bindings is None:
            source_bindings = binding['sourceBindings']
        assert source_bindings == binding['sourceBindings']
        assert len(binding['classBindings']) == (1571 if short == '121' else 1642)
        for side, artifact in binding['artifacts'].items():
            data = archive.read('artifact/' + artifact['name']) if short == '121' else external_artifacts[short]
            assert hashlib.sha256(data).hexdigest() == artifact['sha256']
            with zipfile.ZipFile(io.BytesIO(data)) as jar:
                for name, expected in binding['classBindings'].items():
                    assert hashlib.sha256(jar.read(name)).hexdigest() == expected, name
        client_artifact = binding['artifacts']['client']
        assert proof['artifactSha256'] == review['artifactSha256'] == client_artifact['sha256']
        assert proof['expectedJar'] == proof['modFile']
        for fqn, loaded in proof['classes'].items():
            assert loaded['sha256'] == binding['classBindings'][fqn.replace('.', '/') + '.class']
            assert client_artifact['name'] in loaded['codeSource'] and client_artifact['name'] in loaded['resource']
        arrays = {}
        for stage in result['stages']:
            data = archive.read(prefix + stage['image'])
            assert hashlib.sha256(data).hexdigest() == stage['sha256']
            assert stage['request']['sameThread'] and not stage['request']['overlayPresent']
            image = Image.open(io.BytesIO(data)).crop(tuple(review['roi']))
            arrays[stage['step']] = np.asarray(image).astype(float)
        for row in review['rows']:
            values = arrays[row['step']]
            assert row['passed']
            if short == '121':
                if row['expected'] == 'native-creeper':
                    score = float(np.mean(values[:, :, 1] - values[:, :, 0]))
                    assert score > 30 and abs(score - row['greenMinusRed']) < 1e-8
                else:
                    clear = float(np.mean(np.abs(values - arrays['clear-after'])))
                    effect = float(np.mean(np.abs(values - arrays['effect-after'])))
                    assert abs(clear - row['meanAbsoluteDistanceToClear']) < 1e-8
                    assert abs(effect - row['meanAbsoluteDistanceToEffect']) < 1e-8
                    assert (clear < effect) if row['expected'] == 'clear' else (effect < clear)
            else:
                stage = next(value for value in result['stages'] if value['step'] == row['step'])
                image = Image.open(io.BytesIO(archive.read(prefix + stage['image']))).crop(tuple(review['roi'])).convert('L')
                score = float(np.mean(np.diff(np.asarray(image).astype(float), axis=1) ** 2))
                assert abs(score - row['gradientSquaredMean']) < 1e-8
                assert score > 100 if row['expected'] == 'clear' else 0.5 < score < 50
        if short == '121':
            assert float(np.mean(np.abs(arrays['retained-native'] - arrays['retained-native-retired']))) < 1
        log = archive.read(prefix + 'client/stdout.log').decode('utf8', errors='replace')
        count = 7 if short == '121' else 3
        assert log.count('OWNED-F3T-NATIVE-PRESS-BEGIN') == log.count('OWNED-F3T-NATIVE-PRESS-END held=false') == count
        assert 'INSTALLED-NEKOJS-SOURCE-PASS' in log
        commands = [json.loads(line) for line in archive.read(prefix + 'commands.jsonl').decode().splitlines()]
        assert any(value['command'] == 'stop' for value in commands)
        frames += len(result['stages'])
        presses += count
assert frames == 30 and presses == 13
repo = root.parents[3]
paths = list(source_bindings)
batch = subprocess.check_output(['git', 'cat-file', '--batch'], cwd=repo,
    input=''.join(manifest['sourceRevision'] + ':' + name + '\n' for name in paths).encode('utf-8'))
offset = 0
for name in paths:
    assert not name.startswith(('/', '\\')) and '..' not in Path(name).parts
    boundary = batch.index(b'\n', offset)
    header = batch[offset:boundary].decode('ascii').split()
    assert len(header) == 3 and header[1] == 'blob' and header[0] == source_bindings[name]['committedBlob'], name
    size = int(header[2])
    data = batch[boundary + 1:boundary + 1 + size]
    offset = boundary + size + 2
    normalized = data.replace(b'\r\n', b'\n')
    assert source_bindings[name]['workingSha256'] in {
        hashlib.sha256(value).hexdigest() for value in [data, normalized, normalized.replace(b'\n', b'\r\n')]}, name
assert offset == len(batch)
print(json.dumps(dict(archive='PASS', nodes=3, originalFrames=frames, nativeF3TDispatches=presses,
    normalProcessExits=6, sourceFiles=len(source_bindings), committedSourcesChecked=len(paths), maintainerAcceptance=False)))
