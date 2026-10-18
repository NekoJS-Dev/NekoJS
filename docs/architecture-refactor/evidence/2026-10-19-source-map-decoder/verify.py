"""Recompute component allocation, original performance gates and test totals."""
import hashlib
import io
import json
import statistics
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
packed = root / 'evidence.zip'
assert packed.stat().st_size == manifest['archiveBytes']
assert hashlib.sha256(packed.read_bytes()).hexdigest() == manifest['archiveSha256']
assert not manifest['samplerChanged'] and not manifest['externalQosController']

def decode(data):
    if data.startswith((b'\xff\xfe', b'\xfe\xff')):
        return data.decode('utf-16')
    try:
        return data.decode('utf-8-sig')
    except UnicodeDecodeError:
        return data.decode('gb18030')

with zipfile.ZipFile(packed) as archive:
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert hashlib.sha256(archive.read(name)).hexdigest() == expected, name
        assert not name.endswith(('server.properties', '.pk8', 'classpath.txt'))
    load = lambda name: json.loads(decode(archive.read(name)))
    prep = load('formal/metadata/preparation.json')
    post = load('post-run-bindings.json')
    assert prep['revision'] == manifest['candidateCommit']
    assert post['trackedSourcesClean'] and post['classBindings'] == prep['classBindings']
    artifact = archive.read('binaries/neoforge-26.1.2.jar')
    assert hashlib.sha256(artifact).hexdigest() == prep['artifactSha256']
    with zipfile.ZipFile(io.BytesIO(artifact)) as jar:
        for name, expected in prep['classBindings'].items():
            assert hashlib.sha256(jar.read(name)).hexdigest() == expected, name
    redactions = load('redactions.json')
    hashes = dict(prep['fixtureHashes'])
    hashes['bench/perf/sample.ps1'] = prep['samplerSha256']
    assert prep['samplerSha256'] == '1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4'
    for name, expected in hashes.items():
        key = 'harness/' + name
        actual = (redactions[key]['originalSha256'] if key in redactions
                  else hashlib.sha256(archive.read(key)).hexdigest())
        assert actual == expected, name

    components = []
    for label in ['baseline-v2', 'candidate']:
        observed = load('component/' + label + '/observations.json')
        summary = load('component/' + label + '/summary.json')
        assert observed['sources'] == 6 and observed['warmups'] == 100
        assert observed['equivalentCases'] == summary['equivalentCases'] == 10019
        pairs = observed['pairs']
        assert [p['pair'] for p in pairs] == list(range(1, 31))
        for i, pair in enumerate(pairs):
            assert list(pair) == (['pair', 'before', 'after'] if i % 2 == 0 else ['pair', 'after', 'before'])
        means = {arm: {key: statistics.mean(p[arm][key] for p in pairs)
                       for key in ['wallNs', 'cpuNs', 'allocatedBytes']} for arm in ['before', 'after']}
        ratio = means['after']['allocatedBytes'] / means['before']['allocatedBytes']
        verdict = 'PASS' if ratio <= 0.8 else 'FAIL'
        assert means == summary['means'] and ratio == summary['allocationRatio']
        assert verdict == summary['allocationGate'] == ('FAIL' if label == 'baseline-v2' else 'PASS')
        if label == 'candidate':
            assert summary['currentDecoderClassSha256'] == prep['classBindings']['com/tkisor/nekojs/core/error/SourceMapRegistry.class']
        components.append(dict(label=label, allocationRatio=ratio, verdict=verdict))

    binding = load('component/validation/binding.json')
    assert binding['buildPassed']
    for name, expected in binding['sourceHashes'].items():
        assert hashlib.sha256(archive.read('source/' + name)).hexdigest() == expected
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for node, expected in binding['totals'].items():
        count = dict.fromkeys(totals, 0)
        for name in archive.namelist():
            if name.startswith('component/validation/' + node + '/') and name.endswith('.xml'):
                result = ET.fromstring(archive.read(name))
                for key in count: count[key] += int(result.attrib.get(key, 0))
        assert count == expected, node
        for key in totals: totals[key] += count[key]
    assert totals == dict(tests=4989, failures=0, errors=0, skipped=271)
    gates = load('component/ci-gates.json')['gates']
    assert len(gates) == 4 and all(g['state'] == 'pass' and not g['failures'] for g in gates)

    groups = load('formal/metadata/groups.json')
    judgment = load('formal/metadata/judgment.json')
    assert [g['group'] for g in groups] == ['reload-1', 'reload-2', 'startup']
    results, sessions = [], 0
    for group, recorded in zip(groups, judgment['groups'], strict=True):
        name = group['group']
        assert group['exitCode'] == 0 and len(group['outputDirectories']) == 1
        rows = [json.loads(line) for line in decode(archive.read('formal/' + name + '/samples.jsonl')).splitlines() if line.strip()]
        startup = name == 'startup'
        assert len(rows) == (7 if startup else 5)
        assert [r['index'] for r in rows] == list(range(1, len(rows) + 1))
        assert [r['kind'] for r in rows] == (['warmup'] * 2 + ['formal'] * 5 if startup else ['formal'] * 5)
        for row in rows:
            assert row['rev'] == prep['revision'][:8] and row['node'] == '26.1.2'
            assert row['killed'] is False and row['forced_kill'] is False and not row.get('timeout')
            assert row['stop_channel'] == 'rcon' and row['done_s'] is not None and row['wall_done_ms'] is not None
            if not startup:
                assert 'phase=COMMIT - no errors.' in row['rcon_response']
                assert 'PERF02-SERVER tick-bench load' in row['marker_line']
        for row in rows if startup else rows[:1]:
            stdout = decode(archive.read('formal/' + name + '/' + row['tag'] + '-stdout.log'))
            assert 'Done (' in stdout and 'BUILD SUCCESSFUL' in stdout
            sessions += 1
        field, limit = ('wall_done_ms', 41173) if startup else ('marker_ms', 285.3)
        values = [r[field] for r in rows if r['kind'] == 'formal']
        mean = statistics.mean(values)
        verdict = 'PASS' if mean <= limit else 'FAIL'
        assert recorded['valid'] and recorded['invalidIndices'] == []
        assert (values, mean, limit, verdict) == (recorded['values'], recorded['mean'], recorded['limit'], recorded['verdict'])
        results.append(dict(group=name, values=values, mean=mean, limit=limit, verdict=verdict))
    assert sessions == 9 and judgment['collectionComplete'] and judgment['valid']
    assert judgment['verdict'] == ('PASS' if all(r['verdict'] == 'PASS' for r in results) else 'NOT_PASS')
print(json.dumps(dict(archive='PASS', tests=totals, components=components, formal=results), indent=2))
