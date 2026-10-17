"""Verify archived diagnostic controls without treating them as a performance pass."""
import hashlib
import json
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
        for name, entry in manifest['entries'].items():
            data = archive.read(name)
            assert len(data) == entry['bytes'] and hashlib.sha256(data).hexdigest() == entry['sha256'], name

        def read(name):
            return json.loads(archive.read(name))

        cjs = read('cjs-source-prototype-v6/summary.json')
        pairs = read('cjs-source-prototype-v6/run.log')['pairs']
        assert len(pairs) == 10
        assert cjs['equivalentCount'] and not cjs['equivalentThis'] and cjs['rejectedForSemanticChange']
        assert cjs['dynamicMsPerSix'] == sum(p['dynamicNs'] for p in pairs)/100/1e6
        assert cjs['sourceMsPerSix'] == sum(p['sourceNs'] for p in pairs)/100/1e6
        assert all(p['dynamicRealPaths'] == p['sourceRealPaths'] == 0 for p in pairs)

        patterns = read('pattern-compilation-control/summary.json')
        pairs = read('pattern-compilation-control/pairs.json')
        assert len(pairs) == 10 and patterns['fixedPatterns'] == 8 and patterns['sources'] == 6
        assert patterns['baselineMsPerSix'] == sum(p['baselineNs'] for p in pairs)/1000/1e6
        assert patterns['candidateMsPerSix'] == sum(p['candidateNs'] for p in pairs)/1000/1e6

        rows = read('root-reload-diagnostic-v3/samples.json')
        summary = read('root-reload-diagnostic-v3/summary.json')
        assert len(rows) == 30 and [r['index'] for r in rows] == list(range(30))
        assert all(r['contextReplaced'] and r['preparedEntries'] == 6 for r in rows)
        assert all(r['oldContextClosed'] for r in rows[1:])
        assert summary['lastTwentyMeanMs'] == sum(r['nanos'] for r in rows[10:])/20/1e6
        assert b'PERF02-SERVER tick-bench load' in archive.read('root-reload-diagnostic-v3/script-logs/server.log')
        assert b'No SLF4J providers' not in archive.read('root-reload-diagnostic-v3/run-stderr.log')

        rcon = read('rcon-client-startup-control/summary.json')
        assert len(rcon['samples']) == 20
        assert rcon['meanNormalMs'] == statistics.mean(p['normalMs'] for p in rcon['samples'])
        assert rcon['meanWithoutSiteMs'] == statistics.mean(p['withoutSiteMs'] for p in rcon['samples'])
        print(json.dumps(dict(integrity='PASS', diagnosticOnly=True,
                              formalPerformancePassNotEstablished=True, rootReloads=len(rows))))


if __name__ == '__main__':
    verify()
