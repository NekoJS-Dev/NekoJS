import hashlib
import json
import statistics
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
    def read(name): return json.loads(z.read(name).decode('utf-8-sig'))
    for trial in ['4405b0b0-reload-jfr','4405b0b0-stage-jfr-v2']:
        assert read(trial+'/exit.json')['exitCode']==0
        session=read(trial+'/session/session.json')
        assert not session['forcedKill'] and not session['extra']['killed'] and session['extra']['stopChannel']=='rcon'
        assert len(read(trial+'/session/observations.json'))==20
    prefix='4405b0b0-stage-jfr-v2/'
    assert read(prefix+'restoration.json')['sourceRestored']
    restored=read(prefix+'restored-candidate.json')
    assert restored['allClassesRestored'] and restored['classCount']==1638 and restored['jarByteIdentical']
    rows=read(prefix+'analysis.json')['stages']
    summary=read(prefix+'stage-summary.json')
    for stage,recorded in summary.items():
        values=[row for row in rows if row['stage']==stage]
        assert len(values)==recorded['count']==10
        assert round(statistics.mean(row['wallNs']/1e6 for row in values),2)==recorded['meanWallMs']
    assert read('4405b0b0-stage-jfr/preparation-failure.json')['sourceRestored']
    assert not any(name.endswith('.jfr') for name in z.namelist())
print(json.dumps(dict(archiveVerification='PASS',diagnosticOnly=True,normalServerStops=2,
    meanStageMs={stage:row['meanWallMs'] for stage,row in summary.items()},formalPerformanceVerdict='NOT_PASS')))
