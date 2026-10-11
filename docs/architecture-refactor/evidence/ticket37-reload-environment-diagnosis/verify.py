import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

root = Path.cwd().resolve()
out = root / 'docs/architecture-refactor/evidence/ticket37-reload-environment-diagnosis'
summary = json.loads((out / 'summary.json').read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()
archive_path = out / summary['archive']['file']
assert sha(archive_path.read_bytes()) == summary['archive']['sha256']
assert summary['currentFormalPerformance'] == 'FAIL retained'
expected_markers = [[407,224,342,207,219], [378,201,344,310,234], [355,218,209,175,233]]
with ZipFile(archive_path) as archive:
    assert archive.testzip() is None
    assert len(archive.namelist()) == summary['archive']['entries'] == len(summary['entries'])
    assert len(set(archive.namelist())) == len(archive.namelist())
    for entry in summary['entries']:
        data = archive.read(entry['path'])
        assert len(data) == entry['bytes'] and sha(data) == entry['sha256']
        assert 'local-only' not in entry['path'] and not entry['path'].endswith('.jfr') and 'server.properties' not in entry['path']
    for index, group in enumerate(summary['groups']):
        folder = root / 'build/ticket37-perf-owned-ac810fa7/bench/perf/out' / group['folder']
        data = archive.read('samples/' + group['folder'] + '/samples.jsonl')
        assert data == (folder / 'samples.jsonl').read_bytes()
        rows = [json.loads(line) for line in data.decode('utf-8-sig').splitlines()]
        assert rows == group['samples'] and len(rows) == 5
        assert [row['marker_ms'] for row in rows] == expected_markers[index] == group['markerMs']
        assert [row['index'] for row in rows] == [1,2,3,4,5]
        assert all(row['rev'] == group['sourceRevision'][:8] and row['stop_channel'] == 'rcon' and not row['killed'] and not row['forced_kill'] for row in rows)
        assert group['markerMeanMs'] == sum(group['markerMs']) / 5
        assert group['rconRttMeanMs'] == sum(group['rconRttMs']) / 5
        assert group['markerTailMs'] == [m-r for m,r in zip(group['markerMs'], group['rconRttMs'])]
        assert group['markerTailMeanMs'] == sum(group['markerTailMs']) / 5
        assert group['thresholdMs'] == 285.3
        assert group['diagnosticGate'] == ('INSTRUMENTED: not acceptance' if group['instrumented'] else ('PASS' if group['markerMeanMs'] <= 285.3 else 'FAIL'))
        exit_file = 'sampler-exit.json' if group['instrumented'] else 'reload-control.txt.exit.json'
        assert json.loads(archive.read('wrapper/' + group['group'] + '/' + exit_file))['exitCode'] == 0
    aggregate = json.loads(archive.read('wrapper/owned-current-profile/profile-aggregate.json'))
    phases = json.loads(archive.read('wrapper/owned-current-profile/profile-phase-counts.json'))
    assert aggregate['transactionSampleCount'] == len(aggregate['transactionSamples']) == phases['transactionSamples'] == 19
    assert aggregate['serverThreadSampleCount'] == 86
    assert len(phases['probeObservations']) == 1 and phases['probeObservations'][0]['thread'] == 'modloading-worker-0'
    assert summary['profile'] == phases
assert len(summary['currentCompiledClasses']) == 14
for row in summary['currentCompiledClasses']:
    assert sha((root / 'build/ticket37-perf-owned-ac810fa7' / row['path']).read_bytes()) == row['sha256']
record = dict(archiveSha256=summary['archive']['sha256'], fullHashReadback=True,
              rawAllFiveSamplesAndFirstsRetained=True, samplerDirectExits=[0,0,0],
              thresholdsAndDiagnosticOnlyProfileVerified=True, current14CompiledHashesVerified=True,
              rawJfrAndEventPayloadsExcluded=True, currentFormalPerformance='FAIL retained',
              rootCause='NOT ESTABLISHED', overallTickets='NOT COMPLETE', maintainer='NOT RECORDED')
with (out / 'archive-verification.json').open('x', encoding='utf-8') as output:
    output.write(json.dumps(record, indent=2) + '\n')
print(json.dumps(record))
