"""Verify the exact candidate, four normal stops, and separate artifact/data rollback."""
import hashlib
import io
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    assert len(archive.namelist()) == len(set(archive.namelist()))
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert not name.startswith(('/', '\\')) and '..' not in Path(name).parts
        data = archive.read(name)
        assert len(data) == expected['size'] and hashlib.sha256(data).hexdigest() == expected['sha256'], name
    def doc(name):
        return json.loads(archive.read(name))
    final = doc('final-verdict.json')
    assert final['verdict'] == 'PASS' and final['separateArtifactAndDataRollback']
    assert all(final[name] for name in ['backupRetained', 'advancedWorldRetained', 'exactRestoreBeforeBoot', 'repeatedCopyHashesEqual'])
    phases = ['baseline-initial', 'candidate-advance', 'artifact-rollback', 'data-restore']
    rows = [doc(phase + '/result.json') for phase in phases]
    assert doc('summary.json')['records'] == rows
    assert all(row['exitCode'] == 0 and row['verdict'] == 'PASS' for row in rows)
    assert [row['readValue'] for row in rows] == ['proof-1', 'proof-2', 'proof-2', 'proof-1']
    assert [row['block'] for row in rows] == ['minecraft:stone', 'minecraft:gold_block', 'minecraft:gold_block', 'minecraft:stone']
    assert rows[0]['artifactSha256'] == rows[2]['artifactSha256'] == '8b7c6179ef00f442b041d95b5c379820144d101cd4479615d5f1cf19d5194e57'
    assert rows[0]['sourceRevision'] == rows[2]['sourceRevision'] == manifest['oldRevision']
    assert rows[1]['sourceRevision'] == rows[3]['sourceRevision'] == final['currentSourceRevision'] == manifest['candidateRevision']
    backup = doc('backup-manifest.json')
    assert backup == rows[0]['worldAfterStop'] == rows[1]['copiedWorldHashes'] == rows[3]['copiedWorldHashes']
    assert rows[2]['copiedWorldHashes'] == rows[1]['worldAfterStop']
    for phase, value in zip(phases, ['proof-1', 'proof-2', 'proof-2', 'proof-1'], strict=True):
        commands = doc(phase + '/commands.json')
        assert any('data get entity' in row['command'] and 'NekoJSPersistentData' in row['response'] and value in row['response'] for row in commands)
        assert any(row['command'] == 'save-all flush' for row in commands)
        assert any(row['command'] == 'stop' for row in commands)
        log = archive.read(phase + '/server-stdout.log').decode('utf8', errors='replace')
        assert '[Rcon] ROLLBACK-BLOCK-PASS' in log
        assert f'"phase":"{phase}","value":"{value}"' in log
        assert 'Stopping server' in log
    binding = doc('tools/method-bounds-rollback-binding.json')
    candidate = binding['candidate']
    artifact = archive.read('artifact/' + candidate['artifact'])
    assert hashlib.sha256(artifact).hexdigest() == candidate['sha256'] == rows[1]['artifactSha256'] == rows[3]['artifactSha256']
    assert binding['sourceRevision'] == manifest['candidateRevision']
    with zipfile.ZipFile(io.BytesIO(artifact)) as jar:
        for name, digest in candidate['classBindings'].items():
            assert hashlib.sha256(jar.read(name)).hexdigest() == digest, name
print(json.dumps(dict(archive='PASS', phases=4, normalProcessExits=4, candidateClasses=len(candidate['classBindings']),
    codeRollbackRetainsAdvancedData=True, stoppedBackupRestoresPriorData=True)))
