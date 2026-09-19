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

- [x] NekoModulePipeline.prepare 的最高调用者测试证明 JS/CJS/ESM 输入产生正确 language id、module mode、可执行 code/IR、非空可用 source map 和稳定 cache key；compiler map 原样发布，native 同源输入发布行/列 identity map，no-map legacy transformed 输入发布 sourcesContent 保留且 generated-line -> authored-line clamp 的保守 map，sourceURL 仅作为 execution fallback。
- [x] prepared module 不可变，Resolution/Cache 修改源码、身份或诊断上下文时测试变红。
- [x] CJS require/module.exports 与 ESM import/export/link 的模块身份在重复加载、循环依赖和跨入口调用下保持既有语义。
- [x] 内容、路径、mode 或 language identity 变化会失效对应 cache；同 stamp 同长度但内容不同的覆盖写入不会返回旧模块。
- [x] 按 ScriptType 清理只影响目标范围，共享 node_modules/跨类型缓存行为有显式断言。
- [x] 准备失败、resolve/link 失败、缓存失败和执行失败可区分 owner 与阶段，错误不延迟成无来源的 Graal 异常。
- [x] 跨 import 的执行错误能经 compiler/identity map 回到 authored 文件、行列和 failing module identity；sourceURL 仍可作为 execution fallback，错误仍保留 authored module identity。
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
    `stableCacheKey` = SHA-256(source path/language/mode/code/map)；旧三参工厂保留默认 `unknown`）。
  - `NekoModulePipeline`：删除 `LEGACY_INSTANCE / bindLegacyInstance / legacyInstance /
    legacyPrepare / SHARED_COMPILATION_PIPELINE`（05 总账 A7 删除条件即“W3 显式注入后删除”）；
    新增 `identify`（纯语言/mode 描述子）、trust 凭证门 `prepare(path, source, approval)`、
    PREPARE 阶段错误包装；compiler map 原样保留，原生 JS/CJS/ESM 和无 map 的 legacy
    输入用 `NekoSourceMapBuilder.identity` 生成真实非空 map，sourceURL 仅作 execution fallback。
  - `NekoModulePipelineCache`：static 持有改实例持有（构造器注入 pipeline；05 A8 保留域、
    W3 处理显式注入）；`FileStamp` 由 mtime/size/contentHash 与 language/mode identity 组成；
    PREPARE 错误透传不重标 CACHE；JSON 也经该 cache 的逐文件 trust/preparation 边界。
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
- 验收判定：按上述条件解释的 AC1–AC11 全部 pass（逐项证据见对照表；code-review 双轴自查的 4 项补强——反射篡改断言、
  CJS 栈行列断言、legacy host 级装载、root 持有语义测试——已纳入并全绿）。
- code-review（双轴）后修订：删 `NekoSandboxFactory#preparationCache` 未用观察面（投机通用性）；
  AUTO-CJS 路径改附产物分析经查无生产消费者；source-map utility 的 identity 入口现在服务
  native/legacy no-map prepared modules，javadoc 明确不是 parser/lowering/compiler SPI；示例与基线内联内容已核对一致。
- 遗留（不在本票范围）：`SourceMapRegistry` / `NekoEsmVirtualModuleRegistry` 已由 prepared cache
  持有并按 runtime owner 隔离；`ScriptCompilerRegistry.current()` 语言扩展点保留；性能阈值不设
  （PERF_BASELINE 独立）；05/06/07 语义未动。

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

- **Source-map / runtime evidence：finding confirmed, fixed.** 原生 JS/CJS/ESM 和没有 compiler map 的
  legacy path 现在发布非空 conservative map（source path、sources、sourcesContent；native 同源输入有
  generated/source line+column 对齐，transformed 输入按 authored 最后一行 clamp）；有 compiler map 的
  TS/JSX path 仍发布 compiler map，`sourceURL` 只作 execution
  fallback。`host.loadEntry` 仍验证 authored path/line，跨 host source maps 与 virtual sources 不互相污染。
  证据：`NekoModulePipelinePrepareTest`、`LegacyCjsBridgeCharacterizationTest`、
  `NekoModuleIdentityLifecycleTest#loadEntryRuntimeFailureKeepsTranspiledModuleSourceLocation`。

- **Standards / naming / duplication：finding confirmed, fixed or deferred with reason.** SHA-256 实现
  收口至 `NekoModuleHash`；display/root-message 逻辑收口至 `NekoModuleError`；反射型 registry/cache helper
  被替换为公开 owner seam 测试；语言/mode 描述统一由语义明确的 `identify`/`NekoModuleIdentity` 承载；
  仅透传的 `withExplicitPipeline` 删除，保留显式构造器；五元组没有另造 identity class 来重复 source path，
  而是 `NekoModuleIdentity` 承载 language/mode、prepared key 单独承载 source path，避免重复身份事实。
  未做无关的 parser/compiler 重构，故无 deferred implementation debt。

## Review-round-2 addendum（2026-09-19）

本轮针对 `1499d462`（前置实现 `ab7ca108`）继续复核 ticket 11 的二轮 findings；`Status` 保持
`closed`。本 addendum 只记录实际修法、已验证证据和限制，不把未验证的 pack-sync 端到端行为标为完成。

- **Closure 文档矛盾：fixed.** Closure 中过时的 `describe` 更正为实际 API `identify`；稳定 key
  说明补上 source path。当前事实是：原生同源 JS/CJS/ESM 发布行/列 identity map；无 compiler map 的
  legacy transformed 输入发布带 sourcesContent 的非空 conservative line map，并把超出 authored 行
  clamp 到最后 authored 行；compiler map 仍原样发布；`sourceURL` 只是 execution fallback，不能替代
  prepared map。现有 `NekoSourceMapBuilder.identity` 是唯一明确的 source-map utility API，javadoc
  明确它不是 parser/lowering/compiler SPI。`MIGRATION.md` 与本 closure 的 source-map 说明同步。

- **Remote/JSON trust pipeline：fixed at the preparation boundary.** `NekoRuntimeAssembly` 的
  原有 overload 仍使用 `NekoTrustContext.local()`；新增的 assembly context overload 把显式
  remote context 只注入 `NekoModulePipelineCache`，再由同一 cache 传给 host、ESM linker 和
  native-ESM rewriter，不把 trust 参数扩散到 execute API。新增 `prepareJson`，CJS JSON load
  和 native-ESM synthetic JSON 都先逐文件 authorization/preparation；未授权 JSON 在读取或
  执行前以 `PREPARE/Pack Trust` 拒绝，JSON read I/O 以 `CACHE` 保留 cause。证据：
  `NekoModuleTrustStageTest#remoteApprovalFlowsThroughHostCacheAndJsonPreparation`、
  `NekoModuleErrorStageTest#jsonReadFailureCarriesCacheStageAndPath`。限制：pack-sync 具体签发
  remote context 仍由其现有 trust owner 负责，本票只提供并验证 production host/cache 链的注入边界。

- **AC7 source-map evidence：fixed.** `NekoModuleError` 增加统一的 authored `sourcePath`、
  `sourceLine`、`sourceColumn` 诊断字段；host 在最高调用者 `loadEntry` 边界根据 compiler/identity
  map（必要时再以 sourceURL 作 fallback）生成 EXECUTE error，并让跨 import 的 failing module
  identity 跟随映射后的 authored path，cause 保留原始 guest exception。证据：
  `NekoModuleIdentityLifecycleTest#crossImportTranspiledRuntimeFailureExposesAuthoredLocationAtLoadEntry`
  和 `#loadEntryRuntimeFailureKeepsTranspiledModuleSourceLocation`，均断言异常对象字段，
  不读取 registry 作为唯一证据。原生无 compiler map 的 prepared fallback map 负责映射，sourceURL 仍可作执行 fallback。

- **CJS nested dependency error layering：fixed.** `requireUnchecked` / `resolveToStringUnchecked`
  通过 host-thread boundary failure 保留 nested `NekoModuleError`，`executeScriptModule` 优先
  传播原阶段；因此 nested resolve/prepare/link 不再被归为 EXECUTE。JSON 读取同样不再原样
  抛出 IOException。证据：`NekoModuleErrorStageTest#nestedCjsResolutionFailureRetainsResolveStageAndCause`
  （断言 RESOLVE、specifier、父路径和 cause）以及 JSON cache-stage 测试。

- **Scope creep / duplicate wrappers：fixed.** `NekoSourceMapBuilder` 仅作为明确的 source-map
  utility API 公开 fallback 入口，不是 parser/lowering/compiler SPI；`NekoModulePipeline` 以私有 `LanguageBinding` 合并 language id/plugin
  查找，`NekoScriptModuleLoaderHost` 以私有 `resolveWithStage` 合并三个 resolver wrapper。
  两个 helper 都不新增公共 API，且未改动 parser/compiler 的无关结构。

## Review-round-3 addendum（2026-09-19）

本轮针对最终二次 code-review 的 Spec findings 在 `3e428580` 上继续 fix-forward；`Status` 保持
`closed`，既有 AC 不反勾。以下记录实际 production seam、行为测试和仍然存在的边界。

- **AC8 production remote trust：fixed.** `NekoRuntimeAssembly` 默认生产装配现在创建
  runtime-owned `NekoRuntimeTrustContext`，并把同一对象注入 `NekoModulePipelineCache` 与
  `NekoRuntimeRoot`。NeoForge `PackSyncClientConnections` 和 Fabric `FabricPackSync` 在 assembly
  后把现有 `NekoRuntimeRoot` 传给 common `PackSyncClient`：PackSyncClient 只有在验签、盘上哈希复核、显式服务器信任和
  `SERVER_CACHE` 激活成功后，才把每个物化内容文件的 `REMOTE_AUTHORIZED(packId,keyId)` 凭证送入
  当前 runtime；随后才触发 `root.reload(CLIENT)`。无 runtime binding、无非空签名 keyId、验签/完整性
  失败或异常授权都不激活/不注入；断线、空清单、hashOnly 清理会先 revoke，远端路径不会回落为
  `LOCAL_TRUSTED`。没有新增 trust store 或 runtime owner。证据：
  `PackSyncClientTest#successfulActivationAuthorizesRuntimeCacheAndDisconnectRevokesIt` 实际把
  PackSync 物化文件送入 cache prepare，并断言断线后的 `PREPARE/Pack Trust` 拒绝；平台生产接线位于
  `PackSyncClientConnections` / `FabricPackSync`；`#switchingServerBucketsRevokesPreviousRuntimeSourcesBeforeActivatingNext`
  覆盖不同 server/bucket 切换。common 不再接收 `RemoteTrustHook`，也不持有 static current root。

- **AC4 execution-cache identity：fixed.** CJS `ModuleState` 现在保存 prepared `cacheKey`，
  ESM lifecycle 在 namespace/record cache 命中前比较 prepared identity；内容、path、mode、language
  变化因此不能复用旧 exports/namespace。host 在入口 cache hit 前刷新已知 dependency tree，子模块
  identity 变化会清理受影响的父执行树；异步入口复用同一刷新路径。证据：
  `NekoModuleIdentityLifecycleTest#changedCjsSourceInvalidatesExportsWithoutExplicitInvalidate`、
  `#changedEsmSourceInvalidatesNamespaceWithoutExplicitInvalidate`（等长覆盖，不调用 invalidate），
  `#changedLanguageIdentityInvalidatesCjsExportsWithoutExplicitInvalidate`、
  `#changedStaticEsmChildInvalidatesParentWithoutExplicitInvalidate`、
  `#changedDynamicJsonChildInvalidatesParentAndRetainsDependencyPath` 与
  `NekoModulePipelineCacheStampTest` 的 path/mode/language identity 断言。cache 的 package-private
  `BiConsumer<Path,String>` observation seam 让 direct linker/rewriter preparation 也登记 module path/key。

- **Literal dynamic import staging：fixed.** `NekoNativeEsmSourceRewriter` 对 literal dynamic
  import 只替换 literal span；执行时调用 `resolveNativeImport(parentId, literalSpecifier)`，由 host
  完成 resolve/prepare、recordDependency 和 virtual ESM link。resolver I/O/未解析错误统一包装为
  `NekoModuleError.RESOLVE`，link 错误为 `LINK`，host 不再把已有 staged error 转成 `EXECUTE`；父模块
  路径、specifier 和原始 cause 保留。证据：`NekoModuleIdentityLifecycleTest` 的 runtime dependency
  invalidation、RESOLVE 和 LINK 用例。

- **ESM cycle characterization：fixed/characterized.** 增加真实 `.mjs` A↔B 循环依赖，使用已存在的
  ESM link/evaluation lifecycle 与 live binding 语义，断言循环可完成且导出值正确：
  `NekoModuleIdentityLifecycleTest#esmCycleUsesExistingLinkAndEvaluationSemantics`。当前实现没有将
  cycle 伪装成 unsupported；CJS partial exports 与 ESM cycle 各自沿既有语义运行。

- **Observation surface：fixed.** `NekoRuntimeRoot#preparationCache()` 改为 package-private；
  `NekoModulePipelineCache#sourceMaps()` / `#virtualModules()` 保持 package-private owner seam；
  `DefaultErrorTracker#sourceMaps()` / `#virtualModules()` 也改为 package-private，只读返回
  `NekoSourceMapView` / `NekoVirtualModuleView`。跨包执行环境只收到只读 view，唯一 public cache 诊断
  为 `preparedEntryCount()`；测试迁移到 owner package，不保留 public mutable registry getter。

- **Standards canonical-path duplicate：fixed.** `NekoTrustApprovedSource.subjectOf(Path)`
  收口为 package helper，`NekoRuntimeTrustContext` 复用同一 canonical/Windows-case identity，
  没有再造第二套路径规范化事实源或公共 API。

- **Remaining limitations：recorded, not deferred AC debt.** `NekoEsmParser` 既有 top-level scan
  对嵌在被整体跳过的复杂 `export` statement 内的 dynamic import 仍不是本轮 parser 重构范围；本轮
  characterization 使用 parser 已承诺的 literal dynamic-import expression 形态，并覆盖 rewriter
  的 resolver seam。PackSync remote authorization 只授权盘上 bundle content files，manifest 本身
  不作为可执行模块授权；这与 pack activation 的脚本内容边界一致。

## Review-round-4 addendum（2026-09-19）

本轮针对最终 review 的 A-D findings 在 `c44bbf2e` 上 fix-forward；`Status` 继续保持 `closed`，只记录已实现且已验证的修法，不把未覆盖行为标为完成。

- **A. Source-map contract：fixed.** 恢复 06-language-module-pipeline 的真实契约：每个成功的 prepared
  language module 都有非空可用 map。native `.js`/`.mjs`/`.cjs` 和 legacy compiler 未提供 map 时调用
  唯一明确的 `NekoSourceMapBuilder.identity` utility；native 同源输入包含行/列 identity，legacy
  transformed 输入使用 generated-line -> authored-line clamp 的 conservative map，均包含 source path、
  sources、sourcesContent；TS/JSX 等 compiler-produced map 仍使用 compiler map。cache 不再把
  null 发布为空 mapping；`sourceURL` 只保留为执行 fallback。证据：`NekoModulePipelinePrepareTest`、
  `LegacyCjsBridgeCharacterizationTest` 和 `SourceMapRegistry` 行映射断言。

- **B. Remote trust cache protection：fixed.** `NekoRuntimeTrustContext` 现在以 remote cache root 为
  protection domain；bundle replacement/revoke 先清理旧 approvals/path markers，再授权当前 sources。
  已见过的旧 bucket root 继续受保护，因此 stale/old files、空清单、hashOnly 和断线都不会回落为
  `LOCAL_TRUSTED`。NeoForge、Fabric 在 assembly 后把现有 root 传入 PackSyncClient，bundle switch
  先停用旧 active set，再授权新 root；没有 RemoteTrustHook、第二 trust store 或 static current root。
  证据：`PackSyncClientTest` 的 replacement、stale、不同 server/bucket switch、current-source 和
  disconnect assertions。

- **C. Dynamic import runtime graph：fixed.** literal dynamic import 不再在 rewrite 阶段 resolve/register；
  执行期经 `resolveNativeImport` 走 resolver、prepare、dependency graph 和 ESM link。动态子模块等长
  内容变化会使 parent execution cache 失效；RESOLVE/LINK 阶段和 cause 在异步 `loadEntry` 路径保留。
  static import/export 的 link path 继续记录依赖；cache 的 package-private observation seam 记录
  static/dynamic child 的 resolved path/prepared key，JSON dynamic resolve 额外以 resolved id 登记 key。
  证据：`NekoModuleIdentityLifecycleTest` 的 static child、dynamic JSON、runtime dependency invalidation、
  missing-module RESOLVE 和 bad-module LINK 用例。

- **D. Registry observability：fixed.** `DefaultErrorTracker.sourceMaps()` 与 `virtualModules()` 改为
  package-private，并返回 `NekoSourceMapView` / `NekoVirtualModuleView` 只读契约；测试使用 package seam，
  不保留 public mutable registry getter。runtime-owned mutable registries 仍只在 owner package 内部使用。
  证据：`DefaultErrorTrackerTest#registryObservationsArePackagePrivateReadOnlyViews`。

- **E. Path/document consistency：fixed.** remote trust 继续复用
  `NekoTrustApprovedSource.subjectOf/isWithin` 的 canonical/Windows-case identity，不新增第二套 canonical
  path helper；本 ticket、closure、Review-round-2/3 addendum 和 `MIGRATION.md` 已改为 fallback-map/
  protected-root/runtime-dynamic-import 的事实口径。

- **Remaining limitations:** parser 对被整体跳过的复杂 `export` statement 内 dynamic import 的既有扫描
  限制仍不在本轮 parser 重构范围；测试使用 parser 已承诺的 literal expression 形态。PackSync 仍只授权
  盘上 bundle content files，manifest 不作为可执行模块；这些边界未被本轮标记为额外完成项。

## Acceptance evidence matrix（2026-09-19）

下表是本轮 fix-forward 的实际验收口径。Gradle 命令均在仓库根目录执行；测试制品位于
`common/build/test-results/test/` 与 `common/build/reports/tests/test/`，本轮不更新 golden。

| AC | 实现 seam / 测试方法 | 精确 Gradle 命令 | 结果、制品与限制 |
|---|---|---|---|
| AC1 | `NekoModulePipelinePrepareTest` 的 native `.js/.mjs/.cjs`、legacy transformed fallback map、captured binding；`ModulePipelineIsolationTest` 的 source-map helper 运行/扫描 | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoModulePipelinePrepareTest --tests com.tkisor.nekojs.core.module.ModulePipelineIsolationTest` | PASS；JUnit XML/HTML test report；native 同源 map 是行/列 identity，legacy no-map 是保守 line map，不宣称 exact。 |
| AC2 | `NekoModulePipelinePrepareTest#preparedModuleIsImmutable`、tamper/cache-key assertions | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoModulePipelinePrepareTest` | PASS；record/final/反射篡改与 key 变化证据在测试报告；无额外限制。 |
| AC3 | `NekoModuleIdentityLifecycleTest` 的 CJS/ESM identity、重复加载、cycle、跨入口；`NekoModulePipelineCacheStampTest` | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoModuleIdentityLifecycleTest --tests com.tkisor.nekojs.core.module.NekoModulePipelineCacheStampTest` | PASS；最高调用者执行结果与 cache stamp 报告；复杂 export 中 dynamic-import parser 扫描仍沿既有限制。 |
| AC4 | cache content/path/mode/language stamp；两 host 共享 cache 的 static ESM/JSON child；dynamic ESM child、dynamic JSON 等长改写 invalidation | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoModulePipelineCacheStampTest --tests com.tkisor.nekojs.core.module.NekoModuleIdentityLifecycleTest --tests com.tkisor.nekojs.core.module.NekoModulePipelineMultiHostTest` | PASS；等长覆盖后两个 host 的 parent execution tree 都返回新值；multi-host 用例分别证明 static ESM 与 JSON child 的观察者不会被后构造 host 覆盖。 |
| AC5 | `ScriptTypeScopedCacheClearTest` 的 owner/type/共享 node_modules 清理与 registry isolation | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.ScriptTypeScopedCacheClearTest` | PASS；runtime-owned cache/source-map/virtual-module 测试报告；无真机 reload 制品。 |
| AC6 | `NekoModuleErrorStageTest`、`NekoModuleTrustStageTest`、`NekoScriptModuleLoaderHostSyntaxLocationTest` | `./gradlew.bat :common:check` | PASS；full common check report；未宣称未执行的不同 loader server runtime smoke。 |
| AC7 | `NekoModuleIdentityLifecycleTest` 的跨 import authored path/line/column、static/dynamic literal rewrite runtime failure；virtual rewritten map 注册与 native fallback map；prepare map registry 合法 line/path/sourceContent | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoModuleIdentityLifecycleTest --tests com.tkisor.nekojs.core.module.NekoModulePipelinePrepareTest` | PASS；测试从 `loadEntry`/`loadEntryAsync` 异常字段和 guest cause 观察 authored path、合法 line/column，未以 registry 查询作为唯一证据；rewritten native source 的 generated column 使用 conservative authored-line 起点，transformed compiler source 同样不宣称 exact column；sourceURL 仅 fallback。 |
| AC8 | root-owned `NekoRuntimeTrustContext` 直接传入 PackSyncClient；签名/盘上 hash/授权/revoke；同 bucket replacement 的 selected active set；不同 server/bucket switch | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.pack.sync.PackSyncClientTest` | PASS；包含 `successfulActivationAuthorizesRuntimeCacheAndDisconnectRevokesIt`、`replacingBundleRejectsOldAndStaleFilesButAllowsCurrentSource` 与 `switchingServerBucketsRevokesPreviousRuntimeSourcesBeforeActivatingNext`；replacement 保留旧物理缓存但 active registry 只含当前 syncId 目录；无真实 Minecraft network session。 |
| AC9 | `LegacyCjsBridgeCharacterizationTest` 与 legacy compiler path 的 conservative map/captured binding | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.LegacyCjsBridgeCharacterizationTest --tests com.tkisor.nekojs.core.module.NekoModulePipelinePrepareTest` | PASS；legacy bridge 保留原因/删除 gate 仍记录在 MIGRATION；不删除公开语言。 |
| AC10 | `ModulePipelineIsolationTest` 源码扫描/纯签名扫描/无零参或隐式 default helper 构造器/绝对路径 source-map helper；host 只接收 resolver/cache；平台依赖只在 execution assembly；三个 runtime-owned cache/registry 禁止 `ScriptTypeEnv`、`NekoJSPaths`、`Platform` token | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.ModulePipelineIsolationTest`（并由 `:common:check` 重跑） | PASS；反射与去注释扫描覆盖 `NekoModulePipelineCache`、`NekoModuleResolver`、loader host、`SourceMapRegistry`、`NekoEsmVirtualModuleRegistry`、error tracker，禁止 `NekoJSPaths.get()`、`Platform.getGameDir`、`ScriptTypeEnv` 与 `defaultPreparationCache`；common compile 不创建 Context。 |
| AC11 | `ModuleExamplesSmokeTest`、module-examples resources、MIGRATION baseline | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.ModuleExamplesSmokeTest` | PASS；最小 JS/CJS/ESM 示例测试制品；真实 loader/in-game smoke 不在此命令内。 |

## Review-round-7 addendum (2026-09-19)

本轮针对终审 hard findings 在 `a1b19c70` 工作树上继续 fix-forward；本 addendum 只记录实际收口、调用者迁移和验证边界，不把未执行的真实 Minecraft/loader/network session 标为完成。

- **W3 runtime cache ownership：fixed.** 删除 `NekoSandboxFactory` 与 `NekoRuntimeRoot` 的无 cache public 构造器；同时删除 `ScriptManager`、`NekoJSFileSystem` 和 `NekoNodeModuleInstaller` 会自行创建 `NekoModulePipelineCache` 的旧入口。生产 `NekoRuntimeAssembly` 仍只创建一个 cache，并把同一引用传给 factory/root；root 构造边界还会拒绝 factory/cache identity 不一致的调用。manager、filesystem、installer、host 的测试调用者现在都显式接收测试 fixture 或装配传入的 cache。`NekoRuntimeModuleCacheOwnershipTest#factoryRootManagerAndHostAllUseTheSameCacheIdentity` 通过字段 identity 断言四个对象使用同一实例，并断言 root 拒绝第二份 cache；`ModulePipelineIsolationTest#runtimeObjectsHaveNoImplicitPreparationCacheConstructionPath` 反射断言旧构造器和旧 installer overload 不存在。限制：该 identity 测试不启动完整 `NekoRuntimeAssembly` 的 auto-load/discovery 序列，生产 single-owner 图由 assembly 源码和显式构造签名共同验证。

- **AC10 resolution-cache platform boundary：fixed.** 本 round-7 snapshot 当时以纯值 `NekoModuleResolutionPaths(Path gameDir, Path root, Path nodeModules)` 注入 roots；`NekoModuleResolver` 不再 import/持有 `NekoJSPaths`，也不访问 `Platform`，所有 containment、real-path、node_modules 和 loader-relative path 计算只使用注入的 `Path` 与 `java.nio.file.Files`。该 record 方案随后由 round-8 的 plain `Path` constructor cutover supersede；旧 `NekoModuleResolver(NekoJSPaths, ...)` 构造器已删除。`NekoModuleResolverTest` 覆盖 relative entry/JSON/script candidates、bare node_modules、traversal 和 symlink escape。限制：symlink 用例依赖测试文件系统允许创建 symbolic link；当前 Windows 环境已实际执行通过。

- **Filesystem path injection：fixed.** `NekoJSFileSystem` 只保留 `(initialWorkingDirectory, policy, paths, preparationCache)` 显式入口，目录过滤器复用该实例的 paths，不再在迭代时调用 `NekoJSPaths.get()`；Node installer 只保留带 resolver/paths/error tracker/config/cache 的显式 install 入口。既有 `ScriptManager` / `ScriptLocator` discovery owner 未改动。

### Review-round-7 evidence matrix

| Finding / AC | 精确证据 | 精确 Gradle 命令 | 结果、制品与限制 |
|---|---|---|---|
| W3 cache identity / constructor cutover | `NekoRuntimeModuleCacheOwnershipTest` 的 factory/root/manager/host identity；`ModulePipelineIsolationTest` 的 factory/root/manager/filesystem/installer 旧入口反射断言 | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.lifecycle.NekoRuntimeModuleCacheOwnershipTest --tests com.tkisor.nekojs.core.module.ModulePipelineIsolationTest` | PASS；JUnit XML/HTML test report；未宣称完整 loader bootstrap smoke。 |
| AC10 pure resolver boundary | round-7 historical `NekoModuleResolutionPaths` scan；round-8 `NekoModuleResolver` plain `Path` constructor/source scan；resolver 无 `NekoJSPaths`/`Platform` | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.ModulePipelineIsolationTest` | PASS；当前源码扫描与反射证据以 round-8 plain `Path` constructor 为准；不替代真实 Minecraft runtime evidence。 |
| Resolver behavior preservation | relative entry, `.js`/`.json` candidate, bare `node_modules`, traversal rejection, symlink escape | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoModuleResolverTest` | PASS；symlink test 在当前 Windows 文件系统实际运行；无 golden 更新。 |
| Required compile/check gates | common main/test compilation、common check、NeoForge/Fabric 26.2.0 main compilation、whitespace check | `./gradlew.bat :common:compileJava :common:compileTestJava`; `./gradlew.bat :common:check`; `./gradlew.bat :26.2.0:compileJava :26.2.0-fabric:compileJava`; `git diff --check` | 本轮均以命令实际结果为准；平台既有 deprecation、`this-escape` 与 Gson `InlineMe` classfile warning 记录为 warning，不影响成功；未更新 golden。 |

补充构建证据：`./gradlew.bat :common:compileJava :common:compileTestJava` 与
`./gradlew.bat :26.2.0:compileJava :26.2.0-fabric:compileJava` 均 PASS；平台编译仅有既有
deprecation、`this-escape` 与 Gson 注解缺失告警。综合门禁为 `./gradlew.bat :common:check`，未更新任何
golden 文件。上述限制是边界说明，不把未运行的真实 Minecraft/network bucket session 写成已通过。

## Review-round-8 addendum (2026-09-19)

本轮针对终审指出的 candidate module-session、PackSync reload 结果、common path case policy 和内部 helper
可见性问题继续 fix-forward；本 addendum 只记录当前实现与实际测试边界，不把平台连接或真实网络 session
推断为已验证。

- **Candidate module-session isolation：fixed.** `NekoModulePipelineCache` 继续由唯一
  `NekoRuntimeRoot` 持有，但现在可以在同一 owner 下打开 generation child session。pipeline、trust
  context 和 path policy 共享；prepared entries、source maps、virtual ESM sources 与 preparation
  observers 独立。`ScriptManager` 把 active/candidate session 随 `RuntimeEnvironment` 发布；失败只关闭
  candidate，commit 切换 candidate session 后再关闭旧 session；root close/clear 回收所有 child。
  `DefaultErrorTracker` 同步切换 session views，并在 candidate 失败时恢复旧类型错误状态。证据：
  `NekoModulePipelineCacheSessionTest#discardedCandidateKeepsActivePreparedMapAndVirtualSource`、
  `ScriptReloadGenerationTest#failedEsmReloadKeepsActiveSourceMapAndSuccessfulCommitPublishesNewSession`。

- **PackSync reload result and rollback：fixed.** Common reload hook 现在返回明确的 `boolean`；
  NeoForge/Fabric hook 只在 `root.reload(CLIENT).success()` 为真时报告成功，并把异常报告为失败。
  bundle 的 registry/remote credential 激活后若 CLIENT reload 失败，会停用选中的 SERVER_CACHE 集合、
  revoke remote sources；如果旧 active generation 仍在运行，则恢复旧 registry/credential，以保持旧
  runtime 一致，并返回 disconnect Outcome，不再返回 accepted。证据：
  `PackSyncClientTest#reloadFailureRejectsBundleAndRevokesSelectedRuntimeState`、
  `#failedReplacementRestoresThePreviousActiveRegistryAndCredentials` 与既有成功路径测试。
  当前证据是 common JUnit 和两套平台源码编译；没有真实 Minecraft client 或 network session smoke 证据。

- **Path/FileSystem case policy：fixed.** `NekoCanonicalPath` 与 `SourceMapRegistry` 不读取
  `os.name`；大小写行为由注入 `Path` 的 `FileSystem` 对 `A`/`a` 的 equality 决定，canonical/cache/trust
  与 source-map lookup 使用同一语义。`ModulePipelineIsolationTest` 扫描 common path boundary 并在当前
  文件系统上断言 canonical/source-map case behavior；该测试不伪造另一个 provider 的真实 filesystem。

- **Internal helper visibility：fixed.** `NekoModuleHash` 及其方法改为 `core.module` package-private；
  `NekoModuleResolutionPaths` record 删除，`NekoModuleResolver` 公共构造器改接收 plain `Path` roots 与
  `ScriptFilePolicy`。测试与 production callers 已迁移；source-map utility 仍是唯一保留的明确 public
  helper 边界。

### Review-round-8 evidence matrix

| Finding | 精确证据 | 精确 Gradle 命令 | 结果与限制 |
|---|---|---|---|
| Candidate session isolation/rollback | `NekoModulePipelineCacheSessionTest`、`ScriptReloadGenerationTest` 的 failure/commit/source-map assertions | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoModulePipelineCacheSessionTest --tests com.tkisor.nekojs.script.ScriptReloadGenerationTest` | PASS；覆盖 active 保持、candidate 丢弃、成功 commit；无真实 loader runtime smoke。 |
| PackSync hook failure | `PackSyncClientTest#reloadFailureRejectsBundleAndRevokesSelectedRuntimeState`、`#failedReplacementRestoresThePreviousActiveRegistryAndCredentials` 与 trusted success path | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.pack.sync.PackSyncClientTest` | PASS；common hook/registry/trust 状态证据；无真实 Minecraft/network session。 |
| Path/FileSystem policy | `ModulePipelineIsolationTest` source scan + current filesystem case semantics；`SourceMapRegistryTest` registry isolation | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.ModulePipelineIsolationTest --tests com.tkisor.nekojs.core.error.SourceMapRegistryTest` | PASS；当前环境语义已测，不宣称跨 provider 或真实 Windows/Linux 双机 smoke。 |
| Helper visibility/resolver migration | `ModulePipelineIsolationTest` reflection/source scan；`NekoModuleResolverTest` resolver behavior | `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.ModulePipelineIsolationTest --tests com.tkisor.nekojs.core.module.NekoModuleResolverTest` | PASS；record 已删除、hash 非 public；无 golden 更新。 |
| Required build gates | common compile/check、26.2.0 NeoForge/Fabric compile、`git diff --check` | `./gradlew.bat :common:compileJava :common:compileTestJava :common:check :26.2.0:compileJava :26.2.0-fabric:compileJava`; `git diff --check` | PASS；平台仅有既有 deprecation、`this-escape` 与 Gson `InlineMe` classfile warnings；不等于 Minecraft/network smoke。 |

## Review-round-5 addendum（2026-09-19）

本轮针对终审 findings 在 `31012c7a` 工作树上继续 fix-forward；票据仍保持 `Status: closed`。本 addendum 只记录本轮实际修法、行为证据和 source-map 精度边界。

- **1. Shared-cache preparation observation：fixed.** `NekoModulePipelineCache` 不再持有单个可覆盖的 `volatile BiConsumer`，改为 package-private `registerPreparationObserver` / `unregisterPreparationObserver` 的 `CopyOnWriteArrayList`。每个 `NekoScriptModuleLoaderHost` 保存自己的 observer；host close 和 `NekoNodeRuntime.close` 解除注册，runtime root 的最终 `cache.clear()` 清理 owner 级 observer 与三类 cache。`NekoModulePipelineMultiHostTest` 分别以 static ESM 与 JSON child 的内容变更从两个 host 的 `loadEntry` 结果证明后构造 host 不会使先构造 host 丢失 prepared key 观察。

- **2. Virtual rewritten ESM source maps：fixed with a documented conservative boundary.** `NekoNativeEsmSourceRewriter` 为每个 registered virtual module 注册带 virtual generated file 的 map；unchanged native source 复用 prepared map，长度变化的 rewrite 经 package-private cache composer 生成真实 generated-line 的 authored map。`loadEntry` 与 `loadEntryAsync` runtime failure tests 均从异常字段和原始 guest cause 观察 authored path、合法 line/column，dynamic async guest stack 在 host boundary 解析后同样经 virtual map。限制：rewrite replacement 会改变列宽，当前 fallback 对 rewritten/transformed source 只保证 authored path、line 和合法 conservative column（通常是 authored line 起点），不宣称 generated-to-authored exact column；compiler-provided map 未被伪造为 exact composition。

- **3. AC10 construction isolation：fixed.** 删除无调用者的 host/context、`defaultPreparationCache`、cache、virtual registry、source-map registry 和 `DefaultErrorTracker(SandboxConfig)` 隐式构造路径；host 现在只接收 Context、resolver 和显式 preparation cache，不接收 platform path。`ModulePipelineIsolationTest` 新增 constructor reflection 与去注释源码扫描，覆盖零参构造、`NekoJSPaths.get()`、`Platform.getGameDir` 和 default helper token，而不是只依赖生产 assembly 路径。

- **4. Canonical path identity：fixed.** 新的 package-private `NekoCanonicalPath` 是 cache key、trust subject/`covers` 和 runtime trust protected-root checks 的共同 canonical/realpath/Windows-case helper；没有新增 public path API，也没有保留 cache/trust 两套 normalize/realpath 事实。

- **5. JSON execution identity：fixed.** `NekoModulePipelineCache#jsonExecutionKey(Path, String)` 是 package-private owner helper；`prepareJson`、host JSON load、native JSON resolve 和 dependency refresh 都经该 helper 生成同一 execution key，不再由 host 复制 key 组合。

本轮执行证据：目标 module/source-map/isolation tests、`./gradlew.bat :common:check`、`./gradlew.bat :26.2.0:compileJava`、`./gradlew.bat :26.2.0-fabric:compileJava` 均 PASS；`git diff --check` PASS；未更新 golden。平台编译的既有 deprecation、`this-escape` 与 Gson 注解缺失告警不影响成功结果。

## Review-round-6 addendum（2026-09-19）

本轮针对 `47430aea` 之后终审指出的 preparation/resolution-cache platform boundary 与 pack-sync stale active set fix-forward；票据继续保持 `Status: closed`。本 addendum 只记录本轮实际边界修复和证据，不修改权威 spec，不更新 golden。

- **1. Preparation/Resolution-Cache platform boundary：fixed.** `NekoModulePipelineCache.scriptTypeOf`、`SourceMapRegistry.clearByScriptType` 和 `NekoEsmVirtualModuleRegistry.scriptTypeOf` 删除 `ScriptTypeEnv` 依赖，只使用各自注入的 root/registry 路径边界和公开稳定约定 `ScriptType.name + "_scripts"` 推导类型目录；不读取 `NekoJSPaths.get()`、`Platform.getGameDir` 或其它平台 owner。既有 `ScriptManager` / `ScriptLocator` discovery owner 未改动。`ModulePipelineIsolationTest#runtimeOwnedCachesUseOnlyInjectedRootForScriptTypeBoundaries` 对三个源码文件去注释扫描并禁止 `ScriptTypeEnv`、`NekoJSPaths`、`Platform` token；已有构造器结构断言继续证明这些类型没有隐式默认路径。

- **2. PackSync stale active set：fixed.** `ScriptPackRegistry` 保留无参 `activateServerCachePacks(Path)` 的兼容全扫描语义，新增按 syncId 集合筛选的 overload，只扫描选中 syncId 编码目录；`PackSyncClient.handleBundle` 在盘上 `resolved` 完整性校验通过后传入 `resolved.keySet()`。bucket 中旧物理目录/文件不删除，但不会再进入 `serverCachePacks()`。`PackSyncClientTest#replacingBundleRejectsOldAndStaleFilesButAllowsCurrentSource` 断言旧文件仍在、active registry 只含当前目录且当前 source 可准备；`#switchingServerBucketsRevokesPreviousRuntimeSourcesBeforeActivatingNext` 保留并覆盖不同 bucket 切换。

- **3. Evidence boundary：verified.** 相关单测、`:common:compileJava`、`:common:compileTestJava`、`:common:check`、`:26.2.0:compileJava`、`:26.2.0-fabric:compileJava` 和 `git diff --check` 为本轮验收命令；未宣称真实 Minecraft/network session smoke，未更新 golden。
