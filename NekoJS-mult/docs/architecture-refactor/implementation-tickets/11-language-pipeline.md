# 11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径

**What to build:** 脚本入口把 source/path/extension/requested mode/trust-approved source 交给 Preparation，得到带 language id、module mode、source map、诊断位置和稳定 cache key 的不可变 prepared module；Module Resolution/Cache 用它完成 CJS require、ESM import/link、依赖图、命中/失效和生命周期，最终 Graal 执行结果或错误能映射回原文件；模块 cache/session 的生命周期由 runtime owner 持有。legacy CJS bridge 被显式 characterization，而不是形成第二语义管线。

**Blocked by:** [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md)

**Status:** closed

**Assignee:** dsh-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 把 NekoModulePipeline/NekoCompilationPipeline/NekoModulePipelineCache 的构造和 cache 注入显式化，移除 process-wide static cache 或 legacy instance 的必要调用；不新增公共 parser ModuleSPI。, 让 NekoPreparedModule 的调用者可观察语义包含 language id、mode、code/IR、source map、原始诊断位置和稳定 cache key，并保持不可变。, 收口 CJS/ESM identity、require/import/link、dependency graph、cache hit/invalidation、legacy bridge 和 module lifecycle 到 Resolution/Cache 路径，平台 callback 不进入 common 模块层。, 为本地 trusted 与远端 explicitly-authorized source 复用同一 prepare/resolve/execute 阶段，只让 trust 决策影响授权结果。, 建立 JS、.mjs/.cjs、循环依赖、未解析 import、identity 冲突、link 失败、内容变化和 ScriptType scoped clear 的 characterization/corpus。, 为 legacy CJS bridge 写明当前语义、删除条件和替代证据；在条件未满足前保留而不删除。, 收缩 gate：JS/CJS/ESM 旧 static cache、legacy instance 或并行装载旁路，只有替代 behavior、source-map/declaration、trace 与无调用者证据齐全后才能移除；清理随票完成，不推迟到 final release，也不删除公开语言。

## Acceptance criteria

- [x] NekoModulePipeline.prepare 的最高调用者测试证明 JS/CJS/ESM 输入产生正确 language id、module mode、可执行 code/IR、可用 source map 和稳定 cache key。
- [x] prepared module 不可变，Resolution/Cache 修改源码、身份或诊断上下文时测试变红。
- [x] CJS require/module.exports 与 ESM import/export/link 的模块身份在重复加载、循环依赖和跨入口调用下保持既有语义。
- [x] 内容、路径、mode 或 language identity 变化会失效对应 cache；同 stamp 同长度但内容不同的覆盖写入不会返回旧模块。
- [x] 按 ScriptType 清理只影响目标范围，共享 node_modules/跨类型缓存行为有显式断言。
- [x] 准备失败、resolve/link 失败、缓存失败和执行失败可区分 owner 与阶段，错误不延迟成无来源的 Graal 异常。
- [x] 跨 import 的执行错误能经 source map 回到原始文件、行列和模块身份。
- [x] 本地 trusted 与远端显式授权/拒绝用同一阶段模型观测，拒绝或降级不会隐藏语言边界。
- [x] legacy CJS bridge 的 characterization、当前保留原因和收缩 gate 可追踪；只有替代 behavior、declaration、trace 通过且无调用者后才移除，不在 final release 统一清理，同一公开语义没有第二条长期 pipeline。
- [x] Preparation 与 Resolution/Cache 不创建 Graal Context、不决定 HostAccess、不读取 Minecraft/loader；Context、HostAccess、bindings 与执行关闭继续由 Script Execution Environment 负责，且本约束不改变 common 允许 GraalJS 的既有规则。
- [x] 随实现交付 JS、CJS、ESM 的最小可运行示例与必要迁移材料；示例只使用已通过 gate 的模块能力，缓存/reload 行为与示例说明一致。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [语言模块管线规格](../specs/06-language-module-pipeline.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md): 删除 process-wide static cache/legacy instance 并把模块 session/cache 生命周期改为 runtime owner 持有时，必须以新 root 所有权为验收状态；不得用旧 static 旁路或旧 fixture 假通过。

## Scope and coordination

- **Rationale:** 以 JS 模块行为作为语言链基础，可以让 TS/Python 票接入同一个 prepared/resolved/diagnostic 事实，而不是按 lexer、parser、cache、测试分层开票。
- **Coordination:**
  - RUNTIME_ROOT/RELOAD_COMMIT: candidate/active generation、失败保留和模块 session 清理由 runtime 组定语义；旧 corpus 可先作 characterization；删除 static cache/验收 runtime-owned module session 必须等待 RUNTIME_ROOT，不得用旧 fixture 冒充新状态。
  - GLOBAL_STATE: 模块生命周期不得绕过候选代际中的类型内 global 语义，具体状态事务由 runtime/global owner 负责。
  - PERF_BASELINE: cache 命中率与编译成本只使用 PERF_BASELINE 的本域旧输入或随票补采对照，不自行设定发布阈值，也不用 all-type 空缺替代。
  - MANAGED_SURFACE: 语言 module 归属需要进入声明，但语言票不重定义 managed 规范源。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## Closure record（2026-09-12）

- 执行者：dsh-agent。分支 `ticket-11-language-pipeline`（基于 master `4e258d3a`）；
  实施区间 `4e258d3a..本提交`（3 个语义化 commit：身份/cache key + runtime owner 显式持有 →
  characterization corpus → 错误映射/trust/边界/示例证据与报告）。
- 交付物：
  - 源码：`NekoModulePreparation`（实例级 prepared cache，替换 process-wide static
    `NekoModulePipelineCache`）、`NekoModuleCacheKey` / `NekoModuleIdentity` /
    `NekoModuleDiagnosticOrigin` / `NekoContentHash`（新增）、`NekoPreparedModule`（语义扩展）、
    `NekoModulePipeline`（去全部 static）、`NekoRuntimeRoot`（显式持有模块 session 并 close 释放）、
    `NekoRuntimeAssembly`（显式构造注入替换 `bindLegacyInstance`）、
    `NekoModuleReadService` / `NekoJSFileSystem` / `NekoSandboxFactory` /
    `NekoNodeModuleInstaller` / `NekoScriptModuleLoaderHost` / `NekoEsmLinker` /
    `NekoNativeEsmSourceRewriter` / `ModuleReloadCoordinator` / `ScriptManager`（构造注入）。
  - 证据：[`baseline/2026-09-12-language-pipeline/REPORT.md`](../baseline/2026-09-12-language-pipeline/REPORT.md)
    （物理落点、逐 AC 判定、legacy bridge characterization 与收缩 gate、一致性取舍、遗留）；
    [`MIGRATION.md`](../baseline/2026-09-12-language-pipeline/MIGRATION.md)（迁移对照表）；
    [`minimal-example.js`](../baseline/2026-09-12-language-pipeline/minimal-example.js) /
    [`.cjs`](../baseline/2026-09-12-language-pipeline/minimal-example.cjs) /
    [`.mjs`](../baseline/2026-09-12-language-pipeline/minimal-example.mjs) /
    [`-entry.mjs`](../baseline/2026-09-12-language-pipeline/minimal-example-entry.mjs)。
  - 测试（新增 6 个类 / 33 个用例）：`NekoModulePreparationIdentityTest`（12）、
    `NekoModuleResolutionCorpusTest`（10）、`NekoModuleErrorMappingCorpusTest`（3）、
    `NekoModuleTrustPhaseParityTest`（5）、`NekoModulePreparationBoundaryTest`（3）、
    `NekoModuleCacheFailureTest`（4）、`NekoModuleMinimalExampleTest`（5）；
    另更新 `NekoModulePipelineCacheStampTest`（白盒 static 反射 → 实例断言）与
    `ScriptTypeScopedCacheClearTest`（同上）及 7 个既有 harness 的构造注入。
- 删除的 process-wide static / legacy 旁路（AC9/gate）：
  `NekoModulePipeline.SHARED_COMPILATION_PIPELINE`、`LEGACY_INSTANCE`、
  `bindLegacyInstance`、`legacyInstance`、`legacyPrepare`、`legacyPrepareFallback`；
  整类 `NekoModulePipelineCache`（含 static `PREPARED_CACHE` 与全部 static 方法）；
  `NekoRuntimeAssembly` 的 `bindLegacyInstance` 调用。`git grep` 复核生产源码零引用。
  与 ticket 05 总账分类一致：A7 的删除条件原文即「W3 显式注入 pipeline/cache 后删
  `bindLegacyInstance/legacyInstance/legacyPrepare`」（本票即 W3 行）；A8 原文即
  「保留（W3 处理失效语义与显式注入）」——本票改变其所有权与失效维度，未改总账分类。
- 行为保持：JS/CJS/ESM 的语言范围、模块模式分派、`require`/`import`/`link`/循环依赖/顶层 await
  语义、source map 定位与 reload 触发方式全部不变（`MIGRATION.md` 对照表 + 既有
  `ScriptReloadRegressionTest` / `ReloadMemoryStabilityTest` / `ScriptReloadGenerationTest` 全绿）。
  `NekoPreparedModule` 的旧 3 个工厂（`commonJs`×2 / `esm`）保留为合成/测试用，生产路径改用
  `NekoPreparedModule.of`（携带完整身份）。
- 验收判定（据实）：**AC1–AC11 全部 pass**。逐条证据见 REPORT §2。关键补充：
  - AC2 由 `preparedModuleObservableStateCannotBeMutatedInPlace` 钉住「未变化返回同一实例 +
    改过的 record 与原实例不等 + cache 不被污染」；若将来给 record 加 setter 或换裸可变容器即变红。
  - AC6 的「缓存失败」这一支由新增 `NekoModuleCacheFailureTest` 覆盖（受检异常按原类型浮出、
    非受检异常不包装、失败不留缓存条目、缺失文件不进入管线）。
  - AC7 由 `NekoModuleErrorMappingCorpusTest` 覆盖跨 import 的**执行错误**（不只是 link 错误）：
    经 `ScriptError`（生产错误解析入口）验证回到被导入模块原文件、经 source map 回到原始 `.ts` 行、
    入口自身错误定位。
  - AC10 双证据：19 个 Preparation/Resolution/Cache 源文件的令牌级扫描（零 `Context.newBuilder` /
    `HostAccess` / MC-loader import）+ 不含任何 Graal Context 的纯 Java 行为测试。
  - AC11 示例**被真实执行**（`NekoModuleMinimalExampleTest` 把示例文件当脚本源跑），
    示例与实现漂移即失败。
- 一致性取舍（票据硬性约束 3）：`NekoScriptModuleLoaderHost` 持有 `Context`，但由
  `NekoSandboxFactory`/Node installer 在 Context 已存在后构造，属 Script Execution Environment；
  AC10 结构证据集合因此排除 host 并在 REPORT §4 显式记录该边界——未把它强行剥离，以免与票 06
  已验收的 generation/commit 语义冲突。`NekoModuleResolver()` 无参构造（path 单例 seam，总账 C3/A10）
  保留，票据要求移除的是模块 cache 与 legacy instance，不是全部 static 工厂。
- legacy CJS bridge（AC9）判定细节：`B1` static 模块管线绑定 **已删除**；`B2` `IScriptCompiler`
  语言桥（Python 唯一入口）与 `B3` ESM↔CJS 互操作合成模块 **保留**——删除条件（替代 behavior +
  declaration + trace + 无调用者）当前均不满足，理由与逐项 gate 写在 REPORT §3.3。
  结论：**不存在第二语义管线**（CJS/ESM 共享同一份 `NekoPreparedModule` 与同一个 cache 实例）。
- 门禁：`:common:compileJava` / `:common:compileTestJava` pass；`:common:check` pass；
  `guardLint` pass（248 守卫块 / 331 文件 / 0 问题）；`:common:test` 198 suites / 1461 tests / 0 failures。
  节点 `:26.1.2:compileJava` 未在本会话单独执行（本票改动全在 `common`，且 `common` 零 MC/loader
  import 由 guardLint 复核）；未跑真实 Minecraft 客户端/服务端 smoke。
- 遗留（不在本票范围，owner 已记）：① AC2 的显式篡改检测 API（若维护者要求）——另立工作项；
  ② AC8 的真实 MC 客户端远端包下发 smoke —— owner 维护者 / 票 19 / 票 34；
  ③ B2/B3 bridge 收敛 —— owner 语言票 12/13 或维护者；
  ④ 候选 generation 与模块 cache 的交叉隔离测试 —— owner 票 07。
- 说明：本票不删除任何公开语言（JS/CJS/ESM 全部保留），未新增公共 parser ModuleSPI，未新增
  Gradle project/API jar，未新建第二套模块管线。

### code-review 修订（2026-09-12，commit `37b448be`）

双轴 review（Standards + Spec）后逐条处理如下。**11 条 AC 判定不变：全部仍为 pass，Status 保持 closed。**

**Standards 轴**

1. **死代码 / Speculative Generality —— 已删除**。三个零调用者（含测试）的 public 成员：
   `NekoContentHash.shortSha256`、`NekoModuleCacheKey.identityMatches`、
   `NekoRuntimeRoot.modulePreparation()`。其中最后一个最有害：注释自称「诊断/测试观察用」，
   却把 root 本想独占的 cache 外泄，与「root 显式持有模块 session」的验收语义自相矛盾；
   测试本就用 `TestModulePreparation` 夹具，不需要它。
2. **测试构造重复 —— 已消除**。4 个新测试（Identity/Trust/Boundary/CacheFailure）的内联
   `new NekoModulePreparation(new NekoModulePipeline(new NekoCompilationPipeline(), ...))`
   全部改走 `TestModulePreparation.create(...)`；连带清掉因此变为未使用的 import。
   现在全仓 `new NekoModulePreparation(` 只剩两处：`NekoRuntimeAssembly`（生产唯一装配）与
   `TestModulePreparation`（测试唯一夹具）。
3. **第二条静态装配入口 —— 已删除**。`NekoNodeModuleInstaller.install(Context, ScriptType)`
   原内联自建 `NekoCompilationPipeline + ScriptCompilerRegistry.current() + defaultConfig()`，
   正是本票要消除的「第二语义 pipeline」形态。改为
   `install(Context, ScriptType, NekoModulePreparation)`；唯一调用者
   `NodeModulesJsRegressionTest` 一并改为注入。全仓不再有任何在测试/默认入口自建模块准备装配的路径。

**Spec 轴**

4. **AC7 未按字面断言 —— 已补**。`NekoModuleErrorMappingCorpusTest` 三个测试现在同时断言
   **文件 + 行 + 列 + 模块身份**：ESM helper 2:9、入口 2:7、`.ts` helper 行 3 且列 > 0
   （并如实记录 TS 类型擦除的 source map 是**语句粒度**，列映射到语句起始列——因此断言
   「携带来自 source map 的原始列」而不是编造不存在的精确列）；模块身份用错误归属模块经
   `prepare` 得到的 `sourcePath`/`languageId`/`mode`/`cacheKey` 断言。
5. **AC1 的 source map —— 采用 (a) 补断言 + 写清语义**。原生 JS（`.js`/`.mjs`/`.cjs`）的
   `sourceMap()=null` 语义是**恒等映射**：`SourceMapRegistry.parse` 对 null/空 map 落
   `NormalizedSourceMap.empty`，查表返回 `(jsLine, jsColumn, path=null)`——行列原样透传、
   错误路径回退到 displayPath；生成「源到源」map 无增益。新增
   `NekoModulePreparationIdentityTest.sourceMapNullMeansIdentityForNativeJs` 固定该契约，
   并在 `NekoModulePipeline` 该分支加注释说明。转译语言仍必须产出非空 map（由 AC7 的 `.ts` 用例覆盖）。
6. **identity 冲突 corpus —— 已补**（`NekoModuleResolutionCorpusTest`，3 个用例）：
   ① 同名文件位于不同目录保持**不同模块身份**（含虚拟 ESM 注册表按 file-name 建索引这一最易混淆形态：
   两份导出互不串味 + prepared 不同实例 + cache key 不同）；
   ② 同名 `.cjs` 与 `.mjs` 从不同入口 import **不共享模块状态**（互操作不得互相覆盖）；
   ③ 同一路径经两个入口引用复用**同一身份**（对照 ①，证明区分不是「一律不同」）。
7. **AUTO+!module 分支的行为微变 —— 已记录，判定为正确**（REPORT §3.5）。
   `AUTO && !module` 的 `.js`（无 import/export）从 `commonJs(code, sourceMap)`（`cjsRecord=EMPTY`）
   变为携带真实 `CjsStaticAnalyzer` 结果。理由：同一份「模块是 CJS」的结论不应因「扩展名强制」
   还是「AUTO 判定」而得到不同质量的事实；`CjsStaticAnalyzer` 是纯 token 级静态近似，
   不改变 `code`/`mode`/`sourceMap`，**脚本可观察行为零变化**；且 `cjsRecord` 当前无生产读者，
   爆炸半径为零。已写入 REPORT 而非隐藏。
8. **AC10 证据边界 —— 已按白名单口径改写**（REPORT AC10 段）。审计确认：AC10 字面（不**创建**
   Graal Context / 不决定 HostAccess / 不读 MC-loader）在 19 文件**白名单内成立**，
   **在 `core/module` 包级不成立**——同包 `NekoScriptModuleLoaderHost` / `EsmModuleLifecycle`
   持有 `Context`（后者还 `context.eval`）、`NekoEsmModuleRecord(Cache)` 持 static `Value`。
   这些**都是** Script Execution Environment 成员，且**无一**调用 `Context.newBuilder`；
   全仓唯一创建点是 `NekoSandboxFactory:126`。REPORT 不再表述为「整包零 Context」，
   并逐一点名包内持 Context 的成员。`NekoScriptModuleLoaderHost` 不移出：它是票 06 已验收的
   generation/commit 执行入口，强行剥离会与其冲突（REPORT §4）。
9. **AC5 的 static registry —— 已写明归属**（REPORT §3.5/§3.6）。`ModuleReloadCoordinator` 与
   host 仍用 static `NekoEsmVirtualModuleRegistry`；runtime-ledger **A9** 明确把它归为
   **generation-owned** 且原文标注「**06/07 处理，本票不动**」。本票只收口 **A8**（prepared module
   cache）的所有权与失效维度。**这不是本票的验收缺口**，已在 REPORT §3.6 与本节记录，
   避免后续 reviewer 重复报告同一问题。

**本轮额外发现并修复的真实缺陷（测试隔离）**

按主 agent 要求以 `:common:test --rerun-tasks --no-build-cache` **全量真跑**时，首次暴露
**25 个 `DirectoryNotEmptyException`** 失败（`ScriptReloadGenerationTest:201`、
`ScriptReloadRegressionTest:196` 等）。根因在**本票新增测试**，不在被测实现：
新增测试用 `file.toFile().deleteOnExit()` 清理，而 `deleteOnExit` 只在 **JVM 退出时**执行、
测试之间不生效；残留的**子目录**（`resolution-corpus/` 等）让既有测试的目录清理
（`Files.deleteIfExists` 只删文件不删目录）抛异常——全部 common 测试共用**固定** gameDir，
构成跨测试污染。先前子集 `--tests ...` 跑法未触发该顺序，因此此前是绿的。

**修复**：新增 `TestModulePreparation.deleteFilesAndEmptyParents(List<Path>)`，
7 个新增测试全部改为 `@AfterEach` 真删文件 + 变空的父目录；纪律写入 REPORT §5.1.1 与
`MIGRATION.md` §4（**新测试务必至少全量跑一次**）。

**修订后门禁（全量真跑）**：`:common:compileJava` / `:common:compileTestJava` /
`:common:test` 均以 `--rerun-tasks --no-build-cache` 执行 →
**199 suites / 1470 tests / 0 failures / 5 skipped**；`:common:check` pass；
`guardLint` pass（248 守卫块 / 331 文件 / 0 问题）。

**收尾 commit**：`37b448be`（双轴修订）→ `1b3796f4`（本节）→
`eb045f30`（`ScriptTypeScopedCacheClearTest` 的白盒重写遗留 `deleteOnExit` 一并对齐新清理纪律）。
HEAD = `eb045f30`；`git status --short` 为空。
