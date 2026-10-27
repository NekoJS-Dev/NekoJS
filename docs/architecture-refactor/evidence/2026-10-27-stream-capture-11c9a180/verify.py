"""Verify frozen Stream capture evidence and optionally replay strict consumers."""

import argparse
from collections import Counter
from hashlib import sha256
import json
from pathlib import Path
import re
import subprocess
import tempfile
from zipfile import ZipFile
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--typecheck', action='store_true')
parser.add_argument('--installed-typecheck', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())


def blocks(data, entry='', positions=False):
    location = r'(.*?\(\d+,\d+\))' if positions else r'(.*?)\(\d+,\d+\)'
    header = re.compile(r'^.*?\.neko_probe/typescript/' + location + r': error (TS\d+): (.*)$')
    result = []
    for line in data.decode('utf-8').replace('\\', '/').splitlines():
        match = header.match(line)
        if match:
            result.append([(entry, *match.groups()), []])
        elif result:
            result[-1][1].append(line)
    return Counter((*key, '\n'.join(lines).rstrip()) for key, lines in result)


archive = root / manifest['archive']
assert sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']
with ZipFile(archive) as zipped:
    names = zipped.namelist()
    assert len(names) == len(set(names)) == len(manifest['entries'])
    for entry in manifest['entries']:
        assert sha256(zipped.read(entry['path'])).hexdigest() == entry['sha256'], entry['path']

    reports = [ET.fromstring(zipped.read(name)) for name in names if name.startswith('reports/tests/')]
    counts = {key: sum(int(report.get(key, 0)) for report in reports)
              for key in ['tests', 'skipped', 'failures', 'errors']}
    counts['suites'] = len(reports)
    assert counts == manifest['matrixTest'] == dict(tests=5206, skipped=271, failures=0, errors=0, suites=922)
    common = [ET.fromstring(zipped.read(name)) for name in names if name.startswith('reports/tests/common/')]
    assert len(common) == 340 and sum(int(report.get('tests', 0)) for report in common) == 2489
    assert sum(int(report.get('skipped', 0)) for report in common) == 4
    gates = json.loads(zipped.read('reports/stream-capture-ci-gates.json'))['gates']
    assert len(gates) == 4 and all(gate['state'] == 'pass' and not gate['failures'] for gate in gates)
    raw = zipped.read('logs/stream-capture-final-platform-matrix.log')
    log = raw.decode('utf-16') if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else raw.decode('utf-8-sig')
    assert 'BUILD SUCCESSFUL' in log and '> Task :common:check' in log and 'guardLint:' in log
    assert int(ET.fromstring(zipped.read('reports/stream-capture-container-red.xml')).get('failures')) == 1
    red = json.loads(zipped.read('reports/stream-capture-orphan-red.json'))
    green = json.loads(zipped.read('reports/stream-capture-orphan-green.json'))
    assert red['orphanPresent'] == [True, True] and red['exitCode'] == 0
    assert green['orphanPresent'] == [False, False] and green['exitCode'] == 2 and green['codes'] == {'TS2307': 1}

    runs = json.loads(zipped.read('installed/run-summary.json'))
    assert len(runs) == 5 and all(run['exitCode'] == 0 and run['stopSent'] and not run['killed']
                                  and run['sessionLockReleased'] for run in runs)
    classification = json.loads(zipped.read('installed/root-cause-mapping.json'))
    baseline = json.loads(zipped.read('baseline/root-cause-mapping.json'))
    assert classification['total'] == sum(family['total'] for family in classification['families'].values()) == 997
    assert classification['families'].get('UNMAPPED', {}).get('total', 0) == 0
    for name, family in classification['families'].items():
        if name.startswith('F8-'):
            assert family['total'] == 0 and baseline['families'][name]['total'] == 32
        else:
            assert family == baseline['families'][name], name
    before, after = Counter(), Counter()
    baseline_names = [name for name in names if name.startswith('baseline/ide/')]
    assert len(baseline_names) == 10
    for name in baseline_names:
        entry = Path(name).name
        before.update(blocks(zipped.read(name), entry))
        after.update(blocks(zipped.read('installed/ide/' + entry), entry))
    delta = json.loads(zipped.read('installed/diagnostic-delta.json'))
    assert sum(before.values()) == delta['before'] == 1029
    assert sum(after.values()) == delta['after'] == 997
    assert sum((before-after).values()) == delta['fullBlocksRemoved'] == 32
    assert sum((after-before).values()) == delta['fullBlocksAdded'] == 0
    binding = json.loads(zipped.read('installed/source-artifact-binding.json'))
    assert binding['revision'] == manifest['sourceRevision'] == '11c9a180668800bdea856dd3cd7ef89152022075'
    for name, digest in binding['sources'].items():
        assert sha256(zipped.read('source/' + name).replace(b'\r\n', b'\n')).hexdigest() == digest, name
    for name, digest in binding['compiledClasses'].items():
        assert sha256(zipped.read('compiled/common/' + name)).hexdigest() == digest, name
    preparation = json.loads(zipped.read('installed/preparation-manifest-trimmed.json'))
    for node in binding['nodes']:
        prepared = next(item for item in preparation['nodes'] if item['node'] == node['node'])
        artifact = next(mod for mod in prepared['mods'] if mod['file'] == node['jar'])
        assert node['allChangedClassFamiliesMatch'] and artifact['sha256'] == node['sha256']

    if args.typecheck or args.installed_typecheck:
        repo = root.parents[3]
        compiler = repo / 'node_modules/typescript/bin/tsc'
        assert subprocess.check_output(['node', str(compiler), '--version'], text=True).strip() == 'Version 5.8.3'
        target = Path(tempfile.mkdtemp(prefix='stream-capture-replay-', dir=repo / 'build')).resolve()

        def extract(prefix, destination):
            for name in names:
                if not name.startswith(prefix):
                    continue
                path = (destination / name[len(prefix):]).resolve()
                assert path.is_relative_to(target)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(zipped.read(name))

        for variant in manifest['variants'] if args.typecheck else []:
            name = variant['name']
            extract('inputs/' + name + '/', target / name)
            result = subprocess.run(['node', str(compiler), '-p', str(target / name / 'tsconfig.json')], capture_output=True, timeout=120)
            codes = dict(Counter(re.findall(r': error (TS\d+):', result.stdout.decode())))
            assert result.returncode == variant['exitCode'] and not result.stderr and codes == variant['errorCodes'], (name, result.stdout, result.stderr)
            print('REPLAY:', name, 'exit', result.returncode, codes, flush=True)
        if args.installed_typecheck:
            extract('installed/inputs/', target / 'installed')
            summary = json.loads(zipped.read('installed/ide-check-summary.json'))
            assert summary['typescriptVersion'] == 'Version 5.8.3' and summary['skipLibCheck'] is False and summary['noEmit'] is True
            total = 0
            for node in summary['nodes']:
                for entry in node['typescript']:
                    stem = node['node'] + '-' + entry['side']
                    config = json.loads(zipped.read('installed/ide/' + stem + '-tsconfig.json'))
                    config['extends'] = str(target / 'installed' / node['node'] / 'nekojs' / (entry['side'] + '_scripts') / 'jsconfig.json')
                    relocated = target / (stem + '.json')
                    relocated.write_text(json.dumps(config))
                    result = subprocess.run(['node', str(compiler), '-p', str(relocated)], capture_output=True, timeout=180)
                    expected = zipped.read('installed/ide/' + stem + '-tsc.txt')
                    actual = blocks(result.stdout, positions=True)
                    assert result.returncode == entry['exitCode'] == 2 and not result.stderr, (stem, result.stdout, result.stderr)
                    assert actual == blocks(expected, positions=True) and sum(actual.values()) == entry['diagnosticCount'], stem
                    total += entry['diagnosticCount']
                    print('INSTALLED REPLAY:', stem, 'exit 2 diagnostics', entry['diagnosticCount'], flush=True)
            assert total == 997
        print('Artifacts:', target)
print('PASS: frozen Stream capture evidence; full strict TS remains FAIL (997)')
