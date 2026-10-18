"""Verify retained controller failures, recording identity and startup boundaries."""
import hashlib
import json
import math
import re
import zipfile
from collections import Counter
from datetime import datetime, timedelta
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root/'manifest.json').read_text())
packed = root/'evidence.zip'
assert packed.stat().st_size == manifest['archiveBytes']
assert hashlib.sha256(packed.read_bytes()).hexdigest() == manifest['archiveSha256']
assert manifest['controllerExitCodes'] == [1,1] and manifest['rawJfrExcluded']
assert manifest['diagnosticOnly'] and manifest['formalVerdictUnchanged']=='FAIL'
def decode(data):
    if data.startswith((b'\xff\xfe',b'\xfe\xff')): return data.decode('utf-16')
    try: return data.decode('utf-8-sig')
    except UnicodeDecodeError: return data.decode('gb18030')
with zipfile.ZipFile(packed) as archive:
    assert set(archive.namelist())==set(manifest['entries'])
    for name,expected in manifest['entries'].items():
        assert hashlib.sha256(archive.read(name)).hexdigest()==expected,name
        assert not name.endswith(('.jfr','.class','server.properties','.pk8'))
    load=lambda name:json.loads(decode(archive.read(name)))
    initial=load('initial-failure/failure.json')
    assert initial['exitCode']==1 and not initial['gameStarted']
    assert load('initial-failure/startup-jfr-exit.json')['exitCode']==1
    assert load('tools/startup-jfr-v2-exit.json')['exitCode']==1
    prefix='recovered-recording/'
    state=load(prefix+'session.json')
    binding=load(prefix+'binding.json')
    summary=load(prefix+'summary.json')
    analysis=load(prefix+'analysis-at.json')
    assert not state['forcedKill'] and not state['extra']['killed'] and state['extra']['stopChannel']=='rcon'
    assert state['extra']['profileFailure']=='Unexpected game owner or missing startup JFR argument'
    assert summary['controllerExitCodes']==[1,1] and summary['recoveredFromDumpOnExit']
    assert not summary['unassistedJfrStopAtDone'] and summary['formalVerdictUnchanged']=='FAIL'
    stdout=decode(archive.read(prefix+'startup-profile-stdout.log'))
    pid=int(re.search(r'Use jcmd (\d+) JFR.dump name=nekoStartup',stdout).group(1))
    assert pid==analysis['processId']==summary['jfrPid']==18976
    assert analysis['recordingArgumentMatched'] and summary['recordingArgumentMatched']
    assert 'BUILD SUCCESSFUL' in stdout and 'Done (' in stdout
    launch=datetime.fromisoformat(binding['launchRequestedUtc'])
    task=datetime.fromisoformat(re.search(r'STARTUP_PROFILE_GAME_TASK (\S+)',stdout).group(1))
    jvm=datetime.fromisoformat(analysis['jvmStartUtc'])
    done=launch+timedelta(milliseconds=state['doneWallMs'])
    assert launch <= task <= jvm <= done <= datetime.fromisoformat(analysis['lastEventUtc'])
    assert math.isclose((jvm-launch).total_seconds()*1000,summary['launchToJvmMs'])
    assert math.isclose((done-jvm).total_seconds()*1000,summary['jvmToApproximateDoneMs'])
    assert datetime.fromisoformat(analysis['approximateDoneCutoffUtc'])==done
    totals=Counter()
    for bucket in analysis['samplesByUtcSecond'].values(): totals.update(bucket)
    assert dict(totals)==analysis['threadSamples']
    assert analysis['countsAreNotMilliseconds'] and analysis['diagnosticOnly']
    cfg=decode(archive.read('source/accesstransformer.cfg'))
    instructions=[line.split() for line in cfg.splitlines() if re.match(r'^(public|protected|private|default)([+-]f)?\s+',line.strip())]
    assert summary['sourceAt']==dict(lines=len(cfg.splitlines()),instructions=len(instructions),classes=len({p[1] for p in instructions}))
    post=load(prefix+'post-run-bindings.json')
    prep=load('formal-reference/preparation.json')
    assert post['trackedSourcesClean'] and post['normalRunConfigurationRestored']
    assert post['classBindings']==prep['classBindings'] and len(post['classBindings'])==1625
    assert prep['revision']==binding['sourceRevision']==manifest['sourceRevision']
    assert summary['rawJfrSha256']==manifest['rawJfrSha256']
print(json.dumps(dict(archive='PASS',controllerStatus='FAILED_BUT_RECORDING_RECOVERED',
                     launchToJvmMs=summary['launchToJvmMs'],jvmToApproximateDoneMs=summary['jvmToApproximateDoneMs'],
                     normalStop=True,formalVerdictUnchanged='FAIL'),indent=2))
