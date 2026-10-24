"""Verify the later acceptance receipt and every available local artifact without changing them."""
import hashlib
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent
expected = json.loads((root / 'verification.json').read_text(encoding='utf-8'))
payload = (root / 'evidence.zip').read_bytes()
assert len(payload) == expected['bytes'] and hashlib.sha256(payload).hexdigest() == expected['sha256']
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    names = archive.namelist()
    entries = json.loads(archive.read('entries.json'))
    assert len(names) == len(set(names)) == expected['entries']
    assert set(names) == set(entries) | {'entries.json'}
    for name, digest in entries.items():
        assert hashlib.sha256(archive.read(name)).hexdigest() == digest
    snapshot = json.loads(archive.read('manifest-acceptance.json'))
    initial = json.loads(archive.read('manifest.json'))
    bindings = json.loads(archive.read('class-bindings.json'))
    audit = json.loads(archive.read('privacy-audit.json'))
    assert hashlib.sha256(archive.read('manifest.json')).hexdigest() == snapshot['initialManifestSha256']
    assert hashlib.sha256(archive.read('audit-local-credentials.py').replace(b'\r\n', b'\n')).hexdigest() == audit['toolSha256Lf']
assert json.loads((root / 'privacy-audit.json').read_text(encoding='utf-8')) == audit
assert hashlib.sha256((root / 'audit-local-credentials.py').read_bytes().replace(b'\r\n', b'\n')).hexdigest() == audit['toolSha256Lf']
assert snapshot['productionRevision'] == initial['productionRevision'] == expected['revision']
assert snapshot['releaseReady'] is snapshot['remoteUploadPerformed'] is snapshot['sourceVersionRepackaged'] is False
assert snapshot['artifacts'] == initial['artifacts'] and len(snapshot['artifacts']) == expected['artifacts'] == 6
assert snapshot['artifactVersion'] == initial['artifactVersion'] == '1.1.0-preview3'
for name, digest in snapshot['evidenceArchives'].items():
    assert hashlib.sha256((root.parent / name / 'evidence.zip').read_bytes()).hexdigest() == digest
gates = snapshot['gates']
with zipfile.ZipFile(root.parent / '2026-10-24-owner-stamp/evidence.zip') as archive:
    matrix = json.loads(archive.read('matrix/summary.json'))
    assert gates['fullMatrix'] == dict(verdict='PASS', **matrix['total'])
    ide = json.loads(archive.read('installed/ide/summary.json'))
    assert gates['defaultPython']['stubs'] == sum(node['pyright']['stubCount'] for node in ide['nodes']) == 1609
    assert all(node['pyright']['exitCode'] == 0 and node['pyright']['summary']['errorCount'] == node['pyright']['summary']['warningCount'] == 0 for node in ide['nodes'])
    sides = [side for node in ide['nodes'] for side in node['typescript']]
    assert len(sides) == 10 and sum(side['diagnosticCount'] for side in sides) == gates['fullStrictTypeScript']['diagnostics'] == 5537
    assert gates['fullStrictTypeScript']['verdict'] == 'FAIL' and gates['fullStrictTypeScript']['skipLibCheck'] is False
    assert all(side['exitCode'] == 2 and side['diagnosticsComplete'] and not side['compilerCrashed']
        and side['defaultEditorIncludes'] and not side['skipLibCheck'] for side in sides)
    prep = json.loads(archive.read('installed/preparation-manifest.json'))
    assert prep['sourceRevision'] == snapshot['productionRevision']
    for node in prep['nodes']:
        assert json.loads(archive.read('installed/' + node['node'] + '/runtime-verdict.json'))['verdict'] == 'PASS'
        assert json.loads(archive.read('installed/' + node['node'] + '/server-exit.json'))['exitCode'] == 0
    assert gates['installedServers'] == dict(verdict='PASS', nodes=len(prep['nodes']), normalStops=len(prep['nodes']))
with zipfile.ZipFile(root.parent / '2026-10-24-owner-stamp-performance/evidence.zip') as archive:
    judgment = json.loads(archive.read('run/judgment.json'))
    reloads = [row for row in judgment['groups'] if row['group'].startswith('reload-')]
    startup = next(row for row in judgment['groups'] if row['group'] == 'startup')
    assert judgment['collectionComplete'] and judgment['valid'] and judgment['verdict'] == 'NOT_PASS'
    assert all(row['limit'] == 285.3 and row['verdict'] == 'FAIL' for row in reloads)
    assert gates['reloadPerformance'] == dict(verdict='FAIL', meansMs=[row['mean'] for row in reloads], limitMs=285.3)
    assert startup['limit'] == 41173 and startup['verdict'] == 'PASS'
    assert gates['startupPerformance'] == dict(verdict='PASS', meanMs=startup['mean'], limitMs=41173)
with zipfile.ZipFile(root.parent / '2026-10-24-owner-stamp-tcp/evidence.zip') as archive:
    runs = [json.loads(archive.read(kind + '-' + short + '/run-result.json')) for kind in ['basic', 'commit'] for short in ['261', '262']]
    assert all(row['verdict'] == 'PASS' and not row['errors'] and not row['forced'] and all(value == 0 for value in row['exits'].values()) for row in runs)
    assert gates['tcp'] == dict(verdict='PASS', nodes=2, stages=sum(len(row['stages']) for row in runs), normalStops=sum(len(row['exits']) for row in runs))
with zipfile.ZipFile(root.parent / '2026-10-24-owner-stamp-render/evidence.zip') as archive:
    runs = [json.loads(archive.read(short + '/run-result.json')) for short in ['121', '261', '262']]
    reviews = [json.loads(archive.read(short + '/pixel-review.json')) for short in ['121', '261', '262']]
    assert all(row['mechanicalVerdict'] == 'PASS' and not row['errors'] and not row['forced'] and row['clientExit'] == row['serverExit'] == 0 for row in runs)
    assert all(row['verdict'] == 'PASS' and row['screenshotsReviewed'] for row in reviews)
    assert gates['nativeRender'] == dict(verdict='PASS', nodes=3, originalFrames=sum(len(row['stages']) for row in runs), syntheticNativeF3T=13, normalStops=6)
with zipfile.ZipFile(root.parent / '2026-10-24-owner-stamp-rollback/evidence.zip') as archive:
    final = json.loads(archive.read('final-verdict.json'))
    runs = json.loads(archive.read('summary.json'))['records']
    assert final['verdict'] == 'PASS' and final['separateArtifactAndDataRollback']
    assert all(row['verdict'] == 'PASS' and row['exitCode'] == 0 for row in runs)
    assert gates['separatedRollback'] == dict(verdict='PASS', phases=len(runs), normalStops=len(runs), scope=final['scope'])
assert gates['maintainerAcceptance']['verdict'] == 'NOT RECORDED' and gates['finalVersionCutover']['verdict'] == 'NOT DONE'
assert set(audit['proofs']) == {'2026-10-24-owner-stamp-tcp', '2026-10-24-owner-stamp-render', '2026-10-24-owner-stamp-rollback'}
for name, row in audit['proofs'].items():
    assert row['archiveSha256'] == snapshot['evidenceArchives'][name]
    assert row['retainedMatches'] == row['redactedFiles'] == 0
    assert row['localCredentialSources'] > 0 and row['distinctLocalSecrets'] > 0 and row['bytePatterns'] > 0
    assert row['payloadsScanned'] >= row['topLevelEntries'] > 0
    manifest = json.loads((root.parent / name / 'manifest.json').read_text(encoding='utf-8'))
    assert manifest['redactedOriginalHashes'] == {}
candidate = root.parents[3] / 'build/release-candidate-6e43b827'
checked = 0
if candidate.is_dir():
    assert hashlib.sha256((candidate / 'manifest.json').read_bytes()).hexdigest() == snapshot['initialManifestSha256']
    assert json.loads((candidate / 'manifest-acceptance.json').read_text(encoding='utf-8')) == snapshot
    for row in snapshot['artifacts']:
        path = (candidate / row['path']).resolve()
        assert path.is_relative_to(candidate.resolve()) and path.stat().st_size == row['bytes']
        assert hashlib.sha256(path.read_bytes()).hexdigest() == row['sha256']
        with zipfile.ZipFile(path) as jar:
            names = {name for name in jar.namelist() if name.startswith('com/tkisor/nekojs/') and name.endswith('.class')}
            assert names == set(bindings[row['node']]) and len(names) == row['productionClasses']
            assert all(hashlib.sha256(jar.read(name)).hexdigest() == digest for name, digest in bindings[row['node']].items())
        checked += 1
print(json.dumps(dict(acceptanceReceipt='PASS', localArtifactsChecked=checked,
    localPayloads='PASS' if checked == 6 else 'NOT PRESENT', releaseReady=False,
    fullStrictTypeScript='FAIL', reloadPerformance='FAIL', maintainerAcceptance='NOT RECORDED')))
