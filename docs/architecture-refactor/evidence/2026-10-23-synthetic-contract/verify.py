import ast
import collections
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']


def diagnostics(data):
    result = collections.Counter()
    for line in data.decode('utf-8-sig').splitlines():
        match = re.match(r'(.+)\(\d+,\d+\): error (TS\d+): (.*)', line)
        if match:
            name, code, message = match.groups()
            name = name.replace('\\', '/')
            if '/.neko_probe/' in name:
                name = name.split('/.neko_probe/', 1)[1]
            elif '/nekojs/' in name:
                name = 'nekojs/' + name.split('/nekojs/', 1)[1]
            result[(name, code, message)] += 1
    return result


with zipfile.ZipFile(root / 'evidence.zip') as archive:
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        data = archive.read(name)
        assert len(data) == expected['size'] and hashlib.sha256(data).hexdigest() == expected['sha256'], name
    preparation = json.loads(archive.read('installed/preparation-manifest.json'))
    summary = json.loads(archive.read('installed/ide/summary.json'))
    matrix = json.loads(archive.read('synthetic-contract-matrix/summary.json'))
    assert preparation['sourceRevision'] == summary['sourceRevision'] == matrix['sourceRevision'] == manifest['sourceRevision']
    assert manifest['sourceRevision'].startswith('459c0cd7')
    totals = dict(tests=0, skipped=0, failures=0, errors=0)
    for name in archive.namelist():
        if name.startswith('synthetic-contract-matrix/') and name.endswith('.xml'):
            suite = ET.fromstring(archive.read(name))
            for key in totals:
                totals[key] += int(suite.get(key, '0'))
    assert totals == matrix['total'] == dict(tests=5141, skipped=271, failures=0, errors=0)
    assert ET.fromstring(archive.read('tools/synthetic-contract-red.xml')).get('failures') == '1'
    assert archive.read('tools/synthetic-contract-red-tsc.txt').count(b'error TS') == 4
    assert not archive.read('tools/synthetic-contract-green-tsc.txt')
    assert b'test:probe-synthetic-contract' in archive.read('tools/synthetic-contract-all-types.log')
    before = after = added = removed = stubs = 0
    for node in summary['nodes']:
        name = node['node']
        assert node['pyright']['exitCode'] == 0
        assert node['pyright']['summary']['errorCount'] == node['pyright']['summary']['warningCount'] == 0
        stubs += node['pyright']['stubCount']
        assert json.loads(archive.read(f'installed/{name}/server-exit.json'))['exitCode'] == 0
        verdict = json.loads(archive.read(f'installed/{name}/runtime-verdict.json'))
        assert verdict['verdict'] == 'PASS' and not verdict['errors']
        for side in node['typescript']:
            assert side['exitCode'] == 2 and not side['skipLibCheck'] and not side['compilerCrashed']
            assert side['diagnosticsComplete'] and side['defaultEditorIncludes']
            file = name + '-' + side['side'] + '-tsc.txt'
            current = diagnostics(archive.read('installed/ide/' + file))
            previous = diagnostics(archive.read('previous-ide/' + file))
            assert sum(current.values()) == side['diagnosticCount']
            before += sum(previous.values())
            after += sum(current.values())
            added += sum((current - previous).values())
            removed += sum((previous - current).values())
    delta = json.loads(archive.read('installed/ide/delta-from-88e18d84.json'))
    assert delta['total'] == dict(before=before, after=after, added=added, removed=removed)
    assert (before, after, added, removed, stubs) == (5619, 5575, 0, 44, 1609)
    stub_names = [name for name in archive.namelist() if name.startswith('installed/') and name.endswith('.pyi')]
    assert len(stub_names) == stubs
    for name in stub_names:
        ast.parse(archive.read(name).decode('utf-8'), filename=name)
    for directory in ['closeout-fabric-signature-inspection', 'closeout-fabric-signature-annotations']:
        inspection = json.loads(archive.read(directory + '/inspection-result.json'))
        assert inspection['ready'] and inspection['runnerExit'] == inspection['serverExit']['exitCode'] == 0
        accessors = [line for line in inspection['markers'] if 'OWNED-FABRIC-ACCESSOR ' in line]
        assert len(accessors) == 1
        result = json.loads(accessors[0].split('OWNED-FABRIC-ACCESSOR ', 1)[1])
        assert result == dict(methodType='function', resultSize=0)
    current_log = archive.read('installed/26.1.2-fabric/server-stdout.log')
    assert b'OWNED-FABRIC-ACCESSOR {"methodType":"function","resultSize":0}' in current_log
print('PASS: 5141 tests, five normal installed passes, 1609 stubs; strict TSC FAIL 5575, removed44/added0.')
