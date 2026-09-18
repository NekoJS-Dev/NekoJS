# 11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径

**What to build:** 脚本入口把 source/path/extension/requested mode/trust-approved source 交给 Preparation，得到带 language id、module mode、source map、诊断位置和稳定 cache key 的不可变 prepared module；Module Resolution/Cache 用它完成 CJS require、ESM import/link、依赖图、命中/失效和生命周期，最终 Graal 执行结果或错误能映射回原文件；模块 cache/session 的生命周期由 runtime owner 持有。legacy CJS bridge 被显式 characterization，而不是形成第二语义管线。

**Blocked by:** [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md)

**Status:** closed

**Assignee:** 11-agent

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

## Closure record（2026-09-18）

- 执行者：11-agent。工作流：认领（ready-for-agent → in-progress）→ 读票/spec（06/01/07）/
  交接单（W3）/CONTEXT → 源码定位 → TDD（先写失败测试再实现，见过程红侧记录）→
  定期 typecheck（`:common:compileJava`、`:common:compileTestJava`）与单文件测试 →
  全量 `:common:check` → code-review 双轴自查修订 → 验收勾选 → 关票提交（不 push）。
- 先决输入（05 closed，不重做）：单 owner `NekoRuntimeRoot`（`NekoRuntimeAssembly` 共享装配）、
  runtime-owned session 生命周期；本票新增 runtime-owned prepared 缓存实例并由 root 持有。
- 交付物（源码）：
  - `NekoPreparedModule`：新增 `languageId / sourcePath / cacheKey` 组件（record 不可变，
    `stableCacheKey` = SHA-256(language/mode/code/map)；旧三参工厂保留默认 `unknown`）。
  - `NekoModulePipeline`：删除 `LEGACY_INSTANCE / bindLegacyInstance / legacyInstance /
    legacyPrepare / SHARED_COMPILATION_PIPELINE`（05 总账 A7 删除条件即“W3 显式注入后删除”）；
    新增 `describe`（纯语言/mode 描述子）、trust 凭证门 `prepare(path, source, approval)`、
    PREPARE 阶段错误包装、无编译器 map 时的恒等 source map 补齐。
  - `NekoModulePipelineCache`：static 持有改实例持有（构造器注入 pipeline；05 A8 保留域、
    W3 处理显式注入）；`FileStamp` 五元组（mtime/size/contentHash/languageId/mode）；
    PREPARE 错误透传不重标 CACHE；`withExplicitPipeline` 兼容装配入口。
  - `NekoTrustApprovedSource`（新）：`LOCAL_TRUSTED / REMOTE_AUTHORIZED` 精确路径绑定凭证，
    远端签发强制非空 keyId；`NekoModuleError`（新）：Stage（PREPARE/RESOLVE/LINK/CACHE/EXECUTE）
    + owner（Preparation/Resolution-Cache/Execution/Pack Trust）+ source/module 归因，
    继承 IOException（消息文本兼容既有断言）。
  - 注入收口：host/linker/rewriter/coordinator/readService/filesystem/installer/
    sandboxFactory/manager/root/assembly 全链路构造器注入；生产唯一共享实例由 assembly
    创建并传入 factory 与 root；`root.closeSilently` 全清；旧构造器自建隔离实例（测试互不污染）。
  - host 边界：resolve 失败→RESOLVE、宿主装载失败→EXECUTE（消息文本不变）、link 失败沿用
    `NekoEsmLinkException`（自带诊断）、guest 运行时异常原样传播。
- 交付物（测试，common，共 53 新/改 + 13 prior-art 同域）：
  `NekoModulePipelinePrepareTest`(7)、`NekoModulePipelineCacheStampTest`(5，实例重写+扩展)、
  `ScriptTypeScopedCacheClearTest`(7，实例重写+跨实例隔离)、`NekoModuleErrorStageTest`(6)、
  `NekoModuleTrustStageTest`(4)、`NekoModuleIdentityLifecycleTest`(7)、
  `LegacyCjsBridgeCharacterizationTest`(7)、`ModulePipelineIsolationTest`(5)、
  `ModuleExamplesSmokeTest`(4)、`NekoRuntimeModuleCacheOwnershipTest`(1)。
- 交付物（示例/迁移）：`common/src/test/resources/nekojs/module-examples/{js,cjs,esm}/`
  最小可运行示例 + `docs/architecture-refactor/baseline/2026-09-18-language-pipeline/MIGRATION.md`
  （脚本零迁移声明、示例内联、缓存/reload 行为、Java static→注入对照表、legacy 桥保留原因与收缩 gate）。
- 验收判定：AC1–AC11 全部 pass（逐项证据见对照表；code-review 双轴自查的 4 项补强——反射篡改断言、
  CJS 栈行列断言、legacy host 级装载、root 持有语义测试——已纳入并全绿）。
- code-review（双轴）后修订：删 `NekoSandboxFactory#preparationCache` 未用观察面（投机通用性）；
  AUTO-CJS 路径改附产物分析经查无生产消费者（中性）；`NekoSourceMapBuilder.identity` 可见性放宽
  有 parser-SPI 锁定测试兜底；示例与基线内联内容已核对一致。
- 遗留（不在本票范围）：`SourceMapRegistry` / `NekoEsmVirtualModuleRegistry` 仍为带分区清理的
  共享注册表（分区行为已锁定，实例化另票）；`ScriptCompilerRegistry.current()` 语言扩展点保留；
  性能阈值不设（PERF_BASELINE 独立）；05/06/07 语义未动。

## Review-round addendum（2026-09-19）

本轮针对 `ab7ca108` 的双轴 review findings 逐项复核并修正；票据仍保持 `Status: closed`，不回退已勾选 AC。

- **Registry ownership / cross-host pollution：finding confirmed, fixed.** `SourceMapRegistry`
  与 `NekoEsmVirtualModuleRegistry` 原实现仍是 process-wide static map；现改为 runtime-owned
  实例，由 `NekoModulePipelineCache` 持有，生产装配经 `NekoRuntimeAssembly` 注入给 root、filesystem、
  host、rewriter、lifecycle 和 error tracker；`NekoRuntimeRoot.closeSilently` 通过 cache close 清理三类
  条目。`clear(ScriptType)` 现在只清理本 owner 的分区。`SourceMapRegistry`/virtual registry 的 root
  采用 canonical path（不存在时 lexical fallback）。证据：`SourceMapRegistryTest`、
  `ScriptTypeScopedCacheClearTest`、`NekoModuleIdentityLifecycleTest#separateHostsKeepVirtualSourcesAndSourceMapsPrivate`、
  `NekoRuntimeModuleCacheOwnershipTest#rootOwnsSharedCacheIsolatedRootsDoNotShareAndCloseReleases`。

- **Trust context bypass：finding confirmed, fixed.** `NekoModulePipelineCache.prepare(path)` 现在先经
  runtime-owned `NekoTrustContext` 取得逐文件 `NekoTrustApprovedSource`，再把 approval 传到
  `NekoModulePipeline.prepare(path, source, approval)`；linker、rewriter、host、filesystem/read service
  都只能经该 cache 准备依赖。local/remote 凭证仍共用同一 prepare stage；real path、Windows 大小写
  规范化后采用逐文件授权，不承诺目录前缀授权。证据：`NekoModuleTrustStageTest` 的缺失/错配/remote key/
  canonical path 与 resolved dependency deny 用例。

- **Error layering：finding confirmed, fixed.** `NekoEsmLinkException` 现在只作为 cause，link 入口统一
  转为 `NekoModuleError(stage=LINK, owner=Module Resolution/Cache)`；host/module factory/JSON/CJS/ESM
  guest execution failures 归 `EXECUTE`，原始 `PolyglotException`/cause 保留；CJS syntax preparation
  diagnostics 归 `PREPARE`。证据：`NekoModuleErrorStageTest`、`NekoModuleIdentityLifecycleTest`、
  `NekoScriptModuleLoaderHostSyntaxLocationTest`。

- **Identity / cache key / path semantics：finding confirmed, fixed.** 抽出 `NekoModuleIdentity`
  （language id + requested mode）作为 preparation 与 `FileStamp` 的共同输入；prepared stable key
  升级为 SHA-256 v2，覆盖 prepared sourcePath、language、mode、code 和实际 source map（runtime cache
  先将 source path canonicalize），
  不再发生同内容跨路径身份碰撞。cache map key 使用 `toRealPath`，不存在时回退 normalized absolute path，
  Windows lexical identity 忽略大小写。`covers` 与 cache 使用相同路径口径；授权明确是逐文件。证据：
  `NekoModulePipelinePrepareTest#stableCacheKeyCoversPathLanguageModeCodeAndMap`、
  `NekoModulePipelineCacheStampTest` 的 canonical path/content/language/mode tests。

- **Source-map / runtime evidence：finding confirmed, fixed.** 原生 JS/CJS/ESM 和没有 map 的 legacy
  compiler 不再被恒称为“可用 source map”；它们依靠 script-loader 的 `sourceURL`。有 compiler map 的
  TS path 仍发布真实 map，并经 `host.loadEntry` 触发 guest error 验证 authored path/line；跨 host source
  maps 与 virtual sources 不互相污染。证据：`NekoModulePipelinePrepareTest`、`LegacyCjsBridgeCharacterizationTest`、
  `NekoModuleIdentityLifecycleTest#loadEntryRuntimeFailureKeepsTranspiledModuleSourceLocation`。

- **Standards / naming / duplication：finding confirmed, fixed or deferred with reason.** SHA-256 实现
  收口至 `NekoModuleHash`；display/root-message 逻辑收口至 `NekoModuleError`；反射型 registry/cache helper
  被替换为公开 owner seam 测试；`describe`/`ModuleDescriptor` 改为语义明确的 `identify`/`NekoModuleIdentity`；
  仅透传的 `withExplicitPipeline` 删除，保留显式构造器；五元组没有另造 identity class 来重复 source path，
  而是 `NekoModuleIdentity` 承载 language/mode、prepared key 单独承载 source path，避免重复身份事实。
  未做无关的 parser/compiler 重构，故无 deferred implementation debt。
