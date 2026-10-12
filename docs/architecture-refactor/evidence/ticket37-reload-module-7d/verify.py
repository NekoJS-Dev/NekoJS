import hashlib
import json
import re
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parent
summary = json.loads((root / 'summary.json').read_text(encoding='utf-8'))
sha = lambda data: hashlib.sha256(data).hexdigest()
archive = root / summary['archive']['file']
assert archive.stat().st_size == summary['archive']['bytes'] and sha(archive.read_bytes()) == summary['archive']['sha256']
with ZipFile(archive) as source:
    assert source.testzip() is None
    assert len(source.namelist()) == summary['archive']['entries'] == len(summary['entries'])
    assert set(source.namelist()) == {row['name'] for row in summary['entries']}
    for row in summary['entries']:
        data = source.read(row['name'])
        assert len(data) == row['bytes'] and sha(data) == row['sha256']
    manifest = json.loads(source.read('diagnostic/manifest.json'))
    assert len(manifest['files']) == 5
    assert sha(source.read('diagnostic/instrumentation.patch')) == manifest['instrumentationPatchSha256'] == summary['instrumentationPatchSha256']
    for name, hashes in manifest['files'].items():
        assert sha(source.read('diagnostic/original/' + name)) == hashes['originalSha256']
        assert sha(source.read('diagnostic/instrumented/' + name)) == hashes['instrumentedSha256']
    for name in ('runner', 'sampler', 'before-guard', 'after-guard'):
        assert json.loads(source.read('diagnostic/' + name + '-exit.json'))['exitCode'] == 0
    assert json.loads(source.read('diagnostic/restored-source.json'))['sourceRestoredByteEqual']
    assert json.loads(source.read('sampler/binding.json'))['sha256'] == '1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4'
    assert 'sampler/sample.ps1' not in source.namelist()
    samples = [json.loads(line) for line in source.read('samples/samples.jsonl').decode('utf-8-sig').splitlines() if line.strip()]
    assert [row['index'] for row in samples] == [1, 2, 3, 4, 5]
    assert all(row['stop_channel'] == 'rcon' and not row['forced_kill'] and not row['killed'] for row in samples)
    raw = source.read('samples/reload-session-stderr.log').decode('utf-8')
    pattern = re.compile(r'\[DEBUG-7d-(?:phase|exec|module)\] ([-\w]+) type=(\S+) nano=(\d+) epoch=(\d+)(?: (?:message|script|path)=([^\r\n]*))?')
    events = [dict(phase=m[1], scriptType=m[2], nano=int(m[3]), epochMs=int(m[4]), message=m[5] or '') for m in pattern.finditer(raw)]
    server = sorted([e for e in events if e['scriptType'] == 'server'], key=lambda e: e['nano'])
    assert server == [e for e in summary['allEvents'] if e['scriptType'] == 'server']
    starts = [e for e in server if e['phase'] == 'root-begin']
    ends = [e for e in server if e['phase'] == 'root-after-manager']
    assert len(starts) == len(ends) == 6
    assert ends[0]['epochMs'] < summary['readinessEpochMsFloor']
    assert summary['initialPreReadinessRoot']['start'] == starts[0]
    assert summary['initialPreReadinessRoot']['end'] == ends[0]
    local = timezone(timedelta(hours=8))
    for sample, start, end, row in zip(samples, starts[1:], ends[1:], summary['reloads']):
        clock = re.search(r'\[(\d\d:\d\d:\d\d)\]', sample['marker_line'])[1]
        assert datetime.fromtimestamp(start['epochMs']/1000, local).strftime('%H:%M:%S') <= clock <= datetime.fromtimestamp(end['epochMs']/1000, local).strftime('%H:%M:%S')
        current = [e for e in server if start['nano'] <= e['nano'] <= end['nano']]
        assert row['events'] == current and len(row['phases']) == 101
        assert row['rootThroughManagerMs'] == (end['nano'] - start['nano']) / 1e6
        assert row['rconRttMs'] == sample['rcon_rtt_ms'] and row['markerMs'] == sample['marker_ms']
        assert row['rttMinusObservedRootMs'] == row['rconRttMs'] - row['rootThroughManagerMs']
        assert row['markerMinusRttMs'] == row['markerMs'] - row['rconRttMs']
        def span(begin, finish, script=None):
            a = [e for e in current if e['phase'] == begin and (script is None or e['message'] == script)]
            b = [e for e in current if e['phase'] == finish and (script is None or e['message'] == script)]
            assert len(a) == len(b) == 1
            return (b[0]['nano'] - a[0]['nano']) / 1e6
        detail = row['executionBreakdown']
        for key, begin, finish in [('beforeHookMs', 'before-hook-begin', 'before-hook-end'), ('afterHookMs', 'after-hook-begin', 'after-hook-end'), ('preloadMs', 'preload-begin', 'preload-end')]:
            assert detail[key] == span(begin, finish)
        assert len(detail['scripts']) == 6
        for script in detail['scripts']:
            for key, begin, finish in [('entryMs', 'entry-begin', 'entry-end'), ('validationMs', 'validation-begin', 'validation-end'), ('moduleMs', 'module-begin', 'module-end')]:
                assert script[key] == span(begin, finish, script['script'])
        for key in ('entryMs', 'validationMs', 'moduleMs'):
            assert detail[key] == sum(script[key] for script in detail['scripts'])
    for key, value in summary['means'].items():
        assert value == sum(row[key] for row in summary['reloads']) / 5
    for key, value in summary['executionMeans'].items():
        assert value == sum(row['executionBreakdown'][key] for row in summary['reloads']) / 5
assert summary['diagnosticOnly'] and not summary['formalReplacement']
assert summary['originalFormalPerformance'] == 'FAIL_RETAINED' and summary['synchronousObserverOverhead'] == 'NOT_QUANTIFIED'
assert summary['sourceRestored']['sourceRestoredByteEqual'] and summary['overallTickets'] == 'NOT_COMPLETE'
assert summary['compiledBinding']['matchedClasses'] == 1581 and len(summary['compiledBinding']['changedInstrumentedFamilyClasses']) == 11
assert not summary['compiledBinding']['officialCertification']
print(json.dumps(dict(result='PASS', diagnosticOnly=True, formal='FAIL_RETAINED', executionMeans=summary['executionMeans'], sha256=summary['archive']['sha256'])))

for row in summary['reloads']:
    events = row['events']
    detail = row['moduleBreakdown']
    for key, begin, finish in [('resolution', 'resolve-begin', 'resolve-end'), ('dependencyRefresh', 'resolve-end', 'refresh-end'),
                               ('preparationCache', 'prepare-begin', 'prepare-cache-end'), ('preparationObserve', 'prepare-cache-end', 'prepare-end'),
                               ('guestExecutor', 'guest-execute-begin', 'guest-execute-end')]:
        starts = [e for e in events if e['phase'] == begin]
        ends = [e for e in events if e['phase'] == finish]
        assert len(starts) == len(ends) == len(detail[key]) == 6
        for start, end, span in zip(starts, ends, detail[key]):
            assert start['message'] == end['message'] == span['path']
            assert start['nano'] == span['beginNano'] and end['nano'] == span['endNano']
            assert span['ms'] == (end['nano']-start['nano'])/1e6
        assert all(a['endNano'] <= b['beginNano'] for a, b in zip(detail[key], detail[key][1:]))
        assert detail[key+'Ms'] == sum(span['ms'] for span in detail[key])
for key, value in summary['moduleMeans'].items():
    assert value == sum(row['moduleBreakdown'][key] for row in summary['reloads']) / 5
print(json.dumps(dict(result='PASS', moduleMeans=summary['moduleMeans'], diagnosticOnly=True, formal='FAIL_RETAINED')))
