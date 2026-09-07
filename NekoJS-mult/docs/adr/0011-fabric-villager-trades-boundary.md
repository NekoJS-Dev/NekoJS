# Fabric 26.x VillagerTrades 集成边界

状态：设计冻结，尚未实现（2026-09-07）。

## 背景

NeoForge 26.x 的 VillagerTrades 已从 1.21.1 的静态数组模型迁移为数据驱动的：

1. Registries.VILLAGER_TRADE 中的 VillagerTrade 条目；
2. Registries.TRADE_SET 中的 TradeSet 条目，其 HolderSet<VillagerTrade> 被村民运行时消费。

当前 VillagerTradeManager 已实现 26.x 的 staged trade、registry epoch、重复应用清理和 TradeSet 替换，但 VillagerTradesJS / VillagerTradesPlugin 仍是 NeoForge-only。Fabric 26.1.2 和 26.2.0 没有对应的 TradeOfferHelper 或注册事件。

## 决策

1. 不移植 1.21.1 Fabric 的 TradeOfferHelper。它面向旧的 TradeOffers.Factory / 静态交易数组，不能构造或注册 26.x 的 VillagerTrade / TradeSet。
2. Fabric 26.x 采用两层实现：共享层保留 trade 配置解析与 staged manager；Fabric 层提供绑定/插件注册和 reload 生命周期接线。
3. Fabric reload 的正式应用点优先使用 CommonLifecycleEvents.TAGS_LOADED 之后的 registry access，只有当实际 registry epoch 或村民消费时序证明不足时才增加版本化 mixin。
4. 由于 26.x reloadable registries 在应用后被冻结，注册/移除/替换必须通过一个平台边界执行；禁止把 MappedRegistry 私有字段反射操作散落到绑定、插件或事件监听器中。优先抽出 Fabric 专用 adapter，集中处理 frozen 状态、byKey/byLocation/byValue/byId/toId/registrationInfos 和 registry epoch。
5. /nekojs reload server 必须与资源 reload 共用 beginReload/apply 协议，不能只依赖 TAGS_LOADED。

## 分阶段交付

### Phase A：公共面与生命周期

- Fabric 可见的 VillagerTradesJS 与显式插件注册。
- Fabric startup、资源 /reload、脚本 /nekojs reload server 三条路径的 beginReload/apply。
- 先用只读 pendingCount 和 registry availability 诊断验证，不宣称交易已生效。

### Phase B：registry adapter

- 两个 Fabric 26.x 节点分别验证 registry layout 和 frozen/unfreeze 操作。
- 新增 trade 与 TradeSet 替换，保留 vanilla holders、amount、allowDuplicates、randomSequence。
- 处理新 registry epoch、重复 reload、server shutdown/startup 清理。

### Phase C：运行验收

- 启动脚本调用 VillagerTrades.add 并看到 pending request。
- startup 和 datapack reload 后实际村民 restock 能提供新交易。
- 两个自定义交易同时保留 vanilla pool，不重复注册。
- /nekojs reload server 后旧交易被移除，新交易只出现一次。
- 无效 trade-set、item、count、maxUses、xp、priceMultiplier 按契约失败。

## 不做

- 不开始 1.21.1-fabric。
- 不复用旧 Fabric TradeOfferHelper。
- 不在没有真实村民 restock smoke 前开放跨加载器文档承诺。
- 不通过外部发布、GitHub Actions release、CurseForge 或 push 验收。

## 结果

这是一个需要平台 registry adapter 的完整 Fabric port，不是把 NeoForge guard 去掉或增加一个 Fabric callback。实现前必须先完成 Phase A 的边界和两个节点的 mapped registry 证据。
