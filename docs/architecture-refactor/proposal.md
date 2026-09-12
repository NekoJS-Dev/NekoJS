# NekoJS 目标架构与迁移建图（规划已完成；非源码实施授权）

状态：规划已完成并可交接；决策 00-10 已关闭，目标方向以各票 Resolution 为准。当前文档收口不构成源码实施授权。

本文件是基于当前源码、CONTEXT.md、ADR-0001 至 ADR-0010 和本目录 evidence 的设计报告。

文首结构原则以已关闭的 [维护者的最小理解范围与目标模块归属如何确定？](decisions/01-maintainer-module-design.md) 为准；目标方向已由决策 01、03、04、07 确定。下文 A/B/C 只保留为历史候选背景，不再要求维护者重新选择；路线和迁移建图现已完成并可交接，但不批准源码修改。

它不批准任何源码修改或测试实现（包括 P0 tests）、Stonecutter 替换、API jar 拆分、数据格式变更、外部转译器引入或支持节点退役。

术语 Module、Interface、Seam、Adapter、Depth、Locality、Leverage 使用 codebase-design 的定义。

设计优先级固定为：维护者、Java 插件作者、JS/TS 脚本作者。

历史候选背景：A 是低风险前置，B 是按运行时域形成 deep modules 的历史推荐，C 是未来新事实触发的物理化备选。三者都不是待选问题；当前目标方向按 [维护者的最小理解范围与目标模块归属如何确定？](decisions/01-maintainer-module-design.md)、[版本与加载器差异如何组织，Stonecutter 何去何从？](decisions/03-platform-build-strategy.md)、[脚本表面与插件作者模型如何只有一个事实源？](decisions/04-public-contract-and-plugin-model.md) 和 [怎样以可验证的阶段完成本次重构并作为新标准？](decisions/07-validation-and-migration.md) 执行。

历史投影为 A -> B；C 不在当前路线，未来只有新事实和另开决策票才评估。

这不是把每个包或 Gradle 项目都抽象成 Module。

目标是让高复杂度隐藏在小 Interface 后，获得维护 Locality 和调用方 Leverage。

GraalJS 固定。

脚本面继续优先 KubeJS 风格便利写法，同时保留高级 Java 访问。

现有 JS/ESM/CJS、TS/JSX/TSX、Python 路径默认继续采用自研、可控的纯 Java Implementation；不默认替换或删除语言。

小且合适的纯 Java 库只能另行评估，必须检查完整语义覆盖、许可证、维护性和体积；10+ MB 量级不适合。当前没有候选库，因此不设脱离候选的精确 byte gate；候选评估由 06 的语义 corpus 和 07 的显式 gate 承担。

本次切换从当前 `1.1.0-preview3` 收敛为 `1.2.0`，作为唯一公开 breaking cutover；该规则优先于 ADR-0009 的历史发布分段，不扩展为今后任意 breaking 授权。

内部 P0-P4 可分阶段迁移和验证，但不能据此再制造公开 breaking。

目标是 cutover 后只保留新标准实现，不留长期 shim、deprecated wrapper 或双运行时路径。

## 1. 证据基线和不可越过的事实

### 1.1 Gradle 项目、节点和逻辑 Module 是不同概念

当前有两个普通引擎/处理器 Gradle 子项目：common 与 common-api-processor。

Stonecutter 同时创建五个版本节点 Gradle 子项目：NeoForge 的 1.21.1、26.1.2、26.2.0，Fabric 的 26.1.2-fabric、26.2.0-fabric。

证据：settings.gradle.kts:34-43。

因此，loader/version 节点确实是 Gradle 子项目。

五个节点都仍在当前范围内；没有批准任一 node 的 EOL。

但它们不是清晰的独立源码、发布或依赖 Module；它们是 Stonecutter 管理的变体节点。

静态 include 只列 common 和 common-api-processor；五个节点由 Stonecutter DSL 创建。

证据：settings.gradle.kts:34-43。

共享 src 通过 guards 和 replacements 按 loader 与 Minecraft 版本求值。

证据：settings.gradle.kts:3-15；stonecutter.gradle.kts:20-68。

active 节点直编共享树，其他节点经预处理生成副本。

证据：stonecutter.gradle.kts:1-11。

Stonecutter 还承担资源模板、metadata、mixin 相关处理和 active-source IDE 语义。

证据：stonecutter.gradle.kts:12-18；buildSrc/src/main/kotlin/nekojs.neoforge-node.gradle.kts:68-102,176-211。

所以“最终清晰”不能自动推出删除 Stonecutter。

### 1.2 common-api 的历史结论仍有效

common 是 Java 21 的跨平台 engine，持有 Graal 依赖；每个 node fat jar 内嵌 common 输出和运行时依赖。

证据：common/build.gradle:1-3,37,59-66；buildSrc/src/main/kotlin/nekojs.neoforge-node.gradle.kts:213-239。

common-api-processor 仍必须独立，因为 annotation processor 不能和被处理代码同次编译。

证据：common-api-processor/build.gradle:1-2。

独立 common-api 已被合并回 common。

原因不是目录偏好，而是没有独立发布或消费收益，却有跨 jar 静态工厂、包拆分和 SwitchMap 风险。

证据：docs/adr/0007-module-boundaries.md:3-15。

因此，所有候选均不预设重拆 API jar。

只有插件 hook 参数先与引擎 Implementation 解耦，且出现真实发布、依赖或独立测试收益时，才重新评估独立 artifact。

证据：docs/adr/0007-module-boundaries.md:12-15。

### 1.3 api 包的职责与 Graal 边界（[脚本表面与插件作者模型如何只有一个事实源？](decisions/04-public-contract-and-plugin-model.md) 已关闭，实施映射由 [怎样以可验证的阶段完成本次重构并作为新标准？](decisions/07-validation-and-migration.md) 承担）

本轮用户已明确：`common-api` 已并入 `common`，维护者便利优先，`common`（包括 `api.*`）允许使用 GraalJS；不为隔离 Graal 而抽 DTO、adapter 或另一个 API jar。该前提已记录于 [脚本表面与插件作者模型如何只有一个事实源？](decisions/04-public-contract-and-plugin-model.md)；该票已关闭，方向不再待选。

`api.*` 仍需按职责区分作者入口与内部实现；portable contract、Graal interop 和 Minecraft/loader-facing 依赖是不同问题，不强制建立 portable/interop 子模块。用户进一步确认：`common`（含 `api.*`）禁止 Minecraft/loader import；允许这些依赖的共享实现放在根 `src/`，不可因逻辑 owner 文字而反向把 MC/loader 类型带入 `common`。该隔离仍由相应的 common isolation/guard 规则约束。

当前代码使用 `graal.graalvm` 前缀，api 包已有相关 import。

证据：common/src/main/java/com/tkisor/nekojs/api/event/ScriptEventBusJS.java:3-5；common/src/main/java/com/tkisor/nekojs/api/JSTypeAdapter.java:5；common/src/main/java/com/tkisor/nekojs/api/event/EventBusJS.java:15-17。

当前 guardLint 仍包含旧的 Graal 禁止检查，且只匹配 `org.graalvm`；源码 lint 尚未在本规划修订中修改。未来代码实施时应移除或更新过时的 Graal 禁令，同时保留 common 的 MC/loader 隔离。

[脚本表面与插件作者模型如何只有一个事实源？](decisions/04-public-contract-and-plugin-model.md) 已决定 api.* 的职责分类、Point/plugin contract、Graal interop 允许范围和当前不拆独立 artifact；实施时仍需把具体 public signature、capability matrix、迁移表和验证映射写入 [怎样以可验证的阶段完成本次重构并作为新标准？](decisions/07-validation-and-migration.md)。独立 artifact 未来若重开，必须有真实消费者、参数类型解耦及发布/依赖/测试收益，不能以 Graal 允许或禁止作为依据。

### 1.4 应保留的深 Module

NekoPluginBootstrap 已集中完成 Point 注册、freeze、Kahn 拓扑、initializer、collector、finisher、产物发布和 fail-fast。

证据：common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginBootstrap.java:103-160,202-287。

NekoPluginExtensionPoint 已定义 merge、enabled、dependsOn、result、handle 和 reload 语义。

证据：common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginExtensionPoint.java:13-56,144-232。

这是高 Depth 的 Module，loader 层不得重新实现其排序、生命周期或错误语义。

当前 Java 插件作者模型以 ADR-0010 为准。

Point 文件是收集语义事实源，NekoJSPlugin 是便利门面投影，显式 XxxPoint.Contributor 与覆写门面等价。

证据：CONTEXT.md:23-25；docs/adr/0010-plugin-authoring-model.md:8-16。

这修订了 ADR-0001 早期仅生命周期接口的形状。

PluginHookPairingTest 已冻结门面、Point 和签名配对。

证据：common/src/test/java/com/tkisor/nekojs/core/plugin/PluginHookPairingTest.java:23-44,55-125。

通用注册表已具备正确的三层：loader Adapter、平台中立 RegistryEventJS、先攒后建 RegistryRepository；含 Minecraft 类型的大树 registry/builder 不因此强搬进 common。

证据：docs/adr/0004-generic-registry-model.md:3-13。

RegistryRepository 已集中 builder、Additional、duplicate fail-fast、drain 和未投递诊断。

证据：src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryRepository.java:14-22,36-90。

### 1.5 已证实的维护摩擦

NeoForge 与 Fabric 各自手写同构 runtime assembly：插件发现、bootstrap、sandbox、module pipeline、runtime root、脚本发现和 STARTUP 加载。

证据：src/main/java/com/tkisor/nekojs/NekoJSMod.java:142-181；versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/NekoJSFabricMod.java:132-175。

这证明共同装配逻辑值得收敛，但不能推出必须新增 RuntimeAssembly 类或另一层运行时模块；函数或工厂即可作为创建入口。

NeoForge RegistryEventAdapter 按 loader pass drain。

FabricRegistryAdapter 在初始化中批量直注。

证据：src/main/java/com/tkisor/nekojs/listener/RegistryEventAdapter.java:27-61,90-129；versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricRegistryAdapter.java:15-85。

这证明 RegistryDrainStrategy 有两个真实 Adapter。

FabricPluginLoader 维护 BUILTIN_PLUGINS 手工清单，并明确要求新增内置插件同步清单。

NeoForge 则扫描注解并委托 common manager。

证据：versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricPluginLoader.java:15-46；src/main/java/com/tkisor/nekojs/core/NeoForgePluginLoader.java:10-22。

NekoJSBasePluginManager 已集中筛选、实例化、去重和确定性排序。

证据：common/src/main/java/com/tkisor/nekojs/core/NekoJSBasePluginManager.java:21-24,42-95,115-136。

RegistryEventJS 的动态 sugar 来自 RegistryInfosPoint 与 RegistryTypesPoint。

NekoRegistryDeclarations 仍手写对应 declaration，且 Fabric runtime type 清单与手写 declaration 已有漂移证据。

证据：src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEventJS.java:19-103；src/main/java/com/tkisor/nekojs/wrapper/registry/gen/NekoRegistryDeclarations.java:8-15,20-97。

NekoRuntimeRoot 已是窄 composition root，但 loader/client 仍公开读取 static RUNTIME_ROOT。

证据：common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java:20-115；src/main/java/com/tkisor/nekojs/NekoJSMod.java:51-53。

### 1.6 固定安全、数据与转译边界

NekoSandboxFactory 按 ScriptType 构造 Graal Context，注入共享 engine、HostAccess、ClassFilter、NekoJSFileSystem、SandboxPolicy 和 Node runtime。

证据：common/src/main/java/com/tkisor/nekojs/core/NekoSandboxFactory.java:99-174。

Java.loadClass 等同 Java.type 已是用户可见高级 Java 路径。

证据：common/src/main/java/com/tkisor/nekojs/core/NekoSandboxFactory.java:146-154。

普通模块整理不得悄悄收紧它。

SandboxPolicy 是 Graal FS 与 Node shim 共用的写删裁决真相，永久保护 nekojs/config 和旧 engine config。

证据：common/src/main/java/com/tkisor/nekojs/core/fs/SandboxPolicy.java:10-25,46-88。

远端 pack 的顺序是验签、路径与落盘安全、哈希复核、信任、激活和 key pinning。

证据：common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSignatureVerifier.java:15-22,36-121；docs/architecture-refactor/evidence/runtime-and-modules.md:227-231。

本地 GLOBAL/WORLD pack 当前不等同于远端验签强制。

证据：common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackRegistry.java:63-176。

纯 Java 编译链是 source -> lexer -> parser -> lowering -> IR/code/source map。

证据：common/src/main/java/com/tkisor/nekojs/core/compiler/NekoCompilationPipeline.java:9-23；common/src/main/java/com/tkisor/nekojs/core/compiler/NekoTypeScriptLanguagePlugin.java:6-32。

现有 compiler golden 已检查 erasure 幂等、行结构和 Graal 可执行性。

证据：common/src/test/java/com/tkisor/nekojs/core/compiler/NekoCompilerGoldenTest.java:13-30,49-100。

## 2. 历史候选共享的设计纪律（背景）

### 2.1 归属与依赖方向

api 承载真实外部契约，也可按 [脚本表面与插件作者模型如何只有一个事实源？](decisions/04-public-contract-and-plugin-model.md) 的职责裁定承载 Graal interop；common 内不设零 Graal 约束。

common（含 api）承载跨 loader engine、Graal、插件 bootstrap、脚本准备、sandbox、pack trust 和 probe；common 的 MC/loader 隔离仍保留，作者入口与内部实现按职责区分。

含 Minecraft ResourceKey 等类型、但 loader 中立的 registry/builder 域可留在现有共享 MC-facing source；B 的逻辑归属不等于强搬进 common。

loader 真实行为停在 Platform Adapter。

无法以小 Adapter 表达且几乎无共享逻辑的版本差异才进入 versions/node Implementation。

这延续 ADR-0007 的低层优先、守卫无利时才下放原则。

证据：docs/adr/0007-module-boundaries.md:17-35。

Plugin Runtime 依赖 api 和 Point Implementation，不依赖 loader。

Script Preparation 依赖纯 Java frontend 与 source-map 表示，不依赖 Graal Context、trust 或 Minecraft。

Script Execution 依赖 PreparedModule、GraalJS、sandbox 和 frozen Plugin Runtime。

Managed Surface 依赖 normative contract、contribution 和 implementation mapping，不反向依赖 Platform Adapter。

Registry Runtime 依赖 metadata、type、repository 和 builder，不依赖 RegisterEvent 或 Fabric callback。

Platform/Version Adapter 依赖这些 Interface；核心 Module 不反向 import Adapter。

### 2.2 只建立真实 Seam

两个真实 Adapter 才建立公开 Seam。

两处共同装配逻辑、PluginDiscoveryAdapter 和 RegistryDrainStrategy 已有 NeoForge/Fabric 两个变化来源。

只有一个 Implementation 的 parser、Point 或 cache 不为可插拔而硬造 Interface。

历史比较中使用过 RuntimeAssembly、RuntimeGateway、RuntimeKernel、Scope 等标签；01 已拒绝把它们叠层为第二个 manager/owner。下文“统一装配逻辑”只表示函数或工厂，返回或持有单一 NekoRuntimeRoot。

每个 Interface 必须写清生命周期、顺序、错误、reload 和性能特征，而不只给 Java 方法签名。

调用者和测试跨同一 Seam。

若测试必须穿过 Interface 才能断言内部细节，说明 Module 过浅或测试目标错误。

### 2.3 一种事实源不等于万能框架

Plugin 收集通道：Point 文件是事实源，NekoJSPlugin 是作者投影。

Managed Script API：facade、data 和 event registration 经 ContractReflector 与 CoreManagedApiBootstrap 形成 NormativeApiContract。

证据：common/src/main/java/com/tkisor/nekojs/core/api/CoreManagedApiBootstrap.java:319-350。

ApiManifest、probe 和 golden 是派生物，不再造第二份 hand-written API database。

证据：common/src/main/java/com/tkisor/nekojs/core/api/ApiManifestGenerator.java:13-19。

Managed contract 与 `LEGACY_PREVIEW` 继续双轨；`NekoScriptCatalog` 与 `LegacySurfaceAdapter` 是投影和兼容转换，不是全仓万能 catalog 或第二规范源。

证据：common/src/main/java/com/tkisor/nekojs/api/catalog/NekoScriptCatalog.java:33-74；common/src/main/java/com/tkisor/nekojs/api/catalog/LegacySurfaceAdapter.java:12-30；最新 [APIs evidence](evidence/apis-and-extensions.md)。

动态 registry sugar：只在 registry 域建立局部 RegistrySurfaceDescriptor，驱动 runtime sugar 和 declaration。

不把它扩展成全仓 declaration DSL 或通用元编程框架。

### 2.4 四任务可用性验收

01 已将新增事件、新增 Adapter、新增扩展点、新增版本固定为维护者试做验收。下表是该裁定在历史候选中的投影，不是实施授权。

| 维护任务 | 入口与域 owner | 事实源与同步 | 依赖与受影响节点 | 试做验收 |
|---|---|---|---|---|
| 新增事件 | Managed Surface 或对应平台事件域；从 entrypoint 和域目录定位 owner | facade/data/event registration -> NormativeApiContract；manifest/probe 为派生物；legacy event 留在 catalog/LEGACY_PREVIEW | entrypoint -> shared construction -> 单一 root 所拥有的 surface/event bridge；平台时机留在 Adapter | 普通新增不改 bootstrap/runtime 下游；contract、golden、probe 与 loader timing parity 可追踪 |
| 新增 Adapter | Registry Runtime 或 Platform/Version Adapter owner；先区分“新增注册类型/Builder”与“新增平台能力”，不把二者混称为同一种 Adapter | 注册类型走 RegistryInfosPoint、RegistryTypesPoint、Builder/RegistrySurfaceDescriptor；平台能力由对应 loader/version Adapter 持有；两真实 Adapter 才建立 seam | entrypoint -> shared construction -> root-owned domain；common 不反向依赖 platform | 注册类型通过 runtime member、声明/Probe parity；平台能力通过 capability matrix、all-node smoke 和物理 source trace；物理 source set/path 按已关闭的 [版本与加载器差异如何组织，Stonecutter 何去何从？](decisions/03-platform-build-strategy.md) |
| 新增扩展点 | Point 所属 domain owner | Point + NekoJSPlugin hook/Contributor + NekoBuiltinPointsPlugin 显式索引 + pairing test；新增收集通道显式登记并配对 | Plugin Runtime 消费 frozen point；不引入全仓 catalog、capability registry 或元框架 | lifecycle、错误、reload、dependsOn 和 pairing 通过；不承诺固定文件数必减少 |
| 新增版本 | support matrix 与 VersionCompat/node owner；Stonecutter node 仍是当前变体事实 | 五个现有节点、guards/replacements、compat Interface 和 node source；非机械差异才进 Implementation | entrypoint -> shared construction -> root-owned modules；不把跨节点借源写成终态，也不默认新增 Gradle 平台模块 | all-node compile/test/artifact/runtime smoke 与 source trace 通过；EOL 需另行正式批准 |

四任务的实际目标是维护者能从入口追到域 owner、事实源、依赖方向、受影响节点和测试；常规新增不应要求修改 bootstrap/runtime 下游。新增扩展点仍可显式登记并配对，不以固定文件数减少为 gate，也不造全仓元框架。

### 2.5 现有功能覆盖账本（迁移前必须闭合）

四类试做只能证明入口可追踪，不能证明现有功能没有被遗漏。下面的账本把当前功能域映射到目标逻辑 Module、迁移动作和验收证据。它是规划覆盖表，不是源码修改许可；逻辑 owner 不自动等于新的目录或 Gradle 项目。任何未归类的功能都阻塞该域的搬迁、删除或公开行为变更。

| 现有功能域 | 当前证据入口（代表性） | 目标逻辑 owner | 迁移动作 | 必须通过的验收 |
|---|---|---|---|---|
| 启动、runtime 生命周期与 reload | `NekoRuntimeRoot`、`ScriptManager`、`NekoJSMod`、`NekoJSFabricMod` | `NekoRuntimeRoot` + Script Execution Environment + platform lifecycle Adapter | 收拢两 loader 的共同创建顺序；移除 static root 旁路；普通 reload 不重启 Plugin Runtime | startup/CLIENT/afterInit、reload/close、失败保留旧 runtime、timer/listener 不双注册的双 loader smoke |
| 插件发现、Point、Handle 与 builtin 清单 | `NekoPluginBootstrap`、`NekoPluginRuntime`、`NekoJSBasePluginManager`、NeoForge/Fabric loader | Plugin Runtime | 保留 Point 事实源、freeze/topology/Handle；平台只提供 discovery input；Fabric 清单先可验证再考虑删除重复 | pairing、dependsOn/排序、freeze、错误归属、builtin discovery parity 和 reload 语义 |
| 编译、模块解析与语言路径 | `NekoCompilationPipeline`、`NekoModulePipeline`、JS/ESM/CJS、TS/JSX/TSX、Python frontend | Script Preparation + Script Module | 显式注入 pipeline/cache；保留语言、module mode、legacy bridge 和 source map，不并行保留第二语义管线 | language corpus、compiler golden、source location、cache invalidation、Graal 执行与错误诊断 |
| 脚本执行、bindings 与高级 Java access | `NekoSandboxFactory`、`ScriptEnvironmentFactory`、`ScriptExecutor`、`java:`/`Java.type` | Script Execution Environment | 集中 Context、HostAccess、ClassFilter、Node shim、binding 和 session 生命周期；不借重构收紧 Java access | SERVER/CLIENT context、信任策略、HostAccess、reload failure、资源释放和 guest smoke |
| managed API、legacy surface、事件公开名与声明 | `CoreManagedApiBootstrap`、`NekoScriptCatalog`、`LegacySurfaceAdapter`、`api.*`、Probe generators | Managed Surface + Probe | normative contract、legacy preview、Graal interop 分开归类；manifest/declaration 只做派生物 | contract/golden、legacy characterization、贡献校验、Probe parity；不得把 legacy 升为 stable |
| 事件总线、事件绑定与事件包装器 | `eventbus`、`bindings/event`、`wrapper/event`、各 loader 的 `*EventBindings` 与 mixin | Managed Surface 的事件面 + platform event Adapter | 共同事件名/载荷进入 surface；原生回调、mixin 和时机留在 loader/version Adapter；不把平台事件实现搬进 common | 事件注册、取消/优先级、脚本时机、server/client 过滤、NeoForge/Fabric capability matrix |
| 配方、数据生成（不含 Assets）、loot、tags 与 recipe viewer | `RecipeLifecycle*`、`RecipeEventJS`、`MinecraftRecipeHandler`、`RecipeManagerMixin`、`DataGeneratorJS`、viewer wrappers | Managed Surface 的 recipe/data 子域 + platform/version Adapter | 保留 recipe namespace/schema/afterRecipes 生命周期；MC 类型和 mixin 留在 `src/` 或节点；不要把每个 recipe helper 升格为新 EP；Assets 按独立小行处理 | recipe schema/type、JSON builder、afterRecipes 时序、tags/loot、生成资源、两 loader artifact 与 runtime smoke |
| Villager Trades | `VillagerTradeManager`、`VillagerTradesJS`、当前静态 binding + 全局 Manager + registry surgery | Managed Surface 的现有 `ServerEvents` 数据/reload 子事件 + 窄 trade overlay Interface + 26.x/1.21.1 platform/version Adapter | 把当前静态 binding、全局 Manager 和 registry surgery 收敛为事件贡献；1.2.0 已确认包含 `add` 与 stable query；query 建议返回只读快照并绑定 generation/stale 校验，不暴露 live registry view；`remove`/`replace`/`modify` 不作为第一版公开；Fabric `unavailable` 必须显式，不静默 no-op | reload/delete cleanup、registry epoch、query 快照/generation、失败回滚、26.x/1.21.1 capability/golden 与跨节点 smoke |
| Dynamic Registry（服务器运行期） | `DynamicRegistryJS`、`DynamicRegistries`、`DynamicRegistryBinding`、Registry Runtime/Adapter | 服务器运行期动态注册事件 facade（工作名，不是冻结 API）+ Registry Runtime/Adapter | 不归入普通启动期 `RegistryEvents.register`；Adapter 负责 claim/stale/cleanup/ID/sync/registry surgery；采用 NekoJS typed Builder，`setX(...)` 与 JavaBean-style property 共用校验/规范化/fingerprint；同步未证明时不得激活混合代际，也不保留不安全 server-only 多人路径；prepare/ack 只表示协议方向，不宣称真实分布式原子提交；Fabric/1.21.1 capability 显式 | 运行期生命周期、claim/stale/cleanup、ID/sync/registry surgery、property/setter parity、fingerprint/conflict、Fabric/1.21.1 capability、golden 与跨节点 smoke |
| [Runtime Item/Block modification](implementation-tickets/39-item-block-modification.md) | `ItemEvents.MODIFICATION`、`BlockEvents.MODIFICATION`、对应 wrapper 与静态 snapshot/restore 路径 | `NekoRuntimeRoot` 或授权 domain owner + platform/version Adapter | 保留既有 modification 事件入口；candidate 只形成 inert 修改计划，commit 点由 Adapter 应用，snapshot/restore 由唯一 owner 管理；详细语义、验收与删除条件以票 39 为准 | modification catalog/golden、candidate 失败保留旧 active、恢复/重放、setter/property parity、客户端可见性、五节点 capability/source-trace 与 smoke |
| 启动期 registry、Builder、声明注册与类型转换 | `RegistryRepository`、`RegistryEventJS`、`RegistryInfosPoint`、`RegistryTypesPoint`、`js/type_adapter` | Registry Runtime + Platform/Version Adapter | 普通启动期声明注册继续走 `RegistryEvents.register`；descriptor 同时驱动 runtime member 与 declaration；注册类型与平台能力分开；MC 类型留在 `src/`/node | duplicate/additional/default、drain 时机、Proxy member/declaration parity、type conversion 和 loader 差异 |
| 客户端脚本、GUI、render、HUD 与 keybind | `client/gui`、`client/render`、`NekoJSClient`、`KeyBindEvents` | Client Script Environment + platform client Adapter + Managed Surface | 保留 CLIENT session、client-only binding、timer flush 和 GUI/render 原生接线；不让服务端加载 client class | dedicated-server exclusion、client startup/tick、keybind/render smoke、workspace/error dashboard 行为 |
| PostEffects | `PostEffectsJS`、`PostEffectManager`、`ClientEvents`、`posteffect` mixin | Managed Surface 的现有 `ClientEvents` 客户端资源/reload 子事件 + runtime binding/Adapter | `register`/`unregister` 作为客户端资源/reload 贡献进入 `ClientEvents`；`set`/`clear`/`toggle`/`current` 等保留 runtime binding；EntitySelectors、Assets 分别按独立小行处理，不再塞入本行 | client-only/reload cleanup、资源生命周期、两 loader smoke；已有 recipe/loot/tags/JEI/capability/goal/render 事件不重复造 |
| 网络、脚本同步、ClientData、PData 与 pack sync | `network/*`、`NetworkJS`、`ScriptSyncService`、`PDataSyncService`、`PackSync*` | platform network Adapter + Pack Trust/Data Protection | 共用 payload/语义和数据保护规则；transport registration、receiver 与 loader codec 留在 adapter；默认不改 wire id/格式 | encode/decode fixture、双向发送、权限、连接生命周期、pack trust、PData key 和两 loader transport smoke |
| 命令、管理入口与权限 | `NekoJSCommands`、`FabricNekoJSCommands`、`CommandEvents` | Managed Surface + platform command Adapter | 命令公开语义由 surface 冻结；dispatcher/permission/注册时机由 loader adapter 持有 | command tree、权限拒绝、server lifecycle、两 loader registration smoke |
| sandbox、config、pack trust、cache 与持久化数据 | `SandboxPolicy`、`SandboxConfig`、`ScriptPackRegistry`、`PackSyncTrustStore`、`PersistentDataJS`、pdata mixin | Pack Trust + Data Protection + Script Execution Environment | 保持路径、key、格式、默认启用与信任决策；cache/probe 仅在可重建证据充分时重建；必要 migration 必须可回滚 | path traversal、trust/signature、config/world/pdata fixture、备份/原子替换/旧数据回读和失败回滚 |
| 错误、诊断、telemetry、workspace 与用户可见报告 | `ErrorSummaryDTO`、`ScriptErrorReporter`、`NekoRuntimeRoot.errors`、`NekoErrorDashboardScreen`、watchdog | Execution Environment diagnostics + Probe + client UI Adapter | 统一错误上下文、source map、reload boundary 和诊断 ID；GUI/packet 是投影，不成为第二错误事实源 | syntax/runtime/reload/trust errors、源位置、日志历史、网络传输和 dashboard 观测一致性 |
| 数据映射查询 | `DataMapJS`、`NekoJSCorePlugin` 的 `DataMap` 注册 | Registry Runtime 的查询面 + NeoForge Platform Adapter | 保留只读 query binding，不事件化；NeoForge data map/MC 类型留在 `src/`；当前入口是既有 binding，不得因存在就静默升为 managed stable | `furnaceFuel`/`compostable` 命中与缺失、节点 capability、Probe/declaration parity；删除前提是替代查询面覆盖且无调用者 |
| 自定义事件声明 | `ScriptEvents`、`ScriptEventsJS`、`ScriptEventRegistry` | Managed Surface 的事件声明面 | 保留声明式事件面；common 无 MC/loader；动态事件组、注册冲突、reload 清理和声明生成必须由同一 catalog/contract 派生，不造第二注册路径 | 注册/冲突/side/reload 清理、runtime member 与 TS/Python declaration parity；删除前提是动态事件面无调用者且迁移表完成 |
| 原生事件桥与 Probe 事件 | `NativeEventsJS`、`ProbeEvents`、`ProbeIrBuilder`/probe backends | platform event Adapter（`src/`/node）+ Probe owner（common） | NativeEvents 是 legacy/raw adapter，不静默升 managed；ProbeEvents 是 Probe 扩展面，不是通用运行时事件；两者分别记录 capability 与声明来源 | STARTUP reload listener cleanup、原生事件优先级/取消、Probe 事件到真实 catalog/declaration golden；删除前提是替代 bridge/probe 路径通过且无调用者 |
| 实体选择器工具 | `EntitySelectorsJS`、`EntitySelectorBuilderJS`、`EntitySelectorsPlugin` | SERVER/TEST 查询 binding + 平台/版本 query Adapter | factory/query 保持 binding，不事件化；MC selector 类型留 `src/`；不得因名称相似升为 managed event | builder/query 语义、server/test side、非法 selector/level 错误、两 loader capability；删除前提是替代 query 面覆盖且无调用者 |
| 客户端资源生成 | `AssetGeneratorJS`、`ClientEvents.generateAssets`、`DataGeneratorJS` | Client Script Environment + client resource Adapter | 复用既有 `ClientEvents.generateAssets`，不新增 Assets 事件；资源写入/路径/重载由 client Adapter 持有，MC 类型留 `src/` | 资产路径/JSON/资源重载、client-only 过滤、无重复事件、Fabric/NeoForge capability；删除前提是现有事件替代并通过生成/回读 fixture |
| [跨 reload 的 global 共享状态如何参与候选事务？](decisions/10-shared-global-candidate-writes.md) | `NekoGlobal`、`ScriptEnvironmentFactory` 的 binding 注入 | NekoRuntimeRoot + Script Execution Environment 的状态所有权与候选视图；完整语义只以票内 Resolution 为准 | 决策 10 已闭合；本表只保留必要 owner、fixture、迁移与删除条件，不重复 Resolution | 票内 fixture、迁移示例与两 loader 绑定验证通过，旧静态 Map/直接注入旁路无剩余调用者后才删除；不保留双写 shim；当前未验收 |
| 构建、版本兼容、资源、mixin 与五节点产物 | Stonecutter、`McVersionCompat`、`McClientCompat`、各 node source/resource | Platform/Version Adapter + Stonecutter | common 禁止 MC/loader；`src/` 承载共享 MC-facing 实现；不可由 guard 表达的差异下放 node；Stonecutter 暂保留并收紧 | 五 node compile/check、artifact/metadata/mixin、source trace、runtime smoke、IDE/source bridge 可追溯 |

搬运功能适配规则：只有具备明确生命周期、多个脚本/插件贡献、注册/reload/事务提交语义的面，才进入 Managed Surface 事件面；工厂、查询、运行时命令、发送操作继续作为 binding/Adapter。普通启动期 `RegistryEvents.register` 与服务器运行期 Dynamic Registry 的生命周期不同，不能合并成第二条注册路径；不造万能 Event Module、第二 registry path，也不为没有真实生命周期的功能制造 Point。Villager Trades、Dynamic Registry 与 PostEffects 的归属和事件工作名仍是规划投影，不冻结最终公开事件名、payload、API signature 或 Fabric parity。

上述新增小行各有独立 owner：DataMap 是 query binding，ScriptEvents 是声明事件面，NativeEvents/ProbeEvents 分别属于 raw Adapter 与 Probe，EntitySelectors 是 factory/query，Assets 复用既有资源事件。它们不得互相混入，尤其不得塞进 PostEffects 行。现状静态 binding/event 只作 characterization 与迁移输入，不等于目标公开契约；既有 spec（`NormativeApiContract`/managed surface）与 legacy/raw/预览面不得静默升级为 managed。Query/Tools（DataMap、EntitySelectors、Network send、PostEffects `set`/`clear`/`toggle`/`current`）继续作为 binding/Adapter，不事件化；已有 recipe/loot/tags/JEI/capability/goal/render 事件不重复造。每个域必须逐节点记录 `supported`/`partial`/`unavailable`；删除前提是对应 runtime/declaration/smoke/source-trace gate 通过，且旧路径无消费者并获准删除。公开功能删除仍须维护者确认。

#### 2.5.1 W5-W7 现存测试与拟建 contract/golden gate

下表把现状当作基线/characterization，不把“现存实现不等于目标”当作计划错误；只有把现状直接写成目标契约才是错误。

| 范围 | 现存测试（基线） | 缺少覆盖 | 拟建 contract/golden gate 与产物（来源/节点条件） |
|---|---|---|---|
| Villager Trades | `VillagerTradesJS` 静态 `add`/`pendingCount`；无事件与 query golden | 事件名/payload、add 配置规范化、stable query 快照与 generation/stale、reload epoch/失败回滚 | `ServerEvents` 数据/reload 事件 golden、只读 query fixture、Manager/Adapter source trace；NeoForge 26.x/1.21.1，Fabric capability 按实现/source trace 记为 `unavailable` |
| Dynamic Registry | `DynamicRegistryBuilderTest` 链式/default/error；`DynamicRegistrationBookkeepingTest` claim/stale | 事件 facade/payload、property 与 setter 同规范化/fingerprint、冲突、prepare/ack/commit、ID/sync/stale/retired、混合代际门禁 | contract/golden + transaction/reload/delete-cleanup + capability/smoke；当前仅 NeoForge 26.x，Fabric/1.21.1 capability 按实现/source trace 标 `partial`/`unavailable`；prepare/ack 不宣称分布式原子提交，property characterization 仅证明本机行为 |
| PostEffects | `PostEffectChainJsonTest` 与 Manager 局部测试；静态 binding 无事件 golden | `ClientEvents` 子事件名/payload、register/unregister reload cleanup、运行时 options 声明 | client event golden、资源 reload/cleanup、TS/Python declaration parity；NeoForge client，Fabric capability 按 P2/source trace 记为 `unavailable` |
| recipe/loot/tags/JEI/capability/goal/render/network/PData/diagnostics | 各域局部单测与合成 probe fixture；无真实公开表面 golden | 事件名、payload 成员/类型、side/dispatch/cancel、时机、reload cleanup、wire/PD key/revision、诊断字段与 dashboard/packet 投影 | 真实 catalog snapshot → event/binding TS/Python golden → runtime member/declaration parity + capability matrix/source trace；NeoForge primary/secondary/1.21.1，Fabric 逐节点显式 capability；JEI 仅在安装时生效，capability API 为 NeoForge 条件 |
| DataMap/ScriptEvents/NativeEvents/ProbeEvents/EntitySelectors/Assets | 现有 binding/event 代码与零散测试；多数无独立 golden | 独立 owner 分类、spec/legacy 边界、capability、真实 declaration 与查询/资源 fixture | 按本表新增小行逐项建立 contract/golden；common 与 `src/`/node 的 source trace 分开；不新增事件，不复用 PostEffects owner |

> `global` 的权威语义只记录在 [跨 reload 的 global 共享状态如何参与候选事务？](decisions/10-shared-global-candidate-writes.md) 的 Resolution；本覆盖账本只引用该票，并在实施时记录必要 owner、fixture、迁移与删除条件，不重复问题或裁定，也不把计划写成测试已通过。缺少 API 测试/golden 只表示 contract coverage 尚未闭合，不能据此判定节点 capability 为 `unavailable`；capability 必须由真实实现、source trace、runtime smoke 或明确不支持证据决定。

覆盖表的验收顺序是：先确认每行的当前 owner 和能力差异，再迁移一个垂直域，最后删除无调用者的旧路径。它不允许用“逻辑上属于 surface”掩盖 recipe、client、network、diagnostics 等真实平台接线；这些必须各自有 Adapter、source trace 和 smoke 证据。

## 3. 历史候选 A（背景）：保留 Stonecutter，收紧现有架构

### 3.1 目标与目录树

历史候选 A 只作背景，不改变当前 node 图、GraalJS、转译器、API jar、数据格式或远端 trust 策略。

它优先消除 runtime assembly 复制、Fabric 漏登记风险和 registry declaration 漂移。

适合 P0/P1，也是任何终态的低风险入口。

~~~text
common/
  api/                         外部契约与作者入口；Graal 可用，零 MC/loader import；具体职责由已关闭的 [脚本表面与插件作者模型如何只有一个事实源？](decisions/04-public-contract-and-plugin-model.md) 明确
  core/plugin/                 Point、bootstrap、frozen runtime
  core/compiler/ + core/module/  纯 Java preparation 与模块加载
  core/fs/ + core/pack/        sandbox、路径、pack trust
common-api-processor/          必需独立处理器
src/main/java/
  core/                        NeoForge 入口与 platform 接线
  listener/                    NeoForge registry drain Adapter
  wrapper/registry/gen/        loader 中立 registry/builder
  platform/compat/             版本 Compatibility Adapter
versions/node/src/main/
  ...                          node-specific Implementation
stonecutter.gradle.kts         变体、replacement、guardLint
~~~

这只是归属 tree，不创建新的 Gradle 项目。

### 3.2 关键 Interface、Seam 与 Adapter

保留 NekoPluginExtensionPoint、NekoPluginBootstrap、NekoPluginRuntime 的现有深 Module。

不新增名为 RuntimeAssembly 的内部 Module。统一装配逻辑（函数或工厂足够）接收 platform 提供的 discovery/lifecycle services，返回或持有单一 NekoRuntimeRoot。

它只收拢两 loader 已重复的装配顺序。

它不吞掉 client、network、command、registry lifecycle 等真实 loader 差异。

PluginDiscoveryAdapter 的 Interface 是提供候选插件类与 owner metadata。

NeoForge annotation scan 与 Fabric entrypoint/catalog 是两个 Adapter。

Fabric 的手写清单在本候选中先不自动生成。

先将其变为可审阅、可失败的 catalog：测试比较最终 discovered builtin 集合与预期集合，漏项即红。

这避免 ADR-0003 已拒绝的第二注册通道和静默漏配风险。

证据：docs/adr/0003-builtin-extension-point-registration.md:9-12。

Registry 保持现有 Point、RegistryEventJS 和 Repository。

A 只补 runtime member directory 与 Probe declaration 的跨 loader parity test。

### 3.3 依赖、登记同步与 Stonecutter

依赖方向是 Platform entrypoint -> PlatformRuntimeAdapter -> shared construction -> 单一 NekoRuntimeRoot 所拥有的 common runtime modules。

发现方向是 PluginDiscoveryAdapter -> NekoJSBasePluginManager -> NekoPluginBootstrap。

Point 仍只经 NekoPluginExtensionRegistry 注册，不建立 loader 私有 Point 表。

NekoBuiltinPointsPlugin 的显式总索引保留；它是 ADR-0003 要求的可发现登记，不是冗余。

证据：docs/adr/0003-builtin-extension-point-registration.md:5-8。

Stonecutter 原样保留。

它继续执行版本求值、机械 rename replacement 和 guardLint。

新业务不增加内联 guard；版本差异优先进 existing compat facade 或 node Implementation。

证据：docs/adr/0008-guard-discipline.md:20-25。

### 3.4 真相源、清理、风险和阶段

Script API 仍以 Java contract/reflection 为真相。

Plugin API 仍以 Point 为真相。

动态 registry declaration 暂仍为手写派生物，但必须受 parity test 约束。

合并目标：NeoForge/Fabric 的共同装配逻辑，及已证实相同的 builtin contribution。

保留：Point、processor、Stonecutter、guardLint、existing golden、sandbox、trust 实现。

条件删除：Fabric BUILTIN_PLUGINS 重复副本，仅在 catalog/discovery test 通过后。

条件删除：重复 runtime assembly，仅在 lifecycle parity 通过后。

风险：统一装配逻辑变为万能 service locator 或第二 runtime owner。

缓解：严格只提取两处已有同构步骤，平台回调继续留 Adapter。

A 的不足：无法长期解决 script/runtime 混杂和 registry 双事实。

因此 A 不是推荐终态。

## 4. 历史候选 B（背景）：按运行时域形成 deep modules

### 4.1 目标目录树

~~~text
common/src/main/java/com/tkisor/nekojs/
  api/                                  外部契约与作者入口；Graal 可用、零 MC/loader import；具体职责由 [脚本表面与插件作者模型如何只有一个事实源？](decisions/04-public-contract-and-plugin-model.md) 裁定
  runtime/
    plugin/                             discovery input、Point bootstrap、frozen products
    script/
      preparation/                      source -> NekoPreparedModule
      environment/                      Graal Context、sandbox、bindings
      module/                           require/ESM/link/cache/invalidate
      execution/                        ScriptManager 生命周期
    surface/                            normative contract、globals、manifest
    pack/                               local discovery、remote trust gate、cache
    probe/                              可再生 workspace/declaration 输出
  platform/spi/                         仅真实跨 loader 的小 Interface
src/main/java/com/tkisor/nekojs/
  runtime/registry/                     metadata、type、repository、builder、descriptor（逻辑归属；含 MC 类型仍留 MC-facing source）
  platform/neoforge/                    NeoForge Adapter
  platform/fabric/                      Fabric Adapter 逻辑归属（物理 source set/path 已由 [版本与加载器差异如何组织，Stonecutter 何去何从？](decisions/03-platform-build-strategy.md) 裁定）
  platform/compat/                      version Adapter
versions/node/src/main/java/
  platform/version/                     低共享 node Implementation
~~~

以下是历史逻辑归属示意，不是新增物理 source set/path 承诺；Fabric 从版本节点上移的位置已由 [版本与加载器差异如何组织，Stonecutter 何去何从？](decisions/03-platform-build-strategy.md) 裁定。

暂不新增 Gradle 项目；runtime 子目录不自动各自成为 Gradle 项目。C 不在当前路线；未来只有新事实且另开决策票才评估。

含 Minecraft 类型的 registry 域保留 MC-facing source，不能为目录整齐而强搬 common 或破坏现有 isolation。

### 4.2 Plugin Runtime Module

Interface：输入已发现的 OwnedPlugin、script properties、contract contribution 和环境；输出 frozen IPluginRuntime 与可诊断 bootstrap result。

Implementation：保留 Point topology、merge、handle、finish、sealed accumulator、reload 语义。

调用者不读取原始 pointId 到 Object Map。

NekoPluginRuntime 的类型化投影保留为调用者 Leverage。

证据：common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginRuntime.java:40-66。

NekoJSPlugin 保持 Java 作者最短路径。

Contributor 保持显式路径。

新增收集通道继续要求 Point、门面、内置索引和 pairing test 的明确同次变更。

### 4.3 Script Preparation Module

Interface：输入 Path、source、extension、requested mode；输出不可变 NekoPreparedModule 或带 source location 的编译错误。

输出必须保留 code、source map、language id、ESM/CJS mode 和 top-level-await 等语义。

Implementation 包含现有 JS/ESM/CJS、TS/JSX/TSX、Python frontend、IR lowering、legacy bridge、cache key 与静态分析；这些语言默认不替换或删除。

它不创建 Graal Context，不读 SandboxConfig，不决定 host class 可见性。

NekoModulePipeline 已是消费点。

其 legacy static instance 和 process-wide cache 是迁移时改为显式注入的旁路。

证据：common/src/main/java/com/tkisor/nekojs/core/module/NekoModulePipeline.java:30-49,51-129,137-150；common/src/main/java/com/tkisor/nekojs/core/module/NekoModulePipelineCache.java:30-68。

ScriptCompilerRegistry 的 last-wins/replace 语义必须写进这个 Module Interface，因为它会受 plugin 排序影响。

证据：common/src/main/java/com/tkisor/nekojs/core/compiler/ScriptCompilerRegistry.java:105-125。

### 4.4 Script Execution Environment Module

Interface：按 ScriptType create、evaluate、reload、close 环境；输入 PreparedModule 与 frozen runtime；candidate/active、generation、owner thread、错误阶段和资源释放语义按决策 09 约束。

Implementation：Graal Context、Node shim、events、plugin binding、managed global、schema、telemetry、watchdog 和 sandbox。

ScriptManager 保留 discover/load/reload/close 的顶层协调。

调用者不再直接触摸 Context mapping。

NekoSandboxFactory 与 ScriptEnvironmentFactory 是 Module 内部协作点，不必为了测试另造框架。

证据：common/src/main/java/com/tkisor/nekojs/core/NekoSandboxFactory.java:99-174；common/src/main/java/com/tkisor/nekojs/script/ScriptEnvironmentFactory.java:31-96。

高级 Java access 是明确能力，不在该重构中收紧。`candidate/active/generation`、state ownership、线程/重入和 watchdog 的规范性契约见决策 09；本节不复制其状态表。

### 4.5 Managed Surface Module

Interface：从 verified NormativeApiContract、环境、contribution 和 implementation mapping 产生 frozen script-visible surface；再派生 manifest 与 probe 输入。

事实源是 facade/data/event registration 与 contract reflection，不是 JSON 或 d.ts。

CoreManagedApiBootstrap 明确不读 JSON，并反射 facade/data/event registration 构建 contract。

证据：common/src/main/java/com/tkisor/nekojs/core/api/CoreManagedApiBootstrap.java:319-350。

ApiManifest 是 frozen surface 的确定性派生产物。

证据：common/src/main/java/com/tkisor/nekojs/core/api/ApiManifestGenerator.java:13-19,31-66。

ApiManifestGoldenTest 继续冻结有意承诺的 symbol/signature/capability/module。

它不冻结内部 package、helper、cache 或对象身份。

证据：common/src/test/java/com/tkisor/nekojs/core/api/ApiManifestGoldenTest.java:24-54。

### 4.6 Registry Runtime Module

Interface：收集 builder、解析 type/default、处理 Additional、按 registry 提供 drain 与未投递诊断。

Repository 继续拥有 duplicate fail-fast 与队列语义。

RegistryInfosPoint 继续拥有 metadata 发现和插件追加。

RegistryTypesPoint 继续拥有 factory、default 与 overrideWarn。

证据：src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryInfosPoint.java:22-107；src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryTypesPoint.java:19-118。

RegistryEventJS 保持 RegistryEvents.register、event registry sugar、custom 和裸 register 高级入口。

证据：src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEventJS.java:24-38,128-215。

RegistryDrainStrategy 是真实 Seam。

NeoForge Adapter 维持目标 registry 自己的 pass 注册。

Fabric Adapter 维持单批直注和其后置语义。

Repository 不知道 RegisterEvent、Fabric callback 或 PASSED set。

为解决动态 sugar 与 declaration 漂移，只建立局部 RegistrySurfaceDescriptor。

它表达 registry key、sugar name、type/default、builder declaration 片段和平台 availability。

RegistryTypesPoint、runtime member directory 和 registry declaration renderer 消费同一 descriptor。

它不扩展为全仓 declaration DSL。

含 Minecraft 类型的“大树” registry/builder 不强搬 common；B 只表达历史逻辑 owner。NeoForge/Fabric 两个 Adapter 对称保留各自真实时机和 Implementation，物理 source set/path 按已关闭的 [版本与加载器差异如何组织，Stonecutter 何去何从？](decisions/03-platform-build-strategy.md) 执行。

### 4.7 Pack Trust、Runtime Root、登记同步

Pack Trust Interface：远端内容在执行前经历验证、落盘复核、信任和激活，返回带理由的接受或拒绝结果。

本地 pack discovery 与远端 trust gate 保持不同 Module。

NekoRuntimeRoot 保留为深 composition root 和唯一 runtime owner。

统一装配逻辑（函数或工厂足够）只负责共同创建并返回或持有该 root；不新增 RuntimeGateway、第二 manager 或 Scope。逐步移除外部读取 static root。

新增 builtin plugin：由对应域的可验证显式索引供 loader Adapter 消费；第三方发现仍是 loader-specific Adapter。

新增共同 binding：进入对应 runtime domain 的事实源；平台只追加真实 loader-specific 贡献。

新增 registry type：改 registry descriptor 与 Builder，使 runtime sugar 和 Probe declaration 同时派生。

新增 managed API：改 facade/contract 与 implementation mapping；manifest/probe 不手写第二份规范。

### 4.8 依赖图、Stonecutter、清理和风险

~~~text
Platform entrypoint
  -> platform lifecycle/event Adapter
  -> shared construction (function/factory)
  -> one NekoRuntimeRoot owner
       -> Plugin Runtime
       -> Script Preparation
       -> Script Execution Environment
       -> Managed Surface
       -> Registry Runtime
       -> Pack Trust
Platform Registry Adapter
  -> root-owned Registry Runtime delivery
Version Adapter
  -> root-owned compatibility/capability only
~~~

核心 runtime 不反向依赖 NeoForge/Fabric Implementation。

Stonecutter 保留，但新业务 guard 退出 runtime domain，集中到 platform/version Adapter。

统一：两 loader 的共同装配逻辑、共同 builtin contribution、registry sugar 与 declaration 的实证重复。

条件删除：NekoModulePipeline legacy bridge、public static RUNTIME_ROOT、Fabric builtin 副本、已由 descriptor 派生的 declaration 片段。

每项删除前必须通过 explicit injection/reload、lifecycle parity、discovery parity 或 declaration parity。

主要风险：Module 过宽、static 生命周期迁移改变 reload、descriptor 过度泛化。

缓解：以 existing two-adapter Seam 为边界；测试跨外部 Interface，不冻结内部实现。

B 是历史比较中的终态主体背景；当前目标方向已由决策 01/03/04/07 确定，不再重开 A/B/C 选择。

## 5. 历史候选 C（背景）：显式 loader/version Module 的混合路线

### 5.1 目标目录树

~~~text
common                              跨平台 engine、api、GraalJS、纯 Java frontend
common-api-processor                必需处理器
platform-neoforge                   稳定的 NeoForge Adapter
platform-fabric                     稳定的 Fabric Adapter
mc-shared                           跨 loader MC-facing registry/wrapper
mc-retained-era                     仅真实、可测试的 version Adapter
node-mc-loader                      artifact/metadata/mixin 汇编
versions/node                       低共享 node-specific Implementation
buildSrc + Stonecutter              变体、资源、验证、artifact convention
~~~

这是历史候选 C 的混合示意，不是当前路线，也不是 B 之后的必经阶段。

C 不在当前路线；未来只有出现实际发布、依赖或独立测试收益等新事实，并另开决策票时，才评估这些物理 Module/Gradle 结构。

完整 loader x version x runtime-domain 矩阵会增加 source set、metadata、mixin、fat-jar、processor 与 runtime library 组合成本。

Fabric 26.2 当前复用 26.1.2-fabric source bridge；这是当前借源事实，不是终态 source set。Fabric 从版本节点上移的具体 source set/物理路径按已关闭的 [版本与加载器差异如何组织，Stonecutter 何去何从？](decisions/03-platform-build-strategy.md) 执行，不把跨节点借源写成终态。

证据：versions/26.2.0-fabric/gradle.properties:1-6。

### 5.2 既有 Seam、依赖和登记

IPlatform/Platform 已提供 loader environment、version、capability、registry 与 NBT 的跨 loader Interface。

证据：common/src/main/java/com/tkisor/nekojs/platform/IPlatform.java:11-109；common/src/main/java/com/tkisor/nekojs/platform/Platform.java:11-35。

McVersionCompat 与 McClientCompat 已用 ServiceLoader 承担非机械 Minecraft drift。

证据：src/main/java/com/tkisor/nekojs/platform/compat/McVersionCompat.java:8-27；src/main/java/com/tkisor/nekojs/platform/compat/McClientCompat.java:11-60。

C 应固化这些真实 Seam，而不是从零再造 abstraction。

该候选中的两个平台源码区域通过共同创建入口持有单一运行时 root，并按需使用 IPlatform、插件发现和注册表投递 Interface；创建入口可以是函数或工厂，平台源码区域不自动成为新增 Gradle 子项目。

common 不依赖任何 platform artifact。

version Module 只实现 compat Interface，不承载完整 Script Runtime 或 Plugin Runtime。

若 C 未来获批，loader catalog、registry drain、network/event registration 才分别在对应 platform Module 归属一个 Implementation；当前不据此默认新增 Gradle 平台模块。

Plugin Runtime 仍是共同事实源。

### 5.3 Stonecutter 的保留、收紧和退出门槛

保留：Stonecutter 是 node variant evaluator、replacement engine、资源处理器和 guardLint 载体。

收紧：loader 业务差异退出 shared business code；Stonecutter 只处理变体选择、机械 rename 与全 node 验证。

退出：只有替代构建被证据证明等价或更好，并另开决策票时才评估。

退出门槛必须同时满足：

- 覆盖当前五个 Stonecutter node，而非只演示一个样例节点。
- 等价产出 NeoForge/Fabric metadata、mixin/access 配置、common 内嵌、runtime library 与 fat jar 布局。
- 等价或改进 processor/platform option 行为，不能静默掩盖 Fabric 尚未接 processor 的事实。
- 证据：buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts:280-285。
- 每 node 通过 compile、test、artifact verify、guard 等价检查和 runtime smoke。
- 每版本源可追溯到唯一来源，不混入陈旧 generated tree。
- 经一次真实新 node 或 loader 差异接入演练，证明维护步骤、IDE 体验和改动面优于 Stonecutter。

没有这些证据时，清晰目标只支持收紧 Stonecutter，不支持删除它。

### 5.4 清理、风险和阶段

条件合并：platform Adapter 稳定后，把仅为 loader guard 存在的整文件移入 platform Module。

条件删除：被显式 platform/version Module 替换的 guards，在全 node artifact 和 behavior 验收后删除。

保留：shared business code、common、processor、纯 rename replacement、真实 VersionCompat。

主要风险：构建复杂度替代 guard 复杂度、version Module 乘法、Fabric bridge 被错误固化、以及重演 common-api 无收益拆分。

C 不属于当前路线；未来只有出现实际发布、依赖或独立测试收益等新事实，并另开决策票时，才评估物理化。

## 6. 历史候选比较（背景）

| 维度 | A：保留并收紧 | B：运行时域 deep Module | C：显式 loader/version 混合 |
|---|---|---|---|
| 维护者 Locality | 预计有限改善 | 预计较集中改善，需四任务试做验证 | 取决于未来收益证据 |
| Java 插件作者 | 保持双形态 | Point 事实源和错误更集中 | 不应改变 Interface |
| JS/TS 作者 | 主要保持 | sugar/typing 漂移可消除 | 不直接改善体验 |
| Graal/转译器 | 原样保留 | 成为清晰 deep Module | 不应成为构建拆分副作用 |
| Stonecutter | 保留 | 保留并收紧 guard 位置 | 历史背景；退出须另开决策 |
| 登记同步 | Fabric 清单先核验 | catalog/descriptor 有望收拢实证双源，需 parity | 未来物理化后再评估 |
| 数据风险 | 最低 | 低，默认不改格式 | 中，需要更强 artifact/path 验证 |
| 主要风险 | 只整理不深化 | Module 过宽、static 回归 | 矩阵膨胀、构建复杂度 |
| 历史阶段 | P0/P1 背景 | P1-P3 历史建议 | 不在当前路线 |

历史比较中 B 的理由不是目录更漂亮。

预计它会让维护者主要学习少数高 Depth Interface，而不同时理解 loader、版本、Graal、plugin registry、registry pass、declaration 和 source bridge；该判断必须由四任务试做验证。

历史 A 预计降低进入 B 的迁移风险。

C 不作为当前建议；未来只有实际 dependency graph 证明物理化有净收益且另开决策票时才评估。

## 7. 一次性标准化、数据保护和分阶段迁移

### 7.1 公开 contract 和 clean cutover

脚本 registry 面沿用 ADR-0006：RegistryEvents.register、动态 sugar、public field Builder、void 动词。

旧类型化入口和链式兼容包装不永久保留。

证据：docs/adr/0006-script-registry-api-clean-switch.md:3-12。

Java 插件面沿用 ADR-0010：Point 事实源与 NekoJSPlugin 双形态，不保留 V1 shim。

证据：docs/adr/0010-plugin-authoring-model.md:10-16。

用户最新要求覆盖 ADR-0009 的旧公开发布分段：本轮加 0.1 是唯一 public breaking cutover。

P0-P4 是实现与验收阶段，不表示各自另行发 public breaking。

### 7.2 数据保护默认规则

不可再生或需明确保护：world 内容、实体/玩家 pdata、用户脚本与 script pack、信任决策、日志（历史诊断记录，不可再生）。

Fabric pdata 使用 NekoJSPersistentData 子键，并明确追求跨 loader 不丢失。

证据：versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/mixin/NekoEntityPDataMixin.java:16-22,54-65。

信任决策保存在 nekojs/config/trusted-servers.json；bucket 和 key pinning 格式默认保持。

证据：common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncTrustStore.java:15-33,65-116。

可再生候选仅包括 probe 输出和 module cache；远端 cache 副本只有在源仍可重新获取、且有明确重建证据时才可重建。生成的 workspace config/declaration 可能有人编辑，不能一概覆盖。

普通 Module 重排默认不改变数据、格式、路径、读写语义、默认启用规则、pack manifest 或 sandbox file policy。

普通重构不附赠通用 migration 平台。

05 已裁定：普通 reload 只重建脚本环境与模块 session，不重新 bootstrap Plugin Runtime，也不重复注册平台事件、registry 或 network。候选环境准备或执行失败时，丢弃候选并保留当前 active runtime/state，报告带 source location 的失败；只有候选完整通过后才切换，旧环境随后按所有权顺序释放。该保留只覆盖 NekoJS 所拥有的 runtime 资源，不承诺撤销脚本经 Java、网络、世界或其他外部对象造成的副作用。插件实现或平台注册的变化需要显式重启/重新装配契约，不借普通 reload 偷渡。

只有必须改持久格式时，才新增该格式专用 migration：先备份或原子替换、验证可读、定义回滚，并用旧数据 fixture 测试。

### 7.3 P0：待授权的护栏和对照测试

用户已明确取消 api.* 零 Graal 约束。未来代码实施时移除或更新过时的 Graal 禁令；本规划输入不修改源码 lint，保留 common 的 MC/loader 隔离并由 guardLint/checkCommonIsolation 继续覆盖。

建立 NeoForge/Fabric runtime assembly 对照测试，覆盖 plugin bootstrap、ScriptType 加载、STARTUP、CLIENT 和 afterInit 的可观察语义。

建立 Fabric builtin catalog 漏项测试。

建立 registry runtime member directory 与 Probe declaration 的跨 loader parity test。

建立 Script Preparation corpus，覆盖 JS/ESM/CJS、TS/JSX/TSX、Python、legacy CJS 旁路、语法、source map、错误位置、module mode 和 Graal 执行。

建立 pdata、trust-store、pack cache、SandboxPolicy 不变性 fixture。

退出门槛：不改 public API；当前 node existing check 与新增行为测试全绿。

### 7.4 P1：历史候选 A 的收紧背景

以既有 NekoRuntimeRoot 为唯一 owner，提取统一装配逻辑（函数或工厂足够），入口只保留真实 platform 接线。

让 discovery 进入明确 Adapter，先核验 Fabric catalog，不先引入第二发现机制。

收拢已证实共同 builtin contribution，禁止创建长期第三条登记路径。

退出门槛：两 loader runtime lifecycle、reload、plugin order 和 registry smoke 符合共同契约与显式差异。

### 7.5 P2：历史候选 B 的深化背景

逐一收拢 Script Preparation、Execution Environment、Managed Surface、Registry Runtime、Pack Trust 的所有权。

每迁移一个 Module，删除无调用者的重复装配或 static 旁路；不层叠旧 facade 与新 facade。

registry descriptor 先通过 runtime/declaration parity，再删重复手写片段。

退出门槛：调用者只跨小 Interface；测试可替换真实 Seam；无意 public surface drift 被 golden 拦截。

### 7.6 P3：功能域与平台能力逐行验证

按 [怎样以可验证的阶段完成本次重构并作为新标准？](decisions/07-validation-and-migration.md) 的 Resolution，逐域迁移并验证事件、recipe、client/UI/render、network/PData、command、diagnostics 和 pack trust。

每个 coverage ledger 行必须留下 capability matrix、该域 contract fixture、loader smoke、artifact/source trace 和外部 addon fixture；不能以“已有实现”或“缺少测试”替代 capability 结论。

退出门槛：每个功能域覆盖账本行闭合；`unsupported`/`partial` 必须显式记录，不能被静默伪装成 parity。

C 的物理化只保留在 §5 的历史候选背景，不占 P3；未来出现新事实时另行评估。

### 7.7 P4：1.2.0 cutover 和清理

发布 wiki 迁移表、插件作者迁移说明和受支持 node/capability 表。

普通 runtime 成员错误保持简洁。

独立离线 validator/migration report 已确认为可选、默认只读、非硬 release gate；不替代迁移表和必要的数据回滚 fixture，也不让迁移文案成为长期 Script API。

删除旧 public route 前，完成 all-node build、artifact、runtime smoke、data fixture、declaration 和 source trace 验收。

退出门槛：仓库只留下新标准实现；无长期 compatibility branch；数据格式未变或专用 migration 可回滚。

## 8. 测试和 Stonecutter 验收

PluginHookPairingTest 继续冻结有意承诺的 Java 插件门面与 Point 配对，不冻结内部 class 分布。

证据：common/src/test/java/com/tkisor/nekojs/core/plugin/PluginHookPairingTest.java:74-148。

ApiManifestGoldenTest 继续冻结有意承诺的 Script surface，不冻结 private helper、cache 或目录组织。

证据：common/src/test/java/com/tkisor/nekojs/core/api/ApiManifestGoldenTest.java:24-54。

Compiler golden 与 corpus 冻结语义，不冻结 Eraser 私有函数布局。

Registry tests 冻结 builder 收集、duplicate、Additional、default type、sugar directory 和 loader delivery 语义。

Platform tests 冻结 shared construction 的共同结果与显式 loader 差异，不冻结事件注册写法。

Build tests 冻结每个保留 node 的 artifact、metadata、mixin/access、processor path、generated source trace 和 runtime smoke。

Data tests 冻结 pdata key、trust-store、signature、path traversal、protected config；日志按历史诊断记录保留，不把它作为可再生 cache/probe，也不冻结日志措辞。

Stonecutter 的保留、收紧、退出均以同一 node 集合比较 build、IDE import、影响文件、jar 内容、check、runtime smoke 和真实变更演练。

## 9. 已裁定的实施输入与未来重评边界

`00`-`10` 的决策票已经闭合，必要规划决策已完成，本规划可作为实施交接输入；下面不是悬空的 HITL 问题，而是获准实施后必须带证据执行的约束，或在未来出现新事实后才能重新打开的事项：

1. 版本发布目标已确定为 `1.2.0`；从 `1.1.0-preview3` 只做一次 clean cutover，不沿用 ADR-0009 的旧两发布/major 假设。
2. Stonecutter 当前保留并收紧；只有覆盖五节点、artifact、IDE/source trace、runtime smoke 和真实新版本接入的替代方案出现后，才能另开退出决策。
3. 语言路径保留 JS/ESM/CJS、TS/JSX/TSX、Python；候选纯 Java 库必须通过 06 的完整语义 corpus、source-map/诊断、许可证、维护性和体积审查。没有候选时不设置脱离实际的 byte gate。
4. 可以提供显式运行的离线 validator/migration report；普通 runtime 错误保持简洁，不携带修复提示，也不把 validator 做成 Script API。
5. 高级 Java access、Graal interop 和当前 HostAccess/`Java.type` 能力不因重构收紧；若未来另有安全收紧，必须成为独立的安全决策。
6. 本次重构不强制给本地 GLOBAL/WORLD pack 新增签名政策；既有远端 trust gate、路径、格式和默认规则由 05 保护。
7. Registry sugar/Builder、Fabric 能力和事件/recipe/client/network 差异按 02、03、04、07 的 capability matrix 实施；`supported`、`partial`、`unavailable` 必须显式记录，不能用假 parity 填表。
8. Fabric builtin catalog 保持单一可审阅登记路径并配套漏项测试；不引入已被 ADR-0003 排除的第二处理器/ServiceLoader 注册通道。
9. 不新增 physical loader/version Gradle Module 或 API jar。未来只有在真实发布、依赖或独立测试收益成立时，才另开物理化评估。
10. `global` 的完整语义以 [跨 reload 的 global 共享状态如何参与候选事务？](decisions/10-shared-global-candidate-writes.md) 的 Resolution 为唯一权威来源；本表只引用必要 owner、fixture、迁移与删除条件。`shared` 是工作名，最终符号在 W5 冻结，不是新的必决问题。

这些条目不能绕过 `07` 的 release gate，也不构成源码修改、数据迁移、功能删除或节点退役许可。

## 10. 后续动作边界

规划已完成可交接，但本文件不批准删除文件、format migration、node retirement、Stonecutter replacement 或外部转译器引入；也不批准任何源码修改，包括 P0 tests。

所有实现、测试、迁移和功能变更均须在维护者明确实施授权后执行；决策票已闭合不等于实施授权。本规划输入不构成源码修改许可。

详细源码证据在 docs/architecture-refactor/evidence/ 下；实施顺序、物理落点、Fabric source bridge 收口和 release 产物见 [实施交接单](implementation-handoff.md)。

这些 evidence 文件是调查材料，不是新的公开 contract 真相源。
