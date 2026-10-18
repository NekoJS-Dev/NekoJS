"""Verify the current-environment historical-source control, not a release pass."""
import hashlib
import io
import json
import statistics
import zipfile
from pathlib import Path


def verify():
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
        preparation = json.loads(archive.read('control/preparation.json'))
        assert preparation['revision'] == '3400e97ee27e29b487ec100836cf04777262c84d'
        assert preparation['historicalBinaryUnavailable'] and preparation['historicalWorldUnavailable']
        assert json.loads(archive.read('control/build-result.json'))['exitCode'] == 0
        summary = json.loads(archive.read('control/summary.json'))
        assert summary['diagnosticOnly'] and summary['currentSourceFormalVerdictUnchanged'] == 'FAIL'
        redactions = json.loads(archive.read('redactions.json'))
        for name, expected in preparation['addedUntrackedHarness'].items():
            key = 'fixed-harness/'+name.removeprefix('bench/perf/')
            original = redactions.get(key, {}).get('originalSha256', hashlib.sha256(archive.read(key)).hexdigest())
            assert original == expected
        jars = {module: zipfile.ZipFile(io.BytesIO(archive.read('binaries/'+module+'.jar')))
                for module in ['common', '26.1.2']}
        classes = json.loads(archive.read('control/class-bindings.json'))
        assert classes
        for name, expected in classes.items():
            module = 'common' if name.startswith('common/') else '26.1.2'
            entry = name.split('/build/classes/java/main/', 1)[1]
            assert hashlib.sha256(jars[module].read(entry)).hexdigest() == expected, name
        for jar in jars.values(): jar.close()
        groups = json.loads(archive.read('control/groups.json'))
        assert [group['group'] for group in groups] == ['startup', 'reload-1', 'reload-2']
        for group in groups:
            label = group['group']
            assert group['exitCode'] == 0
            rows = [json.loads(line) for line in archive.read(label+'/samples.jsonl').decode('utf-8-sig').splitlines() if line.strip()]
            assert all(row['rev'] == '3400e97e' and not row['forced_kill'] and not row.get('killed')
                       and not row.get('timeout') and row['stop_channel'] == 'rcon' for row in rows)
            formal = [row for row in rows if row['kind'] == 'formal']
            assert len(formal) == 5
            expected = summary['groups'][label]
            if label == 'startup':
                assert len(rows) == 7 and [row['kind'] for row in rows] == ['warmup']*2+['formal']*5
                assert all(row['wall_done_ms'] is not None for row in rows)
                values = [row['wall_done_ms'] for row in formal]
            else:
                assert len(rows) == 5 and all('- no errors.' in row['rcon_response'] for row in rows)
                assert all('PERF02-SERVER tick-bench load' in row['marker_line'] for row in rows)
                values = [row['marker_ms'] for row in formal]
                assert expected['rconValuesMs'] == [row['rcon_rtt_ms'] for row in formal]
                assert expected['rconMeanMs'] == statistics.mean(row['rcon_rtt_ms'] for row in formal)
            assert values == expected['valuesMs'] and statistics.mean(values) == expected['meanMs']
            assert (statistics.mean(values) <= expected['historicalThresholdMs']) == expected['withinHistoricalThreshold']
    print(json.dumps(dict(integrity='PASS', diagnosticOnly=True, oldReferenceGroups=summary['groups'],
                         currentSourceFormalVerdictUnchanged='FAIL', originalHistoricalWorldNotReproduced=True)))


if __name__ == '__main__':
    verify()
