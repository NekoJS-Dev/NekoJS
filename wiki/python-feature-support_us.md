<!-- wiki-page: python-feature-support; locale: us -->

> **English** · [中文](python-feature-support_cn)

<a id="wiki-section-1"></a>
# Python feature support (for the NekoJS JS transpiler)

> This document compares modern Python language features (3.10 – 3.14) with the current support in NekoJS's built-in Python→JS transpiler. Its purposes are: ① to understand what recent Python versions provide; ② to decide which features the transpiler should add or skip.
>
> The **NekoJS transpiler** parses a Python **subset** into an AST, lowers it to JavaScript, and executes it with GraalJS. The key question for each feature is therefore: **can it map cleanly to JS?** (GraalJS natively supports ES2020+, including classes, generators, async/await, template strings, optional chaining, etc.)
>
> Status markers: ✅ supported　🟡 partially supported　❌ unsupported (intentionally skipped)　⏳ possible future extension
>
> This page compares language features and transpiler design choices. For whether something is currently usable in scripts, consult the support table and limits in [Python scripts](python-scripts_us). If the pages conflict, the implementation and Python scripts page take precedence.

---

<a id="wiki-section-2"></a>
## 1. Recent Python version features (3.10 – 3.14)

> Version numbers and PEPs come from the official whatsnew / PEP indexes (links at the end).

<a id="wiki-section-3"></a>
### Python 3.10

| Feature | PEP | Syntax example | JS transpilation | NekoJS status |
|---|---|---|---|---|
| Structural pattern matching `match`/`case` | PEP 634 | `match x:\n  case 1: ...` | Moderate (can lower to an if/else chain) | ✅ (PEP 634, literal/wildcard/capture/`|`/sequence/mapping/class patterns + guard) |
| Parenthesized multiple context managers | — | `with (a as x, b as y):` | Easy (multiple `with` items already supported) | ✅ (equivalent to `with a as x, b as y:`) |
| `X \| Y` union type annotations | PEP 604 | `def f(x: int \| str)` | Easy (annotations are discarded) | ✅ (annotations discarded) |
| More precise error locations (with `^` indicators) | — | — | Unrelated to transpilation | — |

<a id="wiki-section-4"></a>
### Python 3.11

| Feature | PEP | Syntax example | JS transpilation | NekoJS status |
|---|---|---|---|---|
| Exception groups `ExceptionGroup` + `except*` | PEP 654 | `raise ExceptionGroup(...)` / `except* ValueError:` | Difficult (no native JS equivalent; needs emulation) | ❌ |
| `asyncio.TaskGroup` (structured concurrency) | — | `async with asyncio.TaskGroup() as tg:` | Part of stdlib/async | ❌ |
| `add_note` (exception notes) | — | `e.add_note('...')` | Easy (object properties) | ❌ (not mapped) |
| `typing.Self` / `typing.override` decorator | PEP 673 | — | Annotations can simply be discarded | ❌ (these names are not recognized in annotations) |
| tomllib (standard library TOML parsing) | PEP 680 | — | stdlib, unrelated | — |
| Fine-grained traceback locations, speedups (Faster CPython) | — | — | Unrelated to transpilation | — |

<a id="wiki-section-5"></a>
### Python 3.12

| Feature | PEP | Syntax example | JS transpilation | NekoJS status |
|---|---|---|---|---|
| New type parameter syntax (`type` statement + `[T]` generics) | PEP 695 | `type ListOrSet[T] = ...` / `def f[T]():` | Annotation-level; can simply be discarded | ❌ (`[T]`/`type` statements not parsed) |
| Formalized f-strings (PEP 701: arbitrary expressions, nested quotes, backslashes, comments, nested f-strings) | PEP 701 | `f"{f'{x}'}"`, `f"{'\n'.join(xs)}"` | Moderate (needs a stronger tokenizer) | 🟡 (interpolation and format specifiers supported; same-quote nested f-strings and comments unsupported; backslash escapes inside strings work) |
| Per-interpreter GIL | PEP 684 | — | Runtime/interpreter level, unrelated | — |
| `@override` decorator (typing) | PEP 698 | `@override\ndef m():` | Annotations can simply be discarded | ❌ (class method decorators limited to `@staticmethod`/`@classmethod`/`@property`) |
| Buffer protocol exposed to Python | PEP 688 | — | C level, unrelated | — |
| Inlined comprehensions (performance optimization without semantic change) | PEP 709 | — | No semantic change, unrelated | — |

<a id="wiki-section-6"></a>
### Python 3.13

| Feature | PEP | Syntax example | JS transpilation | NekoJS status |
|---|---|---|---|---|
| Experimental free-threaded builds (optional GIL) | PEP 703 | — | Runtime build, unrelated | — |
| Type parameter defaults, `TypeVar` defaults | PEP 696 | `class Guild[T = str]:` | Annotation-level | ❌ |
| `@deprecated` decorator | PEP 702 | `@deprecated` | Annotation-level | ❌ |
| New interactive REPL, colored tracebacks | — | — | Unrelated to transpilation | — |
| Local variable consistency (`locals()` semantics) | PEP 667 | — | Unrelated to transpilation | — |

<a id="wiki-section-7"></a>
### Python 3.14

| Feature | PEP | Syntax example | JS transpilation | NekoJS status |
|---|---|---|---|---|
| Template strings: **t-strings** | PEP 750 | `t"hello {name}"` → `Template` object | Difficult (produces an object rather than a string; requires a Template type) | ❌ (too new; skipped for now) |
| **Deferred evaluation** of annotations | PEP 649 / 749 | Annotations stored as deferred expressions | Annotation-level (we already discard them) | — (annotations discarded) |
| Standard library subinterpreter support | PEP 734 | — | Runtime, unrelated | — |
| Free-threading improvements, tail-call interpreter (CEval) | — | — | Runtime, unrelated | — |

> **Summary**: among the new **syntax-level** features in 3.10–3.14, the ones that matter to a JS transpiler are `match/case` (3.10), relaxed f-strings (3.12 PEP 701), and t-strings (3.14 PEP 750). The rest are either annotation/type-level features (the transpiler already discards annotations) or runtime/interpreter/standard library features (unrelated to translating Python into JS).

---

<a id="wiki-section-8"></a>
## 2. Core syntax features (general, not version-specific)

The following table lists capabilities commonly wanted in a practical Python scripting subset, with **current NekoJS support** and **expected JS mappings**.

<a id="wiki-section-9"></a>
### Statements

| Feature | Syntax example | Expected JS mapping | NekoJS status |
|---|---|---|---|
| Functions `def` (default parameters / `*args` / `**kwargs`) | `def f(a, b=1, *args, **kw):` | `function` (`**kwargs` triggers a prologue) | ✅ |
| Classes and inheritance | `class C(B):` | JS `class` / `extends` | ✅ |
| `super()` | `super().__init__(x)` | `super(x)` / `super.m()` | ✅ |
| `@staticmethod` / `@classmethod` | `@staticmethod` / `@classmethod` | `static` methods; classmethod binds `cls=this` | ✅ |
| Properties `@property` | `@property\ndef x(self):` | JS getter (read-only) | ✅ |
| `if` / `elif` / `else` | — | `if / else if / else` | ✅ |
| `for x in iter` / `while` | — | `for...of` / `while` | ✅ |
| `with` context managers | `with cm as x:` | Inline acquire + try/finally | ✅ |
| `try` / `except` / `else` / `finally` | — | `try/catch/finally` + instanceof | ✅ |
| Assignment (chained / tuple unpacking / augmented) | `a=b=v`, `a,b=...`, `+=` | `var` / destructuring | ✅ |
| Walrus `:=` | `if (n:=f()):` | `(n = f())` | ✅ |
| `assert` | `assert c, msg` | `if (!__nekoTruthy(c)) throw new AssertionError(msg)` | ✅ |
| `del` | `del d[k]` | `delete d[k]` | ✅ |
| `raise` / bare `raise` / `raise ... from` | — | `throw` / rethrow | ✅ (`from` parsed then ignored) |
| `global` / `nonlocal` | `global x` | — | ❌ |
| `return` / `break` / `continue` / `pass` | — | Same names | ✅ |
| `import` / `from ... import` | — | ESM `import` | ✅ |
| Type annotations (parameters / return / variables) | `x: int = 5` | Parsed then discarded | ✅ (discarded) |
| `match` / `case` pattern matching | `match x:` | Lowered to an if/else chain | ✅ (literal/wildcard/capture/`|`/sequence/mapping/class patterns + guard) |
| `async def` / `await` / `async for` / `async with` | — | JS async/await | ❌ (GraalJS supports it, but the value is low) |

<a id="wiki-section-10"></a>
### Expressions

| Feature | Syntax example | Expected JS mapping | NekoJS status |
|---|---|---|---|
| int/float/str/bool/None literals | — | number/string/boolean/null | ✅ |
| f-strings (including format specifiers such as `:.2f`, `!r`) | `f'{x:.2f}'` | Template literals + `__nekoFmt` | ✅ |
| t-strings (3.14) | `t"{x}"` | Template object | ❌ |
| Attribute / index / call | `a.b` / `a[i]` / `f()` | Same forms | ✅ |
| Keyword calls | `f(a=1, b=2)` | Trailing marked object + prologue | ✅ (target requires `**kwargs` or print/sorted) |
| Arithmetic / comparison / boolean / bitwise operations | `+ - * / // % **`, `and/or/not`, `& \| ^ ~ << >>` | Corresponding JS (`//`→`Math.floor`) | ✅ |
| Conditional `a if c else b` | — | `(c ? a : b)` | ✅ |
| `in` / `not in` / `is` / `is not` | — | `__nekoIn` / `===` | ✅ (arrays, strings, dict own keys, and Map/Set) |
| Chained comparisons | `a < b < c` | `(a<b) && (b<c)` | ✅ |
| Slices (arbitrary steps, negative indices) | `xs[::2]`, `xs[::-1]`, `xs[-1]` | Helper / `slice` | ✅ |
| List / tuple / dictionary / set literals | `[1,2]`, `(1,2)`, `{k:v}`, `{1,2}` | Array / array / object / `Set` | ✅ |
| `lambda` | `lambda x: x*2` | Arrow function | ✅ (no `**kwargs`) |
| Comprehensions (list / dictionary / set, multiple `for` and `if` clauses) | `[x for a in A for b in B if c]` | `.filter().map()` / nested `flatMap` | ✅ |
| Generator expressions `(x for x in xs)` | — | Immediately invoked `function*` | ✅ |
| `yield` / `yield from` | — | `function*` / `yield` / `yield*` | ✅ |
| Dictionary merge `\|` (3.9) | `d1 \| d2` | `Bitwise OR` (not specialized for dict) | ❌ (treated as bitwise OR) |
| Literal string concatenation `"a" "b"` | — | Implicit concatenation | ✅ |

<a id="wiki-section-11"></a>
### Built-in functions

Built-ins mapped by NekoJS (see the "Built-in functions" table in [Python scripts](python-scripts_us)): `range / len / print / abs / min / max / sum / str / int / float / bool / list / dict / set / tuple / sorted / any / all / enumerate / reversed / map / filter / zip / round / divmod / ord / chr / pow / hex / oct / bin / repr / format / isinstance / type / callable / getattr / hasattr / setattr / delattr / iter / next / frozenset`.

Still unmapped: `id / vars / globals / locals / eval / exec / input / open / complex / ...` (mostly reflection, IO, or features that do not fit the JS runtime).

---

<a id="wiki-section-12"></a>
## 3. Suggested implementation priorities for the NekoJS transpiler

| Feature | Priority | Reason | Expected mapping |
|---|---|---|---|
| dict merge `\|` | 🟢 Low | Uncommon; `{...a, ...b}` is sufficient | Specialize dict operands into spread merging |
| Relaxed f-strings, PEP 701 | 🟡 Medium | Same-quote nesting / backslashes / comments | Requires a stronger f-string tokenizer (the current FStringParser handles most cases) |
| `global`/`nonlocal` | 🔴 Low/skip | JS module scope semantics differ substantially and are difficult to map faithfully | Skip; encourage return values/containers |
| `async`/`await` | 🔴 Low | GraalJS supports it, but integrating a script-side event loop is complex | Skip for now |
| Exception groups `except*` | 🔴 Low | No native JS equivalent; expensive to emulate | Skip |
| t-strings (3.14) | 🔴 Low | Too new; requires a `Template` type | Skip |
| Type annotation evaluation | 🔴 Low | Unrelated to the transpilation target (static types) | Always discard |

> High-value extensions **completed** in this round: generators/yield, `with`, nested comprehensions, arbitrary slice steps, f-string format specifiers, `**kwargs` + keyword calls, `assert`/`del`/walrus, `try/else`, bare `raise`, type annotation discarding, extended built-ins, `match`/`case` (all patterns), `@property`/`@classmethod`, and `getattr`/`hasattr`/`setattr`/`delattr`/`iter`/`next`/`frozenset`.

---

<a id="wiki-section-13"></a>
## 4. GraalJS / ES capability comparison

| Python construct | ES equivalent | Description |
|---|---|---|
| `class` + inheritance | `class` + `extends` | One-to-one mapping |
| Generators / `yield` / `yield from` | `function*` / `yield` / `yield*` | Native GraalJS |
| `async`/`await` | `async`/`await` | Native GraalJS (script-side event loop required) |
| `with` (context manager) | No native equivalent → try/finally + `__enter__`/`__exit__` | NekoJS inline lowering |
| `**kwargs` | No native equivalent → trailing marked object + prologue | NekoJS convention |
| f-strings | Template literals `` ` ` `` | Format specifiers require a custom `__nekoFmt` |
| Comprehensions | `.map`/`.filter`/`.flatMap` | One-to-one mapping |
| Slices | `Array.prototype.slice` + step helper | Steps require a custom implementation |
| Dictionaries | Plain objects | String-only keys; integer keys reordered |
| Sets | `Set` | One-to-one mapping |
| Tuples | Arrays | Not immutable |

---

<a id="wiki-section-14"></a>
## Reference links

- [What's New in Python 3.10](https://docs.python.org/3/whatsnew/3.10.html)　·　[3.11](https://docs.python.org/3/whatsnew/3.11.html)　·　[3.12](https://docs.python.org/3/whatsnew/3.12.html)　·　[3.13](https://docs.python.org/3.13/whatsnew/3.13.html)　·　[3.14](https://docs.python.org/3/whatsnew/3.14.html)
- [PEP 634 / 636 — match/case (3.10)](https://peps.python.org/pep-0636/)
- [PEP 654 — Exception Groups and `except*` (3.11)](https://peps.python.org/pep-0654/)
- [PEP 695 — Type parameter syntax (3.12)](https://peps.python.org/pep-0695/)　·　[PEP 701 — Formalized f-strings (3.12)](https://peps.python.org/pep-0701/)
- [PEP 703 — Free threading (3.13)](https://peps.python.org/pep-0703/)　·　[PEP 696 — Type parameter defaults (3.13)](https://peps.python.org/pep-0696/)
- [PEP 750 — Template strings, t-strings (3.14)](https://peps.python.org/pep-0750/)　·　[PEP 649 / 749 — Deferred annotation evaluation (3.14)](https://peps.python.org/pep-0649/)

<!-- wiki-nav -->

---

[Previous: Python scripts](python-scripts_us) · [Contents](Home) · [Next: Event reference](event-reference_us)
