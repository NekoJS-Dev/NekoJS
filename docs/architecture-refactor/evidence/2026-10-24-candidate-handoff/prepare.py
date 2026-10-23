"""Prepare the six exact local artifacts with explicit failed release gates; never upload them."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

repo = Path.cwd()
root = repo / 'build/release-candidate-975496d3'
assert not root.exists()
installed = json.loads((repo / 'build/closeout-method-bounds-installed/preparation-manifest.json').read_text())
assert installed['sourceRevision'] == '975496d3bddf6fc76b206921a1d09207120ca24d'
proof_root = repo / 'docs/architecture-refactor/evidence'
proofs = ['2026-10-24-method-bounds', '2026-10-24-method-bounds-performance',
          '2026-10-24-candidate-rollback', '2026-10-24-candidate-tcp', '2026-10-24-candidate-render']
references = {name: hashlib.sha256((proof_root / name / 'evidence.zip').read_bytes()).hexdigest() for name in proofs}
artifacts = []
root.mkdir()
for row in installed['nodes']:
    sources = [('runtime', repo / 'build/closeout-method-bounds-installed' / row['node'] / 'mods' / row['artifact'], row['sha256'])]
    if row['node'] == '1.21.1':
        manifest = json.loads((repo / 'build/priority-closeout/posteffects-121-installed-975496d3-v1/artifact-manifest.json').read_text())
        client = manifest['artifacts']['client']
        sources.append(('client', repo / 'build/priority-closeout/posteffects-121-installed-975496d3-v1/client/mods' / client['name'], client['sha256']))
    for role, source, expected in sources:
        assert hashlib.sha256(source.read_bytes()).hexdigest() == expected
        with zipfile.ZipFile(source) as archive:
            for name, digest in row['classBindings'].items():
                assert hashlib.sha256(archive.read(name)).hexdigest() == digest
        target = root / row['node'] / source.name
        target.parent.mkdir(exist_ok=True)
        shutil.copyfile(source, target)
        assert hashlib.sha256(target.read_bytes()).hexdigest() == expected
        artifacts.append(dict(node=row['node'], role=role, path=target.relative_to(root).as_posix(),
            sha256=expected, bytes=target.stat().st_size, productionClasses=len(row['classBindings'])))
manifest = dict(productionRevision=installed['sourceRevision'],
    checkoutRevisionAtPreparation=subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
    artifactVersion='1.1.0-preview3', releaseReady=False, remoteUploadPerformed=False,
    sourceVersionRepackaged=False, artifacts=artifacts, evidenceArchives=references,
    gates=dict(fullMatrix='PASS: 5149 declared tests / 271 skipped / zero failures and errors',
        installedServers='PASS: five nodes, five normal stops', defaultPython='PASS: 1609 stubs',
        nativeRender='PASS: three nodes / thirty original frames / six normal stops',
        realTcp='PASS: two nodes / thirty stages / fourteen normal stops',
        separatedRollback='PASS: known d91ca974 and current candidate, four stages and normal stops, 26.1.2 whole-world fixture only',
        strictTypeScript='FAIL: 5565 diagnostics', reloadPerformance='FAIL: 313.8 / 308.6 ms against 285.3 ms',
        startupPerformance='PASS: 14132.6 ms against 41173 ms', maintainerAcceptance='NOT RECORDED'),
    limits=['Exact local engineering candidate, not release authorization.',
        'Artifact replacement does not restore data; world-external effects are not rolled back.',
        'Current display version retained; no final 1.2.0 version cutover or repackaging.'])
(root / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
(root / 'SHA256SUMS').write_text(''.join(row['sha256'] + '  ' + row['path'] + '\n' for row in artifacts))
print(json.dumps(dict(localArtifacts=len(artifacts), nodes=len(installed['nodes']), releaseReady=False,
    directory=str(root), artifactBytes=sum(row['bytes'] for row in artifacts))))
