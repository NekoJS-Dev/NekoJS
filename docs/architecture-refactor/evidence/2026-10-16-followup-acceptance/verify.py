"""Check archived hashes and the recorded runtime boundaries without writing files."""

import ast
from collections import Counter
import hashlib
import json
import re
import statistics
import zipfile
from pathlib import Path


def checked_archive(directory, prefix):
    manifest = json.loads((directory / (prefix + '-manifest.json')).read_text(encoding='utf-8'))
    archive = directory / manifest['archive']
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == manifest['sha256']
    with zipfile.ZipFile(archive) as evidence:
        assert len(evidence.namelist()) == len(manifest['entries'])
        assert set(evidence.namelist()) == set(manifest['entries'])
        entries = {name:evidence.read(name) for name in evidence.namelist()}
    for name, expected in manifest['entries'].items():
        assert hashlib.sha256(entries[name]).hexdigest() == expected, name
    return entries


def python_declaration_facts(data):
    module = ast.parse(data)
    imports = {}
    for statement in module.body:
        if isinstance(statement, ast.ImportFrom):
            for alias in statement.names:
                imports[alias.asname or alias.name] = '.' * statement.level + (statement.module or '') + '.' + alias.name
    # The archived stub modules have no local shadowing of imported type names.
    for node in ast.walk(module):
        if isinstance(node, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
            assert node.name not in imports
        if isinstance(node, ast.arg):
            assert node.arg not in imports
        if isinstance(node, ast.Name) and isinstance(node.ctx, ast.Store):
            assert node.id not in imports

    class CanonicalImports(ast.NodeTransformer):
        def visit_Name(self, node):
            if isinstance(node.ctx, ast.Load) and node.id in imports:
                return ast.Name(id=imports[node.id], ctx=node.ctx)
            return node

        def visit_ImportFrom(self, node):
            return ast.ImportFrom(module=node.module, level=node.level,
                                  names=[ast.alias(name=alias.name) for alias in node.names])

    module = CanonicalImports().visit(module)
    statements = []
    exports = []
    last_annotations = {}
    for statement in module.body:
        if isinstance(statement, ast.Assign) and any(isinstance(t, ast.Name) and t.id == '__all__' for t in statement.targets):
            exports.extend(ast.literal_eval(statement.value))
        else:
            statements.append(ast.dump(statement))
        if isinstance(statement, ast.AnnAssign) and isinstance(statement.target, ast.Name):
            last_annotations[statement.target.id] = ast.dump(statement.annotation)
    return Counter(statements), Counter(exports), last_annotations


def check_enum_evidence(directory, reviewed):
    original = b'from host.item import Event\nfrom host.player import Event as Other\nclass Events:\n def item(self, value: Event) -> None: ...\n def player(self, value: Other) -> None: ...\n'
    renamed = b'from host.player import Event\nfrom host.item import Event as Other\nclass Events:\n def item(self, value: Other) -> None: ...\n def player(self, value: Event) -> None: ...\n'
    incorrect = renamed.replace(b'value: Other', b'value: Event')
    assert python_declaration_facts(original) == python_declaration_facts(renamed)
    assert python_declaration_facts(original) != python_declaration_facts(incorrect)
    assert python_declaration_facts(b'x: int\nx: str\n') != python_declaration_facts(b'x: str\nx: int\n')
    entries = checked_archive(directory, 'enum')
    preparation = json.loads(entries['preparation-manifest.json'])
    summary = json.loads(entries['enum-summary.json'])
    assert preparation['sourceRevision'].startswith('489b4a6b')
    assert summary['sourceRevision'] == preparation['sourceRevision']
    assert hashlib.sha256(entries['production-source.patch']).hexdigest() == preparation['productionPatchSha256']
    assert summary['testTotals'] == dict(tests=4909, failures=0, errors=0, skipped=271)
    assert len(preparation['nodes']) == len(summary['ide']['nodes']) == 5
    for node in preparation['nodes']:
        prefix = 'installed/' + node['node'] + '/'
        assert json.loads(entries[prefix + 'runtime-verdict.json'])['verdict'] == 'PASS'
        assert json.loads(entries[prefix + 'server-exit.json'])['exitCode'] == 0
        assert node['classBindings'] and len(node['sha256']) == 64
        invalid_prefix = 'first-invalid/' + node['node'] + '/'
        invalid = json.loads(entries[invalid_prefix + 'runtime-verdict.json'])
        assert json.loads(entries[invalid_prefix + 'server-exit.json'])['exitCode'] == 0
        if not node['node'].endswith('-fabric'):
            assert invalid['verdict'] != 'PASS'
            assert invalid['errors'] == ['No current trade observation for declared']
            assert any(name.endswith('/domain-closeout-trades.js') for name in node['fixtureHashes'])
    for node in summary['ide']['nodes']:
        pyright = node['pyright']
        assert pyright['exitCode'] == 0 and pyright['summary']['errorCount'] == pyright['summary']['warningCount'] == 0
        assert pyright['summary']['filesAnalyzed'] == pyright['stubCount']
        for side in node['typescript']:
            assert side['skipLibCheck'] is False and side['defaultEditorIncludes']
            assert side['diagnosticCount'] == sum(side['codes'].values())
            assert side['exitCode'] != 0 and side['diagnosticCount'] > 0
    for row in summary['comparisons']:
        assert not row['pythonByteIdentical']
        assert row['javaClassStubsByteIdentical']
        assert row['pythonBefore'].keys() == row['pythonAfter'].keys()
        prefix = 'installed/' + row['node'] + '/.neko_probe/python/'
        for path, expected in row['pythonAfter'].items():
            assert hashlib.sha256(entries[prefix + path]).hexdigest() == expected
            assert hashlib.sha256(reviewed[prefix + path]).hexdigest() == row['pythonBefore'][path]
            if path.startswith('nekojs/_java/'):
                assert row['pythonBefore'][path] == expected
        for changed in row['changedFiles']:
            path = changed['path']
            assert path == 'nekojs/__init__.pyi' or path.startswith('nekojs/_events/')
            before = entries['python-before/' + row['node'] + '/' + path]
            after = entries[prefix + path]
            assert hashlib.sha256(before).hexdigest() == changed['beforeSha256']
            assert hashlib.sha256(after).hexdigest() == changed['afterSha256']
            assert before != after and python_declaration_facts(before) == python_declaration_facts(after)
    diagnostic_comparison = json.loads(entries['ide/diagnostic-comparison.json'])
    assert len(diagnostic_comparison) == 5
    assert sum(item['count'] for row in diagnostic_comparison for side in row['sides'] for item in side['removed']) == 160
    assert all(not side['added'] for row in diagnostic_comparison for side in row['sides'])
    strict = json.loads(entries['checks/strict-results.json'])
    assert len(strict) == 6
    for row in strict:
        log = entries['checks/' + row['log']]
        assert hashlib.sha256(log).hexdigest() == row['sha256']
        assert (row['exitCode'] == 0) == (row['mode'] == 'green')
        prefix = 'fixtures/' + row['mode'] + '/' + row['case'] + '/'
        for path, expected in row['fixtureHashes'].items():
            assert hashlib.sha256(entries[prefix + path]).hexdigest() == expected


def check_generic_evidence(directory):
    entries = checked_archive(directory, 'generic')
    previous = checked_archive(directory, 'enum')
    preparation = json.loads(entries['preparation-manifest.json'])
    summary = json.loads(entries['generic-summary.json'])
    assert preparation['sourceRevision'].startswith('f3f32a69')
    assert summary['sourceRevision'] == preparation['sourceRevision']
    assert hashlib.sha256(entries['production-source.patch']).hexdigest() == preparation['productionPatchSha256']
    assert len(preparation['sourceHashes']) == 7
    assert summary['testTotals'] == dict(tests=4913, failures=0, errors=0, skipped=271)
    assert not summary['fullTscPassed'] and not summary['releaseApproved']
    assert not summary['noNewDiagnostics']
    assert len(preparation['nodes']) == len(summary['ide']['nodes']) == 5
    for node in preparation['nodes']:
        prefix = 'installed/' + node['node'] + '/'
        assert json.loads(entries[prefix + 'runtime-verdict.json'])['verdict'] == 'PASS'
        assert json.loads(entries[prefix + 'server-exit.json'])['exitCode'] == 0
        assert node['classBindings'] and len(node['sha256']) == 64
    for node in summary['ide']['nodes']:
        pyright = node['pyright']
        assert pyright['exitCode'] == 0
        assert pyright['summary']['errorCount'] == pyright['summary']['warningCount'] == 0
        assert pyright['summary']['filesAnalyzed'] == pyright['stubCount']
        for side in node['typescript']:
            assert not side['skipLibCheck'] and side['defaultEditorIncludes']
            assert side['exitCode'] != 0 and side['diagnosticCount'] > 0
            data = entries['ide/' + node['node'] + '-' + side['side'] + '-tsc.txt'].decode('utf-8-sig')
            assert side['diagnosticCount'] == sum(side['codes'].values()) == len(re.findall(r'error TS\d+:', data))
    for row in summary['comparisons']:
        assert row['javaClassStubsByteIdentical'] and row['pythonCanonicalFactsEqual']
        assert row['pythonBefore'].keys() == row['pythonAfter'].keys()
        prefix = 'installed/' + row['node'] + '/.neko_probe/python/'
        changed = []
        for path, expected in row['pythonAfter'].items():
            before = previous[prefix + path]
            after = entries[prefix + path]
            assert hashlib.sha256(before).hexdigest() == row['pythonBefore'][path]
            assert hashlib.sha256(after).hexdigest() == expected
            if path.startswith('nekojs/_java/'):
                assert before == after
            if before != after:
                changed.append(path)
                assert path == 'nekojs/__init__.pyi' or path.startswith('nekojs/_events/')
                assert python_declaration_facts(before) == python_declaration_facts(after)
        assert sorted(changed) == sorted(row['changedPythonFiles'])
        assert row['pythonByteIdentical'] == (not changed)

    def diagnostics(data, prefix):
        result = Counter()
        text = data.decode('utf-8-sig')
        for line in text.splitlines():
            match = re.match(r'(.+?)\(\d+,\d+\): error (TS\d+): (.*)', line)
            if match:
                path, code, message = match.groups()
                result[(path.removeprefix(prefix), code, message)] += 1
            elif re.match(r'error TS\d+:', line):
                result[('<global>', *re.match(r'error (TS\d+): (.*)', line).groups())] += 1
        assert sum(result.values()) == len(re.findall(r'error TS\d+:', text))
        return result

    comparison = json.loads(entries['ide/diagnostic-comparison.json'])
    assert len(comparison) == 5
    removed_count = 0
    added_count = 0
    for row in comparison:
        for side in row['sides']:
            name = row['node'] + '-' + side['side'] + '-tsc.txt'
            before = diagnostics(entries['ide-before/' + name], 'build/domain-enum-heritage-installed-2/' + row['node'] + '/')
            after = diagnostics(entries['ide/' + name], 'build/domain-generic-adapter-installed/' + row['node'] + '/')
            added, removed = after - before, before - after
            recorded_added = Counter({(item['file'], item['code'], item['message']):item['count'] for item in side['added']})
            assert recorded_added == added
            assert all(code == 'TS2344' for path, code, message in added)
            added_count += sum(added.values())
            recorded = Counter({(item['file'], item['code'], item['message']):item['count'] for item in side['removed']})
            assert recorded == removed
            removed_count += sum(removed.values())
    assert summary['diagnosticComparison'] == dict(added=added_count, removed=removed_count)
    assert added_count == 930 and removed_count == 40
    strict = json.loads(entries['checks/strict-results.json'])
    assert len(strict) == 6
    for row in strict:
        data = entries['checks/' + row['log']]
        assert hashlib.sha256(data).hexdigest() == row['sha256']
        assert row['exitCode'] == (0 if row['mode'] == 'green' else 2)
        if row['mode'] == 'green':
            assert not data
        else:
            assert b'TS2314' in data and b'TS2578' in data
        prefix = 'fixtures/' + row['mode'] + '/' + row['case'] + '/'
        for path, expected in row['fixtureHashes'].items():
            assert hashlib.sha256(entries[prefix + path]).hexdigest() == expected


def check_resolver_evidence(directory):
    entries = checked_archive(directory, 'resolver')
    previous = checked_archive(directory, 'generic')
    preparation = json.loads(entries['preparation-manifest.json'])
    summary = json.loads(entries['resolver-summary.json'])
    assert preparation['sourceRevision'].startswith('6faed0e9')
    assert summary['sourceRevision'] == preparation['sourceRevision']
    assert hashlib.sha256(entries['production-source.patch']).hexdigest() == preparation['productionPatchSha256']
    assert len(preparation['sourceHashes']) == 1
    assert summary['testTotals'] == dict(tests=4916, failures=0, errors=0, skipped=271)
    assert not summary['fullTscPassed'] and not summary['releaseApproved']
    assert summary['noNewDiagnostics']
    assert len(preparation['nodes']) == len(summary['ide']['nodes']) == 5
    for node in preparation['nodes']:
        prefix = 'installed/' + node['node'] + '/'
        assert json.loads(entries[prefix + 'runtime-verdict.json'])['verdict'] == 'PASS'
        assert json.loads(entries[prefix + 'server-exit.json'])['exitCode'] == 0
        assert node['classBindings'] and len(node['sha256']) == 64
    for node in summary['ide']['nodes']:
        pyright = node['pyright']
        assert pyright['exitCode'] == 0
        assert pyright['summary']['errorCount'] == pyright['summary']['warningCount'] == 0
        assert pyright['summary']['filesAnalyzed'] == pyright['stubCount']
        for side in node['typescript']:
            assert not side['skipLibCheck'] and side['defaultEditorIncludes']
            assert side['exitCode'] != 0 and side['diagnosticCount'] > 0
            data = entries['ide/' + node['node'] + '-' + side['side'] + '-tsc.txt'].decode('utf-8-sig')
            assert side['diagnosticCount'] == sum(side['codes'].values()) == len(re.findall(r'error TS\d+:', data))
    for row in summary['comparisons']:
        assert row['javaClassStubsByteIdentical'] and row['pythonCanonicalFactsEqual']
        assert row['pythonBefore'].keys() == row['pythonAfter'].keys()
        prefix = 'installed/' + row['node'] + '/.neko_probe/python/'
        changed = []
        for path, expected in row['pythonAfter'].items():
            before = previous[prefix + path]
            after = entries[prefix + path]
            assert hashlib.sha256(before).hexdigest() == row['pythonBefore'][path]
            assert hashlib.sha256(after).hexdigest() == expected
            if path.startswith('nekojs/_java/'):
                assert before == after
            if before != after:
                changed.append(path)
                assert path == 'nekojs/__init__.pyi' or path.startswith('nekojs/_events/')
                assert python_declaration_facts(before) == python_declaration_facts(after)
        assert sorted(changed) == sorted(row['changedPythonFiles'])
        assert row['pythonByteIdentical'] == (not changed)

    def diagnostics(data, prefix):
        result = Counter()
        text = data.decode('utf-8-sig')
        for line in text.splitlines():
            match = re.match(r'(.+?)\(\d+,\d+\): error (TS\d+): (.*)', line)
            if match:
                path, code, message = match.groups()
                result[(path.removeprefix(prefix), code, message)] += 1
            elif re.match(r'error TS\d+:', line):
                result[('<global>', *re.match(r'error (TS\d+): (.*)', line).groups())] += 1
        assert sum(result.values()) == len(re.findall(r'error TS\d+:', text))
        return result

    comparison = json.loads(entries['ide/diagnostic-comparison.json'])
    assert len(comparison) == 5
    removed_count = 0
    added_count = 0
    for row in comparison:
        for side in row['sides']:
            name = row['node'] + '-' + side['side'] + '-tsc.txt'
            before = diagnostics(entries['ide-before/' + name], 'build/domain-generic-adapter-installed/' + row['node'] + '/')
            after = diagnostics(entries['ide/' + name], 'build/domain-resolver-installed/' + row['node'] + '/')
            added, removed = after - before, before - after
            recorded_added = Counter({(item['file'], item['code'], item['message']):item['count'] for item in side['added']})
            assert recorded_added == added
            assert all(code == 'TS2344' for path, code, message in added)
            added_count += sum(added.values())
            recorded = Counter({(item['file'], item['code'], item['message']):item['count'] for item in side['removed']})
            assert recorded == removed
            removed_count += sum(removed.values())
    assert summary['diagnosticComparison'] == dict(added=added_count, removed=removed_count)
    assert added_count == removed_count == 0
    component = summary['component']
    measured = json.loads(entries['component/result-actual.json'])
    assert component['diagnosticOnly'] and measured['diagnosticOnly']
    assert measured['identityCases'] == 6 and measured['rejectionCases'] == 5 and measured['symlinkChecked']
    pairs = measured['pairs']
    assert len(pairs) == 10 and [r['baselineFirst'] for r in pairs] == [i % 2 == 0 for i in range(10)]
    scale = measured['repeatsPerArm'] * 1e6
    baseline = statistics.mean(r['baselineNs'] / scale for r in pairs)
    candidate = statistics.mean(r['candidateNs'] / scale for r in pairs)
    assert abs(component['baselineMeanMsPerSet'] - baseline) < 1e-9
    assert abs(component['candidateMeanMsPerSet'] - candidate) < 1e-9
    assert abs(component['meanSavingMsPerSet'] - (baseline - candidate)) < 1e-9
    assert hashlib.sha256(entries['component/baseline-source.java.txt']).hexdigest() == component['baselineBinding']['sourceSha256']
    assert hashlib.sha256(entries['component/baseline-resolver.class.bin']).hexdigest() == component['baselineBinding']['classSha256']
    judgment = json.loads(entries['performance/judgment.json'])
    assert judgment == summary['performance'] and judgment['complete'] and judgment['verdict'] == 'FAIL'
    assert [r['group'] for r in judgment['groups']] == ['reload-1', 'reload-2', 'startup']
    for group in judgment['groups']:
        prefix = 'performance/formal/' + group['group'] + '/'
        rows = [json.loads(line) for line in entries[prefix + 'samples.jsonl'].decode('utf-8-sig').splitlines()]
        formal = [r for r in rows if r['kind'] == 'formal']
        assert len(formal) == 5 and all(r['rev'].startswith('6faed0e9') for r in formal)
        assert all(not r.get('timeout') and not r['forced_kill'] and not r['killed'] and r['stop_channel'] == 'rcon' for r in rows)
        assert group['values'] == [r[group['measure']] for r in formal]
        assert group['mean'] == statistics.mean(group['values'])
        startup = group['group'] == 'startup'
        assert group['limit'] == (41173 if startup else 285.3)
        assert group['verdict'] == ('PASS' if group['mean'] <= group['limit'] else 'FAIL')
        if startup:
            assert len([r for r in rows if r['kind'] == 'warmup']) == 2
        else:
            assert all('phase=COMMIT - no errors' in r['rcon_response'] for r in formal)
    frozen = json.loads(entries['performance/frozen-classes.json'])
    primary = next(r for r in preparation['nodes'] if r['node'] == '26.1.2')
    assert frozen['classesMatchReviewedArtifact'] and frozen['classBindings'] == primary['classBindings']
    assert frozen['reviewedArtifactSha256'] == primary['sha256']
    assert frozen['classBindings']['com/tkisor/nekojs/core/module/NekoModuleResolver.class'] == component['candidateClassSha256']
    perf_preparation = json.loads(entries['performance/preparation.json'])
    assert perf_preparation['samplerSha256'] == '1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4'
    prefix = 'signature-diagnostic/'
    invalid = json.loads(entries[prefix + 'runtime-verdict.json'])
    assert invalid['verdict'] != 'PASS' and invalid['errors'] == ['No current trade observation for declared']
    assert json.loads(entries[prefix + 'server-exit.json'])['exitCode'] == 0
    markers = [json.loads(line.split(b'OWNED-DATA-SIGNATURES=', 1)[1]) for line in entries[prefix + 'server-stdout.log'].splitlines() if b'OWNED-DATA-SIGNATURES=' in line]
    assert len(markers) == 6 and len({r['host'] for r in markers}) == 3
    for row in markers:
        actual = [m for m in row['reflection'] if not m['bridge']]
        assert len(actual) == 1 and actual[0]['genericReturnType'].endswith('.AttachedData')
        assert row['ir'] and all(m['sourceType'].endswith('.AttachedData') and not m['overridden'] for m in row['ir'])


def python_return_contracts(data):
    module = ast.parse(data)
    imports = {}
    for statement in module.body:
        if isinstance(statement, ast.ImportFrom):
            for alias in statement.names:
                imports[alias.asname or alias.name] = '.' * statement.level + (statement.module or '') + '.' + alias.name

    class CanonicalImports(ast.NodeTransformer):
        def visit_Name(self, node):
            if isinstance(node.ctx, ast.Load) and node.id in imports:
                return ast.Name(id=imports[node.id], ctx=node.ctx)
            return node

        def visit_ImportFrom(self, node):
            return None

    module = CanonicalImports().visit(module)
    returns = {}
    for cls in module.body:
        if not isinstance(cls, ast.ClassDef):
            continue
        for method in cls.body:
            if not isinstance(method, ast.FunctionDef):
                continue
            key = (cls.name, method.name, ast.dump(method.args), tuple(ast.dump(d) for d in method.decorator_list))
            assert key not in returns
            returns[key] = ast.unparse(method.returns)
            method.returns = ast.Name(id='RETURN_CONTRACT', ctx=ast.Load())
    return ast.dump(module), returns


def check_return_contract_evidence(directory):
    entries = checked_archive(directory, 'return-contract')
    previous = checked_archive(directory, 'resolver')
    preparation = json.loads(entries['preparation-manifest.json'])
    summary = json.loads(entries['return-contract-summary.json'])
    assert preparation['sourceRevision'].startswith('569376c6')
    assert summary['sourceRevision'] == preparation['sourceRevision']
    assert hashlib.sha256(entries['production-source.patch']).hexdigest() == preparation['productionPatchSha256']
    assert len(preparation['sourceHashes']) == 1
    assert summary['testTotals'] == dict(tests=4921, failures=0, errors=0, skipped=271)
    assert not summary['fullTscPassed'] and not summary['releaseApproved']
    assert summary['noNewDiagnostics']
    assert len(preparation['nodes']) == len(summary['ide']['nodes']) == 5
    for node in preparation['nodes']:
        prefix = 'installed/' + node['node'] + '/'
        assert json.loads(entries[prefix + 'runtime-verdict.json'])['verdict'] == 'PASS'
        assert json.loads(entries[prefix + 'server-exit.json'])['exitCode'] == 0
        assert node['classBindings'] and len(node['sha256']) == 64
    for node in summary['ide']['nodes']:
        pyright = node['pyright']
        assert pyright['exitCode'] == 0
        assert pyright['summary']['errorCount'] == pyright['summary']['warningCount'] == 0
        assert pyright['summary']['filesAnalyzed'] == pyright['stubCount']
        for side in node['typescript']:
            assert not side['skipLibCheck'] and side['defaultEditorIncludes']
            assert side['exitCode'] != 0 and side['diagnosticCount'] > 0
            data = entries['ide/' + node['node'] + '-' + side['side'] + '-tsc.txt'].decode('utf-8-sig')
            assert side['diagnosticCount'] == sum(side['codes'].values()) == len(re.findall(r'error TS\d+:', data))
    shared_returns = {
        ('Connection', 'fabric_getPendingChannelsNames'): 'nekojs._java.net.minecraft.resources.Identifier',
        ('Connection', 'fabric_getSyncedRecipeSerializers'): 'nekojs._java.net.minecraft.world.item.crafting.RecipeSerializer',
        ('RegistryFriendlyByteBuf', 'fabric_getSendableConfigurationChannels'): 'nekojs._java.net.minecraft.resources.Identifier',
        ('RecipeMap', 'fabric_getRecipesBySyncedSerializer'): 'RecipeHolder',
        ('Level', 'fabric_getLoadedChunks'): 'nekojs._java.net.minecraft.world.level.chunk.LevelChunk',
    }
    modern_returns = {
        ('BlockEntityType', 'blocks'): 'nekojs._java.net.minecraft.world.level.block.Block',
        ('LootPool', 'fabric_getConditions'): 'nekojs._java.net.minecraft.world.level.storage.loot.predicates.LootItemCondition',
        ('LootPool', 'fabric_getEntries'): 'nekojs._java.net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer',
        ('LootPool', 'fabric_getFunctions'): 'nekojs._java.net.minecraft.world.level.storage.loot.functions.LootItemFunction',
        ('LootTable', 'fabric_getFunctions'): 'nekojs._java.net.minecraft.world.level.storage.loot.functions.LootItemFunction',
        ('LootTable', 'fabric_getPools'): 'LootPool',
    }
    for row in summary['comparisons']:
        changed_returns = {}
        assert row['pythonBefore'].keys() == row['pythonAfter'].keys()
        prefix = 'installed/' + row['node'] + '/.neko_probe/python/'
        changed = []
        for path, expected in row['pythonAfter'].items():
            before = previous[prefix + path]
            after = entries[prefix + path]
            assert hashlib.sha256(before).hexdigest() == row['pythonBefore'][path]
            assert hashlib.sha256(after).hexdigest() == expected
            if before != after:
                changed.append(path)
                if path.startswith('nekojs/_java/'):
                    old_shape, old_returns = python_return_contracts(before)
                    new_shape, new_returns = python_return_contracts(after)
                    assert old_shape == new_shape and old_returns.keys() == new_returns.keys()
                    for key in old_returns:
                        if old_returns[key] != new_returns[key]:
                            assert old_returns[key] in {'nekojs._java.java.util.Collection', 'nekojs._java.java.util.Set', 'nekojs._java.java.util.List'}
                            changed_returns[key[:2]] = new_returns[key]
                else:
                    assert path == 'nekojs/__init__.pyi' or path.startswith('nekojs/_events/')
                    assert python_declaration_facts(before) == python_declaration_facts(after)
        assert sorted(changed) == sorted(row['changedPythonFiles'])
        assert row['pythonByteIdentical'] == (not changed)
        expected = shared_returns if row['node'] == '26.1.2-fabric' else (shared_returns | modern_returns if row['node'] == '26.2.0-fabric' else {})
        assert changed_returns == {key:'list[' + element + ']' for key, element in expected.items()}
        assert row['javaClassStubsByteIdentical'] == (not row['node'].endswith('-fabric'))

    def diagnostics(data, prefix):
        result = Counter()
        text = data.decode('utf-8-sig')
        for line in text.splitlines():
            match = re.match(r'(.+?)\(\d+,\d+\): error (TS\d+): (.*)', line)
            if match:
                path, code, message = match.groups()
                result[(path.removeprefix(prefix), code, message)] += 1
            elif re.match(r'error TS\d+:', line):
                result[('<global>', *re.match(r'error (TS\d+): (.*)', line).groups())] += 1
        assert sum(result.values()) == len(re.findall(r'error TS\d+:', text))
        return result

    comparison = json.loads(entries['ide/diagnostic-comparison.json'])
    assert len(comparison) == 5
    removed_count = 0
    added_count = 0
    for row in comparison:
        for side in row['sides']:
            name = row['node'] + '-' + side['side'] + '-tsc.txt'
            before = diagnostics(entries['ide-before/' + name], 'build/domain-resolver-installed/' + row['node'] + '/')
            after = diagnostics(entries['ide/' + name], 'build/domain-return-contract-installed/' + row['node'] + '/')
            added, removed = after - before, before - after
            recorded_added = Counter({(item['file'], item['code'], item['message']):item['count'] for item in side['added']})
            assert recorded_added == added
            assert all(code == 'TS2314' for path, code, message in removed)
            added_count += sum(added.values())
            recorded = Counter({(item['file'], item['code'], item['message']):item['count'] for item in side['removed']})
            assert recorded == removed
            removed_count += sum(removed.values())
    assert summary['diagnosticComparison'] == dict(added=added_count, removed=removed_count)
    assert added_count == 0 and removed_count == 118
    observations = json.loads(entries['return-contract-outputs.json'])
    assert len(observations) == 5
    for row in observations:
        prefix = 'installed/' + row['node'] + '/'
        markers = [json.loads(line.split(b'OWNED-DATA-SIGNATURES=', 1)[1]) for line in entries[prefix + 'server-stdout.log'].splitlines() if b'OWNED-DATA-SIGNATURES=' in line]
        assert len(markers) == row['observations'] and len(markers) >= 6 and len(markers) % 3 == 0
        assert len(row['contracts']) == 3 and len({m['host'] for m in markers}) == 3
        for contract in row['contracts']:
            host = contract['host']
            name = host.rsplit('.', 1)[1]
            selected = [m for m in markers if m['host'] == host]
            assert len(selected) == len(markers) // 3
            for marker in selected:
                reflection = [m for m in marker['reflection'] if not m['bridge']]
                assert len(reflection) == 1 and reflection[0]['genericReturnType'] == 'com.tkisor.nekojs.api.data.AttachedData'
                assert len(marker['ir']) == 1
                slot = marker['ir'][0]
                assert slot['sourceType'] == contract['sourceType'] == 'com.tkisor.nekojs.api.data.AttachedData<' + host + '>'
                assert 'name=java:' + host in slot['ref'] and not slot['overridden'] and not slot['getter']
            stub = ast.parse(entries[prefix + contract['python']])
            cls = next(c for c in stub.body if isinstance(c, ast.ClassDef) and c.name == name)
            method = next(m for m in cls.body if isinstance(m, ast.FunctionDef) and m.name == 'data')
            assert ast.unparse(method.returns) == contract['pythonAfter'] == 'AttachedData'
            assert contract['pythonBefore'] == 'AttachedData'
            assert re.search(r'\bdata\(\): \$AttachedData<\$' + name + r'>;', entries[prefix + contract['typescript']].decode('utf-8'))
    red = entries['checks/domain-return-contract-red-corrected.xml']
    import xml.etree.ElementTree as ET
    assert ET.fromstring(red).attrib['failures'] == '3'


def check_raw_input_evidence(directory):
    entries = checked_archive(directory, 'raw-input')
    previous = checked_archive(directory, 'return-contract')
    preparation = json.loads(entries['preparation-manifest.json'])
    summary = json.loads(entries['raw-input-summary.json'])
    assert preparation['sourceRevision'].startswith('04550ae2')
    assert summary['sourceRevision'] == preparation['sourceRevision']
    assert hashlib.sha256(entries['production-source.patch']).hexdigest() == preparation['productionPatchSha256']
    assert len(preparation['sourceHashes']) == 1
    assert summary['testTotals'] == dict(tests=4923, failures=0, errors=0, skipped=271)
    assert not summary['fullTscPassed'] and not summary['releaseApproved']
    assert summary['noNewDiagnostics']
    assert len(preparation['nodes']) == len(summary['ide']['nodes']) == 5
    for node in preparation['nodes']:
        prefix = 'installed/' + node['node'] + '/'
        assert json.loads(entries[prefix + 'runtime-verdict.json'])['verdict'] == 'PASS'
        assert json.loads(entries[prefix + 'server-exit.json'])['exitCode'] == 0
        assert node['classBindings'] and len(node['sha256']) == 64
    for node in summary['ide']['nodes']:
        pyright = node['pyright']
        assert pyright['exitCode'] == 0
        assert pyright['summary']['errorCount'] == pyright['summary']['warningCount'] == 0
        assert pyright['summary']['filesAnalyzed'] == pyright['stubCount']
        for side in node['typescript']:
            assert not side['skipLibCheck'] and side['defaultEditorIncludes']
            assert side['exitCode'] != 0 and side['diagnosticCount'] > 0
            data = entries['ide/' + node['node'] + '-' + side['side'] + '-tsc.txt'].decode('utf-8-sig')
            assert side['diagnosticCount'] == sum(side['codes'].values()) == len(re.findall(r'error TS\d+:', data))
    for row in summary['comparisons']:
        assert row['javaClassStubsByteIdentical'] and row['pythonCanonicalFactsEqual']
        assert row['pythonBefore'].keys() == row['pythonAfter'].keys()
        prefix = 'installed/' + row['node'] + '/.neko_probe/python/'
        changed = []
        for path, expected in row['pythonAfter'].items():
            before = previous[prefix + path]
            after = entries[prefix + path]
            assert hashlib.sha256(before).hexdigest() == row['pythonBefore'][path]
            assert hashlib.sha256(after).hexdigest() == expected
            if path.startswith('nekojs/_java/'):
                assert before == after
            if before != after:
                changed.append(path)
                assert path == 'nekojs/__init__.pyi' or path.startswith('nekojs/_events/')
                assert python_declaration_facts(before) == python_declaration_facts(after)
        assert sorted(changed) == sorted(row['changedPythonFiles'])
        assert row['pythonByteIdentical'] == (not changed)

    def diagnostics(data, prefix):
        result = Counter()
        text = data.decode('utf-8-sig')
        for line in text.splitlines():
            match = re.match(r'(.+?)\(\d+,\d+\): error (TS\d+): (.*)', line)
            if match:
                path, code, message = match.groups()
                result[(path.removeprefix(prefix), code, message)] += 1
            elif re.match(r'error TS\d+:', line):
                result[('<global>', *re.match(r'error (TS\d+): (.*)', line).groups())] += 1
        assert sum(result.values()) == len(re.findall(r'error TS\d+:', text))
        return result

    comparison = json.loads(entries['ide/diagnostic-comparison.json'])
    assert len(comparison) == 5
    removed_count = 0
    added_count = 0
    for row in comparison:
        for side in row['sides']:
            name = row['node'] + '-' + side['side'] + '-tsc.txt'
            before = diagnostics(entries['ide-before/' + name], 'build/domain-return-contract-installed/' + row['node'] + '/')
            after = diagnostics(entries['ide/' + name], 'build/domain-raw-input-installed/' + row['node'] + '/')
            added, removed = after - before, before - after
            recorded_added = Counter({(item['file'], item['code'], item['message']):item['count'] for item in side['added']})
            assert recorded_added == added
            assert all(code == 'TS2344' for path, code, message in added)
            added_count += sum(added.values())
            recorded = Counter({(item['file'], item['code'], item['message']):item['count'] for item in side['removed']})
            assert recorded == removed
            removed_count += sum(removed.values())
    assert summary['diagnosticComparison'] == dict(added=added_count, removed=removed_count)
    assert added_count == 0 and removed_count == 160
    assert all(item['code'] == 'TS2314' for row in comparison for side in row['sides'] for item in side['removed'])
    strict = json.loads(entries['checks/strict-results.json'])
    assert [r['case'] for r in strict] == ['red', 'nested-red', 'sam-red', 'green']
    for row in strict:
        log = entries['checks/' + row['log']]
        assert hashlib.sha256(log).hexdigest() == row['sha256']
        assert row['exitCode'] == (0 if row['case'] == 'green' else 2)
        expected = {'red': 2, 'nested-red': 1, 'sam-red': 2, 'green': 0}[row['case']]
        assert log.count(b'error TS2314:') == expected == len(re.findall(rb'error TS\d+:', log))
        for path, expected in row['fixtureHashes'].items():
            assert hashlib.sha256(entries['fixtures/' + row['case'] + '/' + path]).hexdigest() == expected
    import xml.etree.ElementTree as ET
    assert ET.fromstring(entries['checks/red.xml']).attrib['failures'] == '2'


def main():
    directory = Path(__file__).resolve().parent
    rollback = checked_archive(directory, 'rollback')
    verdict = json.loads(rollback['final-verdict.json'])
    assert verdict['verdict'] == 'PASS' and verdict['separateArtifactAndDataRollback']
    assert verdict['backupRetained'] and verdict['advancedWorldRetained']
    assert verdict['exactRestoreBeforeBoot'] and verdict['repeatedCopyHashesEqual']
    for phase in ['candidate-initial', 'candidate-advance', 'artifact-rollback', 'data-restore']:
        result = json.loads(rollback[phase + '/result.json'])
        assert result['verdict'] == 'PASS' and result['exitCode'] == 0
    reviewed = checked_archive(directory, 'reviewed')
    preparation = json.loads(reviewed['preparation-manifest.json'])
    assert hashlib.sha256(reviewed['production-source.patch']).hexdigest() == preparation['productionPatchSha256']
    for node in preparation['nodes']:
        prefix = 'installed/' + node['node'] + '/'
        assert json.loads(reviewed[prefix + 'runtime-verdict.json'])['verdict'] == 'PASS'
        assert json.loads(reviewed[prefix + 'server-exit.json'])['exitCode'] == 0
        assert node['classBindings'] and len(node['sha256']) == 64
    red = json.loads(reviewed['reclaim/red/result.json'])
    green = json.loads(reviewed['reclaim/green/result.json'])
    assert red['componentsPreserved'] is False and green['componentsPreserved'] is True
    assert red['exitCode'] == green['exitCode'] == 0
    assert green['artifactSha256'] == next(row['sha256'] for row in preparation['nodes'] if row['node'] == '26.2.0')
    ide = json.loads(reviewed['ide/summary.json'])
    assert len(ide['nodes']) == 5
    for node in ide['nodes']:
        assert node['pyright']['summary']['errorCount'] == 0
        assert node['pyright']['summary']['filesAnalyzed'] == node['pyright']['stubCount']
        assert all(side['skipLibCheck'] is False for side in node['typescript'])
    frames = checked_archive(directory, 'frame')
    summary = json.loads(frames['frame-summary.json'])
    assert summary['verdict'] == 'PASS' and summary['clientExit'] == summary['serverExit'] == 0
    assert summary['displayPresentVerified'] is False
    captures = [row for row in summary['captures'] if 'request' in row]
    assert len(captures) == 7
    for capture in captures:
        assert capture['request']['sameThread'] and capture['request']['overlayPresent'] is False
        image = frames['accepted/clientA/screenshots/' + capture['screenshot']['file']]
        assert hashlib.sha256(image).hexdigest() == capture['screenshot']['sha256']
        energy = capture['wallRegion']['meanSquaredHorizontalGradient']
        assert energy > 100 if capture['step'].startswith('clear-') else 0.5 < energy < 50
    for step, filename in [('clear-before', 'frame-clear.png'), ('blur-before', 'frame-blur.png')]:
        capture = next(row for row in captures if row['step'] == step)
        assert hashlib.sha256((directory / filename).read_bytes()).hexdigest() == capture['screenshot']['sha256']
    performance = checked_archive(directory, 'performance')
    for label, expected_groups in [('candidate', 3), ('control', 2)]:
        judgment = json.loads(performance[label + '/judgment.json'])
        assert judgment['complete'] and judgment['verdict'] == 'FAIL'
        assert len(judgment['groups']) == expected_groups
        for group in judgment['groups']:
            prefix = label + '/formal/' + group['group'] + '/'
            rows = [json.loads(line) for line in performance[prefix + 'samples.jsonl'].decode('utf-8-sig').splitlines()]
            formal = [row for row in rows if row['kind'] == 'formal']
            assert len(formal) == 5
            assert all(not row.get('timeout') and not row['forced_kill'] and not row['killed']
                       and row['stop_channel'] == 'rcon' for row in rows)
            assert group['values'] == [row[group['measure']] for row in formal]
            assert group['mean'] == statistics.mean(group['values'])
            expected_verdict = 'PASS' if group['mean'] <= group['limit'] else 'FAIL'
            assert group['verdict'] == expected_verdict
            if group['group'] == 'startup':
                assert len([row for row in rows if row['kind'] == 'warmup']) == 2
                assert group['limit'] == 41173
            else:
                assert group['limit'] == 285.3
                assert all('phase=COMMIT - no errors' in row['rcon_response'] for row in formal)
    frozen = json.loads(performance['candidate/frozen-classes.json'])
    primary = next(row for row in preparation['nodes'] if row['node'] == '26.1.2')
    assert frozen['classesMatchReviewedArtifact'] and frozen['classBindings'] == primary['classBindings']
    assert frozen['reviewedArtifactSha256'] == primary['sha256']
    profile_session = json.loads(performance['profile/session.json'].decode('utf-8-sig'))
    assert not profile_session['forcedKill'] and not profile_session['extra']['killed']
    assert profile_session['extra']['stopChannel'] == 'rcon'
    assert len(json.loads(performance['profile/reloads.json'].decode('utf-8-sig'))) == 5
    investigation = checked_archive(directory, 'investigation')
    prototype = json.loads(investigation['shared-parse/summary.json'])
    assert prototype['verdict'] == 'REJECTED' and prototype['diagnosticOnly']
    assert len(prototype['pairs']) == 10 and prototype['focusedTests']['failures'] == 0
    phases = json.loads(investigation['phases/phase-summary.json'])
    assert phases['diagnosticOnly'] and len(phases['commandedPhases']) == len(phases['commands']) == 20
    assert phases['phaseMeanMs'] == statistics.mean(row['totalMs'] for row in phases['commandedPhases'])
    for name, mean in phases['stepMeansMs'].items():
        assert mean == statistics.mean(row['stepsMs'][name] for row in phases['commandedPhases'])
    session = json.loads(investigation['phases/session.json'].decode('utf-8-sig'))
    assert not session['forcedKill'] and not session['extra']['killed']
    assert session['extra']['stopChannel'] == 'rcon'
    check_enum_evidence(directory, reviewed)
    check_generic_evidence(directory)
    check_resolver_evidence(directory)
    check_return_contract_evidence(directory)
    check_raw_input_evidence(directory)
    print('PASS: artifact, runtime, IDE, rollback, frame, performance, enum, generic, resolver, return-contract and raw-input evidence integrity; full TSC and reload performance remain FAIL')


if __name__ == '__main__':
    main()
