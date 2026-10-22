"""Verify preserved raw-use-site regression evidence and its explicit acceptance limits."""
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, value in manifest['entries'].items():
        data = archive.read(name)
        assert len(data) == value['size'] and hashlib.sha256(data).hexdigest() == value['sha256'], name
    summary = json.loads(archive.read('summary.json'))
    assert summary['productionRevision'].startswith('c0bc2dbe') and not summary['maintainerAcceptance']
    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    for name in archive.namelist():
        if name.startswith('common-xml/'):
            suite = ET.fromstring(archive.read(name))
            for key in counts:
                counts[key] += int(suite.attrib.get(key, 0))
    assert counts == summary['common'] == dict(tests=2420, failures=0, errors=0, skipped=4)
    raw = 'TEST-com.tkisor.nekojs.probe.ir.TypeScriptRawOutputTest.xml'
    assert ET.fromstring(archive.read('common-xml/' + raw)).attrib['failures'] == '0'
    red = ET.fromstring(archive.read('tools/raw-output-red.xml'))
    assert red.attrib['tests'] == '4' and red.attrib['failures'] == '3'
    assert archive.read('tools/raw-output-tsc-red-plain.log').count(b'error TS2314:') == 11
    assert archive.read('tools/raw-output-comparable-tsc-red-v2.log').count(b'error TS2344:') == 2
    for mode in summary['strictModes']:
        assert not archive.read('tools/raw-output-final-tsc-' + mode + '.log')
    assert summary['fullTargetedTypeChain'] == 'PASS'
    assert 'NOT RUN' in summary['fullInstalledIde'] and 'NOT RUN' in summary['performance']
print('PASS: raw regression red/green, 2420 common tests, strict targeted chain; installed IDE and performance remain open.')
