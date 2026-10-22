import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root=Path(__file__).resolve().parent
manifest=json.loads((root/'manifest.json').read_text())
archive=root/'evidence.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest()==manifest['archiveSha256']
with zipfile.ZipFile(archive) as z:
    assert len(z.namelist())==len(set(z.namelist()))==len(manifest['entries'])
    assert set(z.namelist())==set(manifest['entries'])
    for name,sha in manifest['entries'].items(): assert hashlib.sha256(z.read(name)).hexdigest()==sha,name
    review=json.loads(z.read('review/binding.json'))
    assert review['files']==manifest['sourceHashes'] and review['base']==manifest['base']
    for name,sha in manifest['sourceHashes'].items(): assert hashlib.sha256(z.read('source/'+name)).hexdigest()==sha,name
    assert hashlib.sha256(z.read('review/source.patch')).hexdigest()==review['patchSha256']
    totals=dict.fromkeys(['tests','skipped','failures','errors'],0)
    for name in z.namelist():
        if name.startswith('checks/') and name.endswith('.xml'):
            suite=ET.fromstring(z.read(name))
            for key in totals: totals[key]+=int(suite.get(key,'0'))
    assert totals==manifest['tests']==dict(tests=3113,skipped=85,failures=0,errors=0)
    assert b'BUILD SUCCESSFUL in 6m 50s' in z.read('tooling/source-facts-owner-check.log')
    assert b'BUILD FAILED' in z.read('tooling/source-facts-red.log')
    assert len(manifest['classes'])==1640
print(json.dumps(dict(archiveVerification='PASS',tests=totals,sourceFiles=9,
    artifactBindingMetadata=True,artifactNotPacked=True,performanceNotEstablishedByTheseChecks=True)))
