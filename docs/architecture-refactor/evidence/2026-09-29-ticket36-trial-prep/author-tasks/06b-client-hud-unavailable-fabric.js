// 票 36 试做示例 6b：client 事件面 —— fabric 节点 HUD 渲染面的显式拒绝变体
//
// 节点：26.1.2-fabric / 26.2.0-fabric（放置：client_scripts/）。
// capability 事实（票 26 MIGRATION §4 + platformGate 基线行
// `ClientEvents node=<fabric> buses=tick,tickPost,tickPre`）：
//   * fabric 的 ClientEvents 是同名子集组（FabricClientEventBindings），只有
//     tick/tickPre/tickPost 三个总线；
//   * `ClientEvents.hudRender` / `ClientEvents.hud` / `ClientRenderRegistry` /
//     ClientRenderDomainOwner 不在 fabric 生成源码中（neoforge 守卫为假）；
//   * KeyBindEvents 在 fabric 可用（独立孪生：register/pressed/released/tick）。
//
// 预期（显式拒绝，不是静默 no-op）：`ClientEvents.hudRender(...)` 在 fabric 上触发
// 成员解析错误，形如（EventGroupJS.getMember）：
//   No such event bus: ClientEvents.hudRender
// KeyBindEvents 段预期照常工作（真实按键端到端归真机试做验证）。记录两侧实际输出。
// 本变体不是「在 fabric 上寻找 HUD 渲染替代路径」的练习——渲染注册面在 fabric 就是
// 不可用，试做只验证拒绝是否明确、公开材料（wiki/票 26 迁移表）是否提前说清。

KeyBindEvents.register('demo:dash', 'key.keyboard.r', 'movement')

KeyBindEvents.pressed('demo:dash', event => {
  console.log('pressed ' + event.id + ' down=' + event.down)
})

// NeoForge 面写法。在 fabric 上运行本文件预期：脚本加载/执行在下面这行失败，错误点名
// ClientEvents.hudRender 不是本节点事件总线。
ClientEvents.hudRender('demo:badge', { layer: 'foreground', priority: 100 }, (ctx, gui) => {
  ctx.rect(4, 18, 96, 12, 0x40000000)
  ctx.text('DASH READY', 6, 20, 0xFF55FF55)
})
