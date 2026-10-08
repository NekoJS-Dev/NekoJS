import hashlib
import json
import re
import subprocess
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path.cwd()
owned = root / 'build/ticket37-perf-owned-ac810fa7'
repair = root / 'build/ticket37-config-sync-repair'
work = root / 'build/ticket37-perf-085698ab'
destination = root / 'docs/architecture-refactor/evidence/ticket37-0856-performance'
revision = '085698ab1de7e37438b16823ac538ca15a50f87e'
sampler_sha = '1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4'
sha = lambda data: hashlib.sha256(data).hexdigest()
assert not destination.exists(), 'Preserve completed evidence'
assert subprocess.check_output(['git', '-C', str(owned), 'rev-parse', 'HEAD'], text=True).strip() == revision
assert not subprocess.check_output(['git', '-C', str(owned), 'status', '--porcelain'], text=True).strip()
assert sha((owned / 'bench/perf/sample.ps1').read_bytes()) == sampler_sha
console = (repair / 'probe-performance-confirmed-console.txt').read_text(encoding='utf-8-sig')
for label in ['reload-confirmed-1', 'reload-confirmed-2', 'startup-confirmed']:
    assert console.count(f'FORMAL_GROUP={label} EXIT=0') == 1
secrets = [line.partition('=')[2].encode() for line in (owned / 'versions/26.1.2/run/server.properties').read_text().splitlines() if line.startswith('rcon.password=')]
def checked(path):
    data = path.read_bytes()
    scanned = data.replace(b'.gradle-perf02', b'[owned-gradle-cache]')
    assert not any(secret and secret in scanned for secret in secrets), path
    assert not re.search(rb'(?m)^rcon\.password=', data), path
    return data

folders = []
for folder in sorted((owned / 'bench/perf/out').iterdir()):
    if not (folder / 'samples.jsonl').is_file():
        continue
    samples = [json.loads(line) for line in (folder / 'samples.jsonl').read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    if samples and samples[0]['rev'] == revision[:8]:
        folders.append((folder, samples))
assert len(folders) == 4
assert [samples[0]['mode'] for folder, samples in folders] == ['reload', 'reload', 'reload', 'startup']
destination.mkdir()
groups = []
all_entries = []
for index, (folder, samples) in enumerate(folders):
    mode = samples[0]['mode']
    assert all(s['rev'] == revision[:8] and s['node'] == '26.1.2' and not s['forced_kill'] and not s['killed'] and s['stop_channel'] == 'rcon' for s in samples)
    formal = [s for s in samples if s['kind'] == 'formal']
    assert len(formal) == 5
    assert [s['index'] for s in formal] == ([1, 2, 3, 4, 5] if mode == 'reload' else [3, 4, 5, 6, 7])
    assert len(samples) == (5 if mode == 'reload' else 7)
    if mode == 'reload':
        assert all('phase=COMMIT - no errors.' in s['rcon_response'] for s in formal)
    values = [s['marker_ms' if mode == 'reload' else 'wall_done_ms'] for s in formal]
    threshold = 285.3 if mode == 'reload' else 41173
    entries = []
    archive_path = destination / (folder.name + '-raw.zip')
    with ZipFile(archive_path, 'w', ZIP_DEFLATED) as archive:
        for path in sorted(folder.iterdir()):
            if not path.is_file():
                continue
            data = checked(path)
            archive.writestr(path.name, data)
            entries.append(dict(path=path.name, bytes=len(data), sha256=sha(data)))
    with ZipFile(archive_path) as archive:
        assert archive.testzip() is None
        for record in entries:
            assert sha(archive.read(record['path'])) == record['sha256']
    groups.append(dict(group=folder.name, mode=mode, values_ms=values, mean_ms=sum(values)/5, threshold_ms=threshold, measurementVerdict='PASS' if sum(values)/5 <= threshold else 'FAIL', samplerExitCode=None if index == 0 else 0, exitObservation='First wrapper returned null, not observed' if index == 0 else 'Synchronous LASTEXITCODE directly observed', warmups_ms=[s['wall_done_ms'] for s in samples if s['kind'] == 'warmup'], archive=dict(file=archive_path.name, sha256=sha(archive_path.read_bytes()), entries=entries)))
for path in work.iterdir():
    if path.is_file():
        (destination / path.name).write_bytes(checked(path))
for name in ['prepare-probe-performance.ps1', 'run-probe-performance.ps1', 'run-probe-performance-confirmed.ps1', 'archive-probe-performance.py', 'probe-performance-guard.txt', 'probe-performance-console.txt', 'probe-performance-confirmed-console.txt']:
    (destination / name).write_bytes(checked(repair / name))
for path in sorted(destination.iterdir()):
    if path.is_file():
        all_entries.append(dict(file=path.name, bytes=path.stat().st_size, sha256=sha(path.read_bytes())))
summary = dict(sourceRevision=revision, samplerSha256=sampler_sha, groups=groups, allFifteenReloadMeanMs=sum(sum(g['values_ms']) for g in groups if g['mode'] == 'reload')/15, combinedVerdict='NOT GREEN' if any(g['measurementVerdict'] != 'PASS' for g in groups) else 'PASS', firstOuterRunnerExitCode=1, confirmedOuterRunnerExitCode=0, noHistoricalPooling=True, noCausalSpeedupClaim=True, shutdown='All ten game sessions stopped via RCON without forced kill', files=all_entries)
(destination / 'performance-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
(destination / '.gitattributes').write_bytes(b'*.txt -text whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol\n*.ps1 -text\nfixture-hashes.json -text\n')
print(json.dumps(dict(source=revision, groups=[{k:v for k,v in g.items() if k != 'archive'} for g in groups], combinedVerdict=summary['combinedVerdict'])))
