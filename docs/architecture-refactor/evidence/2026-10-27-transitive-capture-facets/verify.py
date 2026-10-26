"""Verify the frozen complete-capture sources, test reports, and strict consumers."""

import argparse
from collections import Counter
from hashlib import sha256
from io import BytesIO
import json
import re
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZipFile
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--typecheck', action='store_true')
parser.add_argument('--installed-typecheck', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
def identities(name, data):
    header = re.compile(r'^.*?\.neko_probe/typescript/(.*?)\(\d+,\d+\): error (TS\d+): (.*)$')
    blocks = []
    for line in data.decode('utf-8').replace('\\', '/').splitlines():
        match = header.match(line)
        if match:
            blocks.append([(name.removesuffix('-tsc.txt'), *match.groups()), []])
        elif blocks:
            blocks[-1][1].append(line)
    return Counter((*key, '\n'.join(lines).rstrip()) for key, lines in blocks)

archive = root / manifest['archive']
assert sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']
with ZipFile(archive) as zipped:
    assert len(zipped.namelist()) == len(set(zipped.namelist())) == len(manifest['entries'])
    for entry in manifest['entries']:
        assert sha256(zipped.read(entry['path'])).hexdigest() == entry['sha256'], entry['path']
    reports = [ET.fromstring(zipped.read(name)) for name in zipped.namelist()
               if name.startswith('reports/common-test/') and name.endswith('.xml')]
    counts = {key: sum(int(report.get(key, 0)) for report in reports)
              for key in ['tests', 'skipped', 'failures', 'errors']}
    counts['suites'] = len(reports)
    assert counts == manifest['commonTest'] == dict(tests=2483, skipped=4, failures=0, errors=0, suites=339)
    matrix = reports + [ET.fromstring(zipped.read(name)) for name in zipped.namelist()
                        if name.startswith('reports/matrix/') and name.endswith('.xml')]
    matrix_counts = {key: sum(int(report.get(key, 0)) for report in matrix)
                     for key in ['tests', 'skipped', 'failures', 'errors']}
    matrix_counts['suites'] = len(matrix)
    assert matrix_counts == manifest['matrixTest'] == dict(tests=5200, skipped=271, failures=0, errors=0, suites=921)
    gates = json.loads(zipped.read('reports/complete-capture-transitive-ci-gates.json'))['gates']
    assert len(gates) == 4 and all(gate['state'] == 'pass' and not gate['failures'] for gate in gates)
    if 'installed/source-artifact-binding.json' in zipped.namelist():
        runs = json.loads(zipped.read('installed/run-summary.json'))
        assert len(runs) == 5 and all(run['exitCode'] == 0 and run['stopSent']
                                      and not run['killed'] and run['sessionLockReleased'] for run in runs)
        classification = json.loads(zipped.read('installed/root-cause-mapping.json'))
        assert classification['total'] == sum(family['total'] for family in classification['families'].values())
        assert classification['families'].get('UNMAPPED', {}).get('total', 0) == 0
        baseline_file = root / manifest['baselineArchive']
        assert sha256(baseline_file.read_bytes()).hexdigest() == manifest['baselineArchiveSha256']
        before, after = Counter(), Counter()
        with ZipFile(baseline_file) as baseline:
            assert len(baseline.namelist()) == 10
            for name in baseline.namelist():
                before.update(identities(name, baseline.read(name)))
                after.update(identities(name, zipped.read('installed/ide/' + name)))
        delta = json.loads(zipped.read('installed/diagnostic-delta.json'))
        assert sum(before.values()) == delta['before'] and sum(after.values()) == delta['after'] == classification['total']
        assert sum((before-after).values()) == delta['fullBlocksRemoved']
        assert sum((after-before).values()) == delta['fullBlocksAdded']
        binding = json.loads(zipped.read('installed/source-artifact-binding.json'))
        assert binding['revision'] == manifest['sourceRevision']
        for name, digest in binding['sources'].items():
            assert sha256(zipped.read('source/' + name).replace(b'\r\n', b'\n')).hexdigest() == digest, name
        for name, digest in binding['compiledClasses'].items():
            assert sha256(zipped.read('compiled/common/' + name)).hexdigest() == digest, name
        preparation = json.loads(zipped.read('installed/preparation-manifest-trimmed.json'))
        for node in binding['nodes']:
            prepared = next(entry for entry in preparation['nodes'] if entry['node'] == node['node'])
            artifact = next(mod for mod in prepared['mods'] if mod['file'] == node['jar'])
            assert node['allChangedClassFamiliesMatch'] and artifact['sha256'] == node['sha256']
    raw = zipped.read('logs/complete-capture-transitive-matrix.log')
    log = raw.decode('utf-16') if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else raw.decode('utf-8-sig')
    assert 'BUILD SUCCESSFUL' in log and '> Task :common:check' in log and 'guardLint:' in log
    with ZipFile(BytesIO(zipped.read('reports/complete-capture-first-common-results.zip'))) as failed:
        failures = [ET.fromstring(failed.read(name)) for name in failed.namelist() if name.endswith('.xml')]
        assert sum(int(report.get('failures', 0)) for report in failures) == 3
        assert any('PackSyncClientTest' in report.get('name', '') and int(report.get('failures', 0)) == 1
                   for report in failures)
    delta = json.loads(zipped.read('reports/complete-capture-golden-delta.json'))
    assert len(delta) == 2
    with ZipFile(BytesIO(zipped.read('reports/complete-capture-goldens-before.zip'))) as before:
        for entry in delta:
            path = entry['file']
            old = before.read(path)
            new = zipped.read('source/' + path)
            assert sha256(old).hexdigest() == entry['before']
            assert sha256(new).hexdigest() == entry['after']
            marker = '    export interface $StreamView' if '/stream/' in path else '    export interface $OptionalView'
            old_prefix = old.decode().split(marker)[0]
            new_prefix = new.decode().split('    type $NekoCaptureCollectionInput')[0]
            native = lambda value: '\n'.join(line for line in value.splitlines() if not line.startswith('import '))
            assert native(old_prefix) == native(new_prefix)
    if args.typecheck or args.installed_typecheck:
        repo = root.parents[3]
        compiler = repo / 'node_modules/typescript/bin/tsc'
        assert subprocess.check_output(['node', str(compiler), '--version'], text=True).strip() == 'Version 5.8.3'
        target = Path(tempfile.mkdtemp(prefix='complete-capture-replay-', dir=repo / 'build')).resolve()
        for variant in manifest['variants'] if args.typecheck else []:
            prefix = 'inputs/' + variant + '/'
            for name in zipped.namelist():
                if not name.startswith(prefix):
                    continue
                destination = (target / variant / name[len(prefix):]).resolve()
                assert destination.is_relative_to(target)
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(zipped.read(name))
            result = subprocess.run(['node', str(compiler), '-p', str(target / variant / 'tsconfig.json')],
                                    capture_output=True, timeout=120)
            assert result.returncode == 0 and not result.stdout and not result.stderr, (variant, result.stdout, result.stderr)
            print('REPLAY:', variant, 'exit 0')
        if args.installed_typecheck:
            summary = json.loads(zipped.read('installed/ide-check-summary.json'))
            assert summary['typescriptVersion'] == 'Version 5.8.3'
            assert summary['skipLibCheck'] is False and summary['noEmit'] is True
            for name in zipped.namelist():
                prefix = 'installed/inputs/'
                if not name.startswith(prefix):
                    continue
                destination = (target / 'installed' / name[len(prefix):]).resolve()
                assert destination.is_relative_to(target)
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(zipped.read(name))
            header = re.compile(r'^.*?\.neko_probe/typescript/(.*?\(\d+,\d+\): error TS\d+: .*)$')
            def diagnostic_blocks(text):
                blocks = []
                for line in text.splitlines():
                    match = header.match(line)
                    if match:
                        blocks.append(match[1])
                    elif blocks:
                        blocks[-1] += '\n' + line
                return Counter(block.rstrip() for block in blocks)
            total = 0
            for node in summary['nodes']:
                for entry in node['typescript']:
                    stem = node['node'] + '-' + entry['side']
                    config = json.loads(zipped.read('installed/ide/' + stem + '-tsconfig.json'))
                    config['extends'] = str(target / 'installed' / node['node'] / 'nekojs' / (entry['side'] + '_scripts') / 'jsconfig.json')
                    relocated = target / (stem + '.json')
                    relocated.write_text(json.dumps(config), encoding='utf-8')
                    result = subprocess.run(['node', str(compiler), '-p', str(relocated)], capture_output=True, timeout=180)
                    text = result.stdout.decode('utf-8').replace('\\', '/')
                    expected = zipped.read('installed/ide/' + stem + '-tsc.txt').decode('utf-8').replace('\\', '/')
                    assert result.returncode == entry['exitCode'] and not result.stderr, (stem, result.stdout, result.stderr)
                    actual_blocks, expected_blocks = diagnostic_blocks(text), diagnostic_blocks(expected)
                    assert actual_blocks == expected_blocks and sum(actual_blocks.values()) == entry['diagnosticCount'], stem
                    total += entry['diagnosticCount']
                    print('INSTALLED REPLAY:', stem, 'exit', result.returncode, 'diagnostics', entry['diagnosticCount'])
            print('Installed diagnostic total:', total, '(expected strict TS failure retained)')
        print('Artifacts:', target)
print('PASS: frozen complete-capture evidence; installed-node and final-candidate gates remain separate')
