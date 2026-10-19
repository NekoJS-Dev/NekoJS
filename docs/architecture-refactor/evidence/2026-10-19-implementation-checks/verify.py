"""Verify the frozen check outputs without invoking Gradle or a game."""
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
expected = json.loads((root / 'verification.json').read_text(encoding='utf-8'))
archive = root / 'evidence.zip'
assert archive.stat().st_size == expected['archiveBytes']
assert hashlib.sha256(archive.read_bytes()).hexdigest() == expected['archiveSha256']
with zipfile.ZipFile(archive) as evidence:
    names = evidence.namelist()
    assert len(names) == len(set(names)) == expected['entries']
    manifest = json.loads(evidence.read('manifest.json'))
    assert set(names) == set(manifest) | {'manifest.json'}
    for name, digest in manifest.items():
        assert hashlib.sha256(evidence.read(name)).hexdigest() == digest, name
    for name in ['focused-result.json', 'full-check-result.json',
                 'source-roots-result.json', 'ci-gates-result.json']:
        result = json.loads(evidence.read('run/' + name).decode('utf-8-sig'))
        assert result['exitCode'] == 0 and result['commit'] == expected['revision']
    counts = {}
    for phase in ['focused-xml', 'full-xml']:
        stats = dict(tests=0, failures=0, errors=0, skipped=0)
        for name in names:
            if name.startswith(phase + '/') and name.endswith('.xml'):
                suite = ET.fromstring(evidence.read(name))
                for key in stats:
                    stats[key] += int(suite.attrib.get(key, 0))
        assert stats == expected['counts'][phase]
        assert stats['failures'] == stats['errors'] == 0
        counts[phase] = stats
    checks = json.loads(evidence.read('run/ci-gates.json'))['gates']
    assert len(checks) == 4 and all(gate['state'] == 'pass' for gate in checks)
    binding = json.loads(evidence.read('binding.json'))
    assert binding['revision'] == expected['revision']
    assert binding['preservedFilesUnchanged'] and not binding['artifactsIncluded']
    assert not binding['gameLaunched'] and not binding['performanceMeasured']
    for name, digest in binding['sourceSha256'].items():
        assert hashlib.sha256(evidence.read('source/' + name)).hexdigest() == digest
    full_log = evidence.read('run/full-check.log').decode('utf-8-sig')
    assert 'BUILD SUCCESSFUL' in full_log and 'FAILED' not in full_log
    for node in ['1.21.1', '26.1.2', '26.2.0', '26.1.2-fabric', '26.2.0-fabric']:
        assert '> Task :' + node + ':build' in full_log
    assert '警告 0 条' in full_log
print(json.dumps(dict(evidenceVerification='PASS', counts=counts), ensure_ascii=False))
