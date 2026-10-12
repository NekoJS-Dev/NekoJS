import hashlib
import io
import json
import zipfile
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parent
manifest = json.loads((root/'manifest.json').read_text())
summary = json.loads((root/'runtime-summary.json').read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()
archive = root/manifest['archive']
assert sha(archive.read_bytes())==manifest['sha256'] and archive.stat().st_size==manifest['bytes']
def rows(text,marker):
    return [json.loads(line.split(marker+' ',1)[1]) for line in text.splitlines() if marker+' ' in line]
def edge(data):
    with Image.open(io.BytesIO(data)) as original: original.verify()
    with Image.open(io.BytesIO(data)) as original:
        frame=original.convert('RGB')
        assert frame.size==(854,480)
        return max(sum(abs(a-b) for a,b in zip(frame.getpixel((x,180)),frame.getpixel((x+1,180)))) for x in range(170,519))
with zipfile.ZipFile(archive) as source:
    assert source.testzip() is None
    assert set(source.namelist())=={row['name'] for row in manifest['entries']}
    for row in manifest['entries']:
        data=source.read(row['name'])
        assert len(data)==row['bytes'] and sha(data)==row['sha256']
    for node in ('server','clientA','clientB'):
        assert json.loads(source.read('runtime/'+node+'/exit.json'))['exitCode']==0
    for letter in ('A','B'):
        text=source.read('runtime/client'+letter+'/stdout.log').decode(errors='replace')
        assert rows(text,'SEMANTIC-FIRST-HUD')==summary['firstHud'][letter]
        states=rows(text,'SEMANTIC-NATIVE-CAPTURE')
        assert states==summary['steps'][letter]
        rejected=next(row for row in states if row['step']=='vanilla-rejected')
        assert rejected['generation']==2 and rejected['active'] and '[NEKO-1008]' in text
        assert json.loads(source.read('runtime/client'+letter+'/close-probe.json'))['result']=='PASS'
    for pair in summary['pixelPairs']:
        calculated={}
        for mode,row in pair['images'].items():
            data=source.read('runtime/client'+pair['client']+'/screenshots/'+row['file'])
            assert sha(data)==row['sha256']
            calculated[mode]=edge(data)
            assert calculated[mode]==row['maximumAdjacentRgbDifference']
        assert calculated['clear']>calculated['vanilla']
    sync=rows(source.read('runtime/server/stdout.log').decode(errors='replace'),'CONFIG-SYNC-PROOF')[-1]
    assert sync==summary['sync'] and sync['status']=='COMMITTED' and sync['activatedGeneration']==3
    for outcome in ('ack','activation-report'):
        assert len({row['participant'] for row in sync['outcomes'] if row['generation']==3 and row['outcome']==outcome})==2
assert summary['firstPairA'].startswith('FAIL') and summary['overall']=='NOT_COMPLETE'
assert summary['firstPixel']=='NOT_VERIFIED' and summary['nativeScreenshots']==20
print(json.dumps(dict(result='PASS',entries=len(manifest['entries']),pairs=len(summary['pixelPairs']),sha256=manifest['sha256'])))

assert summary['productionRevision']=='7d77308bfa400721304b20940ada91092180a1ed'
assert summary['artifactSha256']=='2a64c34ca2d7abc3e8b2cd6402b17f204b3c10b4d15fcd1c2ebc917b3dd9aff2'
assert len(summary['compiledClassBindings'])==1592 and summary['firstRecoveredA'].startswith('FAIL')
assert summary['noBridge'] and summary['noCrossProcessSkyActivation']
with zipfile.ZipFile(archive) as source:
    preparation=json.loads(source.read('runtime/preparation.json'))
    assert preparation['classBindings']==summary['compiledClassBindings'] and preparation['installedCopies']==3
    assert not preparation['httpBridgeInstalled'] and not preparation['crossProcessWindowActivation']
    captures=[json.loads(line) for line in source.read('runtime/native-captures.jsonl').decode().splitlines()]
    images=[row for row in captures if row['screenshots']]
    assert len(images)==20 and all(not row['firstPixelFrame'] for row in images)
    assert len([name for name in source.namelist() if '/screenshots/' in name])==20
    for row in images:
        assert len(row['screenshots'])==1
        image=row['screenshots'][0]
        data=source.read('runtime/client'+row['client']+'/screenshots/'+image['file'])
        assert len(data)==image['bytes'] and sha(data)==image['sha256']
    rcon=[json.loads(line) for line in source.read('runtime/rcon.jsonl').decode().splitlines()]
    assert any('not loaded' in row['response'] for row in rcon)
    assert len([row for row in rcon if row['command'].startswith('give ') and 'Gave 1' in row['response']])==2
    assert rcon[-1]['command']=='stop' and 'Stopping' in rcon[-1]['response']
    for mode in ('reject','recover'):
        assert json.loads(source.read('runtime/'+mode+'-candidate.json'))['ready']
    server=source.read('runtime/server/stdout.log').decode(errors='replace')
    assert server.count('Dynamic registry configuration synchronized for ')==2
    assert any(row['status']=='AWAITING_ACK' and row['activatedGeneration']==2 for row in rows(server,'CONFIG-SYNC-PROOF'))
    for letter in ('A','B'):
        assert [row['generation'] for row in summary['firstHud'][letter]]==[1,2,3]
        assert json.loads(source.read('runtime/client'+letter+'/close-probe.json'))['observedSeconds']<10
print(json.dumps(dict(result='PASS',rawNativeScreenshots=20,selectedPixelPairs=6,firstPixel='NOT_VERIFIED',overall='NOT_COMPLETE')))
