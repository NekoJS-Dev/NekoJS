import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

out = Path('docs/architecture-refactor/evidence/ticket37-ts-constructor-member-diagnosis')
record = json.loads((out/'manifest.json').read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()
path = out/record['archive']['file']
assert sha(path.read_bytes()) == record['archive']['sha256'] and path.stat().st_size == record['archive']['bytes']
with ZipFile(path) as archive:
    assert archive.testzip() is None and len(set(archive.namelist())) == len(archive.namelist()) == record['archive']['entries']
    for row in record['entries']:
        data = archive.read(row['path'])
        assert sha(data) == row['sha256'] and len(data) == row['bytes']
    original = archive.read('actual/surface.d.ts')
    assert sha(original) == record['generatedSurfaceSha256']
    for folder in ['ticket37-ts-constructor-member-red','ticket37-ts-constructor-member-resolved-red']:
        assert archive.read('controls/'+folder+'/surface.d.ts') == original
    red = json.loads(archive.read('controls/ticket37-ts-constructor-member-resolved-red/summary.json'))
    models = json.loads(archive.read('controls/ticket37-ts-constructor-member-resolved-models/summary.json'))
    assert red == record['red'] and models == record['models']
    assert len(red['diagnostics']) == 3 and red['exitCode'] == 2
    for model in models['models']:
        name = '"constructor"' if model['model'] == 'quoted' else '["constructor"]'
        expected = original.decode().replace('get constructor(): boolean;', 'get '+name+'(): boolean;').replace('static constructor(parameters:', 'static '+name+'(parameters:')
        actual = archive.read('controls/ticket37-ts-constructor-member-resolved-models/'+model['model']+'/surface.d.ts').decode()
        assert actual == expected
    assert models['models'][0]['checks'][0]['exitCode'] == 2
    assert models['models'][1]['checks'][0]['exitCode'] == 0
    assert models['models'][1]['checks'][1]['exitCode'] == 2 and len(models['models'][1]['checks'][1]['diagnostics']) == 3
    assert b'AssertionError' in archive.read('runner/constructor-member-models.txt')
result = dict(status='PASS', fullEntryHashesAndReadback=True, originalGeneratedBytesRetained=True,
    candidatesExactlyTwoContextualNamesOnly=True, initialHarnessFailuresRetained=True, validCallerAndThreeNegativeRejects=True,
    productionChange='NONE', runtimeAndRealBackendRegression='NOT RUN', repair='NOT COMPLETE', overall='NOT COMPLETE')
assert not (out/'archive-verification.json').exists()
(out/'archive-verification.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
