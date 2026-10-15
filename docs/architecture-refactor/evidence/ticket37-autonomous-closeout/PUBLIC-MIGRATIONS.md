# 公开迁移与删除核对（2026-10-07）

这是逐符号迁移核对。2026-10-15 维护者已对下表 A15/A16/A22 精确范围答复「批准上述范围，验证通过后删除」；完整范围和条件见[本轮切换清单](../2026-10-15-domain-closeout/PUBLIC-CUTOVER.md)。A28 的既有精确追认继续有效。该结论不授权正式发布、数据删除、协议变更或最终回滚政策；实际验证结果见[本轮报告](../2026-10-15-domain-closeout/REPORT.md)。

| 票/旧 public symbol | 当前状态 | 新写法/替代路径 | 数据与行为影响 | 确认状态 |
|---|---|---|---|---|
| 15 Builder 的 public 可写字段 | 已改为 accessor；脚本 property 继续有效 | `RegistryEvents.register(event => event.item(id, build => { build.maxStackSize = 16 }))`；Java 使用 getter/setter | 同一 setter 校验/规范化/指纹；Java field 访问是既有 breaking；不改变保存格式 | 历史改动已列迁移；本轮未新增删除批准 |
| 15 `nekojs.registry` manual declaration | 已移除手写 producer，默认生产声明由 registry 元数据和 builder 契约派生 | 默认/具名 sugar、计算类型名、custom、Supplier 保留；使用事件推导及 `NekoStartupBuilders.RegistrySugar`，旧全局 builder 类型改用该命名空间 | 无保存/wire 变化；FoodBuilder 回调和真实 nullable 属性补齐；计算名称 fallback 仍由 runtime 验证 | 2026-10-15 精确批准，验证条件和结果见本轮报告 |
| 15 `NekoScriptCatalogSnapshot` 14-arg public ctor | 已移除；内部调用者迁移完毕 | 使用当前 16-arg ctor，显式传 class declarations 和 builder surfaces；保留另一 15-arg ctor | Java 签名 breaking；无保存/wire 变化 | 2026-10-15 精确批准 |
| 16 `DynamicRegistry` binding 六个 entry overload；旧 fluent builder；`DynamicEntryHandle` | 已移除旧 binding、类与返回 handle，wiki 已迁移 | `DynamicRegistryEvents.dynamicRegistry(event => event.item(id, build => { build.maxStackSize = 16 }))`，soundEvent/mobEffect 同理 | candidate 收集、commit 后 activation；成功 activation 才发布 backend claims，失败回滚；无立即 handle；保留共享 backend 和 sync gate | 2026-10-15 精确批准，失败/空代际回归及实机窗口见本轮报告 |
| 22 NeoForge 26.x `VillagerTrades.add/pendingCount` | 已移除；1.21.1 既有移除追认继续有效 | `ServerEvents.tradeDeclaration` / `tradeReload`，保留 `VillagerTrades.query()/describe()` | 整批验证和 commit；遗漏项物理保留并记 unrestored，显式 obsolete 才 retire | 2026-10-15 精确批准 |
| 22 两节点旧 `VillagerTradeManager` public nested types/静态 methods/旧 listener-command hooks | 已移除旧 manager 与钩子 | root-owned collector、节点 Adapter、generation query；资源重载由现有 owner 重放 | 保留 query producer、平台 surgery 和持久数据；legacy 可变池必须兼容 NeoForge 重建 | 2026-10-15 精确批准；生命周期验证见本轮报告 |
| 28 `PostEffects.register/unregister/has`、Manager 旧 writers、binding close 清屏 override | **已删除**；A28 已追认下文精确接口及 teardown 变化，不是 blanket 删除 | `ClientEvents.postEffects(event => event.register/unregister(...))`；`hasDefinition` 读 active generation；runtime set/clear/toggle/current 保留 | 候选失败保留旧 active；binding teardown 不清屏；资源 reload 的 vanilla renderer 行为另验；legacy resources-only，任意新 inline id 激活限制已披露 | A28 精确追认已取得；不表示视觉、性能或发布验收 |

## 不属于删除清单

- DynamicRegistries / RegistrySurgery / DynamicRegistrySet / bookkeeping / RegistryDataCollectorMixin / enabled gate / debug commands：新 activation/rollback 和客户端 component replay 使用它们。
- RegistryEvents binding、custom/Supplier advanced entry、TypeDoc 和有效 builder 对象生命周期。
- VillagerTrades query/describe binding producer、域 collector、新 Adapter。
- PostEffectManager 的 active definition、parse/install generation、runtime shader source、cache lifecycle 和 set/clear/toggle/current/query。
- 任意用户数据、world、pack、trust-store；网络协议 schema、payload id 与已有 frozen wire gate。

## 技术验证与仍开窗口

新 PostEffects 双语言生产 parity、两 Fabric 的明确失败断言和旧 callback golden；26.2 官方制品 native resource inversion/clear/F3+T、声明资源 id override 实际 blur；26.2 官方交易报价；Fabric 官方制品 declaration-only preflight 明确拒绝，均由同目录证据承载。

历史未验证窗口由各自后续证据更新，不从旧包直接外推；当前技术结果以本轮报告和原票验收项为准。28 多节点首帧/任意内联 id 激活限制仍不外推。Potion.effect 短重载、fixedRange nullable 与具体 setMode fluent 返回已修复，见 [声明精度证据](../ticket15-16-declaration-precision/README.md)。历史 closed header 不代表其余 AC 已满足。

## 已取得的维护者结论（主会话原文）

以下 A22 窄范围追认是历史阶段结论，其当时未批准部分已由 2026-10-15 上表精确批准覆盖；两次批准均不扩大到 query producer 或用户数据删除。

- A22：维护者明确选择「追认仅1.21.1已发生的移除」。确认仅覆盖 1.21.1 `VillagerTradesJS#add(String, Object)` 与 `#pendingCount()` 的既有移除；不批准剩余 26.x 入口、两个节点旧 `VillagerTradeManager`、listener/command hooks 或 query binding producer 的删除。保留 `VillagerTradesPlugin` 注册及 query/describe；legacy 静态池实机替换与交易 retirement/reload 等未验证项不因追认而通过。
- A28：维护者明确选择「追认以下精确接口及行为变化」。确认覆盖已删除的 `PostEffectsJS#register(String, Map)`、`#unregister(String)`、`#has(String)`，`PostEffectManager#register`、`#unregister`、`#clearRegistered`，以及 binding close 不再在 teardown 时清屏；保留 set/clear/toggle/current 与 query。已披露 1.21.1 resources-only 及任意新 inline id 激活限制；该确认不是首帧/全节点视觉、性能门禁、正式发布或数据回滚策略批准，也不授权删除 active definition、parse/install generation、runtime shader source 或 cache lifecycle。

## 仍需维护者结论的项目

A15/A16/A22 的上述精确范围及 A28 已列追认不重复申请。最终发布/cutover、数据回滚策略和任何性能政策重裁仍未批准；未验证技术窗口继续保留。客户端物理 exposure ledger 不携带 stale bit，服务端 claim retirement 是权威；本轮没有修改冻结 wire schema，也不把客户端本地 fresh 标记解释为服务端所有权。

这些结论不等于正式 1.2.0 发布、版本切换、性能豁免或数据回滚策略批准。
