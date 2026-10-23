"""Reclassify baseline duplicates from its sealed declarations, without a local installed server."""
import collections
import hashlib
import json
from pathlib import Path
import re
import zipfile

root = Path(__file__).resolve().parent
baseline = root.parent / '2026-10-24-method-bounds'
manifest = json.loads((baseline / 'manifest.json').read_text())
assert hashlib.sha256((baseline / 'evidence.zip').read_bytes()).hexdigest() == manifest['archiveSha256']
expected = json.loads((root / 'ts2300-baseline.json').read_text())
entities = {}
diagnostic_count = 0
with zipfile.ZipFile(baseline / 'evidence.zip') as archive:
    logs = sorted(name for name in archive.namelist() if name.startswith('installed/ide/') and name.endswith('-server-tsc.txt'))
    assert len(logs) == 5
    for name in logs:
        node = Path(name).name.removesuffix('-server-tsc.txt')
        for raw in archive.read(name).decode('utf-8-sig').splitlines():
            match = re.match(r"(.+)\((\d+),(\d+)\): error TS2300: Duplicate identifier '([^']+)'\.", raw)
            if not match:
                continue
            path, line, column, member = match.groups()
            relative = path.replace('\\', '/').split('/closeout-method-bounds-installed/', 1)[1]
            assert relative.startswith(node + '/')
            relative = relative[len(node) + 1:]
            lines = archive.read('installed/' + node + '/' + relative).decode('utf8').splitlines()
            line = int(line)
            text = lines[line - 1]
            if text.lstrip().startswith('import '):
                owner, kind = '', 'import'
            else:
                owners = [found.group(1) for candidate in lines[:line]
                          if (found := re.match(r'\s*export (?:abstract )?(?:class|interface|enum) ([\w$]+)', candidate))]
                assert owners, (path, line)
                owner, kind = owners[-1], 'field-method'
            key = (node, relative, owner, member)
            row = entities.setdefault(key, dict(node=node, file=relative, owner=owner, member=member, kind=kind, declarations=[]))
            row['declarations'].append(dict(line=line, column=int(column), text=text.strip()))
            diagnostic_count += 1
rows = sorted(entities.values(), key=lambda row: (row['node'], row['file'], row['owner'], row['member']))
assert rows == expected['entities']
assert diagnostic_count == expected['diagnosticLocations'] == 1006
assert len(rows) == 438 and collections.Counter(row['kind'] for row in rows) == {'field-method': 436, 'import': 2}
members = {(row['file'], row['owner'], row['member']) for row in rows if row['kind'] == 'field-method'}
assert len(members) == 96 and len({(file, owner) for file, owner, _ in members}) == 31
assert sum('/org/joml/' in file for file, _, _ in members) == 78
assert expected['nodeEntities'] == dict(collections.Counter(row['node'] for row in rows))
for row in rows:
    if row['kind'] == 'field-method':
        member = re.escape(row['member'])
        assert any(re.match(r'(?:readonly )?' + member + r'\s*[?:]', declaration['text']) for declaration in row['declarations'])
        assert any(re.match(member + r'\s*[<(]', declaration['text']) for declaration in row['declarations'])
print(json.dumps(dict(baseline='975496d3', duplicateLocations=1006, fieldMethodEntities=436,
    importEntities=2, uniqueFieldMethodMembers=96, uniqueClasses=31, jomlMembers=78,
    scope='Declaration shape classification; does not prove runtime behavior or authorize migration.')))
