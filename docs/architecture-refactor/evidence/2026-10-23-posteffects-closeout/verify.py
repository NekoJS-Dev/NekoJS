import hashlib,json,xml.etree.ElementTree as ET,zipfile
from pathlib import Path
root=Path(__file__).resolve().parent
m=json.loads((root/'manifest.json').read_text())
assert hashlib.sha256((root/'evidence.zip').read_bytes()).hexdigest()==m['archiveSha256']
with zipfile.ZipFile(root/'evidence.zip') as z:
 assert set(z.namelist())==set(m['entries'])
 for n,v in m['entries'].items():
  d=z.read(n); assert len(d)==v['size'] and hashlib.sha256(d).hexdigest()==v['sha256'],n
 a=json.loads(z.read('source-audit.json')); s=json.loads(z.read('summary.json'))
 assert s['currentSource']==a['currentSource'] and s['currentSource'].startswith('88e18d84')
 assert s['componentFiles']==len(a['componentSources'])==14
 for row in a['componentSources']:
  assert row['unchangedFromInstalledSource'] and hashlib.sha256(z.read('source/'+row['path'])).hexdigest()==row['sha256']
 assert all(row['kind']=='comment' for row in a['oldStaticSymbolMentions'])
 count=dict(tests=0,skipped=0,failures=0,errors=0)
 for n in z.namelist():
  if n.startswith('tests/'):
   r=ET.fromstring(z.read(n))
   for k in count:count[k]+=int(r.get(k,'0'))
 assert count==s['tests'] and count['failures']==count['errors']==0
 assert not s['wholeCurrentCandidateClientRetest'] and not s['wholeReleaseAcceptance']
 assert not s['physicalKeyboardAndPresentAcceptance'] and not s['otherMigrationDeletionAuthorized']
 for folder in ['2026-10-22-unified-client-native','2026-10-22-posteffects-legacy-installed']:
  assert json.loads(z.read('prior-evidence/'+folder+'-verify.log'))['archive']=='PASS'
  expected=z.read('prior-evidence/'+folder+'-manifest.json')
  peer=root.parent/folder/'manifest.json'
  assert peer.read_bytes()==expected
  pm=json.loads(expected)
  assert hashlib.sha256((peer.parent/'evidence.zip').read_bytes()).hexdigest()==pm['archiveSha256']
 assert '追认以下精确接口及行为变化' in z.read('acceptance/PUBLIC-MIGRATIONS.md').decode('utf-8')
print('PASS: ticket 28 engineering evidence, exact existing deletion approval, 14 unchanged sources; release gates remain separate.')
