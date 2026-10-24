"""Audit the three frozen runtime proofs against their private local test patterns."""
from datetime import datetime, UTC
import hashlib
import io
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent
repo = root.parents[3]
output = root / 'privacy-audit.json'
assert not output.exists()
tools = repo / 'build/priority-closeout'
groups = {
    '2026-10-24-owner-stamp-tcp': [tools / f'dynamic-{kind}-6e43b827-{short}-v1/server/server.properties'
        for kind in ['basic', 'commit'] for short in ['261', '262']],
    '2026-10-24-owner-stamp-render': [tools / f'posteffects-{short}-installed-6e43b827-v1/server/server.properties'
        for short in ['121', '261', '262']],
    '2026-10-24-owner-stamp-rollback': [repo / 'build/closeout-owner-stamp-rollback' / phase / 'server.properties'
        for phase in ['baseline-initial', 'candidate-advance', 'artifact-rollback', 'data-restore']],
}
proofs = {}
for name, paths in groups.items():
    secrets = set()
    for path in paths:
        assert path.resolve().is_relative_to(repo.resolve()) and path.is_file()
        properties = dict(line.split('=', 1) for line in path.read_text(encoding='utf-8-sig').splitlines()
                          if '=' in line and not line.startswith('#'))
        value = properties['rcon.password']
        assert value
        secrets.add(value)
    patterns = {value.encode(encoding) for value in secrets for encoding in ['utf-8', 'utf-16-le', 'utf-16-be']}
    state = dict(payloadsScanned=0, nestedArchives=0, retainedMatches=0)
    def scan(data, depth=0):
        assert depth <= 4
        state['payloadsScanned'] += 1
        state['retainedMatches'] += sum(data.count(pattern) for pattern in patterns)
        stream = io.BytesIO(data)
        if zipfile.is_zipfile(stream):
            state['nestedArchives'] += 1
            with zipfile.ZipFile(stream) as archive:
                assert len(archive.namelist()) == len(set(archive.namelist()))
                for entry in archive.namelist():
                    scan(archive.read(entry), depth + 1)
    folder = root.parent / name
    manifest = json.loads((folder / 'manifest.json').read_text(encoding='utf-8'))
    assert manifest['redactedOriginalHashes'] == {}
    payload = (folder / 'evidence.zip').read_bytes()
    digest = hashlib.sha256(payload).hexdigest()
    assert digest == manifest['archiveSha256']
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        entries = len(archive.namelist())
        for entry in archive.namelist():
            scan(archive.read(entry))
    assert state['retainedMatches'] == 0
    proofs[name] = dict(archiveSha256=digest, topLevelEntries=entries, **state,
        localCredentialSources=len(paths), distinctLocalSecrets=len(secrets), bytePatterns=len(patterns), redactedFiles=0)
audit = dict(recordedAtUtc=datetime.now(UTC).isoformat(), proofs=proofs,
    toolSha256Lf=hashlib.sha256(Path(__file__).read_bytes().replace(b'\r\n', b'\n')).hexdigest(),
    limits='Private patterns and their digests are not archived. Public verification binds this receipt; it cannot independently repeat a private-pattern scan without local inputs.')
output.write_text(json.dumps(audit, indent=2) + '\n', encoding='utf-8', newline='\n')
print(json.dumps(dict(archives=len(proofs), payloadsScanned=sum(row['payloadsScanned'] for row in proofs.values()),
    retainedMatches=sum(row['retainedMatches'] for row in proofs.values()), redactedFiles=0)))
