"""Verify the committed fix, regression results, and intentionally limited acceptance scope."""
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
sha = lambda data: hashlib.sha256(data).hexdigest()
assert sha((root / 'evidence.zip').read_bytes()) == manifest['archiveSha256']
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    assert len(archive.namelist()) == len(set(archive.namelist())) == len(manifest['entries'])
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, row in manifest['entries'].items():
        data = archive.read(name)
        assert len(data) == row['size'] and sha(data) == row['sha256'], name
    summary = json.loads(archive.read('summary.json'))
    assert summary['revision'] == manifest['revision']
    totals = dict.fromkeys(['tests', 'skipped', 'failures', 'errors'], 0)
    for name in archive.namelist():
        if name.startswith('common-xml/'):
            suite = ET.fromstring(archive.read(name))
            for key in totals:
                totals[key] += int(suite.get(key, '0'))
    assert totals == summary['common'] == dict(tests=2416, skipped=4, failures=0, errors=0)
    for name in ['event-dependency-red.xml', 'event-key-red.xml']:
        suite = ET.fromstring(archive.read('run/' + name))
        assert int(suite.get('tests')) == int(suite.get('failures')) == 1
    focused = 0
    for name in ['event-dependency-final-focused.xml', 'event-dependency-final-golden.xml']:
        suite = ET.fromstring(archive.read('run/' + name))
        assert int(suite.get('failures')) == int(suite.get('errors')) == int(suite.get('skipped')) == 0
        focused += int(suite.get('tests'))
    assert focused == summary['focusedTests'] == 12
    assert summary['commonCheckExitCode'] == summary['guardLintExitCode'] == summary['fullProbeTypesExitCode'] == 0
    assert b'BUILD SUCCESSFUL' in archive.read('run/event-dependency-final-common-check.log')
    assert b'guardLint:' in archive.read('run/event-dependency-final-common-check.log')
    assert b'BUILD FAILED' in archive.read('run/event-dependency-common-check.log')
    assert b'error TS' not in archive.read('run/event-dependency-all-types.log')
    config = json.loads(archive.read('strict-fixture/tsconfig.json'))
    assert config['compilerOptions']['strict'] and config['compilerOptions']['skipLibCheck'] is False
    for name, binding in summary['sourceBindings'].items():
        assert sha(archive.read('sources/' + name)) == binding['workingSha256']
    assert summary['standards']['finalFindings'] == summary['spec']['finalFindings'] == 0
    assert summary['reviewersReadOnly'] and not summary['maintainerAcceptance']
    assert summary['fullInstalledIdeNotRerun'] and summary['performanceNotPassed']
print(json.dumps(dict(archiveVerification='PASS', common=totals, focusedTests=focused,
                     fullProbeTypes='PASS', installedFullIde='NOT_RERUN', releaseAcceptance='OPEN')))
