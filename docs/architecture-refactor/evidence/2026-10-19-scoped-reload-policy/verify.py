"""Verify the rejected prototype and recompute its original-gate FAIL results."""
import hashlib
import io
import json
import re
import statistics
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
packed = root / 'evidence.zip'
assert packed.stat().st_size == manifest['archiveBytes']
assert hashlib.sha256(packed.read_bytes()).hexdigest() == manifest['archiveSha256']
assert manifest['prototypeRejected'] and not manifest['promotedToStonecutter']
assert not manifest['samplerChanged'] and not manifest['externalQosController']

def decode(raw):
    if raw.startswith((b'\xff\xfe', b'\xfe\xff')):
        return raw.decode('utf-16')
    try:
        return raw.decode('utf-8-sig')
    except UnicodeDecodeError:
        return raw.decode('gb18030')

with zipfile.ZipFile(packed) as archive:
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert hashlib.sha256(archive.read(name)).hexdigest() == expected, name
        assert not name.endswith(('server.properties', '.pk8', 'adapter-classpath.txt'))
    load = lambda name: json.loads(decode(archive.read(name)))
    prep = load('formal/metadata/preparation.json')
    post = load('post-run-bindings.json')
    assert post['trackedSourcesClean'] and not post['promotedToStonecutter']
    assert post['classBindings'] == prep['classBindings'] and len(prep['classBindings']) == 1630
    assert prep['revision'] == manifest['prototypeCommit'] == '8ca66bf43ce7abb0665e038dc5d07a732aa0159a'
    artifact = archive.read('binaries/prototype-neoforge.jar')
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
        if key in redactions:
            assert redactions[key]['originalSha256'] == expected
        else:
            assert hashlib.sha256(archive.read(key)).hexdigest() == expected
    groups = load('formal/metadata/groups.json')
    judgment = load('formal/metadata/judgment.json')
    assert [g['group'] for g in groups] == ['reload-1', 'reload-2', 'startup']
    results = []
    sessions = 0
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
            text = decode(archive.read('formal/' + name + '/' + row['tag'] + '-stdout.log'))
            assert 'Done (' in text and 'BUILD SUCCESSFUL' in text
            assert not re.search(r'NEKO-10(?:19|20)', text)
            sessions += 1
        field, limit = ('wall_done_ms', 41173) if startup else ('marker_ms', 285.3)
        values = [r[field] for r in rows if r['kind'] == 'formal']
        mean = statistics.mean(values)
        verdict = 'PASS' if mean <= limit else 'FAIL'
        assert recorded['valid'] and recorded['invalidIndices'] == []
        assert (values, mean, limit, verdict) == (recorded['values'], recorded['mean'], recorded['limit'], recorded['verdict'])
        results.append(dict(group=name, values=values, mean=mean, limit=limit, verdict=verdict))
    assert sessions == 9 and all(r['verdict'] == 'FAIL' for r in results)
    assert judgment['valid'] and judgment['verdict'] == 'NOT_PASS'
    full = load('validation/full-build/binding.json')
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for node, expected in full['nodeTotals'].items():
        count = dict.fromkeys(totals, 0)
        prefix = 'validation/full-build/results/' + node + '/'
        for name in archive.namelist():
            if name.startswith(prefix) and name.endswith('.xml'):
                result = ET.fromstring(archive.read(name))
                for key in count: count[key] += int(result.attrib.get(key, 0))
        assert count == expected and count['failures'] == count['errors'] == 0
        for key in totals: totals[key] += count[key]
        for suite, tests in [('ReloadExecutionTest', 9), ('RuntimeCommandLifecycleSourceTraceTest', 5)]:
            package = 'platform' if suite == 'ReloadExecutionTest' else 'command'
            path = 'validation/final-tests/' + node + '/TEST-com.tkisor.nekojs.' + package + '.' + suite + '.xml'
            result = ET.fromstring(archive.read(path))
            assert int(result.attrib['tests']) == tests
            assert int(result.attrib['failures']) == int(result.attrib['errors']) == int(result.attrib['skipped']) == 0
    assert totals == dict(tests=2646, failures=0, errors=0, skipped=267)
    old = decode(archive.read('validation/full-build/source/src/main/java/com/tkisor/nekojs/platform/ReloadExecution.java'))
    final = decode(archive.read('source/src/main/java/com/tkisor/nekojs/platform/ReloadExecution.java'))
    assert old.replace('reload continues without the hint', 'reload continues').replace('\r\n', '\n') == final.replace('\r\n', '\n')
    for mode in ['subsets', 'processor', 'declaration-final', 'nodes-final']:
        report = load('validation/gates/scoped-policy-ci-' + mode + '.json')
        assert all(g['state'] == 'pass' and not g['failures'] for g in report['gates'])
    isolation = load('validation/thread-isolation/observation.json')
    assert isolation['passed'] and isolation['ownerBefore'] == isolation['ownerAfter'] == [1, 0, 0]
    assert isolation['ownerDuring'] == [1, 1, 0]
    assert isolation['existingBefore'] == isolation['existingDuring'] == isolation['newBefore'] == isolation['newDuring'] == [1, 0, 0]
    binding = load('validation/thread-isolation/binding.json')
    assert binding['artifactSha256'] == prep['artifactSha256'] and binding['executedAfterFormalCohort']
    assert all(s['exitCode'] == 0 for s in binding['steps'])
    print(json.dumps(dict(evidenceVerification='PASS', performanceVerdict='NOT_PASS',
                          promotedToStonecutter=False, sessions=sessions, fullBuildTests=totals,
                          finalFocusedTests=70, threadIsolation=True, groups=results), indent=2))
