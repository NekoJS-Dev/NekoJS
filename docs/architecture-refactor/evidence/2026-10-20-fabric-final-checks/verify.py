"""Verify the frozen Fabric build and focused declaration evidence without running builds."""
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

base = Path(__file__).resolve().parent
expected = json.loads((base / 'verification.json').read_text(encoding='utf-8'))
archive = base / 'evidence.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest() == expected['archiveSha256']
with zipfile.ZipFile(archive) as frozen:
    assert len(frozen.namelist()) == expected['entries']
    for name, digest in expected['files'].items():
        assert hashlib.sha256(frozen.read(name)).hexdigest() == digest, name
    assert frozen.read('fabric-build.exit').decode('utf-8-sig').strip() == '0'
    log = frozen.read('fabric-build.log').decode('utf-8-sig')
    assert 'BUILD SUCCESSFUL' in log and 'guardLint:' in log
    for node in ('26.1.2-fabric', '26.2.0-fabric'):
        for task in ('build', 'verifyFabricRuntimeArtifact'):
            assert f'> Task :{node}:{task}' in log
        for suite, tests, skipped in (('test', 409, 37), ('platformGateTest', 3, 0)):
            totals = dict.fromkeys(('tests', 'failures', 'errors', 'skipped'), 0)
            prefix = f'junit/{node}/{suite}/'
            for name in frozen.namelist():
                if name.startswith(prefix) and name.endswith('.xml'):
                    root = ET.fromstring(frozen.read(name))
                    for key in totals:
                        totals[key] += int(root.get(key, 0))
            assert totals == dict(tests=tests, failures=0, errors=0, skipped=skipped), (node, suite, totals)
        source_test = ET.fromstring(frozen.read(
            f'junit/{node}/test/TEST-com.tkisor.nekojs.pack.FabricWorldPackStatusTest.xml'))
        regression = [test for test in source_test.findall('testcase')
                      if test.get('name') == 'fabricServerTickRoutesTimersThroughTheRootIncludingIndependentPackages()']
        assert len(regression) == 1
        assert not list(regression[0])
    for stage in ('community-fixture', 'community-types'):
        assert frozen.read(stage + '.exit').decode('utf-8-sig').strip() == '0'
    common = ET.fromstring(frozen.read(
        'junit/common/test/TEST-com.tkisor.nekojs.probe.CommunityProbeTypeScriptFixtureTest.xml'))
    assert tuple(int(common.get(key, 0)) for key in ('tests', 'failures', 'errors', 'skipped')) == (1, 0, 0, 0)
    summary = json.loads(frozen.read('summary.json'))
    assert summary['revision'].startswith('cc55c4ce')
    assert summary['preservedFiles'] == {
        'common\\src\\test\\probe-ts\\generated\\index.d.ts': 'cfdfa54cc5f9214d42d4bb5f1eef3c6984e9dc3414c4952a76236c0a923b2ed2',
        'common\\src\\test\\resources\\nekojs\\dynamic\\dynamic-registry-events.expected.d.ts': 'b9bd89c5e3610f9898ec481e91dd0d5309182282d58dfe5abd72def33903452e',
    }
    bindings = json.loads(frozen.read('production-bindings.json'))
    assert all(len(row['classes']) == 1569 for row in bindings.values())
print('PASS: both Fabric builds, timer wiring regressions, focused declarations, and frozen provenance')
