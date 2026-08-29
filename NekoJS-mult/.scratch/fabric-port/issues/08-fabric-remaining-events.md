# 08: Fabric 余量事件：damagePre/Post + ItemEvents 子集

**What to build:** 按已确立的「中立 payload + fabric 桥 + 同名组」模式批量接通：`EntityEvents.damagePre/damagePost`（fabric `ServerLivingEntityEvents` 的 ALLOW_DAMAGE/BEFORE_DAMAGE/AFTER_DAMAGE）、`ItemEvents.rightClicked` 子集（fabric `UseBlockCallback`/`UseItemCallback`）。低优先级，模式已成熟，可一次多接几个。

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] damagePre/damagePost 在 fabric 真机触发（damage 命令冒烟）
- [ ] ItemEvents 子集至少一个事件真机触发
- [ ] 四节点编译 + 测试 + guardLint 绿
