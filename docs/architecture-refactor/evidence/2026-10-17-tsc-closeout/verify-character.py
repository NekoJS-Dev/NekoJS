"""Verify the character projection evidence and preserve the full IDE failure result."""
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'character-manifest.json').read_text(encoding='utf-8'))
archive = root / manifest['archive']
assert hashlib.sha256(archive.read_bytes()).hexdigest() == manifest['sha256']
with zipfile.ZipFile(archive) as bundle:
    assert set(bundle.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert hashlib.sha256(bundle.read(name)).hexdigest() == expected, name
    summary = json.loads(bundle.read('character-summary.json'))
    assert summary == json.loads((root / 'character-summary.json').read_text(encoding='utf-8'))
    assert summary['testTotals']['failures'] == summary['testTotals']['errors'] == 0
    assert 'BUILD SUCCESSFUL' in bundle.read('checks/tsc-character-matrix.log').decode('utf-8-sig')
    for name in bundle.namelist():
        if name.startswith('tests/') and name.endswith('.xml'):
            suite = ET.fromstring(bundle.read(name))
            assert int(suite.attrib.get('failures', 0)) == int(suite.attrib.get('errors', 0)) == 0, name
    for source, expected in summary['candidate']['sourceHashes'].items():
        assert hashlib.sha256(bundle.read('source/' + source)).hexdigest() == expected
    for row in summary['ideAfter']['nodes']:
        node = row['node']
        assert json.loads(bundle.read(f'installed/{node}/runtime-verdict.json'))['verdict'] == 'PASS'
        assert json.loads(bundle.read(f'installed/{node}/server-exit.json'))['exitCode'] == 0
        assert bundle.read(f'installed/{node}/server-stdout.log').count(b'OWNED-CHARACTER-VALUES-PASS') >= 2
        expected = b'OWNED-CHARACTER-PREDICATE-' + (b'ERASURE-REJECTED' if node == '1.21.1' else b'ACCEPTED')
        assert bundle.read(f'installed/{node}/server-stdout.log').count(expected) >= 2
        assert row['pyright']['exitCode'] == 0
        assert row['pyright']['summary']['errorCount'] == row['pyright']['summary']['warningCount'] == 0
        for side in row['typescript']:
            assert side['diagnosticsComplete'] and not side['compilerCrashed']
            raw = bundle.read(f"ide-after/{node}-{side['side']}-tsc.txt")
            assert len(re.findall(rb'error TS\d+:', raw)) == side['diagnosticCount']
            assert not bundle.read(f"ide-after/{node}-{side['side']}-tsc-stderr.txt")
    for row in summary['pythonDiffs']:
        assert not row['added'] and not row['removed']
        for name, expected in row['before'].items():
            assert hashlib.sha256(bundle.read(f"python-before/{row['node']}/{name}")).hexdigest() == expected
        for name, expected in row['after'].items():
            assert hashlib.sha256(bundle.read(f"installed/{row['node']}/.neko_probe/python/{name}")).hexdigest() == expected
    passed = all(side['exitCode'] == 0 for row in summary['ideAfter']['nodes'] for side in row['typescript'])
    assert json.loads(bundle.read('baseline-control/verdict.json'))['verdict'] == 'EXPECTED_LEGACY_FAILURE_REPRODUCED'
    assert json.loads(bundle.read('baseline-control/control-manifest.json'))['archivedSourceMatchesCommit']
    assert json.loads(bundle.read('baseline-control/server-exit.json'))['exitCode'] == 0
    assert json.loads(bundle.read('initial-runtime-failure/1.21.1/runtime-verdict.json'))['verdict'] == 'NOT GREEN'
    assert summary['fullTscPassed'] == passed
    assert summary['releaseApproved'] is False
print(json.dumps(dict(integrity='PASS', tests=summary['testTotals'],
    diagnosticComparison=summary['diagnosticComparison'], fullTscPassed=passed, releaseApproved=False), indent=2))
