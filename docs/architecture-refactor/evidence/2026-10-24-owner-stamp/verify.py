"""Recount the full matrix, installed checks and complete strict TypeScript diagnostics."""
import ast
import collections
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
assert hashlib.sha256((root / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
audit = json.loads((root / 'privacy-audit.json').read_text(encoding='utf-8'))
assert audit['archiveSha256'] == manifest['archiveSha256']
audit_tool = (root / 'audit-local-credentials.py').read_bytes().replace(b'\r\n', b'\n')
assert audit['toolSha256'] in {hashlib.sha256(data).hexdigest()
                              for data in [audit_tool, audit_tool.replace(b'\n', b'\r\n')]}
assert audit['retainedMatches'] == audit['redactedFiles'] == 0 and manifest['redactedOriginalHashes'] == {}
assert audit['topLevelEntries'] == len(manifest['entries']) and audit['payloadsScanned'] >= audit['topLevelEntries']
assert audit['localCredentialSources'] == 5 and audit['distinctLocalSecrets'] > 0 and audit['bytePatterns'] > 0
def diagnostics(data):
    counts = collections.Counter()
    for line in data.decode('utf-8-sig').splitlines():
        match = re.match(r'(.+)\(\d+,\d+\): error (TS\d+): (.*)', line)
        if not match:
            continue
        name, code, message = match.groups()
        name = name.replace('\\', '/')
        if '/.neko_probe/' in name:
            name = name.split('/.neko_probe/', 1)[1]
        elif '/nekojs/' in name:
            name = 'nekojs/' + name.split('/nekojs/', 1)[1]
        counts[(name, code, message)] += 1
    return counts
with zipfile.ZipFile(root / 'evidence.zip') as archive:
    assert len(archive.namelist()) == len(set(archive.namelist()))
    assert set(archive.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        assert not name.startswith(('/', '\\')) and '..' not in Path(name).parts
        data = archive.read(name)
        assert len(data) == expected['size'] and hashlib.sha256(data).hexdigest() == expected['sha256']
    def doc(name):
        return json.loads(archive.read(name))
    preparation = doc('installed/preparation-manifest.json')
    summary = doc('installed/ide/summary.json')
    matrix = doc('matrix/summary.json')
    assert preparation['sourceRevision'] == summary['sourceRevision'] == matrix['sourceRevision'] == manifest['sourceRevision']
    assert manifest['sourceRevision'] == '6e43b82789a7dbf7b9659dc1c21fefecac662649'
    totals = dict(tests=0, skipped=0, failures=0, errors=0)
    for name in archive.namelist():
        if name.startswith('matrix/') and name.endswith('.xml'):
            suite = ET.fromstring(archive.read(name))
            for key in totals:
                totals[key] += int(suite.get(key, '0'))
    assert totals == matrix['total'] == dict(tests=5158, skipped=271, failures=0, errors=0)
    assert archive.read('tools/event-import-ts-red.log').count(b'error TS2300:') == 6
    assert b"Cannot find name 'T'" in archive.read('tools/owner-generic-ts-red.log')
    assert b'error TS2314:' in archive.read('tools/owner-event-ts-red.log')
    assert not archive.read('tools/event-import-ts-green.log')
    assert b'test:probe-owner-generics' in archive.read('tools/owner-stamp-probe-types.log')
    assert b'test:probe-event-imports' in archive.read('tools/owner-stamp-probe-types.log')
    before = after = removed = added = stubs = 0
    removed_codes = collections.Counter()
    for node in summary['nodes']:
        name = node['node']
        assert node['pyright']['exitCode'] == 0
        assert node['pyright']['summary']['errorCount'] == node['pyright']['summary']['warningCount'] == 0
        stubs += node['pyright']['stubCount']
        assert doc(f'installed/{name}/server-exit.json')['exitCode'] == 0
        verdict = doc(f'installed/{name}/runtime-verdict.json')
        assert verdict['verdict'] == 'PASS' and not verdict['errors']
        for side in node['typescript']:
            assert side['exitCode'] == 2 and not side['skipLibCheck'] and not side['compilerCrashed']
            assert side['defaultEditorIncludes'] and side['diagnosticsComplete']
            file = name + '-' + side['side'] + '-tsc.txt'
            current = diagnostics(archive.read('installed/ide/' + file))
            previous = diagnostics(archive.read('previous-ide/' + file))
            assert sum(current.values()) == side['diagnosticCount']
            before += sum(previous.values())
            after += sum(current.values())
            removed += sum((previous - current).values())
            added += sum((current - previous).values())
            for (_, code, _), count in (previous - current).items():
                removed_codes[code] += count
    assert (before, after, removed, added, stubs) == (5549, 5537, 12, 0, 1609)
    assert removed_codes == {'TS2300': 8, 'TS2304': 4}
    assert doc('installed/ide/delta-from-87baad4b.json')['total'] == dict(before=before, after=after, removed=removed, added=added)
    stub_names = [name for name in archive.namelist() if name.startswith('installed/') and name.endswith('.pyi')]
    assert len(stub_names) == stubs
    for name in stub_names:
        ast.parse(archive.read(name).decode('utf8'), filename=name)
    for node in ['26.2.0', '26.2.0-fabric']:
        declaration = archive.read('installed/'+node+'/.neko_probe/typescript/@package/net/minecraft/server/dedicated/index.d.ts')
        assert b'class $Settings$MutableValue<T extends $Settings<T>, V>' in declaration
    for node in ['26.1.2-fabric', '26.2.0-fabric']:
        events = archive.read('installed/'+node+'/.neko_probe/typescript/@side-only/server/events/index.d.ts')
        assert b'$PlayerEntityInteractEventJS as $NekoImport$PlayerEntityInteractEventJS ' in events
        assert b'$PlayerEntityInteractEventJS as $NekoImport$PlayerEntityInteractEventJS1,' in events
    measurement = doc('attribute-measurement/summary.json')
    samples = [json.loads(line) for line in archive.read('attribute-measurement/samples.jsonl').decode().splitlines()]
    assert len(samples) == 26 and len({row['checksum'] for row in samples}) == 1
    assert all(row['operations'] == 18000 for row in samples)
    assert len(measurement['fixtureBindings']) == 18 and measurement['exitCode'] == 0
    assert not archive.read('attribute-measurement/stderr.txt')
    for combined, group in [(False,'separate'), (True,'combined')]:
        values = [row['elapsedNs']/row['operations'] for row in samples if row['phase']=='formal' and row['combined']==combined]
        assert len(values) == 10 and values == measurement['groups'][group]['nsPerOperation']
        assert abs(sum(values)/len(values)-measurement['groups'][group]['mean']) < 1e-8
    assert hashlib.sha256(archive.read('tools/FileStampAttributesBenchmark.java')).hexdigest() == measurement['sourceSha256']

print(json.dumps(dict(archive='PASS', tests=5158, skipped=271, normalInstalledPasses=5,
    pythonStubs=1609, fullStrictTypeScript='FAIL', diagnostics=5537, removed=12, added=0)))
