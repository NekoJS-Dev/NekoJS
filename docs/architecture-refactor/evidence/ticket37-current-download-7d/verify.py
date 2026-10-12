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
assert len(summary['classBindings']) == 1592 and summary['copiesPerAttempt'] == 4
with ZipFile(archive) as data:
    assert data.testzip() is None
    assert set(data.namelist()) == {row['name'] for row in manifest['entries']}
    for row in manifest['entries']:
        value = data.read(row['name'])
        assert len(value) == row['bytes'] and sha(value) == row['sha256']
    load = lambda name: json.loads(data.read(name))
    lines = lambda name: [json.loads(line) for line in data.read(name).decode('utf-8').splitlines()]
    for attempt, exits in summary['directExits'].items():
        for node, code in exits.items():
            assert load(attempt + '/' + node + '/exit.json')['exitCode'] == code == 0
        preparation = load(attempt + '/preparation.json')
        assert preparation['artifactSha256'] == summary['artifactSha256'] and preparation['classBindings'] == summary['classBindings']
        signed = load(attempt + '/signed-pack-preparation.json')
        assert signed['result'] == 'SIGNED_AND_VERIFIED' and not signed['allowUnsigned'] and not signed['privateKeyPersisted']
    assert all(load('attempt' + str(i) + '/orchestrator-exit.json')['exitCode'] == 1 for i in (1, 2, 3))
    assert load('attempt4/orchestrator-exit.json')['exitCode'] == 0
    assert b'signature mismatch for owned-download-window-7d' in data.read('attempt1/clientC/stdout.log')
    assert b'Run /nekojs trust kubernetes.docker.internal' in data.read('attempt2/clientC/stdout.log')
    diagnosis = load('attempt1/signature-diagnosis.json')
    assert diagnosis == summary['signatureDiagnosis'] and not diagnosis['actualBootFilesValid'] and diagnosis['originalSignedFilesValid']
    assert diagnosis['soleAddedFile'] == 'client_scripts/jsconfig.json' and not diagnosis['removedActualFiles']
    assert b'PACK-DOWNLOAD-PROOF executed=true' in data.read('attempt3/clientC/stdout.log')
    assert any(row['event'] == 'downstream-ended' and row.get('detail') == 'Invalid framed VarInt' for row in lines('attempt3/proxyC.jsonl'))
    assert b'No actual pending reload with C ack' in data.read('attempt3/download-proof-runner.txt')
    verdict = load('attempt4/download-verdict.json')
    assert verdict == summary['download'] and verdict['result'] == 'PASS'
    assert verdict['notExecutedOrCachedBeforeRelease'] and verdict['signedRemotePackExecuted'] and verdict['signatureKeyPinned']
    assert not verdict['fakeReplies'] and not verdict['changedWireBytes'] and not verdict['allowUnsigned']
    pending, committed = verdict['awaitingAckWhilePartial'], verdict['latestGenerationCatchup']
    assert pending['status'] == 'AWAITING_ACK' and pending['activatedGeneration'] == verdict['baselineGeneration'] == 2
    assert not any(row['generation'] == 3 and row['phase'] == 'COMMIT' for row in pending['phases'])
    assert len({row['participant'] for row in pending['outcomes'] if row['generation'] == 3 and row['outcome'] == 'ack'}) == 1
    assert committed['status'] == 'COMMITTED' and committed['activatedGeneration'] == 3
    for outcome in ('ack', 'activation-report'):
        assert len({row['participant'] for row in committed['outcomes'] if row['generation'] == 3 and row['outcome'] == outcome}) == 2
    server = data.read('attempt4/server/logs/latest.log').decode('utf-8')
    snapshots = [json.loads(line.split('LIVE-WINDOW ', 1)[1]) for line in server.splitlines() if 'LIVE-WINDOW ' in line]
    assert pending in snapshots and committed in snapshots and snapshots.index(pending) < snapshots.index(committed)
    assert 'NekoWindow7dD joined the game' in server and 'Gave' in verdict['give']['response']
    rcon = lines('attempt4/rcon.jsonl')
    assert any(row.get('command') == verdict['give']['command'] and row.get('response') == verdict['give']['response'] for row in rcon)
    for letter in ('C', 'D'):
        events = lines('attempt4/proxy' + letter + '.jsonl')
        assert not any(row['event'] in ('hold-expired', 'bundle-hold-expired') for row in events)
        assert not any(row['event'] == 'downstream-ended' and row.get('error') == 'ValueError' for row in events)
        for row in events:
            if row['event'] in ('pack-frame-forwarded', 'dynamic-frame-forwarded'):
                original_event = 'pack-frame' if row['event'] == 'pack-frame-forwarded' else 'dynamic-frame'
                assert any(original['event'] == original_event and original['frameSha256'] == row['frameSha256'] and original['bytes'] == row['bytes'] for original in events)
        text = data.read('attempt4/client' + letter + '/stdout.log')
        assert b'PACK-DOWNLOAD-PROOF executed=true' in text and b'Activated 1 remote script pack(s)' in text
        trust = load('attempt4/client' + letter + '/trusted-servers.json')
        assert any(value['serverAddress'] == 'kubernetes.docker.internal' for value in trust['trustedServers'].values())
        assert 'owned-download-window-7d' in trust['trustedKeys']
    events = lines('attempt4/proxyD.jsonl')
    held = next(row for row in events if row['event'] == 'bundle-partial-held')
    forwarded = next(row for row in events if row['event'] == 'pack-frame-forwarded')
    assert held['prefixBytes'] == forwarded['prefixBytes'] == verdict['partialBundle']['prefixBytes'] == 64
    assert held['frameSha256'] == forwarded['frameSha256'] == verdict['partialBundle']['frameSha256']
    assert held['bytes'] == forwarded['bytes'] > 64 and 0 < forwarded['time'] - held['time'] < 8
    pack_manifest = data.read('attempt4/server/packs/owned-download/manifest.json')
    cached = [name for name in data.namelist() if name.startswith('attempt4/clientD/server_packs/') and name.endswith('/manifest.json')]
    assert len(cached) == 1 and data.read(cached[0]) == pack_manifest and sha(pack_manifest) == verdict['cachedManifestSha256']
    cached_script = cached[0].rsplit('/', 1)[0] + '/client_scripts/download-proof.js'
    assert sha(data.read(cached_script)) == verdict['cachedScriptSha256']
    key = load('attempt4/clientD/trusted-servers.json')['trustedKeys']['owned-download-window-7d']
    assert key['publicKey'] == json.loads(pack_manifest)['signature']['publicKey']
    assert summary['noBridge'] and summary['noNativeActivationOrInput'] and summary['forcedKills'] == 0
    assert summary['overall'] == 'NOT_COMPLETE' and summary['maintainerAcceptance'] == 'NOT_RECORDED'
print(json.dumps(dict(result='PASS', evidenceOnly=True, entries=len(manifest['entries']), partialBundleBarrier=True, generation=3, overall='NOT_COMPLETE')))
