# Python 特性清单（面向 NekoJS JS 转译器）

> 本文档对照「现代 Python（3.10 – 3.14）的语言特性」与「NekoJS 内置 Python→JS 转译器」的当前支持情况，用于：①了解最新 Python 都有什么；②决定转译器该补哪些、跳过哪些。
>
> **NekoJS 转译器**把一个 Python **子集**解析成 AST 后 lowering 成 JavaScript，交给 GraalJS 执行。所以判断每个特性的关键问题是：**它能否干净地映射到 JS？**（GraalJS 原生支持 ES2020+，含类、生成器、async/await、模板字符串、可选链等。）
>
> 状态标记：✅ 支持　🟡 部分支持　❌ 不支持（有意跳过）　⏳ 可作为后续扩展

---

## 一、最新 Python 版本特性总览（3.10 – 3.14）

> 版本号与 PEP 均来自官方 whatsnew / PEP 索引（见文末链接）。

### Python 3.10

| 特性 | PEP | 语法示例 | 对 JS 转译 | NekoJS 状态 |
|---|---|---|---|---|
| 结构化模式匹配 `match`/`case` | PEP 634 | `match x:\n  case 1: ...` | 中（可降级为 if/else 链） | ❌ |
| 括号化多上下文管理器 | — | `with (a as x, b as y):` | 易（已支持多项 `with`） | ✅（等价 `with a as x, b as y:`） |
| `X \| Y` 联合类型注解 | PEP 604 | `def f(x: int \| str)` | 易（注解直接丢弃） | ✅（注解已丢弃） |
| 更精确的错误定位（带 `^` 指示） | — | — | 与转译无关 | — |

### Python 3.11

| 特性 | PEP | 语法示例 | 对 JS 转译 | NekoJS 状态 |
|---|---|---|---|---|
| 异常组 `ExceptionGroup` + `except*` | PEP 654 | `raise ExceptionGroup(...)` / `except* ValueError:` | 难（JS 无原生对应，需模拟） | ❌ |
| `asyncio.TaskGroup`（结构化并发） | — | `async with asyncio.TaskGroup() as tg:` | 属于 stdlib/async | ❌ |
| `add_note`（异常附加说明） | — | `e.add_note('...')` | 易（对象属性） | ❌（未映射） |
| `typing.Self` / `typing.override` 装饰器 | PEP 673 | — | 注解，丢弃即可 | ❌（注解未识别这些名字） |
| tomllib（标准库 TOML 解析） | PEP 680 | — | stdlib，无关 | — |
| 细粒度 traceback 定位、加速（Faster CPython） | — | — | 与转译无关 | — |

### Python 3.12

| 特性 | PEP | 语法示例 | 对 JS 转译 | NekoJS 状态 |
|---|---|---|---|---|
| 类型参数新语法（`type` 语句 + `[T]` 泛型） | PEP 695 | `type ListOrSet[T] = ...` / `def f[T]():` | 注解层面，丢弃即可 | ❌（未解析 `[T]`/`type` 语句） |
| f-string 形式化（PEP 701：任意表达式、嵌套引号、反斜杠、注释、嵌套 f-string） | PEP 701 | `f"{f'{x}'}"`、`f"{'\n'.join(xs)}"` | 中（需更强 tokenizer） | 🟡（支持插值与格式说明符；嵌套同引号/反斜杠未支持） |
| 每解释器独立 GIL | PEP 684 | — | 运行时/解释器层，无关 | — |
| `@override` 装饰器（typing） | PEP 698 | `@override\ndef m():` | 注解，丢弃即可 | ❌（类方法装饰器仅 `@staticmethod`） |
| 缓冲区协议对 Python 暴露 | PEP 688 | — | C 层，无关 | — |
| 推导式「内联化」（语义不变的性能优化） | PEP 709 | — | 语义不变，无关 | — |

### Python 3.13

| 特性 | PEP | 语法示例 | 对 JS 转译 | NekoJS 状态 |
|---|---|---|---|---|
| 实验性「自由线程」构建（可选 GIL） | PEP 703 | — | 运行时构建，无关 | — |
| 类型参数默认值 `TypeVar` 默认 | PEP 696 | `class Guild[T = str]:` | 注解层面 | ❌ |
| `@deprecated` 装饰器 | PEP 702 | `@deprecated` | 注解层面 | ❌ |
| 新交互式 REPL、彩色 traceback | — | — | 与转译无关 | — |
| 局部变量一致性（`locals()` 语义） | PEP 667 | — | 与转译无关 | — |

### Python 3.14

| 特性 | PEP | 语法示例 | 对 JS 转译 | NekoJS 状态 |
|---|---|---|---|---|
| 模板字符串 **t-string** | PEP 750 | `t"hello {name}"` → `Template` 对象 | 难（产生对象而非字符串，需 Template 类型） | ❌（太新，暂跳过） |
| 注解**延迟求值** | PEP 649 / 749 | 注解存为延迟表达式 | 注解层面（我们本就丢弃） | —（注解已丢弃） |
| 标准库子解释器支持 | PEP 734 | — | 运行时，无关 | — |
| 自由线程改进、尾调用解释器（CEval） | — | — | 运行时，无关 | — |

> **小结**：3.10–3.14 里**语法级**的新东西，对「JS 转译器」真正有意义的只有：`match/case`（3.10）、f-string 放宽（3.12 PEP 701）、t-string（3.14 PEP 750）。其余要么是注解/类型层面（转译器本就丢弃注解），要么是运行时/解释器/标准库层面（与「把 Python 翻成 JS」无关）。

---

## 二、核心语法特性清单（通用，非版本特定）

下表是「一个实用的 Python 脚本子集」通常会想要的能力，标注**当前 NekoJS 支持情况**与**预期 JS 映射**。

### 语句级

| 特性 | 语法示例 | 预期 JS 映射 | NekoJS 状态 |
|---|---|---|---|
| 函数 `def`（默认参数 / `*args` / `**kwargs`） | `def f(a, b=1, *args, **kw):` | `function`（`**kwargs` 触发 prologue） | ✅ |
| 类与继承 | `class C(B):` | JS `class` / `extends` | ✅ |
| `super()` | `super().__init__(x)` | `super(x)` / `super.m()` | ✅ |
| `@staticmethod` / `@classmethod` | `@staticmethod` / `@classmethod` | `static` 方法；classmethod 绑 `cls=this` | ✅ |
| 属性 `@property` | `@property\ndef x(self):` | JS getter（只读） | ✅ |
| `if` / `elif` / `else` | — | `if / else if / else` | ✅ |
| `for x in iter` / `while` | — | `for...of` / `while` | ✅ |
| `with` 上下文管理器 | `with cm as x:` | 内联 acquire + try/finally | ✅ |
| `try` / `except` / `else` / `finally` | — | `try/catch/finally` + instanceof | ✅ |
| 赋值（链式 / 元组解包 / 增强） | `a=b=v`、`a,b=...`、`+=` | `var` / 解构 | ✅ |
| 海象 `:=` | `if (n:=f()):` | `(n = f())` | ✅ |
| `assert` | `assert c, msg` | `if(!c) throw new Error(msg)` | ✅ |
| `del` | `del d[k]` | `delete d[k]` | ✅ |
| `raise` / 裸 `raise` / `raise ... from` | — | `throw` / 重抛 | ✅（`from` 解析后忽略） |
| `global` / `nonlocal` | `global x` | — | ❌ |
| `return` / `break` / `continue` / `pass` | — | 同名 | ✅ |
| `import` / `from ... import` | — | ESM `import` | ✅ |
| 类型注解（参数 / 返回 / 变量） | `x: int = 5` | 解析后丢弃 | ✅（丢弃） |
| `match` / `case` 模式匹配 | `match x:` | 降级 if/else 链 | ✅（字面量/通配/捕获/`|`/序列/映射/类模式+guard） |
| `async def` / `await` / `async for` / `async with` | — | JS async/await | ❌（GraalJS 支持但价值低） |

### 表达式级

| 特性 | 语法示例 | 预期 JS 映射 | NekoJS 状态 |
|---|---|---|---|
| 字面量 int/float/str/bool/None | — | number/string/boolean/null | ✅ |
| f-string（含 `:.2f` 等格式说明符、`!r`） | `f'{x:.2f}'` | 模板字面量 + `__nekoFmt` | ✅ |
| t-string（3.14） | `t"{x}"` | Template 对象 | ❌ |
| 属性 / 索引 / 调用 | `a.b` / `a[i]` / `f()` | 同名 | ✅ |
| 关键字调用 | `f(a=1, b=2)` | 尾随带标记对象 + prologue | ✅（目标需 `**kwargs` 或 print/sorted） |
| 算术 / 比较 / 布尔 / 位运算 | `+ - * / // % **`、`and/or/not`、`& \| ^ ~ << >>` | 对应 JS（`//`→`Math.floor`） | ✅ |
| 三元 `a if c else b` | — | `(c ? a : b)` | ✅ |
| `in` / `not in` / `is` / `is not` | — | `.includes` / `===` | ✅（`in` 仅数组/字符串） |
| 链式比较 | `a < b < c` | `(a<b) && (b<c)` | ✅ |
| 切片（任意步长、负下标） | `xs[::2]`、`xs[::-1]`、`xs[-1]` | 辅助函数 / `slice` | ✅ |
| 列表 / 元组 / 字典 / 集合字面量 | `[1,2]`、`(1,2)`、`{k:v}`、`{1,2}` | 数组 / 数组 / 对象 / `Set` | ✅ |
| `lambda` | `lambda x: x*2` | 箭头函数 | ✅（无 `**kwargs`） |
| 推导式（列表 / 字典 / 集合，多层 `for`、多个 `if`） | `[x for a in A for b in B if c]` | `.filter().map()` / `flatMap` 嵌套 | ✅ |
| 生成器表达式 `(x for x in xs)` | — | 立即调用的 `function*` | ✅ |
| `yield` / `yield from` | — | `function*` / `yield` / `yield*` | ✅ |
| 字典合并 `\|`（3.9） | `d1 \| d2` | `位或`（未对 dict 特化） | ❌（按位或处理） |
| 字面量字符串拼接 `"a" "b"` | — | 隐式拼接 | ✅ |

### 内建函数

NekoJS 已映射的内建（详见 [Python-脚本.md](Python-脚本.md)「内置函数」表）：`range / len / print / abs / min / max / sum / str / int / float / bool / list / dict / set / tuple / sorted / any / all / enumerate / reversed / map / filter / zip / round / divmod / ord / chr / pow / hex / oct / bin / repr / format / isinstance / type / callable`。

仍未映射：`id / vars / globals / locals / eval / exec / hasattr / setattr / getattr / input / open / iter / next / frozenset / complex / ...`（多为反射、IO 或与 JS 运行时不匹配者）。

---

## 三、NekoJS 转译器实现优先级建议

| 特性 | 优先级 | 理由 | 预期映射 |
|---|---|---|---|
| `match`/`case` | 🟡 中 | 实用、可降级为 if/else 链；但模式解构（序列/映射/类模式）工作量大 | 降级为 `if/else if` 链（先支持字面量与类模式） |
| dict 合并 `\|` | 🟢 低 | 少见；`{...a, ...b}` 即可 | 对 dict 操作数特化为 spread 合并 |
| f-string PEP 701 放宽 | 🟡 中 | 嵌套同引号 / 反斜杠 / 注释 | 需更强的 f-string tokenizer（当前 FStringParser 够用绝大多数场景） |
| `global`/`nonlocal` | 🔴 低/跳过 | JS 模块作用域语义差异大，难忠实映射 | 建议跳过，鼓励用返回值/容器 |
| `@property`/`@classmethod` | 🟡 中 | OOP 常用 | `Object.defineProperty` getter / 绑定类 |
| `async`/`await` | 🔴 低 | GraalJS 支持但脚本侧事件循环接入复杂 | 暂跳过 |
| 异常组 `except*` | 🔴 低 | JS 无原生对应，模拟成本高 | 跳过 |
| t-string（3.14） | 🔴 低 | 太新、需 `Template` 类型 | 跳过 |
| 类型注解求值 | 🔴 低 | 与转译目标无关（静态类型） | 永久丢弃 |
| 更多内建（`getattr`/`hasattr`/`iter`/`next`） | 🟢 低 | 按需补 | 属性访问 / `Symbol.iterator` |

> 已**完成**的高价值扩展（本轮）：生成器/yield、`with`、多层推导式、任意切片步长、f-string 格式说明符、`**kwargs`+关键字调用、`assert`/`del`/海象、`try/else`、裸 `raise`、类型注解丢弃、扩展内建函数。

---

## 四、GraalJS / ES 能力对照

| Python 构造 | ES 对应 | 说明 |
|---|---|---|
| `class` + 继承 | `class` + `extends` | 一一对应 |
| 生成器 / `yield` / `yield from` | `function*` / `yield` / `yield*` | GraalJS 原生 |
| `async`/`await` | `async`/`await` | GraalJS 原生（脚本侧需事件循环） |
| `with`（上下文管理器） | 无原生 → try/finally + `__enter__`/`__exit__` | NekoJS 内联降低 |
| `**kwargs` | 无原生 → 尾随带标记对象 + prologue | NekoJS 约定 |
| f-string | 模板字面量 `` ` ` `` | 格式说明符需手写 `__nekoFmt` |
| 推导式 | `.map`/`.filter`/`.flatMap` | 一一对应 |
| 切片 | `Array.prototype.slice` + 步长辅助 | 步长需自实现 |
| 字典 | 普通对象 | 键仅字符串、整数键重排序 |
| 集合 | `Set` | 一一对应 |
| 元组 | 数组 | 无不可变性 |

---

## 参考链接

- [What's New in Python 3.10](https://docs.python.org/3/whatsnew/3.10.html)　·　[3.11](https://docs.python.org/3/whatsnew/3.11.html)　·　[3.12](https://docs.python.org/3/whatsnew/3.12.html)　·　[3.13](https://docs.python.org/3.13/whatsnew/3.13.html)　·　[3.14](https://docs.python.org/3/whatsnew/3.14.html)
- [PEP 634 / 636 — match/case（3.10）](https://peps.python.org/pep-0636/)
- [PEP 654 — Exception Groups and `except*`（3.11）](https://peps.python.org/pep-0654/)
- [PEP 695 — 类型参数语法（3.12）](https://peps.python.org/pep-0695/)　·　[PEP 701 — f-string 形式化（3.12）](https://peps.python.org/pep-0701/)
- [PEP 703 — 自由线程（3.13）](https://peps.python.org/pep-0703/)　·　[PEP 696 — 类型参数默认值（3.13）](https://peps.python.org/pep-0696/)
- [PEP 750 — 模板字符串 t-string（3.14）](https://peps.python.org/pep-0750/)　·　[PEP 649 / 749 — 注解延迟求值（3.14）](https://peps.python.org/pep-0649/)
