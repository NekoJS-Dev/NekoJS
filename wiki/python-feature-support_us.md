<!-- wiki-page: python-feature-support; locale: us -->

> **English** · [中文](python-feature-support_cn)

<a id="wiki-section-1"></a>
# Python feature support

This page lists the language features added in recent Python versions (3.8 through 3.14) and whether they work in NekoJS.

NekoJS converts Python code to JavaScript before running it, and supports only part of Python. For everyday syntax (functions, classes, comprehensions, f-strings and so on), see [Python scripts](python-scripts_us). This page answers one question: "I learned this in a recent Python version. Can I use it in NekoJS?"

Status values in the tables:

- **Works**: write it as usual.
- **Partly**: works with limits; see the notes.
- **No**: fails with an error, or gives a wrong result.
- **N/A**: interpreter, performance or standard library changes that don't affect scripts.

<a id="wiki-section-2"></a>
## Summary

- Works: the walrus operator `:=`, `match` / `case` (some patterns), and type annotations (including `int | str`, `list[int]` and `Self`; all are ignored).
- Doesn't work: `async` / `await`, positional-only `/` and keyword-only `*` parameters, `except*`, the `type` statement and `[T]` generic syntax, t-strings, and dict merge `|`.
- There is no Python standard library, so modules such as `typing`, `asyncio` and `tomllib` can't be imported. You can write `Self` or `List` in annotations, but don't write `from typing import ...`.

<a id="wiki-section-3"></a>
## Python 3.8

| Feature | Example | Status | Notes |
|---|---|---|---|
| Walrus operator | `if (n := len(xs)) > 3:` | Partly | Define the variable with a normal assignment first; see "Known issues" in [Python scripts](python-scripts_us) |
| Positional-only parameters | `def f(a, /, b):` | No | Parse error |
| f-string `=` for debugging | `f'{x=}'` | No | No error, but only the value is printed, without the `x=` prefix |

<a id="wiki-section-4"></a>
## Python 3.9

| Feature | Example | Status | Notes |
|---|---|---|---|
| Dict merge operators | `a \| b`, `a \|= b` | No | No error, but the result is wrong. Use `{**a, **b}` |
| Built-in generic annotations | `x: list[int] = []` | Works | The annotation is ignored |
| `str.removeprefix` / `removesuffix` | `s.removeprefix('a')` | No | Runtime error. Use a slice instead |

<a id="wiki-section-5"></a>
## Python 3.10

| Feature | Example | Status | Notes |
|---|---|---|---|
| `match` / `case` | `match x:` | Partly | Literals, `_`, captures, `\|`, list and dict patterns, keyword class patterns and `if` guards work. Positional class patterns such as `Point(0, 0)` don't |
| Union type annotations | `def f(x: int \| str):` | Works | The annotation is ignored |
| Parenthesized context managers | `with (a as x, b as y):` | No | Drop the parentheses: `with a as x, b as y:` |

<a id="wiki-section-6"></a>
## Python 3.11

| Feature | Example | Status | Notes |
|---|---|---|---|
| Exception groups | `except* ValueError:` | No | Parse error |
| Exception notes | `e.add_note('...')` | No | |
| `Self` annotation | `def copy(self) -> Self:` | Works | The annotation is ignored |
| `asyncio.TaskGroup`, `tomllib` | | No | No standard library |

<a id="wiki-section-7"></a>
## Python 3.12

| Feature | Example | Status | Notes |
|---|---|---|---|
| `type` statement | `type Pair = tuple[int, int]` | No | Parse error |
| Type parameter syntax | `def first[T](xs):` | No | Parse error. Remove `[T]` |
| Relaxed f-strings | `f"{f"{x}"}"` | Partly | Using the other quote type inside works (`f"{'a'}"`); nesting the same quote type or writing comments inside `{}` doesn't |
| `@override` | `@override` | No | Methods accept only `@staticmethod`, `@classmethod` and `@property` |

<a id="wiki-section-8"></a>
## Python 3.13

| Feature | Example | Status | Notes |
|---|---|---|---|
| Type parameter defaults | `class Box[T = int]:` | No | Type parameter syntax itself isn't supported |
| `@deprecated` | `@deprecated('use g')` | No | Decorators can't take arguments |
| New REPL, free-threaded builds | | N/A | |

<a id="wiki-section-9"></a>
## Python 3.14

| Feature | Example | Status | Notes |
|---|---|---|---|
| Template strings (t-strings) | `t'hello {name}'` | No | Parse error |
| Deferred evaluation of annotations | | N/A | NekoJS ignores annotations anyway |

<a id="wiki-section-10"></a>
## Older syntax that doesn't work

These aren't new features, but people often ask about them:

| Syntax | Status | Alternative |
|---|---|---|
| `async` / `await`, `async for`, `async with` | No | Use event callbacks |
| `global` / `nonlocal` | No | Keep state in a dict or list |
| Starred assignment `a, *rest = xs` | No | `a = xs[0]` and `rest = xs[1:]` |
| `str.format` | No | f-strings |
| Multiple base classes | No | Single inheritance or composition |

For the full list of unsupported syntax and alternatives, see [Python scripts](python-scripts_us).

<a id="wiki-section-11"></a>
## References

- [What's New in Python](https://docs.python.org/3/whatsnew/index.html): official release notes for each version.
- [PEP 572 — Assignment expressions (3.8)](https://peps.python.org/pep-0572/)
- [PEP 636 — match/case tutorial (3.10)](https://peps.python.org/pep-0636/)
- [PEP 654 — Exception groups and `except*` (3.11)](https://peps.python.org/pep-0654/)
- [PEP 695 — Type parameter syntax (3.12)](https://peps.python.org/pep-0695/)
- [PEP 701 — Formalized f-string syntax (3.12)](https://peps.python.org/pep-0701/)
- [PEP 750 — Template strings (3.14)](https://peps.python.org/pep-0750/)

<!-- wiki-nav -->

---

[Previous: Python scripts](python-scripts_us) · [Contents](Home) · [Next: Event reference](event-reference_us)
