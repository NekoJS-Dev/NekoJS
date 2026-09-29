// 票 36 试做示例 2b：Dynamic Registry —— 无该能力的节点上的显式拒绝变体
//
// 节点：1.21.1 / 26.1.2-fabric / 26.2.0-fabric（放置：server_scripts/）。
// capability 事实（票 16 REPORT §3 + 票 21 REPORT §4/§5，platformGate 基线行
// `DynamicRegistryEvents node=<fabric/1.21.1> state=not-verified`）：
//   * 新 facade 的事件组 `DynamicRegistryEvents` 只在 26.x NeoForge 注册
//     （DynamicRegistryPlugin 整文件 `neoforge && >=26` 守卫）；
//   * 旧直注 binding `DynamicRegistry`（`DynamicRegistry.item/soundEvent/mobEffect`，
//     `ServerEvents.started` 内直注、真实 registry mutation）同样只在 26.x NeoForge，
//     且受 `nekojs/config/engine.toml [dynamicRegistry] enabled = true` 门控——
//     **默认 false**。默认关闭下调用会得到（DynamicRegistryJS.requireRunningServer）：
//       DynamicRegistry is disabled: set [dynamicRegistry] enabled = true in
//       nekojs/config/engine.toml and restart the game
//     （无运行中的 server 时则提示移进 ServerEvents.started 回调或 reload。）
//
// 预期（显式拒绝，两段都要记录实际输出）：
//   1) 本文件在新 facade 上的写法：`DynamicRegistryEvents` 在这些节点不是已注册绑定，
//      脚本加载/预检预期得到「未知标识符」类定位诊断（GlobalBindingMemberValidator：
//      "Unknown identifier 'DynamicRegistryEvents'…"），执行预期 ReferenceError——
//      两者都必须是点名、可定位的失败，不允许静默 no-op。
//   2) （可选，26.x NeoForge 上验证配置门）在 26.x NeoForge 默认配置下调用旧
//      `DynamicRegistry.item(...)`，预期得到上面引用的 disabled 提示。
// 不允许把本变体当成「在无能力节点上寻找动态注册替代路径」的练习——动态注册在
// 这些节点就是不可用，试做只验证拒绝是否明确、公开材料是否提前说清了门禁。

// 新 facade 写法（26.x NeoForge 面的推荐入口）。在本文件列出的节点上运行预期失败：
DynamicRegistryEvents.dynamicRegistry(event => {
  event.item('mymod:ruby', b => { b.maxStackSize = 16 })
})
