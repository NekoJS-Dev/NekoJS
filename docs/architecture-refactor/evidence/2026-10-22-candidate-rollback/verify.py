"""Verify separate code and whole-world data rollback observations offline."""
import hashlib
import json
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(root / 'evidence.zip') as z:
    assert len(z.namelist()) == len(set(z.namelist()))
    assert set(z.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert not name.startswith(('/', '\\')) and '..' not in Path(name).parts
        data = z.read(name)
        assert len(data) == expected['size'] and hashlib.sha256(data).hexdigest() == expected['sha256'], name
    def doc(name):
        return json.loads(z.read(name))
    final = doc('final-verdict.json')
    assert final['verdict'] == 'PASS' and final['separateArtifactAndDataRollback']
    assert final['backupRetained'] and final['advancedWorldRetained'] and final['repeatedCopyHashesEqual']
    phases = ['baseline-initial', 'candidate-advance', 'artifact-rollback', 'data-restore']
    rows = [doc(phase + '/result.json') for phase in phases]
    assert len(doc('summary.json')['records']) == 4
    assert all(row['exitCode'] == 0 and row['verdict'] == 'PASS' for row in rows)
    assert [row['readValue'] for row in rows] == ['proof-1', 'proof-2', 'proof-2', 'proof-1']
    assert [row['block'] for row in rows] == ['minecraft:stone', 'minecraft:gold_block', 'minecraft:gold_block', 'minecraft:stone']
    assert rows[0]['artifactSha256'] == rows[2]['artifactSha256'] != rows[1]['artifactSha256'] == rows[3]['artifactSha256']
    assert rows[0]['sourceRevision'] == rows[2]['sourceRevision'] == manifest['oldRevision']
    assert rows[1]['sourceRevision'] == rows[3]['sourceRevision'] == manifest['candidateRevision']
    backup = doc('backup-manifest.json')
    assert backup == rows[0]['worldAfterStop'] == rows[1]['copiedWorldHashes'] == rows[3]['copiedWorldHashes']
    assert rows[2]['copiedWorldHashes'] == rows[1]['worldAfterStop']
    for phase, value in zip(phases, ['proof-1', 'proof-2', 'proof-2', 'proof-1'], strict=True):
        commands = doc(phase + '/commands.json')
        assert any('data get entity' in row['command'] and 'NekoJSPersistentData' in row['response'] and value in row['response'] for row in commands)
print(json.dumps(dict(archive='PASS', entries=len(manifest['entries']), phases=4, normalProcessExits=4,
    codeRollbackRetainsAdvancedData=True, stoppedBackupRestoresPriorData=True)))
