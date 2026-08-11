# Python 脚本

NekoJS **本体内置** Python 子集支持，可以直接用 `.py` 写脚本，无需额外编译步骤或外部 Python 运行时。

> 这是一个**子集转译器（transpiler）**，不是完整的 Python 解释器。Python 源码会被解析成 AST、lowering 成 JavaScript，最终交给 GraalJS 执行。所以你能直接用 NekoJS 的全部 JS 绑定（`Item`、`Utils`、`ServerEvents` ...），但只能用下文列出的那部分 Python 语法和内置函数。

## 快速上手

把 `.py` 文件丢进任意脚本目录（`startup_scripts/`、`server_scripts/`、`client_scripts/`、`test_scripts/`），它会和 `.js`/`.ts` 一样被自动加载——无需任何配置。

```python
# server_scripts/hello.py

# 监听「服务端启动完成」事件
ServerEvents.started(lambda e: print('[NekoJS] python 脚本已加载！'))
```

进世界或执行 `/nekojs reload`，日志里应出现 `[NekoJS] python 脚本已加载！`。

> 注意：脚本类型、reload 行为、可用 API 与 JS 脚本**完全一致**，详见 [脚本基础](脚本基础)。`.py` 只是另一种被支持的扩展名。

### 一个更现实的例子

```python
# server_scripts/recipes.py

def add_sticks(event, output, count):
    """一行合成：count 个木棍。"""
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

## 支持的语法

| 语法 | 状态 |
|---|---|
| 函数 `def f(a, b=1, *args):`（默认参数、`*args`） | 支持，降级为 `function`（hoisted） |
| `if` / `elif` / `else` | 支持，降级为 `if / else if / else` |
| `for x in iter:` / `while cond:` | 支持，`for...of` / `while` |
| 赋值：`=`、增强 `+=`/`-=`/...`//=`/`**=`、元组解包 `a, b = ...`、多目标 `a = b = v` | 支持 |
| `lambda` | 支持，降级为箭头函数 |
| **单层**推导式：列表 `[expr for x in iter if cond]`、字典 `{k:v for ...}`、集合 `{e for ...}`（可选 `if`） | 支持，降级为 `.filter().map()` / `Object.fromEntries` / `new Set` |
| 切片 `xs[lo:up]`、`xs[::-1]`、负下标 `xs[-1]` | 支持（详见「切片」节；除 `[::-1]` 外步长不支持） |
| `try` / `except` / `finally`（多个 except、`except (A, B)` 多类型、instanceof 类型匹配） | 支持（详见「try / except」节） |
| 关键字参数 `print(sep=)`、`sorted(reverse=)` | 支持（其余调用带 kwargs 会报错） |
| f-string `f'{x}'`（**不含格式说明符**） | 支持，降级为模板字面量 |
| 列表 / 元组 / 字典 / 集合字面量 | 支持，分别降级为数组 / 数组 / 对象 / `new Set([...])` |
| 类（`__init__`/构造器、`self`→`this`、`extends` 继承、`super()`、`@staticmethod`、`__str__`→`toString`） | 支持 |
| `import` / `from ... import ...`（按相对路径加载兄弟 `.py`/`.js` 模块，详见「模块与 import」节） | 支持 |
| `raise Expr` | 支持，降级为 `throw Expr;`（裸 `raise` 不支持） |
| 装饰器 `@deco` / `@pkg.deco`（顶层函数、类） | 支持，降级为定义后 `name = deco(name)`（`@deco(...)` 带参数、类方法装饰器不支持） |
| 三元 `a if cond else b` | 支持，降级为 `(cond ? a : b)` |
| 比较 `in` / `not in` / `is` / `is not` | 支持 |
| `pass` / `break` / `continue` / `return` | 支持 |

### 函数

```python
def greet(name, prefix='Hello'):
    return f'{prefix}, {name}!'

def sum_all(*nums):
    return sum(nums)

print(greet('NekoJS'))        # Hello, NekoJS!
print(sum_all(1, 2, 3, 4))    # 10
```

### 控制流

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

### 赋值

```python
a = 1
a += 5              # 增强赋值
b = c = 0           # 多目标链式赋值（右边只求值一次）
x, y = 1, 2         # 元组解包 → JS 数组解构
n //= 2             # 地板除增强赋值
```

### lambda 与列表推导式

```python
square = lambda x: x * x

# 单层 for + 可选 if 过滤
evens = [n for n in range(10) if n % 2 == 0]
doubled = [square(n) for n in evens]
```

### f-string

```python
name = 'NekoJS'
version = 2
print(f'{name} v{version}, sqrt(2) ~ {2 ** 0.5}')
```

> 注意：f-string 的**格式说明符**（`{x:.2f}`、`{n:>4}` 等）**不支持**，`{expr}` 内只能是普通表达式。

### 容器字面量

```python
xs     = [1, 2, 3]        # 列表 → JS 数组
point  = (4, 5)           # 元组 → JS 数组
d      = {'a': 1, 'b': 2} # 字典 → JS 对象
unique = {1, 2, 2, 3}     # 集合 → new Set([1, 2, 2, 3])
```

### 类

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
print(cat)                # Animal(Tom)  —— 子类没重写 __str__，走父类的
print(Cat.default())      # Animal(cat)
```

要点：

- `self` 在实例方法里被改写成 JS 的 `this`；`@staticmethod` 方法不会被改写。
- `__init__` → `constructor`，`__str__` → `toString`，其它方法名（含 snake_case）原样保留。
- `extends` 翻译成 JS 的 `extends`；`super().__init__(args)` → `super(args)`，`super().method(args)` → `super.method(args)`。
- 类**方法**装饰器只支持 `@staticmethod`（其它方法装饰器会清晰报错）；顶层函数和类的装饰器见「装饰器」节。
- 实例化自己定义的类时 `Cat('Tom')` 会自动降级为 `new Cat('Tom')`。

### 装饰器

顶层函数和类的装饰器会翻译成「定义后包装」：

```python
def double(fn):
    def wrapped(x):
        return fn(x) * 2
    return wrapped

@double
def base(x):
    return x + 1

# 等价于：定义 base 后执行 base = double(base)
print(base(20))    # (20 + 1) * 2 = 42
```

- `@a` / `@b` / `def f` 会按 Python 语义从最近的一个开始包：`f = a(b(f))`。
- 装饰器本身可以是带点的名字：`@pkg.helper` → `f = pkg.helper(f)`。
- **类方法**装饰器仍只支持 `@staticmethod`（其它方法装饰器会报错）；**带参数**的装饰器 `@deco(...)` 也不支持（会清晰报错）——需要时写一个返回装饰器的普通函数，再 `@that_func` 引用。

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
```

`raise Expr` 降级为 JS `throw Expr;`。配合 `try/except` 可以抛出并捕获任意值。**裸 `raise`**（在 except 里重新抛出当前异常）暂不支持——请显式 `raise <一个值>`。`raise ... from cause` 的 `from` 子句会被解析但忽略。

### 模块与 import

`.py` 文件之间可以**互相 import**——`import` / `from ... import ...` 会被翻译成真正的 ESM `import`，由 NekoJS 的模块解析器按**相对路径**加载兄弟文件（与 `.js`/`.ts` 走完全相同的解析管线）：

```python
# server_scripts/math_utils.py —— 一个可被 import 的库
PI = 3.14

def circle_area(r):
    return PI * r * r
```

```python
# server_scripts/main.py —— import 上面的库
from math_utils import circle_area       # import { circle_area } from './math_utils';

print(circle_area(2))                    # 12.56
```

要点：

- 模块名按**相对路径**解析：`foo` → `./foo`，`a.b.c` → `./a/b/c`。解析器会自动尝试 `.py` / `.js` / `index.*` 等扩展名，所以也可以 import 同目录下的 `.js` 模块。
- `import foo` → `import * as foo from './foo'`（命名空间，用 `foo.x` 访问）；`import foo as f` 同理绑定到 `f`。
- `from foo import a, b` → `import { a, b } from './foo'`；`from foo import a as x` → `import { a as x } from './foo'`。
- 每个 `.py` 文件会自动 **export 它所有顶层定义**（`def`/`class`/顶层赋值的名字），所以兄弟文件能直接 `from <它> import <名字>`——无需任何额外声明。
- `from X import *` **不支持**（ESM 无法把命名空间展开进当前作用域）。
- import 必须在**模块顶层**（不能写在函数/类体里）。

> NekoJS 注入的全局绑定（`ServerEvents`、`Item`、`Utils` …）仍在全局作用域里，直接用名字即可（`ServerEvents.started(...)`），无需 import。

## 内置函数

| Python | 等价 JS lowering | 备注 |
|---|---|---|
| `range(stop)` / `range(start, stop[, step])` | `Array.from({length: ...}, ...)` | 单参数 `[0, stop)`；双参数 `[start, stop)`；三参数含步长 |
| `len(x)` | `(x).length` | 对数组/字符串；dict/set 需要 `len(list(d.keys()))` 等绕开 |
| `print(...)` | `console.log([...].join(sep))` | 支持 `sep=` 关键字参数；`end=` 被忽略（console.log 固定换行） |
| `abs(x)` | `Math.abs(x)` | |
| `min(...)` / `min(iterable)` | `Math.min(...)` | 单 iterable 参数会自动 spread |
| `max(...)` / `max(iterable)` | `Math.max(...)` | 同上 |
| `sum(iterable)` | `reduce((a,b)=>a+b, 0)` | |
| `str(x)` | `String(x)` | |
| `int(x[, base])` | `parseInt(x, base)` | 可选进制 |
| `float(x)` | `Number(x)` | |
| `bool(x)` | `Boolean(x)` | |
| `list()` / `list(iterable)` | `[]` / `[...iterable]` | |
| `dict()` / `dict(iterable)` | `({})` / `Object.fromEntries(iterable)` | |
| `set()` / `set(iterable)` | `new Set()` / `new Set(iterable)` | |
| `tuple(iterable)` | `[...iterable]` | 注意：返回的是可变数组（JS 无不可变元组） |
| `sorted(iterable)` | `[...iterable].sort(比较器)` | 支持 `reverse=True` 关键字；不支持 `key=` |
| `any(iterable)` / `all(iterable)` | `.some(x=>x)` / `.every(x=>x)` | |
| `enumerate(iterable)` | `(iterable).map((v, i) => [i, v])` | 返回 `[index, value]` 对 |
| `ord(c)` / `chr(n)` | `codePointAt(0)` / `String.fromCodePoint(n)` | |
| `pow(x, y)` | `Math.pow(x, y)` | 不支持三参数模幂 |
| `callable(x)` | `typeof x === "function"` | |

> 常用 **str/list/dict/set 方法**已映射到 JS 等价写法（见下节「方法映射」）；未映射的方法原样透传 `obj.method(args)`（若 JS 侧恰好有同名方法仍可用）。

## 方法映射

常用 str/list/dict/set 方法会自动转译到 JS 等价写法：

| 类型 | 已映射方法 |
|---|---|
| **str** | `upper lower strip lstrip rstrip find rfind index ljust rjust zfill replace startswith endswith count split`(无参按空白) `join` |
| **list** | `append`(→push) `copy insert remove pop reverse` |
| **dict** | `keys values items update get`(带默认值) |
| **set** | `discard`(→delete)；`add` 原样透传 |

未在上表的方法原样透传（`obj.method(args)`），若 JS 侧有同名方法仍可用。

## 切片

- `xs[lo:up]`、`xs[:up]`、`xs[lo:]`、`xs[:]` → `xs.slice(lo, up)`
- `xs[::-1]` → 反转（字符串走 `split/reverse/join`，其它走 `...reverse`）
- 负数下标 `xs[-1]` → `xs.slice(-1)[0]`（Python 末元素语义）
- **仅 `[::-1]` 步长**被支持；其它步长（如 `[::2]`）会报错

## try / except

```python
try:
    risky()
except ValueError as e:
    handle_v(e)
except TypeError:
    handle_t()
finally:
    cleanup()
```

降级为 JS `try/catch/finally`。支持**多个 `except` 子句**，按顺序做**类型匹配**（instanceof），**未匹配的异常会被重新抛出**（与 Python 一致）：

- `except MyErr as e:` → `if (e instanceof MyErr) { var e = e; ... }`
- `except (A, B):` → `e instanceof A || e instanceof B`（括号多类型）
- 裸 `except:` 捕获一切，必须是**最后一个** except 子句
- **Python 内置异常名**（`Exception`、`ValueError`、`TypeError`、`KeyError` 等）在 JS 运行时不存在，会被映射为 JS 的 `Error`（最接近的基类）。因此 `class MyErr(Exception):` 会转成 `class MyErr extends Error`，`raise MyErr()` + `except MyErr` 能**端到端匹配**。
- `as e` 绑定的 `e` 是底层 JS 错误对象。

> 注意：`raise 42`（抛非 Error 值）配合 `except Exception` **不会**匹配（`42 instanceof Error` 为假），异常会被重新抛出——这与 Python 语义一致。要捕获任意值用裸 `except:`。

## Python ↔ JS 差异要点

转译器尽力贴近 Python 语义，但底层是 JS，以下几处务必留意：

| 差异 | 说明 |
|---|---|
| **`self` → `this`** | 仅在**实例方法体**内改写；普通函数里的 `self` 不替换。`@staticmethod` 方法不改写。 |
| **dict 是 JS 对象** | 键只能是字符串（数字键会被强制成字符串）。不能用任意可哈希对象（如元组）当键。 |
| **`//` 地板除** | `a // b` → `Math.floor(a / b)`，与 Python 一样向负无穷取整（`-7 // 2 == -4`）。 |
| **`**` 幂** | `a ** b` 直接透传给 JS `**`，语义一致。 |
| **`in` / `not in`** | `x in coll` → `coll.includes(x)`。**只对数组/字符串工作**；对 dict（JS 对象）不工作（对象没有 `.includes`）。判断字典键请写 `'k' in obj`（JS 原生 `in`）或 `obj.k !== undefined`。 |
| **`is` / `is not`** | 透传成 JS `===` / `!==`（严格相等）。`None` 映射为 `null`，所以 `x is None` 等价于 `x === null`。 |
| **`==` / `!=`** | 透传成 JS **宽松相等**（会做类型转换，`1 == '1'` 为真），与 Python 的值相等语义不同。要严格相等请用 `is` / `is not`。 |
| **`and` / `or`** | 透传成 `&&` / `||`，同样短路返回操作数值，语义一致。 |
| **整数精度** | Python 的 `int` 是任意精度，JS 只有双精度浮点 Number。`> 2^53` 的整数会丢精度，`int(x)` 也受此限制。常规整数运算无差异。 |
| **变量作用域** | 所有赋值都 emit 成 `var`（函数作用域、可重复声明、被提升），不是 `let`/`const`。没有块级作用域和 TDZ——循环/分支里赋的值会「漏」到外层函数作用域。 |
| **布尔真值（重要）** | Python 里 `[]`/`{}`/`''`/`0`/`None` 都是假；但 JS 里**空数组、空对象是真**。转译器不做真值转换，所以 `if xs:` 当 `xs=[]` 时，Python 不执行分支，JS `if ([])` 会执行。**对空容器判断请显式写 `if len(xs) == 0:`。** |
| **`None` / `True` / `False`** | 分别映射为 JS `null` / `true` / `false`（注意不是 `undefined`）。 |

## 限制（v1 暂不支持）

下列语法/特性在当前版本**不支持**，遇到会清晰报错（错误信息会带文件名与位置）：

- 裸 `raise`（在 except 里重新抛出当前异常）
- 切片步长（除 `[::-1]` 外，如 `[::2]`）
- 生成器 / `yield` / `yield from`
- `with` 语句（上下文管理器）
- 带参数的装饰器 `@deco(...)`、类方法装饰器（除 `@staticmethod` 外）
- `**kwargs` 关键字参数（`*args` 支持；`print(sep=)`/`sorted(reverse=)` 例外）
- f-string 格式说明符（`{x:.2f}`、`{n:>4}` 等）
- 嵌套/多层推导式（`[... for x in ... for y in ...]`）
- `from X import *`
- 矩阵乘 `@` / `@=`
- 上表以外的内置函数（如 `isinstance`、`type`、`id`、`round` 的银行家舍入、`zip` 的多迭代器）

> 替代思路：需要这些能力时，直接写 JS 等价写法，或在 `.py` 里调用 NekoJS 暴露的 JS 绑定。Python 子集与 JS/TS 脚本在同一个运行时里，可以混用。

## 工作原理

### 插件自发现

Python 支持由 `PythonTranspilerPlugin`（在 `common/.../core/compiler/python/`）提供，通过 `@RegisterNekoJSPlugin` 自注册。它落在 `com.tkisor.nekojs.*` 包下，所以**两个平台都会自动发现**，无需任何手工接线：

- **NeoForge**：ASM 注解扫描从内嵌的 `common` 类里捡到它。
- **Cleanroom 1.12.2**：包扫描器（根 `com.tkisor.nekojs`）同样能找到它。

插件把 `.py` 扩展名注册到 `PythonToJsCompiler`，于是把 `.py` 文件丢进脚本目录就会和 `.js`/`.ts` 一样被加载。

### 转译管线

```
.py 源码
   │  PythonLexer（含 INDENT/DEDENT 记号）
   ▼
Python AST（递归下降 PythonParser）
   │  PythonEmitter（lowering 为 JS 源码）
   ▼
JavaScript  +  v3 source map
   │  （交给 GraalJS 执行，与 JS/TS 同一个运行时）
   ▼
执行
```

### 源码映射（source map）

`PythonToJsCompiler#compileDetailed` 会同时返回 JS 和一份**逐语句的 v3 source map**（由 `PythonSourceMap` 构建）。每条语句首行带一个 4 字段 segment，把生成的 JS 行映射回原始 `.py` 行。运行时栈追踪因此能定位到 Python 源码行号，而不是降级后的 JS 行号。

转译或解析失败会抛 `IllegalArgumentException`，错误信息包含文件名与行列位置，例如：

```
python transpile failed in server_scripts/foo.py: python parse error at line 12, col 5: ...
```

### 与 JS/TS 共存

Python 脚本和 JS/TS 脚本共享：

- 同一个 GraalJS 运行时与沙盒配置；
- 同一套全局绑定（`Item`、`ServerEvents`、`global`、`Utils` ...）；
- 同一套 reload / 错误处理 / `logs/nekojs/<type>.log` 机制。

可以在同一个整合包里混用 `.js`、`.ts`、`.py`，互不干扰。

## 下一步

- [脚本基础](脚本基础) —— 脚本类型、生命周期、reload 行为。
- [全局绑定](全局绑定) —— Python 里直接用名字即可访问的顶层 API（无需 import）。
- [TypeScript 与 JSX](TypeScript-与-JSX) —— 另一种本体语言前端。
- [常见问题](常见问题) —— 报错排查。
