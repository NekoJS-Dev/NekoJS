"""Verify the sealed evidence without requiring installed game servers."""
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'dependency-manifest.json').read_text(encoding='utf-8'))
archive = root / manifest['archive']
assert hashlib.sha256(archive.read_bytes()).hexdigest() == manifest['sha256']
with zipfile.ZipFile(archive) as bundle:
    assert set(bundle.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert hashlib.sha256(bundle.read(name)).hexdigest() == expected, name
    summary = json.loads(bundle.read('dependency-summary.json'))
    assert summary == json.loads((root / 'dependency-summary.json').read_text(encoding='utf-8'))
    assert summary['testTotals']['failures'] == summary['testTotals']['errors'] == 0
    assert summary['releaseApproved'] is False
    assert 'BUILD SUCCESSFUL' in bundle.read('checks/tsc-dependency-root-matrix-2.log').decode('utf-8-sig')
    for name in bundle.namelist():
        if name.startswith('tests/') and name.endswith('.xml'):
            suite = ET.fromstring(bundle.read(name))
            assert int(suite.attrib.get('failures', 0)) == int(suite.attrib.get('errors', 0)) == 0, name
    for source, expected in summary['candidate']['sourceHashes'].items():
        assert hashlib.sha256(bundle.read('source/' + source)).hexdigest() == expected, source
    for row in summary['ide']['nodes']:
        node = row['node']
        runtime = json.loads(bundle.read(f'installed/{node}/runtime-verdict.json'))
        assert runtime['verdict'] == 'PASS'
        assert json.loads(bundle.read(f'installed/{node}/server-exit.json'))['exitCode'] == 0
        assert row['pyright']['exitCode'] == 0
        assert row['pyright']['summary']['errorCount'] == row['pyright']['summary']['warningCount'] == 0
        report = json.loads(bundle.read(f'installed/{node}/.neko_probe/typescript/@special/dependencies.json'))
        assert report['complete'] == (not report['unavailable'])
        for side in row['typescript']:
            assert side['diagnosticsComplete'] and not side['compilerCrashed']
            raw = bundle.read(f"ide/{node}-{side['side']}-tsc.txt")
            assert len(re.findall(rb'error TS\d+:', raw)) == side['diagnosticCount']
            assert not bundle.read(f"ide/{node}-{side['side']}-tsc-stderr.txt")
    for phase in ['before', 'after']:
        rows = json.loads(bundle.read(f'variance-assignments/{phase}/summary.json'))
        assert len(rows) == 5
        assert all(not row['compilerCrashed'] and not row['focusDiagnostics'] for row in rows)
        assert all(row['diagnosticOnlyIncreasedStack'] == (phase == 'before') for row in rows)
    passed = all(side['exitCode'] == 0 for row in summary['ide']['nodes'] for side in row['typescript'])
    assert summary['fullTscPassed'] == passed
print(json.dumps(dict(integrity='PASS', tests=summary['testTotals'], fullTscPassed=passed,
    releaseApproved=False), indent=2))
