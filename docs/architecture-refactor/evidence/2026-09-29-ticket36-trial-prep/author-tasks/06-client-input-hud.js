// 票 36 脚本作者试做示例 6/11：client 事件面（输入与 HUD）
//
// 来源：逐字复用票 26 基线示例
// docs/architecture-refactor/baseline/2026-09-21-client-input-hud/examples/client-input-hud.js
// 放置：<gameDir>/nekojs/client_scripts/*.js
// 节点 capability（票 26 MIGRATION §4 按节点差异表 + platformGate 基线）：
//   * 26.x NeoForge：KeyBindEvents + ClientEvents 全量（本示例完整适用）；
//   * 1.21.1：ClientEvents.hud/hudRender 可用，但 **KeyBindEvents 不存在**（>=26 守卫）——
//     引用即显式失败；
//   * 两个 fabric 26.x：KeyBindEvents 可用（独立孪生），ClientEvents 只有
//     tick/tickPre/tickPost——hud/hudRender 引用即显式失败（见 06b 变体）。
//   * 真实按键端到端（consumeClick 的真实点击源、HUD 实际出图）票 26 记为
//     not verified，归票 34 真机试做——本轮试做正好覆盖。
//
// 票 26 最小可运行示例：CLIENT 输入与 HUD 常驻渲染器
// 放置：<gameDir>/nekojs/client_scripts/*.js
//
// 只使用本票收口的既有入口：
//   - KeyBindEvents.register(id, key[, category])  直接创建按键绑定（返回 KeyMapping 句柄）
//   - KeyBindEvents.pressed / released / tick      按绑定 id 定向订阅输入事件
//   - ClientEvents.hud(...)                        每帧 HUD 监听（参数 PainterJS）
//   - ClientEvents.hudRender(id, options, cb)      按 id 注册常驻渲染器（调用即注册）
// 注意：register / hudRender 挂在事件组上，但语义是「调用即注册」，不是注册监听器。

// ---- 1) 按键绑定：直接注册 ----
// 同 id 幂等：重复调用返回同一句柄；绑定跨 CLIENT 脚本 reload 存活。
const dashKey = KeyBindEvents.register('demo:dash', 'key.keyboard.r', 'movement')

// 脚本也可以自己轮询：返回的是 KeyMapping 句柄，isDown() / consumeClick() 均可观察。
ClientEvents.tickPost(() => {
  if (dashKey.consumeClick()) {
    console.log('dash triggered by polling')
  }
})

// ---- 2) 输入事件：pressed / released / tick 是按绑定 id 定向的事件订阅 ----
KeyBindEvents.pressed('demo:dash', event => {
  console.log('pressed ' + event.id + ' down=' + event.down)
})

KeyBindEvents.released('demo:dash', event => {
  console.log('released ' + event.id)
})

// tick：按住期间每客户端 tick 一次（KubeJS 同款语义，松开不触发）
KeyBindEvents.tick('demo:dash', event => {
  console.log('held ' + event.id)
})

// ---- 3) HUD 每帧监听：普通监听器（随 CLIENT reload 反注册，走候选 generation 边界）----
ClientEvents.hud(painter => {
  painter.text('HUD listener', 4, 4, 0xFFFFFFFF)
})

// ---- 4) HUD 常驻渲染器：按 id 注册 ----
// 层 background|normal|foreground；同层内 priority 小者先绘制；同 id 重复注册覆盖旧渲染器。
// 候选期只记账（不上海生产路由），CLIENT reload 的 commit 点整批换装；候选失败/取消时
// 旧 generation 的渲染器继续服务。
ClientEvents.hudRender('demo:badge', { layer: 'foreground', priority: 100 }, (ctx, gui) => {
  ctx.rect(4, 18, 96, 12, 0x40000000)
  ctx.text('DASH READY', 6, 20, 0xFF55FF55)
})
