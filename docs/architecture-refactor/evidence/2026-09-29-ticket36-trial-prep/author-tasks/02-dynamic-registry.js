// 票 36 脚本作者试做示例 2/11：Dynamic Registry（服务器运行期动态注册）
//
// 来源：逐字复用票 21 基线示例
// docs/architecture-refactor/baseline/2026-09-22-registry-dynamic-sync/examples/dynamic-registry-transaction.js
// （16 号候选计划 fixture 转换而来的生产最小示例，AC10）。
// 放置：server_scripts/dynamic_registry.js。
// 节点（票 21 REPORT §5 能力表 + platformGate 基线）：声明面只在 26.x NeoForge 存在
// （26.1.2 / 26.2.0）；平台侧激活（真实 registry mutation / 多人同步）三类候选类型
// （Item/SoundEvent/MobEffect）均 not verified，公开激活阻塞。1.21.1 与两个 fabric 节点
// 没有该事件组（见 02b 变体）。旧直注 binding `DynamicRegistry`（[dynamicRegistry]
// 配置门，默认 false）与本次试做的新 facade 是两条独立路径，见 02b 头注。
//
// 试做预期可见效果（诚实口径）：reload 成功、声明进入 inert 候选计划与账本，不发生
// 热更新——这不是缺陷，是当前激活门禁的预期状态；试做要验证的是：公开材料是否足以
// 让脚本作者在动手前就知道这一点（AC2/AC4）。

// server_scripts/dynamic_registry.js —— 服务器运行期动态注册「批事务」生产最小示例
// （ticket 21 / AC10，由 16 号候选计划 fixture 转换而来）
//
// ⚠ 激活门禁现状（诚实例，2026-09-22）：
//   本示例的声明面（DynamicRegistryEvents.dynamicRegistry + 类型直达 Builder）与
//   事务管线（preflight → 同 key fingerprint 冲突 → 服务端 prepare → 客户端
//   prepare/ack → 受控 commit）已经由 ticket 21 的 common 层事务实现与 JVM 测试
//   证明；但**平台侧激活（真实数值 ID 分配 / registry surgery / 网络 payload 传输）
//   尚未接线**：没有任何候选类型通过「目标 Adapter + 事务 + 同步」三门，三类均记
//   not verified 并阻塞公开激活。生产环境中本示例当前的可见效果与 ticket 16 相同
//   ——声明进入 inert 候选计划与账本（claim/stale/exposed 可查询），不发生热更新。
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
// （试做时可把上一行改回 16→32 再 `/nekojs reload server` 观察：reload 以
//   dynamic-registry-conflict 结构化失败、旧 active 继续服务——这是本家族的
//   「实际诊断输出」采集点。）
