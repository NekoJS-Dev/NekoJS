import hashlib
import json
import re
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parent
summary = json.loads((root / 'summary.json').read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()
archive = root / summary['archive']['file']
assert archive.stat().st_size == summary['archive']['bytes'] and sha(archive.read_bytes()) == summary['archive']['sha256']
with ZipFile(archive) as source:
    assert source.testzip() is None
    assert set(source.namelist()) == {row['name'] for row in summary['entries']}
    for row in summary['entries']:
        data = source.read(row['name'])
        assert len(data) == row['bytes'] and sha(data) == row['sha256']
    manifest = json.loads(source.read('diagnostic/manifest.json'))
    assert sha(source.read('diagnostic/instrumentation.patch')) == manifest['instrumentationPatchSha256'] == summary['instrumentationPatchSha256']
    for name, hashes in manifest['files'].items():
        assert sha(source.read('diagnostic/original/' + name)) == hashes['originalSha256']
        assert sha(source.read('diagnostic/instrumented/' + name)) == hashes['instrumentedSha256']
    assert json.loads(source.read('sampler/binding.json'))['sha256'] == '1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4'
    assert 'sampler/sample.ps1' not in source.namelist()
    for name in ('runner', 'sampler', 'before-guard', 'after-guard'):
        assert json.loads(source.read('diagnostic/' + name + '-exit.json'))['exitCode'] == 0
    assert json.loads(source.read('diagnostic/restored-source.json'))['sourceRestoredByteEqual']
    samples = [json.loads(line) for line in source.read('samples/samples.jsonl').decode('utf-8-sig').splitlines() if line.strip()]
    assert len(samples) == 5 and [row['index'] for row in samples] == [1, 2, 3, 4, 5]
    assert all(row['stop_channel'] == 'rcon' and not row['killed'] and not row['forced_kill'] for row in samples)
    raw = source.read('samples/reload-session-stderr.log').decode('utf-8')
    events = re.findall(r'\[DEBUG-7d-phase\] ([-\w]+) type=(\S+) nano=(\d+) epoch=(\d+)(?: message=([^\r\n]*))?', raw)
    roots = [row for row in events if row[0] == 'root-begin' and row[1] == 'server']
    ends = [row for row in events if row[0] == 'root-after-manager' and row[1] == 'server']
    assert len(roots) == len(ends) == 6
    initial = summary['initialPreReadinessRoot']
    assert initial['start']['nano'] == int(roots[0][2]) and initial['end']['nano'] == int(ends[0][2])
    assert initial['end']['epochMs'] < summary['readinessEpochMsFloor']
    assert '[' + summary['readinessClock'] + ']' in source.read('samples/reload-session-stdout.log').decode('utf-8')
    for sample, start, end, reload in zip(samples, roots[1:], ends[1:], summary['reloads']):
        duration = (int(end[2]) - int(start[2])) / 1e6
        assert reload['index'] == sample['index']
        assert reload['rootThroughManagerMs'] == duration
        assert reload['rconRttMs'] == sample['rcon_rtt_ms'] and reload['markerMs'] == sample['marker_ms']
        assert reload['rttMinusObservedRootMs'] == sample['rcon_rtt_ms'] - duration
        assert reload['markerMinusRttMs'] == sample['marker_ms'] - sample['rcon_rtt_ms']
        assert len(reload['phases']) == 11
        prior = int(start[2])
        for event, phase in zip(reload['events'][1:], reload['phases']):
            assert phase['intervalFromPriorEventMs'] == (event['nano'] - prior) / 1e6
            assert phase['elapsedFromRootMs'] == (event['nano'] - int(start[2])) / 1e6
            prior = event['nano']
    for key, value in summary['means'].items():
        assert value == sum(row[key] for row in summary['reloads']) / 5
assert summary['diagnosticOnly'] and not summary['formalReplacement']
assert summary['originalFormalPerformance'] == 'FAIL_RETAINED' and summary['synchronousObserverOverhead'] == 'NOT_QUANTIFIED'
assert summary['sourceRestored']['sourceRestoredByteEqual']
binding = summary['compiledBinding']
assert binding['matchedClasses'] == 1586 and len(binding['changedInstrumentedFamilyClasses']) == 6
assert not binding['officialCertification'] and summary['overallTickets'] == 'NOT_COMPLETE'
print(json.dumps(dict(result='PASS', diagnosticOnly=True, formal='FAIL_RETAINED', means=summary['means'], sha256=summary['archive']['sha256'])))
