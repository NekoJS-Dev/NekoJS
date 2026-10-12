import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

directory = Path(__file__).resolve().parent
project = directory.parents[3]
sha = lambda data: hashlib.sha256(data).hexdigest()
manifest = json.loads((directory / 'manifest.json').read_text())
archive_path = directory / manifest['archive']['file']
assert sha(archive_path.read_bytes()) == manifest['archive']['sha256']
assert archive_path.stat().st_size == manifest['archive']['bytes']
with ZipFile(archive_path) as archive:
    assert archive.testzip() is None
    assert set(archive.namelist()) == set(manifest['entries']) and len(archive.namelist()) == 93
    for name, entry in manifest['entries'].items():
        data = archive.read(name)
        assert len(data) == entry['bytes'] and sha(data) == entry['sha256']
        assert 'server.properties' not in name and 'owned-java-args' not in name
    summary = json.loads(archive.read('runtime-summary.json'))
    assert summary == json.loads((directory / 'runtime-summary.json').read_text())
    assert summary['sourceRevision'] == 'd10912ebbf24d9d5df75a908dc7a5529da09979a'
    assert summary['directExits'] == dict(server=0, clientA=4294967295, clientB=0)
    assert not summary['bridgeProductFixed'] and not summary['bridgeJarChanged']
    assert summary['firstPixelFrame'] == 'NOT_VERIFIED' and summary['overall'] == 'NOT_COMPLETE'
    assert summary['closeProbes']['A']['result'] == 'FAIL' and summary['closeProbes']['A']['directExit'] is None
    assert summary['closeProbes']['B']['result'] == 'PASS' and summary['closeProbes']['B']['directExit']['exitCode'] == 0
    for mode in ('original', 'dispose-executor', 'no-request'):
        record = json.loads(archive.read('runtime/bridge-' + mode + '/result.json'))
        assert record == summary['minimalActualBridgeProbes'][mode]
        assert record['bridgeStopReturned']
        assert record['forcedCleanup'] == (mode == 'original')
        assert record['statusRequestVerified'] == (mode != 'no-request')
        assert record['result'] == ('FAIL' if mode == 'original' else 'PASS')
    originals = []
    for letter, names in summary['nativeScreenshots'].items():
        assert len(names) == 4
        for index, name in enumerate(names):
            data = archive.read('runtime/client' + letter + '/screenshots/' + name)
            target = directory / (letter.lower() + '-' + ['clear', 'blur', 'reload-blur', 'reload-clear'][index] + '.png')
            assert data == target.read_bytes()
            originals.append(sha(data))
        assert [row['generation'] for row in summary['firstHud'][letter]] == [1, 2]
        assert summary['firstHud'][letter][-1]['declared'] and summary['firstHud'][letter][-1]['active']
    assert len(set(originals)) == 8
    root = project / 'build/ticket37-client-lifecycle-d109'
    properties = dict(line.split('=', 1) for line in (root / 'server/server.properties').read_text().splitlines() if '=' in line)
    secret = properties['rcon.password'].encode()
    assert all(secret not in archive.read(name) for name in archive.namelist())
    preparation = json.loads(archive.read('runtime/preparation.json'))
    assert len(summary['compiledClassBindings']) == 1592
    for node in ('server', 'clientA', 'clientB'):
        jar_path = root / node / 'mods' / preparation['artifact']
        assert sha(jar_path.read_bytes()) == summary['artifactSha256']
        with ZipFile(jar_path) as jar:
            assert all(sha(jar.read(name)) == expected for name, expected in summary['compiledClassBindings'].items())
    assert (root / 'clientA/mods/minecraft-mcp-26.2-neoforge.jar').is_file()
    assert not (root / 'clientB/mods/minecraft-mcp-26.2-neoforge.jar').exists()
result = dict(archiveAndAllEntries='PASS', actualCredentials='PASS', threeJar1592ClassBindings='PASS',
    eightDistinctNativeFramesRetained=True, originalBridgeShutdown='FAIL', isolatedExecutorDisposal='PASS',
    noBridgeClientExit='PASS', bridgeProductFixed=False, nativeCrashRootCause='NOT_ESTABLISHED', overall='NOT_COMPLETE')
print(json.dumps(result))
(directory / 'archive-verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
