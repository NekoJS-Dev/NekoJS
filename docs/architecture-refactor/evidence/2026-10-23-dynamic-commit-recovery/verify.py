"""Verify archived recovery observations; installed JAR checks were performed before sealing."""
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
    assert summary['productionRevision'] == 'b47734c6721ee8434df48c0b2a86b887d577f02e'
    assert not summary['maintainerAcceptance'] and not summary['fixtureMutatesProduction']
    assert len(summary['nodes']) == 2
    for node in summary['nodes']:
        prefix = node['node'] + '/'
        read = lambda name: json.loads(archive.read(prefix + name))
        result, prep, binding = read('run-result.json'), read('preparation.json'), read('candidate-binding.json')
        assert result['verdict'] == 'PASS' and not result['errors'] and not result['forced']
        assert len(result['stages']) == 7 and result['exits'] == dict(server=0, clientA=0, clientB=0)
        assert result['sourceRevision'] == summary['productionRevision']
        assert len(binding['sourceBindings']) == 1099 and len(binding['classBindings']) == 1640
        assert binding['jar'] == prep['jar'] == node['jar']
        assert hashlib.sha256(archive.read(prefix + 'helper.jar')).hexdigest() == result['helperSha256']
        stages = [read(stage + '.json') for stage in result['stages']]
        assert all(stage[side]['sameThread'] for stage in stages for side in ['server', 'clientA', 'clientB'])
        fault = stages[2]
        generation = fault['server']['activatedGeneration']
        outcomes = lambda stage, event: [v for v in stage['server']['syncOutcomes']
                                         if v['generation'] == generation and v['outcome'] == event]
        players = {v['name']: v['id'] for v in fault['server']['players']}
        assert len(players) == 2
        assert {v['participantId'] for v in outcomes(fault, 'ack')} == set(players.values())
        failed = outcomes(fault, 'activation-failed-post-commit')
        assert len(failed) == 1 and failed[0]['participantId'] == players['OwnedTcpA']
        assert 'OWNED-TCP-COMMIT-FAULT' in failed[0]['reason']
        assert fault['server']['status'] == 'COMMITTED' and fault['server']['lastSummary']['committed']
        assert fault['server']['items']['tcp_commit_fail']['present']
        assert fault['clientB']['items']['tcp_commit_fail']['present']
        for stage in [fault, stages[3]]:
            assert stage['clientA']['items']['tcp_seed']['present']
            assert not stage['clientA']['items']['tcp_commit_fail']['present']
        assert stages[3]['clientA']['connected'] and outcomes(stages[3], 'state-sync-rejected')
        assert len(outcomes(stages[4], 'state-sync-rejected')) > len(outcomes(stages[3], 'state-sync-rejected'))
        assert not stages[4]['clientA']['connected'] and len(stages[4]['server']['players']) == 1
        assert 'DisconnectedScreen' in stages[4]['clientA']['screen']
        assert stages[5]['clientA']['connected'] and not stages[5]['clientA']['faultEnabled']
        assert stages[5]['server']['activatedGeneration'] == generation
        assert outcomes(stages[5], 'state-sync-completed')
        for side in ['server', 'clientA', 'clientB']:
            for key in ['tcp_seed', 'tcp_commit_fail', 'tcp_followup']:
                assert stages[6][side]['items'][key] == dict(present=True, maxStackSize=16, rarity='RARE')
            names = [name for name in archive.namelist()
                     if name.startswith(prefix + side + '/installed-source-')]
            assert len(names) == 1
            proof = json.loads(archive.read(names[0]))
            assert proof['artifactSha256'] == node['jar']['sha256'] and len(proof['classes']) == 5
            assert proof['pid'] == read(side + '-owned-process.json')['pid']
            for cls, value in proof['classes'].items():
                assert binding['classBindings'][cls.replace('.', '/') + '.class'] == value['sha256']
                assert node['jar']['name'] in value['codeSource'] and node['jar']['name'] in value['resource']
        client_log = archive.read(prefix + 'clientA-stdout.log')
        assert client_log.count(b'OWNED-TCP-COMMIT-FAULT owner=') == 3
        assert client_log.count(b'thread=Render thread') == 3
        for side in ['server', 'clientB']:
            assert b'OWNED-TCP-COMMIT-FAULT owner=' not in archive.read(prefix + side + '-stdout.log')
print('PASS: two actual TCP nodes, fourteen stages, six normal exits; maintainer acceptance remains open.')
