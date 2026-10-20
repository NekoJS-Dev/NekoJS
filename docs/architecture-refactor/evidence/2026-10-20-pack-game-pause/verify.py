"""Validate the failed empty-server observation without restarting a game."""
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
    result = json.loads(frozen.read('result.json'))
    assert not result['accepted'] and result['ready'] and result['normalStop']
    assert result['launcherExitCode'] == 0 and result['forcedOwnedGamePids'] == []
    commands = json.loads(frozen.read('commands.json'))
    reloads = [row for row in commands if row['command'] == 'nekojs reload']
    assert len(reloads) == 44 and all('phase=COMMIT - no errors.' in row['response'] for row in reloads)
    assert 'Immediate did not run once:' in commands[-2]['response']
    assert commands[-2]['command'] == 'packprobe sample window40 4'
    assert commands[-1]['command'] == 'stop'
    assert 'Server empty for 60 seconds, pausing' in frozen.read('game-latest.log').decode('utf-8')
    rows = [json.loads(line) for line in frozen.read('resources.jsonl').splitlines()]
    assert [row['checkedTicks'] for row in rows[-3:]] == [643, 643, 643]
    memory = json.loads(frozen.read('memory.json'))
    assert len(memory) == 8 and all(row['pid'] == 2324 for row in memory)
    engine = json.loads(frozen.read('started.json'))
    assert engine['engineImplementation'] == 'Interpreted' and engine['supportsCompilation'] is False
print('PASS: failed v3 retained, native pause located, no full resource-acceptance claim')
