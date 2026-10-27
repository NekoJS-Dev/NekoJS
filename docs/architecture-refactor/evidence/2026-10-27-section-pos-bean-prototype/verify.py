"""Verify the isolated SectionPos declaration proposal; production remains unchanged."""

import argparse
from collections import Counter
from hashlib import sha256
import json
from pathlib import Path
import re
import subprocess
import tempfile
from zipfile import ZipFile
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--typecheck', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parent
manifest = json.loads((root / 'manifest.json').read_text())
archive = root / 'artifacts.zip'
baseline = root / manifest['baselineArchive']
assert sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']
assert sha256(baseline.read_bytes()).hexdigest() == manifest['baselineSha256']


def blocks(data, entry, positions=False):
    location = r'(.*?\(\d+,\d+\))' if positions else r'(.*?)\(\d+,\d+\)'
    header = re.compile(r'^.*?\.neko_probe/typescript/' + location + r': error (TS\d+): (.*)$')
    result = []
    for line in data.decode().replace('\\', '/').splitlines():
        match = header.match(line)
        if match:
            result.append([(entry, *match.groups()), []])
        elif result:
            result[-1][1].append(line)
    return Counter((*key, '\n'.join(lines).rstrip()) for key, lines in result)


with ZipFile(archive) as zipped, ZipFile(baseline) as original:
    assert len(zipped.namelist()) == len(set(zipped.namelist())) == len(manifest['entries'])
    for entry in manifest['entries']:
        assert sha256(zipped.read(entry['path'])).hexdigest() == entry['sha256'], entry['path']
    proposal = json.loads(zipped.read('reports/proposal.json'))
    assert proposal['total'] == 973 and len(proposal['entries']) == 10
    assert len(proposal['changes']) == 5 and sum(item['removed'] for item in proposal['changes']) == 15
    for change in proposal['changes']:
        node = change['file'].split('/')[0]
        runtime = json.loads(zipped.read('runtime/' + node + '.json'))
        for axis, value in zip('xyz', [4, 5, 6]):
            assert runtime['memberReads'][1][axis] == {'kind': 'number', 'result': value}
            assert runtime['section.' + axis + '()'] == {'number': value}
            assert 'not a function' in runtime["section['" + axis + "()']()"]['error']
        assert runtime["section['getX()']()"] == {'number': 4}
        report = ET.fromstring(zipped.read('runtime/' + node + '-test.xml'))
        assert report.get('tests') == '1' and report.get('failures') == report.get('errors') == report.get('skipped') == '0'
        old = original.read('installed/inputs/' + change['file']).decode()
        def edit(match):
            return re.sub(r'^        [xyz]\(\): number;\r?\n', '', match[0], flags=re.M)
        expected = re.sub(r'    export (?:class|interface) \$SectionPos(?: |\{)[\s\S]*?\n    }', edit, old)
        assert expected.encode() == zipped.read('modified/' + node + '-core.d.ts')
    before, after = Counter(), Counter()
    for entry in proposal['entries']:
        name = entry['entry'] + '-tsc.txt'
        before.update(blocks(original.read('installed/ide/' + name), name))
        actual = blocks(zipped.read('diagnostics/' + name), name)
        assert sum(actual.values()) == entry['count'] and entry['exitCode'] == 2
        after.update(actual)
    assert sum(before.values()) == 997 and sum(after.values()) == 973
    assert sum((before-after).values()) == 24 and not (after-before)
    old = json.loads(original.read('installed/root-cause-mapping.json'))
    new = json.loads(zipped.read('reports/root-cause-mapping.json'))
    for family, value in old['families'].items():
        if family.startswith('F13-'):
            assert value['total'] == 36 and new['families'][family]['total'] == 12
        else:
            assert value == new['families'][family], family
    if args.typecheck:
        repo = root.parents[3]
        compiler = repo / 'node_modules/typescript/bin/tsc'
        assert subprocess.check_output(['node', str(compiler), '--version'], text=True).strip() == 'Version 5.8.3'
        target = Path(tempfile.mkdtemp(prefix='section-bean-replay-', dir=repo / 'build')).resolve()
        for name in original.namelist():
            if not name.startswith('installed/inputs/'):
                continue
            destination = (target / name[len('installed/inputs/'):]).resolve()
            assert destination.is_relative_to(target)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(original.read(name))
        for change in proposal['changes']:
            node = change['file'].split('/')[0]
            (target / change['file']).write_bytes(zipped.read('modified/' + node + '-core.d.ts'))
        for entry in proposal['entries']:
            stem = entry['entry']
            node, side = stem.rsplit('-', 1)
            config = json.loads(original.read('installed/ide/' + stem + '-tsconfig.json'))
            config['extends'] = str(target / node / 'nekojs' / (side + '_scripts') / 'jsconfig.json')
            path = target / (stem + '.json')
            path.write_text(json.dumps(config))
            result = subprocess.run(['node', str(compiler), '-p', str(path)], capture_output=True, timeout=180)
            assert result.returncode == 2 and not result.stderr
            assert blocks(result.stdout, stem, True) == blocks(zipped.read('diagnostics/' + stem + '-tsc.txt'), stem, True), stem
            print('REPLAY:', stem, entry['count'], flush=True)
        print('Artifacts:', target)
print('PASS: proposal evidence; production remains 997, proposed tree remains FAIL (973)')
