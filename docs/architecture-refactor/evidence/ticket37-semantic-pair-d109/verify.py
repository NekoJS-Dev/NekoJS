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
        assert rejected['generation']==4 and rejected['active'] and '[NEKO-1008]' in text
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
assert summary['firstPixel']=='NOT_VERIFIED' and summary['nativeScreenshots']==18
print(json.dumps(dict(result='PASS',entries=len(manifest['entries']),pairs=len(summary['pixelPairs']),sha256=manifest['sha256'])))
