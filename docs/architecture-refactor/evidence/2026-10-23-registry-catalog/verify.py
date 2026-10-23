import hashlib,json,xml.etree.ElementTree as ET,zipfile
from pathlib import Path
root=Path(__file__).resolve().parent
m=json.loads((root/'manifest.json').read_text())
assert hashlib.sha256((root/'evidence.zip').read_bytes()).hexdigest()==m['archiveSha256']
with zipfile.ZipFile(root/'evidence.zip') as z:
 assert set(z.namelist())==set(m['entries'])
 for n,v in m['entries'].items():
  d=z.read(n); assert len(d)==v['size'] and hashlib.sha256(d).hexdigest()==v['sha256'],n
 s=json.loads(z.read('summary.json')); assert s['productionRevision'].startswith('88e18d84') and not s['maintainerAcceptance']
 totals=dict(tests=0,skipped=0,failures=0,errors=0)
 for n in z.namelist():
  if n.startswith('registry-catalog-matrix/') and n.endswith('.xml'):
   suite=ET.fromstring(z.read(n))
   for k in totals: totals[k]+=int(suite.get(k,'0'))
 assert totals==s['matrix']['total'] and totals['failures']==totals['errors']==0,totals
 focus=dict(tests=0,skipped=0,failures=0,errors=0)
 for n in z.namelist():
  if n.startswith('registry-catalog-focused-v2-xml/') and n.endswith('.xml'):
   suite=ET.fromstring(z.read(n))
   for k in focus: focus[k]+=int(suite.get(k,'0'))
 assert focus==dict(tests=48,skipped=0,failures=0,errors=0)
 for name,fail in [('registry-catalog-common-red.xml',3),('registry-catalog-blockstate-red.xml',1),('registry-catalog-subclass-red.xml',1)]:
  assert int(ET.fromstring(z.read('tools/'+name)).get('failures'))==fail
 assert z.read('tools/registry-catalog-tsc-red.log').count(b'error TS2694:')==2
 for mode in ['full','server']: assert not z.read('tools/registry-catalog-tsc-'+mode+'-green.log')
 assert z.read('tools/bean-receiver-hypothesis-v2.log').count(b'error TS2578:')==2
 assert 'NOT RUN' in s['fullInstalledIde'] and 'NOT RUN' in s['performance']
print('PASS: source-bound registry red/green, 48 focused tests, full matrix; installed IDE and performance remain separate.')
