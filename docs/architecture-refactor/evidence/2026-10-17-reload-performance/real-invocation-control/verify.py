"""Verify the real-game caller contrast without changing the formal performance verdict."""
import hashlib
import json
import math
import statistics
import zipfile
from pathlib import Path

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
    load = lambda name: json.loads(archive.read(name).decode('utf-8-sig'))
    rows = load('control/observations.json')
    summary = load('control/summary.json')
    bindings = load('tooling/real-rcon-control-bindings.json')
    formal = load('formal-preparation.json')
    assert bindings['revision'] == formal['revision'] == '6e5bb8f00c3747b828056589853cb0471477b18a'
    assert bindings['classBindings'] == formal['classBindings'] and len(formal['classBindings']) == 1625
    assert bindings['formalVerdictUnchanged'] == 'FAIL' and bindings['firstSamplesRetained']
    sampler = archive.read('fixed-harness/sample.ps1')
    original = load('redactions.json').get('fixed-harness/sample.ps1', {}).get('originalSha256', hashlib.sha256(sampler).hexdigest())
    assert original == formal['samplerSha256'] == '1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4'
    assert len(rows) == summary['commands'] == 40 and summary['diagnosticOnly']
    generations = []
    for pair in range(1, 21):
        selected = [row for row in rows if row['pair'] == pair]
        assert [row['arm'] for row in selected] == (['native', 'direct'] if pair % 2 else ['direct', 'native'])
        for row in selected:
            assert row['markerMs'] >= row['rconMs'] > 0
            assert 'phase=COMMIT - no errors.' in row['response']
            generations.append(int(row['response'].split('generation=')[1].split()[0]))
        difference = sum(row['rconMs']*(1 if row['arm'] == 'native' else -1) for row in selected)
        assert math.isclose(difference, summary['nativeMinusDirectRconPairedMs'][pair-1], abs_tol=1e-9)
    assert generations == list(range(2, 42))
    for arm in ['native', 'direct']:
        selected = [row for row in rows if row['arm'] == arm]
        for metric in ['rconMs', 'markerMs']:
            assert math.isclose(statistics.mean(row[metric] for row in selected), summary['groups'][arm][metric], abs_tol=1e-9)
        assert summary['groups'][arm]['markerMs'] > 285.3
    session = load('control/session.json')
    assert not session['forcedKill'] and not session['extra']['killed'] and session['extra']['stopChannel'] == 'rcon'
    assert session['doneWallMs'] > 0 and session['extra']['stopSent']
    assert load('tooling/real-rcon-control-exit.json')['exitCode'] == 0
    assert b'REAL_RCON_CONTROL_COMPLETE' in archive.read('tooling/real-rcon-control-controller.log')
print(json.dumps(dict(integrity='PASS', diagnosticCommands=40, formalPerformanceVerdict='FAIL', means=summary['groups'])))
