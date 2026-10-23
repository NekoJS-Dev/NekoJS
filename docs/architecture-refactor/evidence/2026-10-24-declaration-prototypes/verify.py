"""Verify the rejected representation experiment without treating expected negative cases as success gates."""
import hashlib
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    assert len(archive.namelist()) == len(set(archive.namelist()))
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert not name.startswith(('/', '\\')) and '..' not in Path(name).parts
        data = archive.read(name)
        assert len(data) == expected['size'] and hashlib.sha256(data).hexdigest() == expected['sha256']
    result = json.loads(archive.read('raw-interface-overload-result.json'))
    assert result['rawFirstOrdinaryCallsExit'] == 0 and result['unchangedRawExplicitGuaranteeExit'] == 2
    assert result['javaRejectsRawExplicitPrecisionExit'] == 1 and not result['productionAdopted']
    assert not archive.read('raw-interface-overload-base.log').strip()
    assert 'TS2578' in archive.read('raw-interface-overload-no-new-precision.log').decode()
    assert b'compiler.err.prob.found.req' in archive.read('raw-cycle-invalid-precision-v2.log')
    log = archive.read('raw-cycle-java.log').decode()
    assert 'RAW-EXPLICIT-TYPE-ARGUMENT-RETAINS-ENUM-RESULT' in log
    assert 'INTERFACE-VIEW-CAST-CAN-FAIL-FOR-UNCHECKED-RAW-IMPLEMENTATION' in log
print(json.dumps(dict(archive='PASS', productionAdopted=False, ordinaryOverloadSelection='PASS',
    unchangedConcreteGenericGuarantee='FAIL', scope='raw-interface-overload-prototype-only')))
