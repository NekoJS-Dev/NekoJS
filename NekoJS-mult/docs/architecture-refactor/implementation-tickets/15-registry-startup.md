# 15: 启动期注册、typed Builder 与连带注册垂直收口

**What to build:** STARTUP 脚本通过唯一 RegistryEvents.register 调用者 Interface 声明对象，经 Registry Runtime 收集、显式 setter/JavaBean property 同路径写入、校验、规范化与类型工厂，由各节点 Adapter 在正确注册 pass 创建对象和已裁定连带对象；runtime member、TS/Python declaration、contract/golden、capability 与节点 smoke 从同一条路径可见。该票只处理游戏启动前声明注册，不把服务器运行期 Dynamic Registry 并入同一路径。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)

**Status:** in-progress

**Assignee:** zcode-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** `none` 只表示实现、测试和证据整理可由 agent 执行；验收条件中要求的维护者删除确认是发布门禁。agent 可以准备替代路径 parity、迁移表和旧 route 无消费者证据，但不得在获得维护者 sign-off 前删除旧公开路径或勾选对应删除验收项，也不因此把本票改判为 `ready-for-human`。

**Work items:**

- W6

## Acceptance criteria

- [ ] STARTUP 脚本只从现有 RegistryEvents.register 调用者 Interface 进入；事件在首个注册表 pass 前恰好收集一次，default 类型糖方法、命名类型和 custom/register 输入都能到达对应 Registry Runtime 请求。
- [ ] Registry Runtime 对 duplicate、additional、default、类型冲突和 drain 顺序保留现有已验证语义：同批重复 fail-fast，additional 不与来源对象外的同 id 冲突，每个 registry pass 只 drain 一次且无未交付残留；失败不留下可污染下一轮启动的进程级暂存。
- [ ] managed 可写配置 Builder 的显式 setter 与 JavaBean-style property assignment 必须调用同一 setter，并进入同一校验、规范化、definition fingerprint 和注册收集路径；final id、只读成员和未开放 experimental 成员例外。不得依赖未验证的 Graal 天然 Bean 行为，等价性由 runtime contract fixture 固定。
- [ ] setter/property parity 由 GraalJS runtime contract test 固定，TS/Python declaration 呈现一致的成员语义；迁移表说明旧 property 写法继续有效、显式 setter 不引入第二语义，final identity 字段例外可显式记录。
- [ ] 启动期既有 `custom(id, type, ...)` 与 `register(registry, id, supplier)` 高级能力保持可用（PR 单注册表 skeleton 的 `register(id, Supplier)` 只是历史形状，不覆盖当前统一事件签名），不被误解为 Dynamic Registry 的通用 `{ type: ... }` catalog；二者必须走唯一启动注册 epoch、重复 ID、类型匹配及各自适用的连带注册 gate；具名类型经既有 Builder 工厂，裸 Supplier 不强制套用具名工厂，规范化 fingerprint 只适用于可表达的启动声明；不对任意 Supplier 代码或其外部副作用承诺可比较指纹/回滚，更不据此开放 Dynamic Registry。Supplier 仍须在合法启动 pass 创建对象并做返回值、实际类型与重复 ID 校验。
- [ ] 随实现交付默认类型糖方法、`custom`、`register(Supplier)`、setter/property parity 和连带注册的最小可运行示例与必要迁移材料；示例只使用已通过 gate 的启动注册能力。
- [ ] typed Builder 的公开成员、校验和错误结果由契约反射生成；Block/Item/Fluid 等已验证启动期类型只按裁定完成约定连带注册，不在本票扩大类型或复制第二套类型 catalog。
- [ ] MC/loader 对象创建、注册 pass 接线、版本差异和 capability 只放在共享 MC-facing Adapter 或节点 Adapter；common 作者契约不引入 Minecraft/loader 类型，26.1.2、26.2.0、1.21.1 与两个 Fabric 节点的实际差异逐项记录。
- [ ] runtime member、TS/Python declaration、Probe/manifest 与 contract/golden 均由同一契约输入派生；legacy preview 只作迁移观察，不静默升级为 stable；普通测试不得改写 golden 或声明产物。
- [ ] 成功、失败、重复、类型冲突、连带注册缺失和 drain 失败测试都从 RegistryEvents 调用者 Interface 贯穿到节点 Adapter 的可观察注册结果，测试输出包含定义、注册表、节点和错误来源，不依赖私有仓库字段。
- [ ] 五节点 source trace、artifact 检查和按各节点既定支持等级与声明能力的最小 runtime smoke 证明声明、实现与能力一致；差异显式记录，不自动补 Fabric parity，也不把 experimental 节点当作 primary 回归。
- [ ] 本票不把 Dynamic Registry 指向启动期 drain 路径，也不在服务器运行期修改 live registry；两个生命周期在契约、测试和迁移表中保持分离。
- [ ] 旧类型化启动入口、手写 declaration、重复 type catalog 或不受测兼容 wrapper 只能在替代路径 parity、旧 route 无消费者、迁移表覆盖所有公开写法并获得维护者删除确认后删除；本票不保留长期双路径。

- [ ] 参照 PR 37 的收集与冻结边界补负例：Builder 配置 callback 抛错不得留可被 drain 的半成品，finish 后底层收集容器的变化不得修改已发布快照；只读 live view 不冒充冻结结果，任意 Supplier 的内部可变状态不被误称为深不可变。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): RegistryEvents 调用者 wrapper、事件成员、dispatch 时机和 catalog/golden 消费由事件面基础提供，不能并行发明第二总线语义。

## Scope and coordination

**Rationale:** 启动期注册是完整的静态声明→drain→节点注册路径，能独立交付并验收；Dynamic Registry 的事务、同步和 stale 语义会超出单一上下文，因此必须分开。

**Coordination:**

- 与 EVENT_SURFACE owner 协调现有 RegistryEvents 总线的注册时机和 catalog 唯一性；事件基础是 blocker，这里只协调具体接入，不因共享事件文件另加串行。
- 与 MANAGED_SURFACE owner 确认 registry Builder 与 declaration 是契约反射输入，避免手写声明成为第二事实源。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
