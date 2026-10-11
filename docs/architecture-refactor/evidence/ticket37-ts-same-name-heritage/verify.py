import hashlib
import io
import json
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile

root=Path.cwd().resolve()
out=root/'docs/architecture-refactor/evidence/ticket37-ts-same-name-heritage'
manifest=json.loads((out/'manifest.json').read_text())
sha=lambda data:hashlib.sha256(data).hexdigest()
path=out/manifest['archive']['file']
assert sha(path.read_bytes())==manifest['archive']['sha256']
with ZipFile(path) as archive:
    assert archive.testzip() is None
    assert set(archive.namelist())=={row['path'] for row in manifest['entries']}
    for row in manifest['entries']:
        data=archive.read(row['path'])
        assert len(data)==row['bytes'] and sha(data)==row['sha256']
        assert not row['path'].endswith(('server.properties','.jar','.jfr'))
    source=manifest['sourceRevision']
    assert archive.read('source/test.patch')==subprocess.check_output(['git','diff',manifest['baselineRevision'],source,'--'])
    assert not subprocess.check_output(['git','diff',manifest['baselineRevision'],source,'--','common/src/main','src/main','versions'])
    for name in archive.namelist():
        if name.startswith('source/') and name.endswith('.java'):
            assert archive.read(name)==subprocess.check_output(['git','show',source+':'+name.removeprefix('source/')])
    exits=json.loads(archive.read('direct-exits.json'))
    assert all(exits[key]==0 for key in ['reflection','matrix','npm','postBuildPlatformGates','redCollector','controlCollector'])
    red='diagnosis/ticket37-same-name-heritage-red/'
    rows=json.loads(archive.read(red+'summary.json'))
    assert rows[0]==rows[1] and rows[0]['exitCode']==2
    assert len(rows[0]['diagnostics'])==4 and any('TS2506' in line for line in rows[0]['diagnostics'])
    original=archive.read(red+'child.d.ts')
    controls='diagnosis/ticket37-same-name-heritage-controls/'
    alias=archive.read(controls+'alias-and-all-base-references/child.d.ts')
    expected=original.replace(b'import { $PersistentData }',b'import { $PersistentData as $BasePersistentData }')
    expected=expected.replace(b'extends $PersistentData<string>',b'extends $BasePersistentData<string>')
    expected=expected.replace(b'acceptBase(value: $PersistentData<string>)',b'acceptBase(value: $BasePersistentData<string>)')
    assert alias==expected
    control_rows=json.loads(archive.read(controls+'summary.json'))
    assert [row['exitCode'] for row in control_rows]==[2,2,0]
    extra=json.loads(archive.read('diagnosis/ticket37-same-name-heritage-extra-controls/summary.json'))
    assert extra['minimal']['exitCode']==2 and len(extra['minimal']['diagnostics'])==1
    assert sum(': error TS' in line for line in extra['modelNegative']['diagnostics'])==3
    for name in ['base.d.ts','caller.ts','tsconfig.json']:
        assert archive.read(red+name)==archive.read(controls+'alias-and-all-base-references/'+name)
    with ZipFile(io.BytesIO(archive.read('diagnosis/ticket37-same-name-heritage-focused/xml.zip'))) as xml:
        suites=[ET.fromstring(xml.read(name)) for name in xml.namelist()]
        assert len(suites)==1 and suites[0].get('tests')=='2' and suites[0].get('failures')=='0'
        assert any('actualHostInheritance' in test.get('name','') for test in suites[0].findall('testcase'))
    assert manifest['matrix']['total']==dict(xml=803,tests=4749,skipped=271,failures=0,errors=0)
    assert b'BUILD SUCCESSFUL in 1m 42s' in archive.read('runner/same-name-heritage-matrix.txt')
    assert len(manifest['nodes'])==5
    for row in manifest['nodes']:
        for side in ['server','startup']:
            report=archive.read(f"installed/{row['node']}/{side}-tsc.txt")
            assert sum(b'TS2506' in line and b"'$PersistentDataJS'" in line for line in report.splitlines())==1
    assert manifest['productionChanges'] is False and manifest['productionRepair']=='NOT IMPLEMENTED'
record=dict(archiveSha256=manifest['archive']['sha256'],fullHashReadback=True,sourcePatchMatches=True,
    repeatedActualRed=True,exactThreeNameSubstitutionsOnly=True,originalCallerModulesAndConfigPreserved=True,
    minimalRedAndThreeNegativeTypeRejects=True,productionHostAccessRuntimeFact=True,
    fiveActualInstalledSelfHeritageErrorsRetained=True,matrixTests=4749,matrixFailures=0,
    productionRepair='NOT IMPLEMENTED',fullIde='FAIL',overallTickets='NOT COMPLETE',maintainerAcceptance='NOT RECORDED')
assert not (out/'archive-verification.json').exists()
(out/'archive-verification.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
print(json.dumps(record))
