"""Verify all frozen ordinary modern-node XML and artifact binding records."""
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
    for name, entry in manifest['entries'].items():
        data = archive.read(name)
        assert len(data) == entry['size'] and hashlib.sha256(data).hexdigest() == entry['sha256'], name
    totals = dict(tests=0, skipped=0, failures=0, errors=0)
    for name in archive.namelist():
        if name.startswith('xml/'):
            suite = ET.fromstring(archive.read(name))
            for key in totals:
                totals[key] += int(suite.get(key, 0))
    assert totals == dict(tests=2212, skipped=236, failures=0, errors=0)
    summary = json.loads(archive.read('summary.json'))
    assert summary['productionRevision'].startswith('b47734c6') and not summary['maintainerAcceptance']
    for node in summary['tests']:
        bindings = json.loads(archive.read('bindings/' + node + '.json'))
        assert len(bindings) == (1583 if 'fabric' in node else 1640)
print('Verified: four ordinary modern builds, 2212 tests / 236 skipped / zero failures; TCP and release gates remain separate.')
