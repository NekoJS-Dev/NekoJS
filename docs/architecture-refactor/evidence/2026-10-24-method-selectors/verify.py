"""Verify recorded selector observations; does not rerun Minecraft or sign human acceptance."""
import collections
from datetime import datetime
import hashlib
import io
import json
from pathlib import Path
import re
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
ARCHIVE_SHA = '57737df28bbcaf6643fa63c414d4656de4b54a43cf03ec25740c6727ab9a94d6'
PEER_SHA = '37a1e62646398c6998ffd36b0c59e4b97cc2dbaec56f2de3e9885ce735d39d82'
digest = lambda data: hashlib.sha256(data).hexdigest()
archive = HERE / 'evidence-v2.zip'
assert digest(archive.read_bytes()) == ARCHIVE_SHA
with zipfile.ZipFile(archive) as jar:
    files = {name: jar.read(name) for name in jar.namelist()}
assert len(files) == 35
for name, expected in json.loads(files['entries.json']).items():
    assert digest(files[name]) == expected, name
binding = json.loads(files['binding.json'])
assert binding['sourceRevision'] == '6e43b82789a7dbf7b9659dc1c21fefecac662649'
assert binding['maintainerAcceptance'] is False
peer = (HERE / binding['candidateArchive']).resolve()
assert digest(peer.read_bytes()) == PEER_SHA
with zipfile.ZipFile(peer) as outer:
    candidate = outer.read('artifact/nekojs-neoforge-26.1.2-1.1.0-preview3.jar')
assert digest(candidate) == binding['candidateSha256'] == '7b0dd1544837cf86ba35ccc4817da40cde54ff7c07544fc3b25fd5437027e8ae'
with zipfile.ZipFile(io.BytesIO(candidate)) as jar:
    count = len([n for n in jar.namelist() if n.startswith('com/tkisor/nekojs/') and n.endswith('.class')])
    assert count == binding['candidateClassCount'] == 1642
    for name, expected in binding['loadedProductionClasses'].items():
        assert digest(jar.read(name)) == expected
installed = json.loads(files['installed/result.json'])
assert installed['verdict'] == 'RECORDED' and installed['exitCode'] == 0 and installed['normalRconStop'] is True
assert installed['stop']['attempted'] is True and installed['stop']['responseReceived'] is True
assert installed['stop']['shutdownMarkersObserved'] is True
assert installed['stop']['response'].strip() == 'Stopping the server'
assert datetime.fromisoformat(installed['stop']['requestedUtc']) <= datetime.fromisoformat(installed['stop']['returnedUtc'])
assert digest(files['initial-stop-record/evidence.zip']) == 'dee4b31e60e29f1caea3276096e71ab8784838c7bd84b88e82ca0c0fd3b00e6e'
with zipfile.ZipFile(io.BytesIO(files['initial-stop-record/evidence.zip'])) as initial:
    unconfirmed = json.loads(initial.read('installed/result.json'))
    assert unconfirmed['normalRconStop'] is True and 'stop' not in unconfirmed
assert installed['artifactSha256'] == binding['candidateSha256']
stdout = files['installed/stdout.log'].decode('utf-8', errors='replace')
assert 'Stopping server' in stdout and 'Closing FML Loader' in stdout
for name, expected in binding['loadedProductionClasses'].items():
    fqn = name.removesuffix('.class').replace('/', '.')
    assert any(line.startswith(fqn + '|') and line.endswith('|' + expected) and
               '/mods/nekojs-neoforge-26.1.2-1.1.0-preview3.jar|' in line
               for line in installed['loadedBinding'].splitlines())
rows = {row['label']: row for row in installed['observations']}
assert len(rows) == 28
expected_values = {'field': 3, 'direct': 4, 'selector': 4, 'extracted': 4, 'int': 'int:2',
    'double': 'double:2.0', 'intNarrowing': 'int:2', 'primitiveArray': 2, 'referenceArray': 2,
    'binaryNested': 'nested', 'erased': 2, 'varargsArraySpelling': 'a/b', 'renamedDirect': 'renamed:2',
    'originalSelector': 'renamed:2', 'vectorField': 7, 'vectorSelector': 7,
    'matrixFieldBefore': 1, 'matrixGetter': 1, 'matrixSetter': True, 'matrixFieldAfter': 3, 'matrixExtracted': 3}
expected_rejections = {'canonicalNested', 'genericSpelling', 'varargsEllipsisSpelling', 'hiddenDirect',
                       'hiddenSelector', 'renamedSelector', 'originalDirect'}
assert set(rows) == set(expected_values) | expected_rejections
for name, expected in expected_values.items():
    assert rows[name]['success'] is True and rows[name]['value'] == expected, name
for name in expected_rejections:
    assert rows[name]['success'] is False and 'TypeError' in rows[name]['message'], name
marker = 'OWNED-SELECTOR-OBSERVATIONS '
logged = [json.loads(line.split(marker, 1)[1]) for line in stdout.splitlines() if marker in line]
assert logged == [installed['observations']]
standalone = json.loads(files['standalone/result.json'])
assert standalone['engineVersion'] == '25.1.3' and len(standalone['observations']) == 36
assert json.loads(files['standalone/binding.json'])['exitCodes'] == {'compile': 0, 'run': 0}
plain = {row['expression']: row for row in standalone['observations']}
assert plain['target.hidden()']['success'] is True
assert plain["target['hidden()']()"]['success'] is True
assert plain['target.renamed(2)']['success'] is False
assert plain["target['x()']()"]['value'] == 4
assert plain["Java.type('java.lang.String')"]['success'] is False
strict = json.loads(files['strict/strict-result.json'])
assert strict['exitCodes'] == {'before': 2, 'after': 0}
assert files['strict/before-tsc.log'].count(b'error TS2300') == 2
assert not files['strict/after-tsc.log']
assert b'TS2688' in files['initial-preparation-failure/after-tsc.log']
for name in ['before', 'after']:
    config = json.loads(files[f'strict/{name}-tsconfig.json'])
    assert config['compilerOptions']['strict'] is True and config['compilerOptions']['skipLibCheck'] is False
    assert config['compilerOptions']['types'] == []
inventory = json.loads(files['strict/collision-inventory.json'])
assert inventory['sourceRevision'] == binding['sourceRevision'] and inventory['diagnosticLocations'] == 1002
source_archive = HERE.parent / '2026-10-24-owner-stamp/evidence.zip'
assert digest(source_archive.read_bytes()) == '1fc7538fc9adc05adaec76f1727a17d1e1bfbf4dd82d65514182749ec7d101a8'
with zipfile.ZipFile(source_archive) as source_zip:
    source_files = {}
    for local_path, expected in inventory['sourceHashes'].items():
        local_path = local_path.replace('\\', '/')
        archived_path = local_path.replace('build/closeout-owner-stamp-installed/', 'installed/', 1)
        source_files[local_path] = source_zip.read(archived_path)
        assert digest(source_files[local_path]) == expected, archived_path
entities = inventory['entities']
assert len(entities) == 436
members = {(row['file'], row['owner'], row['member']) for row in entities}
assert len(members) == inventory['uniqueFieldMethodMembers'] == 96
assert len({(file, owner) for file, owner, member in members}) == 31
assert sum('/org/joml/' in file for file, owner, member in members) == 78
assert collections.Counter(row['node'] for row in entities) == inventory['nodeEntities']
for row in entities:
    escaped = re.escape(row['member'])
    assert any(re.match(r'(?:readonly )?' + escaped + r'\s*[?:]', d['text']) for d in row['declarations'])
    assert any(re.match(escaped + r'\s*[<(]', d['text']) for d in row['declarations'])
    source_path = f"build/closeout-owner-stamp-installed/{row['node']}/{row['file']}"
    lines = source_files[source_path].decode('utf-8').splitlines()
    for declaration in row['declarations']:
        assert lines[declaration['line'] - 1].strip() == declaration['text']
audit = json.loads(files['privacy-audit.json'])
assert audit['payloadsChecked'] == 72 and audit['retainedMatches'] == audit['redactedFiles'] == 0
for filename, expected in binding['installedSources'].items():
    assert digest(files['tools/' + filename]) == expected
local = ROOT / 'build/priority-closeout/installed-selector-261-v2/mods'
local_checked = 0
if local.exists():
    assert digest((local / 'nekojs-neoforge-26.1.2-1.1.0-preview3.jar').read_bytes()) == binding['candidateSha256']
    assert digest((local / 'graal-1504336-8762962.jar').read_bytes()) == binding['installedGraalSha256']
    assert digest((local / 'owned-selector-probe.jar').read_bytes()) == digest(files['installed/mods/owned-selector-probe.jar'])
    local_checked = 3
print(json.dumps(dict(verdict='PASS', scope='Recorded bounded observations, not full strict output or migration acceptance',
    observations=len(rows), expectedSuccesses=len(expected_values), expectedRejections=len(expected_rejections),
    currentCollisionMembers=len(members), normalServerExits=1, strictPrototype= strict['exitCodes'],
    localJarsChecked=local_checked, collisionSourceFilesChecked=len(source_files),
    initialStopClaim='UNCONFIRMED; retained, excluded from normal-stop count',
    privateScan='Bound receipt only; cannot repeat without private inputs',
    maintainerAcceptance=False), indent=2))
