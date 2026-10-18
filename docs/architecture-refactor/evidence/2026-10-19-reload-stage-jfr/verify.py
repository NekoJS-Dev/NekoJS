import hashlib
import json
import re
import statistics
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
data = (root / 'evidence.zip').read_bytes()
assert len(data) == manifest['archiveBytes']
assert hashlib.sha256(data).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert hashlib.sha256(archive.read(name)).hexdigest() == expected, name
    def load(name):
        return json.loads(archive.read(name).decode('utf-8-sig'))
    rows = load('session-v2/observations.json')
    state = load('session-v2/session.json')
    analysis = load('analysis.json')
    summary = load('summary.json')
    assert not state['forcedKill'] and not state['extra']['killed'] and state['extra']['stopChannel'] == 'rcon'
    assert load('tools/stage-jfr-v2-exit.json')['exitCode'] == 0
    assert load('tools/stage-jfr-exit.json')['exitCode'] == 1
    assert load('first-failure.json')['reloadSamples'] == 0
    assert len(rows) == 40
    for pair in range(1, 21):
        selected = rows[(pair-1)*2:pair*2]
        assert all(row['pair'] == pair for row in selected)
        expected = ['list', 'nekojs reload'] if pair % 2 else ['nekojs reload', 'list']
        assert [row['command'] for row in selected] == expected
        for row in selected:
            assert row['rconMs'] > 0
            if row['command'] == 'list':
                assert 'There are 0 of a max of 20 players online:' in row['response']
            else:
                assert 'phase=COMMIT - no errors.' in row['response'] and row['markerMs'] >= row['rconMs']
    assert len(analysis['stages']) == 60
    trace = archive.read('session-v2/stages-stderr.log').decode('utf-8-sig')
    events = re.findall(r'\[DEBUG-STAGE-JFR-19\] stage=(root|recipe|command) wallNs=(\d+) cpuNs=(\d+)', trace)
    assert len(events) == 60
    for stage in ['root', 'recipe', 'command']:
        jfr = [row for row in analysis['stages'] if row['stage'] == stage]
        logged = [(int(wall), int(cpu)) for name, wall, cpu in events if name == stage]
        assert len(jfr) == len(logged) == 20
        assert [(row['wallNs'], row['cpuNs']) for row in jfr] == logged
        assert all(row['thread'] == 'Server thread' for row in jfr)
        for key, raw in [('wallMs', 'wallNs'), ('cpuMs', 'cpuNs')]:
            assert abs(summary['stages'][stage][key] - statistics.mean(row[raw]/1e6 for row in jfr)) < 1e-7
    for key, command, raw in [('reloadRconMs','nekojs reload','rconMs'), ('queryRconMs','list','rconMs'), ('markerMs','nekojs reload','markerMs')]:
        assert abs(summary[key] - statistics.mean(row[raw] for row in rows if row['command'] == command)) < 1e-7
    binding = load('binding.json')
    assert binding['sourceRestored'] and len(binding['classes']) == 1626
    assert binding['jfrSha256'] == manifest['rawJfrSha256']
    assert load('restoration.json')['commandRestored'] and load('restoration.json')['noDiagnosticClasses']
    assert summary['formalVerdictUnchanged'] == manifest['formalVerdictUnchanged'] == 'FAIL'
    print(json.dumps(dict(evidenceVerification='PASS', diagnosticOnly=True, pairs=20,
        reloadRconMs=summary['reloadRconMs'], queryRconMs=summary['queryRconMs'],
        stages=summary['stages'], originalGateUnchanged='FAIL', rawJfrIncluded=False), indent=2))
