<!-- wiki-page: python-scripts; locale: cn -->

> **中文** · [English](python-scripts_us)

<a id="wiki-section-1"></a>
# Python 脚本

NekoJS 本体内置了 Python 支持。把 `.py` 文件放进脚本目录，它就会和 `.js` 一样被加载。

不需要安装 Python，也不需要 `pip install` 任何东西。NekoJS 会先把 Python 代码转成 JavaScript，再交给 Graal 执行。所以 `ServerEvents`、`Item` 这些绑定在 Python 里用法完全一样。

要注意，这不是完整的 Python。它只支持一部分语法和内置函数，也没有 Python 标准库。本页会讲清楚哪些能用、哪些不能用。

<a id="wiki-section-2"></a>
## 第一个 Python 脚本

新建 `nekojs/server_scripts/hello.py`：

```python
from nekojs import *

print('Hello from Python!')
```

执行 `/nekojs reload`，然后打开 `logs/nekojs/server.log`，就能看到这行输出。`print` 的内容会写进对应脚本类型的日志。

四个脚本目录都可以放 `.py`，生效方式和 `.js` 一样：服务端脚本用 `/nekojs reload`，启动脚本要重启游戏。见 [脚本基础](script-basics_cn)。

<a id="wiki-section-3"></a>
## 开启代码补全

想在 VS Code 里得到补全，需要做三件事：

1. 在游戏里执行 `/nekojs probe python`。它会在游戏目录下生成 `.neko_probe/python/`，里面是给编辑器看的类型声明。只执行 `/nekojs probe` 只会生成 TypeScript 的声明，不会生成 Python 的。`/nekojs probe all` 会同时生成两种。
2. 在每个 `.py` 文件顶部写 `from nekojs import *`。
3. 在 VS Code 里装好 Python 扩展和 Pylance，然后打开游戏目录、`nekojs/` 或某个脚本目录作为工作区。

导入可以写成下面两种之一：

```python
from nekojs import *

# 或者只导入用到的名字
from nekojs import Item, ServerEvents
```

这一行只是给编辑器看的。NekoJS 转换代码时会把它直接去掉，运行时的绑定本来就存在。所以不写它脚本也能跑，只是编辑器不认识 `ServerEvents` 这些名字。

不要写成 `import nekojs`，也不要用 `as` 起别名（比如 `from nekojs import Item as I`）。NekoJS 只会去掉 `from nekojs import ...` 这种写法。`import nekojs` 会被当成导入一个叫 `nekojs` 的文件而报错；别名会在运行时变成未定义的名字。

还是没有补全时，按顺序检查：

- `.neko_probe/python/` 是否存在。不存在就重新执行 `/nekojs probe python`。
- 文件顶部是否有上面那行导入。
- VS Code 打开的是不是游戏目录、`nekojs/` 或脚本目录本身，而不是它们的上一级文件夹。probe 只在这些目录里写了 `pyrightconfig.json` 和 `.vscode/settings.json`。
- 新建了放 `.py` 的子文件夹，或者加了新模组之后，再执行一次 `/nekojs probe python`。

更多配置细节见 [Probe 类型生成](probe-type-generation_cn)。

<a id="wiki-section-4"></a>
## 一个实际的例子

用 Python 改配方，写在 `server_scripts/recipes.py`：

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

回调可以是普通的 `def` 函数，也可以是 `lambda`。写多行逻辑时用 `def` 更清楚。

<a id="wiki-section-5"></a>
## 常用写法

下面这些写法都可以直接用。

<a id="wiki-section-6"></a>
### 函数

默认参数、`*args`、`**kwargs` 都支持：

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

只有声明了 `**kwargs` 的函数才能用关键字传参。上面的 `greet` 没有 `**kwargs`，所以 `greet(name='x')` 会报错。内置的 `print(sep=...)`、`sorted(key=..., reverse=...)`、`min(key=...)`、`max(key=...)` 例外。

<a id="wiki-section-7"></a>
### 类

支持 `__init__`、单继承、`super()`、`@staticmethod`、`@classmethod`、`@property` 和 `__str__`：

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

几点区别：

- 只支持一个父类。
- `@property` 只读，不能写 setter。
- 类体里直接写的 `x = 5` 是每个实例各有一份，用 `self.x` 读，不是 Python 那种共享的类属性。
- 在 `@classmethod` 里创建实例要写类名，比如 `Counter()`。写 `cls()` 会报错。

<a id="wiki-section-8"></a>
### 字符串格式化

f-string 支持常用的格式说明：

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

`format(x, '.2f')` 和 `%` 格式化也能用。`'{}'.format(x)` 这种 `str.format` 方法**不支持**，请改用 f-string。

<a id="wiki-section-9"></a>
### 推导式和生成器

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

列表、字典、集合推导式，多层 `for`，`yield`、`yield from` 和生成器表达式都支持。

<a id="wiki-section-10"></a>
### 异常

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

多个 `except`、`except (A, B)`、`else`、`finally`、裸 `raise` 都支持。`ValueError`、`TypeError`、`KeyError` 等常见内置异常都可以抛出和捕获。取错误信息请用 `e.message`；`str(e)` 会带上类名，比如 `ValueError: ...`。

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

支持字面量、`_`、捕获名字、`|`、列表模式（可带 `*rest`）、字典模式、`case ... if 条件`。类模式只能写关键字形式，比如 `case Point(x=0):`。字典模式的值只能是一个名字，不能再嵌套模式。

<a id="wiki-section-12"></a>
### with 和装饰器

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

`__exit__` 不会收到异常参数，只做清理用。装饰器只能写名字（`@double`、`@pkg.helper`），不能带参数（`@deco(1)` 会报错）。

<a id="wiki-section-13"></a>
## 在文件之间导入

`.py` 文件可以导入同一目录下的其他 `.py` 或 `.js` 文件。每个文件顶层定义的函数、类和变量都可以被导入。

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

规则：

- 模块名按相对当前文件的路径找：`foo` 就是同目录的 `foo.py`（或 `foo.js`），`lib.kits` 就是 `lib/kits.py`。
- `import` 只能写在文件顶层，不能写在函数里。
- 不支持 `from .foo import x` 这种以点开头的写法，也不支持 `from foo import *`（`from nekojs import *` 除外）。
- 从别的文件导入的类，不能直接用 `Box(1)` 创建实例。请在那个文件里提供一个创建函数（比如上面的 `make_kit`），再导入这个函数。
- 没有 Python 标准库。`import math`、`from typing import List` 会被当成导入同目录的文件而报错。数学函数可以直接用 JavaScript 的 `Math`，比如 `Math.floor(x)`。

`ServerEvents`、`Item` 等 NekoJS 绑定不需要导入，直接用。

<a id="wiki-section-14"></a>
## 支持情况一览

| 写法 | 能用吗 | 说明 |
|---|---|---|
| `if` / `elif` / `else`、`for`、`while`、`for ... else` | 能 | |
| `def`、默认参数、`*args`、`**kwargs`、`lambda` | 能 | `lambda` 里不能用 `**kwargs` |
| `class`、`super()`、`@staticmethod` / `@classmethod` / `@property` | 能 | 只支持一个父类 |
| 推导式、生成器、`yield` / `yield from` | 能 | |
| f-string、`%` 格式化、`format()` | 能 | `str.format` 方法不能用 |
| `try` / `except` / `else` / `finally`、`raise` | 能 | `raise ... from ...` 的 `from` 部分会被忽略 |
| `match` / `case` | 部分 | 见上文 |
| `with`、装饰器 | 能 | 装饰器不能带参数 |
| 切片 `xs[1:3]`、`xs[::-1]`、`xs[-1]` | 能 | |
| `a, b = b, a`、`a = b = 0`、`+=` 等 | 能 | `a, *rest = xs` 不能用 |
| 海象运算符 `:=` | 能 | 变量要先赋过值，见“已知问题” |
| `assert`、`del d[k]`、`del obj.attr` | 能 | `del x`（删除变量）不能用 |
| 类型注解 `x: int = 5`、`-> str` | 能 | 会被忽略，不做检查 |
| `import` / `from ... import ...` | 能 | 只能导入脚本目录里的文件 |

<a id="wiki-section-15"></a>
## 内置函数和方法

这些内置函数可以用：

`range`、`len`、`print`、`abs`、`min`、`max`、`sum`、`round`、`divmod`、`pow`、`str`、`int`、`float`、`bool`、`list`、`dict`、`set`、`tuple`、`frozenset`、`sorted`、`reversed`、`enumerate`、`zip`、`map`、`filter`、`any`、`all`、`ord`、`chr`、`hex`、`oct`、`bin`、`repr`、`format`、`isinstance`、`type`、`callable`、`getattr`、`hasattr`、`setattr`、`delattr`、`iter`、`next`。

不在这个列表里的（比如 `input`、`open`、`eval`、`id`）都不能用。

下面这些常用方法会自动转换成 JavaScript 的对应写法：

| 类型 | 支持的方法 |
|---|---|
| 字符串 | `upper` `lower` `strip` `lstrip` `rstrip` `find` `rfind` `index` `count` `replace` `split` `join` `startswith` `endswith` `ljust` `rjust` `zfill` |
| 列表 | `append` `insert` `remove` `pop` `sort` `reverse` `copy` |
| 字典 | `get` `keys` `values` `items` `update` |
| 集合 | `add` `discard` |

`strip()` 这类方法只支持不带参数的写法。其他没列出的方法会原样调用，对象本身有这个方法就能用。

> **NekoJS 对象上的同名方法**：上表的转换按方法名进行，不看对象类型。如果 NekoJS 或模组提供的对象恰好有同名方法（比如配方事件的 `event.find(...)`、文本的 `.append(...)`），它会被错误地转换。这时改用方括号调用：`event['find']({'output': 'minecraft:stone'})`。

<a id="wiki-section-16"></a>
## 和标准 Python 不一样的地方

代码最终以 JavaScript 运行，下面这些地方结果会和 Python 不同：

| 写法 | 区别 | 建议 |
|---|---|---|
| 字典的键 | 只能是字符串，数字键会变成字符串，不能用元组当键 | 用字符串做键 |
| `list(d)`、`iter(d)`（`d` 是字典） | 会报错 | 用 `d.keys()` 或 `for k in d:` |
| `==` 比较列表或字典 | 只判断是不是同一个对象，不比较内容 | 自己逐项比较 |
| `xs[i]`，`i` 是负数变量 | 得到 `None`，不会从末尾取 | 写 `xs[len(xs) + i]`；直接写 `xs[-1]` 没问题 |
| `round(2.5)`、`round(-1.5)` | 得到 `3` 和 `-1`，不是 Python 的 `2` 和 `-2` | 需要精确舍入时自己处理 |
| 很大的整数 | 超过 2^53 会丢失精度 | 避免依赖大整数运算 |
| `int('abc')` | 得到 NaN，不会抛 `ValueError` | 先检查字符串内容 |
| `next(gen)` 取完以后 | 返回 `None`，不抛 `StopIteration` | 用 `for` 遍历 |
| 集合的 `\|` `&` `-`、字典的 `\|` | 不是集合或字典运算，结果没有意义 | 字典合并用 `{**a, **b}` |
| `s.update(...)`（`s` 是集合） | 不会往集合里加元素 | 用 `for x in other: s.add(x)` |
| 函数里给外层变量赋值 | 会新建一个局部变量；也没有 `global` / `nonlocal` | 把要修改的数据放进字典或列表里改 |
| `print(..., end='')` | `end` 被忽略，每次都换行 | 先拼好字符串再打印 |

`True` / `False` / `None` 照常使用。空列表、空字典、空字符串在 `if` 里都按假处理，和 Python 一样。

<a id="wiki-section-17"></a>
## 不支持的写法

| 写法 | 替代办法 |
|---|---|
| `async` / `await` | 没有替代，改用事件回调 |
| `global` / `nonlocal` | 把状态放进字典或列表 |
| `from X import *`（`nekojs` 除外） | 写出要导入的名字 |
| `import nekojs`、`from nekojs import X as Y` | 用 `from nekojs import *` 或不带别名的 `from nekojs import X` |
| 带参数的装饰器 `@deco(...)` | 写一个返回装饰器的函数，再用 `@名字` 引用 |
| 多个父类 | 改成单继承或组合 |
| 仅限位置参数 `def f(a, /, b)`、仅限关键字参数 `def f(a, *, b)` | 用普通参数，或改用 `**kwargs` |
| `str.format` | f-string |
| `a, *rest = xs` | `a = xs[0]`，`rest = xs[1:]` |
| 矩阵乘 `@` | 自己写函数 |
| `b'...'` 字节串、复数、`...` | 不支持 |
| Python 标准库（`math`、`json`、`re` 等） | 用 JavaScript 自带的 `Math`、`JSON` 或 NekoJS 绑定 |

<a id="wiki-section-18"></a>
## 脚本属性

`.py` 文件目前不能设置 `priority`、`after` 等脚本属性。NekoJS 只读取文件开头的 `//` 注释，而 `//` 在 Python 里是语法错误；写成 `# priority: 10` 则不会被读取。需要控制加载顺序时，把这部分逻辑放进 `.js` 文件。见 [脚本属性](script-properties_cn)。

<a id="wiki-section-19"></a>
## 报错怎么看

Python 写错时，错误信息会写明文件和行列号：

```text
python transpile failed in server_scripts/foo.py: python parse error at line 12, col 5: ...
```

排查方法和其他脚本一样：执行 `/nekojs error`，或者查看 `logs/nekojs/<类型>.log`。运行时报错的行号也会对应到 `.py` 源码。

<a id="wiki-section-20"></a>
## 已知问题

- **`isinstance` 检查内置类型**：同一个文件里，如果既用了 `isinstance(x, str)` 这类内置类型检查（`int`、`float`、`str`、`list`、`dict` 等），又用了 `print`、`str()` 或 `%` 格式化，整个文件会加载失败，报 `Identifier '__nekoPyStr' has already been declared`。检查自己定义的类或异常类型不受影响。在修复之前，把内置类型检查放到单独的文件里。
- **海象运算符**：`:=` 赋值的变量必须先用普通赋值定义过，否则运行时报“未定义”。先写一行 `n = 0`，再用 `if (n := len(items)) > 3:`。

<a id="wiki-section-21"></a>
## 接下来

- [脚本基础](script-basics_cn)：脚本类型、reload、出错了怎么查。
- [Python 特性清单](python-feature-support_cn)：新版本 Python 的各项特性能不能用。
- [全局绑定](global-bindings_cn)：可以直接使用的 `Item`、`Text` 等工具。
- [Probe 类型生成](probe-type-generation_cn)：补全文件的生成和编辑器配置。
- [常见问题](faq_cn)：遇到问题先来这里。

<!-- wiki-nav -->

---

[上一篇: 数据、状态与持久化](data-and-state_cn) · [目录](Home) · [下一篇: Python 特性清单](python-feature-support_cn)
