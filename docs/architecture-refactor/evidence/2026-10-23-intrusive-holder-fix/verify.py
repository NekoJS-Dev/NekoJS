"""Verify the committed fix's focused red/green checks without claiming TCP acceptance."""
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
    count = 0
    for node in ['26.1.2', '26.2.0', '26.1.2-fabric', '26.2.0-fabric']:
        suite = ET.fromstring(archive.read('focused/' + node + '.xml'))
        assert all(int(suite.get(key, 0)) == 0 for key in ['failures', 'errors', 'skipped'])
        count += int(suite.get('tests'))
    assert count == 16
    red = ET.fromstring(archive.read('run/intrusive-holder-refreeze-red.xml'))
    assert int(red.get('failures')) == 3 and int(red.get('tests')) == 4
    assert b'BUILD SUCCESSFUL' in archive.read('run/intrusive-holder-final-focused.log')
    summary = json.loads(archive.read('summary.json'))
    assert summary['reviews']['maintainerAcceptance'] is False
print('Verified: old implementation 3 failures; four nodes 16 focused passes; TCP recovery remains separately unverified.')
