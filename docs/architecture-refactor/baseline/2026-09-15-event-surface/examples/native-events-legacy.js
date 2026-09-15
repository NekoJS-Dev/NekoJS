// NekoJS NativeEvents 最小示例（票 14 AC10）
//
// !! tier：legacy/raw Adapter 观察面（NeoForge-only）!!
// - 不是 managed stable：不经 managed contract/catalog 派生，不进 TS/Python
//   managed declaration；声明来源是 TypeDocCatalogEntry（probe 补全/文档）。
// - 只在 STARTUP 脚本可用；STARTUP reload 时上一轮原生监听器整体注销后重挂。
// - 监听器返回 true 会取消可取消事件（ICancellableEvent）。
// - Fabric 侧无此面（loader 专属 raw bridge；迁移建议见 MIGRATION.md）。
//
// 放置：startup_scripts/native.js
// 运行：需要真实 NeoForge 游戏 bus（/reload STARTUP 脚本即重挂）——本示例
// 不在单测中执行（单测无法进入 FML loader），行为由根树
// NativeEventsLegacyTierCharacterizationTest 在 tier/注册面上 characterization。

// 普通监听：类名或 Class 对象 + 回调
NativeEvents.onEvent('net.neoforged.neoforge.event.entity.living.LivingDeathEvent', event => {
  console.log('entity died: ' + event.getEntity());
});

// 显式优先级 + 接收已取消事件；返回 true 取消（仅可取消事件生效）
NativeEvents.onEventTyped('HIGH', false,
    'net.neoforged.neoforge.event.level.BlockEvent.BreakEvent', event => {
  if (String(event.getPlayer().getName()) === 'Steve') {
    return true; // 取消这次破坏
  }
  return false;
});
