import ast
import collections
import hashlib
import json
import re
from pathlib import Path

project = Path(__file__).resolve().parents[2]
work = Path(__file__).resolve().parent
root = project / 'build/ticket37-ts-installed-26.2'
old_root = project / 'build/ticket37-b7-installed-26.2'
ts = root / '.neko_probe/typescript'
previous_ts = old_root / '.neko_probe/typescript'
assert (ts / '@registry-builders/index.d.ts').read_bytes() == (previous_ts / '@registry-builders/index.d.ts').read_bytes()
assert (ts / '@manual/index.d.ts').read_bytes() == (previous_ts / '@manual/index.d.ts').read_bytes()
dependencies = (ts / '@event-builders/index.d.ts').read_text(encoding='utf-8')
assert re.findall(r'^interface (\w+) \{', dependencies, re.M) == ['DynamicItemBuilder', 'DynamicMobEffectBuilder', 'DynamicSoundEventBuilder']
server = (ts / '@side-only/server/events/index.d.ts').read_text(encoding='utf-8')
assert '../../../@event-builders/index.d.ts' in server and '../../../@registry-builders/' not in server

def diagnostics(path, prefix):
    return collections.Counter(re.sub(r'\(\d+,\d+\)(?=: error TS)', '', line.replace(prefix, '<owned-profile>')) for line in path.read_text(encoding='utf-8-sig').splitlines() if ': error TS' in line)

old = diagnostics(project / 'docs/architecture-refactor/evidence/ticket37-b7-candidate-validation/installed-26.2/ticket37-live-ts-strict-output.txt', 'build/ticket37-b7-installed-26.2')
new = diagnostics(work / 'live-strict-output.txt', 'build/ticket37-ts-installed-26.2')
removed = list((old - new).elements())
added = list((new - old).elements())
assert sum(old.values()) == 3116 and sum(new.values()) == 3109
assert len(removed) == 7 and not added, (removed, added)
assert all('/@registry-builders/index.d.ts' in line and 'error TS2717:' in line for line in removed)
result = {'comparison_normalization': 'Owned profile prefix and diagnostic line/column removed; file/code/message and duplicate counts preserved. Six existing diagnostics moved with declaration order.', 'old_diagnostics': sum(old.values()), 'new_diagnostics': sum(new.values()), 'removed': removed, 'added': added, 'full_strict_tsc_exit': 2, 'legacy_registry_builders_byte_identical': True, 'manual_byte_identical': True, 'event_builder_interfaces': re.findall(r'^interface (\w+) \{', dependencies, re.M)}
(work / 'live-diagnostic-delta.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result, indent=2))

audit_source = (project / 'build/ticket37-check-live-probe.py').read_text(encoding='utf-8')
audit_source = audit_source.replace('@registry-builders/index.d.ts', '@event-builders/index.d.ts')
namespace = {'__name__': '__main__'}
import sys
sys.argv = ['audit-installed.py', str(root)]
exec(compile(audit_source, 'updated-live-probe-audit', 'exec'), namespace)

old_python = project / 'build/ticket37-python-installed-26.2/.neko_probe/python'
python_root = root / '.neko_probe/python'
old_files = {file.relative_to(old_python).as_posix(): hashlib.sha256(file.read_bytes()).hexdigest() for file in old_python.rglob('*') if file.is_file()}
new_files = {file.relative_to(python_root).as_posix(): hashlib.sha256(file.read_bytes()).hexdigest() for file in python_root.rglob('*') if file.is_file()}
assert old_files.keys() == new_files.keys()
changed = [name for name in old_files if old_files[name] != new_files[name]]
print(json.dumps({'python_file_set_unchanged': True, 'python_files_with_different_bytes': changed}))
