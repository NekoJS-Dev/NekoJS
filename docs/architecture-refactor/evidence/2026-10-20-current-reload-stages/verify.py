"""Recompute current stage observations from retained owner-thread events."""
import hashlib
import json
import math
import statistics
import zipfile
from pathlib import Path

directory = Path(__file__).resolve().parent
binding = json.loads((directory/'verification.json').read_text(encoding='utf-8'))
archive = directory/'evidence.zip'
assert archive.stat().st_size == binding['bytes']
assert hashlib.sha256(archive.read_bytes()).hexdigest() == binding['sha256']
with zipfile.ZipFile(archive) as evidence:
    assert len(evidence.namelist()) == binding['entries']
    manifest = json.loads(evidence.read('manifest.json'))
    for name,expected in manifest.items():
        assert hashlib.sha256(evidence.read(name)).hexdigest() == expected, name
    summary = json.loads(evidence.read('summary.json'))
    events = json.loads(evidence.read('server-events.json'))['events']
    stages = [e['values'] for e in events if e['type']=='neko.ReloadStage']
    assert len(stages)==60
    for stage in ('root','recipe','command'):
        selected = [r for r in stages if r['stage']==stage]
        assert len(selected)==20 and all(r['eventThread']['javaName']=='Server thread' for r in selected)
        assert math.isclose(statistics.mean(r['wallNs']/1e6 for r in selected),summary['stages'][stage]['wallMs'])
        assert math.isclose(statistics.mean(r['cpuNs']/1e6 for r in selected),summary['stages'][stage]['cpuMs'])
    rows = json.loads(evidence.read('session/observations.json').decode('utf-8-sig'))
    assert len(rows)==40
    reloads = [r for r in rows if r['command']=='nekojs reload']
    queries = [r for r in rows if r['command']=='list']
    assert len(reloads)==len(queries)==20
    assert {r['pair'] for r in reloads}=={r['pair'] for r in queries}==set(range(1,21))
    assert all('phase=COMMIT - no errors.' in r['response'] for r in reloads)
    assert math.isclose(statistics.mean(r['rconMs'] for r in reloads),summary['reloadRconMs'])
    assert math.isclose(statistics.mean(r['rconMs'] for r in queries),summary['queryRconMs'])
    assert math.isclose(statistics.mean(r['markerMs'] for r in reloads),summary['markerMs'])
    assert math.isclose(summary['reloadRconMs']-summary['stages']['command']['wallMs'],summary['outsideCommandMs'])
    state = json.loads(evidence.read('session/session.json').decode('utf-8-sig'))
    assert not state['forcedKill'] and not state['extra']['killed'] and state['extra']['stopChannel']=='rcon'
    restored = json.loads(evidence.read('restoration.json'))
    formal = json.loads(evidence.read('formal-source-binding.json'))
    assert all(restored[k] for k in ('sourceRestored','worktreeClean','classesMatchFormal','jarMatchesFormal'))
    assert restored['classCount']==len(formal['classBindings'])==1627
    assert restored['jarSha256']==formal['jarSha256']
    for stage in ('build-diagnostic','run-diagnostic','build-restored'):
        assert json.loads(evidence.read(stage+'-result.json'))['exitCode']==0
print('PASS: all 20 stage/query pairs and exact restoration binding; formal verdict remains NOT_PASS')
