"""Verify committed capture-return evidence and optionally replay its exact strict consumers."""

import argparse
from collections import Counter
from hashlib import sha256
from io import BytesIO
import json
from pathlib import Path
import re
import subprocess
import tempfile
from zipfile import ZipFile
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--typecheck', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
archive = root / manifest['archive']
assert sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']
expected = {'generated': {}, 'without-facets': {'TS2345': 5}}
with ZipFile(archive) as zipped:
    assert len(zipped.namelist()) == len(set(zipped.namelist())) == len(manifest['entries'])
    for entry in manifest['entries']:
        assert sha256(zipped.read(entry['path'])).hexdigest() == entry['sha256'], entry['path']
    tests = [ET.fromstring(zipped.read(path)) for path in zipped.namelist()
             if path.startswith('reports/common-test/') and path.endswith('.xml')]
    counts = {key: sum(int(test.attrib.get(key, 0)) for test in tests)
              for key in ('tests', 'skipped', 'failures', 'errors')}
    counts['suites'] = len(tests)
    assert counts == manifest['commonTest'] == {
        'tests': 2478, 'skipped': 4, 'failures': 0, 'errors': 0, 'suites': 338}
    raw = zipped.read('logs/capture-return-contract-release-common-check.log')
    log = raw.decode('utf-16') if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else raw.decode('utf-8-sig')
    assert 'BUILD SUCCESSFUL' in log and '> Task :common:check' in log and 'guardLint:' in log
    gates = json.loads(zipped.read('reports/ci-gates.json'))['gates']
    assert len(gates) == 4 and all(gate['state'] == 'pass' and not gate['failures'] for gate in gates)
    assert manifest['ciGates']['nodeReportUsesExistingArtifacts'] is True
    delta = json.loads(zipped.read('reports/golden-delta.json'))
    changes = imports = 0
    with ZipFile(BytesIO(zipped.read('reports/goldens-before.zip'))) as before:
        for path in delta['changedFiles']:
            old = before.read(path).decode('utf-8').splitlines()
            new = zipped.read('source/' + path).decode('utf-8').splitlines()
            assert len(old) == len(new)
            for left, right in zip(old, new):
                if left == right:
                    continue
                if left.startswith('import '):
                    assert right.startswith('import ') and left.split(' from ')[1] == right.split(' from ')[1]
                    names = lambda line: set(line[line.index('{') + 1:line.index('}')].strip().split(', '))
                    assert names(left) <= names(right)
                    assert names(right) - names(left) <= {'$OptionalView', '$ConstantDesc', '$ConstantDesc_'}
                    imports += 1
                else:
                    assert re.match(r'\s+describeConstable\(\): \$Optional<', left)
                    assert right == left[:-1] + ' & $OptionalView<(number | string | $ConstantDesc)>;'
                    changes += 1
    assert (len(delta['changedFiles']), changes, imports) == (2, 10, 3)
    for variant in manifest['variants']:
        name = variant['name']
        assert variant['errorCodes'] == expected[name]
        assert variant['exitCode'] == (2 if expected[name] else 0)
        text = zipped.read('logs/' + name + '/tsc.txt').decode('utf-8')
        assert Counter(re.findall(r': error (TS\d+):', text)) == expected[name]
        assert not zipped.read('logs/' + name + '/tsc-stderr.txt')
    if args.typecheck:
        repo = root.parents[3]
        compiler = repo / 'node_modules/typescript/bin/tsc'
        assert subprocess.check_output(['node', str(compiler), '--version'], text=True).strip() == 'Version 5.8.3'
        target = Path(tempfile.mkdtemp(prefix='capture-return-replay-', dir=repo / 'build')).resolve()
        for variant in manifest['variants']:
            name = variant['name']
            for relative in variant['files']:
                path = (target / name / relative).resolve()
                assert path.is_relative_to(target)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(zipped.read('inputs/' + name + '/' + relative))
            result = subprocess.run(['node', str(compiler), '-p', str(target / name / 'tsconfig.json')],
                                    capture_output=True, timeout=120)
            codes = Counter(re.findall(r': error (TS\d+):', result.stdout.decode('utf-8')))
            assert result.returncode == variant['exitCode'] and not result.stderr
            assert codes == expected[name], (name, result.stdout.decode('utf-8'))
            (target / name / 'replayed-tsc.txt').write_bytes(result.stdout)
            print('REPLAY:', name, 'exit', result.returncode, dict(codes))
        print('Artifacts:', target)
print('PASS: capture-return evidence; complete capture views and installed-node acceptance remain open')
