import hashlib
import json
import subprocess
from pathlib import Path
from zipfile import ZipFile

root = Path.cwd().resolve()
out = root/'docs/architecture-refactor/evidence/ticket37-node-util-predicate'
trial = root/'build/ticket37-node-util-predicate-installed'
record = json.loads((out/'manifest.json').read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()
path = out/record['archive']['file']
assert sha(path.read_bytes()) == record['archive']['sha256'] and path.stat().st_size == record['archive']['bytes']
with ZipFile(path) as archive:
    assert archive.testzip() is None
    assert len(archive.namelist()) == len(set(archive.namelist())) == record['archive']['entries']
    for row in record['entries']:
        data = archive.read(row['path'])
        assert len(data) == row['bytes'] and sha(data) == row['sha256']
    assert archive.read('source/repair.patch') == subprocess.check_output(['git','diff','--abbrev=9',record['sourceBase'],'--',*record['sourceFiles']])
    for name, expected in record['sourceFiles'].items():
        assert sha((root/name).read_bytes()) == expected
        assert (root/name).read_bytes().replace(b'\r\n',b'\n') == subprocess.check_output(['git','show',record['sourceRevision']+':'+name]).replace(b'\r\n',b'\n')
    assert record['matrix'] == json.loads(archive.read('control/ticket37-node-util-predicate-matrix/summary.json'))
    assert record['firstMatrix'] == json.loads(archive.read('control/ticket37-node-util-predicate-matrix-failed/summary.json'))
    assert 'BUILD FAILED' in archive.read('runner/node-util-predicate-full-matrix.txt').decode('utf-8-sig')
    assert 'BUILD SUCCESSFUL' in archive.read('runner/node-util-predicate-full-matrix-confirmation.txt').decode('utf-8-sig')
    assert record['comparison'] == json.loads(archive.read('installed/ide/comparison-f84.json'))
    assert len(record['comparison']['nodes']) == 5
    for row in record['comparison']['nodes']:
        assert all(item['removed'] == 2 and item['added'] == 0 for item in row['typescript'])
        assert row['python']['removed'] == row['python']['added'] == 0
    erasure = record['erasure']
    assert not erasure['rawBytesEqual'] and erasure['fullJavaScriptAstEqual'] and erasure['changedReturnNegativeControlRejected']
    for phase in ['red','green']:
        data = archive.read('control/ticket37-node-util-predicate-'+phase+'/util-erased.js')
        assert sha(data) == erasure[phase]['sha256'] and len(data) == erasure[phase]['bytes']
    assert record['strictCallers']['red'][0]['exitCode'] == 2 and record['strictCallers']['green'][0]['exitCode'] == 0
    assert record['strictCallers']['green'][1]['exitCode'] == 2
    assert len(record['runtime']) == 5
    for row in record['runtime']:
        assert row == json.loads(archive.read('installed/'+row['node']+'/runtime-verdict.json'))
        assert not row['errors'] and all(row['checks'].values())
        assert json.loads(archive.read('installed/'+row['node']+'/server-exit.json'))['exitCode'] == 0
    assert all(row['syntaxDiagnostics'] == 0 for row in record['parse']['nodes'])
for row in record['artifacts']:
    artifacts = list((trial/row['node']/'mods').glob('nekojs-*.jar'))
    assert len(artifacts) == 1 and sha(artifacts[0].read_bytes()) == row['sha256']
    with ZipFile(artifacts[0]) as jar:
        assert jar.read('nekojs/node/modules/util.ts') == (root/'common/src/main/resources/nekojs/node/modules/util.ts').read_bytes()
console = root/'build/ticket37-config-sync-repair/node-util-predicate-archive.txt'
assert json.loads(console.read_text(encoding='utf-8-sig'))['archive'] == record['archive']
assert not (out/'archive-console.txt').exists()
(out/'archive-console.txt').write_bytes(console.read_bytes())
result = dict(status='PASS', archive=record['archive'], completeEntryHashes=True, committedSourcePatchMatch=True,
    actualFiveArtifactResourceBytes=True, firstFailureAndCompletedConfirmationRetained=True,
    allFiveDirectRuntimeExitsAndCompleteComparisons=True, wholeIde='FAIL', filesystemRootCause='UNRESOLVED',
    erasureRawBytesEqual=False, erasureAstEqual=True, currentSourcePerformance='NOT RUN in this archive',
    maintainerAcceptance='NOT RECORDED', overall='NOT COMPLETE')
(out/'archive-verification.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
