import hashlib
import json
import subprocess
from pathlib import Path
from zipfile import ZipFile

root = Path.cwd().resolve()
destination = root/'docs/architecture-refactor/evidence/ticket37-node-ambient-classes'
trial = root/'build/ticket37-node-ambient-installed'
manifest = json.loads((destination/'manifest.json').read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()
archive_path = destination/manifest['archive']['file']
assert sha(archive_path.read_bytes()) == manifest['archive']['sha256']
assert archive_path.stat().st_size == manifest['archive']['bytes']
with ZipFile(archive_path) as archive:
    assert archive.testzip() is None
    assert len(set(archive.namelist())) == len(archive.namelist()) == manifest['archive']['entries']
    for row in manifest['entries']:
        data = archive.read(row['path'])
        assert len(data) == row['bytes'] and sha(data) == row['sha256']
    patch = subprocess.check_output(['git', 'diff', '--abbrev=9', manifest['sourceBase'], '--', *manifest['sourceFiles']])
    assert patch == archive.read('source/repair.patch')
    for name, expected in manifest['sourceFiles'].items():
        assert sha((root/name).read_bytes()) == expected
        assert (root/name).read_bytes().replace(b'\r\n', b'\n') == subprocess.check_output(['git', 'show', manifest['sourceRevision']+':'+name]).replace(b'\r\n', b'\n')
    assert manifest['matrix'] == json.loads(archive.read('control/ticket37-node-ambient-matrix/summary.json'))
    assert manifest['comparison'] == json.loads(archive.read('installed/ide/comparison-2a421.json'))
    assert len(manifest['comparison']['nodes']) == 5
    for row in manifest['comparison']['nodes']:
        assert all(item['removed'] == 4 and item['added'] == 0 for item in row['typescript'])
        assert row['python']['removed'] == row['python']['added'] == 0
        assert set(row['generation'][0]['changed']) == {'@manual/index.d.ts', *row['generation'][0]['scopedOrderOnly']}
        assert row['generation'][1]['changed'] == row['generation'][1]['scopedOrderOnly']
    assert json.loads(archive.read('installed/ide/typescript-order-audit.json'))['status'] == 'PASS'
    for row in manifest['runtime']:
        assert row == json.loads(archive.read('installed/'+row['node']+'/runtime-verdict.json'))
        assert not row['errors'] and all(row['checks'].values())
        assert json.loads(archive.read('installed/'+row['node']+'/server-exit.json'))['exitCode'] == 0
    first = json.loads(archive.read('installed/runtime-summary-first-four.json'))
    last = json.loads(archive.read('installed/runtime-summary-26.2.json'))
    assert len(first) == 4 and len(last) == 1 and last[0]['node'] == '26.2.0'
    assert {row['node']:row for row in first+last} == {row['node']:row for row in manifest['runtime']}
    assert 'BUILD SUCCESSFUL' in archive.read('runner/node-ambient-full-matrix.txt').decode('utf-8-sig')
    assert manifest['parse'] == json.loads(archive.read('installed/typescript-parse.json'))
    assert all(row['syntaxDiagnostics'] == 0 for row in manifest['parse']['nodes'])
    performance = json.loads(archive.read('related/f84-performance-summary.json'))
    assert performance['overall'] == 'PASS' and performance['sourceRevision'] == manifest['sourceRevision']
classes = {}
for name in ['NodeModuleTypeDocs.class', 'NodeModuleTypeDocs$TSExtractor.class']:
    path = 'com/tkisor/nekojs/core/compiler/'+name
    data = (root/'common/build/classes/java/main'/path).read_bytes()
    classes[path] = sha(data)
    for row in manifest['artifacts']:
        artifacts = list((trial/row['node']/'mods').glob('nekojs-*.jar'))
        assert len(artifacts) == 1 and sha(artifacts[0].read_bytes()) == row['sha256']
        with ZipFile(artifacts[0]) as artifact: assert artifact.read(path) == data
console = root/'build/ticket37-config-sync-repair/node-ambient-archive.txt'
document = json.loads(console.read_text(encoding='utf-8-sig'))
assert document['archive'] == manifest['archive'] and document['sourceRevision'] == manifest['sourceRevision']
target = destination/'archive-console.txt'
assert not target.exists()
target.write_bytes(console.read_bytes())
result = dict(status='PASS', archive=manifest['archive'], fullEntryHashes=True,
              committedSourceAndCurrentPatchMatch=True, actualFiveArtifactClassBytes=classes,
              fullMatrixAndCompletedComparisons=True, supplementalRuntimeRetained=True,
              scopedDeclarationOrderChangesRecorded=True, unchangedDiagnosticIdentitiesPreserved=True,
              completedConsoleSha256=sha(console.read_bytes()), fullIDE='FAIL', currentSourcePerformance='PASS: separately archived exact f84',
              maintainerAcceptance='NOT RECORDED', overall='NOT COMPLETE')
(destination/'archive-verification.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
print(json.dumps(result))
