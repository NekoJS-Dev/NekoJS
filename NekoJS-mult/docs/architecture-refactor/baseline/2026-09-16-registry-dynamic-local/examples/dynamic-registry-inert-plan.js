// server_scripts/dynamic_registry.js —— 服务器运行期动态注册「inert 候选计划」示例
// （ticket 16 / AC12 fixture）
//
// ⚠ 仅本地计划，尚未公开激活：
//   本示例提交的是 generation-scoped 的 inert 候选计划——事件收集、全规范化 fingerprint、
//   preflight、同 key 冲突、stale 记录与 inert Adapter 请求。计划不会在候选期修改 live
//   registry，公开激活（真实注册/多人同步）由后续事务/同步 gate（票 21）裁决。
//   不要把本示例当作生产脚本使用指南；生产示例与迁移材料由票 21 在 gate 通过后发布。
//   本文件由隔离 harness（common/src/test 的 DynamicRegistryInertPlanExampleTest）原样执行，
//   不作为可发布的脚本内容。
//
// 触发时机（引擎负责，脚本不用管）：
//   1) 初次 server registry ready 时收集一次；
//   2) 每次成功的 script/data reload 在候选阶段重新收集，成功 commit 才发布计划
//      （候选失败/取消不另起写入，旧 active 定义继续服务）。
//
// 候选范围（冻结）：只有 item / soundEvent / mobEffect 三个类型直达入口；没有通用 type
// catalog，未知类型名（含用注册表键冒充 type 的写法）不会变成注册能力。
//
// 命名：本 facade 住在独立事件组 DynamicRegistryEvents（成员 dynamicRegistry）。spec 08 的
// 工作名 ServerEvents.dynamicRegistry 未采用——理由与迁移口径见
// docs/architecture-refactor/baseline/2026-09-16-registry-dynamic-local/MIGRATION.md。

DynamicRegistryEvents.dynamicRegistry(event => {
  // 1) 类型直达入口 + property 写入（与显式 setter 同一条校验/规范化/fingerprint 路径）
  event.item('mymod:ruby', b => { b.maxStackSize = 16; b.rarity = 'epic' });

  // 2) 同一入口的显式 setter 形态（两种写法等价，同一 id 下 fingerprint 相同）
  event.item('mymod:sapphire', b => { b.setMaxStackSize(16).setRarity('rare') });

  // 3) 声音事件：fixedRange 可省略（默认 null = 由声音定义决定）
  event.soundEvent('mymod:boom', b => { b.setFixedRange(16) });

  // 4) 生物效果：category/color + 注册 mode（'world' 默认 / 'reloadable'）
  event.mobEffect('mymod:wither_touch', b => { b.setCategory('harmful').setColor(0x8B0000) });
});

// ---- 后续 reload 轮次的语义（由隔离 harness 的 fixture 演示，见同目录测试） ----
//
// 重复声明相同定义：同 fingerprint → 重 claim，不冲突。
//
// 同一 id 换写法（property ↔ 显式 setter）：读数与 fingerprint 不变 → 仍不冲突
//   （DynamicRegistryInertPlanExampleTest 的 parity 场景钉住）。
//
// 改动已声明 key 的定义（如把 ruby 的 maxStackSize 改成 32）：
//   → 整批冲突失败（reload 报 STATE_PLAN / dynamic-registry-conflict），
//   → 旧 active 定义继续服务；remove/replace/modify 不在第一版。
//
// 删除一条声明（脚本不再声明 mymod:boom）：
//   → boom 标记 stale/retired（不物理删除，账目与 fingerprint 保留），
//   → claim/stale/mode 可经 Registry Runtime 观察面查询。
//
// 未开放类型：本 facade 只接受 item/soundEvent/mobEffect；其它类型（含 entity_type、fluid、
// block 等）没有入口，也不算「暂时不可用」——它们从未进入候选范围（not verified，见 REPORT
// 能力表），不会以 no-op 或静默降级出现。
