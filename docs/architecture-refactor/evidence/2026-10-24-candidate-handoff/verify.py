"""Check the local candidate's exact artifacts while retaining its failed release gates."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parent
repo = root.parents[3]
bundle = repo / 'build/release-candidate-975496d3'
expected = json.loads((root / 'candidate-manifest.json').read_text())
assert json.loads((bundle / 'manifest.json').read_text()) == expected
assert expected['productionRevision'] == '975496d3bddf6fc76b206921a1d09207120ca24d'
assert not expected['releaseReady'] and not expected['remoteUploadPerformed'] and not expected['sourceVersionRepackaged']
assert expected['artifactVersion'] == '1.1.0-preview3'
assert expected['gates']['strictTypeScript'].startswith('FAIL:')
assert expected['gates']['reloadPerformance'].startswith('FAIL:')
assert expected['gates']['maintainerAcceptance'] == 'NOT RECORDED'
assert len(expected['artifacts']) == 6 and len({row['node'] for row in expected['artifacts']}) == 5
for row in expected['artifacts']:
    file = (bundle / row['path']).resolve()
    assert file.is_relative_to(bundle.resolve())
    data = file.read_bytes()
    assert len(data) == row['bytes'] and hashlib.sha256(data).hexdigest() == row['sha256'], row['path']
assert (bundle / 'SHA256SUMS').read_text() == ''.join(row['sha256'] + '  ' + row['path'] + '\n' for row in expected['artifacts'])
for name, digest in expected['evidenceArchives'].items():
    file = root.parent / name / 'evidence.zip'
    assert hashlib.sha256(file.read_bytes()).hexdigest() == digest, name
print(json.dumps(dict(localIntegrity='PASS', artifacts=6, nodes=5, artifactBytes=sum(row['bytes'] for row in expected['artifacts']),
    releaseReady=False, strictTypeScript='FAIL', reloadPerformance='FAIL', maintainerAcceptance=False)))
