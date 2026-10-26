"""Recompute the frozen matrix and raw/source-context diagnostic deltas without launching games."""

from collections import Counter
import argparse
from hashlib import sha256
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile
import subprocess
import tempfile


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--typecheck', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
archive = root / manifest['archive']
assert sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']
for name, digest in manifest['dataFiles'].items():
    assert sha256((root / name).read_bytes()).hexdigest() == digest, name

with ZipFile(archive) as zipped:
    assert len(zipped.namelist()) == len(set(zipped.namelist())) == len(manifest['entries'])
    for entry in manifest['entries']:
        assert sha256(zipped.read(entry['path'])).hexdigest() == entry['sha256'], entry['path']
    assert (root / 'compare_diagnostics.py').read_bytes() == zipped.read('tools/compare_diagnostics.py')
    recorded_binding = json.loads((root / 'source-artifact-binding.json').read_text(encoding='utf-8'))
    for name, digest in recorded_binding['sources'].items():
        assert sha256(zipped.read('source/' + name)).hexdigest() == digest, name
    for name, digest in recorded_binding['compiledClasses'].items():
        assert sha256(zipped.read('binding/' + name)).hexdigest() == digest, name
    from compare_diagnostics import parse

    def diagnostics(prefix):
        logs = {Path(name).name: zipped.read(name).decode('utf-8') for name in zipped.namelist()
                if name.startswith(prefix + '/ide/') and name.endswith('-tsc.txt')}
        assert len(logs) == 10

        def source(file):
            location, relative = file.replace('\\', '/').split('/.neko_probe/typescript/', 1)
            node = location.rsplit('/', 1)[-1]
            return zipped.read(prefix + '/sources/' + node + '/' + relative).decode('utf-8').splitlines()

        return parse(logs, source)

    before, before_semantic, before_entries, before_contexts = diagnostics('baseline')
    after, after_semantic, after_entries, after_contexts = diagnostics('current')
    assert sum(before.values()) == 1079
    assert sum(after.values()) == 1029
    assert sum((before - after).values()) == 50
    assert sum((after - before).values()) == 0
    assert sum((before_semantic - after_semantic).values()) == 50
    assert not after_semantic - before_semantic
    delta = json.loads((root / 'diagnostic-delta.json').read_text(encoding='utf-8'))
    assert delta['rawRemoved'] == 50 and delta['rawAdded'] == 0
    assert delta['semanticRemoved'] == 50 and delta['semanticAdded'] == 0
    assert delta['beforeMissingContexts'] == before_contexts
    assert delta['afterMissingContexts'] == after_contexts
    assert delta['beforeEntrypoints'] == dict(before_entries)
    assert delta['afterEntrypoints'] == dict(after_entries)
    counts = Counter()
    suites = 0
    for name in zipped.namelist():
        if name.startswith('matrix/') and name.endswith('.xml'):
            suite = ET.fromstring(zipped.read(name))
            suites += 1
            counts.update({key: int(suite.get(key, '0'))
                           for key in ('tests', 'failures', 'errors', 'skipped')})
    assert suites == manifest['matrixSuites'] == 919
    assert counts == manifest['matrix'] == Counter(tests=5193, failures=0, errors=0, skipped=271)
    mapping = json.loads(zipped.read('current/root-cause-mapping.json'))
    assert mapping['total'] == 1029 == sum(f['total'] for f in mapping['families'].values())
    assert all(d['family'] != 'UNMAPPED' for d in mapping['diagnostics'])
    previous = json.loads((root / 'baseline-root-cause-summary.json').read_text(encoding='utf-8'))
    for family, data in mapping['families'].items():
        if family == 'F1-constable-optional':
            assert previous['families'][family]['total'] == 50 and data['total'] == 0
        else:
            assert data == previous['families'][family], family
    if args.typecheck:
        repo = root.parents[3]
        compiler = repo / 'node_modules/typescript/bin/tsc'
        assert subprocess.check_output(['node', str(compiler), '--version'], text=True).strip() == 'Version 5.8.3'
        consumer = Path(tempfile.mkdtemp(prefix='constable-value-replay-', dir=repo / 'build')).resolve()
        for name in zipped.namelist():
            if not name.startswith('consumer/'):
                continue
            target = (consumer / name.removeprefix('consumer/')).resolve()
            assert target.is_relative_to(consumer)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(zipped.read(name))
        for mode in ['', 'edited', 'adapted']:
            result = subprocess.run(['node', str(compiler), '-p', str(consumer / mode / 'tsconfig.json')],
                                    capture_output=True, text=True)
            assert result.returncode == 0, result.stdout + result.stderr
        print('PASS: three frozen positive/negative strict consumers; artifacts:', consumer)

runs = json.loads((root / 'run-summary.json').read_text(encoding='utf-8'))
assert len(runs) == 5
assert all(r['exitCode'] == 0 and not r['killed'] and r['stopSent'] and r['sessionLockReleased'] for r in runs)
binding = json.loads((root / 'source-artifact-binding.json').read_text(encoding='utf-8'))
assert binding['revision'].startswith('3d2cc1cb')
assert len(binding['nodes']) == 5 and all(n['allChangedClassFamiliesMatch'] for n in binding['nodes'])
summary = json.loads((root / 'ide-check-summary.json').read_text(encoding='utf-8'))
assert all(entry['exitCode'] == 2 and entry['diagnosticCount'] == after_entries[node['node'] + '-' + entry['side']]
           for node in summary['nodes'] for entry in node['typescript'])
print('PASS: five installed runs, 5193 matrix tests; 1079 -> 1029, raw/source-context -50/+0; strict TS and full capture API remain open')
