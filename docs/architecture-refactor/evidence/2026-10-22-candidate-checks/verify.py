"""Verify the frozen checks without interpreting failed gates as successful."""
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root/'manifest.json').read_text(encoding='utf-8'))
archive = root/'evidence.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(archive) as z:
    assert len(z.namelist()) == len(set(z.namelist())) == len(manifest['entries'])
    assert set(z.namelist()) == set(manifest['entries'])
    for name, row in manifest['entries'].items():
        data = z.read(name)
        assert len(data) == row['size'] and hashlib.sha256(data).hexdigest() == row['sha256'],name
    def read(name): return json.loads(z.read(name).decode('utf-8-sig'))
    summary = read('checks/summary.json')
    totals = dict.fromkeys(['tests','skipped','failures','errors'],0)
    xmls = [name for name in z.namelist() if name.startswith('checks/') and name.endswith('.xml')]
    for name in xmls:
        suite = ET.fromstring(z.read(name))
        for key in totals: totals[key] += int(suite.get(key,'0'))
    assert totals == dict(tests=5067,skipped=271,failures=0,errors=0),totals
    assert summary['sourceRevision'] == manifest['sourceRevision']
    original = 'closeout-4405b0b0-installed'
    control = original+'-v2'
    nodes = ['1.21.1','26.1.2','26.2.0','26.1.2-fabric','26.2.0-fabric']
    first = {}
    for node in nodes:
        row = read(original+'/'+node+'/runtime-verdict.json')
        first[node] = row['verdict']
        state = read(original+'/'+node+'/server-exit.json')
        assert state['exitCode'] == 0,state
        if node == '26.1.2':
            assert row['verdict'] != 'PASS' and row['errors']
        else: assert row['verdict'] == 'PASS' and not row['errors']
    row = read(control+'/26.1.2/runtime-verdict.json')
    assert row['verdict'] == 'PASS' and not row['errors']
    assert read(control+'/26.1.2/server-exit.json')['exitCode'] == 0
    control_preparation = read(control+'/preparation.json')
    assert control_preparation['sourceRevision'] == manifest['sourceRevision'][:8]
    original_node = next(row for row in read(original+'/preparation-manifest.json')['nodes'] if row['node'] == '26.1.2')
    assert control_preparation['artifacts'][original_node['artifact']] == original_node['sha256']
    ide = read(original+'/ide/summary.json')
    assert ide['sourceRevision'] == manifest['sourceRevision']
    stub_count = tsc_count = 0
    for node in ide['nodes']:
        py = node['pyright']
        assert py['exitCode'] == py['summary']['errorCount'] == py['summary']['warningCount'] == 0
        stub_count += py['stubCount']
        for row in node['typescript']:
            assert row['exitCode'] == 2 and row['skipLibCheck'] is False and not row['compilerCrashed']
            assert row['defaultEditorIncludes'] and row['diagnosticsComplete']
            raw = z.read(original+'/ide/'+node['node']+'-'+row['side']+'-tsc.txt').decode('utf-8-sig')
            assert len(re.findall(r'error TS\d+:',raw)) == row['diagnosticCount']
            tsc_count += row['diagnosticCount']
    assert stub_count == 1609 and tsc_count == 5867
print(json.dumps(dict(archiveVerification='PASS',tests=totals,firstTrial=first,
    separate261Control='PASS',normalServerExits=6,pythonStubs=stub_count,
    pyright='PASS',strictTsc='FAIL',strictTscDiagnostics=tsc_count)))
