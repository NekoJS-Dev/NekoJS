"""Verify all current-candidate protocol stages and exact installed production artifacts offline."""
import hashlib
import io
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
basic = ['01-empty-ledger-two-players', '02-real-prepare-nack', '03-play-prepare-ack-commit',
         '04-configuration-state-sync', '05-two-distinct-prepare-acks', '06-ack-timeout-and-late-ack',
         '07-prepare-disconnect-abort', '08-reconnect-activated-state-only']
commit = ['01-empty-two-enabled-clients', '02-normal-dual-commit', '03-real-commit-activation-failure',
          '04-failed-catch-up-retains-prior-client-state', '05-reconnect-rejected-before-play',
          '06-same-client-recovery-after-fault-removed', '07-subsequent-dual-commit-after-recovery']
total_exits = 0
source_bindings = None
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    assert len(archive.namelist()) == len(set(archive.namelist()))
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert not name.startswith(('/', '\\')) and '..' not in Path(name).parts
        data = archive.read(name)
        assert len(data) == expected['size'] and hashlib.sha256(data).hexdigest() == expected['sha256'], name
    def doc(name):
        return json.loads(archive.read(name))
    artifacts = {}
    for short, reference in manifest['artifactReferences'].items():
        if 'path' in reference:
            assert short == '261' and reference['path'] == '../2026-10-24-method-bounds-performance/evidence.zip'
            file = (root / reference['path']).resolve()
            assert hashlib.sha256(file.read_bytes()).hexdigest() == reference['archiveSha256']
            with zipfile.ZipFile(file) as external:
                artifacts[short] = external.read(reference['entry'])
        else:
            artifacts[short] = archive.read(reference['entry'])
    for kind, expected_stages in [('basic', basic), ('commit', commit)]:
        for short in ['261', '262']:
            prefix = kind + '-' + short + '/'
            result = doc(prefix + 'run-result.json')
            binding = doc(prefix + 'candidate-binding.json')
            assert result['sourceRevision'] == binding['sourceRevision'] == manifest['sourceRevision']
            assert result['verdict'] == 'PASS' and result['stages'] == expected_stages
            assert not result['errors'] and not result['forced']
            assert len(result['exits']) == (4 if kind == 'basic' else 3) and all(value == 0 for value in result['exits'].values())
            total_exits += len(result['exits'])
            assert len(binding['sourceBindings']) == 1100 and len(binding['classBindings']) == 1641
            if source_bindings is None:
                source_bindings = binding['sourceBindings']
            assert binding['sourceBindings'] == source_bindings
            matrix = root.parent / '2026-10-24-method-bounds/evidence.zip'
            assert hashlib.sha256(matrix.read_bytes()).hexdigest() == binding['matrixArchiveSha256']
            digest = hashlib.sha256(artifacts[short]).hexdigest()
            assert all(value['sha256'] == digest for value in binding['artifacts'].values())
            with zipfile.ZipFile(io.BytesIO(artifacts[short])) as jar:
                for name, expected in binding['classBindings'].items():
                    assert hashlib.sha256(jar.read(name)).hexdigest() == expected, name
                for side, count in [('server', 1), ('clientA', 1), ('clientB', 2 if kind == 'basic' else 1)]:
                    proofs = [name for name in archive.namelist() if name.startswith(prefix + side + '/installed-source-') and name.endswith('.json')]
                    assert len(proofs) == count
                    for name in proofs:
                        proof = doc(name)
                        assert proof['artifactSha256'] == digest and len(proof['classes']) == 5
                        assert any(doc(file)['pid'] == proof['pid'] for file in archive.namelist()
                                   if file.startswith(prefix) and file.endswith('-owned-process.json'))
                        for fqn, loaded in proof['classes'].items():
                            entry = fqn.replace('.', '/') + '.class'
                            assert loaded['sha256'] == binding['classBindings'][entry]
                            assert binding['artifacts'][side]['name'] in loaded['codeSource'] and binding['artifacts'][side]['name'] in loaded['resource']
            assert hashlib.sha256(archive.read(prefix + 'helper.jar')).hexdigest() == result['helperSha256']
            stages = [doc(prefix + name + '.json') for name in expected_stages]
            assert all(state[side]['sameThread'] for state in stages for side in ['server', 'clientA', 'clientB'])
            commands = [json.loads(line) for line in archive.read(prefix + 'commands.jsonl').decode().splitlines()]
            assert any(value['command'] == 'stop' for value in commands)
            def outcomes(state, generation, outcome):
                return [value for value in state['server']['syncOutcomes'] if value['generation'] == generation and value['outcome'] == outcome]
            if kind == 'basic':
                assert stages[0]['server']['ledgerEmpty'] and stages[0]['server']['ackTimeoutMillis'] == 10000
                assert stages[0]['clientA']['gateEnabled'] and not stages[0]['clientB']['gateEnabled']
                assert not stages[1]['server']['lastSummary']['committed']
                assert outcomes(stages[1], stages[1]['server']['lastSummary']['generation'], 'ack-rejected')
                assert all(not stages[1][side]['items']['tcp_seed']['present'] for side in ['server', 'clientA', 'clientB'])
                generation = stages[4]['server']['activatedGeneration']
                assert len({value['participantId'] for value in outcomes(stages[4], generation, 'ack')}) == 2
                timeout = stages[5]['server']['lastSummary']
                assert timeout['abortReason'].startswith('ack-timeout:')
                assert outcomes(stages[5], timeout['generation'], 'late-ack-outside-transaction')
                assert stages[6]['server']['lastSummary']['abortReason'].startswith('participant-disconnected:')
                assert stages[7]['server']['activatedGeneration'] == generation and len(stages[7]['server']['players']) == 2
                assert outcomes(stages[7], generation, 'state-sync-completed')
                assert all(not stages[7][side]['items'][name]['present'] for side in ['server', 'clientA', 'clientB'] for name in ['tcp_timeout', 'tcp_disconnect'])
                assert all(stages[7][side]['items'][name] == dict(present=True, maxStackSize=16, rarity='RARE') for side in ['server', 'clientA', 'clientB'] for name in ['tcp_seed', 'tcp_dual'])
            else:
                fault = stages[2]
                generation = fault['server']['activatedGeneration']
                assert fault['server']['status'] == 'COMMITTED' and fault['server']['lastSummary']['committed']
                failures = outcomes(fault, generation, 'activation-failed-post-commit')
                players = {value['name']: value['id'] for value in fault['server']['players']}
                assert len(failures) == 1 and failures[0]['participantId'] == players['OwnedTcpA']
                assert 'OWNED-TCP-COMMIT-FAULT' in failures[0]['reason']
                assert fault['server']['items']['tcp_commit_fail']['present'] and fault['clientB']['items']['tcp_commit_fail']['present']
                assert not fault['clientA']['items']['tcp_commit_fail']['present'] and fault['clientA']['items']['tcp_seed']['present']
                assert stages[3]['clientA']['connected'] and outcomes(stages[3], generation, 'state-sync-rejected')
                assert not stages[4]['clientA']['connected'] and 'DisconnectedScreen' in stages[4]['clientA']['screen']
                assert len(stages[4]['server']['players']) == 1
                assert stages[5]['clientA']['connected'] and not stages[5]['clientA']['faultEnabled']
                assert outcomes(stages[5], generation, 'state-sync-completed')
                assert all(stages[6][side]['items'][name] == dict(present=True, maxStackSize=16, rarity='RARE') for side in ['server', 'clientA', 'clientB'] for name in ['tcp_seed', 'tcp_commit_fail', 'tcp_followup'])
                log = archive.read(prefix + 'clientA-stdout.log').decode('utf8', errors='replace')
                assert log.count('OWNED-TCP-COMMIT-FAULT owner=') == log.count('thread=Render thread') == 3
assert total_exits == 14
print(json.dumps(dict(archive='PASS', nodes=2, stages=30, normalProcessExits=total_exits,
    sourceFiles=len(source_bindings), productionClassesPerNode=1641, maintainerAcceptance=False)))
