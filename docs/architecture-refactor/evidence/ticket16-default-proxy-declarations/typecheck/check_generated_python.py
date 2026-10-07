import ast
from pathlib import Path

root = Path(__file__).parent / "generated" / "python"
files = sorted(root.rglob("*.pyi"))
for filename in files:
    ast.parse(filename.read_text(encoding="utf-8"), filename=str(filename))
events = root / "nekojs" / "_events" / "server" / "__init__.pyi"
builders = root / "nekojs" / "_registry_builders" / "__init__.pyi"
event_tree = ast.parse(events.read_text(encoding="utf-8"))
builder_tree = ast.parse(builders.read_text(encoding="utf-8"))
classes = {node.name: node for node in builder_tree.body if isinstance(node, ast.ClassDef)}
imports = [node for node in event_tree.body if isinstance(node, ast.ImportFrom)
           and node.module == "nekojs._registry_builders"]
assert len(imports) == 1
assert {alias.name for alias in imports[0].names} == {
    "DynamicItemBuilder", "DynamicSoundEventBuilder", "DynamicMobEffectBuilder"
}
assert all(alias.name in classes for alias in imports[0].names)
payload = next(node for node in event_tree.body
               if isinstance(node, ast.ClassDef) and node.name == "DynamicRegistryEvent")
assert [ast.unparse(base) for base in payload.bases] == ["Protocol"]
methods = {node.name: node for node in payload.body if isinstance(node, ast.FunctionDef)}
expected = {"item": "DynamicItemBuilder", "soundEvent": "DynamicSoundEventBuilder",
            "mobEffect": "DynamicMobEffectBuilder"}
assert methods.keys() == expected.keys()
for name, builder in expected.items():
    method = methods[name]
    assert [argument.arg for argument in method.args.args] == ["self", "id", "build"]
    assert ast.unparse(method.args.args[1].annotation) == "str"
    assert ast.unparse(method.args.args[2].annotation) == f"Callable[[{builder}], None]"
    assert len(method.args.defaults) == 1
    assert isinstance(method.args.defaults[0], ast.Constant)
    assert method.args.defaults[0].value is Ellipsis
    assert ast.unparse(method.returns) == "bool"
print(f"PYTHON_STUB_AST_PASS={len(files)}")
print("ACTUAL_SCRIPT_SURFACE_IMPORTS_RESOLVE=3")
print("CLOSED_TYPED_OPTIONAL_BOOL_METHODS=3")
print("PYRIGHT_NOT_RUN=true")
