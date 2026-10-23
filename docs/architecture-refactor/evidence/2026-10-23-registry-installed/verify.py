import collections,hashlib,json,re,zipfile
from pathlib import Path
root=Path(__file__).resolve().parent
m=json.loads((root/'manifest.json').read_text()); assert hashlib.sha256((root/'evidence.zip').read_bytes()).hexdigest()==m['archiveSha256']
def diagnostics(data):
 result=collections.Counter()
 for line in data.decode('utf-8-sig').splitlines():
  match=re.match(r'(.+)\(\d+,\d+\): error (TS\d+): (.*)',line)
  if match:
   name,code,message=match.groups(); name=name.replace('\\','/')
   if '/.neko_probe/' in name:name=name.split('/.neko_probe/',1)[1]
   elif '/nekojs/' in name:name='nekojs/'+name.split('/nekojs/',1)[1]
   result[(name,code,message)]+=1
 return result
with zipfile.ZipFile(root/'evidence.zip') as z:
 assert set(z.namelist())==set(m['entries'])
 for n,v in m['entries'].items():
  data=z.read(n); assert len(data)==v['size'] and hashlib.sha256(data).hexdigest()==v['sha256'],n
 s=json.loads(z.read('installed/ide/summary.json')); prep=json.loads(z.read('installed/preparation-manifest.json'))
 assert s['sourceRevision']==prep['sourceRevision']==m['sourceRevision'] and s['sourceRevision'].startswith('88e18d84')
 expected=[[493,494],[562,563],[551,552],[615,616],[586,587]]
 before=after=added=removed=stubs=0
 for row,wanted in zip(s['nodes'],expected):
  node=row['node']; assert row['pyright']['exitCode']==0
  assert row['pyright']['summary']['errorCount']==row['pyright']['summary']['warningCount']==0
  assert row['pyright']['summary']['filesAnalyzed']==row['pyright']['stubCount']; stubs+=row['pyright']['stubCount']
  assert [r['diagnosticCount'] for r in row['typescript']]==wanted
  for r in row['typescript']:
   assert r['exitCode']==2 and r['skipLibCheck'] is False and r['diagnosticsComplete'] and not r['compilerCrashed']
   name=node+'-'+r['side']+'-tsc.txt'
   current=diagnostics(z.read('installed/ide/'+name)); previous=diagnostics(z.read('previous-ide/'+name))
   assert sum(current.values())==r['diagnosticCount']
   after+=sum(current.values()); before+=sum(previous.values()); added+=sum((current-previous).values());removed+=sum((previous-current).values())
  prefix='installed/'+node+'/'
  assert json.loads(z.read(prefix+'runtime-verdict.json'))['verdict']=='PASS'
  assert json.loads(z.read(prefix+'server-exit.json'))['exitCode']==0
 delta=json.loads(z.read('installed/ide/delta-from-1f877b0f.json'))
 assert delta['total']==dict(before=before,after=after,added=added,removed=removed)==dict(before=5857,after=5619,added=6,removed=244)
 assert stubs==1609
print('PASS: five installed runtime passes/normal exits; 1609 stubs pass; full strict TSC FAIL 5619, removed244/added6.')
