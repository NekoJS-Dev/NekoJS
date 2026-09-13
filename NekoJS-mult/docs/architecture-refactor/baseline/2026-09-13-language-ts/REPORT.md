# 工单 12 实施报告：TS/JSX/TSX 编译、source map 与执行行为路径

- 执行者：dsh-agent
- 分支：`ticket-12-language-ts`（基于 `integration/tickets-08-11`，含已关闭的工单 11）
- 证据目录：`docs/architecture-refactor/baseline/2026-09-13-language-ts/`
- 票据：`docs/architecture-refactor/implementation-tickets/12-language-ts.md`

## 0. 结论摘要

工单 12 的两个**已实测确认的真实缺陷**（主 agent 侦察提供证据，本票重写为正式测试后先红后绿）已修复，
并按 AC 补齐 corpus、错误阶段、source map 回映射、cache 失效、混合 import 归属与示例证据。

- **缺陷 A**：`.tsx` 的 language id 在 prepared module（`jsx`）与 IR（`tsx`）两层不一致。
  **裁决：`.tsx` 归 `jsx`**（理由见 §2）。
- **缺陷 B**：未终止的 TS 类型构造被静默擦到文件末尾（作者的代码凭空消失）。**已 fail closed**，
  带原始文件 + 行列在准备阶段抛错（边界见 §3）。
- 另修复一个 AC5 的真实缺口：经 ESM `import` 链执行的 `.ts`/`.jsx`/`.tsx`，其 prepared module 的
  source map 此前**没有**登记到 ESM virtual module id 上，执行期错误的原始文件/行列会丢失（见 §5）。

## 1. 交付物

### 1.1 源码（全部在 `common`）

| 文件 | 改动 | 为什么 |
|---|---|---|
| `core/compiler/NekoTypeScriptCompiler.java` | 新增 `unterminatedTypeAt` 状态、`scanPredicates()` 只读预扫描、`failUnterminated(int)` / `failClosedUnterminated()` 出口；`statementOrBlockDeclarationEnd` / `eraseImplements` / `typeExpressionEnd` 未闭合时改为报告而非吞到 `length`；新增 `jsxFrontend` 模式（构造器重载）与 `eraseJsxFrontendPreservingSourceMap`；删除零调用者的 `erasePreservingSourceMap` | 缺陷 B 的 fail closed；缺陷 A 的 TSX 泛型误擦除修复；收口唯一擦除入口 |
| `core/compiler/NekoJsxCompiler.java` | `compileTsx` 改走 `eraseJsxFrontendPreservingSourceMap` | 让擦除器知道输入已经是 JSX lowering 产物 |
| `core/compiler/NekoJsxLexer.java` | language id 取 `NekoJsxLanguagePlugin.INSTANCE.id()`，不再写死 `"tsx"` | 缺陷 A：两层语言身份同源 |
| `core/compiler/NekoJsxTokenStream.java` | 空 languageId 的默认值改为插件 id（原写死 `"jsx"`） | 同上；单一事实源 |
| `core/module/esm/NekoNativeEsmSourceRewriter.java` | `registerModule` 时把 prepared module 的 source map 登记到 ESM virtual module id | AC5：经 import 链执行的转译模块，执行期错误才能回到原始文件行列 |

**物理落点**：全部在 `common` 的既有 compiler/module 包内，未新增 Gradle project、未新增公共 SPI、未引入任何
转译依赖、未新建第二套 TS 管线。

### 1.2 测试（新增 5 个类 / 29 个用例）

| 测试类 | 用例数 | 覆盖 |
|---|---|---|
| `core/compiler/NekoTypeScriptErasureFailClosedTest` | 12 | 缺陷 B：5 个未终止形态必须抛错（带 file + 行列），7 个合法 TS 边界必须继续擦除并可执行 |
| `core/module/NekoLanguageIdentityInvariantTest` | 3 | 缺陷 A：`.js/.mjs/.cjs/.jsx/.ts/.tsx` 全体「prepared languageId == registry == IR languageId」不变量 |
| `core/module/NekoLanguagePipelineCorpusTest` | 4 | AC1/AC2/AC6：representative corpus 的 id/mode/可执行 code/可用 source map；导出形状与控制流；`.tsx` 内容与语言身份失效 |
| `core/module/NekoTranspiledLanguagePhaseCorpusTest` | 6 | AC3/AC4/AC5/AC7：automatic/classic runtime 的 element+fragment 真执行；三语言的准备期错误阶段；`.jsx/.tsx` 执行期 source map 回映射；六扩展名混合 import 的身份与错误归属 |
| `core/module/NekoTranspiledMinimalExampleTest` | 4 | AC9：三个示例 + 混合入口示例**真实执行**，漂移即失败 |

测试清理纪律沿用工单 11：全部用 `@AfterEach` + `TestModulePreparation.deleteFilesAndEmptyParents` 真删文件与变空父目录，
不使用 `deleteOnExit`（那会污染共享固定 gameDir）。

### 1.3 示例与迁移材料（AC9）

- `minimal-example.ts` / `minimal-example.jsx` / `minimal-example.tsx` / `minimal-example-entry.mjs`
- `MIGRATION.md`

## 2. 缺陷 A 的裁决：`.tsx` 归 `jsx`

### 2.1 事实

同一份 `.tsx` 文件在两层报出不同 language id：

```
ext=.ts   registryId=typescript identityMode=AUTO preparedLanguageId=typescript irLanguageId=typescript
ext=.jsx  registryId=jsx        identityMode=AUTO preparedLanguageId=jsx        irLanguageId=jsx
ext=.tsx  registryId=jsx        identityMode=AUTO preparedLanguageId=jsx        irLanguageId=tsx     <-- 不一致
```

### 2.2 裁决：`.tsx` 的 language id 是 `jsx`

**理由（按权重排序）：**

1. **插件归属是既成事实。** `NekoJsxLanguagePlugin.extensions() = {".jsx", ".tsx"}`，`id() = "jsx"`。
   language id 的语义是「哪个语言 frontend 拥有这份文件」；TSX 只是同一个 JSX frontend 的
   「JSX lowering + TS 擦除」组合形态，不是第二种语言。把它拆成第二个 id，等于承认一个 frontend 有两个身份。
2. **注册表契约。** `ScriptCompilerRegistry.getLanguage`/`replaceLanguage` 按 extension 与 **id** 匹配。
   若引入独立的 `tsx` 语言条目，`replaceLanguage("jsx", ...)` 就不能整体替换 JSX 族，插件作者必须同时替换两个 id
   ——这是没有任何行为收益的契约复杂化。
3. **缓存命中判定必须在编译前完成。** `NekoModulePipeline.identityOf` 只用注册表算出 id（契约要求它与实际产物同源，
   否则会「预判 id != 实际 id -> 永久 miss」）。若产物是 `tsx` 而 identityOf 只能说 `jsx`，这个契约直接破裂
   ——这正是缺陷 A 的机制。
4. **既有测试与默认值。** `NekoCommonBuiltinPluginTest` 断言 `getLanguage(".tsx") != null`；
   `NekoJsxTokenStream` 的 languageId 默认值本来就是 `"jsx"`。归 `jsx` 与两者一致。
5. **`.tsx` 不是独立语言，这是 TS 官方的立场。** tsc 里 `jsx: "react-jsx"` 对 `.jsx` 与 `.tsx` 是同一个 JSX 转换，
   语言身份由 `--jsx` 决定，不由扩展名分裂。

### 2.3 影响的调用点

- `NekoJsxLexer.tokenize`：不再写死 `"tsx"`，改为 `NekoJsxLanguagePlugin.INSTANCE.id()`。
- `NekoJsxTokenStream`：空值的默认值由字面量 `"jsx"` 改为插件 id（单一事实源）。
- `NekoIRProgram.languageId`（经 `NekoEsmSourceAst` -> `NekoUnifiedIrBuilder.fromEsm`）：`.tsx` 现在得到 `jsx`。
- `NekoPreparedModule.languageId()` / `NekoModuleCacheKey.languageId()`：值不变（本来就是 `jsx`），现在与 IR 一致。

**未受影响**：`.jsx` 仍是 `jsx`（没有被改成 `tsx`）；`.ts` 仍是 `typescript`；module mode、可执行产物、
JSX runtime 选择、source map 全部不变。`NekoCommonBuiltinPluginTest` 只断言非 null，无需改动。

### 2.4 底层不变量（已加测试）

真正需要保证的不是「某个 id 的具体文本」，而是 **同一文件的 prepared languageId 必须等于其 IR / 执行产物 languageId**。
`NekoLanguageIdentityInvariantTest.preparedLanguageIdEqualsRegistryAndIrLanguageIdForEveryExtension` 对
`.js / .mjs / .cjs / .jsx / .ts / .tsx` 全体断言这条三元相等（prepared == registry plugin id == IR），任一层将来漂移都会变红。

## 3. 缺陷 B 的 fail-closed 边界

### 3.1 机制

拆解器（`statementOrBlockDeclarationEnd` / `typeExpressionEnd`）本身不抛异常，只做位置计算。
未闭合时它把位置记到 `unterminatedTypeAt` 并返回 `length`；擦除主循环在**真正写 out 之前**先跑一遍只读的 `scanPredicates()`，
遇到已置位的未终止标记就用 `failClosedUnterminated()` 抛
`IllegalArgumentException("Unterminated TypeScript type construct in <file> at <line>:<col>: ...")`。
消息**只陈述事实**（文件 + 行列 + 未终止），不含修复指引——CONTEXT.md 规定报错保持普通形式、
修复指引由 wiki / MIGRATION.md 承载。

**为什么需要只读预扫描**：`typeAnnotationAt` 是谓词，主循环会先在条件里调它一次、进分支后再拆解一次。
若直接在拆解处抛异常，就会出现「谓词判真 -> 主循环已经擦掉了前面的类型片段 -> 才抛错」的撕裂。
预扫描保证「一旦会报告未终止，就在任何擦除动作之前变成异常」，同时拆解器保持纯位置计算 + 单一错误路径。

### 3.2 现在会 fail closed 的形态（有测试）

实际覆盖的是 **2 类、5 种**形态（不是「所有未终止类型构造」）：

| 形态 | 示例 | 报告位置 |
|---|---|---|
| 未闭合的 interface 体 | `interface Foo {`（+ 换行 + `  bar: string;`） | interface 关键字起点（1:1） |
| 未闭合的 type 对象体 | `type T = {` | type 关键字起点（3:1） |
| 未闭合的 export interface | `export interface I {` | interface 关键字起点（1:8） |
| 未闭合的 declare class 体 | `declare class C {` | declare 关键字起点（1:1） |
| 类型标注里的泛型参数列表未闭合 | `const x: Array<string = SIDE_EFFECT();` | 类型标注的 `:` 处（1:9） |

**仍然静默通过（本票未覆盖，已核验）**：

| 形态 | 示例 | 结果 |
|---|---|---|
| 类声明头的泛型参数列表未闭合 | `class C<T { x = 1; }` | 原样保留 → 执行期 SyntaxError |
| 函数声明头的泛型参数列表未闭合 | `function f<T { return 1; }`、`function f<T`（换行） | 原样保留 → 执行期 SyntaxError |
| 泛型调用未闭合 | `foo<Bar(1);` | 原样保留 → 执行期 SyntaxError |
| 泛型箭头未闭合 | `const f = <T, (x: T) => x;` | 部分擦除 → 执行期 SyntaxError |
| `implements` 子句未闭合 | `class C implements I {`、`class C implements { x = 1; }`、`class C implements I`（EOF） | 原样保留或按普通 `{` 处理 → 执行期 SyntaxError |

这些形态的**后果与前一类不同**：它们<strong>不删除代码</strong>（语句仍在产物里），只是产出的 JS 无法解析，
在**执行期**以硬 `SyntaxError` 失败——失败可见、不静默改语义。已在
`NekoTypeScriptErasureFailClosedTest.declarationHeaderAndImplementsFormsAreStillSilentAndKeepTheirCode` 中钉成事实。

> 说明：`eraseImplements` 中曾经加过一个 fail-closed 分支，但 `scanPredicates()` 只对 `:` 类型标注跑拆解，
> 因此该分支的状态**永远无法被消费**（dead code）。code-review 时已删除，本表如实记录 `implements` 未覆盖。

### 3.3 仍然合法、行为不变（有测试，防止过度收紧）

`interface Foo { bar: string }`、`type T = string`、`type T = { a: string }`、`function id<T>(x: T): T`、
`const id = <T,>(x: T) => x`、`const m: Map<string, number> = ...`、跨行泛型类型标注、文件末尾的 `let x: number`、
`abstract class` 成员 elide —— 全部继续擦除成功并可执行。

判定口径是**保守**的：只有在「类型声明体扫到文件末尾仍未闭合的 `{`」或「类型标注里的泛型 `<` 扫到文件末尾仍未闭合」时才报告；
任何能在文件内找到配对闭合的形态、以及擦除器根本不解析的 `<…>` 位置（声明头、调用点）都走原路径。
设计上偏向漏报：宁可让畸形输入继续沿用旧行为，也不把能跑的脚本判死。

### 3.4 顺带修复的同一 root cause 缺陷（TSX 泛型组件）

`.tsx` 的泛型组件 `<Foo<number>/>` 会被 JSX 层 lowering 成 `Foo<number>` 表达式。擦除器随后把这段平衡的 `<number>`
当成 TS 泛型实参擦掉，产物变成 `__nekoJsxFactory(Foo, null)`，**组件运行时值丢失**
（`NekoJsxCompilerTest.tsxGenericComponentWithObjectTypePassesComponentValueToFactory` 实测 expected: "function" but was: "string"）。
修复：擦除器新增 `jsxFrontend` 模式，在该模式下只有「泛型调用 `foo<T>(`」与「泛型箭头 `<T>(` / `<T>=`」两种后续形态才认定是待擦除类型参数；
后跟 `,`/)`/`]`/`;` 的 `<...>` 一律保留。这是在修复缺陷 B 的过程中被既有测试当场抓住的回归，不是本票主动扩张范围。

## 4. 行为保持

- `.ts` / `.jsx` / `.tsx` 全部保留；扩展名、module mode、JSX runtime 选择、classic factory 形态未变。
- 装饰器仍然明确报错（`unsupported("decorator (@)", ...)`），语义未变。
- 工单 11 已验收的 `NekoPreparedModule` / `NekoModulePreparation` / `NekoModuleCacheKey` / `NekoModuleIdentity` /
  错误阶段模型**只消费未重定义**；本票唯一的模块层改动是 `NekoNativeEsmSourceRewriter` 多登记一份 source map
  （纯增量，不改变返回值/控制流）。
- 既有 golden **零变更**（见 §7）。
- `TypeScriptErasureParseCorpusTest` 的「擦除产物行数不变」不变量保持：fail closed 是新增失败路径，
  成功路径的等长擦除语义未动。

## 5. AC5 的真实缺口（本票修复）

工单 11 的 `NekoModuleErrorMappingCorpusTest` 覆盖了 `.ts` 的 source map 回映射，但那条路径是 CJS/script 加载
（`NekoModuleReadService` -> 磁盘相对路径），而 **ESM import 链**走的是 `NekoNativeEsmSourceRewriter.registerModule` ->
`NekoEsmVirtualModuleRegistry` 的 virtual module id。Graal 报的源位置是 virtual id，而 `ScriptMapRegistry` 只按精确路径查表
（工单 11 特意去掉了 `endsWith` 兜底，避免同后缀路径串味），于是查不到映射、原始文件/行列丢失。

本票在 `registerModule` 里把 prepared module 的 source map 用**同一个 virtual module id** 登记。
新增 `NekoTranspiledLanguagePhaseCorpusTest.executionErrorInJsxAndTsxMapsBackThroughSourceMap` 钉住：
`.jsx` 的 throw 回到原始第 3 行、`.tsx` 回到原始第 4 行，且模块身份（languageId/mode/cacheKey）可解释。

## 6. 收缩 gate（Work item 6）

**结论：TS/JSX/TSX 不存在旧并行转换或装载旁路，无可删除内容。**

`grep` 复核（限非 test/build/docs）：

- TS 擦除入口：`eraseTypescript`（public，`NekoNodeModuleInstaller` 加载内置 node 模块资源用）、
  `eraseDetailed`（`NekoTypeScriptLexer` 用）、`eraseJsxFrontendPreservingSourceMap`（`NekoJsxCompiler.compileTsx` 用）。
  收口后去掉了零调用者的 `erasePreservingSourceMap`。
- JSX 转换入口：`compileJsx` / `compileTsx` 的唯一生产调用者是 `NekoJsxLexer`。
- `src/`、`versions/`、`common-api-processor/` 内**零** `NekoTypeScriptCompiler` / `NekoJsxCompiler` 引用。

即：TS/JSX/TSX 已经全部经 `NekoModulePipeline.prepare` 这一条 Preparation 路径，与 JS/CJS/ESM 共享同一份
prepared module / 身份 / cache key / source map / 错误阶段。没有为凑票据删除任何无辜代码。

## 7. golden 与既有测试

**既有 golden 零变更。** `NekoCompilerGoldenTest` / `TypeScriptErasureParseCorpusTest` / `NekoJsxCompilerTest` /
`NekoJsxFragmentTest` / `NekoJsxAttributesTest` / `NekoCompilationPipelineJsxRuntimeTest` 在生产代码修好之后全部保持绿
——`.tsx` 泛型组件那条用例是本票修复的**目标**（见 §3.4），不是需要维护者审阅的 golden 变更。

## 8. 逐 AC 判定

| AC | 判定 | 证据 |
|---|---|---|
| AC1 三档 representative corpus 产生正确 language id、module mode、可执行 code/IR、非空可用 source map | **pass** | `NekoLanguageIdentityInvariantTest`（六扩展名三元 id 相等 + cacheKey/identityOf 同源）、`NekoLanguagePipelineCorpusTest.representativeCorpusProducesLanguageIdModeExecutableCodeAndUsableSourceMap`（真执行出期望值 + source map 注册后映射回原始行） |
| AC2 类型擦除不改变运行时值、控制流、导出形状 | **pass** | `NekoLanguagePipelineCorpusTest.typescriptErasureKeepsControlFlowValuesAndExportShape`（导出形状 3 个、类型导出不计数、产物真跑出 `pos:41`） |
| AC3 JSX/TSX element、fragment、automatic runtime 与既有 golden/执行测试一致 | **pass** | `NekoTranspiledLanguagePhaseCorpusTest.tsxAutomaticRuntimeElementAndFragmentExecuteCorrectly`（jsx/jsxs/Fragment + 注入 import + 真执行元素树）、`jsxClassicRuntimeElementAndFragmentExecuteCorrectly`；既有 `NekoJsxCompilerTest` 全绿 |
| AC4 准备期错误携带原始文件、行列与阶段，不被后续 JS 位置覆盖 | **pass** | `preparationErrorsCarryOriginalFileAndLineForTsJsxTsx`（三语言 + 断言不含 PolyglotException）、`NekoTypeScriptErasureFailClosedTest`（13 例：5 例 fail closed + 7 例合法边界 + 1 例「仍静默」边界，file+行列） |
| AC5 执行期异常经 source map 回原始位置并保留模块身份 | **pass** | `executionErrorInJsxAndTsxMapsBackThroughSourceMap`（`.jsx` 3 行、`.tsx` 4 行 + languageId/mode/cacheKey）；修复见 §5 |
| AC6 源码/mode/language id/依赖变化失效 cache，未变化可观察命中 | **pass** | `NekoLanguagePipelineCorpusTest.tsxContentChangeInvalidatesCacheAndLanguageIdentityChangeIsObservable`；工单 11 的 `NekoModulePreparationIdentityTest` 四维失效保持绿 |
| AC7 与 JS/CJS/ESM 混合 import 的身份和错误归属可追踪 | **pass** | `mixedImportsTrackIdentityAcrossJsCjsEsmTsJsxTsx`（六扩展名真执行 + 身份逐一断言）、`mixedImportErrorIsAttributedToTheFailingTranspiledModule`（错误归属到真正抛错的 `.tsx`） |
| AC8 不新增外部转译依赖、公共 parser SPI、Gradle project 或第二套 TS 管线 | **pass** | §6 的 grep 证据；未改 `settings.gradle.kts` / `build.gradle.kts`；未新增接口 |
| AC9 交付 `.ts/.jsx/.tsx` 最小可运行示例与迁移材料 | **pass** | `minimal-example.ts/.jsx/.tsx/-entry.mjs` + `MIGRATION.md`；`NekoTranspiledMinimalExampleTest` 把示例当真脚本执行 |

## 9. 未验证项与 owner

- **真实 Minecraft 客户端/服务端 smoke（not-verified）**：本票改动全在 `common`，未启动真实客户端，也未用
  minecraft-mcp 跑 `/nekojs reload`。owner：维护者 / 工单 19 / 工单 34。结构证据是 `guardLint` 的 common MC/loader isolation 复核。
- **节点 `:26.1.2:compileJava` 未单独执行**：本票改动全部在 `common` 且不触碰平台面；owner：维护者。
- `.ts` 的行级映射粒度：TS 擦除的产物行号与原始源**逐行相同**（等长擦除 + 显式 identity map），因此
  `NekoModuleErrorMappingCorpusTest` 的 `.ts` 行断言是精确的；列是语句粒度（既有结论，未变）。

## 10. 遗留（不在本票范围）

**先区分两类后果（重要，两者严重度不同）：**

| | 旧缺陷（**已修**） | 现遗留（**未修，本票不要求修**） |
|---|---|---|
| 行为 | 静默**删除**代码：作者的可执行语句凭空消失 | 静默**保留**代码：产出无法解析的 JS |
| 何时失败 | 永不失败（脚本语义已被悄悄改变） | **执行期硬 `SyntaxError`**，失败可见 |
| 风险 | 高——无提示地改变脚本语义 | 低——不丢代码、失败可见 |

实测对照：`interface Foo {` + `SIDE_EFFECT();` 在旧实现下整段被擦成空白（`SIDE_EFFECT()` 消失）；
而下面的遗留形态里 `SIDE_EFFECT()` 仍在产物中，只是整份 JS 无法解析。

1. **TS 擦除器的整体容错哲学仍不统一（第二类，低严重度）**：除 §3.2 已覆盖的 5 种外，
   下列形态仍静默产出无法解析的 JS，由 Graal 在执行期报 `SyntaxError`（不删代码、失败可见）：
   - 声明头泛型参数列表未闭合：`class C<T { … }`、`function f<T { … }`、`function f<T`（换行）
   - 泛型调用/泛型箭头未闭合：`foo<Bar(1);`、`const f = <T, (x: T) => x;`
   - `implements` 子句未闭合：`class C implements I {`、`class C implements { … }`、`class C implements I`（EOF）
   - 其他：`satisfies` 后跟对象类型、类内 index signature `[key: string]: number`、`import A = require(...)`
   这类形态能不能安全判定、值不值得 fail closed，应由后续语言票结合真实作者反馈评估。owner：后续语言票 / 维护者。
2. **ESM parser 对纯 JS 语法错误的宽松度**：`.js` 与 `.ts` 对 `const = = 1;`、未终止字符串、孤立 `}`、
   未闭合函数体完全相同（都不抛）。该宽松来自 ESM parser，**不是本票引入的 TS 专属问题**，且工单 11 已验收该层；
   本票未改动 ESM parser 的通用容错。owner：后续语言票 / 维护者。
3. `.jsx` 下悬空的 `<Foo<number>/>`（泛型组件语法误用于 `.jsx`）的报错质量仍可改进。owner：后续语言票。

## 11. 门禁

见票据 Closure record 的「门禁」小节（`:common:test --rerun-tasks --no-build-cache`、`:common:check`、`guardLint`）。