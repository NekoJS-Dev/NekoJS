import ast
import hashlib
import json
import re
import zipfile
from pathlib import Path

root=Path(__file__).resolve().parent
manifest=json.loads((root/'manifest.json').read_text())
summary=json.loads((root/'runtime-summary.json').read_text())
sha=lambda data:hashlib.sha256(data).hexdigest()
archive=root/manifest['archive']
assert sha(archive.read_bytes())==manifest['sha256'] and archive.stat().st_size==manifest['bytes']
pyi_count=0
with zipfile.ZipFile(archive) as source:
    assert source.testzip() is None
    assert set(source.namelist())=={row['name'] for row in manifest['entries']}
    for row in manifest['entries']:
        data=source.read(row['name'])
        assert len(data)==row['bytes'] and sha(data)==row['sha256']
        if row['name'].endswith('.pyi'):
            ast.parse(data.decode('utf-8'),filename=row['name']); pyi_count+=1
    assert json.loads(source.read('runner-exit.json'))['exitCode']==0
    assert json.loads(source.read('proof-runner-exit.json'))['exitCode']==0
    assert json.loads(source.read('ide-runner-exit.json'))['exitCode']==0
    captured=json.loads(source.read('ide/summary.json'))
    assert captured==summary['ide']
    for node in summary['nodes']:
        name=node['node']
        assert json.loads(source.read('runtime/'+name+'/server-exit.json'))['exitCode']==0
        assert json.loads(source.read('runtime/'+name+'/runtime-verdict.json'))==node['runtime']
        assert node['runtime']['verdict']=='PASS'
        ide=next(row for row in captured['nodes'] if row['node']==name)
        assert ide['pyright']['exitCode']==1 and ide['pyright']['summary']['errorCount']>0
        assert ide['pyright']['stubCount']==node['pythonAstStubs']
        for side in ide['typescript']:
            text=source.read('ide/'+name+'-'+side['side']+'-tsc.txt').decode('utf-8-sig')
            assert len(re.findall(r'error TS\d+:',text))==side['diagnosticCount']
            assert side['exitCode']==2 and side['defaultEditorIncludes'] and not side['skipLibCheck']
            config=json.loads(source.read('ide/'+name+'-'+side['side']+'-tsconfig.json'))
            assert set(config)=={'extends','compilerOptions'} and config['compilerOptions']==dict(skipLibCheck=False,noEmit=True)
assert pyi_count==1600 and sum(row['pythonAstStubs'] for row in summary['nodes'])==1600
assert summary['wholeTypeScript']=='FAIL' and summary['pyright']=='FAIL' and summary['overall']=='NOT_COMPLETE'
print(json.dumps(dict(result='PASS',entries=len(manifest['entries']),pythonAstStubs=pyi_count,sha256=manifest['sha256'])))
