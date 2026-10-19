"""Verify the component contrast and exact restoration without running Minecraft."""
import hashlib
import json
import math
import statistics
import zipfile
from pathlib import Path

directory = Path(__file__).resolve().parent
archive = directory / 'evidence.zip'
binding = json.loads((directory / 'verification.json').read_text(encoding='utf-8'))
assert hashlib.sha256(archive.read_bytes()).hexdigest() == binding['sha256']
assert archive.stat().st_size == binding['bytes']
with zipfile.ZipFile(archive) as evidence:
    assert len(evidence.namelist()) == binding['entries']
    manifest = json.loads(evidence.read('manifest.json'))
    for name, expected in manifest.items():
        assert hashlib.sha256(evidence.read(name)).hexdigest() == expected, name
    contrast = json.loads(evidence.read('contrast.json'))
    summary = json.loads(evidence.read('summary.json'))
    assert contrast['diagnosticOnly'] and contrast['successfulTreesOnly']
    assert contrast['fixtures'] == 6 and contrast['currentSchemaCases'] == 3
    assert contrast['diagnosticsPerSix'] == 0 and len(contrast['samples']) == 40
    arms = {}
    for row in contrast['samples']:
        assert row['rounds'] == 20 and row['nanos'] > 0
        arms.setdefault(row['arm'], {})[row['pair']] = row['nanos'] / row['rounds'] / 1e6
    baseline = arms['per-call-parse']
    reuse = arms['successful-syntax-reuse']
    assert set(baseline) == set(reuse) == set(range(1, 21))
    assert math.isclose(statistics.mean(baseline.values()), summary['baselineMsPerSix'])
    assert math.isclose(statistics.mean(reuse.values()), summary['reuseMsPerSix'])
    assert math.isclose(summary['baselineMsPerSix'] - summary['reuseMsPerSix'], summary['savedMsPerSix'])
    assert sum(reuse[i] < baseline[i] for i in baseline) == summary['reuseWinningPairs']
    restoration = json.loads(evidence.read('restoration.json'))
    formal = json.loads(evidence.read('formal-source-binding.json'))
    assert restoration['sourceRestored'] and restoration['worktreeClean']
    assert restoration['classesMatchFormal'] and restoration['jarMatchesFormal']
    assert restoration['classCount'] == len(formal['classBindings']) == 1627
    assert restoration['jarSha256'] == formal['jarSha256']
    samples = [json.loads(line) for line in evidence.read('session/samples.jsonl').decode('utf-8-sig').splitlines() if line.strip()]
    assert len(samples) == 1
    row = samples[0]
    assert row['stop_channel'] == 'rcon' and not row['forced_kill'] and not row['killed']
    assert 'phase=COMMIT - no errors.' in row['rcon_response']
    for name in ('build-diagnostic', 'run-diagnostic', 'build-restored'):
        assert json.loads(evidence.read(name + '-result.json'))['exitCode'] == 0
print('PASS: complete component pairs, normal shutdown, and restoration binding; formal verdict remains NOT_PASS')
