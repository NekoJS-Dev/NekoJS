"""Recompute CPU counters and the bounded preflight contrast; formal FAIL remains unchanged."""
import hashlib
import json
import math
import re
import statistics
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root/'manifest.json').read_text())
packed = root/'evidence.zip'
assert hashlib.sha256(packed.read_bytes()).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(packed) as archive:
    names = archive.namelist()
    assert len(names) == len(set(names)) and set(names) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        data = archive.read(name)
        assert len(data) == expected['bytes'] and hashlib.sha256(data).hexdigest() == expected['sha256'], name
    load = lambda name: json.loads(archive.read(name).decode('utf-8-sig'))
    close = lambda a,b: math.isclose(a,b,abs_tol=1e-8)
    rows, summary = load('cpu/observations.json'), load('cpu/summary.json')
    assert [r['index'] for r in rows] == list(range(1,11))
    assert all('phase=COMMIT - no errors.' in r['response'] for r in rows)
    pattern = r'\[DEBUG-CPU-18\] stage=(root|recipe) wallNs=(\d+) threadCpuNs=(\d+) processCpuNs=(\d+) gcMs=(\d+) jitMs=(\d+)'
    raw = re.findall(pattern, archive.read('cpu/cpu-stderr.log').decode('utf-8', errors='replace'))
    assert [r[0] for r in raw] == ['root','recipe']*10
    for stage in ['root','recipe']:
        chosen = [row for row in raw if row[0] == stage]
        for index,key in enumerate(['wallMs','threadCpuMs','processCpuMs','gcMs','jitMs'],1):
            mean = statistics.mean(int(row[index])/(1e6 if index <= 3 else 1) for row in chosen)
            assert close(mean,summary['stages'][stage][key])
    assert close(statistics.mean(r['rconMs'] for r in rows),summary['meanRconMs'])
    assert close(statistics.mean(r['markerMs'] for r in rows),summary['meanMarkerMs'])
    wall = sum(int(row[1])/1e6 for row in raw)/10
    assert close(summary['meanRconMs']-wall,summary['meanOutsideStagesMs'])
    assert summary['formalVerdictUnchanged'] == 'FAIL'
    session = load('cpu/session.json')
    assert not session['forcedKill'] and not session['extra']['killed'] and session['extra']['stopChannel'] == 'rcon'
    assert load('tooling/cpu-exit.json')['exitCode'] == 0
    restored, formal = load('cpu/restoration.json'), load('formal-preparation.json')
    assert restored['classBindings'] == formal['classBindings'] and restored['compiledClasses'] == 1625
    assert restored['classesMatchOriginalFormal'] and restored['cleanCheckout'] and not restored['restoredBuildPending']
    instrumented = load('cpu/instrumented-classes.json')
    assert len(instrumented['classes']) == 1626
    assert instrumented['changedFromFormal'] == ['com/tkisor/nekojs/command/NekoJSCommands$ReloadCpuStamp.class','com/tkisor/nekojs/command/NekoJSCommands.class']
    binding = load('tooling/cpu-source-binding.json')
    assert hashlib.sha256(archive.read('tooling/cpu-original-command.java')).hexdigest() == binding['originalSha256']
    bad, fixed = archive.read('encoding-failure/instrumented-source-cp936.java'), archive.read('cpu/instrumented-command.java')
    assert bad.decode('cp936').encode('utf-8') == fixed
    assert hashlib.sha256(fixed).hexdigest() == binding['instrumentedSha256']
    failures = load('encoding-failure/summary.json')['failures']
    assert len(failures) == 10 and len({r['className'] for r in failures}) == 6
    assert all('MalformedInputException' in r['message'] or 'IllegalStateException' in r['message'] for r in failures)
    test_totals = {}
    for prefix in ['encoding-failure/test-results/','cpu/instrumented-test-results/']:
        counts = [0,0,0,0]
        for name in names:
            if name.startswith(prefix) and name.endswith('.xml'):
                suite = ET.fromstring(archive.read(name))
                for index,key in enumerate(['tests','failures','errors','skipped']): counts[index] += int(suite.get(key,0))
        test_totals[prefix] = counts
    assert test_totals['encoding-failure/test-results/'][1] == 10
    assert test_totals['cpu/instrumented-test-results/'][1:3] == [0,0]
    contrast, measured = load('preflight/contrast.json'), load('preflight/summary.json')
    assert len(contrast) == 20
    for pair in range(10):
        assert [r['arm'] for r in contrast if r['pair'] == pair] == (['baseline','capture-replay'] if pair % 2 == 0 else ['capture-replay','baseline'])
    for arm in ['baseline','capture-replay']:
        assert close(statistics.mean(r['nanos']/r['rounds']/1e6 for r in contrast if r['arm'] == arm), measured['meanMsPerSix'][arm])
    assert measured['diagnosticsPerSix'] == len(load('preflight/diagnostics.json')) == 3
    assert all(r['reports'] == 120 for r in contrast)
    assert load('prior-jfr/summary.json')['serverRootSamples'] == 74
    assert b'ALL_RELOAD_SAMPLES=74' in archive.read('prior-jfr/windowed-profile.txt')
    assert not load('prior-jfr/input-binding.json')['originalBinaryArchived']
print(json.dumps(dict(integrity='PASS', diagnostics=10, means=summary, sourceRestoration='PASS', testTotals=test_totals)))
