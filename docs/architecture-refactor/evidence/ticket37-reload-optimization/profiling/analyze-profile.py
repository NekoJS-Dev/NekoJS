import collections
import json
import pathlib
import re

profile_dir = pathlib.Path(__file__).parent / 'ticket37-reload-profile'
blocks = (profile_dir / 'cpu-samples.txt').read_text(encoding='utf-8').split('\n}\n')
server_samples = []
reload_samples = []
for block in blocks:
    if 'sampledThread = "Server thread"' not in block:
        continue
    frames = re.findall(r'^    ([^\n]+)', block, re.MULTILINE)
    timestamp = re.search(r'startTime = ([^\n]+)', block)
    entry = {'type': block.split(' {', 1)[0].strip(), 'time': timestamp.group(1) if timestamp else None, 'frames': frames}
    server_samples.append(entry)
    if any('ScriptManager.reloadScripts' in frame or 'reloadScriptsTransactional' in frame for frame in frames):
        reload_samples.append(entry)

leaf_counts = collections.Counter(entry['frames'][0] for entry in reload_samples if entry['frames'])
neko_counts = collections.Counter()
path_counts = collections.Counter()
for entry in reload_samples:
    seen = set()
    for frame in entry['frames']:
        if frame.startswith('com.tkisor.nekojs.'):
            signature = frame.split(' line:', 1)[0]
            if signature not in seen:
                neko_counts[signature] += 1
                seen.add(signature)
    path = next((frame.split(' line:', 1)[0] for frame in entry['frames'] if frame.startswith('com.tkisor.nekojs.')), 'no-visible-neko-frame')
    path_counts[path] += 1

park_events = json.loads((profile_dir / 'server-only.json').read_text(encoding='utf-8'))['recording']['events']
park_summary = collections.Counter()
reload_parks = []
for event in park_events:
    values = event['values']
    frames = ((values.get('stackTrace') or {}).get('frames') or [])
    methods = [frame['method']['type']['name'].replace('/', '.') + '.' + frame['method']['name'] for frame in frames]
    duration = values.get('duration')
    top = methods[0] if methods else 'no-stack'
    park_summary[top] += 1
    if any('ScriptManager' in method for method in methods):
        reload_parks.append({'start': values['startTime'], 'duration': duration, 'methods': methods})

summary = {
    'diagnostic_only': True,
    'server_sample_count': len(server_samples),
    'reload_path_sample_count': len(reload_samples),
    'reload_leaf_samples': leaf_counts.most_common(20),
    'nearest_neko_frames': path_counts.most_common(20),
    'neko_inclusive_sample_counts': neko_counts.most_common(25),
    'server_park_count': len(park_events),
    'server_park_top_frames': park_summary.most_common(10),
    'reload_path_parks': reload_parks[:20],
    'sample_examples': [entry for entry in reload_samples[:3]],
    'limitations': 'Sample counts are not exact milliseconds; native samples and parked durations are not CPU utilization. The JFR thread scrub does not retain sampledThread events, so sampled stacks are filtered from the safe full CPU text instead.'
}
output = profile_dir / 'analysis.json'
output.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({key: value for key, value in summary.items() if key not in {'sample_examples', 'reload_path_parks'}}, ensure_ascii=False, indent=2))
