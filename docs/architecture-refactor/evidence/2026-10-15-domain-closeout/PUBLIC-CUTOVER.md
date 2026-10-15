# 公开入口精确切换范围

本清单列出本轮已获批准的精确删除范围；批准不豁免替代和验证条件。适用条件：替代实现、消费者迁移及领域验证通过后再删除；不豁免任何验收项。

| 项目 | 拟移除 | 替代与保留 | 行为影响 |
|---|---|---|---|
| A15 | `NekoRegistryDeclarations` 的 `nekojs.registry` 手写声明生产路径；`NekoScriptCatalogSnapshot` 的 14 参数兼容构造函数 | 契约派生的 STARTUP 事件、Builder、默认/具名 sugar、custom、Supplier；保留 15 参数和当前 16 参数构造函数。`RegistrySugar` 同源派生，删除前明确旧全局类型名的迁移 | TypeScript 全局手写类型迁移至事件推导/命名空间；旧 Java 构造调用需显式传入缺省的 class/builder 列表。无保存格式或 wire 变化 |
| A16 | `DynamicRegistry` Binding；`DynamicRegistryJS` 的 soundEvent/mobEffect/item 各两个 overload；仅被其使用的旧 fluent Builder 与 `DynamicEntryHandle` | `DynamicRegistryEvents.dynamicRegistry` 的类型专用 callback Builder。保留 Plugin 的事件与声明注册、共享 backend、surgery、bookkeeping、同步 gate、mixin、调试查询；先迁移 teardown 生命周期 | 定义从立即变更改为 candidate 收集、commit 后 activation；不再同步返回 handle，失败保留 active |
| A22 | 26.x `VillagerTrades.add/pendingCount`；两套旧 `VillagerTradeManager` 的 pending/flush 生命周期及 listener/command hooks | `ServerEvents.tradeDeclaration/tradeReload`、DomainOwner 与节点 Adapter；保留 `VillagerTradesPlugin` 和 `query/describe` | 事件声明整批验证并随 commit 应用，显式 obsolete 控制 retirement；不保留旧部分跳过/手动 flush 管线 |

A22 已追认的两个 1.21.1 Script 方法和 A28 已追认的精确接口/teardown 变化不重新申请。此范围不涉及用户数据删除、不改变协议、不授权正式发布、远程上传或最终回滚政策。

## 已准备的技术条件

- 确认每个生产入口、保留 backend 和节点变体；旧交易钩子在旧入口仍存活时不能提前摘除。
- 新启动声明补齐计算类型名重载与同源 `RegistrySugar`；旧 14 参数构造函数的内部测试消费者已迁移至当前构造形状。
- `:common:check :26.2.0:test --tests '*StartupRegistryDefaultDeclarationsTest'` 已通过。完整节点与实机结果另行记录，不由该局部通过推定。

维护者结论（2026-10-15 主会话原文）：**「批准上述范围，验证通过后删除」**。批准仅适用于上表及问题中明确列出的 A15/A16/A22 范围；不扩大到正式发布或回滚政策。验证和最终删除结果另附。
