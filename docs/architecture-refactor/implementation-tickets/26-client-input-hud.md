# 26: CLIENT 输入与 HUD callback 生命周期

**What to build:** CLIENT 脚本通过 `KeyBindEvents.register(...)` 创建按键绑定，通过 `KeyBindEvents.pressed(...)`、`KeyBindEvents.released(...)`、`KeyBindEvents.tick(...)` 订阅输入事件；`ClientEvents.registerKeyMappings(...)` 订阅原生 key mapping 注册事件，`ClientEvents.hud(...)` 订阅 HUD 绘制事件，`ClientEvents.hudRender(...)` 按 id 注册常驻渲染器。验证各自既有调用者 Interface 到平台 client Adapter 的注册、owner-thread dispatch、reload 清理、client-only 过滤、声明与按节点能力；不把直接注册入口误写成事件监听，不包含 GUI 屏幕与 render Adapter 资源域。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** agent 可以实现、测试并准备替代路径 parity、公开迁移表、旧 route 无消费者和 golden/发布证据；删除旧公开输入/HUD路径或最终 golden/发布确认的维护者 sign-off 不能由 agent 代答，未经 sign-off 不得删除旧路径或勾选对应删除项。

**Work items:**

- W7

**命名层次：** Java 源码中的 `REGISTER`、`PRESSED`、`REGISTER_KEY_MAPPINGS` 等是内部字段定位符；脚本公开名分别由 `GROUP.add/client(...)` 的字符串成员名确定，不是根据字段名自动转换。本票以小驼峰脚本名描述调用者行为；不要求重命名 Java 常量，也不因挂载在事件组下就把直接注册调用改成监听事件。

## Acceptance criteria

- [ ] CLIENT session 由唯一 Runtime Root 在客户端启动入口创建，早期 key mapping、client tick 和 timer flush 在同一 owner 语义下发生；dedicated server 或裸 JVM 不加载 client-only 类。
- [ ] keybind 注册在正确注册期执行且同 full id 幂等，CLIENT reload 不重复 KeyMapping；pressed/released/tick 事件只派发给新 generation，非法 id/key/category 显式失败并给出可定位错误。
- [ ] Script API 成员使用小驼峰命名（lowerCamelCase，如 `preTick`、`registerKeyMappings`），事件组名保持 `KeyBindEvents` / `ClientEvents`；`KeyBindEvents.pressed/released/tick`、`ClientEvents.registerKeyMappings/hud` 继续复用各自既有事件，`KeyBindEvents.register(...)` 与 `ClientEvents.hudRender(...)` 分别保留直接创建按键绑定、按 id 注册常驻渲染器的调用语义。声明、示例及 catalog/golden 区分直接注册入口与事件监听，事件名、payload、side filter、取消/优先级和触发线程唯一，不新增重复 bus；不得把 Java 大写字段名当作脚本调用名。
- [ ] reload candidate 阶段新 keybind/HUD listener 对生产路由不可见；commit 后旧 listener/timer/handler 停止接收新 callback，新 generation 恰好执行一次，失败或取消时旧 active 继续可用且候选资源全部清理。
- [ ] 按键状态、consumeClick 与 HUD 呈现结果可从脚本调用者 Interface 观察，断言不依赖 KeyMapping 私有集合或平台回调对象身份。
- [ ] 平台/版本 Adapter 按各节点既定支持等级与声明能力验证真实差异；client-only 过滤、注册时机或输入事件不可用时以 supported/partial/unavailable 明示，不自动补 Fabric parity。
- [ ] 调用者 Interface、Adapter 契约、runtime member、TS/Python declaration、contract/golden 和节点 runtime smoke 可互相追溯；普通测试只读 golden，更新需旧新 diff、影响说明和维护者审阅。
- [ ] GUI、render Adapter、PostEffects 与 Assets 保持独立 owner；本票不重复声明或清理它们的资源。
- [ ] 旧输入/HUD入口、重复 handler 或绕过 Runtime Root 的静态装配只能在替代路径 parity、公开迁移表、旧 route 无消费者和维护者确认后删除；不保留长期双路径。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): KeyBindEvents 输入成员与 ClientEvents 的 key mapping/HUD 成员、side filter、取消/优先级和 catalog/golden 消费由事件面基础提供，不得重复声明 bus。

## Scope and coordination

**Rationale:** keybind 与 HUD/overlay 是同一条 CLIENT 输入/callback 生命周期路径，可单独注册、dispatch、reload 清理和 smoke；GUI/render Adapter 资源另拆，避免原合并客户端票过宽。

**Coordination:**

- 与 CLIENT_GUI_RENDER owner 并行协调 KeyBindEvents/ClientEvents 与平台 client Adapter；两票不互相硬串。
- 与 EVENT_SURFACE owner 协调成员唯一性、side filter 与既有 bus；事件基础实现是 blocker，具体成员仍需协调。
- 与 RUNTIME_ROOT/RELOAD_COMMIT owner 对齐 client owner thread、generation 路由和失败清理；共享 runtime 文件只作接口协调。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## JSX UI feature coordination（2026-09-12）

[41: JSX Screen Adapter](41-jsx-ui-neoforge-screen-adapter.md) 拥有 JSX Screen 内部输入、焦点与滚动路由；本票继续拥有 keybind/HUD，不互相硬串或合并 owner。
