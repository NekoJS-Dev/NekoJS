"""Verify sealed observations and hashes without extraction, credentials, or game processes."""
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
    for trial in manifest['successful']:
        run = doc(trial + '/run-result.json')
        review = doc(trial + '/acceptance-review.json')
        artifact = doc(trial + '/artifact-manifest.json')
        assert run['verdict'] == review['verdict'] == 'PASS'
        assert run['sourceRevision'] == manifest['sourceRevision']
        assert not run['errors'] and not run['forced']
        assert len(run['exits']) == 4 and all(value == 0 for value in run['exits'].values())
        assert review['generations'] == dict(emptyLedger=1, nack=2, firstCommit=3, dualCommit=4,
            timeout=5, disconnect=6, reconnectActivated=4)
        expected = next(value['sha256'] for value in artifact['artifacts'] if value['name'].startswith('nekojs-'))
        assert review['artifactSha256'] == expected
        for binding in review['installedProofs']:
            proof = doc(trial + '/' + binding['side'] + '/' + binding['proof'])
            assert proof['artifactSha256'] == expected
            jar = proof['modFile'].replace('\\', '/').split('/')[-1]
            for name, value in proof['classes'].items():
                assert jar in value['codeSource'] and jar in value['resource']
                assert value['sha256'] == artifact['nodeClassBindings'][name.replace('.', '/') + '.class']
        states = [doc(trial + '/' + name + '.json') for name in run['stages']]
        assert len(states) == 8
        players = review['participants']
        def outcome(state, generation, name, participant):
            return any(value['generation'] == generation and value['outcome'] == name
                and value['participantId'] == participant for value in state['server']['syncOutcomes'])
        assert outcome(states[1], 2, 'ack-rejected', players['OwnedTcpB'])
        assert outcome(states[2], 3, 'ack', players['OwnedTcpA'])
        assert outcome(states[3], 3, 'state-sync-completed', players['OwnedTcpB'])
        assert all(outcome(states[4], 4, 'ack', player) for player in players.values())
        assert states[5]['server']['lastSummary']['abortReason'].startswith('ack-timeout:')
        assert outcome(states[5], 5, 'late-ack-outside-transaction', players['OwnedTcpA'])
        assert states[6]['server']['lastSummary']['abortReason'] == 'participant-disconnected:' + players['OwnedTcpA']
        assert outcome(states[7], 4, 'state-sync-completed', players['OwnedTcpA'])
        for side in ['server', 'clientA', 'clientB']:
            assert all(states[7][side]['items'][name] == dict(present=True, maxStackSize=16, rarity='RARE')
                for name in ['tcp_seed', 'tcp_dual'])
            assert all(not states[7][side]['items'][name]['present'] for name in ['tcp_timeout', 'tcp_disconnect'])
        assert all(state[side]['sameThread'] for state in states for side in ['server', 'clientA', 'clientB'] if state[side])
    assert all(doc(trial + '/run-result.json')['verdict'] == 'NOT GREEN' for trial in manifest['failed'])
    assert doc(manifest['failed'][1] + '/external-cleanup.json')['forced']
print(json.dumps(dict(archive='PASS', entries=len(manifest['entries']), nodes=2, stages=16,
    normalProcessExits=8, retainedFailedAttempts=2, retainedExternalForcedCleanup=1)))
