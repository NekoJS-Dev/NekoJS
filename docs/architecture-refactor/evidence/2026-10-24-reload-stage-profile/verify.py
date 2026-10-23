"""Recount stage observations and check restoration against the frozen production artifact."""
import hashlib
import io
import json
from pathlib import Path
import statistics
import zipfile

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    assert len(archive.namelist()) == len(set(archive.namelist()))
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert not name.startswith(('/', '\\')) and '..' not in Path(name).parts
        data = archive.read(name)
        assert len(data) == expected['size'] and hashlib.sha256(data).hexdigest() == expected['sha256']
    def doc(name):
        return json.loads(archive.read(name).decode('utf-8-sig'))
    assert doc('failed-compile/exit.json')['exitCode'] == 1
    assert b'Compilation failed' in archive.read('failed-compile/session/stages-stderr.log')
    assert doc('complete/exit.json')['exitCode'] == 0
    session = doc('complete/session/session.json')
    assert not session['forcedKill'] and not session['extra']['killed'] and session['extra']['stopChannel'] == 'rcon'
    rows = doc('complete/session/observations.json')
    reloads = [row for row in rows if row['command'] == 'nekojs reload']
    queries = [row for row in rows if row['command'] == 'list']
    assert len(rows) == 60 and len(reloads) == len(queries) == 30
    assert all('reload committed:' in row['response'] and 'no errors.' in row['response'] for row in reloads)
    assert all('There are 0 of a max of' in row['response'] for row in queries)
    summary = doc('complete/summary.json')
    assert summary['diagnosticOnly'] and summary['formalVerdictUnchanged'] == 'FAIL'
    assert abs(summary['reloadRconMs'] - statistics.mean(row['rconMs'] for row in reloads)) < 1e-9
    assert abs(summary['queryRconMs'] - statistics.mean(row['rconMs'] for row in queries)) < 1e-9
    assert abs(summary['markerMs'] - statistics.mean(row['markerMs'] for row in reloads)) < 1e-9
    stages = doc('complete/root-analysis.json')['stages']
    for name, expected in summary['stages'].items():
        selected = [row for row in stages if row['stage'] == name]
        assert len(selected) == 30 and all(row['thread'] == 'Server thread' for row in selected)
        for field, source in [('wallMs', 'wallNs'), ('cpuMs', 'cpuNs')]:
            assert abs(expected[field] - statistics.mean(row[source] / 1e6 for row in selected)) < 1e-9
    restored = doc('complete/restoration.json')
    frozen = doc('frozen-source-binding.json')
    assert restored['sourceRestored'] and restored['cleanCheckout'] and restored['classesMatchFrozenCandidate']
    assert restored['classCount'] == 1641 and restored['restoredBuildExit'] == 0
    reference = manifest['artifactReference']
    external = root / reference['path']
    assert hashlib.sha256(external.read_bytes()).hexdigest() == reference['archiveSha256']
    with zipfile.ZipFile(external) as source:
        production = source.read(reference['entry'])
    assert hashlib.sha256(production).hexdigest() == restored['rebuiltArtifactSha256']
    with zipfile.ZipFile(io.BytesIO(production)) as jar:
        for name, expected in frozen['classBindings'].items():
            assert hashlib.sha256(jar.read(name)).hexdigest() == expected
    binding = doc('complete/binding.json')
    assert binding['revision'] == manifest['sourceRevision'] == restored['productionRevision']
    assert len(binding['bindings']) == 4
    for row in binding['bindings']:
        assert hashlib.sha256(archive.read('complete/' + Path(row['path']).name)).hexdigest() == row['originalSha256']
print(json.dumps(dict(archive='PASS', diagnosticReloads=30, queryControls=30, stageTypes=14,
    normalStops=1, exactRestoredClasses=1641, formalPerformance='FAIL',
    limitation='Derived JFR summaries are recounted; raw JFR is local and not replayed by this verifier.')))
