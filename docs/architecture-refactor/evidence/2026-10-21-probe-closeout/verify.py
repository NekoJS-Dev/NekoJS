"""Verify archived stage evidence without launching Minecraft or declaring release acceptance."""
import collections
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf8'))
summary = json.loads((root / 'summary.json').read_text(encoding='utf8'))
archive = root / 'evidence.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']


def diagnostics(text):
    rows = collections.Counter()
    for line in text.splitlines():
        match = re.match(r'.*?(/\.neko_probe/[^\r\n]*?)\(\d+,\d+\): error (TS\d+): (.*)', line)
        if match:
            rows[match.groups()] += 1
    assert sum(rows.values()) == len(re.findall(r'error TS\d+:', text))
    return rows


with zipfile.ZipFile(archive) as z:
    assert len(z.namelist()) == len(set(z.namelist()))
    assert set(z.namelist()) == set(manifest['entries'])
    for name, expected in manifest['entries'].items():
        data = z.read(name)
        assert len(data) == expected['size'], name
        assert hashlib.sha256(data).hexdigest() == expected['sha256'], name
        assert not name.endswith(('server.properties', '.jar', 'owned-java-args.txt'))
    totals = collections.Counter()
    for name in z.namelist():
        if name.startswith('checks/xml/') and name.endswith('.xml'):
            suite = ET.fromstring(z.read(name))
            totals.update({k: int(suite.attrib.get(k, 0)) for k in ['tests', 'skipped', 'failures', 'errors']})
    assert dict(totals) == dict(tests=5056, skipped=271, failures=0, errors=0), totals
    build = z.read('checks/logs/dd15186c-final-build-v2.log').decode('utf8', errors='replace')
    assert 'BUILD SUCCESSFUL' in build and '> Task :common:checkCommonIsolation' in build
    for node in summary['nodes']:
        name = node['node']
        report = json.loads(z.read('installed/' + name + '/runtime-verdict.json'))
        assert report['verdict'] == 'PASS' and not report['errors'], name
        assert report['checks'] and all(report['checks'].values()), name
        assert json.loads(z.read('installed/' + name + '/server-exit.json'))['exitCode'] == 0
        python = json.loads(z.read('ide/current/' + name + '-pyright.json'))
        count = sum(p.startswith('declarations/' + name + '/python/') and p.endswith('.pyi') for p in z.namelist())
        assert python['summary']['filesAnalyzed'] == count == node['pyright']['stubCount']
        assert python['summary']['errorCount'] == python['summary']['warningCount'] == 0
    comparison = json.loads(z.read('ide/current/comparison.json'))
    aggregate = collections.Counter()
    for row in comparison['rows']:
        filename = row['node'] + '-' + row['side'] + '-tsc.txt'
        old = diagnostics(z.read('ide/baseline/' + filename).decode('utf-8-sig'))
        new = diagnostics(z.read('ide/current/' + filename).decode('utf-8-sig'))
        actual = dict(before=sum(old.values()), after=sum(new.values()),
                      removed=sum((old - new).values()), added=sum((new - old).values()))
        assert all(row[k] == value for k, value in actual.items()), row
        assert z.read('ide/current/' + filename.replace('-tsc.txt', '-tsc-stderr.txt')) == b''
        aggregate.update(actual)
    assert dict(aggregate) == dict(before=6867, after=6523, removed=344, added=0), aggregate
    assert summary['fullTypeScriptVerdict'] == 'FAIL'
    assert summary['afterDiagnostics'] == aggregate['after']
    preparation = json.loads(z.read('installed/preparation-manifest.json'))
    assert preparation['sourceRevision'] == manifest['sourceRevision'] == summary['sourceRevision']
    assert len(preparation['nodes']) == 5
    for node in preparation['nodes']:
        assert len(node['classBindings']) > 1500 and len(node['sha256']) == 64

print('PASS: archive integrity, 5056 tests, five installed server exits, full Python, and 344 removed TS diagnostics verified.')
print('FAIL retained: full TypeScript has 6523 diagnostics. This verifier does not grant client, TCP, rollback or release acceptance.')
