"""Verify frozen native Fabric client resource and signed TCP acceptance evidence."""
import hashlib
import json
import zipfile
from collections import Counter
from pathlib import Path

base = Path(__file__).resolve().parent
receipt = json.loads((base / 'verification.json').read_text(encoding='utf-8'))
archive = base / 'evidence.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest() == receipt['archiveSha256']
labels = ['initial', 'world-a', 'f3-t-world-a', 'after-a', 'world-b', 'after-b',
          'world-a-return', 'after-a-return', 'remote-untrusted', 'remote-valid',
          'remote-tampered', 'remote-recovered', 'remote-rotated', 'remote-final', 'remote-disconnected']
checks = base.parent / '2026-10-20-fabric-final-checks'
build_receipt = json.loads((checks / 'verification.json').read_text(encoding='utf-8'))
assert hashlib.sha256((checks / 'evidence.zip').read_bytes()).hexdigest() == build_receipt['archiveSha256']
with zipfile.ZipFile(checks / 'evidence.zip') as built:
    production = json.loads(built.read('production-bindings.json'))
with zipfile.ZipFile(archive) as frozen:
    assert len(frozen.namelist()) == len(set(frozen.namelist())) == receipt['entries']
    assert set(frozen.namelist()) == set(receipt['files'])
    for name, digest in receipt['files'].items():
        assert hashlib.sha256(frozen.read(name)).hexdigest() == digest, name
    assets = json.loads(frozen.read('assets/population.json'))
    assert assets['complete'] is True
    objects = {}
    for index in assets['indexes']:
        data = frozen.read('assets/' + index['file'])
        assert hashlib.sha1(data).hexdigest() == index['sha1']
        for item in json.loads(data)['objects'].values():
            if item['hash'] in objects:
                assert objects[item['hash']] == item['size']
            objects[item['hash']] = item['size']
    assert len(objects) == len(assets['objects']) == 5077
    assert objects == {row['hash']: row['size'] for row in assets['objects']}
    assert Counter(row['source'] for row in assets['objects']) == {'verified-local': 4608, 'pinned-mojang': 469}
    def document(prefix, name):
        return json.loads(frozen.read(prefix + '/' + name))
    def lines(prefix, name):
        return [json.loads(line) for line in frozen.read(prefix + '/' + name).splitlines()]
    assert set(receipt['runs']) == {'26.1.2-fabric', '26.2.0-fabric'}
    for node, run in receipt['runs'].items():
        prefix = node
        configuration = document(prefix, 'configuration.json')
        assert configuration['node'] == node and configuration['remote'] is True
        assert configuration['revision'] == run['revision'] and run['revision'].startswith('5fec3f89')
        for name, digest in configuration['testSources'].items():
            assert hashlib.sha256(frozen.read(prefix + '/' + name)).hexdigest() == digest, name
        assert document(prefix, 'production-classes.json') == production[node]['classes']
        assert len(production[node]['classes']) == 1569
        for artifact, digest in [('observer.jar', 'observer-sha256.txt'),
                                 ('server-observer.jar', 'server-observer-sha256.txt')]:
            assert hashlib.sha256(frozen.read(prefix + '/' + artifact)).hexdigest() == frozen.read(
                prefix + '/' + digest).decode().strip()
        result = document(prefix, 'result.json')
        assert result['accepted'] and result['remoteSyncAccepted'] and result['screenshotsReviewed'] is False
        assert result['completed'] == labels and result['pid'] == run['pid']
        stopping = document(prefix, 'stopping.json')
        assert stopping == {'pid': result['pid'], 'clientStopping': True}
        assert document(prefix, 'server-result.json') == {
            'normalStop': True, 'forcedOwnedGamePids': [], 'launcherExitCode': 0}
        assert document(prefix, 'server-stopped.json')['serverStopped'] is True
        execution = document(prefix, 'execution.json')
        assert [row['stage'] for row in execution] == ['classpath', 'compile', 'signed-fixture', 'client']
        assert all(row['exitCode'] == 0 and not row.get('forcedOwnedGamePids')
                   and not row.get('timedOut', False) for row in execution)
        rows = lines(prefix, 'resources.jsonl')
        assert [row['label'] for row in rows] == labels and {row['pid'] for row in rows} == {result['pid']}
        for row in rows:
            remote = row['label'] in ('remote-valid', 'remote-recovered', 'remote-final')
            world = row['label'] in ('world-a', 'f3-t-world-a', 'world-b', 'world-a-return')
            if remote:
                expected = 'REMOTE'
            elif world:
                expected = 'B' if row['label'] == 'world-b' else 'A'
            else:
                expected = 'GLOBAL'
            assert row['marker'] == expected
            assert row['tcpPlay'] is remote
            assert len(row['stack']) == (3 if remote or world else 2)
            assert row['stack'][:2] == ['ROOT', 'GLOBAL']
            assert row['stack'][-1] == expected
            assert len(row['worldPacks']) == int(world)
            assert 'MissingItemModel' not in row['itemModel']
            model = json.loads(row['generatedModel'])
            assert model['parent'] == 'minecraft:item/generated'
            assert model['textures']['layer0'] == 'minecraft:item/diamond'
        address = document(prefix, 'remote-address.json')
        assert address['address'] == '127.0.0.1' and address['port'] == 25893 and address['loopback']
        assert address['initiallyTrusted'] is False
        key = document(prefix, 'remote-fixture.json')
        rejected = lines(prefix, 'rejections.jsonl')
        assert [row['phase'] for row in rejected] == ['untrusted', 'tampered', 'rotated']
        assert 'Run /nekojs trust ' + address['normalizedHost'] in rejected[0]['reason']
        assert 'signature mismatch for ' + key['keyId'] in rejected[1]['reason']
        assert 'key rotation not accepted' in rejected[2]['reason']
        phases = lines(prefix, 'server-phases.jsonl')
        requests = lines(prefix, 'server-requests.jsonl')
        assert [row['phase'] for row in phases] == [row['phase'] for row in requests] == [
            'tampered', 'restore', 'rotated', 'restore']
        assert all(row['players'] == 1 for row in requests if row['phase'] != 'restore')
        assert prefix + '/server-errors.jsonl' not in frozen.namelist()
        commands = document(prefix, 'remote-commands.json')
        assert [row['command'] for row in commands] == [
            'directoryprobe tampered', 'directoryprobe restore', 'directoryprobe rotated', 'directoryprobe restore', 'stop']
        assert all(row['exitCode'] == 0 for row in commands)
        assert all('DIRECTORY-REMOTE ' + row['phase'] + ' PASS' in commands[index]['response']
                   for index, row in enumerate(phases))
        review = document(prefix, 'visual-review.json')
        assert review['allThreeViewed'] and review['humanMaintainerAccepted'] is False
        assert len(review['images']) == 3
        for row in review['images']:
            data = frozen.read(prefix + '/screenshots/' + Path(row['path'].replace('\\', '/')).name)
            assert data.startswith(b'\x89PNG\r\n\x1a\n') and hashlib.sha256(data).hexdigest() == row['sha256']
        assert 'BUILD SUCCESSFUL' in frozen.read(prefix + '/client.log').decode('utf-8')
        assert 'BUILD SUCCESSFUL' in frozen.read(prefix + '/server.log').decode('utf-8')
print('PASS: both Fabric clients, 15 phases each, signed TCP rejection/recovery, native models, reviewed images and normal exits')
