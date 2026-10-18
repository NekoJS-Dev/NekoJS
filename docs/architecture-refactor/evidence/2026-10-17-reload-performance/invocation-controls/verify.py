"""Recompute local RCON controls; these do not replace any formal game measurements."""
import hashlib
import json
import statistics
import zipfile
from pathlib import Path


def verify():
    root = Path(__file__).resolve().parent
    manifest = json.loads((root/'manifest.json').read_text())
    packed = root/'evidence.zip'
    assert hashlib.sha256(packed.read_bytes()).hexdigest() == manifest['archiveSha256']
    with zipfile.ZipFile(packed) as archive:
        assert len(archive.namelist()) == len(set(archive.namelist()))
        assert set(archive.namelist()) == set(manifest['entries'])
        for name, expected in manifest['entries'].items():
            data = archive.read(name)
            assert len(data) == expected['bytes'] and hashlib.sha256(data).hexdigest() == expected['sha256'], name
        client = archive.read('tooling/original-rcon.py')
        for label, arms in [('native-direct', ['native', 'direct']),
                            ('client-path', ['native-d', 'direct-d', 'native-c', 'direct-c'])]:
            observations = json.loads(archive.read(label+'/observations.json').decode('utf-8-sig'))
            summary = json.loads(archive.read(label+'/summary.json'))
            server = json.loads(archive.read(label+'/server.json'))
            rows = observations['rows']
            assert len(rows) == len(arms)*20 and summary['commands'] == len(rows)
            assert len(server['connections']) == len(rows) and not server['failures']
            assert observations['diagnosticOnly'] and observations['noGame']
            assert observations['powerShellVersion'].startswith('5.1.')
            assert all(row['response'] == 'control-ok' for row in rows)
            assert summary['sourceHashes']['rcon.py'] == hashlib.sha256(client).hexdigest()
            assert summary['sourceHashes']['sample.ps1'] == '1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4'
            for arm in arms:
                selected = [row for row in rows if row['arm'] == arm]
                assert [row['pair'] for row in selected] == list(range(1, 21))
                assert statistics.mean(row['elapsedMs'] for row in selected) == summary['meanMs'][arm]
            assert summary['meanServerServiceMs'] == statistics.mean(row['serviceMs'] for row in server['connections'])
        failed = json.loads(archive.read('failed-native-direct/server.json'))
        assert len(failed['connections']) == 1 and not failed['failures']
        assert b'Process]::Start($info)' in archive.read('failed-native-direct/stderr.log')
        throttle = archive.read('gradle/ThrottlingOutputEventListener.txt').decode()
        assert 'org.gradle.internal.console.throttle' in throttle and 'bipush        100' in throttle
        assert 'scheduleAtFixedRate' in throttle
        plain = archive.read('gradle/OutputEventRenderer.txt').decode()
        assert 'addPlainConsole(java.io.OutputStream, java.io.OutputStream)' in plain and 'Method throttled:' in plain
        relay = archive.read('gradle/LogToClient$AsynchronousLogDispatcher.txt').decode()
        assert 'long 10l' in relay and 'Thread.sleep' in relay
    print(json.dumps(dict(integrity='PASS', diagnosticOnly=True, controlCommands=120,
                         gamePerformancePassNotEstablished=True, runtimeConfigurationChanged=False)))


if __name__ == '__main__':
    verify()
