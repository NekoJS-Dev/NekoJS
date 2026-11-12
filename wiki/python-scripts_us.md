<!-- wiki-page: python-scripts; locale: us -->

> **English** · [中文](python-scripts_cn)

<a id="wiki-section-1"></a>
# Python scripts

Python support is built into NekoJS. Put a `.py` file in a script folder and it loads just like a `.js` file.

You don't need to install Python, and you don't need to `pip install` anything. NekoJS converts your Python code to JavaScript and runs it on Graal. That's why bindings such as `ServerEvents` and `Item` work exactly the same way in Python.

Keep in mind that this is not full Python. Only part of the syntax and built-in functions are supported, and there is no Python standard library. This page explains what works and what doesn't.

<a id="wiki-section-2"></a>
## Your first Python script

Create `nekojs/server_scripts/hello.py`:

```python
from nekojs import *

print('Hello from Python!')
```

Run `/nekojs reload`, then open `logs/nekojs/server.log` to see the line. Output from `print` goes to the log for that script type.

You can put `.py` files in any of the four script folders. They take effect the same way as `.js` files: `/nekojs reload` for server scripts, a game restart for startup scripts. See [Script basics](script-basics_us).

<a id="wiki-section-3"></a>
## Setting up code completion

To get completion in VS Code, do three things:

1. Run `/nekojs probe python` in game. It creates `.neko_probe/python/` in the game folder, which holds type declarations for your editor. Plain `/nekojs probe` only generates TypeScript declarations, not Python ones. `/nekojs probe all` generates both.
2. Add `from nekojs import *` at the top of every `.py` file.
3. Install the Python extension and Pylance in VS Code, then open the game folder, `nekojs/`, or one of the script folders as your workspace.

The import can take either of these forms:

```python
from nekojs import *

# or import only the names you use
from nekojs import Item, ServerEvents
```

This line exists only for your editor. NekoJS removes it when converting the code, because the bindings already exist at runtime. A script without it still runs, but the editor won't recognize names like `ServerEvents`.

Don't write `import nekojs`, and don't rename with `as` (for example `from nekojs import Item as I`). NekoJS only removes the `from nekojs import ...` form. `import nekojs` is treated as importing a file called `nekojs` and fails; an alias becomes an undefined name at runtime.

If completion still doesn't work, check these in order:

- Does `.neko_probe/python/` exist? If not, run `/nekojs probe python` again.
- Is the import line at the top of the file?
- Is VS Code open on the game folder, `nekojs/`, or a script folder itself, rather than a folder above them? Probe writes `pyrightconfig.json` and `.vscode/settings.json` only into those folders.
- After adding a new subfolder with `.py` files, or installing new mods, run `/nekojs probe python` again.

For configuration details, see [Probe type generation](probe-type-generation_us).

<a id="wiki-section-4"></a>
## A practical example

Change recipes with Python in `server_scripts/recipes.py`:

```python
from nekojs import ServerEvents

STICK_SOURCES = {
    'minecraft:cobblestone': 4,
    'minecraft:bamboo': 2,
}

def add_recipes(event):
    event.remove({'output': 'minecraft:stick'})
    for item, count in STICK_SOURCES.items():
        event.shapeless(f'{count}x minecraft:stick', [item])

ServerEvents.recipes(add_recipes)
```

A callback can be a normal `def` function or a `lambda`. Use `def` when the logic spans several lines.

<a id="wiki-section-5"></a>
## Common patterns

Everything below works as written.

<a id="wiki-section-6"></a>
### Functions

Default arguments, `*args` and `**kwargs` are supported:

```python
def greet(name, prefix='Hello'):
    return f'{prefix}, {name}!'

def total(*nums):
    return sum(nums)

def describe(name, **opts):
    color = opts.get('color', 'white')
    return f'{name} ({color})'

print(greet('NekoJS'))              # Hello, NekoJS!
print(total(1, 2, 3))               # 6
print(describe('gem', color='red')) # gem (red)
```

You can pass keyword arguments only to functions that declare `**kwargs`. `greet` above has no `**kwargs`, so `greet(name='x')` is an error. The built-ins `print(sep=...)`, `sorted(key=..., reverse=...)`, `min(key=...)` and `max(key=...)` are exceptions.

<a id="wiki-section-7"></a>
### Classes

`__init__`, single inheritance, `super()`, `@staticmethod`, `@classmethod`, `@property` and `__str__` are supported:

```python
class Counter:
    def __init__(self, start=0):
        self.value = start

    def add(self, n):
        self.value += n
        return self

    @property
    def doubled(self):
        return self.value * 2

    def __str__(self):
        return f'Counter({self.value})'

class LoudCounter(Counter):
    def add(self, n):
        print('adding', n)
        return super().add(n)

c = LoudCounter(5).add(3)
print(c, c.doubled)   # Counter(8) 16
```

Differences from Python:

- A class can have only one base class.
- `@property` is read-only; setters are not supported.
- `x = 5` written directly in the class body gives each instance its own copy. Read it with `self.x`. It is not a shared class attribute.
- Inside a `@classmethod`, create instances with the class name, such as `Counter()`. `cls()` fails.

<a id="wiki-section-8"></a>
### String formatting

f-strings support the usual format specs:

```python
price = 3.14159
print(f'{price:.2f}')      # 3.14
print(f'{255:x}')          # ff
print(f'{42:05d}')         # 00042
print(f'{"hi":>6}|')       #     hi|
print(f'{1234567:,}')      # 1,234,567
print(f'{0.25:.0%}')       # 25%
print('%s has %d items' % ('box', 3))
```

`format(x, '.2f')` and `%` formatting also work. The `str.format` method (`'{}'.format(x)`) is **not** supported; use an f-string instead.

<a id="wiki-section-9"></a>
### Comprehensions and generators

```python
evens = [n for n in range(10) if n % 2 == 0]
pairs = [(i, j) for i in range(2) for j in range(2) if i != j]
lengths = {name: len(name) for name in ['oak', 'birch']}

def squares(n):
    for i in range(n):
        yield i * i

print(list(squares(4)))                    # [0, 1, 4, 9]
print(sum(x for x in squares(4) if x > 1)) # 13
```

List, dict and set comprehensions, nested `for` clauses, `yield`, `yield from` and generator expressions all work.

<a id="wiki-section-10"></a>
### Exceptions

```python
def parse_count(text):
    try:
        n = int(text)
        if n < 0:
            raise ValueError('count must not be negative')
        return n
    except ValueError as e:
        print('bad count:', e.message)
        return 0
    finally:
        print('checked', text)
```

Multiple `except` clauses, `except (A, B)`, `else`, `finally` and bare `raise` are supported. Common built-in exceptions such as `ValueError`, `TypeError` and `KeyError` can be raised and caught. Use `e.message` for the error text; `str(e)` includes the class name, as in `ValueError: ...`.

<a id="wiki-section-11"></a>
### match / case

```python
def describe(point):
    match point:
        case [0, 0]:
            return 'origin'
        case [x, 0]:
            return f'on x axis at {x}'
        case {'id': item_id}:
            return f'item {item_id}'
        case _:
            return 'something else'
```

Supported patterns: literals, `_`, capture names, `|`, list patterns (with optional `*rest`), dict patterns, and `case ... if condition`. Class patterns must use keywords, such as `case Point(x=0):`. Values in a dict pattern must be plain names, not nested patterns.

<a id="wiki-section-12"></a>
### with and decorators

```python
class Timer:
    def __enter__(self):
        print('start')
        return self

    def __exit__(self):
        print('stop')

with Timer() as t:
    print('working')

def double(fn):
    def wrapped(x):
        return fn(x) * 2
    return wrapped

@double
def base(x):
    return x + 1

print(base(20))   # 42
```

`__exit__` receives no exception arguments; use it for cleanup only. Decorators must be plain names (`@double`, `@pkg.helper`). Decorators with arguments (`@deco(1)`) are an error.

<a id="wiki-section-13"></a>
## Importing between files

A `.py` file can import other `.py` or `.js` files from the script folder. Every function, class and variable defined at the top level of a file can be imported.

```python
# server_scripts/lib/kits.py
def make_kit(name, items):
    return {'name': name, 'items': items}
```

```python
# server_scripts/main.py
from lib.kits import make_kit

kit = make_kit('starter', ['minecraft:bread'])
```

Rules:

- Module names are paths relative to the current file: `foo` is `foo.py` (or `foo.js`) in the same folder, and `lib.kits` is `lib/kits.py`.
- `import` must be at the top level of the file, not inside a function.
- Relative imports starting with a dot (`from .foo import x`) and `from foo import *` are not supported. `from nekojs import *` is the only exception.
- You can't create an instance of an imported class directly with `Box(1)`. Provide a function in that file that creates it (like `make_kit` above) and import the function.
- There is no Python standard library. `import math` or `from typing import List` is treated as importing a file from the same folder and fails. For math, use JavaScript's `Math` directly, for example `Math.floor(x)`.

NekoJS bindings such as `ServerEvents` and `Item` need no import; use them directly.

<a id="wiki-section-14"></a>
## Support at a glance

| Syntax | Works? | Notes |
|---|---|---|
| `if` / `elif` / `else`, `for`, `while`, `for ... else` | Yes | |
| `def`, default arguments, `*args`, `**kwargs`, `lambda` | Yes | No `**kwargs` in a `lambda` |
| `class`, `super()`, `@staticmethod` / `@classmethod` / `@property` | Yes | One base class only |
| Comprehensions, generators, `yield` / `yield from` | Yes | |
| f-strings, `%` formatting, `format()` | Yes | The `str.format` method doesn't work |
| `try` / `except` / `else` / `finally`, `raise` | Yes | The `from` part of `raise ... from ...` is ignored |
| `match` / `case` | Partly | See above |
| `with`, decorators | Yes | Decorators can't take arguments |
| Slices `xs[1:3]`, `xs[::-1]`, `xs[-1]` | Yes | |
| `a, b = b, a`, `a = b = 0`, `+=` and similar | Yes | `a, *rest = xs` doesn't work |
| Walrus operator `:=` | Yes | Assign the variable first; see "Known issues" |
| `assert`, `del d[k]`, `del obj.attr` | Yes | `del x` (deleting a variable) doesn't work |
| Type annotations `x: int = 5`, `-> str` | Yes | Ignored, not checked |
| `import` / `from ... import ...` | Yes | Only for files in the script folders |

<a id="wiki-section-15"></a>
## Built-in functions and methods

These built-in functions are available:

`range`, `len`, `print`, `abs`, `min`, `max`, `sum`, `round`, `divmod`, `pow`, `str`, `int`, `float`, `bool`, `list`, `dict`, `set`, `tuple`, `frozenset`, `sorted`, `reversed`, `enumerate`, `zip`, `map`, `filter`, `any`, `all`, `ord`, `chr`, `hex`, `oct`, `bin`, `repr`, `format`, `isinstance`, `type`, `callable`, `getattr`, `hasattr`, `setattr`, `delattr`, `iter`, `next`.

Anything not on this list (such as `input`, `open`, `eval` or `id`) is not available.

These common methods are converted to their JavaScript equivalents automatically:

| Type | Supported methods |
|---|---|
| String | `upper` `lower` `strip` `lstrip` `rstrip` `find` `rfind` `index` `count` `replace` `split` `join` `startswith` `endswith` `ljust` `rjust` `zfill` |
| List | `append` `insert` `remove` `pop` `sort` `reverse` `copy` |
| Dict | `get` `keys` `values` `items` `update` |
| Set | `add` `discard` |

Methods like `strip()` work only without arguments. Methods not listed are called as written, so they work if the object really has that method.

> **Same-named methods on NekoJS objects**: the conversion above goes by method name, not by object type. If an object from NekoJS or a mod has a method with the same name (such as the recipe event's `event.find(...)` or a text value's `.append(...)`), the call is converted by mistake. Call it with brackets instead: `event['find']({'output': 'minecraft:stone'})`.

<a id="wiki-section-16"></a>
## Differences from standard Python

The code finally runs as JavaScript, so these cases give different results from Python:

| Code | Difference | What to do |
|---|---|---|
| Dict keys | Keys can only be strings. Number keys become strings, and tuples can't be keys | Use string keys |
| `list(d)`, `iter(d)` where `d` is a dict | Error | Use `d.keys()` or `for k in d:` |
| `==` on lists or dicts | Checks whether both are the same object, not whether the contents match | Compare items yourself |
| `xs[i]` where `i` is a negative variable | Gives `None` instead of counting from the end | Write `xs[len(xs) + i]`; a literal `xs[-1]` is fine |
| `round(2.5)`, `round(-1.5)` | Gives `3` and `-1`, not Python's `2` and `-2` | Handle rounding yourself when it matters |
| Very large integers | Precision is lost above 2^53 | Don't rely on big-integer math |
| `int('abc')` | Gives NaN instead of raising `ValueError` | Check the string first |
| `next(gen)` after the last item | Returns `None` instead of raising `StopIteration` | Loop with `for` |
| Set `\|` `&` `-`, dict `\|` | Not set or dict operations; the result is meaningless | Merge dicts with `{**a, **b}` |
| `s.update(...)` where `s` is a set | Doesn't add elements to the set | Use `for x in other: s.add(x)` |
| Assigning to an outer variable inside a function | Creates a new local variable; there is no `global` / `nonlocal` | Keep data you need to change in a dict or list |
| `print(..., end='')` | `end` is ignored; every call ends the line | Build the string first, then print it |

`True`, `False` and `None` work as usual. Empty lists, dicts and strings count as false in `if`, just like in Python.

<a id="wiki-section-17"></a>
## Unsupported syntax

| Syntax | Alternative |
|---|---|
| `async` / `await` | None; use event callbacks |
| `global` / `nonlocal` | Keep state in a dict or list |
| `from X import *` (except `nekojs`) | List the names you import |
| `import nekojs`, `from nekojs import X as Y` | Use `from nekojs import *` or an unaliased `from nekojs import X` |
| Decorators with arguments `@deco(...)` | Write a function that returns the decorator, then use `@name` |
| Multiple base classes | Use single inheritance or composition |
| Positional-only `def f(a, /, b)` and keyword-only `def f(a, *, b)` parameters | Use plain parameters, or `**kwargs` |
| `str.format` | f-strings |
| `a, *rest = xs` | `a = xs[0]` and `rest = xs[1:]` |
| Matrix multiplication `@` | Write a function |
| `b'...'` byte strings, complex numbers, `...` | Not supported |
| Python standard library (`math`, `json`, `re` and so on) | Use JavaScript's built-in `Math` and `JSON`, or NekoJS bindings |

<a id="wiki-section-18"></a>
## Script properties

`.py` files can't set script properties such as `priority` or `after` yet. NekoJS reads only `//` comments at the start of a file, and `//` is a syntax error in Python. `# priority: 10` is not read. If you need to control load order, put that logic in a `.js` file. See [Script properties](script-properties_us).

<a id="wiki-section-19"></a>
## Reading errors

When your Python code has a mistake, the error shows the file, line and column:

```text
python transpile failed in server_scripts/foo.py: python parse error at line 12, col 5: ...
```

Troubleshoot the same way as other scripts: run `/nekojs error` or check `logs/nekojs/<type>.log`. Line numbers in runtime errors also point to your `.py` source.

<a id="wiki-section-20"></a>
## Known issues

- **`isinstance` with built-in types**: if one file both checks a built-in type with `isinstance` (such as `int`, `float`, `str`, `list` or `dict`) and uses `print`, `str()` or `%` formatting, the whole file fails to load with `Identifier '__nekoPyStr' has already been declared`. Checks against your own classes or exception types are not affected. Until this is fixed, put built-in type checks in a separate file.
- **Walrus operator**: a variable assigned with `:=` must already be defined by a normal assignment, or you get an "is not defined" error at runtime. Write `n = 0` first, then `if (n := len(items)) > 3:`.

<a id="wiki-section-21"></a>
## Next steps

- [Script basics](script-basics_us): script types, reloading, and finding errors.
- [Python feature support](python-feature-support_us): which features from recent Python versions work.
- [Global bindings](global-bindings_us): ready-to-use tools such as `Item` and `Text`.
- [Probe type generation](probe-type-generation_us): generating completion files and editor setup.
- [FAQ](faq_us): check here first when something goes wrong.

<!-- wiki-nav -->

---

[Previous: Data, state and persistence](data-and-state_us) · [Contents](Home) · [Next: Python feature support](python-feature-support_us)
