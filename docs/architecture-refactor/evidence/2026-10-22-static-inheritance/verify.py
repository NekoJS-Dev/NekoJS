"""Verify sealed evidence without extracting files or launching games."""
import hashlib
import json
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf8'))
archive = root / 'evidence.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(archive) as z:
    assert len(z.namelist()) == len(set(z.namelist()))
    assert set(z.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert not name.startswith(('/', '\\')) and '..' not in Path(name).parts
        data = z.read(name)
        assert len(data) == expected['size'], name
        assert hashlib.sha256(data).hexdigest() == expected['sha256'], name
    def document(name):
        return json.loads(z.read(name))
    candidate = document('checks/d91ca974-matrix/summary.json')
    assert candidate['total'] == dict(tests=5066, failures=0, errors=0, skipped=271)
    assert manifest['sourceFollowupCommonTests']['failures'] == manifest['sourceFollowupCommonTests']['errors'] == 0
    trial = 'installed/closeout-d91ca974-installed/'
    runtime = document(trial + 'runtime-summary.json')
    assert len(runtime) == 5 and all(row['verdict'] == 'PASS' and not row['errors'] for row in runtime)
    assert all(document(trial + row['node'] + '/server-exit.json')['exitCode'] == 0 for row in runtime)
    ide = document(trial + 'ide/summary.json')
    assert sum(row['pyright']['stubCount'] for row in ide['nodes']) == 1609
    assert all(row['pyright']['exitCode'] == 0 and row['pyright']['summary']['errorCount'] == 0
               and row['pyright']['summary']['warningCount'] == 0 for row in ide['nodes'])
    assert all(ts['exitCode'] == 2 and not ts['skipLibCheck'] and not ts['compilerCrashed']
               for row in ide['nodes'] for ts in row['typescript'])
    assert sum(ts['diagnosticCount'] for row in ide['nodes'] for ts in row['typescript']) == 5867
    comparison = document(trial + 'ide/diagnostic-comparison.json')
    assert sum(value['count'] for row in comparison['nodes'] for side in row['sides'] for value in side['added']) == 8
    assert sum(value['count'] for row in comparison['nodes'] for side in row['sides'] for value in side['removed']) == 594
    native = 'native/posteffects-261-installed-d91ca974-v2/'
    run = document(native + 'run-result.json')
    review = document(native + 'pixel-review.json')
    assert run['mechanicalVerdict'] == review['verdict'] == 'PASS'
    assert run['clientExit'] == run['serverExit'] == 0 and not run['errors'] and not run['forced']
    assert len(run['stages']) == 7 and review['screenshotsReviewed'] and review['sourceBindingVerified']
    for stage in run['stages']:
        assert hashlib.sha256(z.read(native + stage['image'])).hexdigest() == stage['sha256']
    previous = document('native/posteffects-261-installed-d91ca974/run-result.json')
    assert previous['mechanicalVerdict'] == 'NOT GREEN' and previous['errors']
print(json.dumps(dict(archive='PASS', entries=len(manifest['entries']), candidateTests=5066,
    sourceFollowupCommonTests=manifest['sourceFollowupCommonTests'], installedNodes=5,
    fullTypeScript='FAIL: 5867 diagnostics; 594 removed / 8 added', nativeInstalledFrames=7)))
