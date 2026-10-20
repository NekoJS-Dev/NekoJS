"""Verify the frozen native package resource and memory observations."""
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
    assert result['accepted'] and result['ready'] and result['normalStop']
    assert result['launcherExitCode'] == 0 and result['forcedOwnedGamePids'] == []
    assert result['formalPerformanceMeasured'] is False
    commands = json.loads(frozen.read('commands.json'))
    reloads = [row for row in commands if row['command'] == 'nekojs reload']
    assert len(reloads) == 55 and all('phase=COMMIT - no errors.' in row['response'] for row in reloads)
    assert all(row['exitCode'] == 0 for row in commands)
    assert commands[-1]['command'] == 'stop'
    rows = [json.loads(line) for line in frozen.read('resources.jsonl').splitlines()]
    commits = [row for row in rows if row['stage'] == 'commit']
    samples = [row for row in rows if row['stage'] == 'sample']
    assert len(commits) == 55 and len(samples) == 11
    assert [row['label'] for row in commits] == ['scale1', 'scale4', 'scale8', 'steady4'] + [
        'reload' + str(index) for index in range(1, 51)] + ['disabled']
    assert all(row['serverContexts'] == row['packs'] + 1 and
               row['registeredContexts'] == row['packs'] + 2 and not row['callbackFailures'] for row in rows)
    assert all(row['lastPeak'] == row['expectedPeak'] for row in commits)
    assert all(row['observedCollected'] == row['retiredContexts'] for row in samples)
    assert all(len(row['immediateCounts']) == row['packs'] and
               all(count == 1 for count in row['immediateCounts'].values()) for row in samples)
    assert all(after['checkedTicks'] > before['checkedTicks'] for before, after in zip(samples, samples[1:]))
    assert samples[-1]['retiredContexts'] == 272 and samples[-1]['checkedTicks'] == 729
    memory = json.loads(frozen.read('memory.json'))
    assert len(memory) == 11 and all(row['process']['owned'] and row['pid'] == 4596 for row in memory)
    assert [row['resourceRow'] for row in memory] == samples
    assert all(row['nmtTotalCommittedKiB'] == row['nmtHeapCommittedKiB'] + row['nmtNonHeapCommittedKiB'] for row in memory)
    assert memory[4]['nmtHeapCommittedKiB'] == 416 * 1024 and memory[-1]['nmtHeapCommittedKiB'] == 640 * 1024
    engine = json.loads(frozen.read('started.json'))
    assert engine['engineImplementation'] == 'Interpreted' and engine['supportsCompilation'] is False
    assert 'Server empty for 60 seconds, pausing' not in frozen.read('game-latest.log').decode('utf-8')
    assert json.loads(frozen.read('public-server-configuration.json'))['pause-when-empty-seconds'] == 0
    binding = json.loads(frozen.read('source-binding.json'))
    assert binding['revision'].startswith('4c2fd226')
    assert hashlib.sha256(frozen.read('PackGameObservation.java')).hexdigest() == binding['observerSha256']
    observer = json.loads(frozen.read('observer-binding.json'))
    assert hashlib.sha256(frozen.read('observer.jar')).hexdigest() == observer['sha256']
    checks = base.parent / '2026-10-20-fabric-final-checks'
    receipt = json.loads((checks / 'verification.json').read_text(encoding='utf-8'))
    assert hashlib.sha256((checks / 'evidence.zip').read_bytes()).hexdigest() == receipt['archiveSha256']
    with zipfile.ZipFile(checks / 'evidence.zip') as build:
        assert binding['classes'] == json.loads(build.read('production-bindings.json'))['26.2.0-fabric']['classes']
print('PASS: 55 commits, 50 steady reloads, 272 retired Contexts collected, 11 native memory samples, normal exit')
