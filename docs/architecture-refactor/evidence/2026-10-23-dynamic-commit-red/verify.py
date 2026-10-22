"""Verify preserved failure evidence; successful verification does not mean acceptance passed."""
import hashlib
import json
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, entry in manifest['entries'].items():
        data = archive.read(name)
        assert len(data) == entry['size'] and hashlib.sha256(data).hexdigest() == entry['sha256'], name
    summary = json.loads(archive.read('summary.json'))
    assert summary['verdict'] == 'NOT GREEN'
    result = json.loads(archive.read('v3/run-result.json'))
    assert len(result['stages']) == 4 and not result['forced']
    assert result['exits'] == dict(server=0, clientA=4294967295, clientB=0)
    stage = json.loads(archive.read('v3/04-failed-catch-up-retains-prior-client-state.json'))
    assert stage['clientA']['connected'] and stage['clientB']['connected']
    assert stage['clientA']['items']['tcp_seed']['present']
    assert not stage['clientA']['items']['tcp_commit_fail']['present']
    assert stage['server']['status'] == 'COMMITTED'
    generation = stage['server']['activatedGeneration']
    outcomes = [entry['outcome'] for entry in stage['server']['syncOutcomes'] if entry['generation'] == generation]
    assert outcomes.count('ack') == 2 and 'activation-failed-post-commit' in outcomes and 'state-sync-rejected' in outcomes
    crash = next(archive.read(name) for name in archive.namelist() if name.startswith('v3/clientA/crash-reports/'))
    assert b'Some intrusive holders were not registered' in crash and b'Minecraft.disconnect' in crash
    binding = json.loads(archive.read('v3/candidate-binding.json'))
    assert len(binding['sourceBindings']) == 1099 and len(binding['classBindings']) == 1640
print('Failure evidence verified: four real TCP stages; disconnect crashes. Acceptance remains NOT GREEN.')
