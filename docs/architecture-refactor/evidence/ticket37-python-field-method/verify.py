import hashlib
import io
import json
import subprocess
from pathlib import Path
from zipfile import ZipFile

root = Path.cwd().resolve()
destination = root/'docs/architecture-refactor/evidence/ticket37-python-field-method'
manifest = json.loads((destination/'manifest.json').read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()
archive_path = destination/manifest['archive']['file']
assert sha(archive_path.read_bytes()) == manifest['archive']['sha256']
assert archive_path.stat().st_size == manifest['archive']['bytes']
with ZipFile(archive_path) as archive:
    assert archive.testzip() is None
    assert len(set(archive.namelist())) == len(archive.namelist()) == manifest['archive']['entries']
    for row in manifest['entries']:
        data = archive.read(row['path'])
        assert len(data) == row['bytes'] and sha(data) == row['sha256']
    patch = subprocess.check_output(['git', 'diff', '--abbrev=9', manifest['sourceBase'], '--', *manifest['testSourceFiles']])
    assert patch == archive.read('source/test.patch')
    assert not subprocess.check_output(['git', 'diff', manifest['sourceBase'], '--', 'common/src/main/java'])
    for name, expected in manifest['testSourceFiles'].items():
        assert sha((root/name).read_bytes()) == expected
    assert 'BUILD SUCCESSFUL' in archive.read('runner/python-field-method-common-check.txt').decode('utf-8-sig')
    red = json.loads(archive.read('control/ticket37-python-field-method-red-compiler/summary.json'))
    assert red == manifest['originalCompiler']
    assert [row['summary']['errorCount'] for row in red] == [2, 6, 7, 3]
    assert all(row['exitCode'] == 1 for row in red)
    models = json.loads(archive.read('control/ticket37-python-field-method-model-controls/summary.json'))
    assert models == manifest['standaloneModels']
    assert [row['summary']['errorCount'] for row in models['rows']] == [3, 0, 4, 2, 1, 0]
    assert all(row['summary']['warningCount'] == 1 for row in models['rows'])
    facts = json.loads(archive.read('generated/probe-python-field-method-host-facts/host-facts.json'))
    assert facts == manifest['realHostFacts']
console = root/'build/ticket37-config-sync-repair/python-field-method-archive.txt'
console_document = json.loads(console.read_text(encoding='utf-8-sig'))
assert console_document['archive'] == manifest['archive']
assert console_document['commonCheck'] == manifest['commonCheck']['total']
assert console_document['testPatchSha256'] == manifest['testPatchSha256']
target = destination/'archive-console.txt'
assert not target.exists()
target.write_bytes(console.read_bytes())
result = dict(status='PASS', archive=manifest['archive'], fullEntryHashes=True,
              currentTestPatchMatches=True, productionUnchanged=True, originalRedRetained=True,
              modelCounterexamplesAndWarningsRetained=True, completedArchiveConsoleSha256=sha(console.read_bytes()),
              compilerStatus='FAIL', nodeMatrix='NOT RUN', maintainerAcceptance='NOT RECORDED')
(destination/'archive-verification.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
print(json.dumps(result))
