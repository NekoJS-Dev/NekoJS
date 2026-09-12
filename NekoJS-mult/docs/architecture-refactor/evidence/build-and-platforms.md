# 构建与平台拓扑：证据报告

> 状态：调查记录；不是 ADR、实施计划或已批准的架构决策。
>
> 范围：Stonecutter 多版本/多加载器、Gradle source set 与制品装配、CI/验证链路。
>
> 标签：**事实**来自当前仓库配置或本轮实际命令；**推断**明确标注；**候选建议**需要后续设计和用户裁定。

## 1. 调查边界与本轮验证

本文件记录只读调查与非修改性 Gradle 验证。本次补充仅编辑本证据文档；未编辑源代码、构建脚本、ADR、工作流或其他文档。

本轮实际成功运行：

| 命令 | 结果 | 能证明什么 | 不能证明什么 |
| --- | --- | --- | --- |
| ./gradlew projects --console=plain | 成功 | 当前 Settings 解析出的项目图 | 节点编译、测试或运行时可用性 |
| ./gradlew guardLint --console=plain | 成功；252 个守卫、332 个共享 Java 文件、0 警告 | 当前守卫形状、边界和常量检查通过 | 所有节点编译或运行时行为 |
| ./gradlew :common:check --console=plain | 成功 | common 测试和隔离门禁当前通过 | 平台节点行为 |
| ./gradlew :26.2.0-fabric:check --console=plain | 成功 | 预处理、编译、jar、AW 与 Fabric 制品隔离门禁通过 | 五节点全部构建；最终 jar 的真实 Loader 运行 |
| ./gradlew help --warning-mode all --console=plain | 成功 | Gradle 弃用警告可定位 | Gradle 10 完整兼容 |

本轮没有运行完整五节点 build、NeoForge GameTest server 或 Fabric development runtime smoke。因此，本文件不会将 CI 配置存在误写为“本轮实际完整运行通过”。

调查前后 git status 仅见既有的相邻目录备份/agent 文件与本仓 .memsearch 等未跟踪项。本文件是用户随后明确授权落盘后创建的唯一文件。

## 2. 当前项目图与支持面

### 2.1 Gradle 项目图

**事实。** ./gradlew projects 输出七个直接子项目：

- :1.21.1
- :26.1.2
- :26.2.0
- :26.1.2-fabric
- :26.2.0-fabric
- :common
- :common-api-processor

Settings 中的 Stonecutter 图创建三个 NeoForge 节点，再由对应 NeoForge MC 节点派生两个 Fabric 节点并指定 fabric.gradle.kts：settings.gradle.kts 第 34–40 行。文件头第 3 行称“四个节点”，而第 5–8 行实际列出五个；这是注释不一致，不改变实际项目图。

**事实。** 当前范围不支持 Forge 1.20.1；配置解释其 API 代差无法由共享 guards/replacements 桥接：settings.gradle.kts 第 10–11 行。wiki 说明 Cleanroom 1.12.2 不在本仓构建：wiki/构建系统.md 第 92–96 行。没有 1.21.1-fabric 节点。

### 2.2 Fabric 的构建与发布状态

**事实。** CI 主 build job 构建五个节点：.github/workflows/ci-build.yml 第 141–146 行；两个 Fabric jar 会单独上传给 smoke job：第 191–199 行。

**事实。** Fabric jar 目前仍从 release artifact 集合排除：ci-build.yml 第 163–167 行；CurseForge 的 Fabric 发布步骤仍为注释：第 558–570 行。因此“可构建、有 CI smoke”与“公开发布”是不同状态。

**事实。** docs/fabric-node-expansion.md 仍写有“26.2 未独立 runtime smoke”和“26.1 未端到端验证”：第 10–18 行。当前 workflow 已定义两个 Fabric runtime-smoke matrix leg：ci-build.yml 第 215–224 行。这是待澄清的文档漂移；本报告不据任一表述单独断言实际 CI 历史结果。

## 3. Stonecutter 的真实职责

### 3.1 active 节点、guards 与 replacements

**事实。** 控制器当前设置 active 节点为 26.1.2：stonecutter.gradle.kts 第 10–11 行。active 节点直编共享 src，其余节点（含 Fabric）经 stonecutterGenerate 使用预处理副本：第 1–2 行。

**事实。** switchVersion 会改写控制器文件中的 active 行，任务注释要求随后 IDE 重新 Gradle sync：stonecutter.gradle.kts 第 263–287 行。因此日常 IDE 体验有可变 active-source 上下文，不是仅以无状态 Gradle 属性选择版本。

**事实。** loader guards 常量来自每个节点的 deps.platform，通过 constants.match 建立 neoforge/fabric 常量：stonecutter.gradle.kts 第 42–47 行。版本差异同时使用条件，例如 current.parsed >= 26：第 49–67 行。

**事实。** !mc_ids 是默认启用的纯改名表；mc_legacy_api 默认关闭，只允许局部开启：stonecutter.gradle.kts 第 20–34 行。规则必须在配置期以字面 DSL 注册；改为循环会静默失效：第 39–41 行。

**事实。** Stonecutter 还扩展默认 handlers，使 .json 使用 json5 handler、.toml 使用 cfg handler：stonecutter.gradle.kts 第 12–18 行。因此它不仅处理 Java 源，也处理 mixin JSON 和 NeoForge mods metadata 模板。

### 3.2 guardLint 的边界与局限

**事实。** guardLint 扫描共享 src 下 Java、common 的 API 包和全树、及 versions 下节点属性：stonecutter.gradle.kts 第 91–100 行。它检查 guard 配对、文本块、禁用分支形状、每文件 20 条硬限、连续段告警、loader 泄露、恒假 loader 常量和 common 边界：第 71–90 行、第 191–245 行。

**事实。** sandboxCheck 聚合 guard lint 与每个应用 Java plugin 的节点 check：stonecutter.gradle.kts 第 249–261 行。同段注释明确共享树没有目录级 loader 隔离，靠 guards 和 review 维持：第 249–250 行。

**事实。** 本轮 guardLint 实际通过，报告 252 个 guards、332 个共享 Java 文件、0 个警告。该结果只表示当前 lint 规则通过；不表示各节点 API 可用性或运行时行为已经完整验证。

**事实。** 项目记录 active 侧 guards 对磁盘源是惰性注释，禁用分支须块注释包装：docs/fabric-port-status.md 第 273–276 行。这是维护者理解“同一源在 active 与生成节点怎样不同”的额外知识。

### 3.3 与构建关键路径的耦合

**事实。** NeoForge convention plugin 不能静态依赖 Stonecutter extension，故通过反射调用 process(File, String)：buildSrc/src/main/kotlin/nekojs.neoforge-node.gradle.kts 第 68–74 行。Access Transformer 和 mods metadata 模板均经该桥：第 96–102 行、第 185–211 行。

**事实。** createMinecraftArtifacts 显式依赖 stonecutterGenerate，因为生成目录也是 MDG 输入：nekojs.neoforge-node.gradle.kts 第 176–180 行。

**推断。** 立即替换 Stonecutter 不能只迁 Java guards；还须重建或消除条件源码、替换、AT/mods 模板预处理、生成任务依赖和 active IDE 语义。

## 4. source set、模块边界与重复面

### 4.1 common 与 GraalJS

**事实与规划前提。** common 是 Java 21 引擎模块；本轮用户已明确 `common`（含 `com.tkisor.nekojs.api.*`）允许使用 GraalJS，不为隔离 Graal 而抽 DTO、adapter 或另一个 API jar。用户同时确认 `common`（含 `api.*`）禁止 MC/loader import；允许这些依赖的共享实现放在根 `src/` 或 node，并由相应检查覆盖。当前源码 lint 仍包含过时的 Graal 禁止检查，且只匹配 `org.graalvm`，本轮未修改源码 lint；未来代码实施时移除或更新该过时检查，同时保留 common MC/loader 隔离。api 的职责、命名和 portable contract/Graal interop/MC-loader 分类已由 04 Resolution 定义；具体实现映射交由 07 验证。

**事实。** Graal 是 common 的 api 依赖：common/build.gradle 第 59–66 行。catalog 固定 NeoForge 对应 curse 文件号 8762962，并解释不可降级的 runtime 原因：gradle/libs.versions.toml 第 21–29 行。Fabric plugin 仅将该依赖解析为 8762963：nekojs.fabric-node.gradle.kts 第 57–64 行。

**已知约束。** 用户要求固定 GraalJS，语言编译器保持可控的纯 Java 实现。本调查没有发现必须改变这两点的构建证据，也不把引入外部非纯 Java 编译器列为候选方案。

### 4.2 NeoForge source set 与 fat jar

**事实。** NeoForge plugin 定义节点源码由共享 src/main/java 和本节点 versions/<node>/src/main 下 Java/resources 组合：nekojs.neoforge-node.gradle.kts 第 1–12 行；再加入节点 src/generated/resources 与根 resources-modern/resources-legacy：第 76–89 行。

**事实。** 每个 NeoForge node 依赖 common，且向 ModDev 登记本节点与 common source set：nekojs.neoforge-node.gradle.kts 第 111–116 行、第 153–172 行。jar 会嵌入 common output/runtime，并排除 Graal artifact：第 213–239 行。

**事实。** 版本本地同名源码主要是 override/孪生，不应视为同一节点内普通并列重复。wiki 定义 versions/<node> 为参数与不适合 guards 的 node-specific 源，并给出共享 26.x 与 1.21.1 孪生模型：wiki/项目架构.md 第 49–61 行。

### 4.3 Fabric source bridge 与制品隔离

**事实。** Fabric plugin 读取 deps.fabric_source_node，将该节点目录的 Java/resources 加入当前 Fabric node，并排除 NeoForge*.java：nekojs.fabric-node.gradle.kts 第 21–25 行、第 88–97 行。

**事实。** 26.2.0-fabric 当前仅有参数文件，其中 deps.fabric_source_node 指向 26.1.2-fabric：versions/26.2.0-fabric/gradle.properties 第 1–6 行。本轮追踪源计数也为 26.1.2-fabric 本地 main Java 61 个、26.2.0-fabric 为 0 个。这是显式的跨节点借源，不是独立的 26.2 Fabric 实现。

**事实。** Fabric 会排除 NeoForge 的 AT、mods metadata 与混合 mixin 资源：nekojs.fabric-node.gradle.kts 第 148–164 行。verifyFabricRuntimeArtifact 直接检查最终 jar 的 Fabric 入口和禁止的 NeoForge 类/资源，且接入 check：第 210–278 行。本轮 :26.2.0-fabric:check 输出实际包含 stonecutterGenerate、extractIcuClasses、validateAccessWidener 与该验证任务。

**事实。** Fabric 尚未接入 common-api-processor；注释说明 processor 平台模型尚未覆盖 Fabric：nekojs.fabric-node.gradle.kts 第 280–285 行。

### 4.4 可量化的当前规模

**事实。** 本轮以 git ls-files 和 Java 路径计数所得如下。数字用于估计迁移量，不等价于编译单元数：

| 位置 | main Java | test Java |
| --- | ---: | ---: |
| common | 507 | 182 |
| 共享 MC 树 src | 301 | 31 |
| versions/1.21.1 | 59 | 0 |
| versions/26.1.2 | 3 | 0 |
| versions/26.2.0 | 3 | 0 |
| versions/26.1.2-fabric | 61 | 0 |
| versions/26.2.0-fabric | 0 | 0 |

**推断。** 主要结构性维护面不是 common 被嵌入各 fat jar，而是 1.21.1 的大量 override、Fabric 借源、以及 loader/版本差异混在共享树内时所需的 guard 专门知识。是否消除其中任何一项，取决于对应节点是否继续支持。

### 4.5 Loader 运行时装配与插件发现

**事实。** 版本/加载器节点不是目录内各自的 Gradle build 文件：静态 `include` 只有 `:common-api-processor` 和 `:common`（`settings.gradle.kts:43`），五个节点由 Stonecutter DSL 创建（`settings.gradle.kts:34-40`）。根入口只分别应用 NeoForge/Fabric convention plugin（`build.gradle.kts:1-6`；`fabric.gradle.kts:1-6`）；具体装配逻辑位于 `buildSrc` 的两个预编译 Kotlin DSL 脚本（`buildSrc/src/main/kotlin/nekojs.neoforge-node.gradle.kts:1-22`；`buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts:1-20`）。因此，目录名 `versions/<node>` 表达的是节点参数与源桥，而非独立模块边界。

**事实。** 两个 loader 入口存在相同的运行时装配骨架。NeoForge `NekoJSMod` 在构造期间依次执行 platform setup、事件注册、workspace 初始化、脚本初始化和 client 注册（`src/main/java/com/tkisor/nekojs/NekoJSMod.java:61-75`）；其脚本初始化依次执行插件发现、`bootstrapOwned`、桥接绑定、compiler/sandbox/runtime-root 组装、各 `ScriptType` 发现与 STARTUP 加载（`:142-181`）。Fabric `NekoJSFabricMod.onInitialize` 先挂平台事件/网络/命令，再初始化 workspace、脚本、注册表并 fire after-init（`versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/NekoJSFabricMod.java:88-120`）；其 `initializeScripts` 与 NeoForge 逻辑同构（`:132-175`）。

**推断。** 这不是“版本模块不足”本身造成的复制，而是 loader runtime assembly 尚未被收成一个深模块：当前两入口必须同时知道插件、沙盒、编译器、runtime root、脚本发现与 STARTUP 的顺序。若重构，应先让 common 提供一个小的运行时装配接口，把真实变化（平台初始化、事件接线、注册表 drain）作为 loader adapter 注入；仅移动目录而不收敛该顺序，复制仍会保留。

**事实。** 插件发现的实现已经分为 loader adapter 与 common 注册管理两层。`NekoJSBasePluginManager` 负责 annotation 过滤、实例化、重复发现去重和确定性 priority/FQN/owner 排序（`common/src/main/java/com/tkisor/nekojs/core/NekoJSBasePluginManager.java:21-24,42-95,115-136`）。NeoForge adapter 做全类路径注解扫描并把类交给 manager（`src/main/java/com/tkisor/nekojs/core/NeoForgePluginLoader.java:10-22`）；Fabric 则维护 `BUILTIN_PLUGINS` 手工清单，并另枚举 `nekojs` entrypoint（`versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricPluginLoader.java:15-23,32-46`）。该文件明确写明：新增无平台守卫的内置插件必须同步清单（`:18-20`）。

**推断。** Fabric 手工清单与 NeoForge 注解扫描构成真实的双登记面；新增 common plugin 时存在 Fabric 漏发现风险。应将“内置插件目录”收为一个可由两 loader 消费的单一事实源（例如生成清单或公共 catalog），但第三方发现仍保留为 loader adapter 的真实差异。

### 4.6 Loader adapter 已有的可复用 seam 与差异成本

**事实。** 通用注册表已经呈现“中立收集 core + loader drain adapter”的形态。NeoForge adapter 在第一个 `RegisterEvent` 前 post 一次脚本收集事件，再按注册表 pass 抽干，并处理实体属性/创造标签页后置事件（`src/main/java/com/tkisor/nekojs/listener/RegistryEventAdapter.java:27-37,46-61,90-122`）。Fabric adapter 同样先 post 一次收集事件，但在 `onInitialize` 中单批直注并完成同类属性、标签页和燃料收尾（`versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricRegistryAdapter.java:15-19,27-61,64-85`）。中立 `RegistryRepository` 只保存 builder/连带对象并执行冲突检测（`src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryRepository.java:15-21,36-71`）。

**推断。** 这是适合深化而非按版本复制的 seam：`RegistryRepository` 及其脚本收集面可继续留在中立 runtime，NeoForge 的 pass-aware drain 与 Fabric 的 batch drain 是两个已被当前实现证明真实存在的 adapter。把 pass 顺序泄漏回共享 builder 或把整个注册表域拆为每版本模块，都会失去该局部性。

**事实。** 平台装配并不完全对称：NeoForge node 同时把 `:common-api-processor` 和 `:common` 放入 annotation processor path，且传 `-Anekojs.platform=$platformTag`（`buildSrc/src/main/kotlin/nekojs.neoforge-node.gradle.kts:153-162,243-250`）；Fabric node 只依赖 `:common`，并明确注明 processor 与 platform option 尚未接线（`buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts:67-86,280-285`）。processor 自身之所以仍是独立子项目，是因为 annotation processor 不能和被处理代码同一次编译（`common-api-processor/build.gradle:1-2`），它对 `:common` 只有非传递 compileOnly 依赖（`:27-35`）。

**推断。** 若立刻将 loader/version 都物理拆成 Gradle projects，除 source roots 外还必须复制或参数化 processor path、platform tag、metadata、fat-jar 和 loader-specific runtime libraries；当前不对称说明这不是纯目录迁移。更稳妥的混合路线是先收紧 runtime-domain 模块与 loader adapter，再仅把稳定的 loader-level 物理面显式化；只有某个 MC 代际已形成两个可独立测试的 adapter（而非纯改名 guard/replacement）时，才有足够证据拆出 version module。

### 4.7 已有运行时与版本 seam：应收口而非重造

**事实。** `NekoRuntimeRoot` 已是平台 composition root：它内部持有 core、plugin runtime、event bridge、script properties、environment factory 与按 `ScriptType` 分组的 `ScriptManager`（`common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java:20-59`），并提供 `reload`、`reloadFile`、`runTests`、`errors` 和 `close` 等 lifecycle surface（`:73-115`）。但 NeoForge 入口将它公开为 static `RUNTIME_ROOT`（`src/main/java/com/tkisor/nekojs/NekoJSMod.java:51-53`），客户端直接依赖它来加载或 reload CLIENT 脚本（`src/main/java/com/tkisor/nekojs/client/NekoJSClient.java:43-48,67-79`）；Fabric 客户端也读取其入口 static root（`versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/NekoJSFabricClient.java:29-35`）。

**推断与后续裁决。** `NekoRuntimeRoot` 已有可用的生命周期 interface，问题在于 command、listener、network/client 的取得方式及重复状态容器。本轮已在 [维护者模块归属决策](../decisions/01-maintainer-module-design.md) 确认沿用 Root 职责、允许彻底重构，最终单一 owner；函数或工厂足以承载共同装配，不要求新增 `RuntimeGateway`、第二个 manager 或 ServiceLocator。

**事实。** Loader 运行时抽象已存在于 `IPlatform`：它规定环境、MC/loader 版本、游戏目录、mods、capabilities、NBT/registry/probe 等协定（`common/src/main/java/com/tkisor/nekojs/platform/IPlatform.java:11-109`），全局 `Platform.init` 强制一次初始化（`common/src/main/java/com/tkisor/nekojs/platform/Platform.java:11-35`）。NeoForge 和 Fabric 分别从各 loader 实现这些信息（`src/main/java/com/tkisor/nekojs/platform/NeoForgePlatform.java:20-111`；`versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/platform/FabricPlatform.java:27-89`）；能力集合也已显式不对称：NeoForge 申明 10 项，Fabric 当前只声明 TAGS、RESOURCE_PACKS、NETWORK_CUSTOM_CHANNEL（`:71-83`；`FabricPlatform.java:21-25,73-77`）。

**事实。** 不适合 Stonecutter 机械替换的 MC API drift 已经有明确的 version seam。`McVersionCompat` 将 26.1/26.2 的复杂编译期差异下沉到每版本实现，并由 `META-INF/services` / `ServiceLoader` 解析（`src/main/java/com/tkisor/nekojs/platform/compat/McVersionCompat.java:8-27`）；`McClientCompat` 同样将 screen、render、toast、HoverEvent 等跨 1.21.1/26.x 形状差下沉到版本实现（`src/main/java/com/tkisor/nekojs/platform/compat/McClientCompat.java:11-36,44-60`）。服务文件确实位于 1.21.1、26.1.2、26.2.0 的版本 resources 下（例如 `versions/26.1.2/src/main/resources/META-INF/services/com.tkisor.nekojs.platform.compat.McVersionCompat$Impl`）。

**推断。** 因此“显式 loader/version modules 或 hybrid”不应从零发明抽象，而应把现有两类真实 seam 固化：loader 层以 `IPlatform`、runtime/event/registry adapters 为中心；版本层以 ServiceLoader compat facade 承担已经无法证明为纯改名的 API drift。Stonecutter 可继续只承担经验证的机械 rename 与资源预处理，直到这些 facade 的实际覆盖面和保留节点集合证明更大规模物理拆分值得。

## 5. 生成物与不可作为源码重构目标的路径

**事实。** NeoForge datagen 写入节点 src/generated/resources：nekojs.neoforge-node.gradle.kts 第 118–145 行。Stonecutter 输出在 build/generated/stonecutter/main，且被 MDG artifacts 链消费：第 176–180 行。

**事实。** 两类节点的 mod metadata 都生成到 build/generated/sources/modMetadata；Fabric 还把 ICU 解包到 build/generated/icuClasses：nekojs.neoforge-node.gradle.kts 第 183–211 行；nekojs.fabric-node.gradle.kts 第 109–146 行。

**事实。** .gitignore 忽略 .gradle、所有 build、run、run-server、日志及 processed：.gitignore 第 1–18 行。本轮 git ls-files 未见受追踪的 build 或 processed 输出。现场 versions 下 build/run/run-server 均应当作生成/运行产物，而不是源树重复。

## 6. CI、验证与反馈链路

### 6.1 工作流覆盖

**事实。** 主 build job 用 JDK 25 和 Node 22：ci-build.yml 第 92–122 行。它依次运行 common/processor checks、guard lint、三条 NeoForge NBT smoke、Probe typecheck，随后运行五节点 build：第 124–146 行。

**事实。** 另有 JDK 21 common job：ci-build.yml 第 369–400 行，Windows common job：第 593–629 行，以及 NeoForge 26.1 GameTest：第 631–670 行。

**事实。** Fabric 测试显式使用 JUnit Platform，且没有发现测试时失败：nekojs.fabric-node.gradle.kts 第 166–177 行；维护文档明确其测试面仍是子集：wiki/构建系统.md 第 92–95 行。

### 6.2 慢、脆弱、难定位的证据

**推断（有配置依据）。** 主 job 将五节点 build 集中在一条 Gradle 命令，Fabric runtime matrix 要等该 job：ci-build.yml 第 141–146 行、第 215–217 行。Gradle 允许并行但 configuration cache 关闭：gradle.properties 第 1–5 行。冷缓存下任一节点缓慢/失败会延后其余结果；这不是本轮实测耗时排名。

**事实。** NeoForge GameTest 为规避旧 NeoForm/build-cache 导致的 NoSuchMethodError，主动清除两个 Gradle cache：ci-build.yml 第 651–655 行。

**推断（有配置依据）。** GameTest 是最难诊断的 runtime 环节：它直接运行 :26.1.2:runGameTestServer，未设置显式 shell/工作流 timeout，失败后也不上传运行日志，只 grep 成功 marker：ci-build.yml 第 663–670 行。Fabric smoke 有 180 秒 timeout、提前错误检测和失败日志上传：第 319–366 行。

**事实与推断。** Fabric smoke 先检查下载 artifact 的 jar entries/metadata：ci-build.yml 第 248–304 行；随后运行 checkout 的 :$node:runServer：第 319–356 行。**推断：**它证明上传 jar 的静态结构及当前源码 dev run 可启动，但不严格证明下载的最终 remap jar 放入真实 mods 后可被 Fabric Loader 运行。marker 出现后即 TERM，wait 退出码被 || true 吞掉：第 331–356 行，所以它是启动 smoke 而非完整行为回归套件。

**事实。** 节点清单至少在 Settings、主 build 命令、Fabric smoke matrix 三处手写；新增节点指南也要求同步编辑 Settings 与 CI：settings.gradle.kts 第 34–40 行；ci-build.yml 第 141–146 行、第 220–223 行；wiki/构建系统.md 第 82–86 行。这是漏建/漏冒烟的维护风险证据。

### 6.3 构建卫生信号

**事实。** 根 gradle.properties 固定本机 Windows JDK 25 路径：第 14–18 行；Linux CI 需要 sed 删除它：ci-build.yml 第 116–119 行。

**事实。** 本轮 help --warning-mode all 将 Gradle 10 弃用警告定位到 common/build.gradle 第 18 和 24 行的 Groovy space assignment 仓库 URL 赋值。同次配置还输出 ModDev 对 26.1.2/26.2 使用最新已知 26.1 snapshot capabilities 的提示。两者未导致本轮失败，应进入构建卫生 backlog，而非伪装为阻断缺陷。

## 7. 已决定方向与实施边界

**已决定方向。** 02 已将 NeoForge 26.1.2 定为 primary、26.2.0 secondary，NeoForge 1.21.1 与两个 Fabric node 定为 experimental；五个节点均保留，任何 EOL 需另有证据和维护者确认。

**已决定方向。** NeoForge 和 Fabric 都在范围内，但支持等级不代表能力 parity。必须按“版本 × loader × 能力”表达 `supported`、`partial`、`unavailable`，由 02/07 的 matrix 和 smoke 证据维护。

**约束。** GraalJS 固定；语言编译器保持可控的纯 Java 实现。这不自动禁止已有 Node 22 Probe typecheck。若“纯 Java”还要覆盖所有构建期工具，需用户另行澄清，因为 CI 已使用 Node：ci-build.yml 第 98–102 行、第 121–122 行。

**已决定约束。** 03 已选择保留并收紧 Stonecutter：新业务差异退出 replacements/guard 密集区，退出 Stonecutter 只能在覆盖五节点、IDE/build/jar/runtime 和真实新版本接入的替代证据齐全后另行重评。

## 8. Stonecutter 的已选路线与未来退出门槛

| 路线 | 直接收益 | 代价/风险 | 批准前所需证据 |
| --- | --- | --- | --- |
| 保留原样 | 不动 active-node IDE 工作流；不重写 AT/mods 预处理 | guards、反射桥、跨节点借源、手写清单继续存在 | 未来保留节点/loader 很少变化，且维护者接受现有心智模型 |
| 保留但收紧 | 继续利用已验证预处理链；新业务逻辑不再进入 replacements/guard 密集区 | Stonecutter 与 active 语义仍有长期成本 | 删除 Fabric 借源后，统计剩余转换点、IDE sync 成本和每次改动涉及节点数 |
| 替换为显式 Gradle source sets/variants | loader、版本、资源归属从目录/模型直接读取；可删除 active-node 概念 | 需重建或消除 guards/replacements、AT/mods 处理、资源选择和任务依赖 | 同一保留节点的 PoC 以更少构建逻辑/跨轴改动达成相同 jar、check 与 runtime 结果 |

**已选路线。** 当前采用“保留但收紧”：先明确 loader/version 归属、减少 Fabric 借源和 shared business guard，再以同一五节点集合比较构建命令、IDE 可见性、jar 等价、runtime smoke 和代码变动数。只有未来证据满足 03 的退出门槛，才另开替代方案决策。

## 9. 目标形态与阶段顺序（规划输入，非源码授权）

03 与实施交接单采用的物理目标是：不新增 Gradle project，继续 two-project + five-node 图。

~~~text
common/                         纯 Java 引擎、api 契约、GraalJS（零 MC/loader import）
common-api-processor/           纯 Java 注解处理器（必须独立）
src/main/java + resources/      共享 MC-facing wrapper/binding/registry/builder
src/fabric/java + resources/    Fabric 两 26.x 节点共同 loader source root
versions/<node>/src/main/       无法用 Adapter/facade 表达的节点 override
buildSrc + stonecutter          变体求值、机械替换、资源/metadata/mixin、node 验证
~~~

Fabric 共同实现放独立 `src/fabric` 而不是根 `src/main/platform/fabric`：后者仍会被 NeoForge 节点看到，需要整文件 loader 守卫并承受 active/生成副本双语义；独立 source root 只由 Fabric convention 挂载。

实施顺序（细节见 [实施交接单](../implementation-handoff.md)）：

1. W0 按 02 已定的五节点等级冻结 jar 清单、check、runtime smoke、test skip 和 source trace 基线。
2. W8 将 Fabric loader 专属实现/资源 materialize 到 `src/fabric`，两个 Fabric 节点共用该 root，比较最终 jar 后删除 `deps.fabric_source_node` bridge。既有 spec 已提出该方向：docs/fabric-node-expansion.md 第 20–42 行、第 75–80 行。
3. W1-W7 按垂直域迁移 runtime、script、surface、registry 和功能域；每域先跑受影响节点，再跑 02/07 的全节点 gate。
4. 收紧新增 guard/replacement：逻辑差异走显式 loader/version 面；转换规则只服务经证明等价的版本差异。这与 ADR-0008 的界限一致：docs/adr/0008-guard-discipline.md 第 7–9 行。
5. 只有未来证据满足 03 的退出门槛，才用相同节点集合比较 Stonecutter 与显式 Gradle PoC；当前不启动替代构建。
6. 不论 Stonecutter 最终决定为何，改善 CI：单一 node catalog 驱动矩阵；NeoForge GameTest 加 timeout/失败日志；下载 Fabric jar 作为真实 mods 输入运行，源码 dev run 保留为独立信号。

## 10. 实施输入与未来重评边界

1. 五节点等级已由 02 固定；实施只需补齐 capability matrix、artifact/runtime evidence 和发布报告，不得擅自 EOL。
2. Stonecutter 当前保留并收紧；替代方案只能在 03 的完整等价证据出现后重评。
3. `common` 的 MC/loader-free、`src/` 的共享 MC-facing 归属、`src/fabric` 的 Fabric 归属和 node override 规则是构建 gate，不是可在实施中隐式放宽的选择。
4. 现有 Gradle 项目名、fat-jar 坐标和插件 API 默认保持；任何改变必须进入 07 迁移表并有维护者确认。
5. runner 成本、最终 Fabric remap jar 的真实 mods 运行、Node Probe typecheck 和性能预算在 P0/P4 报告中量化；它们是执行证据，不重新打开已闭合的支持/构建票。

## 11. 文档一致性清单

**事实。** README 已提及两个 Fabric 节点，却在源码树图只列 26.1.2-fabric：README.md 第 32–34 行、第 77–81 行。wiki/项目架构.md 已列出 26.2.0-fabric source bridge：第 49–61 行。

**实施输入。** 在结构实施前，先将过时 Fabric spec 标为历史状态或更新为当前 CI 状态；随后维护“版本 × loader × 构建 × runtime smoke × 发布 × 能力差异”表，条目按 02 的支持等级和 07 的 gate 填写。
