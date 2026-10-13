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
for manifest_name in ['manifest.json','caller-inputs-manifest.json']:
    data = json.loads((root/manifest_name).read_text(encoding='utf-8'))
    path = root/data['archive']
    assert hashlib.sha256(path.read_bytes()).hexdigest() == data['sha256']
    with zipfile.ZipFile(path) as archive:
        assert sorted(archive.namelist()) == sorted(data['entries'])
        for name, expected in data['entries'].items():
            assert hashlib.sha256(archive.read(name)).hexdigest() == expected,name
with zipfile.ZipFile(root/manifest['archive']) as archive:
    totals = dict(tests=0,failures=0,errors=0,skipped=0,xml=0)
    for name in archive.namelist():
        if '/matrix-canonical-xml/' in name and name.endswith('.xml'):
            tree = ET.fromstring(archive.read(name))
            for key in ['tests','failures','errors','skipped']: totals[key] += int(tree.get(key,0))
            totals['xml'] += 1
    assert totals == summary['matrix'] and totals['failures'] == totals['errors'] == 0
    stubs = [name for name in archive.namelist() if '/.neko_probe/python/' in name and name.endswith('.pyi')]
    assert len(stubs) == summary['totalPythonAstCount'] == 4815
    for name in stubs: ast.parse(archive.read(name).decode('utf-8'),filename=name)
    for row in summary['native']:
        prefix = 'build/'+row['stage']+'/'+row['node']+'/'
        raw = archive.read(prefix+'server-stdout.log')
        assert raw.count(b'IDE-BATCH-NATIVE-ESM-ASSERT-UUID-PASS') == row['markers'] == 2
        assert raw.count(b'[NEKO-4038]')+raw.count(b'[NEKO-4039]') == row['predeclarationFailures']
        assert json.loads(archive.read(prefix+'server-exit.json'))['exitCode'] == row['exitCode'] == 0
        verdict = json.loads(archive.read(prefix+'runtime-verdict.json'))
        assert verdict['verdict'] == 'PASS' and not verdict['errors'] and all(verdict['checks'].values())
    final = 'build/ticket37-interface-console-canonical-installed/'
    assert hashlib.sha256(archive.read(final+'production-source.patch')).hexdigest() == summary['productionPatchSha256']
    for node in summary['ide']['nodes']:
        for result in node['typescript']:
            raw = archive.read(final+'ide/'+node['node']+'-'+result['side']+'-tsc.txt')
            assert len(re.findall(rb'error TS\d+:',raw)) == result['diagnosticCount']
            assert b"'$ParticleOptions' only refers to a type" not in raw
            assert b"Cannot find name 'console'" not in raw
            assert result['exitCode'] == 2 and not result['skipLibCheck'] and result['defaultEditorIncludes']
        particle = archive.read(final+node['node']+'/.neko_probe/typescript/@package/net/minecraft/core/particles/index.d.ts')
        assert b'export const $ParticleOptions: {' in particle
        pyright = json.loads(archive.read(final+'ide/'+node['node']+'-pyright.json'))
        assert pyright['summary'] == node['pyright']['summary'] and node['pyright']['exitCode'] == 1
    for row in summary['delta']:
        assert not row['added']
        assert sum(item['count'] for item in row['removed']) == (2 if row['side']=='server' else 1)
    for row in summary['callers']:
        raw = archive.read('build/ticket37-interface-console-batch/'+row['case']+'-'+row['mode']+'-v2-tsc.txt')
        assert len(re.findall(rb': error TS\d+:',raw)) == row['diagnosticCount']
        assert row['result'] == 'PASS' and not row['skipLibCheck']
    assert summary['exactSourcePerformance'] == 'NOT_RUN' and summary['maintainerAcceptance'] == 'NOT_RECORDED'
print('PASS: hashes, canonical matrix, 4815 ASTs, fifteen native exits, real gap removal, and retained IDE failures')
