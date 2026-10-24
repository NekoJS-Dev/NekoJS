"""Verify installed signature observations and full scratch-program diagnostic comparisons."""
from collections import Counter, defaultdict
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
digest = lambda data: hashlib.sha256(data).hexdigest()
def archive(name, expected):
    payload = (HERE / name).read_bytes()
    assert digest(payload) == expected
    with zipfile.ZipFile(HERE / name) as jar:
        data = {entry: jar.read(entry) for entry in jar.namelist()}
    entries = json.loads(data['entries.json'])
    assert set(data) == set(entries) | {'entries.json'}
    for entry, expected_hash in entries.items():
        assert digest(data[entry]) == expected_hash
    return data
uniform = archive('evidence.zip', '04f75f03c329aee589cafff18510f94875f8478797625ea2d8e2e01b256e3eee')
preserved = archive('preserved-ordinary.zip', '8ce15f64138002ac7d6a0a49bae622570f2a502ff15ad1fc3407ae2abc980852')
assert len(uniform) == 177 and len(preserved) == 117
def read(data, name):
    return json.loads(data[name].decode('utf-8-sig'))
binding = read(uniform, 'binding.json')
assert binding['sourceRevision'] == '6e43b82789a7dbf7b9659dc1c21fefecac662649'
assert not binding['productionChanged'] and not binding['migrationApproved'] and not binding['maintainerAcceptance']
assert read(preserved, 'binding.json')['uniformEvidenceSha256'] == digest((HERE / 'evidence.zip').read_bytes())
peer_path = HERE.parent / '2026-10-24-owner-stamp/evidence.zip'
assert digest(peer_path.read_bytes()) == binding['sourceEvidenceSha256']
with zipfile.ZipFile(peer_path) as jar:
    peer = {name: jar.read(name) for name in jar.namelist()}
prep = json.loads(peer['installed/preparation-manifest.json'])
nodes = ['1.21.1', '26.1.2', '26.2.0', '26.1.2-fabric', '26.2.0-fabric']
runtime = read(uniform, 'runtime/summary.json')['records']
assert [row['node'] for row in runtime] == nodes and len(runtime) == 5
class_pairs = set()
structural_pairs = set()
structural_classes = set()
node_pairs = 0
source_files_checked = 0
local_jars = 0
for node, record in zip(nodes, runtime, strict=True):
    prefix = f'runtime/{node}/'
    assert record == read(uniform, prefix + 'result.json')
    assert record['verdict'] == 'RECORDED' and record['normalRconStop'] and record['exitCode'] == 0
    stop = record['stop']
    assert stop['attempted'] and stop['responseReceived'] and stop['shutdownMarkersObserved']
    assert stop['response'].strip() == 'Stopping the server'
    assert datetime.fromisoformat(stop['requestedUtc']) <= datetime.fromisoformat(stop['returnedUtc'])
    source = next(row for row in prep['nodes'] if row['node'] == node)
    assert record['candidateSha256'] == source['sha256']
    assert record['expectedIndexClassSha256'] == source['classBindings']['com/tkisor/nekojs/api/JavaMemberIndex.class']
    observed = read(uniform, prefix + 'signatures.json')
    assert observed['indexClassSha256'] == record['expectedIndexClassSha256']
    assert '/mods/' in observed['indexSource'] and source['artifact'] in observed['indexSource']
    log = uniform[prefix + 'stdout.log'].decode('utf-8', errors='replace')
    assert 'Stopping server' in log
    batches = []
    for line in log.splitlines():
        marker = 'OWNED-SELECTOR-SIGNATURES '
        if marker in line:
            match = re.fullmatch(r'(\d+) (.+)', line.split(marker, 1)[1].strip())
            assert match
            batches.append((int(match[1]), json.loads(match[2])))
    batches.sort()
    assert [offset for offset, _ in batches] == list(range(0, record['requestedPairs'], 20))
    assert [row for _, batch in batches for row in batch['rows']] == observed['rows']
    assert observed['batches'] == len(batches)
    request = read(uniform, prefix + 'request.json')
    assert len(observed['rows']) == len(request) == record['requestedPairs']
    assert [(row['className'], row['member']) for row in observed['rows']] == [(row['className'], row['member']) for row in request]
    for row in observed['rows']:
        assert row['status'] == 'MATCHED' and row['methods']
        for method in row['methods']:
            assert method['selector'] == method['javaName'] + '(' + ','.join(method['parameters']) + ')'
    graph = read(uniform, f'closure/{node}.json')
    for filename, expected in graph['sourceHashes'].items():
        assert digest(peer[f'installed/{node}/{filename}']) == expected
        source_files_checked += 1
    assert not graph['affectedUnknownParents']
    assert len(graph['selected']) == graph['structuralPairs']
    declarations = [row for row in graph['selected'] if row['methodDeclarations']]
    assert len(declarations) == graph['declarationPairs'] == record['requestedPairs']
    node_pairs += len(declarations)
    class_pairs.update((row['owner'], row['member']) for row in declarations)
    structural_pairs.update((row['owner'], row['member']) for row in graph['selected'])
    structural_classes.update(row['owner'] for row in graph['selected'])
    local = ROOT / 'build/priority-closeout/selector-signatures-installed-v4' / node / 'mods'
    if local.exists():
        assert digest((local / source['artifact']).read_bytes()) == source['sha256']
        assert digest((local / 'owned-selector-signatures.jar').read_bytes()) == digest(uniform[prefix + 'mods/owned-selector-signatures.jar'])
        local_jars += 2
assert node_pairs == 874 and len(class_pairs) == 208 and len(structural_pairs) == 221 and len(structural_classes) == 86
records = read(uniform, 'join/records.json')
assert len(records) == 1004 and not read(uniform, 'join/ambiguous.json')
for record in records:
    assert record['oldDeclaration'].startswith(record['member'] + '(')
    assert record['newDeclaration'] == json.dumps(record['selector']) + record['oldDeclaration'][len(record['member']):]
    assert {method['selector'] for method in record['javaMethods']} == {record['selector']}
    observed = read(uniform, f"runtime/{record['node']}/signatures.json")
    matching = next(row for row in observed['rows'] if row['className'] == record['className'] and row['member'] == record['member'])
    assert all(method in matching['methods'] for method in record['javaMethods'])
preservation = read(preserved, 'policy/summary.json')
policy_records = read(preserved, 'policy/records.json')
materializations = read(preserved, 'policy/materializations.json')
assert len(policy_records) == 1004 and len(materializations) == 1
assert Counter(row['mode'] for row in policy_records) == {'replace-ordinary-with-selector': 979, 'retain-ordinary-and-add-selector': 25}
assert preservation['ordinarySpellingReplacements'] == 979 and preservation['retainedOwnDeclarations'] == 25
assert preservation['inheritedMaterializations'] == 1
for before, after in zip(records, policy_records, strict=True):
    for key in before:
        if key != 'newDeclaration':
            assert before[key] == after[key]
    expected = before['newDeclaration'] if after['mode'] == 'replace-ordinary-with-selector' else before['oldDeclaration'] + ' ' + before['newDeclaration']
    assert after['newDeclaration'] == expected
assert materializations[0]['member'] == 'showNotification' and materializations[0]['newDeclaration'] == ' showNotification(): boolean;'
assert materializations[0]['node'] == '1.21.1' and materializations[0]['owner'].endswith('#$SmithingRecipe')
def diagnostics(data, prefix):
    rows = []
    for line in data.decode('utf-8-sig').splitlines():
        match = re.match(r'(.+)\((\d+),(\d+)\): error (TS\d+): (.+)', line)
        if match:
            filename, row, column, code, message = match.groups()
            filename = filename.replace('\\', '/')
            marker = prefix + '/'
            assert marker in filename
            rows.append((filename.split(marker, 1)[1], int(row), int(column), code, message))
    return rows
programs_checked = 0
external_inputs_checked = 0
for data, edits, prefix in [(uniform, records, 'build/priority-closeout/selector-declaration-prototype-v1'),
                             (preserved, policy_records + materializations, 'build/priority-closeout/selector-preserved-declaration-prototype-v1')]:
    summary = read(data, 'prototype/summary.json')
    assert summary['baseline'] == 5537 and summary['prototype'] == 1621 and summary['removed'] == 3916 and summary['added'] == 0
    assert not summary['sourceGeneratorChanged'] and summary['prototypeOnly'] and not summary['migrationApproved']
    assert len(summary['changedFiles']) == 44
    for changed in summary['changedFiles']:
        node, filename = changed['node'], changed['file']
        original = peer[f'installed/{node}/{filename}']
        assert digest(original) == changed['originalSha256']
        text = original.decode('utf-8')
        offsets = [0]
        for line in text.splitlines(keepends=True):
            offsets.append(offsets[-1] + len(line))
        spans = []
        for edit in edits:
            if edit['node'] == node and edit['file'] == filename:
                start = offsets[edit['line'] - 1] + edit['column'] - 1
                end = start + len(edit['oldDeclaration'])
                assert text[start:end] == edit['oldDeclaration']
                spans.append((start, end, edit['newDeclaration']))
        spans.sort()
        assert len(spans) == changed['edits']
        assert all(spans[i][1] <= spans[i + 1][0] for i in range(len(spans) - 1))
        for start, end, replacement in reversed(spans):
            text = text[:start] + replacement + text[end:]
        assert text.encode('utf-8') == data[f'prototype/{node}/{filename}']
        assert digest(text.encode('utf-8')) == changed['prototypeSha256']
    for node in nodes:
        changed = {'node/' + row['file']: row for row in summary['changedFiles'] if row['node'] == node}
        for side in ['server', 'startup']:
            p = f'prototype/{node}/{side}'
            cfg = read(data, p + '-tsconfig.json')
            original_cfg = read(data, p + '-original-tsconfig.json')
            assert cfg['compilerOptions'] == original_cfg['compilerOptions'] == {'skipLibCheck': False, 'noEmit': True}
            assert set(cfg) == set(original_cfg) == {'extends', 'compilerOptions'}
            inputs = read(data, p + '-program-inputs.json')
            assert inputs['verdict'] == 'PASS' and inputs['identicalEffectiveOptions']
            assert inputs['missingInputs'] == inputs['unrelatedChanges'] == 0
            assert inputs['options']['strict'] is True and inputs['options']['skipLibCheck'] is False and inputs['options']['noEmit'] is True
            assert set(inputs['originalSources']) == set(inputs['prototypeSources'])
            assert len(inputs['originalSources']) == inputs['sourceFiles']
            for identity, expected in inputs['originalSources'].items():
                after = inputs['prototypeSources'][identity]
                if identity.startswith('node/'):
                    assert digest(peer[f'installed/{node}/{identity[5:]}']) == expected
                    assert after == (changed[identity]['prototypeSha256'] if identity in changed else expected)
                else:
                    assert '/node_modules/typescript/lib/' in identity and expected == after
                    local = ROOT / 'node_modules/typescript/lib' / identity.rsplit('/', 1)[1]
                    if local.exists():
                        assert digest(local.read_bytes()) == expected
                        external_inputs_checked += 1
            old = diagnostics(peer[f'installed/ide/{node}-{side}-tsc.txt'], 'build/closeout-owner-stamp-installed')
            new = diagnostics(data[p + '-tsc.txt'], prefix)
            comparison = read(data, p + '-comparison.json')
            removed = list((Counter(old) - Counter(new)).elements())
            added = list((Counter(new) - Counter(old)).elements())
            assert comparison['exitCode'] == 2
            assert (len(old), len(new), len(removed), len(added)) == (comparison['baseline'], comparison['prototype'], comparison['removed'], comparison['added'])
            assert [list(row) for row in removed] == comparison['removedDiagnostics']
            assert [list(row) for row in added] == comparison['addedDiagnostics'] == []
            programs_checked += 1
for data in [uniform, preserved]:
    privacy = read(data, 'privacy-audit.json')
    assert privacy['retainedMatches'] == privacy['redactedFiles'] == privacy['replacements'] == 0
    assert read(data, 'redactions.json') == {'originalSha256': {}, 'ranges': {}}
truncated = read(uniform, 'initial-truncated-run/result.json')
assert truncated['verdict'] == 'NOTGREEN' and truncated['exitCode'] == 0 and truncated['normalRconStop']
print(json.dumps(dict(verdict='PASS', scope='Installed signature observations and scratch prototypes; no production migration',
    ownDeclarationPairs=874, memberFamilies=208, structuralMemberFamilies=221, structuralClasses=86,
    signatures=1004, ordinarySpellingReplacements=979, retainedOwnDeclarations=25, inheritedMaterializations=1,
    normalSuccessfulServerExits=5, initialIncompleteServerExits=1, prototypePrograms=programs_checked,
    baselineDiagnostics=5537, prototypeDiagnostics=1621, removed=3916, added=0,
    closureSourceFilesChecked=source_files_checked, localRuntimeJarsChecked=local_jars,
    localExternalInputChecks=external_inputs_checked, productionChanged=False, migrationApproved=False,
    privateScan='Receipt only; cannot repeat without private inputs'), indent=2))
