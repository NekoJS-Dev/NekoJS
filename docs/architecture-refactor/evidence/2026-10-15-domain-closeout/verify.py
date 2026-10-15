"""Verify the archived domain-closeout evidence without changing the workspace."""

import hashlib
import json
import zipfile
from pathlib import Path


def main():
    directory = Path(__file__).resolve().parent
    manifest = json.loads((directory / 'manifest.json').read_text(encoding='utf-8'))
    archive = directory / manifest['archive']
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == manifest['sha256']
    with zipfile.ZipFile(archive) as evidence:
        assert set(evidence.namelist()) == set(manifest['entries'])
        for name, expected in manifest['entries'].items():
            assert hashlib.sha256(evidence.read(name)).hexdigest() == expected, name
        preparation = json.loads(evidence.read('preparation-manifest.json'))
        assert hashlib.sha256(evidence.read('production-source.patch')).hexdigest() == preparation['productionPatchSha256']
        for path, expected in preparation['sourceHashes'].items():
            addition = 'source-additions/' + path
            if addition in manifest['entries']:
                assert hashlib.sha256(evidence.read(addition)).hexdigest() == expected
        for node in preparation['nodes']:
            verdict = json.loads(evidence.read('installed/' + node['node'] + '/runtime-verdict.json'))
            assert verdict['verdict'] == 'PASS'
            assert node['classBindings'] and len(node['sha256']) == 64
    print('PASS: archive hashes, source patch binding, five runtime verdicts and class-binding manifests')


if __name__ == '__main__':
    main()
