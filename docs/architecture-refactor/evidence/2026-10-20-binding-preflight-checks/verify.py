"""Recompute the frozen check and component results without a build or game."""
import hashlib
import json
import math
import statistics
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
expected = json.loads((root/'verification.json').read_text(encoding='utf-8'))
archive = root/'evidence.zip'
assert archive.stat().st_size == expected['archiveBytes']
assert hashlib.sha256(archive.read_bytes()).hexdigest() == expected['archiveSha256']
with zipfile.ZipFile(archive) as evidence:
    names = evidence.namelist()
    assert len(names) == len(set(names)) == expected['entries']
    manifest = json.loads(evidence.read('manifest.json'))
    assert set(names) == set(manifest) | {'manifest.json'}
    for name, digest in manifest.items():
        assert hashlib.sha256(evidence.read(name)).hexdigest() == digest, name
    for name in ['focused', 'common-check', 'runtime-artifacts']:
        result = json.loads(evidence.read('run/'+name+'-result.json'))
        assert result['commit'] == expected['revision'] and result['exitCode'] == 0
    for phase, wanted in expected['counts'].items():
        counts = dict(tests=0, failures=0, errors=0, skipped=0)
        for name in names:
            if name.startswith('run/'+phase+'/') and name.endswith('.xml'):
                suite = ET.fromstring(evidence.read(name))
                for key in counts:
                    counts[key] += int(suite.attrib.get(key, 0))
        assert counts == wanted and counts['failures'] == counts['errors'] == 0
    binding = json.loads(evidence.read('binding.json'))
    assert binding['revision'] == expected['revision'] and binding['preservedFilesUnchanged']
    assert not binding['gameLaunched'] and not binding['formalPerformanceMeasured']
    for name, digest in binding['sourceSha256'].items():
        assert hashlib.sha256(evidence.read('source/'+name)).hexdigest() == digest
    for name, digest in binding['classSha256'].items():
        assert hashlib.sha256(evidence.read('classes/'+name+'.class')).hexdigest() == digest
        baseline = evidence.read('control/baseline-'+name+'.java')
        assert baseline.replace(name.encode(), ('Original'+name).encode()) == evidence.read('control/Original'+name+'.java')
    observations = json.loads(evidence.read('control/observations.json'))
    assert observations['diagnosticOnly'] and not observations['formalPerformanceMeasured']
    assert observations['regex']['equivalenceCases'] == 45
    assert observations['regex']['diagnosticsPerSix'] == 26
    assert observations['binding']['equivalentInstalls'] == observations['binding']['contexts'] == 100
    assert observations['binding']['syntheticBindings'] == 72
    assert observations['binding']['knownGlobals'] == 155
    for component in ['regex', 'binding']:
        value = observations[component]
        divisor = value.get('roundsPerArm', 1)
        pairs = value['pairs']
        assert len(value['warmups']) == 20 and len(pairs) == 30
        first_pair = 21 if component == 'binding' else 1
        assert [p['pair'] for p in pairs] == list(range(first_pair, first_pair+30))
        result = expected['components'][component]
        for arm in ['before', 'after']:
            for key in ['wallNs', 'cpuNs', 'allocatedBytes']:
                actual = statistics.mean(p[arm][key]/divisor for p in pairs)
                assert math.isclose(actual, result['means'][arm][key])
        saved = [(p['before']['wallNs']-p['after']['wallNs'])/divisor for p in pairs]
        assert math.isclose(statistics.mean(saved), result['wallSavedNs'])
        assert statistics.median(saved) == result['pairedMedianSavedNs']
        assert sum(s > 0 for s in saved) == result['pairedWallWins']
    for index in [0, 1]:
        assert json.loads(evidence.read('control/'+str(index)+'-result.json'))['exitCode'] == 0
    log = evidence.read('run/runtime-artifacts.log').decode('utf-8-sig')
    assert 'BUILD SUCCESSFUL' in log and 'FAILED' not in log
    for node in ['1.21.1', '26.1.2', '26.2.0']:
        assert '> Task :'+node+':verifyNeoForgeRuntimeArtifact' in log
        assert '> Task :'+node+':verifyDevModSourceSets' in log
    for node in ['26.1.2-fabric', '26.2.0-fabric']:
        assert '> Task :'+node+':verifyFabricRuntimeArtifact' in log
print(json.dumps(dict(evidenceVerification='PASS', counts=expected['counts'], formalPerformanceMeasured=False)))
