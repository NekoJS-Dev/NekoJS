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
expected = json.loads((base / 'v4-verification.json').read_text(encoding='utf-8'))
archive = base / 'v4-evidence.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest() == expected['archiveSha256']
with zipfile.ZipFile(archive) as frozen:
    for name, digest in expected['files'].items():
        assert hashlib.sha256(frozen.read(name)).hexdigest() == digest, name
    result = json.loads(frozen.read('v4/result.json'))
    assert result['accepted'] is False and result['pid'] == 13272 and not result['remoteSyncAccepted']
    assert result['completed'] == ['initial', 'world-a', 'f3-t-world-a', 'after-a', 'world-b',
                                   'after-b', 'world-a-return', 'after-a-return']
    assert 'Unexpected untrusted rejection:' in result['failure']
    assert 'Run /nekojs trust kubernetes.docker.internal' in result['failure']
    rows = [json.loads(line) for line in frozen.read('v4/resources.jsonl').splitlines()]
    assert [row['label'] for row in rows] == result['completed']
    assert {row['pid'] for row in rows} == {13272}
    assert json.loads(frozen.read('v4/server-result.json')) == dict(
        normalStop=True, forcedOwnedGamePids=[], launcherExitCode=0)
    review = json.loads(frozen.read('v4/visual-review.json'))
    assert review['allThreeViewed'] and review['humanMaintainerAccepted'] is False
    assert len(review['images']) == 3 and result['screenshotsReviewed'] is False
    for row in review['images']:
        name = 'v4/screenshots/' + Path(row['path'].replace('\\', '/')).name
        assert hashlib.sha256(frozen.read(name)).hexdigest() == row['sha256']
print('PASS: v2/v3/v4 failures retained, complete local v4 phases and primary visual review verified; remote incomplete')
