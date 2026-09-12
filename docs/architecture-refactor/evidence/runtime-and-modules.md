# 核心运行时与模块边界：现状证据报告

> 观察时间：2026-09-07 23:16:05 +08:00。
> 本轮只读审阅了 architecture-refactor-map.md、proposal.md、decisions/*.md 与 evidence/*.md；其他 agent 可能同时修改这些 draft/open 文件，本报告不覆盖其工作。
> 本轮仅修正本文件，不修改业务代码、ADR、构建配置、路线图、提案或决策票。

## 范围、方法与结论等级

- 目的：为 NekoJS 重构提供当前运行时、模块归属和平台差异的证据基线。

- 范围：common 的 core、api、compiler、module、lifecycle、pack、platform，以及根 src 和 versions 的 loader 入口；同时核对构建脚本和相关 ADR。

- 术语遵循 CONTEXT.md：Script API 面向脚本作者，Plugin API 面向 Java 插件作者；扩展点、贡献面、扩展点句柄按既有定义使用。

- 事实：可由列出的源码、构建文件和行号直接核验。

- 推断：由事实推出并标明边界；未运行游戏或完整矩阵时，不把静态风险写成运行时结果。

- 建议：候选方向，不是实施授权。

- GraalJS 是固定执行引擎；高级 Java 访问是既有语义，不因模块整理自动收紧或删除。

- 语言实现自研优先；小型、合适且纯 Java 的库可以另行评估，10+ MB 量级不合适；非纯 Java 转译依赖不接受。语义、source map、诊断和语料覆盖必须先证明。

- 决策 07 已确定：本次重构完成后以 `1.2.0` 作为新标准并只做一次 clean cutover；这不是 2.x 规则，也不是今后任意版本随意破坏的长期授权。脚本和插件迁移表进入 P4 release handoff。

- 本地可信脚本与远端脚本均采用显式受限授权；当前不承诺对恶意脚本提供强隔离。

- 数据保护优先维持既有格式、路径和语义；不为重构附赠迁移平台，任何格式迁移、备份或回滚需求须单列裁定。


## 仓库与发布拓扑（事实）

- settings.gradle.kts:34-43 实际创建五个 Stonecutter 节点：NeoForge 1.21.1、26.1.2、26.2.0，以及 Fabric 26.1.2、26.2.0。settings 的“四个节点”注释陈旧；设计和验收以 DSL 为准。

- 这些节点是 Stonecutter 变体，不等于独立 Gradle 子项目。静态 Gradle 项目主要是 common 与 common-api-processor；五个节点由 Stonecutter DSL 生成并应用 loader convention plugin。
  证据：[settings.gradle.kts:34-43][buildSrc/src/main/kotlin/nekojs.neoforge-node.gradle.kts:1-22][buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts:1-20]

- common 是 Java 21、跨 MC/loader 的引擎主体；各 loader 制品把 common 输出和运行时依赖嵌入 fat jar。
  证据：[common/build.gradle:1-3,59-66][buildSrc/src/main/kotlin/nekojs.neoforge-node.gradle.kts:153-162,213-239][buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts:75-86,179-208]

- common-api-processor 是因注解处理器编译时序而保留的独立子项目，不是已批准的公共契约制品。
  证据：[common-api-processor/build.gradle:1-2,27-35]

- common-api 已并入 common 的 com.tkisor.nekojs.api；历史 ADR 记录过重新拆 jar 的失败成本。逻辑 Module 的归属必须先于物理项目拆分，不能把目录或 Gradle project 名当作架构结论。
  证据：[docs/adr/0007-module-boundaries.md:3-15,17-40]

- 当前没有批准独立 API jar、删除版本或删除功能。任何物理拆分都要先证明真实发布、依赖或独立测试收益，并通过正式决策票。


## 两个 loader 的装配镜像（事实）

~~~text
NeoForge entry
  static Platform/IdCompat init
  -> workspace and event registration
  -> annotated plugin discovery -> NekoPluginRuntime.bootstrapOwned
  -> compiler/config/filter/core/sandbox/module pipeline
  -> NekoRuntimeRoot -> ScriptManager discovery -> STARTUP load
  -> RegisterEvent passes -> load-complete afterInit

Fabric entry
  static Platform/IdCompat/catalog provider init
  -> event/network/command bridges
  -> workspace and the same script assembly skeleton
  -> FabricRegistryAdapter one-batch registration -> afterInit
  -> client entry loads CLIENT at CLIENT_STARTED
~~~

- NeoForge 的主装配在 src/main/java/com/tkisor/nekojs/NekoJSMod.java:142-182；Fabric 的对应路径在 versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/NekoJSFabricMod.java:132-175。

- 两处都执行插件 runtime bootstrap、ClassFilter.loadEngineConfig、共享 Engine、NekoSandboxFactory、NekoModulePipeline.bindLegacyInstance、NekoRuntimeRoot、按 ScriptType 发现并加载 STARTUP。两处镜像证明存在真实 runtime-assembly seam，不证明必须创建同名新类。

- loader-specific timing 不能被隐藏：Fabric 在初始化末尾批量注册后 fireAfterInit；NeoForge 在 FMLLoadComplete 的 pass 后 fireAfterInit。Fabric CLIENT 在 CLIENT_STARTED 才加载，NeoForge 在构造事件入队任务中加载。
  证据：[NekoJSMod.java:184-199][NekoJSFabricMod.java:116-120][NekoJSFabricClient.java:21-36][NekoJSClient.java:42-48]

- NeoForge loader 扫描注解类后交给 common manager；Fabric 还维护 BUILTIN_PLUGINS 手工清单并读取 entrypoint。Fabric 清单漏项是实际登记风险，不应以第二套 discovery framework 掩盖。
  证据：[NeoForgePluginLoader.java:10-22][FabricPluginLoader.java:15-46][NekoJSBasePluginManager.java:42-95,114-148]


## Plugin bootstrap 与已有运行时 root（事实）

- NekoPluginBootstrap 已是深 Module：内置 Point 先行，第三方 provider 随后；freeze 校验 id/dependsOn、Kahn 拓扑排序和环；每个 Point 依次 initializer、collector、finisher、seal、publish。
  证据：[NekoPluginBootstrap.java:114-141,225-277,316-369]

- Point/Handle interface 已承载 merge、enabled、dependsOn、产物访问和 reload 语义。应保留并深化此 seam，不再造万能 PluginContext、PluginFeatures 或 magic DI。
  证据：[NekoPluginExtensionPoint.java:13-56,144-232][docs/adr/0001-extension-point-model-v2.md][docs/adr/0010-plugin-authoring-model.md]

- NekoRuntimeRoot 已是平台 composition root。它持有 core、plugin runtime、event bridge、script properties、environment factory、ScriptManager map 和 ResourceTracker，公开 manager、reload、reloadFile、runTests、errors、close 等窄 lifecycle surface。
  证据：[common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java:20-58,61-117]

- Root close 的顺序是 managers、event/listeners、resources；它不是一个可随意向下传递的宽对象图。优先方向是收紧入口对 static root 的持有和双重 manager ownership，而不是再叠一层 RuntimeKernel、RuntimeAssembly、Gateway 或 Scope 容器。
  证据：[NekoRuntimeRoot.java:27-33,112-157]

- ScriptManager 仍有 Context 反查静态表；reload 会清理 listener、binding、cache 后再装候选 Context，失败时保留旧 Context 但共享状态已清空；STARTUP reload 不是事务式。
  证据：[ScriptManager.java:47-101,340-465,582-600,691-740]

- 两 loader 同时写 RuntimeRoot map 与继承的 NekoJS.scriptManagers，形成同一 manager 的双容器归属。最终应由一个 runtime owner 负责创建、发布、reload、close；具体生命周期仍由 05-runtime-lifecycle-and-data.md 裁定。
  证据：[NekoJSMod.java:173-176][NekoJSFabricMod.java:164-167][NekoRuntimeRoot.java:57,73-76]


## compiler、module 与跨包依赖（事实）

- compiler 与 module 不是单向层：NekoModulePipeline 依赖 compiler 的 compiler、IR、language plugin、registry 和 validators；compiler 又引用 module.esm 的 AST/parser 类型，module.esm 反向使用 compiler lexical helper，形成实际包循环。
  证据：[NekoModulePipeline.java:3-19][NekoCompilationPipeline.java:3][NekoCompileOutput.java:3][NekoEsmLexer.java:3,100-138]

- NekoModulePipeline 有 static SHARED_COMPILATION_PIPELINE、LEGACY_INSTANCE 和 fallback；两个生产 loader 都 bindLegacyInstance。NekoModulePipelineCache 又以进程级静态 Path cache 读取 legacy pipeline，而非由 host 显式持有。
  证据：[NekoModulePipeline.java:29-49,137-150][NekoModulePipelineCache.java:25-54,113-119]

- NekoScriptModuleLoaderHost 同时持有 resolver、ESM linker/record cache、CJS/ESM module cache、dependency graph、revision 和 reload coordinator；CJS require 支持循环依赖。实例 host 与全局编译选择因此混在一起。
  证据：[NekoScriptModuleLoaderHost.java:34-78,253-365]

- 分派语义不可省略：原生 JS/CJS 可 raw source 直接分析；legacy compiler 先 compileDetailed 再分析产物；其他语言经过 NekoCompilationPipeline，再按 IR 的 AUTO/module 选择 CJS 或 ESM。
  证据：[NekoModulePipeline.java:75-108]

- 候选的内部 seam 可以是 raw source -> prepared module，但 interface 必须保留 source map、ESM AST、CJS analysis record、module identity、cache invalidation 和 legacy CJS 旁路；不要把每个 parser/lexer 提升为 SPI。

- 这些 compiler/module/package 依赖与生命周期静态旁路应在 06-language-module-pipeline.md 和 05-runtime-lifecycle-and-data.md 中裁定；不能先用一层 Global pipeline 或通用声明框架把环隐藏掉。


## Registry、pack 与 loader 生命周期（事实）

- Registry 现有三层为 loader 注册事件 Adapter、平台无关 RegistryEventJS、先收集后建的 RegistryRepository；同 registry 同 id 重复对象 fail-fast。
  证据：[docs/adr/0004-generic-registry-model.md:3-13][RegistryRepository.java:23-90]

- NekoRegistryPointsPlugin 以 static handles 发布 RegistryInfos/RegistryTypes，RegistryEventJS.create 反向读取；NeoForge RegistryEventAdapter 持有 static repository/pass state，按 RegisterEvent pass drain；FabricRegistryAdapter 一次收集后枚举 key 直注。两种 drain 语义是真实的两个 Adapter，不能伪装为同一 loader event。
  证据：[NekoRegistryPointsPlugin.java:27-39,86-101][RegistryEventAdapter.java:42-78][FabricRegistryAdapter.java:21-85]

- 可评估一个域内 registration plan 来收拢收集、冲突诊断和 drain，但它是候选内部实现，不是再造 capability registry、全仓 catalog 或 container stack。Registry domain 的事实源仍须保持局部。

- ScriptPackRegistry 是 static default instance，管理 GLOBAL、WORLD、SERVER_CACHE，并按 scope 组合 enabled packs。
  证据：[ScriptPackRegistry.java:14-61,89-120]

- NeoForge ServerEventListener 会激活/停用 WORLD pack、挂 data pack、reload SERVER 并清理 world listeners；当前 Fabric source-tree lifecycle 中未见对应 activateWorldPacks/deactivateWorldPacks 调用。
  证据：[ServerEventListener.java:57-128][FabricServerEventBindings.java:137-157]

- 因此 Fabric WORLD pack 是 lifecycle gap 的静态事实；是否支持、受限或明确不支持须由 05 票裁定，不能由本报告替用户决定。

- PackSyncClient 的远端流程是 bounds -> 验签 -> persist -> 盘上 load/re-hash -> server trust -> activate/reload/pin；未信任 server 的已验签包会先进入 cache，未签名包在 allowUnsigned=false 时更早拒绝。
  证据：[PackSyncClient.java:135-199][PackSignatureVerifier.java:59-99]

- GLOBAL/WORLD 本地扫描不是远端签名强制策略；不能把远端 trust gate 外推为本地政策。Pack、world、pdata、cache 的格式和路径均列为保护对象，不附带迁移平台。
  证据：[ScriptPackRegistry.java:103-180][ScriptPackManifest.java:12-15,38-73]


## 隐式全局状态与 reload 风险（事实与推断）

- 进程级或静态状态包括 Platform.INSTANCE、NekoIdCompat.ADAPTER、NekoJSPaths、NekoEsmVirtualModuleRegistry ROOT、ClassFilter config、NekoSharedEngine、NekoJSBasePluginManager entries/cache、NekoPluginRuntime.current、NekoRuntimeAccess.runtime、ScriptCompilerRegistry.current、ScriptEventRegistry definitions、ScriptContextRegistry maps、NekoModulePipeline legacy/cache、SourceMapRegistry、ScriptPackRegistry、registry handles 和两个 loader 的 public RUNTIME_ROOT。
  证据：[Platform.java:10-35][NekoIdCompat.java:6-31][NekoJSPaths.java:19-38][NekoSharedEngine.java:5-15][NekoPluginRuntime.java:97-144][ScriptContextRegistry.java:27-62]

- 这些状态假定一 JVM 一套 platform/path/plugin/runtime；这符合当前进程，但增加嵌入、多 root、隔离测试和可预测重启的难度。它们是 ownership 风险，不自动证明需要取消共享 Engine。

- NekoPluginRuntime.bootstrapOwned 的生产入口只在两个 mod entry；普通 ScriptManager.reload 不会重新 bootstrap plugin runtime。NekoRuntimeAccess 的“full reload re-bootstrap”描述没有对应的生产调用证据，publish 也未见生产清空/null 路径。
  证据：[NekoJSMod.java:144-145][NekoJSFabricMod.java:135-136][ScriptManager.java:340-465][NekoPluginRuntime.java:86-95,139-144]

- 若未来允许 plugin runtime 重建，recipe override、registry、managed API、probe 和 static compiler state 的清空/替换顺序必须先有契约；这是 05 票内容，不在本 evidence 中决定。


## 契约包、Graal interop 与平台边界（事实；用户前提已澄清）

- 本轮用户直接澄清已记录于 [公开契约与插件面决策票](../decisions/04-public-contract-and-plugin-model.md)：`common-api` 已并入 `common`，维护者便利优先，`common`（含 `api.*`）允许使用 GraalJS；不为隔离 Graal 而抽 DTO、adapter 或另一个 API jar。

- 当前 api 源码实际使用 relocated `graal.graalvm.polyglot`，例如 JSTypeAdapter.java:5、EventBusJS.java:15-17。这是现行 Graal interop 事实，不是仍待裁定的“api.* 零 Graal”违规。当前源码 lint 仍包含过时的 Graal 禁止检查，且只匹配 `org.graalvm`；本轮未修改源码 lint。未来代码实施时移除或更新该过时检查；同时 `common`（含 `api.*`）禁止 MC/loader import，允许这些依赖的共享实现放在根 `src/` 或 node。
  证据：[docs/adr/0007-module-boundaries.md:21-35][stonecutter.gradle.kts:102-109]

- NekoJSPlugin、EventBusJS 等 api 类型还直接引用 core compiler/fs/module/plugin、probe、ScriptContextRegistry 和 ScriptManager；api.* 现在同时承载 portable surface、Graal interop、plugin facade 和 engine-internal surface。04 已决定按职责分类、保留现有 public FQCN 的稳定性优先级，不强制建立 portable/interop 子模块。
  证据：[NekoJSPlugin.java:3-20][EventBusJS.java:3,6-17]

- 不应靠重新拆 API jar 代替依赖整理；当前不新增独立制品；未来只有在参数类型解耦、真实消费者和发布/测试收益都证明后，才另开决策重评。

- McVersionCompat 在静态初始化中通过 ServiceLoader 必须找到 provider；源码服务声明目前在 NeoForge 26.1.2/26.2.0，Fabric 节点没有对应 service 声明或实现，26.x LevelExtension 的 spawnLightning 又调用 McVersionCompat.get。
  证据：[src/main/java/com/tkisor/nekojs/platform/compat/McVersionCompat.java:21-37][versions/26.1.2/src/main/resources/META-INF/services/com.tkisor.nekojs.platform.compat.McVersionCompat$Impl][versions/26.2.0/src/main/resources/META-INF/services/com.tkisor.nekojs.platform.compat.McVersionCompat$Impl][src/main/java/com/tkisor/nekojs/api/inject/LevelExtension.java:8,50-52]

- 上述 provider 可达性是静态推断，不是 runtime 实测；没有据此声称 Fabric 普通 bootstrap 必然失败。对应 wiring test 还被 neoforge guard 排除。
  证据：[src/test/java/com/tkisor/nekojs/platform/compat/McVersionCompatWiringTest.java:1-30]


## 语言、Graal Context 与安全边界（事实）

- NekoCompilationPipeline 的输入是 file/source/extension/language plugin，阶段为 source -> token -> AST -> IR -> compile output；IR 保留 languageId、code、sourceMap、requestedMode、module、topLevelAwait，compile output 带 ESM AST。
  证据：[NekoCompilationPipeline.java:9-23][NekoIRProgram.java:4-16][NekoCompileOutput.java:7-14]

- 内置 TS 使用手写 lexer/parser/type eraser；JSX/TSX 先 lowering 再复用 TS eraser；Python 为仓库内 Java lexer/AST/emitter。legacy IScriptCompiler 仍允许外部语言插件，但非纯 Java 转译依赖不属于本轮候选。
  证据：[NekoTypeScriptLanguagePlugin.java:6-32][NekoTypeScriptCompiler.java:1-38][NekoJsxCompiler.java:20-36][PythonToJsCompiler.java:10-18,34-47]

- NekoNodeModuleInstaller 对 modules.list 中的 .ts 资源直接 eraseTypescript 后 context.eval，不经过 NekoModulePipeline；这是另一个必须保留并验收的执行路径。
  证据：[NekoNodeModuleInstaller.java:30-59]

- NekoSandboxFactory 为每个 ScriptType 建 Context，共享 static Engine，注入 NekoJSFileSystem、HostAccess、ClassFilter、SandboxPolicy 和 Node runtime；HostAccess 实际是广泛模型，ClassFilter 只治理 Java.type/Java.loadClass 查找，不治理已返回宿主对象的成员图。
  证据：[NekoSandboxFactory.java:99-174][NekoSharedHostAccess.java:22-46][ClassFilter.java:17-31,107-113]

- 远端已有 Ed25519、大小/路径/哈希、server trust 和 key pinning gate；本地脚本仍是显式受限而非恶意代码强隔离。任何进一步收紧 Java/FS/network 能力须作为独立安全兼容决策。
  证据：[PackSignatureVerifier.java:15-22,36-121][SandboxPolicy.java:10-25,46-88]

- FileSystem 的 createSymbolicLink 直接拒绝，但 createLink/setAttribute 未见调用 SandboxPolicy；这是静态差异，本报告未运行 Graal，不断言 guest 可利用。


## Script/Plugin surface 与 Probe 声明生成（事实）

- NekoJSPlugin 是 Java 插件作者入口，但 public hook 参数直接引用 core.compiler、core.plugin、probe、wrapper 等实现类型；ScriptCompilersPoint、ProbeBackendsPoint、TypeDocsPoint 反向以它为 collector。
  证据：[common/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java:3-20,22-55,75-134][ScriptCompilersPoint.java:18-36][ProbeBackendsPoint.java:23-48][TypeDocsPoint.java:27-86]

- NekoScriptCatalog.snapshot 汇合 runtime bindings/events/adapters、typeDocs、manual declarations、platform recipe/host-extension/snippet/registry、managed API 与 LegacySurfaceAdapter；它是现有投影，不自动成为全仓唯一事实源。
  证据：[common/src/main/java/com/tkisor/nekojs/api/catalog/NekoScriptCatalog.java:33-74,120-149,154-260,263-302][LegacySurfaceAdapter.java:12-30,33-73]

- managed API contribution 在 bootstrap 时由 verified contract 建 registry 并建立 ApiRuntimeProvider；ApiManifest 是冻结 surface 的观测结果，NormativeApiContract 才是规范输入。
  证据：[NekoPluginBootstrap.java:49-95][common/src/main/java/com/tkisor/nekojs/api/surface/ApiManifest.java:5-23][core/api/ApiManifestGenerator.java:13-68]

- Probe 的真实 seam 是 catalog snapshot -> ProbeCoordinator -> ProbeContext -> ProbeBackend -> ProbeOutputCommitter；coordinator 负责 config、BFS、IR、overrides、artifacts 和 backend 调度，backend 先产出内存文本再统一提交。
  证据：[ProbeCoordinator.java:179-294][ProbeClassCollector.java:22-99][ProbeContext.java:21-84][ProbeBackend.java:12-24,69-127][ProbeOutputCommitter.java:19-40,57-131]

- TypeScript/Python backend 走同一 hook；registry lock 拒绝相同 languageId/name。手写 NekoCommonManualDeclarations、NeoForge type docs、NodeModuleTypeDocs 和 WorkspaceGenerator 仍有并行派生/补充来源，应做域内一致性测试，不应引入全仓声明 DSL。
  证据：[NekoProbeBuiltinPlugin.java:8-31][ProbeBackendRegistry.java:46-93][NekoCommonManualDeclarations.java:8-17,25-185][NodeModuleTypeDocs.java:14-29,36-81][WorkspaceGenerator.java:61-204]

- 推断：catalog snapshot 不是 Graal runtime 注入的唯一事实源；若未来追求更一致的 Script API surface，必须区分 normative contract、反射输出、LEGACY_PREVIEW 和手写补充，不能仅汇合成 DTO 后宣称完成统一。

## 候选 seam 与当前 draft 口径（未裁定）

- 现有证据支持深化三类局部 seam：loader-neutral 的共同装配顺序、compiler/module 的 prepared-module 内部协作、registry/pack 的真实双 Adapter 生命周期。每一类都必须以小 Interface 隐藏深 Implementation，并在迁移后删除旧 ownership。

- RuntimeAssembly、RuntimeKernel、ScriptRuntimeScope、RegistryRegistrationPlan、PackLifecycle 等名称均是 proposal/evidence 的候选设计标签，不是批准的新类或新容器。

- 按 01 票的最新 HITL 方向，逻辑 Module 优先于 Gradle project；暂不新增 Gradle 模块；比较 Root/Kernel 后沿用 NekoRuntimeRoot 已有 composition-root 职责，但允许大改其 Implementation 或 Interface。最终应只有一个 runtime owner，不能叠加 RuntimeKernel/Gateway/Assembly/Scope 多层；入口可由工厂或 static 创建，名称不构成决策。

- NekoRuntimeRoot 的单一 owner 应吸收必要的 runtime-scoped state；普通业务类不应获得宽对象图，也不应通过 magic DI、ServiceLocator 或 capability registry 取依赖。

- registry 动态糖、声明、managed surface 和 probe 不是一个全仓通用 catalog；每个域保留自己的 normative source、derived output 和一致性测试，禁止 general declaration framework。

- Scope/try-with-resources 语法糖历史上没有实际 close 清理语义，不应因重构重新引入。

- 03 已决定保留并收紧 Stonecutter：新业务差异退出 replacements/guard 密集区；退出只有在五节点等价 PoC、节点矩阵、IDE/build/jar/runtime 验证和正式批准齐全后另行重评。


## 路线、数据、版本与决策票边界

- proposal 当前仍是 Draft：推荐“共享 deep runtime Modules + 小 loader/version Adapters”，以低风险切片推进；这与本报告的 evidence 候选一致，不等于已批准实施。

- 路线不批准更换 GraalJS、编译器，删除功能/版本，建立独立 API jar，或把 breaking 扩展成今后随意破坏。任何 public breaking 都须有迁移表、现有功能逐项确认和验收证据。

- 数据验收优先确认格式、路径、读写语义和旧配置保护不变；若确需迁移，按 05/07 的 data inventory、备份或原子替换、旧 fixture 回读和回滚契约执行，不在运行时重构里附赠通用 migration platform。

- 人工确认的模块原则见 [维护者的最小理解范围与目标模块归属如何确定？](../decisions/01-maintainer-module-design.md)；00-07 均已关闭，结论以各票 Resolution 和 [实施交接单](../implementation-handoff.md) 为准，不在本报告复制状态表。

- 01：维护者最小理解范围与逻辑模块归属；02：版本/loader 支持矩阵；03：Stonecutter/build；04：public contract/plugin；05：runtime lifecycle/data；06：language/module pipeline；07：validation/migration。具体需求应落正式票，不反复追问已确认约束。


## 验证边界

- 本轮没有运行完整五节点构建、游戏服务器、Fabric runtime 或 Graal guest；本报告只记录源码、构建配置和已有 evidence 的静态核验。

- 已有 build evidence 记录的成功命令只证明相应项目图、guardLint、common check 或单节点 Fabric check，不等于五节点运行时行为或发布承诺。

- 因此 WORLD pack、McVersionCompat provider、FileSystem 方法差异和 reload 失败路径均保留为可执行验证项，不在本轮替代正式票结论。

- 后续验收应分别覆盖 contract、probe、artifact、runtime smoke、数据保护和维护者试做；每项结果回写对应决策票，不在本 evidence 文件私自结票。

## 结论

- 最高价值事实是：五节点 Stonecutter 图、NeoForge/Fabric 两处装配镜像、compiler/module 互依与静态 pipeline/cache、registry pass/batch 双 Adapter、pack/world 生命周期差异、api 职责与 Graal interop 分类已由 04 闭合，具体实现映射与过时 Graal lint 规则尚待未来实施更新（后两项均按静态边界表述）。

- 最稳妥的候选方向是深化现有 NekoRuntimeRoot 的单一 owner 和已有深 Module，逐步删除 static/镜像旁路；不是创建一套新的容器叠层或全仓注册/声明框架。

- GraalJS、数据路径/格式、受限信任模型和纯 Java 自研优先均是约束；Stonecutter 已决定保留收紧，节点 EOL 仍未批准，独立 API jar 与编译器替换未获批准，功能/版本删减须逐项确认。
