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
assert archive.stat().st_size == manifest['bytes'] and sha(archive.read_bytes()) == manifest['sha256']
totals = dict(tests=0, failures=0, errors=0, skipped=0, xml=0)
stubs = removed = 0
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
    assert totals == summary['matrix'] == dict(tests=4810, failures=0, errors=0, skipped=271, xml=810)
    assert sha(source.read('production-source.patch')) == summary['productionPatchSha256']
    for phase, count in [('red', 16), ('red-single-alias', 8), ('red-assigned-members', 4)]:
        assert ET.fromstring(source.read('repair/' + phase + '/NodeModuleTypeDocsTest.xml')).get('failures') == '1'
        assert json.loads(source.read('repair/' + phase + '/java-exit.json').decode('utf-8-sig'))['exitCode'] == 1
        result = json.loads(source.read('repair/' + phase + '/tsc-exit.json'))
        assert result == dict(exitCode=2, diagnostics=count, skipLibCheck=False)
    green = 'repair/green-verified/'
    assert json.loads(source.read(green + 'tsc-exit.json')) == dict(exitCode=0, diagnostics=0, skipLibCheck=False)
    assert not source.read(green + 'tsc.txt').strip()
    assert json.loads(source.read(green + 'callable-exit.json'))['exitCode'] == 0
    assert not source.read(green + 'callable-tsc.txt').strip()
    for prefix in ('negative', 'callable-negative'):
        assert json.loads(source.read(green + prefix + '-exit.json')) == dict(exitCode=2, diagnostics=2)
        assert len(re.findall(r'error TS\d+:', source.read(green + prefix + '-tsc.txt').decode())) == 2
    for name in ('matrix', 'matrix-final', 'npm', 'platform', 'focused-final'):
        assert json.loads(source.read('repair/' + name + '-exit.json').decode('utf-8-sig'))['exitCode'] == 0
    for name in ('runner', 'proof-runner', 'ide-runner'):
        assert json.loads(source.read(name + '-exit.json'))['exitCode'] == 0
    captured = json.loads(source.read('ide/summary.json'))
    assert captured == summary['ide']
    declaration = source.read(green + 'assert.d.ts').decode().strip()
    assert declaration.count('type NekoSyncFn =') == 4
    assert 'constructor(options?: NekoAssertErrorOptions);' in declaration
    for node in summary['nodes']:
        name = node['node']
        assert json.loads(source.read('runtime/' + name + '/server-exit.json'))['exitCode'] == 0
        assert json.loads(source.read('runtime/' + name + '/runtime-verdict.json')) == node['runtime']
        assert node['runtime']['verdict'] == 'PASS'
        manual = source.read('generated/' + name + '/.neko_probe/typescript/@manual/index.d.ts').decode()
        assert declaration in manual
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
            assert not side['addedHeaders'] and side['addedCount'] == 0
            assert sum(row['count'] for row in side['removedHeaders']) == side['removedCount'] == 12
            assert all(row['code'] == 'TS2300' and 'NekoSyncFn' in row['message']
                       or row['code'] == 'TS2304' and 'AssertionError' in row['message']
                       for row in side['removedHeaders'])
            removed += side['removedCount']
assert stubs == 1600 and removed == 120 and summary['rejectedIllegalCallers'] == 4
assert summary['overall'] == 'NOT_COMPLETE' and summary['formalPerformance'] == 'NOT_RUN_FOR_THIS_PATCH'
print(json.dumps(dict(result='PASS', matrix=totals, entries=len(manifest['entries']), pythonAstStubs=stubs,
                     removedHeaders=removed, wholeTypeScript='FAIL', pyright='FAIL', sha256=manifest['sha256'])))
