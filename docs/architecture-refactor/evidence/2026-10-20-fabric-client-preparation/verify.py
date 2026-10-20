"""Verify the failed offline client preparation and normal server cleanup."""
import hashlib
import json
import zipfile
from pathlib import Path

base = Path(__file__).resolve().parent
expected = json.loads((base / 'verification.json').read_text(encoding='utf-8'))
archive = base / 'evidence.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest() == expected['archiveSha256']
with zipfile.ZipFile(archive) as frozen:
    assert len(frozen.namelist()) == expected['entries']
    for name, digest in expected['files'].items():
        assert hashlib.sha256(frozen.read(name)).hexdigest() == digest, name
    stages = json.loads(frozen.read('v2/execution.json'))
    assert [(row['stage'], row['exitCode']) for row in stages] == [
        ('classpath', 0), ('compile', 0), ('signed-fixture', 0), ('client', 1)]
    assert ':26.2.0-fabric:downloadAssets FAILED' in frozen.read('v2/client.log').decode('utf-8')
    stopped = json.loads(frozen.read('v2/server-result.json'))
    assert stopped == dict(normalStop=True, forcedOwnedGamePids=[], launcherExitCode=0)
    ready = json.loads(frozen.read('v2/server-ready.json'))
    final = json.loads(frozen.read('v2/server-stopped.json'))
    assert ready['pid'] == final['pid'] and final['serverStopped']
    assert 'v2/result.json' not in frozen.namelist()
    assert not any('acceptance-private/' in name or name.endswith(('server.properties', '.pk8')) for name in frozen.namelist())
expected = json.loads((base / 'v3-verification.json').read_text(encoding='utf-8'))
archive = base / 'v3-evidence.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest() == expected['archiveSha256']
with zipfile.ZipFile(archive) as frozen:
    for name, digest in expected['files'].items():
        assert hashlib.sha256(frozen.read(name)).hexdigest() == digest, name
    result = json.loads(frozen.read('v3/result.json'))
    assert result['accepted'] is False and result['pid'] == 25452
    assert result['completed'] == ['initial', 'world-a', 'f3-t-world-a']
    assert 'Translation precedence changed' in result['failure']
    assert result['remoteSyncAccepted'] is False
    assert json.loads(frozen.read('v3/server-result.json')) == dict(
        normalStop=True, forcedOwnedGamePids=[], launcherExitCode=0)
    log = frozen.read('v3/client-latest.log').decode('utf-8')
    assert 'Reloading ResourceManager:' in log and 'Client gametests failed with an exception' in log
    assert frozen.read('v3/world-a-item.png').startswith(b'\x89PNG\r\n\x1a\n')
print('PASS: v2 offline failure and v3 asynchronous-observer failure retained; no full client acceptance claim')
