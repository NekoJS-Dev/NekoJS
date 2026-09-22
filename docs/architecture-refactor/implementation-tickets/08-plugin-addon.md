# 08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活

**What to build:** 用一个真实外部 addon fixture 证明 Java 插件能通过 loader discovery 和 fat jar 依赖进入 Plugin Runtime，沿既有 Point/Contributor/Hook 贡献，bootstrap 后经 Extension Handle 或脚本 binding 消费产物；普通 reload 不重新 bootstrap/freeze 插件，而 generation session object 失效。该票只补真实消费链和边界修正，不新增 Point 或插件框架。

**Blocked by:** [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)

**Status:** in-review（实现/测试/证据已交付；AC 全项勾选并附证据；窗口：AC3 的 classpath 形态、AC2/AC13 的其余三节点例行覆盖——见各项标注；非维护者签收）

**Assignee:** zed-flash-08（main-session agent；GLM-5.3 subagent worktree）

**Claim record (2026-09-22):** worktree `../NekoJS-mult-t08` on branch `ticket-08-plugin-addon`（基于 `124aace6`）。预计改动范围：test-only 外部 addon fixture 制品及其构建接线、NeoForge/Fabric 真实 discovery 与贡献消费测试、reload 存活断言、addon 级失败输出、无调用者 legacy manager facade/bootstrap 删除、插件作者最小示例与迁移材料、`baseline/2026-09-22-plugin-addon/` 证据。不修改 19/20 等 pack trust 域文件。

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- 构造带 NeoForge production metadata 和 Fabric `fabric.mod.json` NekoJS entrypoint 的 test-only 外部 addon 制品，模拟真实 fat jar/classpath discovery 输入。
- 让 addon 通过既有 Contributor/Hook 与一个既有扩展点贡献，bootstrap 后由 Handle/result 和脚本可见 binding 消费同一冻结产物。
- 验证 discovery、contribution、dependsOn 拓扑、initialize/collect/finish、freeze、initialization/result 的外部顺序与错误定位；保留现有 Point 生命周期，不新建第二套 Collector 框架。
- 在普通脚本 reload 前后断言 Plugin Runtime bootstrap/freeze 只发生一次，Handle 产物仍可读，generation token 不能操作新 session。
- 补依赖错误、重复 id、freeze 后注册和未知依赖的 addon 级失败输出。
- 交付最小外部 addon 可运行示例与插件作者迁移材料；示例只使用已通过 gate 的公开依赖和入口。
- 删除无生产调用者的 legacy manager facade/bootstrap 入口；仅当外部 fixture 继续需要嵌入入口时保留并记录原因。

## Acceptance criteria

- [x] 外部 addon fixture 是独立 test-only artifact：依赖面与未来第三方插件一致，不 import 生产内部测试 seam，且五个生产 jar 均验证不包含其类、资源或 metadata。【证据：`common/src/addonFixture` source set 编译 classpath 结构上只有 common main 输出（第三方 fat-jar 编译面的子集），`verifyAddonFixtureDependencySurface`（挂 `:common:check`）持续守护；`verifyExternalAddonIsolation`（挂 `sandboxCheck`）对五个节点 jar 逐一断言无 `com/example/addon/` 条目、无 fixture modid 名字标记、metadata 无 modid 痕迹，并递归扫描嵌套 jar（防 fixture 以 nested jar 形态混入），数量硬断言 = 5，通过——`baseline/2026-09-22-plugin-addon/command-output/06-verifyExternalAddonIsolation.txt`（主会话复查时强化 gate 后重跑）】
- [x] 至少一个 NeoForge 节点通过真实 annotation/mod metadata discovery、至少一个 Fabric 节点通过真实 `fabric.mod.json` entrypoint discovery 发现并执行 addon；干净 run/mods 目录重复执行结果一致。【证据：`:26.1.2:runServer`（mods 目录放 fixture jar）——FML `Found valid mod file … {exampleaddon}` → 真实注解发现注册主/次插件 → 冒烟脚本消费冻结产物 `T08-ADDON-SMOKE: startup binding ok, marker=exampleaddon-frozen-product init=1`（`command-output/02`）；`:26.1.2-fabric:runServer`——FabricLoader 加载 `exampleaddon` + `nekojs` entrypoint 发现执行，同一标记（`command-output/01`）；两次 runServer 各自从全新 run/mods 目录启动；JUnit 侧 `Ticket08LoaderDiscoveryTest.repeatedCleanDiscoveryRunsGiveIdenticalResults` 两轮干净 discovery 结果逐项一致（两节点重复执行均绿，`command-output/05`）。其余三节点未单独跑 loader 冒测（同构共享树，owner CI/票 34）】
- [x] fat jar 与 classpath 两种 discovery 输入至少覆盖一种真实目标发布形态；未覆盖形态显式记录，不得宣称两者均已证明。【证据：fat jar（真实目标发布形态——物理 jar 进 mods 目录）在两 loader 的 `runServer` 均被真实发现并执行（`command-output/01/02`）；JUnit harness 的 `URLClassLoader` 也是 jar 形态。**未覆盖并如实记录**：解包 classes-dir 的纯 classpath discovery 输入形态未单独证明（无独立 fixture；与 jar 形态共用同一注册缝，不宣称已证明）】
- [x] 现有 Point initialize/collect/finish、dependsOn、Contributor/Hook 与 Handle 语义保持为唯一插件生命周期；扩展点间依赖和结果可见性用真实 fixture 表达，不新建第二套 initializer/merger/finisher 框架。【证据：fixture 的 `exampleaddon:greetings` 点用既有 V2 builder + `dependsOnId("nekojs:bindings")` 表达依赖，initializer 经 `context.result` 读先序冻结产物；`Ticket08ExternalAddonChainTest.addonContributesThroughHookAndProviderAndFreezesOneProduct` 断言收集序与冻结数据；本票零新增生产 Point/框架】
- [x] 一个扩展点不得通过回调初始化或改写另一个扩展点的中间状态，finish 只冻结自身结果；Collector 式思路只能作为改进现有 Point 的评估输入，不能先造通用框架。【证据：结构面——collector 签名只接收自身累积器，`NekoPluginExtensionContext` 只暴露**已 finish** 产物（读未 finish 点抛 `IllegalStateException`，既有 `NekoPluginBootstrapV2Test.undeclaredDataDependencyThrowsAtReadSite` 前例）；fixture 只读冻结产物，未造任何 Collector 框架】
- [x] 真实 loader discovery 能发现外部 addon，owner identity、priority、requiredMods/clientOnly 规则与旧契约一致，内部实例化不能冒充通过。【证据：`command-output/02` 日志逐条显示 `Registered plugin: com.example.addon.ExampleAddonPlugin (priority 1200…)` 与 secondary（priority 400），clientOnly/requiredMods 缺失者被过滤；`Ticket08LoaderDiscoveryTest`/`Ticket08ExternalAddonChainTest.jarDiscoveryRegistersOnlyEligiblePluginsWithJarOwnerIdentity` 断言 owner codeSource 指向被发现的 jar、fixture 类在引擎/节点类路径上 `ClassNotFoundException`（内部实例化不可冒充）】
- [x] 随实现交付的外部 addon 最小可运行示例和插件作者迁移材料可按公开说明复现；示例只使用已通过 gate 的公开依赖、loader metadata 和入口，不依赖生产内部测试 seam。【证据：`baseline/2026-09-22-plugin-addon/examples/external-addon/`（README 公开构建说明）；示例源码经 JDK 21 `javac -cp common/build/classes/java/main` 独立编译通过（REPORT §4）；其同构 twin（fixture）已在两 loader 真实启动路径执行；`MIGRATION.md` 覆盖发现/生命周期/冻结/reload/失败输出/迁移检查单】
- [x] addon 贡献按 Point 事实源收集，Hook/Contributor 投影效果等价，脚本侧可观察到同一冻结产物。【证据：`Ticket08ExternalAddonChainTest`——hook（`registerBinding` 覆写）与显式 `BindingsPoint.Contributor` 的绑定进同一冻结 map；`runtime.extensionProduct`、Handle `result()`、脚本 binding `ExampleAddon` 三口径 `assertSame` 同一冻结对象（surface.marker()/publishedSummary 可读）】
- [x] Extension Handle 在 finish 前拒绝读取，finish 后可读产物；依赖、环、重复 id、freeze 后注册在对应阶段带 addon 定位失败。【证据：`handleRejectsReadsBeforeFinishAndSkippedPointsStayUnpublished`；四例 addon 定位失败测试（red→green 见 `command-output/07-red-green-chain.txt`）——报错含点 id 与肇事插件 owner id/类名（`NekoPluginBootstrap.scopedTo` + registrants 归因，本票唯一生产行为改动）】
- [x] 普通 reload 不重新 discovery、bootstrap 或 freeze Plugin Runtime；Point result 与 Handle 身份保持有效。【证据：`ordinaryReloadKeepsPluginRuntimeFrozenAndInvalidatesGenerationTokens`——一次装配后两轮事务 reload：addon `registrationCalls`/`initCalls` 恒为 1，Handle `result()` 与 `extensionProduct` 跨 reload `assertSame`，reload 路径不触碰插件发现/注册面（结构性）】
- [x] reload 后旧 generation 的 session object、事件 token 或临时计划不能静默操作新 session，错误明确指向失效 generation。【证据：同测试——`ScriptManager.activeGenerationOf(旧 Context) == -1` 而新 Context 返回新 generation；旧 generation token 走 `VillagerTradeDomainState.query` 返回显式 `STALE` 且 reason=`generation-not-active`（不静默作用于新 session；与票 22 前例同语义）】
- [x] session 清理不关闭仍由进程级 Plugin Runtime 持有的共享 Java 对象；Binding.value 的共享对象不被误当作 generation 快照。【证据：同测试——普通 reload 后 addon surface `closeCalls == 0`，两代脚本经 binding 读到**同一实例**（`remembered` 两次 `assertSame` 同一 surface，非快照重建）】
- [x] 外部 addon 在至少一个 NeoForge 节点与 Fabric 当前节点的 loader 启动/烟测路径中被发现并执行。【证据：`:26.1.2:runServer`（NeoForge primary）与 `:26.1.2-fabric:runServer`（Fabric 当前节点）均出现 FML/FabricLoader 发现行 + `T08-ADDON-SMOKE` 冻结产物消费行 + 服务器 `Done`（`command-output/01/02`）；其余三节点 owner CI/票 34 例行 smoke】
- [x] 无调用者 manager facade、旧 legacy bootstrap 和 loader 私有 Point registry 旁路仅在外部 addon 与 reload fixture 通过后删除。【证据：顺序即证据——commit `24771e21` 先交付外部 addon 链 + reload 存活测试（11/11 绿），commit `5e3222d3` 才删除 @Deprecated 且无生产调用方的 `NekoPluginRuntime.bootstrap(List<NekoJSPlugin>,…)`（外部 fixture 走 owned 路径，不需要嵌入入口）；`NekoJSBasePluginManager` 两 loader 均有生产调用方故保留；本分支检索确认不存在 loader 私有 Point registry 旁路】
- [x] 对声明为冻结的 Point 产物，保留旧累积器引用再写的 fixture 证明完成后写入被拒绝或不影响已发布结果；注册图 freeze、累积器 seal 和结果发布是不同边界。共享 Java 对象按已定生命周期处理，不由只读 Map 外壳推导任意对象深冻结。【证据：`sealedAccumulatorRejectsPostFinishWritesAndLateBindingWritesDoNotLeak`——Sealable 累积器（fixture 自有）finish 后 `add` 抛 `IllegalStateException`；非 Sealable 的 bindings 累积器迟到写入不改已发布冻结 map（两臂分别验证 seal 与 publish 边界；注册图 freeze 由迟到注册拒绝测试覆盖）；共享对象只按其自身生命周期处理（reload 测试的 surface 实例跨代同引用、closeCalls=0，未做任意深冻结推导）】

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [PR 37 维护体验回归约束规格](../specs/00-pr37-maintainer-research.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md): 进程级 Handle 与 generation session 的分离只有在完整 candidate reload 后才可观察。

## Scope and coordination

- **Rationale:** 插件链路的主要缺口不是重造 Point 框架，而是真实 discovery 到消费的端到端证据；单 context 可用 addon marker、Handle result、reload 次数和失效 token 独立验证。
- **Coordination:**
  - MANAGED_SURFACE 组拥有公开签名和 declaration 观测；本票只消费既有 Point/binding，不新增规范源。
  - BUILD_BASELINE 提供旧 addon/Probe 输出输入；若外部 fixture 制品接线需要构建支持，只协调不重开 build/release 票。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## Closure record（2026-09-22）

- 执行者：zed-flash-08（GLM-5.3 subagent worktree）。分支 `ticket-08-plugin-addon`（基于 `7768e02d`），
  未合入 mult、未推送；证据目录 `../baseline/2026-09-22-plugin-addon/`（REPORT.md + examples +
  MIGRATION.md + command-output）。
- 交付：test-only 外部 addon fixture 制品（独立依赖面 + 两 loader 生产 metadata + 五 jar 隔离门禁）、
  真实 discovery 到贡献消费到 reload 存活的端到端测试（common 11 例 + 共享树节点 3 例/节点）、
  addon 定位失败输出（唯一生产行为改动）、无调用方 legacy bootstrap 删除、最小示例与迁移材料。
- 验证：`:common:check`（1774/0）、`guardLint`（0 警告）、`:26.1.2:test` 与 `:26.1.2-fabric:test`
  的 discovery 用例（含重复执行）、`verifyExternalAddonIsolation`（五 jar）、`:26.1.2:runServer`
  与 `:26.1.2-fabric:runServer` 真实 loader 冒烟（mods 目录 jar → 发现 → 执行 → 冻结产物消费）。
  未跑：其余三节点 loader 冒测 / gameTestServer、sandboxCheck 整体聚合（组成部分各自绿）。
- 如实记录的窗口：classpath（解包 classes-dir）discovery 输入形态未单独证明（fat jar 形态已证）；
  其余三节点覆盖 owner CI/票 34；NEKO- 码未引入（零新增问题上报型日志；Error-Reference 页在
  维护者 stonecutter 分支演进，本分支不存在）；docs/agents/coding.md 在本分支缺失（AGENTS.md
  引用悬空）。维护者签收与合并裁定不在本记录内。
