<!-- wiki-page: python-feature-support; locale: cn -->

> **中文** · [English](python-feature-support_us)

<a id="wiki-section-1"></a>
# Python 特性清单

本页列出近几个 Python 版本（3.8 到 3.14）新增的语言特性，以及它们在 NekoJS 里能不能用。

NekoJS 会把 Python 代码转成 JavaScript 再运行，只支持 Python 的一部分。日常写法（函数、类、推导式、f-string 等）的完整说明见 [Python 脚本](python-scripts_cn)。本页只回答一个问题：“我在新版 Python 里学到的这个写法，在 NekoJS 里能用吗？”

表中的状态：

- **能用**：可以照常写。
- **部分**：能用，但有限制，见说明。
- **不能用**：会报错，或者结果不对。
- **无关**：解释器、性能或标准库层面的改动，写脚本时用不到。

<a id="wiki-section-2"></a>
## 先看结论

- 能用：海象运算符 `:=`、`match` / `case`（部分模式）、类型注解（包括 `int | str`、`list[int]`、`Self` 等写法，都会被忽略）。
- 不能用：`async` / `await`、仅限位置参数 `/` 和仅限关键字参数 `*`、`except*`、`type` 语句和 `[T]` 泛型语法、t-string、字典合并 `|`。
- 没有 Python 标准库，所以 `typing`、`asyncio`、`tomllib` 等模块都不能导入。类型注解里可以写 `Self`、`List`，但不要写 `from typing import ...`。

<a id="wiki-section-3"></a>
## Python 3.8

| 特性 | 示例 | 状态 | 说明 |
|---|---|---|---|
| 海象运算符 | `if (n := len(xs)) > 3:` | 部分 | 变量要先用普通赋值定义过，见 [Python 脚本](python-scripts_cn) 的“已知问题” |
| 仅限位置参数 | `def f(a, /, b):` | 不能用 | 报解析错误 |
| f-string 的 `=` 调试写法 | `f'{x=}'` | 不能用 | 不报错，但只输出值，没有 `x=` 前缀 |

<a id="wiki-section-4"></a>
## Python 3.9

| 特性 | 示例 | 状态 | 说明 |
|---|---|---|---|
| 字典合并运算符 | `a \| b`、`a \|= b` | 不能用 | 不报错，但结果不对。用 `{**a, **b}` |
| 内置类型做泛型注解 | `x: list[int] = []` | 能用 | 注解被忽略 |
| `str.removeprefix` / `removesuffix` | `s.removeprefix('a')` | 不能用 | 运行时报错。可以用切片代替 |

<a id="wiki-section-5"></a>
## Python 3.10

| 特性 | 示例 | 状态 | 说明 |
|---|---|---|---|
| `match` / `case` | `match x:` | 部分 | 支持字面量、`_`、捕获、`\|`、列表、字典、关键字形式的类模式和 `if` 条件；不支持位置形式的类模式 `Point(0, 0)` |
| 联合类型注解 | `def f(x: int \| str):` | 能用 | 注解被忽略 |
| 带括号的多个上下文管理器 | `with (a as x, b as y):` | 不能用 | 去掉括号：`with a as x, b as y:` |

<a id="wiki-section-6"></a>
## Python 3.11

| 特性 | 示例 | 状态 | 说明 |
|---|---|---|---|
| 异常组 | `except* ValueError:` | 不能用 | 报解析错误 |
| 给异常加备注 | `e.add_note('...')` | 不能用 | |
| `Self` 注解 | `def copy(self) -> Self:` | 能用 | 注解被忽略 |
| `asyncio.TaskGroup`、`tomllib` | | 不能用 | 没有标准库 |

<a id="wiki-section-7"></a>
## Python 3.12

| 特性 | 示例 | 状态 | 说明 |
|---|---|---|---|
| `type` 语句 | `type Pair = tuple[int, int]` | 不能用 | 报解析错误 |
| 泛型参数语法 | `def first[T](xs):` | 不能用 | 报解析错误。去掉 `[T]` 即可 |
| 更宽松的 f-string | `f"{f"{x}"}"` | 部分 | 表达式里用另一种引号可以（`f"{'a'}"`）；嵌套同种引号、在 `{}` 里写注释不行 |
| `@override` | `@override` | 不能用 | 方法上只能用 `@staticmethod`、`@classmethod`、`@property` |

<a id="wiki-section-8"></a>
## Python 3.13

| 特性 | 示例 | 状态 | 说明 |
|---|---|---|---|
| 类型参数默认值 | `class Box[T = int]:` | 不能用 | 泛型参数语法本身就不支持 |
| `@deprecated` | `@deprecated('use g')` | 不能用 | 装饰器不能带参数 |
| 新 REPL、自由线程构建 | | 无关 | |

<a id="wiki-section-9"></a>
## Python 3.14

| 特性 | 示例 | 状态 | 说明 |
|---|---|---|---|
| 模板字符串 t-string | `t'hello {name}'` | 不能用 | 报解析错误 |
| 注解延迟求值 | | 无关 | NekoJS 本来就忽略注解 |

<a id="wiki-section-10"></a>
## 更早就有、但不能用的写法

这些写法不是新特性，但也常被问到：

| 写法 | 状态 | 替代办法 |
|---|---|---|
| `async` / `await`、`async for`、`async with` | 不能用 | 改用事件回调 |
| `global` / `nonlocal` | 不能用 | 把状态放进字典或列表 |
| 星号解包赋值 `a, *rest = xs` | 不能用 | `a = xs[0]`，`rest = xs[1:]` |
| `str.format` | 不能用 | f-string |
| 多个父类 | 不能用 | 单继承或组合 |

完整的不支持列表和替代办法见 [Python 脚本](python-scripts_cn)。

<a id="wiki-section-11"></a>
## 参考链接

- [What's New in Python](https://docs.python.org/3/whatsnew/index.html)：各版本的官方更新说明。
- [PEP 572 — 海象运算符（3.8）](https://peps.python.org/pep-0572/)
- [PEP 636 — match/case 教程（3.10）](https://peps.python.org/pep-0636/)
- [PEP 654 — 异常组与 `except*`（3.11）](https://peps.python.org/pep-0654/)
- [PEP 695 — 类型参数语法（3.12）](https://peps.python.org/pep-0695/)
- [PEP 701 — f-string 语法形式化（3.12）](https://peps.python.org/pep-0701/)
- [PEP 750 — 模板字符串（3.14）](https://peps.python.org/pep-0750/)

<!-- wiki-nav -->

---

[上一篇: Python 脚本](python-scripts_cn) · [目录](Home) · [下一篇: 事件参考](event-reference_cn)
