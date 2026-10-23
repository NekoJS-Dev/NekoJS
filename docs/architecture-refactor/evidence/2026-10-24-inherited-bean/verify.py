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
    assert manifest['sourceRevision'] == '87baad4b9b082158b71d25aae7d0ba6ece0a810e'
    totals = dict(tests=0, skipped=0, failures=0, errors=0)
    for name in archive.namelist():
        if name.startswith('matrix/') and name.endswith('.xml'):
            suite = ET.fromstring(archive.read(name))
            for key in totals:
                totals[key] += int(suite.get(key, '0'))
    assert totals == matrix['total'] == dict(tests=5152, skipped=271, failures=0, errors=0)
    assert archive.read('tools/inherited-bean-ts-red.log').count(b'error TS2611:') == 2
    assert b'3 tests completed, 2 failed' in archive.read('tools/inherited-bean-red.log')
    assert not archive.read('tools/inherited-bean-ts-green.log') and not archive.read('tools/inherited-bean-ts-expanded.log')
    assert b'test:probe-inherited-bean' in archive.read('tools/inherited-bean-probe-types.log')
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
    assert (before, after, removed, added, stubs) == (5565, 5549, 16, 0, 1609)
    assert removed_codes == {'TS2611': 16}
    assert doc('installed/ide/delta-from-975496d3.json')['total'] == dict(before=before, after=after, removed=removed, added=added)
    stub_names = [name for name in archive.namelist() if name.startswith('installed/') and name.endswith('.pyi')]
    assert len(stub_names) == stubs
    for name in stub_names:
        ast.parse(archive.read(name).decode('utf8'), filename=name)
    bee = archive.read('installed/26.1.2/.neko_probe/typescript/@package/net/minecraft/world/entity/animal/bee/index.d.ts')
    arrow = archive.read('installed/26.1.2/.neko_probe/typescript/@package/net/minecraft/world/entity/projectile/arrow/index.d.ts')
    assert b'readonly goalSelector: $GoalSelector;' in bee and b'getGoalSelector():' in bee
    assert b'        noPhysics: boolean;' in arrow and b'isNoPhysics(): boolean;' in arrow and b'setNoPhysics(' in arrow
print(json.dumps(dict(archive='PASS', tests=5152, skipped=271, normalInstalledPasses=5,
    pythonStubs=1609, fullStrictTypeScript='FAIL', diagnostics=5549, removed=16, added=0)))
