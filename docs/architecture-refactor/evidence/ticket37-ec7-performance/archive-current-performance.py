import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path

project = Path.cwd()
owned = project / 'build/ticket37-perf-owned-ac810fa7'
work = project / 'build/ticket37-perf-ec7fe684'
repair = project / 'build/ticket37-config-sync-repair'
destination = project / 'docs/architecture-refactor/evidence/ticket37-ec7-performance'
revision = 'ec7fe6848afc1970661ee1f38a7c7c6d4575530c'
sampler_hash = '1F9C5D14A1A586DA4B3F1CA8E71B2407CE089EE8BEE185418963E1B5745DF0B4'

def digest(data):
    return hashlib.sha256(data).hexdigest()

def write_json(path, value):
    path.write_bytes((json.dumps(value, indent=2) + '\n').encode())

assert subprocess.check_output(['git', '-C', str(owned), 'rev-parse', 'HEAD'], text=True).strip() == revision
assert not subprocess.check_output(['git', '-C', str(owned), 'status', '--porcelain'], text=True).strip()
assert digest((owned / 'bench/perf/sample.ps1').read_bytes()).upper() == sampler_hash
console = (repair / 'current-performance-console.txt').read_text(encoding='utf-8-sig')
for label in ['reload-1', 'reload-2', 'startup']:
    assert console.count(f'FORMAL_GROUP={label} EXIT=0') == 1
passwords = []
properties = owned / 'versions/26.1.2/run/server.properties'
for line in properties.read_text(encoding='utf-8').splitlines():
    if line.startswith('rcon.password=') and line.partition('=')[2]:
        passwords.append(line.partition('=')[2].encode())

def checked_bytes(path):
    data = path.read_bytes()
    assert not re.search(rb'(?m)^rcon\.password=', data), path.name
    # The public benchmark cache directory has the same suffix as its fixture password.
    # Only this exact directory token is excluded from credential-value matching.
    scanned = data.replace(b'.gradle-perf02', b'[owned-gradle-cache]')
    assert not any(password in scanned for password in passwords), path.name
    return data

assert not (destination / 'performance-summary.json').exists(), 'Preserve completed evidence'
destination.mkdir(exist_ok=True)
groups = []
for stamp, mode, label in [
    ('20261008T170421Z', 'reload', 'reload-1'),
    ('20261008T170503Z', 'reload', 'reload-2'),
    ('20261008T170539Z', 'startup', 'startup'),
]:
    folder = owned / f'bench/perf/out/{stamp}-{mode}-26.1.2'
    samples = [json.loads(line) for line in (folder / 'samples.jsonl').read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    assert all(s['rev'] == revision[:8] and s['node'] == '26.1.2' and not s['forced_kill'] and not s['killed'] and s['stop_channel'] == 'rcon' for s in samples)
    formal = [s for s in samples if s['kind'] == 'formal']
    assert len(formal) == 5
    assert [s['index'] for s in formal] == ([1, 2, 3, 4, 5] if mode == 'reload' else [3, 4, 5, 6, 7])
    if mode == 'startup':
        assert len(samples) == 7 and len([s for s in samples if s['kind'] == 'warmup']) == 2
    else:
        assert len(samples) == 5 and all('phase=COMMIT - no errors.' in s['rcon_response'] for s in formal)
    values = [s['marker_ms' if mode == 'reload' else 'wall_done_ms'] for s in formal]
    threshold = 285.3 if mode == 'reload' else 41173
    mean = sum(values) / 5
    entries = [(path, path.name) for path in sorted(folder.iterdir()) if path.is_file()]
    entry_records = []
    with zipfile.ZipFile(destination / (label + '-raw.zip'), 'w', zipfile.ZIP_DEFLATED) as archive:
        for path, name in entries:
            data = checked_bytes(path)
            archive.writestr(name, data)
            entry_records.append(dict(path=name, size=len(data), sha256=digest(data)))
    with zipfile.ZipFile(destination / (label + '-raw.zip')) as archive:
        assert archive.testzip() is None
        for path, name in entries:
            assert archive.read(name) == path.read_bytes()
    (destination / (label + '-samples.jsonl')).write_bytes(checked_bytes(folder / 'samples.jsonl'))
    groups.append(dict(group=folder.name, mode=mode, values_ms=values, mean_ms=mean, threshold_ms=threshold, verdict='PASS' if mean <= threshold else 'FAIL', warmups_ms=[s['wall_done_ms'] for s in samples if s['kind'] == 'warmup'], sampler_exit_code=0, archive_entries=entry_records))

for path in sorted(work.iterdir()):
    assert path.is_file()
    (destination / path.name).write_bytes(checked_bytes(path))
for name in ['prepare-current-performance.ps1', 'run-current-performance.ps1', 'performance-guard.txt', 'current-performance-console.txt', 'archive-current-performance.py']:
    (destination / name).write_bytes(checked_bytes(repair / name))
# Bind the original sampler by source revision/hash; its public fixture credential
# default is intentionally not duplicated into this evidence package.
fixture_records = json.loads((work / 'fixture-hashes.json').read_text(encoding='utf-8-sig'))
for record in fixture_records:
    path = owned / record['path']
    assert digest(path.read_bytes()).upper() == record['sha256']

summary = dict(source_revision=revision, sampler_sha256=sampler_hash, groups=groups, all_ten_reload_mean_ms=sum(sum(group['values_ms']) for group in groups if group['mode'] == 'reload') / 10, combined_verdict='PASS' if all(group['verdict'] == 'PASS' for group in groups) else 'NOT GREEN', outer_runner_exit_code=0, game_shutdown='All nine sessions used normal RCON; no forced kill', comparison_scope='Exact ec7 runtime only; historical revisions and diagnostic JFR are not pooled', causal_speedup_claim=False)
write_json(destination / 'performance-summary.json', summary)
(destination / '.gitattributes').write_bytes(b'*.txt -text whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol\n*.jsonl -text whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol\nfixture-hashes.json -text\n*.ps1 -text\n')
print(json.dumps({key: value for key, value in summary.items() if key != 'groups'}, indent=2))
for group in groups:
    print(group['group'], group['values_ms'], group['mean_ms'], group['verdict'])
