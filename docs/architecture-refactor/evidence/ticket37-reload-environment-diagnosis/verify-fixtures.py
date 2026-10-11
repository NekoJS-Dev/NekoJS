import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

root = Path.cwd().resolve()
out = root / 'docs/architecture-refactor/evidence/ticket37-reload-environment-diagnosis'
expected = json.loads((root / 'docs/architecture-refactor/evidence/ticket37-same-name-performance/summary.json').read_text())['fixtureHashes']
with ZipFile(out / 'reload-environment-evidence.zip') as archive:
    for group in ['old-environment-control', 'adjacent-current-control']:
        actual = json.loads(archive.read('wrapper/' + group + '/fixture-hashes.json').decode('utf-8-sig'))
        assert actual == expected
for row in expected:
    assert hashlib.sha256((root / 'build/ticket37-perf-owned-ac810fa7' / row['path']).read_bytes()).hexdigest().upper() == row['sha256']
record = dict(oldAndAdjacentFixtureSnapshotsEqualOriginal93=True,
              currentProfileCheckoutFixturesEqualOriginal93=True, checkedFiles=len(expected))
with (out / 'fixture-verification.json').open('x', encoding='utf-8') as output:
    output.write(json.dumps(record, indent=2) + '\n')
print(json.dumps(record))
