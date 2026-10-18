"""Verify archived setup failures, build results, and actual client login evidence."""
import hashlib
import json
import re
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
archive = root / 'evidence.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']
assert archive.stat().st_size == manifest['archiveBytes']
with zipfile.ZipFile(archive) as data:
    assert set(data.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert hashlib.sha256(data.read(name)).hexdigest() == expected, name
        assert not name.endswith(('private.pk8', 'server.properties'))
    red = ET.fromstring(data.read('run/world-client-wiring-red.xml'))
    green = ET.fromstring(data.read('run/world-client-wiring-green.xml'))
    assert int(red.attrib['failures']) == 1
    assert 'after login' in red.find('testcase/failure').attrib['message']
    assert int(green.attrib['failures']) == int(green.attrib['errors']) == 0
    assert any('localWorldClientScriptsReloadAfterLogin' in case.attrib['name'] for case in green.findall('testcase'))
    builds = data.read('run/world-client-builds.log').decode('utf-8-sig', errors='replace')
    assert 'BUILD SUCCESSFUL' in builds and '> Task :guardLint' in builds
    counts = {}
    for node in ['1.21.1', '26.1.2', '26.2.0']:
        counts[node] = dict(tests=0, failures=0, errors=0, skipped=0)
        files = [name for name in data.namelist() if name.startswith('node-results/' + node + '/') and name.endswith('.xml')]
        assert files, node
        for name in files:
            result = ET.fromstring(data.read(name))
            for key in counts[node]:
                counts[node][key] += int(result.attrib.get(key, 0))
        assert counts[node]['failures'] == counts[node]['errors'] == 0, counts[node]
    before = data.read('run/client-launch-5.log').decode('utf-8-sig', errors='replace')
    after = data.read('run/client-launch-6.log').decode('utf-8-sig', errors='replace')
    expected = 'NEKO_WORLD_ACCEPTANCE side=client world=A pack=sessionprobe'
    assert expected not in before
    assert re.search(re.escape(expected) + r' activation=\d+ event=entry count=1', after)
    assert 'side=client world=A pack=sessionconsumer event=capability world=A count=1' in after
    assert 'side=server world=A pack=sessionprobe' in before
    assert '[NEKO-1017] CLIENT reload after local world pack activation failed' not in after
    binding = json.loads(data.read('run/mcp-gui-compat-v3/binding.json'))
    assert binding['controlModeChecksChanged'] is False
    assert all(step['exitCode'] == 0 for step in binding['steps'])
    assert binding['patchedSha256'] == '920692b6702c0ac5882bc446db4c89f1840ee32cc06004ea536e86fe04ffd97f'
    print(json.dumps({'status': 'PASS', 'nodeResults': counts,
                      'scope': 'setup repair and automatic A CLIENT entry only; A-B-A/network not asserted'}, indent=2))
