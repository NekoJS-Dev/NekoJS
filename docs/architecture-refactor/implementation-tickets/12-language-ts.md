# 12: TS/JSX/TSX 编译、source map 与执行行为路径

**What to build:** 脚本作者写入 .ts/.jsx/.tsx 后，类型擦除、JSX 结构、runtime 注入、模块身份、缓存失效和 Graal 执行结果保持既有语义；语法或转换错误在准备阶段带原始 TS/JSX/TSX source location，运行时错误也能回映射。声明与语言能力归属不因转译丢失。

**Blocked by:** [11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md)

**Status:** closed

**Assignee:** 12-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 扩展 TypeScript erasure、JSX element/fragment、automatic runtime 和 TSX corpus，覆盖固定源码到可执行输出的行为与 source-map 映射。, 确保 requested mode、扩展名和 language id 共同决定 TS/JSX/TSX prepare 结果，Resolution/Cache 不复制类型擦除或 JSX 转换语义。, 为类型语法、 JSX 结构和 TSX 组合错误保留原始行列、文件和准备阶段，生成 JS 后的运行时异常也能回映射。, 将 language id、mode、内容、依赖图和 source map 纳入 cache invalidation，避免 TS/TSX 改动命中旧 JS 产物。, 保持纯 Java 自研实现；不引入非纯 Java 转译器，不因候选库评估阻塞行为票。, 收缩 gate：TS/JSX/TSX 旧并行转换或装载旁路只有在替代 behavior、source-map/declaration、trace 通过且无调用者后移除；随本票完成收缩，不推迟 final release，也不删除公开语言。

## Acceptance criteria

- [x] .ts、.jsx、.tsx representative corpus 均产生正确 language id、module mode、可执行 code/IR 和非空可用 source map。
- [x] TypeScript 类型擦除不改变运行时值、控制流和导出形状；corpus 固定有意承诺的行为而非私有 AST 布局。
- [x] JSX/TSX 的 element、fragment 和 automatic runtime 行为与既有 golden/执行测试一致。
- [x] 准备期语法/转换错误携带原始 TS/JSX/TSX 文件、行列和阶段；不会被后续 JS 位置覆盖。
- [x] 执行期异常经 source map 回到原始 TS/JSX/TSX 位置，并保留模块身份。
- [x] 源码、mode、language id 或依赖变化使 cache key/revision 失效，未变化输入可观察命中。
- [x] TS/JSX/TSX 与 JS/CJS/ESM 混合 import 的身份和错误归属可追踪。
- [x] 不新增外部转译依赖、公共 parser SPI、Gradle project 或第二套 TS 管线。
- [x] 随实现交付 .ts、.jsx、.tsx 的最小可运行示例与必要迁移材料；示例只使用已通过 gate 的语法、runtime 和模块能力。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [语言模块管线规格](../specs/06-language-module-pipeline.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md): TS/JSX/TSX 必须消费同一个不可变 prepared module、模块身份、cache key 和错误阶段模型；否则会重新形成转译语言私有管线。

## Scope and coordination

- **Rationale:** TS/JSX/TSX 的风险在行为、source map 和缓存，而不是 parser 接口；该票从作者源文件到执行结果和诊断完整闭合。
- **Coordination:**
  - MANAGED_SURFACE: TS declaration 的规范源与显式 golden 更新由 managed surface 票约束。
  - DIAGNOSTICS: 错误字段、workspace 展示和用户报告消费本票的 source-map/阶段结果。
  - PERF_BASELINE: TS/TSX corpus 执行时间只作对照记录，不擅自设定阈值。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## Closure record（2026-09-19）

- 执行者：12-agent。工作流：读票/规格 06/票 11 全部 addendum/README 认领规则/票 11 baseline →
  源码定位（prepared module、pipeline、cache、host、compiler/lexer、source-map utility）→
  用 10 个一次性探针测试（已删除）实测当前 TS/JSX/TSX 行为并定位真实缺陷 →
  TDD 红（`NekoTypeScriptJsxPrepareTest` 6/8 红、`NekoTypeScriptJsxRuntimeTest` 7/12 红）→
  实现收口（绿）→ 全量 `./gradlew.bat :common:test --rerun-tasks`（1696 tests, 0 failed）→
  `:common:check` + `:26.2.0:compileJava` + `:26.2.0-fabric:compileJava` + `git diff --check` →
  双轴自查（Standards：合并 `stagedHostFailure`/`findStagedError` 双重实现与
  `nestedOrExecute`/`differentModuleFailure` 重复判断，删除无效的
  `innerFailureLocation` 实验；Spec：逐条 AC 对照 seam）→ 勾选 → 关票提交（不 push）。
- 先决输入（票 11 closed，不重做）：`NekoPreparedModule`/`NekoModulePipeline`/
  `NekoModulePipelineCache`/`NekoModuleError`/`NekoSourceMapBuilder.identity` 的形态与
  语义未变；本票只在既有 compiler/lexer 层补语义、在既有 host seam 补位置解析。

### 交付物（源码）

- `NekoTsxLanguagePlugin`（新，`core.compiler`）：`.tsx` 的独立 language id `tsx`；
  `NekoJsxLanguagePlugin` 收窄为 `.jsx`。两者共用同一个 `NekoJsxLexer`（`.tsx` 时先擦除
  再下降），**没有第二套 TSX 管线**。注册点 `NekoCommonBuiltinPlugin`。
- `NekoCompileException`（新，`core.compiler`）：语言前端的 authored 行列载体；
  继承 `IllegalArgumentException` 且消息文本逐字保留，旧的 message 断言不受影响。
- `NekoTypeScriptCompiler` / `NekoJsxCompiler`：擦除/下降失败改抛带精确索引位置的
  `NekoCompileException`（decorator、enum 字面量、参数属性、全部 JSX 结构错误）。语义未变。
- `NekoModuleError.prepare(..., sourceLine, sourceColumn, ...)`：显式位置重载；
  `NekoModulePipeline` 把 `NekoCompileException` 的 authored 位置发布成错误字段。
- `NekoScriptModuleLoaderHost`：执行期位置解析收口为一个 `mappedPosition` 入口
  （虚拟路径优先、authored 路径兜底、placeholder 路径回落），CommonJS 包装行偏移
  （`cjsBodyLineOffset`），synthetic `#cjs-interop`/`#dynamic` 模块身份还原为 authored 模块，
  嵌套 staged 失败优先于 wrapper 的 EXECUTE 归类，guest 文本里的 Java 栈帧不再被当作脚本位置。
- `script-loader.js`：`bodyLineOffset()` 自测 `new Function` 合成头行数并随 `configure` 上报
  （不硬编码：宿主 JVM/引擎换版本时偏移自动跟随）。
- 未引入外部转译依赖、未新增公共 parser SPI、未新增 Gradle project；resolver/cache/host
  仍不复制任何类型擦除或 JSX 转换语义。

### 交付物（测试，common，3 新类 22 用例 + 1 类补强）

- `NekoTypeScriptJsxPrepareTest`(6)：AC1/AC4/AC6 的 prepare 级 seam。
- `NekoTypeScriptJsxRuntimeTest`(12)：AC2/AC3/AC5/AC7 的执行级 seam。
- `TypeScriptJsxExamplesSmokeTest`(4)：AC9 示例冒烟 + 文档化 source-map/身份。
- `NekoCommonBuiltinPluginTest`(1，补强)：断言 `.ts`/`.jsx`/`.tsx` 三个语言 id 各自注册。
- 回归未破坏的 prior art：`NekoModuleIdentityLifecycleTest`(25)、
  `ModulePipelineIsolationTest`(16)、`LegacyCjsBridgeCharacterizationTest`(7)、
  `NekoTypeScriptCompilerTest`/`NekoJsxCompilerTest`/`NekoCompilerGoldenTest`/
  `TypeScriptErasureParseCorpusTest`/`NekoCompilationPipelineJsxRuntimeTest`（全绿）。

### 交付物（示例/迁移）

- `common/src/test/resources/nekojs/language-ts-examples/{ts,jsx,tsx}/` 最小可运行示例
  （`hello.ts`+`greet.ts`、`hello.jsx`+`greet.jsx`、`hello.tsx`+`greet.tsx`）。
- `docs/architecture-refactor/baseline/2026-09-19-language-ts/MIGRATION.md`：
  脚本零迁移声明、作者可见的两点变化（`.tsx` 独立 language id、错误位置更精确）、
  示例内联、语言身份/source map/cache key 对照表、错误阶段与位置口径、Java 调用点迁移表、
  已知边界与未做事项。

### 验收判定（逐条 seam + 命令 + 结果）

| AC | 实现 seam / 测试方法 | 精确 Gradle 命令 | 结果与限制 |
|---|---|---|---|
| 1 | `NekoTypeScriptJsxPrepareTest#typescriptJsxAndTsxPublishLanguageModeCodeAndUsableMap`、`#jsxAndTsxStayDistinctLanguageIdentitiesForTheSameExtensionFamily` | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoTypeScriptJsxPrepareTest` | PASS（6/6）；三条 corpus 断言 language id（typescript/jsx/tsx）、mode、可执行 code（擦除与下降都已发生）与「每条生成行都映射到真实 authored 行」的可用 map。 |
| 2 | `NekoTypeScriptJsxRuntimeTest#typeErasurePreservesRuntimeValuesControlFlowAndExportShape`、`#tsxAndJsxLeaveTheSameExportShape` | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoTypeScriptJsxRuntimeTest` | PASS（12/12）；经真实 Graal 执行断言控制流（for/if/三元）、算术、参数属性赋值、`enum` 数值成员、对象形状与导出名，不断言私有 AST 布局。 |
| 3 | `NekoTypeScriptJsxRuntimeTest#classicRuntimeLowersElementsFragmentsAndChildren`、`#automaticRuntimeProducesJsxCallsAndFragmentIdentity`；既有 `NekoJsxCompilerTest`/`NekoJsxFragmentTest`/`NekoJsxAttributesTest`/`NekoCompilationPipelineJsxRuntimeTest` 未改且全绿 | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoTypeScriptJsxRuntimeTest --tests com.tkisor.nekojs.core.compiler.NekoJsxCompilerTest --tests com.tkisor.nekojs.core.compiler.NekoJsxFragmentTest --tests com.tkisor.nekojs.core.compiler.NekoCompilerGoldenTest` | PASS；classic element/fragment/children 与 automatic `jsx`/`jsxs`/`Fragment` 身份均经真实执行断言。**golden 未更新**。 |
| 4 | `NekoTypeScriptJsxPrepareTest#typescriptTransformErrorCarriesAuthoredFileLineAndColumn`、`#jsxAndTsxStructureErrorsCarryAuthoredFileLineAndColumn`；执行期的 `NekoTypeScriptJsxRuntimeTest#loweredJsSyntaxErrorAtExecutionReportsTheAuthoredJsxLine`、`#loweredTsSyntaxErrorAtExecutionReportsTheAuthoredTsxLine` | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoTypeScriptJsxPrepareTest --tests com.tkisor.nekojs.core.module.NekoTypeScriptJsxRuntimeTest` | PASS；`NekoModuleError` 带 `Stage.PREPARE`/`owner=Script Preparation` 与 authored 文件+行列（decorator 2:1、JSX/TSX 结构错误 3:3、下降后语法错误 4:1，而非生成行）。 |
| 5 | `NekoTypeScriptJsxRuntimeTest#runtimeFailureInTranspiledModuleReportsAuthoredLineAndIdentity`、`#cjsRuntimeFailureReportsAuthoredLineNotGeneratedWrapperLine`、`#crossImportRuntimeFailureInTranspiledChildKeepsChildAuthoredIdentity` | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoTypeScriptJsxRuntimeTest` | PASS；执行期异常带 guest cause、authored `sourcePath`/`moduleId`；CommonJS 包装偏移已扣除（作者行 3 不再报成 4）。**限制**：literal dynamic import 到失败的 CommonJS 子模块时 Graal 的 promise/host 边界丢失子模块身份，错误归引用方且行列为 `-1`（不编造位置）；同步 `#cjs-interop` 路径保留子模块身份。 |
| 6 | `NekoTypeScriptJsxPrepareTest#unchangedTsJsxTsxInputsHitTheSamePreparedIdentity`、`#contentLanguageAndModeChangesInvalidateTsJsxTsxEntries`；既有 `NekoModulePipelineCacheStampTest` 未改且全绿 | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoTypeScriptJsxPrepareTest --tests com.tkisor.nekojs.core.module.NekoModulePipelineCacheStampTest` | PASS；未变化输入稳定命中；TS/TSX 内容、`.ts`/`.jsx` 语言身份替换、mode 变化都产生不同 cache key 并返回新产物。 |
| 7 | `NekoTypeScriptJsxRuntimeTest#mixedImportsResolveAndPreserveModuleIdentity`、`#linkFailureAcrossMixedImportIsAttributedToTheAuthoredImportingModule`、`#crossImportRuntimeFailureInTranspiledChildKeepsChildAuthoredIdentity` | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoTypeScriptJsxRuntimeTest` | PASS；ts↔tsx↔mjs↔jsx 双向 import 的返回值与身份、混合 link 失败归引用方（LINK）、跨 import 执行失败归失败子模块（EXECUTE，`#cjs-interop` 已还原为 authored 路径）。 |
| 8 | `ModulePipelineIsolationTest`(16) 的纯层源码/签名扫描与 `pipelinePublicSurfaceExposesNoParserSpi`；`git status` 无新 Gradle 子项目、无新依赖 | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.ModulePipelineIsolationTest --tests com.tkisor.nekojs.core.module.LegacyCjsBridgeCharacterizationTest`；`git diff --stat` | PASS；common 未新增 import Minecraft/loader，未新增 build 文件/依赖，未新增公共 parser SPI，TSX 与 JSX 共用同一 lexer（无第二套管线）。 |
| 9 | `TypeScriptJsxExamplesSmokeTest`(4) + `common/src/test/resources/nekojs/language-ts-examples/` + `baseline/2026-09-19-language-ts/MIGRATION.md` | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.TypeScriptJsxExamplesSmokeTest` | PASS；三个示例经真实 host 装载并断言文档承诺的输出、language id 与 authored source-map 映射。真实 loader/in-game smoke 不在此命令内。 |

### 综合门禁

```text
./gradlew.bat :common:compileJava :common:compileTestJava
./gradlew.bat :common:test --rerun-tasks      → 1696 tests completed, 0 failed, 4 skipped
./gradlew.bat :common:check
./gradlew.bat :26.2.0:compileJava :26.2.0-fabric:compileJava
./gradlew.bat :26.1.2:compileJava :26.1.2-fabric:compileJava :26.1.2:compileTestJava :26.1.2-fabric:compileTestJava :26.2.0:compileTestJava :26.2.0-fabric:compileTestJava
git diff --check
```

以上命令均 PASS（平台编译仅有既有 Gson `InlineMe`/Guava 注解缺失 classfile warning 与
既有 deprecation warning，不影响成功）。**未更新任何 golden**。

### 双轴自查结论

- **Standards**：`NekoTsxLanguagePlugin` 复用 `NekoJsxLexer` 而非复制管线；执行期位置解析
  收口为单一 `mappedPosition` 入口，删除重复的 `stagedHostFailure` 与无效的
  `innerFailureLocation` 实验；`NekoCompileException` 只承载位置，不新增 parser/compiler SPI；
  cache key 与 source map 仍是既有单一事实源（`NekoPreparedModule.stableCacheKey`、
  `SourceMapRegistry`）。无投机抽象、无未调用 helper。
- **Spec**：9 条 AC 均落到最高调用者 seam（`prepare` 的 prepared module 字段、
  `loadEntry`/`loadEntryAsync` 的返回值与异常字段），未断言私有 AST 布局或内部函数调用。

### 限制与未做事项

- 未运行真实 Minecraft client/server、loader runtime 或 network session smoke（票据 41/48 范围）；
  本 closure 只声称 common JUnit 与平台编译证据。
- literal dynamic import → 失败的 CJS 子模块的子模块身份在 Graal promise/host 边界丢失
  （见 AC5 行）；未以伪造位置掩盖。
- `.mts`/`.cts` 仍不是已注册语言扩展（`identify` 能得到 ESM/COMMONJS mode，但 prepare 报
  「No script compiler registered」）；既有行为不变，本票不扩大语言范围。
- 未设置任何性能阈值（PERF_BASELINE 独立）；未改 05/06/07 语义。

## Review-round-1 addendum（2026-09-19）

本轮针对协调者复核的两条 Standards findings 在 `ffc5045b` 工作树上 fix-forward；
`Status` 保持 `closed`，不反勾已验证 AC。两条都不是"补说明"，而是实际修源码 + 补可区分测试。

- **F3（enum 数字字面量诊断位置）— confirmed, fixed.** 原实现
  `badEnumNumberLiteral` 用 `source.indexOf(literal)` 在**原始源码**里找位置：注释、
  字符串或更早的合法成员里的同名 token 会先被命中；未命中时回退索引 `0`，
  等于把错误报成 authored `1:1`——与本票"不把生成位置当成 authored 位置"的承诺反向冲突。

  修法（选协调者给的 (a)，因为 phase 1 擦除只做等长空白替换，真实 index 本来就可得）：
  `EnumMember` 增加 `valueStart` 组件；`transformOneEnum` 把 enum body 的 authored 起始
  偏移传给 `parseEnumMembers(body, bodyStart)`；`addEnumMember` 由 segment/eq/前导空白
  算出该成员值的精确 authored 偏移；两处 `badEnumNumberLiteral` 调用点改传
  `m.valueStart()`。同时把 `diagnostic(message, index)` 的语义收紧为
  **负索引 = 位置未知 → 发布 `-1`**，不再回退到 `1:1`（这一条是比 F3 更根本的诚实性修复：
  它保证任何"拿不到位置"的 TS 诊断都不会伪造 `1:1`）。

  证据：新测试 `NekoTypeScriptEnumDiagnosticLocationTest`(3)——同名 token 先出现在
  第 1 行注释、第 2 行字符串（断言 4:列）、先出现在上一个**合法**成员
  `One = 1e0`（断言命中第 3 行而非第 2 行）、多行 enum 前文已有同字面量
  （断言 5:列）。红侧已实测：把 `badEnumNumberLiteral` 换回旧 `indexOf` 实现后
  该测试 **2/3 失败**（`earlierCommentOccurrence…` 与 `multiLineEnum…`），
  恢复后 3/3 通过。

- **F-AC4（执行期才发现的下方语法错误映射路径）— reachable, kept, now discriminating.**
  自己判定结论：**不是死代码**。用一次性探针（已删除）确认同一文件
  `cache.prepare(file)` **成功**（`prepare=SUCCEEDED`），而 `host.loadEntry` 抛
  `PREPARE/Script Preparation line=4 col=1 cause=NekoEsmLinkException`——即诊断只能由
  `withSyntaxLocation` 重解析后经 `authoredPosition` 映射产生，prepare 阶段看不见。
  原有两个用例不可区分，是因为它们只断言 `Stage.PREPARE`，没有断言
  "prepare 已成功" 与 "cause 是 executor 重解析的 `NekoEsmLinkException`"。

  修法：两个用例改名为 `loweredJsSyntaxErrorIsMappedAfterPreparationSucceeded` /
  `loweredTsSyntaxErrorIsMappedAfterPreparationSucceeded`，各自先断言
  `cache.prepare(file)` 成功且 language id 正确（jsx / tsx），再断言
  `staged.getCause() instanceof NekoEsmLinkException`。红侧已实测：临时移除
  `authoredPosition` 映射（退回无位置的 `NekoModuleError.prepare` 重载）后，
  **恰好这两个用例失败**（`expected: <4> but was: <-1>`），恢复后 12/12 通过。
  因此原分支被保留，且现在有用例区分"准备期发现"与"执行器抛错后才映射"。

### Review-round-1 evidence matrix

| Finding | 精确证据 | 精确命令 | 结果与限制 |
|---|---|---|---|
| F3 位置不得被更早同名 token 偷走 | `NekoTypeScriptEnumDiagnosticLocationTest`(3) 的注释/字符串/上一成员/多行用例 | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.compiler.NekoTypeScriptEnumDiagnosticLocationTest` | PASS（3/3）；旧 `indexOf` 实现下 2/3 红，已实测。未命中位置一律 `-1`，不再伪造 `1:1`。 |
| F-AC4 映射路径可达且被覆盖 | `NekoTypeScriptJsxRuntimeTest`(12) 的两个 `…IsMappedAfterPreparationSucceeded` 用例：先断言 `cache.prepare` 成功，再断言 `cause` 是 `NekoEsmLinkException` | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoTypeScriptJsxRuntimeTest` | PASS（12/12）；移除映射分支后恰好 2 个用例红，已实测。分支保留，无删除。 |
| 回归未受影响 | `NekoTypeScriptCompilerTest`、`NekoCompilerGoldenTest`、`TypeScriptErasureParseCorpusTest`、`NekoJsxCompilerTest` 及全量 common | `./gradlew.bat :common:test --rerun-tasks` | PASS；1730 tests / 0 failures / 0 errors / 4 skipped。tsc enum 语义（`enumValidNumericLiteralsCompile`、`enumRejectsInvalidPrefixedNumericLiterals` 等）全部保持。 |
| 综合门禁 | common check | `./gradlew.bat :common:check`；`git diff --check` | PASS；未更新 golden。 |

### 仍未覆盖 / 边界

- `-1`（位置未知）当前在 `NekoTypeScriptEnumDiagnosticLocationTest` 的正式用例里没有被
  直接触发：本实现两处 enum 诊断都能算出真实偏移，`-1` 只在"成员无值"或理论上的
  偏移越界时出现，为防御性语义。未为纯理论分支编造测试。
- 本轮只动了 TS enum 诊断位置与执行期映射用例的区分度；未改任何语言语义、
  未改 cache/身份/trust 边界、未扩大语言范围。
- 仍未运行真实 Minecraft client/server、loader runtime 或 network session smoke。
- 工作树同期有其它票（build.gradle / stonecutter / tools/nekojs-ci-gates.py 等）
  的在途改动，属他人写集；本轮提交只包含本票文件。
