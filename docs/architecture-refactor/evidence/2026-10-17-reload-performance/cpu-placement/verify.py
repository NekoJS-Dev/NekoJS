"""Verify fixed inputs, independently recompute contrasts, and preserve the formal FAIL."""
import hashlib
import json
import math
import re
import statistics
import zipfile
from pathlib import Path

root=Path(__file__).resolve().parent
manifest=json.loads((root/'manifest.json').read_text(encoding='utf-8'))
packed=root/'evidence.zip'
assert hashlib.sha256(packed.read_bytes()).hexdigest()==manifest['archiveSha256']
with zipfile.ZipFile(packed) as archive:
    names=archive.namelist()
    assert len(names)==len(set(names)) and set(names)==set(manifest['entries'])
    for name,wanted in manifest['entries'].items():
        raw=archive.read(name)
        assert len(raw)==wanted['bytes'] and hashlib.sha256(raw).hexdigest()==wanted['sha256'],name
    load=lambda name:json.loads(archive.read(name).decode('utf-8-sig'))
    close=lambda a,b:math.isclose(a,b,abs_tol=1e-8)
    bindings=load('input-bindings.json')
    assert bindings['formalVerdictUnchanged']=='FAIL' and bindings['commonMatchesFormal']
    assert len(bindings['commonClassBindings'])==1152
    assert load('root-cpu-split/class-binding.json')['commonClassBindings']==bindings['commonClassBindings']
    assert load('owned-worktree-status.json')['clean']
    redactions=load('redactions.json')
    for relative,sha in bindings['fixtureHashes'].items():
        name='fixtures/'+relative.removeprefix('bench/perf/fixtures/')
        raw=archive.read(name)
        if name in redactions:
            # Original bytes were checked locally before redaction; archived bytes have their
            # own manifest digest and cannot reproduce the original digest without the secret.
            assert redactions[name]['originalSha256']==sha
            assert raw.count(b'<REDACTED-RCON-CREDENTIAL>')==redactions[name]['occurrences']
        else:
            assert hashlib.sha256(raw).hexdigest()==sha
    sourcebindings=load('path-cpu-attribution/source-bindings.json')
    for relative,wanted in sourcebindings.items():
        name=Path(relative).name
        original=archive.read('path-cpu-attribution/sources/'+Path(name).stem+'.original.java')
        assert hashlib.sha256(original).hexdigest()==wanted['originalSha256']
        assert original==archive.read('current-sources/'+name)
        assert hashlib.sha256(archive.read('path-cpu-attribution/sources/'+name)).hexdigest()==wanted['shadowSha256']
    for label in ['root-cpu-split','path-cpu-attribution','root-native-attribution','root-cpu-placement','root-thread-qos-control']:
        rows=load(label+'/samples.json')
        assert [r['index'] for r in rows]==list(range(50 if label=='root-thread-qos-control' else 30))
        assert all(r['contextReplaced'] and r['preparedEntries']==6 and r['oldContextClosed']==(r['index']>0) for r in rows)
        if label in ['root-cpu-split','path-cpu-attribution']:
            summary=load(label+'/summary.json')
            for group,chosen in [('all',rows),('firstTen',rows[:10]),('lastTwenty',rows[10:])]:
                expected=summary['groups'][group]
                assert close(statistics.mean(r['nanos']/1e6 for r in chosen),expected['wallMs'])
                assert close(statistics.mean(r['cpuNanos']/1e6 for r in chosen),expected['cpuMs'])
                if label=='root-cpu-split':
                    assert close(statistics.mean(r['userNanos']/1e6 for r in chosen),expected['userMs'])
                    assert close(statistics.mean((r['cpuNanos']-r['userNanos'])/1e6 for r in chosen),expected['kernelByDifferenceMs'])
                else:
                    for bucket,calls in [('canonical',24),('containment',6)]:
                        assert all(r['pathCounters'][bucket]['calls']==calls for r in chosen)
                        for key in ['calls','wallNanos','cpuNanos','userNanos']:
                            assert close(statistics.mean(r['pathCounters'][bucket][key]/(1 if key=='calls' else 1e6) for r in chosen),expected[bucket][key])
    topology=load('root-cpu-placement/topology.json')['rows']
    classes={r['logicalProcessor']:r['efficiencyClass'] for r in topology}
    assert classes=={i:int(i<12) for i in range(20)}
    placement=load('root-cpu-placement/samples.json')
    assert all(classes[r['processorBefore']]==classes[r['processorAfter']]==0 for r in placement[1:])
    component=load('root-thread-qos-control/samples.json')
    original=load('root-thread-qos-control/original-policy.json')
    assert original==load('root-thread-qos-control/restored-policy.json')==[1,0,0]
    for pair in range(20):
        assert [r['arm'] for r in component if r['pair']==pair]==(['original','high-qos'] if pair%2==0 else ['high-qos','original'])
    componentSummary=load('root-thread-qos-control/summary.json')
    for arm in ['warmup-original','original','high-qos']:
        chosen=[r for r in component if r['arm']==arm]
        assert close(statistics.mean(r['nanos']/1e6 for r in chosen),componentSummary['arms'][arm]['wallMs'])
        assert close(statistics.mean(r['cpuNanos']/1e6 for r in chosen),componentSummary['arms'][arm]['cpuMs'])
        assert all(r['policyDuringReload']==([1,1,0] if arm=='high-qos' else original) for r in chosen)
        if arm!='warmup-original':assert all(classes[r['processorAfter']]==int(arm=='high-qos') for r in chosen)
    native=load('root-native-attribution/summary.json')
    assert native['lastTwenty']['eventCounts']['jdk.NativeMethodSample']==52
    assert native['lastTwenty']['eventCounts']['jdk.ExecutionSample']==59
    assert not load('root-native-attribution/input-binding.json')['binaryArchived']
    game='game-thread-qos-control-6e5bb8f0/'
    rows=load(game+'observations.json')
    assert len(rows)==40
    assert all('phase=COMMIT - no errors.' in r['response'] for r in rows)
    thread=load(game+'thread-policy-binding.json')
    assert thread['originalPolicy']==[1,0,0]
    assert load(game+'restored-thread-policy.json')['policy']==thread['originalPolicy']
    summary=load(game+'summary.json')
    for pair in range(1,21):
        assert [r['arm'] for r in rows if r['pair']==pair]==(['original','high-qos'] if pair%2 else ['high-qos','original'])
    for arm in ['original','high-qos']:
        chosen=[r for r in rows if r['arm']==arm]
        for key in ['rconMs','markerMs']:
            assert close(statistics.mean(r[key] for r in chosen),summary['arms'][arm][key])
        assert all(r['threadBefore']['policy']==r['threadAfter']['policy']==([1,1,0] if arm=='high-qos' else thread['originalPolicy']) for r in chosen)
        assert all(r['threadBefore']['threadTimes100ns'][0]==r['threadAfter']['threadTimes100ns'][0]==thread['createdAt'] for r in chosen)
    session=load(game+'session.json')
    assert not session['forcedKill'] and not session['extra']['killed'] and session['extra']['stopChannel']=='rcon'
    assert load('tooling/game-thread-qos-control-exit.json')['exitCode']==0
    frozen=load('tooling/game-thread-qos-control-bindings.json')
    assert frozen['classesMatchOriginalFormal'] and len(frozen['classBindings'])==1625
    assert frozen['formalVerdictUnchanged']=='FAIL'
print(json.dumps(dict(integrity='PASS',componentPairs=20,gamePairs=20,gameMeans=summary['arms'],formalVerdict='FAIL')))
