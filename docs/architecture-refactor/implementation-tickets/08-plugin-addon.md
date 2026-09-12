# 08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活

**What to build:** 用一个真实外部 addon fixture 证明 Java 插件能通过 loader discovery 和 fat jar 依赖进入 Plugin Runtime，沿既有 Point/Contributor/Hook 贡献，bootstrap 后经 Extension Handle 或脚本 binding 消费产物；普通 reload 不重新 bootstrap/freeze 插件，而 generation session object 失效。该票只补真实消费链和边界修正，不新增 Point 或插件框架。

**Blocked by:** [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)

**Status:** ready-for-agent

**Assignee:** unassigned

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

- [ ] 外部 addon fixture 是独立 test-only artifact：依赖面与未来第三方插件一致，不 import 生产内部测试 seam，且五个生产 jar 均验证不包含其类、资源或 metadata。
- [ ] 至少一个 NeoForge 节点通过真实 annotation/mod metadata discovery、至少一个 Fabric 节点通过真实 `fabric.mod.json` entrypoint discovery 发现并执行 addon；干净 run/mods 目录重复执行结果一致。
- [ ] fat jar 与 classpath 两种 discovery 输入至少覆盖一种真实目标发布形态；未覆盖形态显式记录，不得宣称两者均已证明。
- [ ] 现有 Point initialize/collect/finish、dependsOn、Contributor/Hook 与 Handle 语义保持为唯一插件生命周期；扩展点间依赖和结果可见性用真实 fixture 表达，不新建第二套 initializer/merger/finisher 框架。
- [ ] 一个扩展点不得通过回调初始化或改写另一个扩展点的中间状态，finish 只冻结自身结果；Collector 式思路只能作为改进现有 Point 的评估输入，不能先造通用框架。
- [ ] 真实 loader discovery 能发现外部 addon，owner identity、priority、requiredMods/clientOnly 规则与旧契约一致，内部实例化不能冒充通过。
- [ ] 随实现交付的外部 addon 最小可运行示例和插件作者迁移材料可按公开说明复现；示例只使用已通过 gate 的公开依赖、loader metadata 和入口，不依赖生产内部测试 seam。
- [ ] addon 贡献按 Point 事实源收集，Hook/Contributor 投影效果等价，脚本侧可观察到同一冻结产物。
- [ ] Extension Handle 在 finish 前拒绝读取，finish 后可读产物；依赖、环、重复 id、freeze 后注册在对应阶段带 addon 定位失败。
- [ ] 普通 reload 不重新 discovery、bootstrap 或 freeze Plugin Runtime；Point result 与 Handle 身份保持有效。
- [ ] reload 后旧 generation 的 session object、事件 token 或临时计划不能静默操作新 session，错误明确指向失效 generation。
- [ ] session 清理不关闭仍由进程级 Plugin Runtime 持有的共享 Java 对象；Binding.value 的共享对象不被误当作 generation 快照。
- [ ] 外部 addon 在至少一个 NeoForge 节点与 Fabric 当前节点的 loader 启动/烟测路径中被发现并执行。
- [ ] 无调用者 manager facade、旧 legacy bootstrap 和 loader 私有 Point registry 旁路仅在外部 addon 与 reload fixture 通过后删除。

- [ ] 对声明为冻结的 Point 产物，保留旧累积器引用再写的 fixture 证明完成后写入被拒绝或不影响已发布结果；注册图 freeze、累积器 seal 和结果发布是不同边界。共享 Java 对象按已定生命周期处理，不由只读 Map 外壳推导任意对象深冻结。

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
