import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

directory = Path(__file__).resolve().parent
project = directory.parents[3]
sha = lambda data: hashlib.sha256(data).hexdigest()
manifest = json.loads((directory / 'manifest.json').read_text())
archive_path = directory / manifest['archive']['file']
assert archive_path.stat().st_size == manifest['archive']['bytes']
assert sha(archive_path.read_bytes()) == manifest['archive']['sha256']
with ZipFile(archive_path) as archive:
    assert archive.testzip() is None
    assert set(archive.namelist()) == set(manifest['entries'])
    assert len(archive.namelist()) == manifest['archive']['entries'] == 131
    for name, entry in manifest['entries'].items():
        data = archive.read(name)
        assert len(data) == entry['bytes'] and sha(data) == entry['sha256']
        assert 'server.properties' not in name and 'hs_err' not in name and 'owned-java-args' not in name
    summary = json.loads(archive.read('runtime-summary.json'))
    assert summary == json.loads((directory / 'runtime-summary.json').read_text())
    assert summary['sourceRevision'] == manifest['sourceRevision'] == '93f4b259ffafb6466918582fcfa950c2c2480d77'
    assert summary['nativeClientOverall'] == 'FAIL' and summary['overall'] == 'NOT_COMPLETE'
    assert summary['postReloadAndBPixelTransitions'] == summary['firstPixelFrame'] == 'NOT_VERIFIED'
    assert summary['exits']['initial'] == dict(server=None, clientA=None, clientB=0)
    assert summary['exits']['resumed'] == dict(server=0, clientA=4294967295, clientB=1)
    transaction = summary['twoPlayerTransaction']
    assert transaction['status'] == 'COMMITTED' and transaction['activatedGeneration'] == 3
    acked = {row['participant'] for row in transaction['outcomes'] if row['generation'] == 3 and row['outcome'] == 'ack'}
    activated = {row['participant'] for row in transaction['outcomes'] if row['generation'] == 3 and row['outcome'] == 'activation-report' and row['reason'] == 'activated'}
    assert len(acked) == 2 and acked == activated
    caught_up = {row['participant'] for row in transaction['outcomes'] if row['generation'] == 2 and row['outcome'] == 'state-sync-completed'}
    assert caught_up == acked
    assert any(row['generation'] == 3 and row['phase'] == 'ACK' and row['passed'] for row in transaction['phases'])
    assert any(row['generation'] == 3 and row['phase'] == 'COMMIT' and row['passed'] for row in transaction['phases'])
    assert [row['generation'] for row in summary['initialHud']] == [1, 2, 3]
    assert len(summary['identicalCaptureGroups']) == 4
    for group in summary['identicalCaptureGroups']:
        assert len({sha(archive.read(group['run'] + '/captures/' + name)) for name in group['files']}) == 1
    for original, copied in [('resumed_a_clear.png', 'a-clear.png'), ('resumed_a_blur.png', 'a-blur.png')]:
        assert archive.read('resumed/captures/' + original) == (directory / copied).read_bytes()
    assert (directory / 'a-clear.png').read_bytes() != (directory / 'a-blur.png').read_bytes()
    dump = archive.read('resumed/clientA/shutdown-thread-dump.txt').decode('utf-8')
    assert '"DestroyJavaVM"' in dump
    assert sum(line.startswith('"pool-13-thread-') and 'daemon' not in line for line in dump.splitlines()) == 8
    cleanup = json.loads(archive.read('resumed/clientA/forced-cleanup.json').decode('utf-8-sig'))
    assert not cleanup['normalExit'] and cleanup['pid'] == 32976
    prior = json.loads((directory.parent / 'ticket37-ts-same-name-import-repair/manifest.json').read_text())
    classes = prior['artifactSourceBinding']['nodes'][0]['classHashes']
    assert len(classes) == 14
    for run, folder in [('initial', 'ticket37-client93-visual'), ('resumed', 'ticket37-client93-resumed')]:
        root = project / 'build' / folder
        preparation = json.loads(archive.read(run + '/preparation.json'))
        properties = dict(line.split('=', 1) for line in (root / 'server/server.properties').read_text().splitlines() if '=' in line)
        secret = properties['rcon.password'].encode()
        assert all(secret not in archive.read(name) for name in archive.namelist())
        for node in ('server', 'clientA', 'clientB'):
            jar_path = root / node / 'mods' / preparation['artifact']
            assert sha(jar_path.read_bytes()) == preparation['artifactSha256']
            with ZipFile(jar_path) as jar:
                assert all(sha(jar.read(name)) == expected for name, expected in classes.items())
result = dict(archiveHashAndAllEntries='PASS', actualLocalCredentials='PASS', sixLocalJarBindings='PASS',
              twoRealAcks='PASS', fourIdenticalCaptureGroupsRetained=True, normalClientExit='FAIL',
              nativeClientOverall='FAIL', firstPixelFrame='NOT_VERIFIED', overall='NOT_COMPLETE')
print(json.dumps(result))
(directory / 'archive-verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
