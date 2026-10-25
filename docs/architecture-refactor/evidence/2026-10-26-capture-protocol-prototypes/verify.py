"""Verify frozen prototype inputs; optionally replay every strict TypeScript experiment."""

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
archive = root / manifest['archive']
assert sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']
with ZipFile(archive) as zipped:
    assert len(zipped.namelist()) == len(set(zipped.namelist())) == len(manifest['entries'])
    for entry in manifest['entries']:
        assert sha256(zipped.read(entry['path'])).hexdigest() == entry['sha256'], entry['path']
    for variant in manifest['variants']:
        text = zipped.read('logs/' + variant['name'] + '/tsc.txt').decode('utf-8')
        codes = Counter(re.findall(r': error (TS\d+):', text))
        assert dict(codes) == variant['errorCodes'], variant['name']
        assert (variant['expectedExitCode'] == 0) == (not codes)
        assert not zipped.read('logs/' + variant['name'] + '/stderr.txt')
    print('PASS: 119 archive members and 16 frozen experiment records; this is not production acceptance')
    if args.typecheck:
        repo = root.parents[3]
        compiler = repo / 'node_modules/typescript/bin/tsc'
        version = subprocess.check_output(['node', str(compiler), '--version'], text=True).strip()
        assert version == 'Version ' + manifest['typescriptVersion'], version
        replay_root = Path(tempfile.mkdtemp(prefix='capture-prototype-replay-', dir=repo / 'build')).resolve()
        assert replay_root.parent == (repo / 'build').resolve()
        records = []

        def write(destination, relative, data):
            target = (destination / relative).resolve()
            assert target.is_relative_to(destination.resolve()), relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)

        for variant in manifest['variants']:
            destination = replay_root / variant['name']
            for relative in manifest['baseFiles']:
                write(destination, relative, zipped.read('base/' + relative))
            for relative in variant['overlays']:
                write(destination, relative, zipped.read('variants/' + variant['name'] + '/' + relative))
            result = subprocess.run(['node', str(compiler), '-p', str(destination / 'tsconfig.json')],
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120)
            (destination / 'replay-tsc.txt').write_bytes(result.stdout)
            (destination / 'replay-stderr.txt').write_bytes(result.stderr)
            codes = Counter(re.findall(r': error (TS\d+):', result.stdout.decode('utf-8')))
            assert result.returncode == variant['expectedExitCode'], variant['name']
            assert dict(codes) == variant['errorCodes'], (variant['name'], dict(codes))
            assert not result.stderr, variant['name']
            records.append({'name': variant['name'], 'exitCode': result.returncode, 'errorCodes': dict(codes)})
            print('REPLAY', variant['name'], 'exit=' + str(result.returncode), flush=True)
        (replay_root / 'summary.json').write_text(json.dumps(records, indent=2) + '\n', encoding='utf-8', newline='\n')
        print('PASS: all 16 original verdicts and diagnostic-code counts reproduced; artifacts:', replay_root)
