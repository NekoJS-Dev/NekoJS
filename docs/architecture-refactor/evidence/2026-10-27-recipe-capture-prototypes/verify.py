"""Replay frozen failing Recipe capture experiments without changing production declarations."""
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
parser.add_argument('--java', action='store_true')
parser.add_argument('--dfu-jar', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parent
repo = root.parents[3]
manifest = json.loads((root / 'manifest.json').read_text())
archive = root / manifest['archive']
assert sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']
with ZipFile(archive) as zipped:
    assert len(zipped.namelist()) == len(set(zipped.namelist())) == len(manifest['entries'])
    for entry in manifest['entries']:
        assert sha256(zipped.read(entry['path'])).hexdigest() == entry['sha256'], entry['path']
    for variant in manifest['variants']:
        log = zipped.read('logs/' + variant['name'] + '/tsc.txt').decode()
        assert Counter(re.findall(r': error (TS\d+):', log)) == variant['errorCodes']
    if args.typecheck or args.java:
        target = Path(tempfile.mkdtemp(prefix='recipe-capture-replay-', dir=repo / 'build')).resolve()
        for name in zipped.namelist():
            if not name.startswith(('inputs/', 'compiled/', 'java/')):
                continue
            destination = (target / name).resolve()
            assert destination.is_relative_to(target)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(zipped.read(name))
        if args.typecheck:
            compiler = repo / 'node_modules/typescript/bin/tsc'
            assert subprocess.check_output(['node', str(compiler), '--version'], text=True).strip() == 'Version 5.8.3'
            for variant in manifest['variants']:
                result = subprocess.run(['node', str(compiler), '-p', str(target / 'inputs' / variant['name'] / 'tsconfig.json')],
                                        capture_output=True, timeout=120)
                codes = Counter(re.findall(r': error (TS\d+):', result.stdout.decode()))
                assert result.returncode == variant['exitCode'] and not result.stderr and codes == variant['errorCodes'], (variant['name'], result.stdout)
                print('REPLAY:', variant['name'], 'exit', result.returncode, dict(codes))
        if args.java:
            jar = args.dfu_jar or Path(manifest['dfuJar'])
            assert sha256(jar.read_bytes()).hexdigest() == manifest['dfuJarSha256']
            javac = Path('C:/Program Files/Java/jdk-21.0.10/bin/javac.exe')
            for name, expected in [('Positive', 0), ('Negative', 1)]:
                output = target / ('javac-' + name)
                output.mkdir()
                result = subprocess.run([str(javac), '-J-Duser.language=en', '-J-Duser.country=US', '-J-Dfile.encoding=UTF-8',
                                         '-Xdiags:verbose',
                                         '-classpath', str(target / 'compiled') + ';' + str(jar), '-d', str(output),
                                         str(target / 'java' / ('Capture' + name + '.java'))], capture_output=True, timeout=60)
                assert result.returncode == expected, (name, result.stderr)
                if name == 'Negative':
                    assert b'CAP#1' in result.stderr and b'CAP#2' in result.stderr and b'encodeStart' in result.stderr
                print('JAVA:', name, 'exit', expected)
        print('Artifacts:', target)
print('PASS: exact failure evidence reproduced; all capture proposals remain rejected')
