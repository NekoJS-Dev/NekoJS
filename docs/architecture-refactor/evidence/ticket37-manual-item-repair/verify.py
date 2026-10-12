import ast
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
summary = json.loads((root / 'runtime-summary.json').read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()
archive = root / manifest['archive']
assert sha(archive.read_bytes()) == manifest['sha256'] and archive.stat().st_size == manifest['bytes']
totals = dict(tests=0, failures=0, errors=0, skipped=0, xml=0)
stubs = 0
with ZipFile(archive) as source:
    assert source.testzip() is None
    assert set(source.namelist()) == {row['name'] for row in manifest['entries']}
    for row in manifest['entries']:
        data = source.read(row['name'])
        assert len(data) == row['bytes'] and sha(data) == row['sha256']
        if row['name'].endswith('.pyi'):
            ast.parse(data.decode('utf-8'), filename=row['name'])
            stubs += 1
        if row['name'].startswith('repair/matrix-xml/') and row['name'].endswith('.xml'):
            document = ET.fromstring(data)
            totals['xml'] += 1
            for key in ('tests', 'failures', 'errors', 'skipped'):
                totals[key] += int(document.get(key, 0))
    assert totals == summary['matrix'] == dict(tests=4806, failures=0, errors=0, skipped=271, xml=810)
    assert sha(source.read('production-source.patch')) == summary['productionPatchSha256']
    red = json.loads(source.read('repair/red/caller/result.json'))
    green = json.loads(source.read('repair/green/caller/result.json'))
    assert red['legalExit'] == 2 and green['legalExit'] == 0 and not green['skipLibCheck']
    assert green['negativeExit'] == 2 and green['negativeDiagnostics'] == 3
    assert not source.read('repair/green/caller/legal-tsc.txt').strip()
    for phase in ('red', 'green'):
        captured = source.read('repair/' + phase + '/26.1.2/registry.d.ts')
        assert sha(captured) == (red if phase == 'red' else green)['manualSha256']
    assert json.loads(source.read('checks/manual-item-red-exit.json').decode('utf-8-sig'))['exitCode'] == 1
    for check in ('matrix', 'npm', 'platform', 'green', 'restricted-green'):
        assert json.loads(source.read('checks/manual-item-' + check + '-exit.json').decode('utf-8-sig'))['exitCode'] == 0
    for name in ('runner', 'proof-runner', 'ide-runner'):
        assert json.loads(source.read(name + '-exit.json'))['exitCode'] == 0
    captured = json.loads(source.read('ide/summary.json'))
    assert captured == summary['ide']
    for node in summary['nodes']:
        name = node['node']
        assert json.loads(source.read('runtime/' + name + '/server-exit.json'))['exitCode'] == 0
        assert json.loads(source.read('runtime/' + name + '/runtime-verdict.json')) == node['runtime']
        assert node['runtime']['verdict'] == 'PASS'
        ide = next(row for row in captured['nodes'] if row['node'] == name)
        assert ide['pyright']['exitCode'] == 1 and ide['pyright']['summary']['errorCount'] > 0
        for side in ide['typescript']:
            text = source.read('ide/' + name + '-' + side['side'] + '-tsc.txt').decode('utf-8-sig')
            assert len(re.findall(r'error TS\d+:', text)) == side['diagnosticCount']
            assert side['exitCode'] == 2 and side['defaultEditorIncludes'] and not side['skipLibCheck']
            config = json.loads(source.read('ide/' + name + '-' + side['side'] + '-tsconfig.json'))
            assert set(config) == {'extends', 'compilerOptions'}
            assert config['compilerOptions'] == dict(skipLibCheck=False, noEmit=True)
        for side in node['strictHeaderComparison']:
            assert not side['addedHeaders']
            assert sum(row['count'] for row in side['removedHeaders']) == (0 if 'fabric' in name else 2)
            assert all(row['code'] == 'TS2300' and "'item'" in row['message'] for row in side['removedHeaders'])
assert stubs == 1600 and summary['overall'] == 'NOT_COMPLETE'
print(json.dumps(dict(result='PASS', entries=len(manifest['entries']), matrix=totals, pythonAstStubs=stubs,
                     wholeTypeScript='FAIL', pyright='FAIL', sha256=manifest['sha256'])))
