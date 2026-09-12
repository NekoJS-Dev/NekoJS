# 27: CLIENT GUI 与 render Adapter 资源呈现清理

**What to build:** CLIENT 只读错误报告等 GUI 调用路径与 render/screen/world render Adapter 资源从注册、呈现到 generation 清理的完整路径；事件成员、side filter、client-only 过滤、声明、按节点能力与迁移可验证，不包含 keybind/HUD 输入域。按[专项产品决定](../editor-removal-and-error-ui.md)，内置 workspace GUI、菜单和编辑器不再属于本票可用性或验收要求。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)、[30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none
**Human input note:** `none` 只表示实现、测试和证据整理可由 agent 执行；验收条件中涉及的维护者删除确认与 golden 更新审阅是后续发布门禁。agent 可以准备替代路径 parity、迁移表、旧 route 无消费者证据和旧新 golden diff，但不得在获得维护者 sign-off 前删除旧公开路径、更新发布性 golden 或勾选对应验收项，也不因此把本票改判为 `ready-for-human`。

**Work items:**

- W7

## Acceptance criteria

- [ ] error dashboard 等只读 GUI 调用者 Interface 能打开、渲染列表/详情/过滤/复制/日志与有效本地定位动作并报告错误；本票不要求 workspace、菜单或编辑器可用，也不接受编辑、保存、上传或下载脚本作为 GUI 验收。错误与诊断事实只来自票 30 的 frozen diagnostic record，GUI 不缓存可变共享状态、不建立第二事实源或旧 DTO 旁路；现状 `fullDetails` 只是编辑器删除工作流的过渡快照证据，不能替代该目标 record。
- [ ] render、world render 和 screen render callback 继续复用现有 ClientEvents/render 事件与 Adapter；事件名、payload、side filter、取消/优先级和触发线程在 catalog/golden 中唯一，不新增重复 bus。
- [ ] 平台 client Adapter 在正确注册期挂载 render/screen 资源并在 render owner thread 分发；Adapter 持有 MC/loader 类型，shared 作者契约不引入平台类型。
- [ ] reload candidate 阶段新 GUI/render 资源与 listener 对生产路由不可见；commit 后旧 generation 停止接收 callback 并按所有权释放，新 generation 恰好呈现一次，失败或取消时旧 active 继续可用且候选资源全部清理。
- [ ] GUI 操作、render context、取消和错误呈现可从调用者 Interface 观察；断言不依赖私有屏幕字段、Renderer 对象身份或未公开平台集合。
- [ ] 平台/版本 Adapter 按各节点既定支持等级与声明能力验证真实差异；不可用或部分可用时以 supported/partial/unavailable 明示，不自动补 Fabric parity，也不把 experimental 当 primary。
- [ ] 调用者 Interface、Adapter 契约、runtime member、TS/Python declaration、contract/golden 和节点 runtime smoke 可互相追溯；普通测试只读 golden，更新需旧新 diff、影响说明和维护者审阅。
- [ ] PostEffects、Assets、recipe/loot/tags/JEI/capability/goal/keybind/HUD 等既有 owner 不被并入本票；本票只验证不重复声明这些事件或 binding。
- [ ] 旧 render/目标 GUI 入口、重复 handler、不受测 wrapper 或绕过 Runtime Root 的资源装配只能在替代路径 parity、公开迁移表、旧 route 无消费者和维护者确认后删除；不保留长期双路径。内置 workspace/编辑器 GUI 与编辑文件同步不因“旧公开路径”理由保留。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [内置游戏内编辑器移除与只读报错 UI 规划覆盖](../editor-removal-and-error-ui.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): ClientEvents GUI/render 相关成员、payload、side filter、取消/优先级和 catalog/golden 消费由事件面基础提供，不得重复声明 bus。
- [30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md): 只读 error dashboard 必须消费统一诊断票冻结的 record、字段和 generation 语义；在旧错误结构上先行实现目标 GUI 会造成返工和第二事实源。编辑器删除本身不等同于该 record 已实现，也不被本票的前置关系阻塞。

## Scope and coordination

**Rationale:** GUI 与 render Adapter 共享呈现资源、owner thread 和清理边界，能形成一条新上下文可验的呈现路径；输入/HUD callback 生命周期另有触发与注册语义，继续拆开。

**Coordination:**

- 与 CLIENT_INPUT_HUD owner 并行协调 ClientEvents 与平台 client Adapter；两票不互相硬串。
- 与 EVENT_SURFACE owner 协调成员唯一性、side filter 与既有 render bus；事件基础实现是 blocker，具体成员仍需协调。
- 与网络/诊断 owner 协调 error dashboard packet 传输；票 30 拥有 diagnostic record，GUI 只做投影，不反向拥有错误事实源。Fabric 维持现有错误文本降级，本票不顺带承诺 Fabric 新面板。
- 与 POST_EFFECTS/ASSETS owner 并行协调 ClientEvents 使用点，避免同文件改动被误写成串行依赖。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## JSX UI feature coordination（2026-09-12）

新增 JSX runtime 与 Screen 能力归 40–48 feature 票；[41: JSX Screen Adapter](41-jsx-ui-neoforge-screen-adapter.md) 与本票协调客户端 Adapter。既有只读 error GUI/render callback 清理范围不变，不要求将 dashboard 改写为 JSX，也不反向依赖新 feature。
