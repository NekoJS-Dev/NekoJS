import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parent
sha = lambda data: hashlib.sha256(data).hexdigest()
manifest = json.loads((root / 'manifest.json').read_text())
summary = json.loads((root / 'runtime-summary.json').read_text())
archive = root / manifest['archive']
assert archive.stat().st_size == manifest['bytes'] and sha(archive.read_bytes()) == manifest['sha256']
assert summary['productionRevision'] == '7d77308bfa400721304b20940ada91092180a1ed'
assert summary['artifactSha256'] == '2a64c34ca2d7abc3e8b2cd6402b17f204b3c10b4d15fcd1c2ebc917b3dd9aff2'
assert len(summary['classBindings']) == 1592
with ZipFile(archive) as data:
    assert data.testzip() is None
    assert set(data.namelist()) == {row['name'] for row in manifest['entries']}
    for row in manifest['entries']:
        value = data.read(row['name'])
        assert len(value) == row['bytes'] and sha(value) == row['sha256']
    load = lambda name: json.loads(data.read(name))
    lines = lambda name: [json.loads(line) for line in data.read(name).decode('utf-8').splitlines()]
    assert load('attempt1/abort-proof-exit.json')['exitCode'] == 1
    assert b'Owned window not reached: D-prepare-held' in data.read('attempt1/abort-proof-runner.txt')
    assert not any(row['event'] == 'dynamic-frame' for row in lines('attempt1/proxyD.jsonl'))
    assert any(row['event'] == 'hold-expired' for row in lines('attempt1/proxyC.jsonl'))
    assert load('attempt2/orchestrator-exit.json')['exitCode'] == 0
    for attempt, exits in summary['directExits'].items():
        for node, code in exits.items():
            assert code == load(attempt + '/' + node + '/exit.json')['exitCode'] == 0
    for attempt in ('attempt1', 'attempt2'):
        preparation = load(attempt + '/preparation.json')
        assert preparation['artifactSha256'] == summary['artifactSha256']
        assert preparation['classBindings'] == summary['classBindings']
    abort = load('attempt2/window-verdict.json')
    commit = load('attempt2/configuration-commit-verdict.json')
    assert summary['abort'] == abort and summary['commit'] == commit
    generation = abort['abortedGeneration']
    state = abort['abort']
    assert state['status'] == 'ABORTED' and state['activatedGeneration'] == abort['baselineGeneration'] == 2
    assert generation == 3
    assert any(row['generation'] == generation and row['outcome'] == 'joined-mid-transaction' for row in state['outcomes'])
    assert any(row['generation'] == generation and row['detail'] == 'participant disconnected before acking the prepare' for row in state['phases'])
    assert not any(row['generation'] == generation and row['phase'] == 'COMMIT' for row in state['phases'])
    assert 'Unknown item' in abort['missingAfterAbort']['response']
    assert 'Gave' in abort['recoveryGive']['response'] and 'Gave' in commit['give']['response']
    server = data.read('attempt2/server/logs/latest.log').decode('utf-8')
    snapshots = [json.loads(line.split('LIVE-WINDOW ', 1)[1]) for line in server.splitlines() if 'LIVE-WINDOW ' in line]
    assert state in snapshots and abort['recovery'] in snapshots and commit['state'] in snapshots
    for state, expected_generation, count in ((abort['recovery'], 4, 1), (commit['state'], 5, 2)):
        assert state['status'] == 'COMMITTED' and state['activatedGeneration'] == expected_generation
        for outcome in ('ack', 'activation-report'):
            assert len({row['participant'] for row in state['outcomes'] if row['generation'] == expected_generation and row['outcome'] == outcome}) == count
    assert any(row['generation'] == 5 and row['outcome'] == 'joined-mid-transaction' for row in commit['state']['outcomes'])
    assert 'NekoWindow7dE joined the game' in server
    for letter in ('C', 'D', 'E'):
        events = lines('attempt2/proxy' + letter + '.jsonl')
        for row in events:
            if row['event'] == 'dynamic-frame-forwarded':
                assert any(original['event'] == 'dynamic-frame' and all(original[key] == row[key] for key in ('kind', 'generation', 'frameSha256', 'bytes')) for original in events)
        if letter == 'D':
            assert any(row['event'] == 'disconnect-before-delivery' and row['generation'] == 3 for row in events)
            assert not any(row['event'] == 'dynamic-frame-forwarded' and row['generation'] == 3 for row in events)
        else:
            assert not any(row['event'] == 'hold-expired' for row in events)
    assert summary['noBridge'] and summary['noNativeActivationOrInput'] and summary['forcedKills'] == 0
    assert summary['overall'] == 'NOT_COMPLETE' and summary['maintainerAcceptance'] == 'NOT_RECORDED'
print(json.dumps(dict(result='PASS', evidenceOnly=True, entries=len(manifest['entries']), abortGeneration=3, recoveryGeneration=4, commitGeneration=5, overall='NOT_COMPLETE')))
