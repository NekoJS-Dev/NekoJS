"""Check archived hashes and the recorded runtime boundaries without writing files."""

import hashlib
import json
import statistics
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
    frames = checked_archive(directory, 'frame')
    summary = json.loads(frames['frame-summary.json'])
    assert summary['verdict'] == 'PASS' and summary['clientExit'] == summary['serverExit'] == 0
    assert summary['displayPresentVerified'] is False
    captures = [row for row in summary['captures'] if 'request' in row]
    assert len(captures) == 7
    for capture in captures:
        assert capture['request']['sameThread'] and capture['request']['overlayPresent'] is False
        image = frames['accepted/clientA/screenshots/' + capture['screenshot']['file']]
        assert hashlib.sha256(image).hexdigest() == capture['screenshot']['sha256']
        energy = capture['wallRegion']['meanSquaredHorizontalGradient']
        assert energy > 100 if capture['step'].startswith('clear-') else 0.5 < energy < 50
    for step, filename in [('clear-before', 'frame-clear.png'), ('blur-before', 'frame-blur.png')]:
        capture = next(row for row in captures if row['step'] == step)
        assert hashlib.sha256((directory / filename).read_bytes()).hexdigest() == capture['screenshot']['sha256']
    performance = checked_archive(directory, 'performance')
    for label, expected_groups in [('candidate', 3), ('control', 2)]:
        judgment = json.loads(performance[label + '/judgment.json'])
        assert judgment['complete'] and judgment['verdict'] == 'FAIL'
        assert len(judgment['groups']) == expected_groups
        for group in judgment['groups']:
            prefix = label + '/formal/' + group['group'] + '/'
            rows = [json.loads(line) for line in performance[prefix + 'samples.jsonl'].decode('utf-8-sig').splitlines()]
            formal = [row for row in rows if row['kind'] == 'formal']
            assert len(formal) == 5
            assert all(not row.get('timeout') and not row['forced_kill'] and not row['killed']
                       and row['stop_channel'] == 'rcon' for row in rows)
            assert group['values'] == [row[group['measure']] for row in formal]
            assert group['mean'] == statistics.mean(group['values'])
            expected_verdict = 'PASS' if group['mean'] <= group['limit'] else 'FAIL'
            assert group['verdict'] == expected_verdict
            if group['group'] == 'startup':
                assert len([row for row in rows if row['kind'] == 'warmup']) == 2
                assert group['limit'] == 41173
            else:
                assert group['limit'] == 285.3
                assert all('phase=COMMIT - no errors' in row['rcon_response'] for row in formal)
    frozen = json.loads(performance['candidate/frozen-classes.json'])
    primary = next(row for row in preparation['nodes'] if row['node'] == '26.1.2')
    assert frozen['classesMatchReviewedArtifact'] and frozen['classBindings'] == primary['classBindings']
    assert frozen['reviewedArtifactSha256'] == primary['sha256']
    profile_session = json.loads(performance['profile/session.json'].decode('utf-8-sig'))
    assert not profile_session['forcedKill'] and not profile_session['extra']['killed']
    assert profile_session['extra']['stopChannel'] == 'rcon'
    assert len(json.loads(performance['profile/reloads.json'].decode('utf-8-sig'))) == 5
    investigation = checked_archive(directory, 'investigation')
    prototype = json.loads(investigation['shared-parse/summary.json'])
    assert prototype['verdict'] == 'REJECTED' and prototype['diagnosticOnly']
    assert len(prototype['pairs']) == 10 and prototype['focusedTests']['failures'] == 0
    phases = json.loads(investigation['phases/phase-summary.json'])
    assert phases['diagnosticOnly'] and len(phases['commandedPhases']) == len(phases['commands']) == 20
    assert phases['phaseMeanMs'] == statistics.mean(row['totalMs'] for row in phases['commandedPhases'])
    for name, mean in phases['stepMeansMs'].items():
        assert mean == statistics.mean(row['stepsMs'][name] for row in phases['commandedPhases'])
    session = json.loads(investigation['phases/session.json'].decode('utf-8-sig'))
    assert not session['forcedKill'] and not session['extra']['killed']
    assert session['extra']['stopChannel'] == 'rcon'
    print('PASS: artifact, runtime, IDE, rollback, frame and performance evidence integrity; reload performance verdict remains FAIL')


if __name__ == '__main__':
    main()
