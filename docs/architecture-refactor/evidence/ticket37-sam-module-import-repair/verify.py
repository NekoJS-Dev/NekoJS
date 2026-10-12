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
    assert totals == summary['matrix'] == dict(tests=4808, failures=0, errors=0, skipped=271, xml=810)
    assert sha(source.read('production-source.patch')) == summary['productionPatchSha256']
    red_xml = ET.fromstring(source.read('repair/red/IndexFileGeneratorTest.xml'))
    assert red_xml.get('failures') == '1'
    caller = json.loads(source.read('repair/caller-summary.json'))
    assert [row['exitCode'] for row in caller['cases']] == [2, 0]
    assert all(not row['skipLibCheck'] for row in caller['cases'])
    assert caller['negativeExit'] == 2 and caller['negativeDiagnostics'] == 2
    assert "Cannot find name '$Runnable'" in source.read('repair/caller/red/tsc.txt').decode()
    assert not source.read('repair/caller/green/tsc.txt').strip()
    negative = source.read('repair/caller/green/negative-tsc.txt').decode()
    assert len(re.findall(r'error TS\d+:', negative)) == 2
    assert 'error TS2345:' in negative and 'error TS2339:' in negative
    for name in ('@package/com/tkisor/nekojs/probe/index.d.ts',
                 '@package/java/lang/index.d.ts', '@package/java/util/concurrent/index.d.ts'):
        assert source.read('repair/green/types/' + name) == source.read('repair/green-final/types/' + name)
        assert source.read('repair/green/types/' + name) == source.read('repair/caller/green/' + name)
    for check, expected in [('red', 1), ('matrix', 1), ('matrix-green', 1),
                            ('matrix-final', 0), ('npm', 0), ('platform', 0),
                            ('regression-green', 0), ('green', 0)]:
        assert json.loads(source.read('checks/sam-module-import-' + check + '-exit.json').decode('utf-8-sig'))['exitCode'] == expected
    failed = ET.fromstring(source.read('repair/matrix-failed/PackSyncClientTest.xml'))
    assert failed.get('failures') == '1' and 'LOCAL_TRUSTED' in failed.find('testcase/failure').get('message')
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
            assert not side['addedHeaders'] and side['addedCount'] == 0
            count = sum(row['count'] for row in side['removedHeaders'])
            assert count == side['removedCount'] == side['previousHeaders'] - side['currentHeaders']
            removed += count
assert stubs == 1600 and removed == 1002
assert summary['overall'] == 'NOT_COMPLETE' and summary['formalPerformance'] == 'NOT_RUN_FOR_THIS_PATCH'
assert summary['packStageGuardRootCause'] == 'NOT_ESTABLISHED'
print(json.dumps(dict(result='PASS', entries=len(manifest['entries']), matrix=totals, pythonAstStubs=stubs,
                     removedHeaders=removed, wholeTypeScript='FAIL', pyright='FAIL', sha256=manifest['sha256'])))
