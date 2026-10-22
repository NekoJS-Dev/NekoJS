"""Verify measured scope; a consistent archive does not make the failed IDE gate pass."""
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
m = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == m['archiveSha256']
with zipfile.ZipFile(root / 'evidence.zip') as z:
    assert len(z.namelist()) == len(set(z.namelist())) == len(m['entries'])
    assert set(z.namelist()) == set(m['entries'])
    for name, row in m['entries'].items():
        data = z.read(name)
        assert len(data) == row['size'] and hashlib.sha256(data).hexdigest() == row['sha256'], name
    def read(name):
        return json.loads(z.read(name).decode('utf-8-sig'))
    totals = dict.fromkeys(['tests', 'skipped', 'failures', 'errors'], 0)
    for name in z.namelist():
        if name.startswith('checks/') and name.endswith('.xml'):
            suite = ET.fromstring(z.read(name))
            for key in totals:
                totals[key] += int(suite.get(key, '0'))
    assert totals == dict(tests=5070, skipped=271, failures=0, errors=0)
    assert read('checks/summary.json')['sourceRevision'] == m['sourceRevision']
    preparation = read('installed/preparation-manifest.json')
    assert preparation['sourceRevision'] == m['sourceRevision']
    with zipfile.ZipFile(root.parent / '2026-10-22-source-facts-performance/evidence.zip') as formal:
        measured = json.loads(formal.read('run/preparation.json'))
        installed = next(row for row in preparation['nodes'] if row['node'] == '26.1.2')
        assert installed['sha256'] == measured['artifactSha256']
        assert installed['classBindings'] == measured['classBindings']
    assert preparation['emptyServerPauseSeconds'] == -1
    assert preparation['previousPauseFailureStillUndiagnosed'] is True
    assert read('installed/runner-exit.json')['exitCode'] == 0
    nodes = ['1.21.1', '26.1.2', '26.2.0', '26.1.2-fabric', '26.2.0-fabric']
    for node in nodes:
        row = read('installed/' + node + '/runtime-verdict.json')
        assert row['verdict'] == 'PASS' and not row['errors'] and all(row['checks'].values()), row
        assert read('installed/' + node + '/server-exit.json')['exitCode'] == 0
        log = z.read('installed/' + node + '/server-stdout.log').decode('utf-8', errors='replace')
        assert log.count('[server] OWNED-OBJECT-COLLECTION-INPUTS-PASS') >= 2
        record = next(r for r in preparation['nodes'] if r['node'] == node)
        assert len(record['classBindings']) == {'1.21.1': 1569, '26.1.2': 1640, '26.2.0': 1640,
                                               '26.1.2-fabric': 1583, '26.2.0-fabric': 1583}[node]
    ide = read('installed/ide/summary.json')
    assert ide['sourceRevision'] == m['sourceRevision']
    stubs = diagnostics = 0
    for node in ide['nodes']:
        py = node['pyright']
        assert py['exitCode'] == py['summary']['errorCount'] == py['summary']['warningCount'] == 0
        stubs += py['stubCount']
        for row in node['typescript']:
            assert row['exitCode'] == 2 and row['skipLibCheck'] is False and not row['compilerCrashed']
            assert row['defaultEditorIncludes'] and row['diagnosticsComplete']
            raw = z.read('installed/ide/' + node['node'] + '-' + row['side'] + '-tsc.txt').decode('utf-8-sig')
            assert len(re.findall(r'error TS\d+:', raw)) == row['diagnosticCount']
            diagnostics += row['diagnosticCount']
    assert stubs == 1609 and diagnostics == 5867
print(json.dumps(dict(archiveVerification='PASS', tests=totals, installedNodes=5,
    normalServerExits=5, pythonStubs=stubs, pyright='PASS', strictTsc='FAIL', strictTscDiagnostics=diagnostics)))
