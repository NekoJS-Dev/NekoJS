# API、扩展点与脚本表面：接口证据报告
> 状态：evidence draft；本文件只记录可核验事实、同步成本和待决问题，不批准架构候选或源码迁移。
>
> 候选名称、路线和比较口径统一引用 `docs/architecture-refactor/proposal.md`；本文不重新编号候选，也不另造 A/B/C 表。

## 1. 证据口径

本轮只修复本证据文件。源码、Gradle、Stonecutter、业务行为和决策票均不在本轮修改范围。
为避免重复长路径，以下前缀只在本文件中定义一次：
- `C` = `common/src/main/java/com/tkisor/nekojs/`。
- `S` = `src/main/java/com/tkisor/nekojs/`。
- `T` = `common/src/test/java/com/tkisor/nekojs/`。
- `D` = `docs/architecture-refactor/decisions/`；`A` = `docs/adr/`。
- `R` = `README.md`；`G` = `stonecutter.gradle.kts`；`W` = `wiki/插件开发.md`。
源码行号按调查时工作树记录。决策状态以各票 metadata 为准，不在证据报告复制状态。
[维护者的最小理解范围与目标模块归属如何确定？](../decisions/01-maintainer-module-design.md) 已记录本轮人工裁决；其他具体接口和迁移建议仍须按各自决策票确认，proposal 不是整体实施授权。

## 2. 公开面先分层

`CONTEXT.md:7-12` 将 Script API 定义为面向整合包作者的公开 JS 接口，将 Plugin API 定义为面向 Java 插件开发者的接口与扩展契约。两者不是同一受众，也不是同一兼容承诺。
Script API 面向 JS/TS；简洁的 KubeJS 风格入口是作者体验要求。`docs/architecture-refactor/proposal.md:21-27` 同时保留高级 Java 访问，GraalJS 是固定运行时。
`W:87-139` 和 `W:168-207` 明确记录 `java:` 导入、`Java.type` 等高级路径，以及绑定对象在 GraalJS Context 活跃时的 interop 语义。它们是有意选择，不是可直接按“泄漏”删除的缺陷。
该公开面不承诺跨 Minecraft 版本的同一 Java 细节；跨版本稳定性必须按脚本语义、插件契约和平台能力分别验证。
接口证据至少要区分三类：
1. portable data：契约符号、版本、模块、能力和普通数据类型；目标是可观察、可验证的脚本表面。
2. Plugin contribution types：`BindingRegistry`、`ApiContributionRegistry`、Point、Handle 等 Java 作者贡献面，带有 bootstrap 生命周期和错误语义。
3. Graal interop：`Value`、`ProxyObject`、`ProxyExecutable`、`Context` 等执行时桥接；它不是 portable data 的自动同义词。
把三类内容塞进同一个“纯 API artifact”会掩盖真实依赖，反而扩大插件作者和维护者的理解范围。

## 3. Managed API 的契约流程

### 3.1 portable-core 的反射输入

`Ccore/api/CoreManagedApiBootstrap.java:319-332` 写明 portable-core 契约由 Java facade、数据类型和事件注册类反射构建，版本从 `api-runtime.properties` 读取，完整性哈希基于 `NormativeApiContract`。
`Ccore/api/CoreManagedApiBootstrap.java:343-350` 反射七个 facade；`:351-356` 继续反射 `TextValue`、`RegistryView`、`NbtEntry`、`ModInfo`、`PerfTimer` 等数据类型。
`Ccore/api/CoreManagedApiBootstrap.java:357-358` 反射 common 侧事件注册符号；`:365-371` 构造 `NormativeApiContract`、计算 integrity 并返回验证契约。
这条链的事实含义是：managed portable-core 的 Java facade/data/event 形状优先由反射输入，规范对象是 `NormativeApiContract`，不是额外手写 JSON。

### 3.2 插件贡献必须命中契约

`Capi/surface/ApiContributionRegistry.java:45-48` 在登记 symbol 前调用 `validateContributionMatchesContract`。
`Capi/surface/ApiContributionRegistry.java:51-72` 另登记 capability provider；`:74-102` 按 owner contract、symbol id 和 tier 查找匹配项，找不到就抛 `CONTRIBUTION_NO_CONTRACT`。
因此插件作者贡献不是任意追加一份 manifest；它必须落在已验证契约的 owner、symbol 和 tier 之内。现有 registry 是边界明确的贡献面，不是引入通用 capability 框架的理由。

### 3.3 manifest 只做观测和输出

`Capi/surface/ApiManifest.java:5-10` 明确说明 `ApiManifest` 是从冻结 surface snapshot 导出的实现观测结果，用于 API diff、stable 子集比较和发布工具；规范性契约仍是人工审阅的 `NormativeApiContract`。
所以不能把 `ApiManifest` 当成第二个 normative source，也不能因它可序列化就再造一份平行规范。

### 3.4 脚本 catalog 仍处迁移双轨

`Capi/catalog/NekoScriptCatalog.java:33-49` 的 snapshot 汇总 bindings、events、adapters、recipe namespaces、host extensions、snippets、type docs、manual declarations 和 registry types。
`Capi/catalog/NekoScriptCatalog.java:56-74` 同时生成 managed API environment，并把临时 catalog 交给 `LegacySurfaceAdapter`。
`Capi/catalog/LegacySurfaceAdapter.java:12-17` 将已有 catalog 转成 `LEGACY_PREVIEW`，明确不把 legacy symbol 当作 normative stable；`:24-30` 只读转换 bindings、events、adapters、host extensions。
结论是迁移双轨：managed contract 与 legacy preview 并存，catalog 仍要承载旧绑定、事件、adapter、host snapshot。不能把整个 Script API 宣称成已完全单一来源，也不能把 legacy preview 误写成稳定规范。

## 4. Java 插件与扩展点的当前流程

### 4.1 作者入口不是纯数据接口

`Capi/NekoJSPlugin.java:3-20` 同时导入 `core.compiler`、`core.fs`、`core.module`、`core.plugin`、`probe`、`wrapper` 以及 api data/event 类型；这证明当前作者入口仍是引擎能力门面，不是已经闭合的独立 DTO 层。
`Capi/NekoJSPlugin.java:77-89` 定义 `registerBinding(BindingRegistry)` 和 `registerAdapters`；其中作者 override 的方法名是单数 `registerBinding`。
普通插件应优先使用已有 `registerBinding`、events、adapters、type docs、probe、recipe 和 API contribution 面。一个新功能只有在确有独立收集生命周期、隔离边界或产物依赖时，才有理由新增收集型 EP。

### 4.2 四处同步是当前真实痛点

以 bindings 为定点样本，新增或改名需要同时核对四处：
1. Point 事实源：`Ccore/plugin/BindingsPoint.java:29-51`，包含 contributor、merge、initializer、collector 和 finish。
2. 作者 hook：`Capi/NekoJSPlugin.java:77-82` 的 default `registerBinding`。
3. 内置清单：`Ccore/plugin/NekoBuiltinPointsPlugin.java:7-14,30-45` 的显式 Point 总索引。
4. 配对测试：`Tcore/plugin/PluginHookPairingTest.java:23-38,55-69,74-125` 的 channel 表、清单一致性、default 签名和无配对 register 检查。
`PluginHookPairingTest` 的断言把漏改一处变成红灯；这是真实同步成本，不是要求再发明一个聚合 registry。

### 4.3 ADR precedence 与已有内核

`A/0001-extension-point-model-v2.md:3-13` 是早期 Collector/V2 形状，其中曾写“`NekoJSPlugin` 纯生命周期接口”。
`A/0010-plugin-authoring-model.md:8-16` 是后续作者模型：Point 是事实源，基接口是门面投影，覆写 hook 与显式 Contributor 等价，V1 shim 不做，并以配对测试冻结作者面。当前 proposal 也以 ADR-0010 为准（`docs/architecture-refactor/proposal.md:119-129`）。
因此不能同时声称“没有注册 hook”和“基接口 hook 是作者入口”；当前应采用 ADR-0010 的双形态解释。
已有内核已经覆盖 PR37 的部分问题：Point 自包含定义见 `Ccore/plugin/NekoPluginExtensionPoint.java:14-35`，Handle 在 `Ccore/plugin/NekoPluginExtensionHandle.java:3-9,31-47` 提供完成后取产物，`dependsOn` 与 Kahn/freeze 语义见 `Ccore/plugin/NekoPluginBootstrap.java:109-125,225-246` 及 Point builder `:202-231`。不应重复发明一套 registry、handle 或 capability 通用框架。

## 5. 通用注册表：runtime 与声明不一致

`Swrapper/registry/gen/NekoRegistryDeclarations.java:8-15` 明说类型声明是手写的：`RegistryEventJS` 的糖方法由 `ProxyObject` 动态暴露，反射声明生成器看不到，因此这里手写 sugar methods 和 builder public fields；NeoForge/Fabric 还按平台保留不同清单。
`Swrapper/registry/gen/RegistryEventJS.java:4-6` 使用 Graal `Value`、`ProxyExecutable`、`ProxyObject`；`:19-38` 说明脚本面先收集、对象延后到 registry pass；`:40-65` 显示 runtime 持有动态 sugar/full-name/member 映射。
这构成明确同步痛点：登记侧的 registry info/types 和运行时动态 members 是一条链，手写 `.d.ts` 是另一条链。两者可测试、可生成或逐步收敛，但不能假装反射已经覆盖 ProxyObject 动态成员。
当前推荐的普通贡献仍是使用已有 registry Point、Builder、Repository 和 Adapter；不存在要求为每个 builder 或每个能力再造通用 EP/capability 层的证据。

## 6. 文档与 guard 证据

### 6.1 README 与 wiki 已经分叉

`R:170,176` 把作者方法写成 `registerBindings`（复数），而源码真实签名是 `registerBinding`（单数）。这不是新 API 设计证据，是文档错误。
`R:193-211` 仍展示旧的 `NekoPluginExtensionProvider`、`registerPluginExtensionPoints` 和 `context` 组装方式；当前 `W:74-81` 说明双形态与写入窗口，`W:144-164` 已改用 V2 builder、merge/initializer/collector/finish 和 handle 语义。
README 示例应改为可编译 fixture 或 snippet compile test，再由 wiki 作为当前作者入口；本轮只记录缺口，不改 README、wiki 或 Java。

### 6.2 api.* 的职责与 Graal 依赖边界（用户前提已澄清）

本轮用户直接澄清已记录于 [公开契约与插件面决策票](../decisions/04-public-contract-and-plugin-model.md)：`common-api` 已并入 `common`，维护者便利优先，`common`（含 `api.*`）允许使用 GraalJS；不为隔离 Graal 而抽 DTO、adapter 或另一个 API jar。

`Capi/event/ScriptEventBusJS.java:3-5`、`Capi/JSTypeAdapter.java:5` 和 `Capi/event/EventBusJS.java:15-17` 使用的是 `graal.graalvm` import。这是当前 Graal interop 事实，不再是“api.* 必须零 Graal”的规范偏差。当前源码 lint 仍包含旧的 Graal 禁止检查，且只匹配 `org.graalvm`；本轮未修改源码 lint。未来代码实施时移除或更新过时的 Graal 禁令；同时 `common`（含 `api.*`）继续禁止 MC/loader import，允许这些依赖的共享实现放在根 `src/` 或 node。

`api.*` 现在同时承载 portable surface、Graal interop、plugin facade 和 engine-internal surface；04 已决定按职责分类、保留现有 public FQCN 的稳定性优先级，不强制建立 portable/interop 子模块。
`A/0007-module-boundaries.md:3-15,23-40` 记录 common-api 合并的原因：没有独立发布/消费者收益，插件 hook 参数又牵引引擎实现。只有先解耦 hook 参数与 Implementation，并出现真实消费者、发布或测试收益，才重新评估 API artifact；独立 jar 仍不是默认终点。

## 7. 真实同步扩散

以下是修改接口时会实际扩散到的边界，供决策票和测试取证：
- managed symbol：Java facade/data/event → reflector → `NormativeApiContract` → contribution validation → manifest/golden → probe 或 managed declaration。
- legacy 脚本面：bindings/events/adapters/host snapshot → catalog → `LegacySurfaceAdapter` → `LEGACY_PREVIEW`；它与 managed symbol 是迁移关系，不是同一事实源。
- Java plugin channel：Point → `NekoJSPlugin` hook → `NekoBuiltinPointsPlugin` 清单 → pairing table/test → bootstrap freeze/Handle/dependsOn。
- registry sugar：registry info/type 登记 → `RegistryEventJS` 的 ProxyObject members → `NekoRegistryDeclarations` 手写类型 → 平台守卫与生成产物。
- 文档面：README 入口、wiki builder 示例、Java snippet fixture、插件签名测试必须一起核对；单改一份示例不能证明接口迁移完成。
同步扩散的结论不是“全仓都必须放进一个 registry”，而是每一类公开面需要一个可追踪事实源和一个可复用验证点。

## 8. 可复用测试资产

现有测试可直接作为证据护栏：
- `Tcore/api/ApiManifestGoldenTest.java:24-30,37-54` 冻结 manifest 的 symbol/signature/capability/module 输出，并要求显式 regenerate 后审阅 diff。
- `Tcore/api/CoreManagedApiBootstrapTest.java:24-41,44-52` 覆盖 portable contract、贡献数量和 legacy binding 冲突。
- `Tcore/plugin/ApiSurfaceBootstrapTest.java:60-112` 覆盖 owner contract、legacy plugin 不进入 managed collection、错误归属等行为。
- `Tprobe/ManagedApiDeclarationGeneratorTest.java:40-63,65-90` 验证 surface 到 TypeScript 声明的生成、排序和不泄漏 native 类型。
- `Tcore/plugin/PluginHookPairingTest.java:74-125` 机械守护 Point、hook、内置清单和签名配对。
- `Tapi/surface/ApiSurfaceSnapshotTest.java:13-20` 守护 surface snapshot 不可变。
本轮应建立、但尚未声称存在的测试：
- README 和 wiki 中最小插件、`registerBinding`、V2 builder 示例的 Java snippet compile test。
- RegistryEventJS 动态 member 集合与 `NekoRegistryDeclarations` 手写声明的 parity test，分别覆盖 NeoForge/Fabric 能力差异。
- catalog legacy preview 与 managed contract 的双轨 characterization，保证 legacy 不被升级成 normative stable。
- ordinary plugin 只走已有贡献面的 fixture，以及“新增收集型通道必须四处同步”的失败样例。
这些测试先作为决策验收输入，不在本轮运行 build，也不因文档修复而改业务代码。

## 9. 证据导出的推荐方向（已由 00-07 Resolution 采纳或约束）

以下是证据导出的工作方向，不是对 `proposal.md` 候选的批准：
1. 保留按领域分开的事实源：managed portable contract、Point/plugin contribution contract、Graal runtime interop、legacy preview catalog 各自有来源；不要追求一个覆盖所有语义的万能文件。
2. 以现有 Point、Handle、dependsOn、freeze 和 pairing test 为深模块边界；普通插件沿用现有贡献面，仅为真实收集生命周期或产物依赖增加 EP。
3. 按 04 已定的 api.* 职责、命名和 portable contract/Graal interop/MC-loader 分类实施；不另造 api 层纯度投票，不新增独立 API artifact。未来若重开 artifact，仍需先解耦并证明真实消费者、发布或测试收益。
4. 把动态 registry members 与手写声明同步作为一个小型垂直验收，优先证明运行时、声明、平台差异和生成物能被追踪。
5. 保留 JS/TS 简易入口和 `java:`/高级 Java 访问；不以“封装更纯”为理由偷偷收紧 GraalJS 语义。
6. 优先维护者 Locality，其次 Java 插件作者，再其次脚本作者；NeoForge 与 Fabric 能力差异进入契约和测试，而非用一个假设的 stable subset 掩盖。
`proposal.md:21-27,199-205` 还锁定 GraalJS、自研纯 Java 前端优先；小型合适纯 Java 库可以评估，但约 10 MB 或更大依赖默认不合适。
03 已决定保留并收紧 Stonecutter；02 已把 NeoForge 26.1.2 定为 primary、26.2.0 secondary，其余三个 node 为 experimental，五节点均不退役。
本次完成即以 `1.2.0` 作为新标准，只做一次 clean cutover，不等待 2.x；允许破坏性变更，但必须有迁移表、数据保护和逐项功能删除确认。
`common/src/main/templates/nekojs/api-runtime.properties:1-6` 当前为 api `0.12.0`、spi/runtime `0.0.0`。mod 版本已由 07 定为 `1.2.0`；api/spi/runtime 属性是否需要同步属于 release handoff 的实施记录，不再是架构决策。
`A/0009-release-and-acceptance-strategy.md:5-6` 的旧“两发布、P2 唯一脚本 breaking”已被 07 取代：本轮是一次 `1.2.0` clean cutover，配迁移表、数据保护和 release gate。

## 10. 已关闭决策票的结论与实施入口

### `D/01-maintainer-module-design.md`：维护者范围与模块归属

- 01 已裁定：逻辑深模块优先、单一 `NekoRuntimeRoot`、域内事实源；四类维护任务作为验收，不按文件数计分。

### `D/02-support-matrix.md`：支持组合

- 02 已裁定支持等级：NeoForge 26.1.2 primary、26.2.0 secondary，其余三个 node experimental；能力矩阵和逐节点证据由 07 的 release gate 承载。

### `D/03-platform-build-strategy.md`：版本差异与 Stonecutter

- 03 已裁定：保留并收紧 Stonecutter；差异优先由 facade/Adapter 或 node 表达；替代方案需完整等价证据。
- api.* 分类由 04 裁定；过时 Graal lint 的更新属于实施工作，`common` 的 MC/loader 隔离保留。

### `D/04-public-contract-and-plugin-model.md`：公开契约与插件面

- 04 已裁定：NormativeApiContract 是 managed 规范源；legacy preview/manifest/Probe/declaration 是观察或派生物；Point/Hook/Contributor/Handle 是插件扩展事实源；raw Java 与 Graal interop 是明确支持的能力；不新增独立 API artifact。

### `D/05-runtime-lifecycle-and-data.md`：生命周期与数据

- 05 已裁定：普通 reload 只切换脚本环境并保留 NekoJS 自有旧 runtime/state；Plugin Runtime 与平台注册不重启；数据格式/路径/key 默认不变，必要迁移必须可回滚；功能删除逐项确认。

### `D/06-language-module-pipeline.md`：语言与模块管线

- 06 已裁定：Preparation、Module Resolution/Cache、Execution 三个逻辑 Module；保留全部语言；纯 Java 自研优先；候选库须通过语义 corpus、source-map/诊断、许可证、维护性和体积审查。

### `D/07-validation-and-migration.md`：验证、迁移与版本

- 07 已裁定：P0-P4 内部阶段、`1.2.0` 唯一 cutover、显式 golden 审阅、五节点/能力 gate、离线 validator、数据回滚保护和维护者四类试做。
- 具体实施顺序、物理落点、Fabric source bridge 收口和 release 产物见 [实施交接单](../implementation-handoff.md)。
本报告到此停止在证据和问题边界；00-07 的结论以各票 Resolution 为准，实施按 [实施交接单](../implementation-handoff.md) 推进，不在本文件私自结票。
