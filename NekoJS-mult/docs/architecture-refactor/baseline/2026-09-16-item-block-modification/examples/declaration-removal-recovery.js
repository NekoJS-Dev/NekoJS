// server_scripts/declaration-removal-recovery.js —— 声明移除后的恢复与「阻止提交」（票 39 最小示例 4/4）
//
// 这一段示例描述**同一份脚本在三个版本上的行为**（示例本身只保留 A 段，B/C 段是注释形态，
// 便于逐段手工验证）：
//
// ── A. 声明存在（本段）────────────────────────────────────────────────────────────
//   serve 期：ItemEvents.modification 在启动收集点与每次成功 SERVER reload 的 DOMAIN_PLAN
//   阶段被 post；声明进入候选计划，commit 点由平台 Adapter 应用。
ItemEvents.modification(event => {
  event.modify('minecraft:diamond', item => item.setMaxStackSize(16))
})
//
// ── B. 把本段删掉后 reload（声明移除）──────────────────────────────────────────────
//   成功 reload = 先恢复 NekoJS 持有基线，再应用新的完整（此例为空）计划：
//     /nekojs reload server   → 钻石 maxStackSize 回到 64（不再像旧 item 路径那样 stale）
//   诊断（domain owner.lastDiagnostics()）outcome = RESTORED，可与 APPLIED 区分。
//
// ── C. 写入无法证明可恢复 / 非法值时（阻止提交）────────────────────────────────────
//   例：event.modify('minecraft:diamond', item => item.setMaxStackSize(500))
//   → 联合预检（STATE_PLAN）拒绝整批：旧 active 保持可用，本次 reload 失败并报出
//     phase=STATE_PLAN / domain=state-plan-preflight:item-block-modification；
//   → 收集期就出错的（未知目标 id、回调抛出）在 DOMAIN_PLAN 阶段整批失败，
//     domain=domain-collect:item-block-modification；
//   → 两种情况都不出现部分修改、混合 generation 或残留挂起监听器；下一轮修好的候选
//     可以正常 commit。
//
// 旧路径对比（迁移表条目）：item 侧旧实现只在「再次 modify 同一目标」时恢复快照，
// 声明移除后保持 stale；block 侧旧实现每次重放前 restore-all。现在两侧统一为
// 「恢复基线 → 应用完整新计划」。
