"""Verify command visibility diagnostics without changing the formal performance verdict."""
import hashlib
import json
import statistics
import zipfile
from pathlib import Path


def summarize(rows):
    samples = []
    for row in rows:
        timeline = {name: (utc, nano) for name, utc, nano in row['clientTimeline']}
        assert len(timeline) == 9
        assert not row['timeout'] and not row['error'] and row['clientExit']['exitCode'] == 0
        sample = dict(index=row['index'], command=row['command'],
                      launchToChildMs=(timeline['child-start'][0]-row['parentStartedUtcNs'])/1e6,
                      importsMs=(timeline['imports-ready'][1]-timeline['child-start'][1])/1e6,
                      connectMs=(timeline['connected'][1]-timeline['connect-start'][1])/1e6,
                      authMs=(timeline['auth-received'][1]-timeline['auth-send'][1])/1e6,
                      commandToResponseMs=(timeline['response-received'][1]-timeline['command-send'][1])/1e6,
                      parentExitMs=row['clientExit']['parentElapsedMs'])
        if row['command'] == 'nekojs reload':
            assert 'phase=COMMIT - no errors.' in row['response']
            phases = row['phases']
            assert set(phases) == {'root-start', 'root-end', 'recipe-end'}
            sample['rootMs'] = (phases['root-end']['serverNano']-phases['root-start']['serverNano'])/1e6
            sample['recipeMs'] = (phases['recipe-end']['serverNano']-phases['root-end']['serverNano'])/1e6
            sample['markerObservedMs'] = row['marker']['parentElapsedMs']
            sample['markerAfterClientExitMs'] = sample['markerObservedMs']-sample['parentExitMs']
            if 'markerSinks' in row:
                sinks = row['markerSinks']
                assert set(sinks) == {'server.log', 'latest.log', 'visibility-stdout.log'}
                for name, marker in sinks.items():
                    sample[name+'ObservedMs'] = marker['parentElapsedMs']
                    sample[name+'AfterClientExitMs'] = marker['parentElapsedMs']-sample['parentExitMs']
                sample['stdoutAfterServerLogMs'] = sinks['visibility-stdout.log']['parentElapsedMs']-sinks['server.log']['parentElapsedMs']
                sample['stdoutAfterLatestLogMs'] = sinks['visibility-stdout.log']['parentElapsedMs']-sinks['latest.log']['parentElapsedMs']
        else:
            assert row['command'] == 'list' and not row['phases'] and row['marker'] is None
        samples.append(sample)
    groups = {}
    for command in sorted({row['command'] for row in samples}):
        selected = [row for row in samples if row['command'] == command]
        keys = set(selected[0])-{'index', 'command'}
        groups[command] = dict(count=len(selected), metrics={
            key: dict(mean=statistics.mean(row[key] for row in selected),
                      min=min(row[key] for row in selected), max=max(row[key] for row in selected))
            for key in sorted(keys)})
    return dict(diagnosticOnly=True, samples=samples, groups=groups)


def verify():
    root = Path(__file__).resolve().parent
    manifest = json.loads((root/'manifest.json').read_text(encoding='utf-8'))
    packed = root/'evidence.zip'
    assert hashlib.sha256(packed.read_bytes()).hexdigest() == manifest['archiveSha256']
    with zipfile.ZipFile(packed) as archive:
        assert len(archive.namelist()) == len(set(archive.namelist()))
        assert set(archive.namelist()) == set(manifest['entries'])
        assert not any(name.endswith('.jfr') for name in archive.namelist())
        for name, expected in manifest['entries'].items():
            data = archive.read(name)
            assert len(data) == expected['bytes'] and hashlib.sha256(data).hexdigest() == expected['sha256'], name
        for label, count in [('command-control', 20), ('three-sinks', 10)]:
            rows = json.loads(archive.read(label+'/observations.json'))
            expected = json.loads(archive.read(label+'/recomputed.json'))
            assert len(rows) == count and [r['index'] for r in rows] == list(range(1, count+1))
            assert summarize(rows) == expected
            session = json.loads(archive.read(label+'/session.json'))
            assert not session['forcedKill'] and not session['extra']['killed']
            assert session['extra']['stopSent'] and session['extra']['stopChannel'] == 'rcon'
            hashes = json.loads(archive.read(label+'/source-hashes.json'))
            assert hashes['sha256'].lower() == manifest['diagnosticSourceSha256']
        restored = json.loads(archive.read('three-sinks/restoration.json'))
        assert restored['revision'] == manifest['revision'] and restored['sourceRestoredAndClean']
        assert restored['rebuilt'] and restored['mainClassesWithoutVisibilityTags'] == 1625
        original = archive.read('tooling/visibility-original-command.java')
        assert hashlib.sha256(original).hexdigest() == manifest['originalSourceSha256']
        sampler = archive.read('tooling/original-sample.ps1')
        assert hashlib.sha256(sampler).hexdigest() == manifest['archivedSamplerSha256']
        assert manifest['originalSamplerSha256'] == '1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4'
        redactions = json.loads(archive.read('redactions.json'))
        assert redactions['tooling/original-sample.ps1']['originalSha256'] == manifest['originalSamplerSha256']
        sink_metrics = expected['groups']['nekojs reload']['metrics']
        assert sink_metrics['stdoutAfterServerLogMs']['min'] > 0
        assert sink_metrics['stdoutAfterLatestLogMs']['min'] > 0
    print(json.dumps(dict(integrity='PASS', diagnosticOnly=True, commands=30,
                         stdoutAfterServerLogMs=sink_metrics['stdoutAfterServerLogMs'],
                         sourceAndBuildRestored=True, formalPerformancePassNotEstablished=True)))


if __name__ == '__main__':
    verify()
