import ast
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root/'manifest.json').read_text(encoding='utf-8'))
summary = json.loads((root/'summary.json').read_text(encoding='utf-8'))
archive_path = root/manifest['archive']
assert hashlib.sha256(archive_path.read_bytes()).hexdigest() == manifest['sha256']
with zipfile.ZipFile(archive_path) as archive:
    assert sorted(archive.namelist()) == sorted(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert hashlib.sha256(archive.read(name)).hexdigest() == expected, name
    totals = dict(tests=0,failures=0,errors=0,skipped=0,xml=0)
    for name in archive.namelist():
        if '/matrix-reviewed-xml/' in name and name.endswith('.xml'):
            document = ET.fromstring(archive.read(name))
            for key in ['tests','failures','errors','skipped']: totals[key] += int(document.get(key,0))
            totals['xml'] += 1
    assert totals == summary['matrix'] and totals['failures'] == totals['errors'] == 0
    prefix = 'build/ticket37-final-ide-installed/'
    stubs = [name for name in archive.namelist() if name.startswith(prefix) and name.endswith('.pyi')]
    assert len(stubs) == summary['pythonAstCount'] == 1605
    for name in stubs: ast.parse(archive.read(name).decode('utf-8'),filename=name)
    preparation = json.loads(archive.read(prefix+'preparation-manifest.json'))
    assert hashlib.sha256(archive.read(prefix+'production-source.patch')).hexdigest() == summary['productionPatchSha256']
    for row in summary['native']:
        node = prefix+row['node']+'/'
        raw = archive.read(node+'server-stdout.log')
        assert raw.count(b'IDE-BATCH-NATIVE-ESM-ASSERT-UUID-PASS') == row['esmAssertAndUuidMarkers'] == 2
        assert raw.count(b'[NEKO-4038]')+raw.count(b'[NEKO-4039]') == row['predeclarationFailures']
        assert json.loads(archive.read(node+'server-exit.json'))['exitCode'] == 0
        runtime = json.loads(archive.read(node+'runtime-verdict.json'))
        assert runtime['verdict'] == 'PASS' and not runtime['errors'] and all(runtime['checks'].values())
    assert json.loads(archive.read(prefix+'runner-exit.json'))['exitCode'] == 0
    for node in summary['ide']['nodes']:
        for result in node['typescript']:
            raw = archive.read(prefix+'ide/'+node['node']+'-'+result['side']+'-tsc.txt')
            assert len(re.findall(rb'error TS\d+:',raw)) == result['diagnosticCount']
            assert result['exitCode'] == 2 and not result['skipLibCheck'] and result['defaultEditorIncludes']
        document = json.loads(archive.read(prefix+'ide/'+node['node']+'-pyright.json'))
        assert document['summary'] == node['pyright']['summary']
        assert node['pyright']['exitCode'] == 1
    for caller in summary['callers']:
        name = 'build/ticket37-final-ide-batch/'+caller['case']+'-'+caller['mode']+'-tsc.txt'
        assert len(re.findall(rb': error TS\d+:',archive.read(name))) == caller['diagnosticCount']
        assert caller['result'] == 'PASS' and not caller['skipLibCheck']
    assert summary['maintainerAcceptance'] == 'NOT_RECORDED'
    assert summary['exactSourcePerformance'] == 'NOT_RUN'
print('PASS: immutable evidence hashes, matrix, 1605 ASTs, native ESM/reload/exits, and retained IDE failures')
