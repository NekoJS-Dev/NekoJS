import hashlib
import json
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
summary = json.loads((root / 'runtime-summary.json').read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()
archive = root / manifest['archive']
assert sha(archive.read_bytes()) == manifest['sha256']
assert archive.stat().st_size == manifest['bytes']
with zipfile.ZipFile(archive) as source:
    assert source.testzip() is None
    assert set(source.namelist()) == {row['name'] for row in manifest['entries']}
    for row in manifest['entries']:
        data = source.read(row['name'])
        assert len(data) == row['bytes'] and sha(data) == row['sha256']
    proof = json.loads(source.read('runtime/pointer-proof.json'))
    assert proof == summary['pointerControl']
    assert proof['crashRbx'] == proof['otherProcessWindow'] != proof['ownWindow']
    assert int(proof['faultAddress'],16) - int(proof['crashRbx'],16) == 0x1f0
    assert proof['directExit']['exitCode'] == 1
    for name in ('minimal-glfw', 'minimal-no-api', 'minimal-pointer-pair'):
        assert json.loads(source.read('runtime/' + name + '/A/exit.json'))['exitCode'] == 1
        assert json.loads(source.read('runtime/' + name + '/B/exit.json'))['exitCode'] == 0
    assert json.loads(source.read('runtime/minimal-pointer-single/A/exit.json'))['exitCode'] == 0
    assert json.loads(source.read('runtime/clientA/exit.json'))['exitCode'] == 1
    assert json.loads(source.read('runtime/clientB/exit.json'))['exitCode'] == 0
    assert json.loads(source.read('runtime/server/exit.json'))['exitCode'] == 0
    for row in summary['retainedRawCrashHashes']:
        assert 'C  [glfw.dll+0x10fa1]' in source.read('runtime/' + row['file'] + '.signal.txt').decode()
    disassembly = source.read('runtime/glfw-crash-disassembly.txt').decode()
    assert '0x10fa1 cmp byte ptr [rbx + 0x1f0], 1' in disassembly
    assert '[GetActiveWindow]' in disassembly and '[GetPropW]' in disassembly
assert summary['overall'] == 'NOT_COMPLETE' and summary['nativeRepair'].startswith('NOT_APPLIED')
print(json.dumps(dict(result='PASS', entries=len(manifest['entries']), sha256=manifest['sha256'])))
