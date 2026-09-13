# Ticket 11 实施报告：JS/CJS/ESM 模块身份、缓存与生命周期基础路径

> 工单：`docs/architecture-refactor/implementation-tickets/11-language-pipeline.md`（W3 行：Script Preparation + Module Resolution/Cache）。
> 权威 spec：`docs/architecture-refactor/specs/06-language-module-pipeline.md`；裁决源 `decisions/06-language-module-pipeline.md`。
> 基线：master `4e258d3a`（ticket 05 closed，本票唯一 blocker）。分支 `ticket-11-language-pipeline`。
> 本报告 commit 区间：`cb1ce575..本提交`。

---

## 0. Commit 清单

| Commit | 内容 | 覆盖 AC |
|---|---|---|
| `cb1ce575` | `refactor(module)`：prepared module 身份/cache key + 模块缓存改 runtime owner 显式持有 | AC1/AC2/AC4/AC5/AC9/AC10（实现面） |
| `0240b1b1` | `test(module)`：身份/cache key + Resolution/Cache characterization corpus | AC1–AC6 |
| `f3f026b4` | `test+docs(module)`：错误映射/trust/边界/示例 + 本报告 | AC7/AC8/AC10/AC11 |
| `f23fe3ed` | `test+docs(ticket-11)`：补 AC2/AC6 强化证据并关闭工单 | AC2/AC6 |
| 本提交 | `refactor+test(module)`：code-review 双轴修订（死代码/装配重复/第二入口/AC7 列与身份/identity 冲突/AUTO 行为记录/A9 归属） | AC1/AC2/AC3/AC7/AC9/AC10 |

---

## 1. 物理落点与所有权变化

### 1.1 删除的 process-wide static / legacy 门面

| 符号 | 原位置 | 处理 |
|---|---|---|
| `NekoModulePipeline.SHARED_COMPILATION_PIPELINE` | `core/module/NekoModulePipeline.java:30` | **删除**（static 编译管线实例） |
| `NekoModulePipeline.LEGACY_INSTANCE` | 同文件 :31 | **删除**（`static volatile` 模块管线实例） |
| `NekoModulePipeline.bindLegacyInstance` | 同文件 :43 | **删除**（static 写点） |
| `NekoModulePipeline.legacyInstance` | 同文件 :47 | **删除**（static 读点） |
| `NekoModulePipeline.legacyPrepare` / `legacyPrepareFallback` | 同文件 :139/:147 | **删除**（static 准备门面 + 静态回退） |
| `NekoModulePipelineCache`（整类，含 `PREPARED_CACHE` static Map + 全部 static 方法） | `core/module/NekoModulePipelineCache.java` | **删除**，由实例级 `NekoModulePreparation` 替换 |
| `NekoRuntimeAssembly:91` 的 `bindLegacyInstance(...)` | `core/lifecycle/NekoRuntimeAssembly.java` | **替换**为显式构造 `NekoModulePreparation` 并注入 root/sandbox |

**生产源码中上述符号引用现为 0**（`git grep` 可复核；ticket 05 总账 A7 的删除条件即「W3 显式注入 pipeline/cache 后删 `bindLegacyInstance/legacyInstance/legacyPrepare`」——本票满足该条件）。

> 注：总账 A7 把 `bindLegacyInstance/legacyInstance/legacyPrepare` 标为「待删除 legacy bypass（static binding）」，删除条件写明归 W3。本票是 W3 行，故删除与总账分类一致，无冲突。A8（`PREPARED_CACHE`）分类为 domain adapter-owned（W3），本票将其改为实例所有权并消除了 process-wide 维度，同样落在 A8 的「W3 处理失效语义与显式注入」范围内。

### 1.2 新增/重构的源码

| 文件 | 角色 | 对应 AC |
|---|---|---|
| `core/module/NekoModuleIdentity.java`（新） | 语言身份（language id + 请求 mode） | AC1/AC4 |
| `core/module/NekoModuleCacheKey.java`（新） | 稳定 cache key = languageId \| mode \| sourcePath \| contentHash | AC1/AC4 |
| `core/module/NekoModuleDiagnosticOrigin.java`（新） | 原始诊断位置（源路径 + 前置无源对应行数） | AC1/AC7 |
| `core/module/NekoContentHash.java`（新） | SHA-256 内容指纹（从旧 FileStamp 抽出） | AC4 |
| `core/module/NekoPreparedModule.java`（重构） | 增加 languageId/mode/sourcePath/cacheKey；全部组件不可变 | AC1/AC2 |
| `core/module/NekoModulePreparation.java`（新，替换 PipelineCache） | **实例级** prepared cache；命中 = stamp ∧ identity | AC2/AC4/AC5 |
| `core/module/NekoModulePipeline.java`（重构） | 去除全部 static；新增 `identityOf` | AC1/AC9/AC10 |
| `core/lifecycle/NekoRuntimeRoot.java`（重构） | **显式持有** `NekoModulePreparation`；close 释放 | AC2/AC5 |
| `core/lifecycle/NekoRuntimeAssembly.java`（重构） | 显式构造注入，替换 static 绑定 | AC2 |
| `core/fs/NekoModuleReadService.java`（重构） | static 工具类 → 实例，注入 preparation | AC2/AC10 |
| `core/fs/NekoJSFileSystem.java`（重构） | 注入 readService，移除 static 调用 | AC2 |
| `core/NekoSandboxFactory.java`（重构） | 注入 preparation 并传给 fs/node installer | AC2 |
| `core/node/NekoNodeModuleInstaller.java`（重构） | 新增 preparation 形参；独立入口自建实例 | AC2 |
| `core/module/NekoScriptModuleLoaderHost.java`（重构） | 构造注入 preparation；去掉单参构造 | AC2/AC3 |
| `core/module/esm/NekoEsmLinker.java`（重构） | 注入 preparation | AC3/AC10 |
| `core/module/esm/NekoNativeEsmSourceRewriter.java`（重构） | 注入 preparation | AC3/AC10 |
| `core/module/ModuleReloadCoordinator.java`（重构） | 注入 preparation 取代 static invalidate | AC4/AC5 |
| `script/ScriptManager.java`（重构） | 注入 preparation；按 ScriptType 清理走实例 | AC5 |
| `core/node/NekoNodeModuleInstaller.java`（code-review 修订） | 删除自建 pipeline 的测试专用重载；默认入口也**必须**注入 preparation | AC2/AC9 |

**code-review 修订（Standards 轴）**：
- 删除三个零调用者的 public 成员：`NekoContentHash.shortSha256`、
  `NekoModuleCacheKey.identityMatches`、`NekoRuntimeRoot.modulePreparation()`
  （最后一个尤其有害：把 root 私有 cache 外泄，与「root 显式持有」自相矛盾）。
- 4 个新测试中的内联 `new NekoModulePreparation(new NekoModulePipeline(new NekoCompilationPipeline(), ...))`
  全部改用 `TestModulePreparation.create(...)`，消除第二条测试装配路径。
- `NekoNodeModuleInstaller.install(Context, ScriptType)` 原内联自建
  `NekoCompilationPipeline + ScriptCompilerRegistry.current() + defaultConfig()`——正是本票要消除的
  「第二语义 pipeline」形态。已改为 `install(Context, ScriptType, NekoModulePreparation)`，
  唯一调用者 `NodeModulesJsRegressionTest` 一并改注入。全仓**不再有任何**在测试/默认入口中
  自建模块准备装配的路径。

**所有权结论**：模块 cache/session 的唯一 owner 是 `NekoRuntimeRoot`（构造注入 + `closeSilently` 释放）。独立构造的 root / 独立构造的 `NekoModulePreparation` 之间零共享——这有独立实例测试证明（`independentPreparationsDoNotShareCache`）。

---

## 2. 逐 AC 判定

### AC1 — JS/CJS/ESM 输入产生正确 language id、module mode、可执行 code/IR、可用 source map 和稳定 cache key

**pass**。证据：`NekoModulePreparationIdentityTest`（`common/src/test/java/com/tkisor/nekojs/core/module/`）
- `cjsInputProducesCommonJsIdentity`：`.cjs` → languageId=`javascript`、requested=COMMONJS、解析 COMMONJS、code 保留 CJS、cjsRecord 非空、sourcePath 指向原文件。
- `esmInputProducesEsmIdentityAndAst`：`.mjs` → ESM、esmAst 非空且识别出 export。
- `jsExtensionUsesAutoRequestedMode`：`.js` → requested=AUTO、解析 COMMONJS。
- `cacheKeyCapturesLanguageModePathAndContent`：四维任一变化 → 不同 key；相同四元组 → 相等 key（稳定性）。
- `sourceMapNullMeansIdentityForNativeJs`（**code-review 修订补**）：原生 JS（`.js`/`.cjs`/`.mjs`）的
  `sourceMap()` 为 `null`，语义是**恒等映射**——`SourceMapRegistry.parse` 对 null/空 map 落
  `NormalizedSourceMap.empty`，查表返回 `(jsLine, jsColumn, path=null)`：行列原样透传、错误路径回退到
  displayPath。测试同时断言该恒等行为（行/列原样、path 为 null），因此「可用 source map」对原生 JS
  有明确定义，不是被默默忽略的缺口。转译语言走另一条路：必须产出**非空** map（由 AC7 的 `.ts` 映射测试覆盖）。

### AC2 — prepared module 不可变，Resolution/Cache 修改源码、身份或诊断上下文时测试变红

**pass**。证据：`NekoModulePreparationIdentityTest`（3 个测试）
- `preparedModuleIsImmutableValue`：record 组件非空；「修改」只能经重新构造产生新实例，原实例 code 不被改动。
- `esmAstCollectionsAreNotBackedByMutableState`：`prepared.esmAst().statements().clear()` 抛 `UnsupportedOperationException`（`List.copyOf` 不可变视图）。
- `preparedModuleObservableStateCannotBeMutatedInPlace`（**AC2 强化**）：
  - 未变化时重复 prepare 返回**同一实例**——不存在「中途被改过但仍被返回」的窗口；
  - 两个独立 cache 消费者对同一输入看到完全相同的可观察元组（languageId / mode / requestedMode / code / cacheKey / origin）；
  - 改过源码的 record 与原实例 `equals == false`，且原实例的可观察元组不变，cache 条目不被污染。
- **会变红的守卫**：若将来给 `NekoPreparedModule` 加上 setter，或把某个组件换成裸可变容器（如直接暴露 `ArrayList`），上述测试即失败。
- **限制（如实记录）**：`NekoPreparedModule` 是 record，从类型上就没有 setter，因此「Resolution/Cache 试图就地改写」在编译期已不可能；测试钉的是可观察元组的稳定性与组件不可变性，而非运行期的篡改检测 API。若维护者要求显式的篡改检测（如 sealed 层级 + 持有者守卫），需另立工作项。

**code-review 修订**：`NekoRuntimeRoot.modulePreparation()` 曾把 root 私有 cache 外泄（自称「诊断/测试
观察用」却与「root 显式持有」矛盾），已**删除**；测试改用 `TestModulePreparation` 夹具。

### AC3 — CJS require/module.exports 与 ESM import/export/link 的模块身份在重复加载、循环依赖和跨入口调用下保持既有语义

**pass**。证据：`NekoModuleResolutionCorpusTest`（从 `NekoScriptModuleLoaderHost.loadEntry` 最高调用者 Seam 观察）
- `cjsModuleIdentityIsStableAcrossRepeatedLoads`：模块体全局计数证明重复加载只执行一次（身份稳定）。
- `circularCjsRequireReturnsPartialExports`：A↔B 互引得部分 exports（不 StackOverflow）。
- `esmImportLinksAndExecutes`：跨模块 import 看到 helper 导出。
- `circularEsmDependenciesLinkAndEvaluate`：循环 ESM link 终止；link 后互调可用。

### AC4 — 内容、路径、mode 或 language identity 变化会失效对应 cache；同 stamp 同长度但内容不同的覆盖写入不会返回旧模块

**pass**。证据：
- `NekoModulePreparationIdentityTest`：`contentChangeInvalidatesCacheEntry`、`pathChangeInvalidatesCacheEntry`、`modeChangeInvalidatesCacheEntry`、`languageIdentityChangeInvalidatesCacheEntry`（换语言插件 → 新语言编译产物）。
- `NekoModulePipelineCacheStampTest.prepareRecompilesWhenOnlyContentChanges`：等长覆盖 + 显式恢复 mtime → 必须重新编译（SHA-256 内容指纹）。
- `NekoModuleResolutionCorpusTest.contentChangeProducesNewPreparedModule`：cache key 变化。
- **新增能力**：命中判定从「只比 stamp」升级为「stamp ∧ language identity」。修复前，插件用 `replaceLanguage` 换掉某扩展名的语言后，同内容同路径的旧语言模块会被继续返回。

### AC5 — 按 ScriptType 清理只影响目标范围，共享 node_modules/跨类型缓存行为有显式断言

**pass**。证据：
- `NekoModuleResolutionCorpusTest.typeScopedClearLeavesCrossTypeAndOtherTypeEntries`：prepared 条目 SERVER 清除 / CLIENT 保留 / node_modules 共享保留。
- `ScriptTypeScopedCacheClearTest.pipelineCacheClearByTypeOnlyRemovesThatType` / `pipelineCacheNoArgClearWipesEverything`：白盒测试已改为实例级断言（不再反射 static `PREPARED_CACHE`）。
- `NekoModuleResolutionCorpusTest` 与上述测试共用同一 `scriptTypeOf` 路径推导实现。
- **边界（总账 A9 归属，非本票缺口）**：`ModuleReloadCoordinator.invalidateModules` 仍调用
  static `NekoEsmVirtualModuleRegistry.invalidate(...)`。runtime-ledger **A9** 明确把该 registry
  归为 **generation-owned** 且标注「**06/07 处理，本票不动**」。本票只把 **prepared module cache**
  的所有权收口到 runtime owner（A8），**未动** A9 的虚拟 ESM registry —— 这是按总账分类执行的
  有意边界，不是遗漏。见 §3.5。

### AC6 — 准备失败、resolve/link 失败、缓存失败和执行失败可区分 owner 与阶段，错误不延迟成无来源的 Graal 异常

**pass**。证据：
- **准备失败**：`NekoModulePreparationIdentityTest.unknownExtensionFailsAtPreparationWithOwner`（未注册扩展名 → 准备期 `IllegalArgumentException`，含 "No script compiler registered"）；`missingSourceFileFailsAtPreparationAsIoError`（缺失文件 → `IOException`，归准备/IO 阶段）。
- **resolve 失败**：既有 prior art `NekoModuleResolverTest`（`resolveForRequireRejectsUnknownBareSpecifier`：bare 未命中抛 MODULE_NOT_FOUND；`rejectsBareModuleTraversalOutsideNodeModules`：路径穿越拒绝）。
- **link 失败**：`NekoModuleResolutionCorpusTest.unresolvedEsmImportFailsAtLinkWithSourceLocation`（报出缺失 specifier）、`missingEsmExportFailsAtLinkWithDiagnostic`（"absent"）、`duplicateEsmExportFailsAtLinkWithOriginalLocation`（`NekoEsmLinkException` + 原文件 span/行）。
- **解析/语法失败**：`cjsSyntaxErrorCarriesStageLocation`（`NekoEsmLinkException` 指向用户文件 2:7）。
- **缓存失败**：`NekoModuleCacheFailureTest`（**新增**）——
  `checkedPipelineFailureSurfacesAsItsOriginalType`（受检异常按 `IOException` 原类型浮出，不被降级为无来源的 `RuntimeException`）、
  `uncheckedPipelineFailureSurfacesUnwrapped`（非受检异常原样浮出，不被包装）、
  `failedPreparationDoesNotPoisonCache`（失败的 prepare 不留条目；修复后重试成功）、
  `missingFileFailsBeforePipelineAndIsNotCached`（与准备/IO 阶段分工）。
- **执行失败**：见 AC7（`ScriptError` 带 source location / path / line / column，非无来源 Graal 异常）。

### AC7 — 跨 import 的执行错误能经 source map 回到原始文件、行列和模块身份

**pass**。证据：`NekoModuleErrorMappingCorpusTest`（新）
- `executionErrorInImportedEsmMapsBackToImportedFile`：入口 import 的 helper.mjs 内 `throw` → `ScriptError.getDisplayPath()` = `server_scripts/error-mapping/helper.mjs`、line=2（**模块身份**正确指向被导入模块）。
- `executionErrorInCompiledLanguageMapsBackThroughSourceMap`：`.ts` helper 经编译后执行 `throw` → 映射回原始 `.ts` 的 line=3（**source map 生效**，不是编译产物行号）。
- `executionErrorInEntryMapsBackToEntryFile`：入口自身错误定位到入口文件。

观测路径与生产一致：Graal `PolyglotException` → `ScriptError.create`（`DefaultErrorTracker` 的错误解析入口，内部查 `SourceMapRegistry`）→ 用户可见 path/line/column。

**code-review 修订补强（AC7 字面三项：文件、行列、模块身份）**：
- 三个测试现在都断言**列号**：ESM helper line=2/col=9、入口 line=2/col=7（`throw new Error` 的 `new` 起始列）；
  `.ts` helper line=3 且列 > 0（TS 类型擦除的 source map 是**语句粒度**，列映射到语句起始列，
  因此断言「携带来自 source map 的原始列」而非编造不存在的精确列——粒度限制如实记录在此）。
- **模块身份**断言：错误归属的模块经 `NekoModulePreparation.prepare` 得到的 `sourcePath` / `languageId` /
  `mode` / `cacheKey` 必须可解释且与错误路径指向同一模块（`.mjs` → javascript/ESM，`.ts` → typescript/ESM）。

### AC8 — 本地 trusted 与远端显式授权/拒绝用同一阶段模型观测，拒绝或降级不会隐藏语言边界

**pass（阶段同一性 + 授权结果两条证据）**。证据：`NekoModuleTrustPhaseParityTest`（新）
- `localAndRemoteAuthorizedSourceShareTheSamePreparationStage`：本地脚本与 `nekojs/server_packs/<bucket>/...` 下的远端授权源经同一 `NekoModulePreparation.prepare` 得到相同 language id / mode / 内容指纹（路径不同 → key 不同，但语言与模块边界一致）。
- `remoteSourceRejectedByTrustStillPreparesIdenticallyWhenInvoked`：拒绝不改变远端源的语言身份与 module mode，也不跳过静态分析。
- `signatureRejectionIsReportedAsAuthorizationResultNotLanguageFailure`：真实 `PackSignatureVerifier`（Ed25519）+ 真实 `PackSyncTrustStore` → 无效签名 `valid=false` 且 `reason` 可解释（授权结果，非语言失败）。
- `unsignedPackIsRejectedByDefaultAndAcceptedOnlyWhenExplicitlyAllowed`：默认拒绝 / 显式 `allowUnsigned` 放行，且 `signed=false` 始终可见。
- `explicitlyAuthorizedUnsignedPackDoesNotHideItsUnverifiedNature`：降级放行不伪装成已验签。
- **结构证据**：`PackSyncClient.handleBundle` 在授权阶段之后调用 `ScriptPackRegistry.activateServerCachePacks` → `ScriptLocator.discover` → 与本地脚本相同的 `ScriptContainer` 装载路径。远端包**没有**独立的第二语言/模块管线。

### AC9 — legacy CJS bridge 的 characterization、当前保留原因和收缩 gate 可追踪；只有替代 behavior、declaration、trace 通过且无调用者后才移除

**pass（characterization + gate 已写明；见 §3）**。本票**未删除**任何 language 级 legacy bridge——按票据「在没有替代 behavior + declaration + trace 通过且无调用者之前不要删除」。

### AC10 — Preparation 与 Resolution/Cache 不创建 Graal Context、不决定 HostAccess、不读取 Minecraft/loader

**pass（口径：白名单，不是整包）**。证据：`NekoModulePreparationBoundaryTest`（新）
- `preparationAndResolutionSourcesCreateNoContextOrHostAccess`：对 **19 个显式列出的** Preparation/Resolution/Cache 源文件做令牌级扫描（跳过 javadoc/注释行），断言零 `Context.newBuilder` 与零 `HostAccess`。
- `preparationAndResolutionSourcesReadNoMinecraftOrLoader`：同集合零 `net.minecraft` / `net.neoforged` / `net.fabricmc`。
- `preparationRunsWithoutAnyGraalContext`：**行为证据**——不构造任何 Graal Context，纯 Java 跑通 `.cjs`/`.mjs`/`.js` 的 prepare 与缓存命中/失效。

**code-review 修订：证据边界必须按白名单读（Spec 轴 c2，已复核）**

审计结论：AC10 字面（**不创建** Graal Context / 不决定 HostAccess / 不读取 MC-loader）在 **19 文件白名单内成立**，
**在 `core/module` 包级不成立**——同包内：
- `NekoScriptModuleLoaderHost` 持有 `Context`（加载/求值 = Execution 职责）；
- `EsmModuleLifecycle` 持有 `Context` 并 `context.eval(...)`；
- `NekoEsmModuleRecord` / `NekoEsmModuleRecordCache` 持有 static `Value`。

这些**都是** Script Execution Environment 的成员（由 `NekoSandboxFactory` 在 Context 创建后构造并注入），
**没有一个**调用 `Context.newBuilder`。全仓唯一的 Context 创建点是 `NekoSandboxFactory:126`（Execution Environment）。
因此：
- **AC10 字面成立**（包内零 `Context.newBuilder`、零 `HostAccess` 决策、零 MC-loader import）；
- 但 **REPORT 不表述为「整包零 Context」**——白名单口径如上；
- `NekoScriptModuleLoaderHost` 之所以**不**被移出，是因为它是票 06 已验收的 generation/commit 路径执行入口；
  强行剥离会与既有决策冲突（见 §4）。
- 本约束**未**改变 common 允许 GraalJS 的既有规则（`guardLint` 的 L2 边界规则不变：只禁 MC/loader import）。

### AC11 — 随实现交付 JS、CJS、ESM 的最小可运行示例与必要迁移材料；示例只使用已通过 gate 的模块能力，缓存/reload 行为与示例说明一致

**pass**。交付物与证据：
- 示例（`docs/architecture-refactor/baseline/2026-09-12-language-pipeline/`）：
  `minimal-example.js`（AUTO→COMMONJS）、`minimal-example.cjs`（强制 COMMONJS）、
  `minimal-example.mjs`（强制 ESM）、`minimal-example-entry.mjs`（ESM 跨模块 import 入口）。
- 迁移材料：`MIGRATION.md`（同目录）。
- **示例被执行验证**：`NekoModuleMinimalExampleTest` 直接把上述示例文件当真实脚本源执行——
  `examplesDeclareTheModuleModeTheyDocument`（模式与说明一致）、`esmEntryExampleImportsAndExecutes`、
  `cjsExampleExportsAreUsable`、`plainJsExampleLoads`、`exampleCacheBehaviourMatchesItsDocumentation`
  （未变化命中、编辑后失效重编译）。示例若与实现漂移，本测试失败。

---

## 3. legacy CJS bridge characterization 与收缩 gate（AC9）

### 3.1 「legacy CJS bridge」在本票语境下指什么

规格 06 把 legacy CJS bridge 归入 Module Resolution/Cache，但**未冻结符号名**。源码审计后，本票把当前存在的 legacy/bridge 面拆成三类，逐一 characterization：

| # | 面 | 位置 | 现状 | 是否本票删除 |
|---|---|---|---|---|
| B1 | **static 模块管线实例绑定**（`LEGACY_INSTANCE`/`legacyPrepare` 门面） | 原 `NekoModulePipeline.java:30-49,137-150` | 迁移期兼容：静态读点让 cache 在注入完成前仍能工作 | **已删除**（本票，§1.1） |
| B2 | **`IScriptCompiler` 语言桥**（`NekoLegacyLanguagePlugin` 把单方法编译器适配成 `NekoLanguagePlugin`） | `core/compiler/NekoLegacyLanguagePlugin.java`；`NekoModulePipeline.languagePlugin` 的两个构造点 | **活跃生产路径**：内置 Python 插件经 `registry.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler())` 注册（`PythonTranspilerPlugin`），必须经此桥才能进统一 IR | **未删除**（见 §3.3） |
| B3 | **ESM↔CJS 互操作合成模块**（`syntheticCjsModule`/`syntheticCjsModuleUri`：从 ESM import 一个 CJS 模块时合成 `export default __neko_exports` 虚拟模块） | `core/module/esm/NekoNativeEsmSourceRewriter.java:163-173,235-240`；调用点 `NekoScriptModuleLoaderHost.java:236` | **活跃生产路径**：`import x from './foo.cjs'` 依赖它 | **未删除**（公开语言能力，规格 06 禁止删除 CJS） |

### 3.2 「是否存在第二语义管线」的结论

**结论：不存在。**

- **CJS 只有一条路径**：`NekoModulePipeline.prepareModule` → （原生 JS 直接过 / 转译语言经 `IScriptCompiler`）→ `CjsStaticAnalyzer.analyze` 产出 `CjsModuleRecord` → `NekoScriptModuleLoaderHost.loadScriptResolved` → `internal/script-loader.js` 的 `new Function` 执行。静态分析结果 `prepared.cjsRecord()` 当前**没有生产读者**（仅 record 携带 + 测试断言），它不构成第二条语义管线，只是准备阶段的附加事实。
- **ESM 只有一条路径**：`NekoModulePipeline.prepareModule`（`NekoEsmParser` 产出 `NekoEsmModuleAst`）→ `NekoEsmLinker.link`（link/export shape 校验）→ `NekoNativeEsmSourceRewriter.registerModule`（specifier 重写 + 虚拟模块注册）→ `EsmModuleLifecycle` 求值。
- 二者共享**同一份** `NekoPreparedModule` 与**同一个** `NekoModulePreparation` cache 实例（本票收口的关键）；不存在「CJS 走一套 cache、ESM 走另一套」的分裂。

### 3.3 当前保留原因与删除条件（收缩 gate）

**B2（`IScriptCompiler` 语言桥）**

- 保留原因：Python 是公开语言能力（规格 06 明确禁止删除），其唯一入口就是 `IScriptCompiler`。删除该接口等于删除 Python 注册面。
- 替代 behavior 要求：所有内置与插件语言都必须改为实现 `NekoLanguagePlugin`（lexer/parser/lowering）并注册 `NekoLanguagePlugin`。
- 替代 declaration 要求：Probe/声明派生必须对「无 `NekoLanguagePlugin` 的语言」不再有分支。
- 替代 trace 要求：`NodeModuleTypeDocs` / workspace 生成对 `legacy:<ext>` language id 无引用。
- 无调用者证据：`registry.registerLanguage(id, ext, IScriptCompiler)` 与 `register(String, IScriptCompiler)` 零调用者；`NekoLegacyLanguagePlugin` 零引用；`PythonTranspilerPlugin` 改为 plugin 形态。
- **当前状态：不满足**（Python 仍用它）。因此**保留**，不在本票删除，也不在 final release 统一清理（票据明确要求随票判定）。

**B3（ESM↔CJS 互操作合成模块）**

- 保留原因：`import` 一个 CJS 文件是公开且被规格 06 保护的互操作语义。
- 替代 behavior 要求：存在另一种方式让 ESM 静态 `import` 拿到 CJS 的 `module.exports` 且保持命名导出形态（当前靠合成 `export const <name> = __neko_exports['<name>']`）。
- 替代 trace 要求：错误/诊断能定位到合成 URI 背后的真实源文件（现由 `NekoEsmVirtualModuleRegistry.displayPath` 提供）。
- 无调用者证据：`syntheticCjsModuleUri` / `syntheticCjsModule` 零调用者。
- **当前状态：不满足**（生产调用点在 `NekoScriptModuleLoaderHost.resolveNativeImport`）。因此**保留**。

### 3.4 本票已满足的删除条件（B1）

B1 的删除条件（ticket 05 总账 A7）是「W3 显式注入 pipeline/cache 后删 `bindLegacyInstance/legacyInstance/legacyPrepare`，字段删除归 W3」。本票：
1. 让 `NekoRuntimeRoot` 显式持有 `NekoModulePreparation` 并注入所有消费者（生产路径不再依赖 static 回退）；
2. 删除 `SHARED_COMPILATION_PIPELINE` / `LEGACY_INSTANCE` / `bindLegacyInstance` / `legacyInstance` / `legacyPrepare` / `legacyPrepareFallback`；
3. `git grep` 复核生产源码引用为 0；
4. 既有测试（白盒反射 static `PREPARED_CACHE` 的两个测试）同步改为实例断言，不再需要 static seam。

---

## 3.5 行为变化记录：AUTO 判定为 CJS 的 `.js` 现在携带 CjsModuleRecord（Spec 轴 b1）

**变化**：`NekoModulePipeline.prepareModule(NekoCompileOutput)` 的 `AUTO && !module` 分支
（`.js` 无 import/export、`enableEsmAuthoring` 开启时判定为 CJS）：

| | 修复前（旧 `NekoModulePipelineCache` 路径） | 本票之后 |
|---|---|---|
| 构造调用 | `NekoPreparedModule.commonJs(code, sourceMap)` | `NekoPreparedModule.of(COMMONJS, key, file, code, sourceMap, null, CjsStaticAnalyzer.analyze(code), 0)` |
| `cjsRecord` | `CjsModuleRecord.EMPTY` | 真实静态分析结果（字面量 require 依赖 + 导出形状） |
| 其余（code / sourceMap / mode） | 不变 | 不变 |

**判定：这个变化是正确的，且与另两条 CJS 分支更一致。** 理由：
1. 同一份「模块是 CJS」的结论，不应因为它是「扩展名强制 COMMONJS」还是「AUTO 判定为 CJS」而得到
   不同质量的事实。修复前 `.cjs` 有静态分析、`.js`（AUTO→CJS）没有——这是路径依赖的偶然差异。
2. `CjsStaticAnalyzer` 是**纯 token 级静态近似**，不产生副作用、不改变可执行 code，因此该变化
   **不改变任何脚本可观察行为**（`code`/`mode`/`sourceMap` 全部不变），只让 `prepared.cjsRecord()`
   的事实更完整。
3. `cjsRecord` 目前**没有生产读者**（只有 record 携带 + 测试断言），因此该变化的爆炸半径为零；
   未来若要把它用于依赖图/require.resolve 校验，AUTO 路径也必须有一致的数据。

**验证**：`NekoModulePreparationIdentityTest.jsExtensionUsesAutoRequestedMode` 断言 `.js` →
requested=AUTO、解析=COMMONJS；`cjsInputProducesCommonJsIdentity` 断言 `.cjs` 的 `cjsRecord` 非空。
新增的一次性行为对照（AUTO 路径的 cjsRecord 从 EMPTY 变为非 EMPTY）本报告如实记录，未写隐藏兼容层。

## 3.6 总账 A9 归属：虚拟 ESM registry 的 static 使用（Spec 轴 a4）

`ModuleReloadCoordinator.invalidateModules(:92)` 与 `NekoScriptModuleLoaderHost.clearSharedCachesForOwnType(:183)`
仍使用 static `NekoEsmVirtualModuleRegistry`。

- runtime-ledger **A9** 把该 registry（`SOURCES`/`DISPLAY_PATHS`/`DISPLAY_PATHS_BY_FILE_NAME`/
  `KEY_BY_FILE_NAME`/`GENERATIONS`/`TYPES`）归为 **generation-owned**，可替换性栏原文：
  「**06/07 处理**（candidate/active 隔离、切换 generation）；**本票不动**」。
- 本票范围是 **A8**（prepared module cache）的所有权与失效维度——已完成。
  A9 的 generation 维度归票 06/07，**本票有意不动**。
- **结论：这不是本票的验收缺口**，是按总账分类执行的边界。记录于此，避免后续 reviewer 重复报同一问题。

## 4. 一致性原则下的取舍（票据硬性约束 3）

| 潜在冲突 | 本票取舍 | 理由 |
|---|---|---|
| `NekoScriptModuleLoaderHost` 持有 `Context`，AC10 要求 Resolution/Cache 不创建 Context | **不把 host 移出**，而是明确它属于 Script Execution Environment：host 由 `NekoSandboxFactory`/Node installer 在 **Context 已存在之后**构造，Context 不由 host 创建。AC10 的结构证据集合因此排除了 host，并在 AC10 判定中显式说明这一边界。 | 规格 06 定义 Execution 负责 Context 与执行关闭；host 的 load/evaluate 动作正是 Execution。强行剥离会与票 06 已验收的 generation/commit 语义冲突。 |
| ticket 05 总账把 `PREPARED_CACHE`（A8）标为 domain adapter-owned「保留」 | 本票**没有**删除 A8 的「缓存存在性」，只改变了它的**所有权**（process-wide static → runtime owner 实例）与失效维度（+language identity）。总账 A8 的可替换性栏原文即「保留（W3 处理失效语义与显式注入）」——本票正是 W3 行。 | 与总账分类一致，无冲突。 |
| `NekoModuleResolver` 有 `new NekoModuleResolver()`（读 `NekoJSPaths.get()` + `ScriptFilePolicy.legacyRuntime()`）的无参构造 | **保留**。它是 process-owned 路径单例 seam（总账 C3/A10），不属于本票的 static 模块缓存。 | 票据要求移除的是「process-wide static cache 与 legacy instance」，不是全部 static 工厂。 |

---

## 5. 验证证据

### 5.1 命令与结果

| 命令 | 结果 |
|---|---|
| `:common:compileJava --rerun-tasks --no-build-cache` | pass |
| `:common:compileTestJava --rerun-tasks --no-build-cache` | pass |
| `:common:test --rerun-tasks --no-build-cache`（全量，非子集） | pass（198 suites / 1470 tests / 0 failures / 5 skipped） |
| `:common:check` | pass |
| `guardLint` | pass（248 守卫块 / 331 文件 / 0 问题） |

### 5.1.1 code-review 修订期间发现并修复的测试隔离缺陷（重要）

**首次**以 `--rerun-tasks --no-build-cache` 全量跑 `:common:test` 时暴露 **25 个失败**，
全部是 `java.nio.file.DirectoryNotEmptyException`（`ScriptReloadGenerationTest:201`、
`ScriptReloadRegressionTest:196` 等）。根因**在本票新增测试**，不在被测实现：

- 这些既有测试的 `@BeforeEach` 用 `Files.deleteIfExists(path)` 清空脚本目录，**只能删文件、不能删目录**；
- 本报告新增的 4 个 corpus 测试（`NekoModuleResolutionCorpusTest` / `NekoModuleErrorMappingCorpusTest` /
  `NekoModuleMinimalExampleTest` 等）用 `file.toFile().deleteOnExit()` 清理——`deleteOnExit` 注册的是
  **JVM 退出时**的回调，测试之间**不会执行**；
- 于是残留的**子目录**（`resolution-corpus/`、`error-mapping/` 等）让后续既有测试的目录清理抛
  `DirectoryNotEmptyException`；因为全部测试共用**固定** gameDir（`nekojs-test-gamedir`），
  这是跨测试污染。

**修复**：新增测试改为记录写入路径并在 `@AfterEach` 真正删除（与既有测试同一纪律），
并在修订后清理了上一轮失败留下的陈旧目录。

**为什么之前是绿的**：先前的子集`--tests ...` 跑法在 Gradle 增量/缓存语义下没有触发该顺序；
`--rerun-tasks --no-build-cache` 全量跑才暴露。这正是主 agent 要求真跑一次的价值所在。
教训已记录：**测试内清理用 `@AfterEach` 真删，不要用 `deleteOnExit`**。

### 5.2 收尾门禁

见 Closure record（`11-language-pipeline.md`）中的最终门禁结果；`:common:check` 与 `guardLint` 的实际输出以该处记录为准。

---

## 6. 遗留与交接

1. **AC2 的显式篡改检测 API**：本票以 record 类型安全 + 组件不可变 + 「未变化返回同一实例」证明；未实现运行期篡改检测（如 sealed 持有者守卫）。若需强制，另立工作项。
   **code-review 修订**：已删除 `NekoRuntimeRoot.modulePreparation()`（曾把 root 私有 cache 外泄给
   「诊断/测试观察」，与 root 独占所有权矛盾）；测试改用 `TestModulePreparation` 夹具。
2. **AC8 的真实运行时端到端**：本票在 common 层证明了阶段同一性与授权结果正交（真实 `PackSignatureVerifier` + `PackSyncTrustStore`）；**未**运行真实 MC 客户端的远端包下发 smoke（需要 minecraft-mcp + 真实服务器）。owner：维护者 / 票 19（pack trust）与 34（P4 五节点验证）。
3. **B2/B3 的删除**：条件未满足，保留（见 §3.3）。收敛路径需要替代 behavior + declaration + trace + 无调用者四项证据齐全；不由本票在 final release 统一清理。
   **code-review 修订**：已把「自建第二准备装配」这一类形态一并清除（`NekoNodeModuleInstaller` 的
   测试重载），避免它成为 B2/B3 之外的第三条并行管线种子。
4. **真实 Graal 运行环境**：本报告所有执行类证据在 JUnit + 真实 GraalJS Context 下取得；**未**在真实 Minecraft 客户端/服务端 smoke 中复跑（票 11 的 AC 不要求节点 runtime smoke，故不构成验收缺口，但作为运行时证据的边界如实记录）。owner（如需）：维护者 / minecraft-mcp。
5. **candidate/active generation 交互**：本票把模块 cache 所有权交给 `NekoRuntimeRoot`，未改动 `ScriptManager` 既有的候选/commit 语义（票 06 已验收）。模块 cache 在 `commitGeneration`/`fullReloadCleanup` 中按 ScriptType 清理，与候选隔离语义一致——但本票**未**新增跨候选 generation 的模块 cache 隔离测试。owner：票 07 / 后续。
6. **虚拟 ESM registry 的 generation 维度**：总账 A9 明确归票 06/07，本票不动（见 §3.6）。
