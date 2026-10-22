"""Verify frozen installed behavior and complete failed IDE diagnostics."""
import hashlib
import json
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, entry in manifest['entries'].items():
        data = archive.read(name)
        assert len(data) == entry['size'] and hashlib.sha256(data).hexdigest() == entry['sha256'], name
    summary = json.loads(archive.read('installed/ide/summary.json'))
    prep = json.loads(archive.read('installed/preparation-manifest.json'))
    assert summary['sourceRevision'] == prep['sourceRevision'] == manifest['sourceRevision']
    assert summary['sourceRevision'].startswith('1f877b0f')
    count = stubs = 0
    expected = [[514, 515], [577, 578], [568, 569], [645, 646], [622, 623]]
    for node, wanted in zip(summary['nodes'], expected):
        assert node['pyright']['exitCode'] == 0
        assert node['pyright']['summary']['errorCount'] == node['pyright']['summary']['warningCount'] == 0
        stubs += node['pyright']['stubCount']
        assert [row['diagnosticCount'] for row in node['typescript']] == wanted
        for row in node['typescript']:
            assert row['exitCode'] == 2 and row['skipLibCheck'] is False and row['diagnosticsComplete'] and not row['compilerCrashed']
            count += row['diagnosticCount']
        prefix = 'installed/' + node['node'] + '/'
        assert json.loads(archive.read(prefix + 'runtime-verdict.json'))['verdict'] == 'PASS'
        assert json.loads(archive.read(prefix + 'server-exit.json'))['exitCode'] == 0
    assert count == 5857 and stubs == 1609
print('Verified: five installed runtime passes/normal exits; 1609 stubs pass; full strict TSC FAILS with 5857 diagnostics.')
