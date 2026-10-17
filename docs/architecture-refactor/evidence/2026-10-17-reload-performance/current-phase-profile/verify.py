"""Recompute diagnostic timings; success never means the formal performance gate passed."""
import hashlib
import json
import re
import statistics
import zipfile
from pathlib import Path


def verify():
    root = Path(__file__).resolve().parent
    manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
    packed = root / 'evidence.zip'
    assert hashlib.sha256(packed.read_bytes()).hexdigest() == manifest['archiveSha256']
    with zipfile.ZipFile(packed) as archive:
        assert len(archive.namelist()) == len(set(archive.namelist()))
        assert set(archive.namelist()) == set(manifest['entries'])
        assert not any(name.endswith('.jfr') for name in archive.namelist())
        for name, entry in manifest['entries'].items():
            data = archive.read(name)
            assert len(data) == entry['bytes'] and hashlib.sha256(data).hexdigest() == entry['sha256'], name

        def read(name):
            return json.loads(archive.read(name))

        rows = read('diagnostic/reloads.json')
        summary = read('diagnostic/summary.json')
        session = read('diagnostic/session.json')
        assert len(rows) == 20 and [r['index'] for r in rows] == list(range(1, 21))
        assert all('phase=COMMIT - no errors.' in r['response'] for r in rows)
        assert not session['forcedKill'] and not session['extra']['killed'] and session['extra']['stopChannel'] == 'rcon'
        text = archive.read('diagnostic/profile-stderr.log').decode('utf-8-sig')
        timings = re.findall(r'command type=server rootNs=(\d+) recipeNs=(\d+)', text)
        assert len(timings) == 20
        assert summary['rootMeanMs'] == statistics.mean(int(a)/1e6 for a, _ in timings)
        assert summary['recipeMeanMs'] == statistics.mean(int(b)/1e6 for _, b in timings)
        assert summary['rconMeanMs'] == statistics.mean(r['rconMs'] for r in rows)
        metrics = [{name: dict(ms=int(ns)/1e6, calls=int(count)) for name, ns, count in
                    re.findall(r'([^=;\s]+)=(\d+)/(\d+);', line)}
                   for line in text.splitlines() if '[DEBUG-CLOSEOUT-17]' in line]
        assert len(metrics) == 20
        for name, expected in summary['metrics'].items():
            assert expected['meanMs'] == statistics.mean(m[name]['ms'] for m in metrics)
            assert expected['meanCalls'] == statistics.mean(m[name]['calls'] for m in metrics)
        assert summary['metrics']['canonicalModulePath']['meanCalls'] == 24
        assert len([name for name in summary['metrics'] if name.startswith('builtinEval/')]) == 17
        restored = read('diagnostic/restoration.json')
        assert restored['revision'] == manifest['revision']
        assert restored['sourceRestoredAndClean'] and restored['helperSourceAbsent']
        assert restored['recompiledClassesWithoutDiagnosticTags'] == 1625

    audit = json.loads((root / 'prior-jfr-audit.json').read_text(encoding='utf-8'))
    assert audit['archivedBinaryJfrUsable'] is False and len(audit['results']) == 2
    assert all(r['originalExitCode'] == 0 and r['archivedRedactedExitCode'] == 1
               and r['recomputedTextMatchesArchive'] for r in audit['results'])
    print(json.dumps(dict(integrity='PASS', diagnosticOnly=True, commands=20,
                          rootMeanMs=summary['rootMeanMs'], recipeMeanMs=summary['recipeMeanMs'],
                          sourceAndBuildRestored=True, formalPerformancePassNotEstablished=True)))


if __name__ == '__main__':
    verify()
