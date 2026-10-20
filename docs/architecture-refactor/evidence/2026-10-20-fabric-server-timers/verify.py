"""Verify preserved failures, not post-fix runtime acceptance."""
import hashlib
import json
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
summary = json.loads((root / 'verification.json').read_text())
archive = root / 'evidence.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest() == summary['archiveSha256']
with zipfile.ZipFile(archive) as evidence:
    assert len(evidence.namelist()) == summary['archiveEntries'] == 38
    assert set(evidence.namelist()) == set(summary['entries'])
    for name, expected in summary['entries'].items():
        assert hashlib.sha256(evidence.read(name)).hexdigest() == expected, name
        assert name.split('/')[-1] not in ('server.properties', 'eula.txt')
    read = lambda name: json.loads(evidence.read(name))
    first, second = read('v1/result.json'), read('v2/result.json')
    assert not first['accepted'] and not first['ready'] and not first['normalStop']
    assert first['launcherExitCode'] == 0
    assert b'VM option "EnableJVMCI" does not exist' in evidence.read('v1/launcher.log')
    assert not second['accepted'] and second['ready'] and second['normalStop']
    assert second['launcherExitCode'] == 0
    commands = read('v2/commands.json')
    assert len(commands) == 5 and all(row['exitCode'] == 0 for row in commands)
    assert commands[1]['command'] == 'nekojs reload'
    assert 'phase=COMMIT - no errors.' in commands[1]['response']
    assert 'scale1 PASS' in commands[2]['response']
    assert commands[3]['command'] == 'packprobe sample scale1 1'
    assert 'java.lang.AssertionError: Immediate did not run once:' in commands[3]['response']
    assert commands[4]['command'] == 'stop'
    assert commands[4]['response'].strip() == 'Stopping the server'
    rows = [json.loads(line) for line in evidence.read('v2/resources.jsonl').splitlines()]
    assert [(row['label'], row['stage']) for row in rows] == [('legacy', 'sample'), ('scale1', 'commit')]
    assert all(not row['callbackFailures'] for row in rows)
    assert rows[1]['serverContexts'] == 2 and rows[1]['registeredContexts'] == 3
    assert rows[1]['lastPeak'] == rows[1]['expectedPeak'] == 4
    memory = read('v2/memory.json')
    assert len(memory) == 1 and memory[0]['label'] == 'legacy'
    started = read('v2/started.json')
    assert started['engineImplementation'] == 'Interpreted'
    assert started['jsImplementation'] == 'GraalVM JavaScript'
    assert started['enableJVMCI'] == 'unavailable-through-HotSpotDiagnosticMXBean'
    assert b'graalmc 25.1.3.7' in evidence.read('v2/launcher.log')
    for version in (1, 2):
        binding = read(f'v{version}/source-binding.json')
        assert binding['revision'] == summary['revision']
        assert len(binding['classes']) == summary['productionClasses'] == 1569
        observer = read(f'v{version}/observer-binding.json')
        assert hashlib.sha256(evidence.read(f'v{version}/PackGameObservation.java')).hexdigest() == observer['sourceSha256']
        assert hashlib.sha256(evidence.read(f'v{version}/game/mods/neko-pack-observation.jar')).hexdigest() == observer['sha256']
assert not summary['postFixChecksRun'] and not summary['v2Accepted']
print('PASS: preserved preparation failure and native timer failure; post-fix acceptance remains unrun')
