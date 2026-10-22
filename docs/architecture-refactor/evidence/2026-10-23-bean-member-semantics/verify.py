import hashlib
import json
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
m = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == m['archiveSha256']
with zipfile.ZipFile(root / 'evidence.zip') as z:
    assert len(z.namelist()) == len(set(z.namelist())) == len(m['entries'])
    assert set(z.namelist()) == set(m['entries'])
    for name, sha in m['entries'].items():
        assert hashlib.sha256(z.read(name)).hexdigest() == sha
    def read(name):
        return json.loads(z.read(name).decode('utf-8-sig'))
    result = read('result.json')
    rows = {r['expression']: r for r in result['observations']}
    assert len(rows) == 8
    for name in ['vector', 'section']:
        assert rows['typeof ' + name + '.x']['value'] == 'number'
        assert rows[name + '.x()']['success']
        extracted = rows['(() => { const member = ' + name + '.x; return member(); })()']
        assert not extracted['success'] and extracted['guestException']
        assert 'member is not a function' in extracted['message']
    assert rows['vector.x']['value'] == rows['vector.x()']['value'] == 7
    assert rows['section.x()']['value'] == rows['section.getX()']['value'] == 11
    binding = read('binding.json')
    assert binding['compilerExit'] == binding['runtimeExit'] == binding['intersectionTscExit'] == 0
    config = read('tsconfig.json')['compilerOptions']
    assert config['strict'] and config['skipLibCheck'] is False
    assert not z.read('intersection-tsc-v2.log').strip()
    assert 'const extractedCall: number = extracted();' in z.read('intersection.ts').decode()
    assert b'TS2688' in z.read('intersection-tsc.log')
print(json.dumps(dict(archiveVerification='PASS', diagnosticOnly=True,
    intersectionProjection='REJECTED_FALSE_EXTRACTED_CALL_PROMISE', productionDeclarationChanged=False)))
