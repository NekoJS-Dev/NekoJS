# 票据 13 迁移材料：Python 转译、模块行为与诊断路径（2026-09-19）

> 范围：Python（`.py` 子集 -> JS，纯 Java 自研）经统一 Preparation / Resolution-Cache /
> Execution 路径的 language 身份、模块模式、source map、prepended-line 语义、准备期与
> 执行期诊断位置。**脚本侧无 breaking，无须迁移**；Java 侧没有新增公开 API、没有新增
> parser SPI、没有第二套模块管线。作者可见变化只有一处：**Python 错误的行列更精确**。

## 1. 脚本作者：无须迁移

既有 Python 语义全部保留，且现在经与其他语言完全相同的模块管线：

- 缩进结构（INDENT/DEDENT、tab 展开、括号内隐式续行、反斜杠续行）、`#` 注释、
  blank/comment-only 行不产生 token——行为不变。
- `def`/`class`/`lambda`/装饰器（`@staticmethod`/`@classmethod`/`@property`）、
  `if/elif/else`、`for/while`+ `else`、`try/except/else/finally`、`with`、
  `match`、推导式、切片、`**kwargs`、f-string、生成器、异常 prelude
  （`raise ValueError(...)`）——行为不变。
- `import m` / `from m import a, b` 走**同一** `NekoModuleResolver` + 同一模块身份与
  失效规则（`.py` 只是 `ScriptCompilerRegistry` 注册的另一个语言；没有第二条 resolver）。
- `from nekojs import *`（IDE/pyright 用的类型桩入口）仍被转译器剥离：无 JS 输出、
  不影响 source map（`PythonEmitterMagicImportTest` 继续锁定）。
- 未引入 Python 运行时、非纯 Java 依赖、公共 parser SPI 或第二个 Gradle 子项目。

**唯一作者可见变化：错误位置。**

| 场景 | 变化前 | 变化后 |
|---|---|---|
| Python 词法错误（如非法字符） | `NekoModuleError.PREPARE`，`sourceLine()/sourceColumn()` 为 `-1`，位置只在消息文本里 | 同一阶段/owner，`sourceLine()/sourceColumn()` 是 authored 行列 |
| Python 语法错误 | 同上（`-1`） | authored 行列 |
| Python 发射期错误（如 `del name`） | 同上（`-1`） | authored **语句**行（列取语句起点，emitter 无 token 级列） |
| 生成 JS 的运行期异常 | 报**生成代码**的行（异常 prelude 的 `class ValueError extends Error` 声明行），例如 authored 第 4 行的 `raise` 报成第 5 行 | 报 authored `.py` 行列（异常 prelude 行无映射，不再被当成作者行） |
| 跨 import 的子模块失败 | 同样的 prelude 偏移，且曾丢失子模块 authored 行 | 保留子模块身份 + authored 行 |

错误**文本**未变（`python lex error at line L, col C: ...` / `python parse error at ...` /
`python transpile failed in <file>: ...`），`NekoModuleError` 仍继承 `IOException`，
既有 `try/catch` 与消息匹配不受影响。新增的是**可观察字段**。

## 2. 最小可运行示例（交付物正本：`common/src/test/resources/nekojs/language-py-examples/`）

示例只使用已过 gate 的 Python 能力；`PythonExamplesSmokeTest` 逐项执行并断言下述输出。

**入口（`py/hello.py`）**

```python
# 最小 Python 示例（NekoJS Python 子集 -> JS，纯 Java 转译，无外部运行时）。
# 运行：把本目录两个文件放入 server_scripts/ 任一子目录，入口被加载后 value 即结果。
# 说明：顶层有 def/赋值时模块是 ESM（可被其它 .py/.mjs import）；去掉它们就是普通 CJS 脚本。
from nekojs import *   # 仅给 IDE/pyright 用的类型桩入口；转译时被剥离，不影响 source map

from greet import greet, TAG


def describe(name):
    # 缩进结构、注释、elif 与 f-string 都由转译器保留
    if name == '':
        return 'nobody'
    elif name == TAG:
        return 'the tag itself'
    else:
        return greet(name)


value = describe('neko')
tag = TAG
```

**被 import 的兄弟模块（`py/greet.py`）**

```python
# 被 import 的兄弟 .py 模块：顶层定义会被导出，供 `from greet import ...` 使用。
TAG = 'py'


def greet(name):
    return 'hello, ' + name + '!'
```

期望：`value === 'hello, neko!'`，`tag === 'py'`。

## 3. language 身份 / module mode / source map / cache key 口径

| 口径 | Python 值 |
|---|---|
| `languageId` | `"python"`（`PythonTranspilerPlugin` 经 `registerLanguage("python", Set.of(".py"), ...)` 注册；`ScriptCompilerRegistry` 按后缀解析） |
| `requestedMode` | `NekoModuleMode.AUTO`（`.py` 不强制 ESM/CJS；只有 `.mjs`/`.cjs` 强制） |
| `mode`（产物） | 顶层有 `def`/`class`/赋值/import 定义名 -> `ESM`（产物带 `export { ... }`，可被 import）；纯语句脚本 -> `COMMONJS`。两种都经同一管线 |
| `code` | 可执行 JS（缩进展开为 `{}`，`def` -> hoisted `function`，操作符/真值/取模/整数除法走按需前置的 `__neko*` 助手） |
| `sourceMap` | v3，statement 粒度；`sources[0]` = authored **路径**，`sourcesContent[0]` = authored 源码原文；仅「语句起始生成行」有段，助手/prelude/export 行为空段（显式无映射） |
| `prependedLineCount` | `0`。Python 的前置行（`__neko*` 助手、异常 prelude）已在**map 内部**记为无映射行，且后续语句的映射不偏移；因此不需要 `SourceMapRegistry` 的 prepended-line 平移 |
| `cacheKey` | `NekoPreparedModule.stableCacheKey`（SHA-256 of path/language/mode/code/sourceMap），与其他语言同一事实源；失效同时受 `FileStamp` 的 mtime/size/contentHash + `NekoModuleIdentity` + compiler registry revision 控制 |

**source map 的一个必要修正**：Python source map 此前把 `sources[0]` 写成**纯文件名**
（`hello.py`）。`SourceMapRegistry` 用 source 条目把映射结果解析回 authored 文件，
因此每个 Python 诊断都会落到 `.native_esm_modules/hello.py` 而不是 authored 模块。
现在与其他前端一致发布 authored 路径（见 `PythonToJsCompiler`），
`PythonToJsCompilerTest` 原有的「`sources[0]` 含 `test.py`」断言继续成立。

## 4. 诊断阶段与位置口径

| 失败 | Stage / owner | 位置来源 |
|---|---|---|
| Python 词法/语法/发射期错误 | `PREPARE` / `Script Preparation` | `NekoCompileException`（`PythonLexer`/`PythonParser`/`PythonEmitter`/`FStringParser` 抛出的 authored 行列载体），由 `NekoModulePipeline` 发布成 `NekoModuleError` 字段 |
| import 找不到模块 | `RESOLVE` / `Module Resolution/Cache` | `sourcePath` = authored 引用方 `.py`，`moduleId` = specifier |
| 缺失导出 / link 失败 | `LINK` / `Module Resolution/Cache` | link 诊断（引用方文件行列） |
| 运行期异常（含跨 import / 异步入口） | `EXECUTE` / `Script Execution Environment` | guest 栈中**能解析到 authored 位置**的那一帧（见 §5），带失败模块身份 |
| 源文件读不到 | `CACHE` / `Module Resolution/Cache` | authored 路径 |

## 5. 执行期帧选择（本次修复的根因）

Python 产物会在顶层前置异常 prelude（`class ValueError extends Error {}` 等）。
抛出一个来自该 prelude 的类实例时，Graal 栈顶是**类声明帧**：

```text
<js> Exception(.native_esm_modules/<hash>.mjs:2:...)
<js> ValueError(.native_esm_modules/<hash>.mjs:6:...)
<js> helper(.native_esm_modules/<hash>.mjs:30:...)   <- 真正 raise 的语句
```

只有最后一帧落在 authored 语句映射上；前两帧是生成坐标，**没有映射**。宿主
（`NekoScriptModuleLoaderHost#sourceLocation`）此前取第一个 guest 帧，于是把 prelude 行
当成 authored 行（`raise` 在第 4 行却报第 5 行）。现在按优先级选帧：

1. 能解析到 authored 位置**且**属于不同模块的帧（跨 import / 动态失败）；
2. 任何能解析到 authored 位置的帧；
3. 都解析不到时，保留原始 source location 不变——**不编造**位置。

这同时把票据 12 记录的一个限制转成了可恢复位置：interop 包装的子模块失败此前
`sourceLine()` 为 `-1`，现在是子模块真实的 `throw` 行（`NekoTypeScriptJsxRuntimeTest`
的对应断言已按新行为更新，归因仍是子模块而非合成 interop 模块）。

## 6. Java 调用点迁移

**无。** 本票没有新增/删除公开 API，没有新增构造器参数、没有新增 Gradle 子项目或依赖。
内部改动仅限：

| 文件 | 改动 |
|---|---|
| `PythonLexer` / `PythonParser` / `PythonEmitter` / `FStringParser` | 抛 `NekoCompileException`（继承 `IllegalArgumentException`，消息文本逐字不变）而不是裸 `IllegalArgumentException`，携带 authored 行列 |
| `PythonToJsCompiler` | map 的 `sources[0]` 由纯文件名改为 authored 路径 |
| `NekoScriptModuleLoaderHost` | `sourceLocation` 由「第一个 guest 帧」改为「第一个能解析到 authored 位置的帧」（见 §5） |

## 7. 已知边界与未做事项

- 未运行真实 Minecraft client/server、loader runtime 或 network session smoke；本材料只声称
  common JUnit 与平台编译证据。
- Python 发射期错误的列固定为语句起点（`col 1`）：emitter 只有语句级 `srcLines`，没有
  token 级列。行是准确的。
- 未闭合的括号/调用等到文件末尾才报错（CPython 同样的文法行为），位置取 parser 实际报告处。
- 未新增 Python 语法；本票只覆盖既有 gate 的 Python 子集，不扩大语言范围。
- 未设置任何性能阈值（PERF_BASELINE 独立）；未改 05/06/07 语义；未更新任何 golden。
- Probe/declaration 侧（`.pyi`、pyrightconfig）由同一 runtime catalog 派生，
  本票未改其契约；语言与 module 归属仍由 `NekoScriptCatalogSnapshot` 单一来源决定。
