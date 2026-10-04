// 票 36 试做示例 1b：启动期注册 —— fabric 节点 fluid 类型的显式拒绝变体
//
// 节点：26.1.2-fabric / 26.2.0-fabric（放置：startup_scripts/）。
// capability 事实（票 15 REPORT §3）：启动注册在 fabric 为 supported（单批直注形状），
// 但 FluidBuilder / `minecraft:fluid` 类型是 NeoForge 面 —— fabric 的糖方法目录只保留
// 平台无关的 4 个（NekoRegistryDeclarations：soundEvent/mobEffect/potion/villagerType）
//
// 预期（显式拒绝，不是静默 no-op）：`event.fluid(...)` 在 fabric 上触发成员解析错误，
// 形如：
//   RegistryEvent has no member 'fluid'; known: [soundEvent, mobEffect, potion,
//   villagerType, custom, register]   // known 列表按节点实际目录
// （来源：RegistryEventJS.getMember 的 IllegalArgumentException；头文件见
// src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEventJS.java。）
// 试做时要记录的实际输出以节点运行为准；只要它是「点名成员 + 列出已知目录」的明确
// 失败、且同批其它条目按整批语义处理（收集期毒化，不部分注册），即符合 AC3 口径。
// 不允许把本变体当成「在 fabric 上寻找 fluid 注册替代路径」的练习——fluid 注册在
// fabric 上就是不可用，试做只验证拒绝是否明确。

RegistryEvents.register(event => {
  // NeoForge 面写法（wiki/注册新内容.md 有 fluid 示例）。在 fabric 上运行本文件预期：
  // 注册收集阶段整批失败，错误点名 'fluid' 不是本节点糖方法。
  event.fluid('mymod:molten_iron', b => {
    b.density = 3000
    b.viscosity = 2000
    b.lightLevel = 12
  })
})
