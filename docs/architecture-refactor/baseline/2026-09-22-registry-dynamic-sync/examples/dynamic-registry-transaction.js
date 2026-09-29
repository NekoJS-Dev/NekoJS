// server_scripts/dynamic_registry.js —— 服务器运行期动态注册「批事务」生产最小示例
// （ticket 21 / AC10，由 16 号候选计划 fixture 转换而来）
//
// ⚠ 激活门禁现状（诚实例，2026-09-29 更新）：
//   本示例的声明面（DynamicRegistryEvents.dynamicRegistry + 类型直达 Builder）与
//   事务管线（preflight → 同 key fingerprint 冲突 → 服务端 prepare → 客户端
//   prepare/ack → 受控 commit）已经由 ticket 21 的 common 层事务实现与 JVM 测试
//   证明。平台接线已于 2026-09-29 合入（wire 备审包：nekojs:dynamic_registry_sync
//   payload + NeoForgeDynamicRegistryAdapter 真实注册手术 + 逐 tick pump），且单节点
//   「已激活」真机演示已跑通（gate 开启的 26.1.2 dedicated server，Item+SoundEvent
//   经真实 surgery 后 LIVE 且脚本/平台可观察，见
//   ../command-output/09-runserver-26-1-2-activation.txt）。但 gate 默认仍关
//   （engine.toml [dynamicRegistry]），真多人同步（客户端 prepare/ack、STATE_SYNC
//   追平、客户端 surgery）尚未真机验证（owner 票 34），MobEffect 未在真机演示中
//   声明——能力表「真机同步 not verified」口径不变，公开激活仍阻塞。gate 关闭的
//   生产环境中本示例的可见效果与 ticket 16 相同——声明进入 inert 候选计划与账本
//   （claim/stale/exposed 可查询），不发生热更新。
//
// 启动期注册 vs 动态注册（迁移时先分清）：
//   * 启动期 `RegistryEvents.register(...)`：boot 期一次性注册，参与 vanilla
//     registry freeze，数值 ID 在世界创建前稳定 —— 需要「必须存在于世界生成前」
//     的内容（方块/实体等）继续走启动期。
//   * 动态注册（本示例）：server 已运行时由 SERVER 脚本声明；批事务裁决后才
//     激活；失败/取消/同步未完成时整批不提交，旧 active 继续服务。
//
// 失败保留（本批任何失败 ⇒ 整批不提交）：
//   - 收集期脚本异常 / watchdog 终止候选：联合 reload 边界失败，本批不入账；
//   - 同 key 定义变化（fingerprint 不同）：dynamic-registry-conflict 整批冲突；
//   - 任一客户端 prepare 拒绝 / ack 超时 / 参与者断线 / close 抢占：整批 ABORT，
//     已 stage 的 prepare 在客户端丢弃，服务端不激活，旧 active 继续服务。
//
// 同 key changed definition 限制（第一版）：
//   已声明过的 id 改定义（哪怕是属性微调）= 整批冲突失败，旧定义继续服务；
//   remove / replace / modify 不在第一版公开面。要改定义，当前唯一路径是
//   换一个新 id 声明。

DynamicRegistryEvents.dynamicRegistry(event => {
  // 最小生产示例：一件物品 + 一个音效 + 一个效果（三类即当前冻结的候选范围）
  event.item('mymod:ruby', b => { b.maxStackSize = 16; b.rarity = 'epic' });
  event.soundEvent('mymod:boom', b => { b.setFixedRange(16) });
  event.mobEffect('mymod:wither_touch', b => { b.category = 'harmful'; b.color = 0x8B0000 });
});

// 同 key 重复声明（同 fingerprint）是幂等重 claim，不是冲突；跨 reload 重复执行
// 本文件也安全 —— 相同定义得到相同 fingerprint，重声明即重 claim。
// 不要尝试：
//   event.item('mymod:ruby', b => { b.maxStackSize = 32 });  // ← 下一批改成不同定义
//   ——整批 dynamic-registry-conflict，连累同批其它新声明一起失败。
