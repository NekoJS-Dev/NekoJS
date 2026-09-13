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
