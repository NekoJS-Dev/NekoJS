"""Recompute world switching, signed networking and memory from frozen records."""
import hashlib
import json
import re
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
archive = root / 'evidence.zip'
assert archive.stat().st_size == manifest['archiveBytes']
assert hashlib.sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(archive) as data:
    assert set(data.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert hashlib.sha256(data.read(name)).hexdigest() == expected, name
        assert not name.endswith(('.pk8', 'server.properties', 'adapter-classpath.txt'))

    def record(name):
        return json.loads(data.read('run/' + name))

    raw = data.read('run/client-launch-7.log')
    log = raw.decode('utf-8-sig')
    result = record('live-acceptance-analysis.json')
    entries = re.findall(r'NEKO_WORLD_ACCEPTANCE side=(server|client) world=([AB]) pack=sessionprobe activation=(\d+) event=entry count=(\d+)', log)
    for side in ['server', 'client']:
        rows = [dict(world=w, activation=int(a), count=int(c)) for s, w, a, c in entries if s == side]
        assert rows == result['worldEntries'][side]
        assert [row['world'] for row in rows] == ['A', 'B', 'A', 'A']
        assert all(row['count'] == 1 for row in rows)
        assert len({row['activation'] for row in rows}) == 4
        consumers = re.findall(r'NEKO_WORLD_ACCEPTANCE side=' + side + r' world=([AB]) pack=sessionconsumer event=capability world=([AB]) count=(\d+) providerActivation=(\d+)', log)
        assert len(consumers) == 4
        assert all(w == actual and int(c) == 1 for w, actual, c, _ in consumers)
        assert [int(a) for *_, a in consumers] == [row['activation'] for row in rows]
    for stage in ['a1', 'b1', 'a2', 'a3']:
        row = record('quiet-' + stage + '.json')
        assert row['pid'] == 25128 and row['passed'] and row['observedSeconds'] == 8
        window = raw[row['startByte']:row['endByte']].decode()
        assert not any('NEKO_WORLD_ACCEPTANCE' in line and 'world=' + row['world'] in line for line in window.splitlines())
    signed = [int(n) for n in re.findall(r'NEKO_SIGNED_ACCEPTANCE event=entry selector=SERVER_CACHE:packs:signedacceptance activation=(\d+)', log)]
    assert signed == result['signedActivations'] == [17, 18]
    for activation in signed:
        assert f'NEKO_SIGNED_ACCEPTANCE event=tick activation={activation} n=1' in log
        assert f'NEKO_SIGNED_ACCEPTANCE event=timer activation={activation}' in log
    assert 'this server distributes signed script packs (kubernetes.docker.internal)' in log
    assert 'NEKO_TAMPERED_CONTENT_MUST_NOT_EXECUTE' not in log
    for first, last, reason in [('tamper1', 'restore1', 'signature mismatch for acceptance-local-20261018'),
                                ('replacement1', 'restore2', 'embedded public key does not match pinned key')]:
        start = record('signed-fixture-' + first + '.json')['clientLogStartByte']
        end = record('signed-fixture-' + last + '.json')['clientLogStartByte']
        window = raw[start:end].decode()
        assert reason in window and 'NEKO_SIGNED_ACCEPTANCE event=entry' not in window
    for stage in ['valid1', 'valid2']:
        row = record('signed-quiet-' + stage + '.json')
        assert row['passed'] and row['pid'] == 25128 and row['observedSeconds'] == 8
        assert b'NEKO_SIGNED_ACCEPTANCE' not in raw[row['startByte']:row['endByte']]
    original = record('valid-signed-manifest.json')
    restored = record('signed-fixture-restore2.json')['after']
    assert restored['scriptSha256'] == hashlib.sha256(data.read('run/valid-signed-client.js')).hexdigest()
    assert restored['manifestSha256'] == hashlib.sha256(data.read('run/valid-signed-manifest.json')).hexdigest()
    replacement = record('replacement-signed-manifest.json')
    assert replacement['signature']['keyId'] == original['signature']['keyId']
    assert replacement['signature']['publicKey'] != original['signature']['publicKey']
    prepared = record('replacement-signature-preparation.json')
    assert prepared['validIndependentSignature'] and prepared['originalPinRejects']
    trust = record('network-final-trust.json')
    assert len(trust['trustedServers']) == len(trust['trustedKeys']) == 1
    assert trust['trustedKeys']['acceptance-local-20261018']['publicKey'] == original['signature']['publicKey']
    for memory in result['memory']:
        stage = memory['stage']
        process = record('memory-' + stage + '/process.json')
        assert process['pid'] == 25128 and process['processStartUtc'] == '2026-10-18T12:57:26.3646991Z'
        for key, value in process.items():
            assert memory[key] == value
        nmt = data.read('run/memory-' + stage + '/nmt.log').decode('utf-16')
        total = int(re.search(r'Total: reserved=\d+KB, committed=(\d+)KB', nmt).group(1))
        heap = int(re.search(r'Java Heap \(reserved=\d+KB, committed=(\d+)KB\)', nmt).group(1))
        assert (total, heap, total - heap) == tuple(memory[k] for k in ['nmtTotalCommittedKiB', 'nmtHeapCommittedKiB', 'nmtNonHeapCommittedKiB'])
    before = record('world-login-main-class-bindings.json')
    assert before == record('production-class-final.json') and len(before) == 1625
    assert 'MCP_ACCEPTANCE_MANUAL_TAKEOVER active=true' in log
    assert 'MCP_ACCEPTANCE_LIST_SELECTION_PATCH_V2' in log
    assert 'MCP_ACCEPTANCE_NATIVE_SCREENSHOT_PATCH' in log
    assert '[minecraft/Minecraft]: Stopping!' in log
    assert 'MCP_ACCEPTANCE_TEST_HTTP_EXECUTOR_CLOSED terminated=true' in log and 'BUILD SUCCESSFUL' in log
    server = data.read('run/server-launch-2.log').decode('utf-8-sig')
    assert 'Done (' in server and 'BUILD SUCCESSFUL' in server
    assert result['unassistedJvmExit'] is False and result['mcpExecutorCleanupRequired'] is True
    assert result['gameTrustCommandVerified'] is False and result['maintainerAcceptance'] is None
    assert result['perContextGameMemoryBudgetVerified'] is False
    for image in ['client-signed-native-capture.png', 'client-final-world-list-native.png']:
        assert data.read('run/' + image).startswith(b'\x89PNG\r\n\x1a\n')
    print(json.dumps(dict(status='PASS', worldSequence='A-B-A plus A for trust setup',
                          signedActivations=signed, quietWindows=6, memoryStages=len(result['memory']),
                          productionClasses=1625, unassistedJvmExit=False,
                          gameTrustCommandVerified=False, maintainerAcceptance=None), indent=2))
