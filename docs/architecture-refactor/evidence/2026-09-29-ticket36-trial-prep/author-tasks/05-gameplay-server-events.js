// 票 36 脚本作者试做示例 5/11：server 事件面（gameplay events）
//
// 来源：逐字复用票 24 基线示例
// docs/architecture-refactor/baseline/2026-09-22-gameplay-event-surface/examples/gameplay-events.js
// 放置：<gameDir>/nekojs/server_scripts/*.js
// 节点：事件族在五节点 platformGate 均 present（票 24 复验 26.1.2 / 26.1.2-fabric /
// 1.21.1 三节点 0 失败、0 member-drift）；本示例用到的成员在 fabric 上的子集与
// 差异（drops 载荷、chat 不可取消等）见票 24 catalog-snapshot.md 的对照表与示例内注释。
//
// 票 24 最小可运行示例：Block/Item/Level/Player/Command/Entity 事件族（server 脚本）
// 放置：<gameDir>/nekojs/server_scripts/*.js
//
// 只使用本票盘点的既有事件族成员（公开名、payload、side、priority/cancel 语义见
// Ticket24GameplayEventCatalogTest 的契约快照；成员名按 26.x 原生载荷 javadoc 核对）。
// 全部为「顶层脚本作用域注册」。

// ---- 1) PlayerEvents：登录欢迎与聊天（chat 可取消：return true）----
PlayerEvents.loggedIn(event => {
  console.log('welcome entity=' + event.entity)
})

PlayerEvents.chat(event => {
  console.log('<' + event.username + '> ' + event.rawText)
  // return true  // 取消这条聊天（可取消总线：监听器返回 true = 取消）
})

// 带优先级的多重订阅：同族总线可挂多个监听器，HIGHEST 先于 NORMAL 执行。
PlayerEvents.chat('HIGH', event => {
  console.log('(high priority sees the chat first)')
})

// ---- 2) EntityEvents：按实体类型定向 + 生命周期代表 ----
EntityEvents.death('minecraft:zombie', event => {
  console.log('a zombie died to ' + event.source.getMsgId())
})

EntityEvents.joinLevel('minecraft:creeper', event => {
  console.log('creeper joined ' + event.level.dimension().identifier())
})

EntityEvents.damagePre('minecraft:villager', event => {
  console.log('villager taking ' + event.originalDamage + ' damage')
  // return true  // 免除本次伤害（damagePre 可取消）
})

EntityEvents.drops('minecraft:skeleton', event => {
  // drops 的载荷跨加载器有差异：NeoForge 可读原生掉落集合，fabric 侧恒为空列表（已知差异）
  console.log('skeleton dropped ' + event.drops.size + ' item entities')
})

// ---- 3) LevelEvents：维度加载与爆炸（别名成员 tick/beforeExplosion 已 @Deprecated）----
LevelEvents.loaded(event => {
  console.log('level loaded: ' + event.level.dimension().identifier())
})

LevelEvents.explosionStart(event => {
  console.log('an explosion is starting')
  // return true  // 取消整场爆炸（explosionStart 可取消）
})

// ---- 4) CommandEvents：命令执行拦截（注意与「注册 /nekojs 管理命令」的票 20 无关）----
CommandEvents.command(event => {
  const input = String(event.parseResults.reader.getString())
  if (input.startsWith('/give')) {
    console.log('intercepting give: ' + input)
    return true // 取消该命令（command 可取消：return true）
  }
})

// ---- 5) ItemEvents：按物品定向的食物/拾取监听----
ItemEvents.foodEaten('minecraft:golden_apple', event => {
  console.log('someone ate a golden apple')
})

ItemEvents.canPickUp('minecraft:diamond', event => {
  // return true  // 阻止拾取钻石（canPickUp 可取消；pickedUpPre 为其冗余别名，建议用主名）
})

// ItemEvents.modification 只验证事件面接线：candidate/snapshot 语义归票 39，
// 见 baseline/2026-09-16-item-block-modification/examples。
ItemEvents.modification(event => {
  event.modify('minecraft:diamond', item => { item.maxStackSize = 16 })
})

// ---- 6) BlockEvents：中立载荷 + dispatch by block id ----
BlockEvents.broken('minecraft:diamond_ore', event => {
  console.log('diamond ore broken')
  // D2 已修复（2026-09-29 合入 mult）：broken 可取消（return true 阻止破坏，
  // 2026-09-28 真机会话验证）。本文件起草于修复前的基线，旧注释已按修复后语义更正。
})

BlockEvents.rightClicked('minecraft:crafting_table', event => {
  // SERVER 总线只投递服务端实例（双逻辑侧过滤），客户端交互走 CLIENT 脚本
  console.log('crafting table used at ' + event.pos.toShortString())
})
