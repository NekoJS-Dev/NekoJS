import ast
import json
import re
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
python_root = root / '.neko_probe' / 'python'
ts_root = root / '.neko_probe' / 'typescript'
parsed = {}
failures = []
for file in python_root.rglob('*.pyi'):
    try:
        parsed[file] = ast.parse(file.read_text(encoding='utf-8'), filename=str(file))
    except SyntaxError as error:
        failures.append({'path': file.relative_to(python_root).as_posix(), 'line': error.lineno, 'message': error.msg})
server_path = python_root / 'nekojs/_events/server/__init__.pyi'
payload = next(node for node in parsed[server_path].body if isinstance(node, ast.ClassDef) and node.name == 'DynamicRegistryEvent')
methods = [node for node in payload.body if isinstance(node, ast.FunctionDef)]
expected = {'item': 'DynamicItemBuilder', 'soundEvent': 'DynamicSoundEventBuilder', 'mobEffect': 'DynamicMobEffectBuilder'}
assert {node.name for node in methods} == set(expected)
for method in methods:
    assert ast.unparse(method.returns) == 'bool'
    assert len(method.args.defaults) == 1 and isinstance(method.args.defaults[0], ast.Constant) and method.args.defaults[0].value is Ellipsis
    assert ast.unparse(method.args.args[-1].annotation) == f'Callable[[{expected[method.name]}], None]'
imports = [node for node in parsed[server_path].body if isinstance(node, ast.ImportFrom) and node.module == 'nekojs._registry_builders']
assert len(imports) == 1 and {alias.name for alias in imports[0].names} == set(expected.values())
builder_path = python_root / 'nekojs/_registry_builders/__init__.pyi'
builders = {node.name for node in parsed[builder_path].body if isinstance(node, ast.ClassDef)}
assert set(expected.values()).issubset(builders)
server_ts = (ts_root / '@side-only/server/events/index.d.ts').read_text(encoding='utf-8')
assert '/// <reference path="../../../@registry-builders/index.d.ts" />' in server_ts
assert 'event: DynamicRegistryEvent' in server_ts and '$DynamicRegistryEventJS' not in server_ts
match = re.search(r'interface DynamicRegistryEvent \{(.*?)\n\}', server_ts, re.S)
assert match is not None
for name, builder in expected.items():
    assert f'{name}(id: string, build?: (build: {builder}) => void): boolean;' in match.group(1)
assert set(re.findall(r'^\s+(\w+)\(', match.group(1), re.M)) == set(expected)
assert not list((ts_root / '@package/com/tkisor/nekojs/core').glob('**/*.d.ts'))
assert not list((python_root / 'nekojs/_java/com/tkisor/nekojs/core').glob('**/*.pyi'))
print(json.dumps({'actual_installed_python_ast_pass': len(parsed), 'actual_installed_python_ast_failures': failures, 'actual_dynamic_payload_methods': sorted(expected), 'concrete_optional_bool_methods': 3, 'actual_script_surface_imports_resolve': 3, 'default_core_modules_absent': True, 'typescript_relative_builder_reference_resolves': (ts_root / '@registry-builders/index.d.ts').is_file(), 'pyright_not_run': True, 'entire_live_typescript_ide_typecheck_not_claimed': True}, sort_keys=True))
if failures:
    sys.exit(1)
