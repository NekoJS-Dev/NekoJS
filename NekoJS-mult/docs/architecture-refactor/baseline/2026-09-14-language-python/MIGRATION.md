# Python（`.py`）迁移与使用材料（工单 13）

> 本文只描述 **已通过 gate 的 Python 能力**与其模块/诊断契约。Python 是既有公开语言，
> 本票**不删除**任何能力，也**不引入** Python 运行时或非纯 Java 依赖。

## 1. 最小可运行示例

| 文件 | 作用 |
|---|---|
| [`minimal-example.py`](./minimal-example.py) | Python 侧：缩进、`def`/`class`、注释、f-string、列表推导、`raise` |
| [`minimal-example-entry.mjs`](./minimal-example-entry.mjs) | ESM 入口：真实 `import` 上面的 `.py` 并写出结果 |

示例由 `PythonMinimalExampleTest` **真实执行**（`NekoScriptModuleLoaderHost.loadEntry`），
并与文档逐项对齐：实现漂移、身份漂移或 source map 漂移都会让该测试变红。

## 2. 模块契约（脚本作者可依赖的事实）

| 维度 | 值 | 说明 |
|---|---|---|
| language id | `python` | 由 `ScriptCompilerRegistry` 解析 `.py` 得到，与 `identityOf`、cache key 同源 |
| 请求模式 | `AUTO` | `.py` 扩展名不强制模块模式 |
| 解析模式 | `ESM` | 转译产物带 `export { ... }` 块（模块定义了顶层名字时） |
| source map | 非空、语句粒度 | `raise`/`throw` 等语句的原始行可经 `SourceMapRegistry` 查回 `.py` |
| cache key | languageId + mode + 路径 + 内容 SHA-256 | 内容 / 语言 / 模式 / 路径任一变化都失效 |

## 3. import 写法

Python 侧写 Python 风格 import，转译器按固定约定生成 ESM import：

| Python 写法 | 生成 | 解析目标 |
|---|---|---|
| `import utils` | `import * as utils from './utils'` | `./utils.py`（解析器按 `supportedExtensions` 探测） |
| `from utils import helper` | `import { helper } from './utils'` | 同上 |
| `import a.b.c` | `import * as c from './a/b/c'` | `./a/b/c.py` |
| `from nekojs import *` | **剥离**（不产生任何 JS，也不影响 source map） | 仅供 pyright 的类型桩入口 |

因此 `.py` 与 `.js`/`.cjs`/`.mjs`/`.ts` **共用同一份**模块身份、依赖图、cache 与 source map
（不存在的第二套 resolver 是本票明确不引入的）。

## 4. 诊断契约

- **源语法/转换错误**：在 **Preparation 阶段**抛 `IllegalArgumentException`，消息形如
  `python transpile failed in <绝对路径>.py: python parse error at line L, col C: ...`，
  即携带原始 `.py` 文件与**原始行列**，不会延迟成执行期无来源的 Graal 异常。
- **执行期错误**：经 source map 回到原始 `.py` 的**真实语句行**。转译产物会在正文之前注入
  运行时 helper 与异常 prelude（约 25 行），这些注入行没有源对应，错误解析会跳过它们
  （见 `ScriptError.firstOriginFrame`），不会把行列停在产物行号或错位到无关的原始行。
- 错误信息保持普通形式（文件 + 行列 + 事实），**不带修复指引**（`CONTEXT.md` 约定）。

## 5. 作者需要注意的 v1 子集边界

| 形态 | 行为 |
|---|---|
| 顶层 `return`/`break`/`continue`/`yield` | v1 在准备阶段不拦截；加载期以硬 `SyntaxError` **可见地失败**，位置仍指向原 `.py` 行，后续语句保留在产物里（characterization：`PythonModuleErrorPhaseCorpusTest`） |
| 类体直接 `raise` | 转译产物不是合法 JS 类体，加载期 `SyntaxError`（可见失败） |
| 多继承 / 装饰器带参数 / `from X import *` / 非 ASCII 标识符 | 准备阶段带位置报错 |
| 顶层 `global`/`nonlocal`/`await` | 准备阶段带位置报错 |

## 6. 迁移动作

本票**没有**任何破坏性变更：既有 `.py` 脚本无需改写。唯一的行为修正是
**执行期错误的行列更准确**（此前会落到转译产物的注入行或错位行）。若你此前为了绕开
错误定位而在 `.py` 里插过占位语句，现在可以删除。
