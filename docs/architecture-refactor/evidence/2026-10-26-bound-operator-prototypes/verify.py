"""Check failed capture experiments and optionally replay their exact strict TypeScript verdicts."""

import argparse
from collections import Counter
from hashlib import sha256
import json
from pathlib import Path
import re
import subprocess
import tempfile
from zipfile import ZipFile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--typecheck', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
assert manifest['production'] is False
archive = root / manifest['archive']
assert sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']
expected = {'operators': {'TS2416': 6, 'TS2322': 1, 'TS2345': 1},
            'supplier': {'TS2416': 6, 'TS2322': 1, 'TS2345': 2}}
with ZipFile(archive) as zipped:
    assert len(zipped.namelist()) == len(set(zipped.namelist())) == len(manifest['entries'])
    for entry in manifest['entries']:
        assert sha256(zipped.read(entry['path'])).hexdigest() == entry['sha256'], entry['path']
    source = zipped.read('source/functional-module-3d2cc1cb.d.ts')
    assert b'identity<T>(): $UnaryOperator<T>;' in source and b'apply: <C extends T>' not in source
    for variant in manifest['variants']:
        name = variant['name']
        assert variant['exitCode'] == 2 and variant['errorCodes'] == expected[name]
        text = zipped.read('logs/' + name + '/tsc.txt').decode('utf-8')
        assert Counter(re.findall(r': error (TS\d+):', text)) == expected[name]
        assert not zipped.read('logs/' + name + '/tsc-stderr.txt')
        assert zipped.read('inputs/' + name + '/@package/java/util/function/index.d.ts') == source
    if args.typecheck:
        repo = root.parents[3]
        compiler = repo / 'node_modules/typescript/bin/tsc'
        assert subprocess.check_output(['node', str(compiler), '--version'], text=True).strip() == 'Version 5.8.3'
        target = Path(tempfile.mkdtemp(prefix='bound-operator-replay-', dir=repo / 'build')).resolve()
        for variant in manifest['variants']:
            name = variant['name']
            for relative in variant['files']:
                path = (target / name / relative).resolve()
                assert path.is_relative_to(target)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(zipped.read('inputs/' + name + '/' + relative))
            result = subprocess.run(['node', str(compiler), '-p', str(target / name / 'tsconfig.json')],
                                    capture_output=True, timeout=120)
            assert result.returncode == 2 and not result.stderr
            codes = Counter(re.findall(r': error (TS\d+):', result.stdout.decode('utf-8')))
            assert codes == expected[name], (name, result.stdout.decode('utf-8'))
            (target / name / 'replayed-tsc.txt').write_bytes(result.stdout)
            print('REPLAY:', name, 'expected FAIL reproduced,', sum(codes.values()), 'diagnostics')
        print('Artifacts:', target)
print('PASS: evidence integrity; both semantic candidates remain FAIL, not production acceptance')
