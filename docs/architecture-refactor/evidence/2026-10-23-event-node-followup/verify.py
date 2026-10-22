"""Verify the frozen ordinary node gate evidence without launching Minecraft."""
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
    for name, digest in manifest['entries'].items():
        assert hashlib.sha256(archive.read(name)).hexdigest() == digest, name
    total = 0
    for name in archive.namelist():
        if name.startswith('platform-xml/'):
            suite = ET.fromstring(archive.read(name))
            assert all(int(suite.get(key, 0)) == 0 for key in ['failures', 'errors', 'skipped'])
            total += int(suite.get('tests'))
    assert total == 15
    assert b'BUILD SUCCESSFUL' in archive.read('run/event-dependency-node-normal-check.log')
print('PASS: 15 ordinary node gate tests; two modern NeoForge builds. Installed acceptance remains open.')
