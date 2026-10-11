import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

directory=Path(__file__).resolve().parent
manifest=json.loads((directory/'manifest.json').read_text())
sha=lambda data:hashlib.sha256(data).hexdigest()
path=directory/manifest['archive']['file']
assert sha(path.read_bytes())==manifest['archive']['sha256']
assert path.stat().st_size==manifest['archive']['bytes']
with ZipFile(path) as archive:
    assert archive.testzip() is None
    assert len(archive.namelist())==len(manifest['entries'])==manifest['archive']['entries']
    for row in manifest['entries']:
        data=archive.read(row['path'])
        assert len(data)==row['bytes'] and sha(data)==row['sha256']
    patch=archive.read('installed/production-source.patch')
    assert sha(patch)==manifest['productionPatchSha256']
    source=manifest['source']
    for name,expected in {**source['files'],**source['tests']}.items():
        assert sha(archive.read('control/ticket37-same-name-import-source/'+name))==expected
    classes=manifest['artifactSourceBinding']['nodes']
    assert len(classes)==5 and all(row['classHashes']==classes[0]['classHashes'] for row in classes)
    assert len(classes[0]['classHashes'])==14
    assert manifest['matrix']['total']==dict(xml=803,tests=4755,skipped=271,failures=0,errors=0)
    for node in manifest['fullIDE']['nodes']:
        assert node['pyright']['exitCode']==1
        assert all(project['exitCode']==2 and project['skipLibCheck'] is False and project['defaultEditorIncludes'] for project in node['typescript'])
        direct=json.loads(archive.read('installed/'+node['node']+'/server-exit.json'))
        assert direct['exitCode']==0
    for row in manifest['comparison']['nodes']:
        assert row['python']['removed']==row['python']['added']==0
        assert all(project['raw']['added']==0 for project in row['typescript'])
    failed=[(row['node'],item['file']) for row in manifest['orderAudit']['nodes'] for item in row['changed'] if not item['normalizedStructurePreserved']]
    assert len(failed)==4 and all(node=='1.21.1' and '/managed/' in name for node,name in failed)
    controls=manifest['unchangedJarOrderControl']['managedOrderComparisons']
    assert len(controls)==4 and all(row['unchangedJarChangesOverloadOrder'] for row in controls)
    assert manifest['remaining']['overall']=='NOT COMPLETE' and manifest['remaining']['currentSourcePerformance']=='NOT RUN'
result=dict(status='PASS',purpose='Archive integrity and recorded scoped verdicts; full IDE/order/performance/overall remain open',
    entries=manifest['archive']['entries'],sha256=manifest['archive']['sha256'],orderFailuresRetained=failed)
with (directory/'archive-verification.json').open('x',encoding='utf-8') as stream:json.dump(result,stream,indent=2)
print(json.dumps(result))
