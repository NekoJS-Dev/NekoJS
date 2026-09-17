# 16: Dynamic Registry inert 定义计划与 typed Builder

**What to build:** SERVER 脚本通过独立的服务器运行期动态注册事件 facade 在 candidate 阶段提交 type-specific callback Builder 定义；本地路径完成事件收集、全规范化 fingerprint、preflight、同 key 冲突、stale/retired 记录和 inert Adapter 请求，并用现有 event Builder 与 Adapter 本地行为测试证明候选不修改 live registry。候选类型只在既有 Item、SoundEvent、MobEffect 范围内选取；完整公开激活由事务/同步 gate 决定，本票不宣称动态热更新已完成。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)

**Status:** in-progress

**Assignee:** zcode-agent

**Optional:** false

**Selected:** true

**Human input:** none
**Human input note:** `none` 只表示实现、测试和证据整理可由 agent 执行；验收条件中涉及的维护者删除确认是后续发布门禁。agent 可以准备替代路径 parity、迁移表和旧 route 无消费者证据，但不得在获得维护者 sign-off 前删除旧公开路径或勾选对应删除验收项，也不因此把本票改判为 `ready-for-human`。

**Work items:**

- W6

## Acceptance criteria

- [ ] server registry ready 触发初次候选；script/data reload 在候选阶段重新收集并完成本批 preflight，成功 commit 才发布计划；失败或取消不另起写入，也不在任意脚本线程即时执行 registry mutation。
- [ ] 脚本作者面使用类型直达入口：已验证类型默认冻结为 `event.item(...)`、`event.soundEvent(...)`、`event.mobEffect(...)` 的 callback Builder 形式；若源码证据迫使不同命名，必须在实施前记录命名决策与理由并进入 contract/golden、declaration 和迁移表。
- [ ] 事件回调只接收 typed callback Builder；不提供通用 type 对象 catalog，也不把未知 type 字符串转换成注册能力。候选范围只从既有 Item、SoundEvent、MobEffect 中选取，且只有通过目标 Adapter、事务与同步 gate 的类型才可公开；未验证类型记录 not verified 并阻塞开放，不因缺测改写为 unavailable。
- [ ] 显式 setter 与 JavaBean-style property 写入调用同一个 setter、校验、规范化和 definition fingerprint 路径；GraalMC 临时 property 实验只作为 characterization，本票验收必须由运行时 contract test 固定，且不得把该实验称为集成通过。
- [ ] 全规范化 fingerprint 覆盖 Builder 输入和约定连带声明，不依赖对象身份或部分字段；相同定义在重复 reload 中得到相同 fingerprint，字段或连带声明变化能被识别。
- [ ] 同一 key 的定义变化在第一版导致整批冲突失败，旧 active 定义继续服务；remove、replace、modify 和未来覆盖机制不出现在公开 Interface、golden、declaration 或迁移承诺中。
- [ ] preflight、fingerprint 冲突和 Adapter 请求都是 generation-scoped inert candidate plan；脚本线程或候选失败不得修改 live registry、挂载生产 callback 或提前发布对外 binding，失败时临时计划和资源全部清理。
- [ ] 脚本不再声明的已暴露项标记 stale/retired，普通 reload 不物理删除；claim、stale、mode 与后续显式清理语义由 Registry Runtime/Adapter Interface 可观察并测试。
- [ ] 调用者 Interface、Registry Runtime/Adapter 契约、TS/Python declaration、contract/golden 和本地行为测试形成同一条证据链；测试优先穿过事件 facade 与 Adapter Interface，不断言私有 Manager 字段。
- [ ] 本票只证明本地 inert 计划行为，不激活多人同步、不宣称动态热更新或任何节点能力已完成；公开激活和能力结论由事务/同步 gate 决定。
- [ ] 旧 DynamicRegistry 静态全局入口和直接 registry surgery 路径只能在事件 facade、候选计划、Adapter 请求、声明和迁移路径全部覆盖且无消费者后删除；删除需维护者确认，不保留双写 shim。
- [ ] 随本票交付隔离测试 harness 可运行的候选计划 fixture，演示类型直达入口、setter/property parity、同 key 冲突与 stale 查询；明确标注仅本地计划、尚未公开激活，不作为生产脚本使用指南。本票不等待票 21 关闭；由票 21 在事务/同步 gate 通过后发布对应生产示例与迁移材料，未开放类型不展示为可用能力。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): 动态注册事件 wrapper、成员目录、dispatch 语义和 candidate 事件收集必须消费既有事件面基础，不新增第二 bus。

## Scope and coordination

**Rationale:** 把可本地闭合的 inert 定义、规范化、冲突与 stale 语义先独立交付，可以让 Builder 和事件契约在单上下文内验证；公开类型激活、多人 prepare/ack/commit 与网络/生命周期 gate 另行处理，避免一张过大的 Dynamic Registry 票。

**Coordination:**

- 与 RUNTIME_ROOT/RELOAD_COMMIT owner 对齐 candidate generation token、owner thread 和失败清理接口；RELOAD_COMMIT 是必要 blocker，接口细节仍需协调。
- 与 REGISTRY_STARTUP owner 共享类型事实与 Builder 表达方式，但禁止复用启动期全局 drain 路径；差异写入两张票的测试。
- 与 GLOBAL_STATE owner 只协调同一 candidate 中写集与注册计划的联合失败边界，不把 global 实现作为 Dynamic Registry blocker。
- 与 REGISTRY_STARTUP owner 共享类型事实、setter/property 语义和规范化输入，但 Dynamic Registry 使用自己的 Builder 路径且不得复用启动期全局 drain，因此不是阻塞；若实施时抽取共同 setter/fingerprint 契约，再升级为真实依赖并说明边界。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
