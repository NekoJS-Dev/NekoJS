import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile

root=Path(__file__).resolve().parent
summary=json.loads((root/'summary.json').read_text())
sha=lambda data:hashlib.sha256(data).hexdigest()
archive=root/summary['archive']['file']
assert archive.stat().st_size==summary['archive']['bytes'] and sha(archive.read_bytes())==summary['archive']['sha256']
with ZipFile(archive) as source:
    assert source.testzip() is None
    assert set(source.namelist())=={row['name'] for row in summary['entries']}
    for row in summary['entries']:
        data=source.read(row['name'])
        assert len(data)==row['bytes'] and sha(data)==row['sha256']
    before=ET.fromstring(source.read('failed/PackSyncClientTest.xml'))
    after=ET.fromstring(source.read('final-matrix/PackSyncClientTest.xml'))
    assert before.get('failures')=='1' and after.get('failures')=='0' and after.get('tests')=='21'
    assert 'LOCAL_TRUSTED' in before.find('testcase/failure').get('message')
    patch=source.read('source/test.patch').decode()
    assert '-        assertTrue(outcome.disconnect().contains("reload"));' in patch
    assert '+        assertTrue(outcome.disconnect().contains("CLIENT script reload failed"), outcome.disconnect());' in patch
    for name,expected in [('sam-module-import-matrix-green',1),('pack-reload-failure-focused',0),('pack-reload-class-check',0),('sam-module-import-matrix-final',0)]:
        assert json.loads(source.read('checks/'+name+'-exit.json').decode('utf-8-sig'))['exitCode']==expected
assert summary['failureRootCause']=='NOT_ESTABLISHED' and summary['productionTrustChange']=='NONE'
print(json.dumps(dict(result='PASS',initialFullMatrix='FAIL',finalFullMatrix='PASS',failureRootCause='NOT_ESTABLISHED',sha256=summary['archive']['sha256'])))
