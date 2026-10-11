import hashlib
import json
import subprocess
from pathlib import Path
from zipfile import ZipFile

root = Path.cwd().resolve()
out = root/'docs/architecture-refactor/evidence/ticket37-ts-constructor-member-repair'
trial = root/'build/ticket37-constructor-member-installed'
record = json.loads((out/'manifest.json').read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()
path = out/record['archive']['file']
assert sha(path.read_bytes()) == record['archive']['sha256'] and path.stat().st_size == record['archive']['bytes']
with ZipFile(path) as archive:
    assert archive.testzip() is None and len(archive.namelist()) == len(set(archive.namelist())) == record['archive']['entries']
    for row in record['entries']:
        data = archive.read(row['path'])
        assert len(data) == row['bytes'] and sha(data) == row['sha256']
    assert archive.read('source/repair.patch') == subprocess.check_output(['git','diff','--abbrev=9',record['sourceBase'],'--',*record['sourceFiles']])
    for name, expected in record['sourceFiles'].items():
        assert sha((root/name).read_bytes()) == expected
        assert (root/name).read_bytes().replace(b'\r\n',b'\n') == subprocess.check_output(['git','show',record['sourceRevision']+':'+name]).replace(b'\r\n',b'\n')
    assert json.loads(archive.read('source/baseline-missing.json')) == ['common/src/test/java/com/tkisor/nekojs/probe/ir/TypeScriptConstructorMemberTest.java']
    assert record['matrix'] == json.loads(archive.read('control/ticket37-constructor-member-matrix/summary.json'))
    assert record['matrix']['total'] == dict(xml=802,tests=4747,skipped=271,failures=0,errors=0)
    assert record['comparison'] == json.loads(archive.read('installed/ide/comparison-60139fff.json'))
    assert len(record['comparison']['nodes']) == 5
    for row in record['comparison']['nodes']:
        expected = 1 if row['node'].endswith('-fabric') else 4
        assert all(item['removed'] == expected and item['added'] == 0 for item in row['typescript'])
        assert row['python']['removed'] == row['python']['added'] == 0
        assert all(not item['missing'] and not item['added'] for item in row['generation'])
    assert json.loads(archive.read('installed/ide/typescript-order-audit.json'))['status'] == 'PASS'
    for name in ['ticket37-constructor-member-regression-red','ticket37-constructor-member-isolated-regression-red']:
        summary = json.loads(archive.read('control/'+name+'/summary.json'))
        assert summary['total']['tests'] == 3 and summary['total']['failures'] == 2
    assert record['strictCallers']['red'][0]['exitCode'] == 2
    assert record['strictCallers']['green-confirmed'][0]['exitCode'] == 0
    negative = record['strictCallers']['green-confirmed'][1]
    assert negative['exitCode'] == 2 and sum(': error TS' in line for line in negative['diagnostics']) == 6
    assert b'AssertionError' in archive.read('runner/constructor-member-caller-green.txt')
    assert b'FileNotFoundError' in archive.read('runner/constructor-member-repair-archive.txt')
    assert len(record['runtime']) == 5
    for row in record['runtime']:
        assert row == json.loads(archive.read('installed/'+row['node']+'/runtime-verdict.json'))
        assert not row['errors'] and all(row['checks'].values())
        assert json.loads(archive.read('installed/'+row['node']+'/server-exit.json'))['exitCode'] == 0
    assert all(row['syntaxDiagnostics'] == 0 for row in record['parse']['nodes'])
relative = 'com/tkisor/nekojs/probe/ir/TypeScriptClassRenderer.class'
compiled = (root/'common/build/classes/java/main'/relative).read_bytes()
for row in record['artifacts']:
    artifacts = list((trial/row['node']/'mods').glob('nekojs-*.jar'))
    assert len(artifacts) == 1 and sha(artifacts[0].read_bytes()) == row['sha256']
    with ZipFile(artifacts[0]) as jar: assert jar.read(relative) == compiled
console = root/'build/ticket37-config-sync-repair/constructor-member-repair-archive-confirmed.txt'
assert json.loads(console.read_text(encoding='utf-8-sig'))['archive'] == record['archive']
assert not (out/'archive-console.txt').exists()
(out/'archive-console.txt').write_bytes(console.read_bytes())
result = dict(status='PASS',fullEntryHashReadback=True,committedSourcePatchMatch=True,actualFiveArtifactClassBytes=sha(compiled),
    redRuntimeAndNegativeControlsRetained=True,completedComparisonsAndExits=True,
    collectorMistakesRetained=True,wholeIDE='FAIL',sourcePerformance='NOT RUN in this archive',
    maintainerAcceptance='NOT RECORDED',overall='NOT COMPLETE')
(out/'archive-verification.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
