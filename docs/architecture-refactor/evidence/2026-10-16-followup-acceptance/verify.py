"""Check archived hashes and the recorded runtime boundaries without writing files."""

import hashlib
import json
import zipfile
from pathlib import Path


def checked_archive(directory, prefix):
    manifest = json.loads((directory / (prefix + '-manifest.json')).read_text(encoding='utf-8'))
    archive = directory / manifest['archive']
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == manifest['sha256']
    with zipfile.ZipFile(archive) as evidence:
        assert len(evidence.namelist()) == len(manifest['entries'])
        assert set(evidence.namelist()) == set(manifest['entries'])
        entries = {name:evidence.read(name) for name in evidence.namelist()}
    for name, expected in manifest['entries'].items():
        assert hashlib.sha256(entries[name]).hexdigest() == expected, name
    return entries


def main():
    directory = Path(__file__).resolve().parent
    rollback = checked_archive(directory, 'rollback')
    verdict = json.loads(rollback['final-verdict.json'])
    assert verdict['verdict'] == 'PASS' and verdict['separateArtifactAndDataRollback']
    assert verdict['backupRetained'] and verdict['advancedWorldRetained']
    assert verdict['exactRestoreBeforeBoot'] and verdict['repeatedCopyHashesEqual']
    for phase in ['candidate-initial', 'candidate-advance', 'artifact-rollback', 'data-restore']:
        result = json.loads(rollback[phase + '/result.json'])
        assert result['verdict'] == 'PASS' and result['exitCode'] == 0
    reviewed = checked_archive(directory, 'reviewed')
    preparation = json.loads(reviewed['preparation-manifest.json'])
    assert hashlib.sha256(reviewed['production-source.patch']).hexdigest() == preparation['productionPatchSha256']
    for node in preparation['nodes']:
        prefix = 'installed/' + node['node'] + '/'
        assert json.loads(reviewed[prefix + 'runtime-verdict.json'])['verdict'] == 'PASS'
        assert json.loads(reviewed[prefix + 'server-exit.json'])['exitCode'] == 0
        assert node['classBindings'] and len(node['sha256']) == 64
    red = json.loads(reviewed['reclaim/red/result.json'])
    green = json.loads(reviewed['reclaim/green/result.json'])
    assert red['componentsPreserved'] is False and green['componentsPreserved'] is True
    assert red['exitCode'] == green['exitCode'] == 0
    assert green['artifactSha256'] == next(row['sha256'] for row in preparation['nodes'] if row['node'] == '26.2.0')
    ide = json.loads(reviewed['ide/summary.json'])
    assert len(ide['nodes']) == 5
    for node in ide['nodes']:
        assert node['pyright']['summary']['errorCount'] == 0
        assert node['pyright']['summary']['filesAnalyzed'] == node['pyright']['stubCount']
        assert all(side['skipLibCheck'] is False for side in node['typescript'])
    print('PASS: archive hashes, source binding, five runtimes/Pyright runs, component red/green and four rollback phases')


if __name__ == '__main__':
    main()
