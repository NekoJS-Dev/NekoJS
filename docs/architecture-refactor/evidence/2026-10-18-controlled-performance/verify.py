"""Recompute both controlled cohorts at the original limits and verify actual artifact bindings."""
import argparse
import datetime
import hashlib
import io
import json
import math
import re
import statistics
import zipfile
from pathlib import Path

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--require-current-pass',action='store_true')
args=parser.parse_args()
root=Path(__file__).resolve().parent
manifest=json.loads((root/'manifest.json').read_text(encoding='utf-8'))
packed=root/'evidence.zip'
assert hashlib.sha256(packed.read_bytes()).hexdigest()==manifest['archiveSha256']
with zipfile.ZipFile(packed) as archive:
    names=archive.namelist()
    assert len(names)==len(set(names)) and set(names)==set(manifest['entries'])
    for name,expected in manifest['entries'].items():
        raw=archive.read(name)
        assert len(raw)==expected['bytes'] and hashlib.sha256(raw).hexdigest()==expected['sha256'],name
    load=lambda name:json.loads(archive.read(name).decode('utf-8-sig'))
    redactions=load('redactions.json')
    post=load('post-run-bindings.json')
    current_pass=False
    verdicts={}
    for label in ['current','historical']:
        prefix=label+'/metadata/'
        prep=load(prefix+'preparation.json')
        revision=prep['revision']
        assert revision.startswith('6e5bb8f0' if label=='current' else '3400e97e')
        expected=prep['classBindings'] if label=='current' else load(prefix+'class-bindings.json')
        assert post[label]['classBindings']==expected and post[label]['trackedSourcesClean']
        jars={}
        for name in ['current-neoforge'] if label=='current' else ['historical-common','historical-neoforge']:
            jars[name]=zipfile.ZipFile(io.BytesIO(archive.read('binaries/'+name+'.jar')))
        for path,sha in expected.items():
            module='current-neoforge' if label=='current' else 'historical-common' if path.startswith('common/') else 'historical-neoforge'
            key=path if label=='current' else path.split('/build/classes/java/main/',1)[1]
            assert hashlib.sha256(jars[module].read(key)).hexdigest()==sha,path
        harness=load(label+'/harness-bindings.json')
        assert harness['bench/perf/sample.ps1']=='1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4'
        for relative,sha in harness.items():
            name=label+'/harness/'+relative.removeprefix('bench/perf/')
            if name in redactions:
                assert redactions[name]['originalSha256']==sha
                assert archive.read(name).count(b'<REDACTED-RCON-CREDENTIAL>')==redactions[name]['occurrences']
            else:assert hashlib.sha256(archive.read(name)).hexdigest()==sha
        groups=load(prefix+'groups.json')
        judgment=load(prefix+'judgment.json')
        assert [r['group'] for r in groups]==['reload-1','reload-2','startup']
        assert judgment['executionPolicyChanged'] and not judgment['samplerChanged'] and judgment['originalUncontrolledVerdict']=='FAIL'
        result=[]
        for group,recorded in zip(groups,judgment['groups'],strict=True):
            groupname=group['group']
            assert group['exitCode']==0
            rows=[json.loads(line) for line in archive.read(label+'/'+groupname+'/samples.jsonl').decode('utf-8-sig').splitlines() if line.strip()]
            startup=groupname=='startup'
            assert len(rows)==(7 if startup else 5)
            assert [r['index'] for r in rows]==list(range(1,len(rows)+1))
            assert [r['kind'] for r in rows]==(['warmup']*2+['formal']*5 if startup else ['formal']*5)
            field,limit=('wall_done_ms',41173) if startup else ('marker_ms',285.3)
            invalid=[]
            for row in rows:
                assert row['rev']==revision[:8] and row['node']=='26.1.2'
                valid=row.get(field) is not None and row.get('wall_done_ms') is not None and row.get('done_s') is not None
                valid=valid and row.get('forced_kill') is False and row.get('killed') is False and not row.get('timeout') and row.get('stop_channel')=='rcon'
                if not startup:
                    response=row.get('rcon_response','').strip()
                    valid=valid and (response=='NekoJS server scripts reloaded. - no errors.' if label=='historical' else 'phase=COMMIT - no errors.' in response)
                    valid=valid and 'PERF02-SERVER tick-bench load' in row.get('marker_line','')
                if not valid:invalid.append(row['index'])
            formal=[r for r in rows if r['kind']=='formal']
            values=[r[field] for r in formal]
            mean=statistics.mean(values) if not invalid else None
            verdict='INVALID' if invalid else 'PASS' if mean<=limit else 'FAIL'
            assert recorded['values']==values and recorded['mean']==mean and recorded['limit']==limit and recorded['verdict']==verdict and recorded['invalidIndices']==invalid
            policy=load(prefix+groupname+'-policy.json')
            assert policy['error'] is None and not policy['globalSettingsChanged']
            assert len(policy['processes'])==(7 if startup else 1)
            for process,row in zip(policy['processes'],rows if startup else rows[:1],strict=True):
                assert process['owned'] and process['originalPolicy']==[1,0,0] and process['appliedPolicy']==[1,1,0]
                assert process['status']=='process-exited' and process['lifetimeEnded'] and process['readbacks']>1
                text=archive.read(label+'/'+groupname+'/'+row['tag']+'-stdout.log').decode('utf-8-sig',errors='replace')
                match=re.search(r'\[(\d\d:\d\d:\d\d)\][^\r\n]*Done \(\d+\.\d+s\)!',text)
                assert match
                created=process['createdAt100ns']/1e7-11644473600
                zone=datetime.timezone(datetime.timedelta(hours=8))
                date=datetime.datetime.fromtimestamp(created,zone).date()
                done=datetime.datetime.combine(date,datetime.time.fromisoformat(match[1]),zone).timestamp()
                if done<created:done+=86400
                assert process['appliedAtUnix']<done
            result.append(dict(group=groupname,values=values,mean=mean,verdict=verdict))
        verdicts[label]=result
        passed=all(r['verdict']=='PASS' for r in result)
        assert judgment['controlledVerdict']==('PASS' if passed else 'FAIL')
        if label=='current':current_pass=passed
        else:
            initial=load(prefix+'judgment-first-response-rejection.json')
            assert all(r['verdict']=='INVALID' and r['invalidIndices']==[1,2,3,4,5] for r in initial['groups'][:2])
            assert all(a['values']==b['values'] for a,b in zip(initial['groups'],judgment['groups'],strict=True))
print(json.dumps(dict(integrity='PASS',controlledResults=verdicts,originalUncontrolledVerdict='FAIL')))
if args.require_current_pass and not current_pass:raise SystemExit(2)
