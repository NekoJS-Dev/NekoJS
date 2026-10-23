import hashlib,json,re,zipfile
from pathlib import Path
root=Path(__file__).resolve().parent
m=json.loads((root/'manifest.json').read_text())
assert hashlib.sha256((root/'evidence.zip').read_bytes()).hexdigest()==m['archiveSha256']
selected={'quick-start','script-packs','registering-new-content','type-adapters','event-extensions'}
def body(text):
 text=text.replace('\r\n','\n')
 text=re.sub(r'^<!-- wiki-page:.*?-->\n*','',text,flags=re.S)
 text=re.sub(r'^> \*\*(?:中文|English)\*\*.*\n*','',text)
 text=text.split('<!-- wiki-nav -->')[0]
 return re.sub(r'<a id="wiki-section-\d+"></a>\n','',text).strip()+'\n'
with zipfile.ZipFile(root/'evidence.zip') as z:
 assert set(z.namelist())==set(m['entries'])
 for k,v in m['entries'].items():
  data=z.read(k);assert len(data)==v['size'] and hashlib.sha256(data).hexdigest()==v['sha256'],k
 before=json.loads(z.read('manifest-before.json'));after=json.loads(z.read('manifest-after.json'))
 changed=[]
 for a,b in zip(before['pages'],after['pages']):
  assert a['id']==b['id'] and a.get('humanReview')==b.get('humanReview') and a.get('translationStatus')==b.get('translationStatus')
  if a!=b:
   changed.append(a['id']);assert {k for k in a if a[k]!=b[k]}<={'sourceDigest','translationDigest'}
  if b['id'] in selected:
   for loc,key in [('cn','sourceDigest'),('us','translationDigest')]:
    assert hashlib.sha256(body(z.read(f"wiki/{b['id']}_{loc}.md").decode('utf-8')).encode()).hexdigest()==b[key]
 assert set(changed)==selected
 assert b'26 bilingual topics; 0 problem(s).' in z.read('strict.log')
 assert 'pass 8' in z.read('tests.log').decode('utf-8-sig') and 'fail 0' in z.read('tests.log').decode('utf-8-sig')
 rows=json.loads(z.read('compile/summary.json'))
 assert {r['node'] for r in rows}=={'1.21.1','26.1.2','26.2.0'}
 assert all(r['exitCode']==0 and r['compiledClasses']==3 for r in rows)
 facts=json.loads(z.read('compiled-capability-facts.json'))
 assert facts['sourceRevision']==m['productionRevision']
 assert all(bool(r['dataComponentsClassSha256'])==(r['node']!='1.21.1') for r in facts['nodes'])
print('PASS: five reviewed topic digests, human states unchanged, strict wiki zero problems, 8 tests, three compiled examples.')
