<!-- wiki-page: python-scripts; locale: us -->

> **English** · [中文](python-scripts_cn)

<a id="wiki-section-1"></a>
# Python scripts

NekoJS has **built-in** support for a Python subset. You can write scripts directly in `.py` files, without an additional compilation step or an external Python runtime.

> This is a **subset transpiler**, not a full Python interpreter. Python source is parsed into an AST, lowered to JavaScript, and executed by GraalJS. You can therefore use all NekoJS JS bindings directly (`Item`, `Utils`, `ServerEvents`, ...), but only the Python syntax and built-in functions listed below.

<a id="wiki-section-2"></a>
## Quick start

Place `.py` files in the appropriate script directory (`startup_scripts/`, `server_scripts/`, `client_scripts/`, `test_scripts/`); they are loaded just like `.js`/`.ts` files. You do not need to install CPython or install NekoJS with pip.

```python
# server_scripts/hello.py
from nekojs import *

print('[NekoJS] Python script loaded')
```

Enter a world or run `/nekojs reload`; this message is printed when the script loads. `ServerEvents.started(...)`, by contrast, fires when the server finishes starting, not on every script reload.

<a id="wiki-section-3"></a>
### Enabling Python type hints

You need to do both: **generate Python stubs** and **make them visible to the editor through imports**.

1. Run `/nekojs probe python` in-game to generate `.pyi` files under `.neko_probe/python/nekojs/`. Bare `/nekojs probe` generates only TypeScript declarations; `/nekojs probe all` runs all backends.
2. Add `from nekojs import *` at the top of every `.py` file that uses NekoJS bindings. If you prefer not to use a star import, import only the names you need, for example `from nekojs import Item, ServerEvents`.
3. Use the Python extension and Pylance in VS Code, opening the game directory, `nekojs/`, or the relevant script directory as the workspace, rather than opening only one file or a parent directory above them.
4. Probe merges `pyrightconfig.json` and `.vscode/settings.json` in these directories, pointing `extraPaths` / `python.analysis.extraPaths` at the actual stub output directory. After creating a new subdirectory containing `.py` files, you can run Python probe again.

Here, `from nekojs import ...` is a **type declaration entry for the editor**. The NekoJS transpiler strips it: it does not load a Python package or generate a JavaScript import. Runtime bindings such as `Item` and `ServerEvents` are already provided by the current script environment, so code may run without this line, but the editor does not automatically recognize all runtime globals as Python names.

> Use imports without aliases. Do not replace this with `import nekojs` or `from nekojs import Item as MyItem`: the current special handling only strips `from nekojs import ...`; it does not create a package object or runtime aliases.

If completion is still missing, first check that the stubs were generated, the import is present, and the current workspace configuration points to `.neko_probe/python` (not the TypeScript directory or the package's `nekojs/` subdirectory). If the import cannot be resolved, check that Pylance is enabled and that the workspace root is correct. See [Probe type generation](probe-type-generation_us) for the full generation and configuration rules.

> Python script types, reload lifecycles, and platform capabilities follow the current script environment; Python does not extend the API separately. See [Script basics](script-basics_us) and [Platforms and compatibility](platform-compatibility_us).

<a id="wiki-section-4"></a>
### A more practical example

```python
# server_scripts/recipes.py
from nekojs import ServerEvents

def add_sticks(event, output, count):
    """One-step crafting: count sticks."""
    event.shapeless(f'{count}x minecraft:stick', [output])

class RecipeSet:
    def __init__(self, event):
        self.event = event
        self.added = 0

    def add(self, output, count):
        add_sticks(self.event, output, count)
        self.added += 1

ServerEvents.recipes(lambda event: (
    RecipeSet(event).add('minecraft:cobblestone', 4),
    RecipeSet(event).add('minecraft:bamboo', 2),
))
```

<a id="wiki-section-5"></a>
## Supported syntax

| Syntax | Status |
|---|---|
| Functions `def f(a, b=1, *args, **kwargs):` (default parameters, `*args`, `**kwargs`) | Supported, lowered to `function` (hoisted); `**kwargs` triggers prologue rebinding |
| `if` / `elif` / `else` | Supported, lowered to `if / else if / else` |
| `match` / `case` (structural pattern matching: literal/wildcard/capture/`|`/sequence/mapping/class patterns + `if guard`) | Supported, lowered to an if/else chain |
| `for x in iter:` / `while cond:` | Supported, `for...of` / `while` |
| Assignment: `=`, augmented `+=`/...`//=`/`**=`, tuple unpacking `a, b = ...`, multiple targets `a = b = v`, walrus `:=` | Supported |
| `lambda` (default parameters and `*args` supported; **`**kwargs` unsupported**) | Supported, lowered to an arrow function |
| Comprehensions: list/dictionary/set, **multiple `for` and `if` clauses supported** | Supported, single level uses `.filter().map()`; multiple levels nest `.flatMap` |
| Slices `xs[lo:up:step]` (arbitrary steps, negative indices) | Supported (see "Slices") |
| `try` / `except` / `else` / `finally` (multiple except clauses, `except (A, B)`, instanceof, bare `raise` rethrow) | Supported (see "try / except") |
| `with` statements (context managers) | Supported (see "with statements") |
| Keyword arguments (any function/method/`__init__` declaring `**kwargs`) | Supported |
| Generators `yield` / `yield from` | Supported, lowered to JS `function*` / `yield` / `yield*` |
| Generator expressions `(x for x in xs if c)` (can be the sole argument of `sum(...)`, etc.) | Supported, lowered to an immediately invoked `function*` |
| Iterable unpacking `f(*args)` / `f(**kw)` / `[1, *xs]` / `{**a, **b}` | Supported, lowered to spread |
| `for/else`, `while/else` (`else` runs only if no `break` occurs) | Supported |
| f-strings `f'{x:.2f}'` (**including format specifiers and `!r/!s/!a` conversions**) | Supported (see "f-strings") |
| Decorators `@deco` / `@pkg.deco` (top-level functions and classes) | Supported, lowered to `name = deco(name)` after definition |
| `assert cond[, msg]` / `del target` | Supported (`assert`→`throw new AssertionError(...)` (built-in exception prelude class); `del` supports only `del d[k]` / `del obj.attr`; deleting an ordinary name with `del x` fails at compile time) |
| Classes (`__init__`/constructors, `self`→`this`, `extends`, `super()`, `@staticmethod`/`@classmethod`/`@property`, `__str__`→`toString`) | Supported |
| Type annotations (parameter `x: int`, return `-> str`, variable `x: int = 5`) | Supported (**parsed then discarded**, no runtime participation) |
| `import` / `from ... import ...` (load sibling `.py`/`.js` modules by relative path) | Supported |
| `raise Expr` / bare `raise` (rethrow) | Supported, lowered to `throw` |
| Conditional expression `a if cond else b` | Supported |
| Comparisons `in` / `not in` / `is` / `is not` | Supported |
| `pass` / `break` / `continue` / `return` | Supported |

<a id="wiki-section-6"></a>
### Functions

```python
def greet(name, prefix='Hello'):
    return f'{prefix}, {name}!'

def sum_all(*nums):
    return sum(nums)

print(greet('NekoJS'))        # Hello, NekoJS!
print(sum_all(1, 2, 3, 4))    # 10
```

<a id="wiki-section-7"></a>
### Control flow

```python
def classify(n):
    if n < 0:
        return 'negative'
    elif n == 0:
        return 'zero'
    else:
        return 'positive'

for i in range(3):
    print(i)

while False:
    pass
```

<a id="wiki-section-8"></a>
### Assignment and the walrus operator

```python
a = 1
a += 5              # Augmented assignment
b = c = 0           # Chained assignment to multiple targets (right side evaluated once)
x, y = 1, 2         # Tuple unpacking → JS array destructuring
n //= 2             # Augmented floor division

# Walrus := assigns within an expression and returns the value
if (n := len(items)) > 10:
    print(f'太长了：{n}')
xs = [y for s in strs if (y := len(s)) > 1]   # Reuse a computed value in a comprehension
```

<a id="wiki-section-9"></a>
### lambda and list comprehensions

```python
square = lambda x: x * x

# Single/nested for clauses and multiple if clauses are supported
evens = [n for n in range(10) if n % 2 == 0]
pairs = [(i, j) for i in range(2) for j in range(2) if i != j]
squares = {x: x * x for x in range(5)}        # Dictionary comprehension
unique = {c for c in 'hello'}                  # Set comprehension
```

<a id="wiki-section-10"></a>
### f-strings

```python
name = 'NekoJS'
version = 2
print(f'{name} v{version}, sqrt(2) ~ {2 ** 0.5:.3f}')   # Square root of 2 to 3 decimal places

# Common format specifiers are all supported:
f'{3.14159:.2f}'      # '3.14'        Decimal places
f'{255:x}'            # 'ff'          Hexadecimal (X → uppercase)
f'{42:08d}'           # '00000042'    Zero-padded width
f'{"hi":>6}'          # '    hi'      Alignment (< left / > right / ^ center)
f'{1234:,}'           # '1,234'       Thousands separator
f'{0.25:.0%}'         # '25%'         Percentage
f'{"hi"!r}'           # '"hi"'        !r/!s/!a conversion
```

Format specifiers are implemented by the runtime helper `__nekoFmt(value, spec, conv)` (injected at the top of a module only when that `.py` file actually uses specifiers/conversions). Supported specifiers: `.Nf` (decimal places), `e`/`E` (scientific notation), `x`/`X`/`o`/`b` (number bases), `%` (percentage), `d`/`c`, width + alignment `< > ^ =`, `0` zero padding, `,` thousands separator, and precision (string truncation). Ordinary `{expr}` without a specifier remains a plain template literal with no overhead.

<a id="wiki-section-11"></a>
### Generators

Any function containing `yield` is lowered to a JS generator function (`function*`), supported natively by GraalJS:

```python
def squares(n):
    for i in range(n):
        yield i * i

list(squares(4))          # [0, 1, 4, 9]
sum(squares(5))           # 30 (sum spreads first now, so it also works with generators)

def chain(a, b):
    yield from a          # yield from → JS yield*
    yield from b
```

> Note: `list()` / `for x in g()` / `sum()` / `any()` / `all()` / `sorted()`, etc. spread first and can consume generators directly. **Generator expressions** `(expr for x in iter if cond)` are also supported (parenthesized, or as the **sole** argument of `sum()`/`any()`/`list()`, etc.: `f(x for x in xs)`). They lower to immediately invoked `function*` functions.

<a id="wiki-section-12"></a>
### with statements

`with ctx as x:` lowers to acquire / `try` / `finally` **in the same scope** (not wrapped in an IIFE, so `return`/`break`/`continue` pass through normally). If the context object exposes JS `__enter__`/`__exit__` methods (Python-style context managers), they are called; otherwise, the value is assigned directly to the `as` target:

```python
class CM:
    def __init__(self):
        self.entered = False
        self.exited = False
    def __enter__(self):
        self.entered = True
        return self
    def __exit__(self):
        self.exited = True

with CM() as obj:
    obj.entered            # True (__enter__ has run)
# After leaving with, obj.exited is True (__exit__ runs in finally)

with some_value as v:      # Without __enter__/__exit__, bind some_value directly to v
    use(v)
```

Multiple items are supported: `with a as x, b as y:` nests acquire/release operations.

<a id="wiki-section-13"></a>
### Container literals

```python
xs     = [1, 2, 3]        # List → JS array
point  = (4, 5)           # Tuple → JS array
d      = {'a': 1, 'b': 2} # Dictionary → JS object
unique = {1, 2, 2, 3}     # Set → new Set([1, 2, 2, 3])
```

<a id="wiki-section-14"></a>
### Keyword arguments (**kwargs)

Functions declaring `**kwargs` collect keyword arguments into a dict. At the call site, keyword arguments become a trailing marked object `{name: value, __nekoKw: true}`; the function starts with a prologue that rebuilds bindings according to Python rules: **positional arguments take precedence over keyword arguments, which take precedence over defaults**. `*args` collects extra positional arguments, and `**kwargs` collects remaining keyword arguments (those already assigned to named parameters are not added to kwargs).

```python
def g(x, y=10, *rest, **kw):
    return str(x) + str(y) + str(len(rest)) + str(kw.get('z'))

g(1, y=2, z=5, w=9)       # x=1 (positional), y=2 (keyword, overrides default 10), rest=[], kw={'z':5,'w':9}
                         # → '1205'

class Bag:
    def __init__(self, **items):
        self.items = items
    def total(self, **opts):
        return sum(self.items.values()) + opts.get('bonus', 0)

Bag(apple=1, banana=2).total(bonus=10)   # 13
```

> Limit: keyword arguments can only be passed to targets **declaring `**kwargs`** (functions, methods, class `__init__`), or to `print`/`sorted` (which have special handling). Ordinary functions without `**kwargs` report an explicit error when given keyword arguments. `lambda` does not support `**kwargs` (arrow functions do not have `arguments`).

<a id="wiki-section-15"></a>
### Classes

```python
class Animal:
    def __init__(self, name):
        self.name = name

    def __str__(self):
        return f'Animal({self.name})'

    @staticmethod
    def default():
        return Animal('cat')

class Cat(Animal):
    def __init__(self, name, lives=9):
        super().__init__(name)
        self.lives = lives

cat = Cat('Tom')
print(cat)                # Animal(Tom): the subclass does not override __str__, so it uses the parent's
print(Cat.default())      # Animal(cat)
```

Key points:

- `self` in instance methods is rewritten to JS `this`; `@staticmethod` methods are not rewritten.
- `__init__` → `constructor`, `__str__` → `toString`; other method names (including snake_case) remain unchanged.
- `extends` becomes JS `extends`; `super().__init__(args)` → `super(args)`, and `super().method(args)` → `super.method(args)`. **Keyword arguments** and `**` unpacking in `super()` fail at compile time (JS `super()` accepts only positional arguments).
- Class method decorators support `@staticmethod`/`@classmethod`/`@property` (others report explicit errors); see "Decorators" for top-level function and class decorators.
- `@classmethod` lowers to a `static` method with `var cls = this` at the beginning (`cls` is the class when called as `Class.method()`).
- `@property` lowers to a JS getter (`get name() { ... }`, read-only, no setter).
- Instantiating a user-defined class with `Cat('Tom')` automatically lowers to `new Cat('Tom')`.
- Ordinary assignments in a class body (`class C: x = 5`) lower to ES2022 **static class fields** `static x = 5;` (accessed as `C.x`, matching Python class attribute semantics); **multiple assignment / tuple unpacking** in class bodies fails at compile time.
- Class docstrings (bare string statements in the class body) lower to `// docstring:` comments with no runtime effect.

<a id="wiki-section-16"></a>
### Decorators

Top-level function and class decorators become wrapping after definition:

```python
def double(fn):
    def wrapped(x):
        return fn(x) * 2
    return wrapped

@double
def base(x):
    return x + 1

# Equivalent: after defining base, execute base = double(base)
print(base(20))    # (20 + 1) * 2 = 42
```

- `@a` / `@b` / `def f` wraps starting with the nearest decorator, following Python semantics: `f = a(b(f))`.
- The decorator can be a dotted name: `@pkg.helper` → `f = pkg.helper(f)`.
- **Class method** decorators support `@staticmethod`/`@classmethod`/`@property` (other method decorators report errors); **parameterized** decorators `@deco(...)` are also unsupported (with an explicit error). If needed, write an ordinary function that returns a decorator, then reference it with `@that_func`.

<a id="wiki-section-17"></a>
### assert / del

```python
assert x > 0                  # → if (!__nekoTruthy(x > 0)) throw new AssertionError("AssertionError");
assert a == b, '不匹配'        # → throw new AssertionError('不匹配') (AssertionError is a built-in exception prelude class, catchable with except AssertionError)

del d['key']                  # → delete d['key']
del obj.attr                  # → delete obj.attr
```

> `del x` (deleting an ordinary name) is **unsupported**: JS cannot remove a `var` binding, so it fails at compile time. Use `del d[k]` / `del obj.attr` instead.

<a id="wiki-section-18"></a>
### raise

```python
def lookup(key):
    if key is None:
        raise ValueError('key is required')   # → throw ValueError("key is required");
    return key

try:
    lookup(None)
except Exception as e:
    print('caught')

# Bare raise (inside except) rethrows the current exception
try:
    raise ValueError('x')
except ValueError:
    print('handling')
    raise                       # → throw __nekoErr;
```

`raise Expr` lowers to JS `throw Expr;`. **Bare `raise`** inside an `except` clause rethrows the current exception (the nearest one when except clauses are nested). The `from` clause in `raise ... from cause` is parsed but ignored.

<a id="wiki-section-19"></a>
### match / case (pattern matching)

`match subject:` / `case pattern [if guard]:` lowers to an if/else chain with a matched flag: a failed guard or pattern falls through to the next case, and the entire match ends after the first matching case body executes (as in Python). Captured names are bound before the guard is evaluated, so guards can reference captured variables.

```python
def describe(point):
    match point:
        case (0, 0):
            return 'origin'
        case (x, 0):
            return 'x-axis ' + str(x)
        case (0, y):
            return 'y-axis ' + str(y)
        case (x, y) if x == y:
            return 'diagonal'
        case _:
            return 'other'
```

Supported patterns: literals (`1`/`'x'`/`True`/`None`/`-1`), wildcard `_`, name captures, `|` OR patterns (`1 | 2 | 3`), sequence patterns (`[a, *r, b]`, with `*` in any position), mapping patterns (`{'k': v, **rest}`), class patterns (`Cls()` or keyword forms such as `Cls(x=p)`), and `case ... if guard`.

> Limits: class patterns support only **keyword** subpatterns `Cls(attr=pat)` (positional subpatterns require `__match_args__`, which is unsupported). `match` is a soft keyword: it is treated as a match statement only if the line can be scanned as `<subject> :`, so `match` can still be an ordinary variable name (`match = 5`, bare `match`, or a `match(x)` call).

<a id="wiki-section-20"></a>
### Modules and import

`.py` files can **import one another**. `import` / `from ... import ...` becomes real ESM `import`; NekoJS's module resolver loads sibling files by **relative path** (using exactly the same resolution pipeline as `.js`/`.ts`):

```python
# server_scripts/math_utils.py: an importable library
PI = 3.14

def circle_area(r):
    return PI * r * r
```

```python
# server_scripts/main.py: import the library above
from math_utils import circle_area       # import { circle_area } from './math_utils';

print(circle_area(2))                    # 12.56
```

Key points:

- Module names resolve by **relative path**: `foo` → `./foo`, `a.b.c` → `./a/b/c`. The resolver automatically tries extensions such as `.py` / `.js` / `index.*`, so `.js` modules in the same directory can also be imported.
- `import foo` → `import * as foo from './foo'` (namespace, accessed as `foo.x`); `import foo as f` similarly binds to `f`.
- `from foo import a, b` → `import { a, b } from './foo'`; `from foo import a as x` → `import { a as x } from './foo'`.
- Each `.py` file automatically **exports all its top-level definitions** (`def`/`class`/top-level assignment names), so sibling files can directly use `from <file> import <name>` without any additional declaration.
- `from X import *` is **unsupported** (ESM cannot expand a namespace into the current scope). The sole exception is `from nekojs import *` (and named `from nekojs import name`), which is **silently stripped** (nekojs is the type stub entry for IDE/pyright, with no runtime meaning).
- Imports must be at **module top level**, not inside function/class bodies.

> Globals injected by NekoJS (`ServerEvents`, `Item`, `Utils`, …) remain in global scope and can be used directly by name (`ServerEvents.started(...)`), with no import required.

<a id="wiki-section-21"></a>
## Built-in functions

| Python | Equivalent JS lowering | Notes |
|---|---|---|
| `range(stop)` / `range(start, stop[, step])` | `Array.from({length: ...}, ...)` | |
| `len(x)` | `__nekoLen(x)` | Array/string `.length`, `Map`/`Set` `.size`, dict (JS object) `Object.keys().length`; no workaround needed for dict/set |
| `print(...)` | `console.log([...].join(sep))` | `sep=` keyword supported; `end=` ignored |
| `abs(x)` | `Math.abs(x)` | |
| `min(...)` / `min(iterable[, key=])` | `Math.min(...)` (or `reduce` with key) | A single iterable argument is spread automatically; `key=` supported |
| `max(...)` / `max(iterable[, key=])` | `Math.max(...)` (or `reduce` with key) | Same as above |
| `sum(iterable)` | `([...iterable]).reduce((a,b)=>a+b, 0)` | Spreads first; can consume generators |
| `str(x)` | `String(x)` | |
| `int(x[, base])` | `parseInt(x, base)` | Optional base |
| `float(x)` | `Number(x)` | |
| `bool(x)` | `Boolean(x)` | |
| `list()` / `list(iterable)` | `[]` / `[...iterable]` | |
| `dict()` / `dict(iterable)` | `({})` / `Object.fromEntries(iterable)` | |
| `set()` / `set(iterable)` | `new Set()` / `new Set(iterable)` | |
| `tuple(iterable)` | `[...iterable]` | Note: returns a mutable array (JS has no immutable tuple) |
| `sorted(iterable[, reverse=][, key=])` | `[...iterable].sort(comparator)` | Both `reverse`/`key` supported |
| `any(iterable)` / `all(iterable)` | `.some(x=>x)` / `.every(x=>x)` | Spreads first; can consume generators |
| `enumerate(iterable)` | `([...iterable]).map((v, i) => [i, v])` | Returns `[index, value]` pairs |
| `reversed(iterable)` | `[...iterable].reverse()` | |
| `map(f, iterable)` | `[...iterable].map(f)` | **Function first** (as in Python); returns an eager array |
| `filter(pred, iterable)` | `[...iterable].filter(pred)` | **Predicate first** |
| `zip(*iterables)` | Pairs up to the shortest input | Multiple iterables; returns an array of `[a,b]` tuples |
| `round(x[, n])` | `Math.round` (scaled by 10^n when n is provided) | Unlike Python's **banker's rounding** (ties to even), JS `Math.round` rounds ties toward positive infinity: `round(-1.5)` returns `-1`, not `-2`. |
| `divmod(a, b)` | `[Math.floor(a/b), a % b]` | |
| `ord(c)` / `chr(n)` | `codePointAt(0)` / `String.fromCodePoint(n)` | |
| `pow(x, y)` | `Math.pow(x, y)` | Three-argument modular exponentiation unsupported |
| `hex(n)` / `oct(n)` / `bin(n)` | `"-0x"+abs.toString(16)`, etc. | Negative numbers use a `-` prefix |
| `repr(x)` | `JSON.stringify(x)` | Approximation |
| `format(x, spec)` | `__nekoFmt(x, spec, null)` | Same rules as f-string format specifiers |
| `isinstance(x, T)` / `isinstance(x, (A,B))` / `isinstance(x, [A,B])` | `x instanceof T` (tuple/list→chain) | Built-in exception names use the prelude class hierarchy (`isinstance(e, ValueError)` → `e instanceof ValueError`, exact matching) |
| `type(x)` | `(x).constructor` | |
| `callable(x)` | `typeof x === "function"` | |
| `getattr(o, name[, d])` / `hasattr` / `setattr` / `delattr` | `o[name]` bracket access (with defaults/existence checks/assignment/delete) | |
| `iter(x)` / `next(it)` | `x[Symbol.iterator]()` / `it.next().value` | Interoperates with generators/iterators |
| `frozenset(x)` | `new Set(x)` | JS has no immutable set; returns an ordinary Set |

> Common **str/list/dict/set methods** are mapped to JS equivalents (see "Method mappings" below). Unmapped methods pass through unchanged as `obj.method(args)` (and still work if JS has a method with the same name).

<a id="wiki-section-22"></a>
## Method mappings

Common str/list/dict/set methods are automatically transpiled to JS equivalents:

| Type | Mapped methods |
|---|---|
| **str** | `upper lower strip lstrip rstrip find rfind index ljust rjust zfill replace startswith endswith count split` (without arguments, splits on whitespace) `join` |
| **list** | `append` (→push) `copy insert remove pop reverse` |
| **dict** | `keys values items update get` (with default value) |
| **set** | `discard` (→delete); `add` passes through unchanged |

Methods not in the table pass through unchanged (`obj.method(args)`) and work if JS has a method with the same name.

<a id="wiki-section-23"></a>
## Slices

Full `xs[start:stop:step]` semantics are supported (positive or negative steps, including expressions):

- `xs[lo:up]`, `xs[:up]`, `xs[lo:]`, `xs[:]` → `xs.slice(lo, up)`
- Arbitrary steps such as `xs[::2]` (every other item), `xs[::-1]` (reverse), `xs[1::2]`, and `xs[::-2]` → a Python-style helper that correctly handles negative indices, the step's sign at runtime, and defaults for each sign
- Negative indices `xs[-1]` → `xs.slice(-1)[0]` (Python last-element semantics)
- String slices return strings; other sequences return arrays

> A `slice` step of `0` throws (as in Python). Slices use an inline helper, so `step` can be a variable (its sign is evaluated at runtime).

<a id="wiki-section-24"></a>
## try / except

```python
try:
    risky()
except ValueError as e:
    handle_v(e)
except TypeError:
    handle_t()
else:
    ran_without_error()   # Runs only if try reaches its end normally (no exception or return/break/continue)
finally:
    cleanup()
```

Lowered to JS `try/catch[/else]/finally`. Key points:

- **Multiple `except` clauses** are supported and checked in order by **type matching** (instanceof). **Unmatched exceptions are rethrown**, as in Python.
- `except MyErr as e:` → `if (e instanceof MyErr) { var e = e; ... }`
- `except (A, B):` → `e instanceof A || e instanceof B` (multiple parenthesized types)
- Bare `except:` catches everything and must be the **last** except clause.
- **`else` clause**: runs only if the try body reaches its end normally (no exception and not interrupted by `return`/`break`/`continue`). Exceptions in it are **not** caught by the same except clauses, but `finally` still runs.
- **Bare `raise`** inside except rethrows the current exception.
- **Python built-in exception names** (`Exception`, `ValueError`, `TypeError`, `KeyError`, etc.) are defined by the **prelude class hierarchy** injected at the top of the module (`class Exception extends Error`, with built-in exceptions extending it and `prototype.name` corrected). `raise ValueError('x')` → `throw new ValueError("x")`, `except ValueError` → `instanceof ValueError` with **exact matching** (native JS `TypeError` can also be caught by `except TypeError` through a `.name` fallback); `isinstance(e, ValueError)` is exact as well.
- The `e` bound by `as e` is the underlying JS error object.

> Note: `raise 42` (throwing a non-Error value) does **not** match `except Exception` (`42 instanceof Error` is false), and the exception is rethrown. This follows Python semantics. Use bare `except:` to catch arbitrary values.

<a id="wiki-section-25"></a>
## Decorators

See the earlier "Classes → Decorators" section (top-level function/class decorators `@deco` lower to wrapping after definition; class method decorators are limited to `@staticmethod`/`@classmethod`/`@property`; parameterized `@deco(...)` is unsupported).

<a id="wiki-section-26"></a>
## Important Python ↔ JS differences

The transpiler tries to follow Python semantics, but the underlying runtime is JS. Pay attention to these differences:

| Difference | Description |
|---|---|
| **`self` → `this`** | Rewritten only inside **instance method bodies**; `self` in ordinary functions is not replaced. `@staticmethod` methods are not rewritten. |
| **dict is a JS object** | Keys can only be strings (numeric keys are coerced to strings, and integer-like keys such as `"10"` are moved forward in numeric order by JS). Arbitrary hashable objects, such as tuples, cannot be keys. |
| **`//` floor division** | `a // b` → `Math.floor(a / b)`, rounding toward negative infinity as in Python (`-7 // 2 == -4`). |
| **`**` exponentiation** | `a ** b` passes directly through to JS `**`, with the same semantics. |
| **`in` / `not in`** | `x in coll` → `__nekoIn(x, coll)`: array/string `.includes`, own keys of dict (JS objects), `Map`/`Set` `.has`. **No workaround is needed** for dictionary key membership (chained membership `a in b in c` also uses the helper). |
| **`is` / `is not`** | Passes through as JS `===` / `!==` (strict equality). `None` maps to `null`, so `x is None` is equivalent to `x === null`. |
| **`==` / `!=`** | Maps to JS **strict equality** `===` / `!==` (`"1" == 1` is false, as in Python). |
| **`and` / `or`** | `__nekoAnd` / `__nekoOr` helpers: short-circuit evaluation + operand values returned + **Python truthiness** (`[] or 'y'` yields `'y'`). |
| **Integer precision** | Python `int` has arbitrary precision; JS only has double-precision floating-point Number. Integers `> 2^53` lose precision, and `int(x)` has this limit too. Ordinary integer arithmetic is unaffected. |
| **Variable scope** | All assignments emit `var` (function-scoped, redeclarable, hoisted), not `let`/`const`. There is no block scope or TDZ: values assigned in loops/branches remain visible in the enclosing function scope. |
| **Truthiness** | Conditions (`if`/`while`/`not`/`and`/`or`/conditional expressions/`assert`/comprehension filters/`match` guards) all use `__nekoTruthy`: `[]`/`{}`/`''`/`0`/`None` are false, as in Python. **You do not need `len(xs)==0` to check empty containers**. |
| **`None` / `True` / `False`** | Map to JS `null` / `true` / `false` respectively (not `undefined`). |
| **List `+` concatenation (pitfall)** | `[1,2] + [3]` uses JS `+` → string `"1,23"` (Python concatenates lists). The same applies to `+=`. Use `a.extend(b)` / `[*a, *b]` for now. |
| **`"%s" % x` formatting (pitfall)** | Takes the numeric remainder path → silently yields `NaN`. Use f-strings / `format(x, spec)` instead. |
| **set/dict bitwise operators (pitfall)** | `s1 \| s2`, `d1 \| d2`, `&`, `^`, `-` on containers use JS bitwise operations → silently yield `0`. Use methods (`union` is not yet available; convert to a list first) or a form such as `dict(d1, **d2)`. |
| **`set.update` (pitfall)** | Currently affected by the dict update mapping (`Object.assign` on a Set object). Use `for x in other: s.add(x)`. |
| **`print(dict)` (pitfall)** | Dictionaries print as `[object Object]`; use `print(list(d.items()))` to inspect them. |
| **Non-literal negative indices (pitfall)** | `xs[-1]` (literal) correctly selects the last element; however, `xs[i]` when runtime `i<0` → JS property access silently yields `undefined`. For negative indices in loops, use `xs[len(xs)+i]`. |
| **`next(gen)` past the end (pitfall)** | Once an iterator is exhausted, `next()` returns `undefined` (Python raises `StopIteration`). Use a `for` loop instead. |
| **`split(sep, maxsplit)` / `replace(old, new, count)` (pitfall)** | The third argument is ignored by native JS methods: `split` discards the remaining part rather than retaining the tail, and `replace` replaces all occurrences rather than the first count. Write a loop when precise control is required. |
| **`str(e)`** | For built-in exceptions, `str(e)` is `"ValueError: msg"` (with the class name prefix, JS Error.toString style); Python returns only `msg`. Use `e.message` for the message itself. |

<a id="wiki-section-27"></a>
## Limits (still unsupported)

The following syntax/features are **unsupported** in the current version. Some produce **specific compile-time errors** (including filename and location):

- `from X import *` (the sole exception: `from nekojs import *` and named `from nekojs import name` are **silently stripped**, only for IDE/pyright type stubs)
- Matrix multiplication `@` / `@=`
- Parameterized decorators `@deco(...)`, other class method decorators (except `@staticmethod`/`@classmethod`/`@property`)
- `**kwargs` in `lambda`
- Keyword arguments passed to ordinary functions **not declaring `**kwargs`** (only `print`/`sorted` have special handling)

Others produce only **generic parse errors** (no specific message), or **silent lowering**:

- `async` / `await` / async generators → generic parse errors
- `global` / `nonlocal` → generic parse errors
- Dictionary merge operator `d1 | d2` → silently treated as bitwise OR (use `{**d1, **d2}` instead)
- Type annotations are only parsed then discarded; they do not perform runtime type checking (annotation names such as `int` are not evaluated)
- Built-ins not in the table above (such as `id`, `vars`, `globals`, `eval`, `exec`) → silently emitted as JS calls without specific rejection

> Alternatives: use JS equivalents directly, or call NekoJS's JS bindings from `.py` files. The Python subset and JS/TS scripts run in the same runtime and can be mixed.

<a id="wiki-section-28"></a>
## How it works

<a id="wiki-section-29"></a>
### Plugin discovery

Python support comes from `PythonTranspilerPlugin` in common, loaded on the current NeoForge and Fabric nodes:

- **NeoForge**: its plugin loading mechanism discovers the embedded Python plugin.
- **Fabric**: the built-in plugin list includes `PythonTranspilerPlugin`; script authors do not wire it manually.
- **Cleanroom 1.12.2**: a separate legacy branch; this repository does not verify its discovery or wiring mechanism.

The plugin registers the `.py` extension with `PythonToJsCompiler`, so `.py` files placed in script directories load just like `.js`/`.ts` files.

<a id="wiki-section-30"></a>
### Transpilation pipeline

```
.py source
   │  PythonLexer (including INDENT/DEDENT tokens)
   ▼
Python AST (recursive-descent PythonParser)
   │  PythonEmitter (lowering to JS source)
   ▼
JavaScript  +  v3 source map
   │  (executed by GraalJS, in the same runtime as JS/TS)
   ▼
Execution
```

<a id="wiki-section-31"></a>
### Source maps

`PythonToJsCompiler#compileDetailed` returns JS along with a **statement-by-statement v3 source map** (built by `PythonSourceMap`). The first line of each statement has a 4-field segment mapping generated JS lines back to the original `.py` lines. Runtime stack traces can therefore locate Python source lines instead of the lowered JS lines.

Transpilation or parsing failures throw `IllegalArgumentException`, with messages containing the filename and line/column location, for example:

```
python transpile failed in server_scripts/foo.py: python parse error at line 12, col 5: ...
```

<a id="wiki-section-32"></a>
### Coexistence with JS/TS

Python and JS/TS scripts share:

- The same GraalJS runtime and sandbox configuration;
- The same global bindings (`Item`, `ServerEvents`, `global`, `Utils`, ...);
- The same reload / error handling / `logs/nekojs/<type>.log` mechanisms.

You can mix `.js`, `.ts`, and `.py` in one modpack without interference.

<a id="wiki-section-33"></a>
## Next steps

- [Script basics](script-basics_us) - Script types, lifecycles, and reload behavior.
- [Global bindings](global-bindings_us) - Runtime top-level APIs; editor hints are obtained through `from nekojs import ...`.
- [Probe type generation](probe-type-generation_us) - Python stubs, editor configuration, and completion troubleshooting.
- [TypeScript and JSX](typescript-and-jsx_us) - Another built-in language frontend.
- [FAQ](faq_us) - Troubleshooting errors.

<!-- wiki-nav -->

---

[Previous: Global bindings](global-bindings_us) · [Contents](Home) · [Next: Python feature support](python-feature-support_us)
