"""Audit archived payloads against local test credentials without disclosing the patterns."""
import hashlib
import io
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent
repo = root.parents[3]
output = root / 'privacy-audit.json'
assert not output.exists(), 'Preserve the existing audit; use a separate output copy for another run.'
secrets = set()
nodes = ['1.21.1', '26.1.2', '26.2.0', '26.1.2-fabric', '26.2.0-fabric']
for node in nodes:
    path = repo / 'build/closeout-owner-stamp-installed' / node / 'server.properties'
    assert path.is_file(), 'Local private test properties are required; no audit was performed.'
    properties = dict(line.split('=', 1) for line in path.read_text(encoding='utf-8-sig').splitlines()
                      if '=' in line and not line.startswith('#'))
    secret = properties['rcon.password']
    assert secret, 'Empty private pattern; no audit was performed.'
    secrets.add(secret)
patterns = {secret.encode(encoding) for secret in secrets for encoding in ['utf-8', 'utf-16-le', 'utf-16-be']}
checked = nested_archives = matches = 0
def scan(data, depth=0):
    global checked, nested_archives, matches
    assert depth <= 4
    checked += 1
    matches += sum(data.count(pattern) for pattern in patterns)
    stream = io.BytesIO(data)
    if zipfile.is_zipfile(stream):
        nested_archives += 1
        with zipfile.ZipFile(stream) as archive:
            assert len(archive.namelist()) == len(set(archive.namelist()))
            for name in archive.namelist():
                scan(archive.read(name), depth + 1)
payload = (root / 'evidence.zip').read_bytes()
with zipfile.ZipFile(io.BytesIO(payload)) as archive:
    entries = len(archive.namelist())
    for name in archive.namelist():
        scan(archive.read(name))
assert matches == 0, 'Private credential pattern matched; do not publish the archive.'
manifest = json.loads((root / 'manifest.json').read_text())
assert manifest['redactedOriginalHashes'] == {}
audit = dict(archiveSha256=hashlib.sha256(payload).hexdigest(),
    toolSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    topLevelEntries=entries, payloadsScanned=checked, nestedArchives=nested_archives,
    localCredentialSources=len(nodes), distinctLocalSecrets=len(secrets), bytePatterns=len(patterns),
    retainedMatches=matches, redactedFiles=0,
    limits='Local pattern audit only; private patterns are not archived or independently reconstructed by verify.py.')
output.write_text(json.dumps(audit, indent=2) + '\n', encoding='utf-8', newline='\n')
print(json.dumps(audit))
