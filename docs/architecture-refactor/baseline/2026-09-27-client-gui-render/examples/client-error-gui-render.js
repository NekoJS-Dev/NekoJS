// 票 27 最小可运行示例：CLIENT 只读错误 GUI 消费链路 + render 资源生命周期
// 放置：<gameDir>/nekojs/client_scripts/*.js
//
// 只使用既有入口（本票不新增任何事件成员或 binding）：
//   - ClientEvents.hudRender(id, options, cb)      按 id 注册常驻 HUD 渲染器（调用即注册）
//   - ClientEvents.worldRender(id, options, cb)    按 id 注册常驻世界渲染器（调用即注册）
//   - ClientEvents.hud(painter)                    每帧 HUD 监听（参数 PainterJS）
//   - ClientEvents.screenRender(event)             Screen 渲染后监听（PainterJS + Screen）
//
// 错误呈现链路（只读，票 30 冻结 record → 票 27 GUI 投影）：
//   脚本失败（例如把下面的 id 改成非字符串触发显式失败）进入 ErrorTracker 时冻结
//   ScriptDiagnosticRecord（errorId/phase/owner/generation/source/line/...）；
//   `/nekojs view_all_errors`（或 reload 反馈）经 record 的 toErrorSummary 单点组装
//   ShowErrorListPacket 发到客户端，只读错误面板按快照呈现：列表 / 详情 / 过滤 /
//   复制 / 打开日志语义的完整原文 / 校验通过的本机定位（本地定位动作由
//   DiagnosticOpenAction seam 的同一套校验承载，远端/虚拟/越界路径明确拒绝）。
//   面板只是快照投影：不缓存可变共享状态，不提供任何编辑/保存/上传能力。

// ---- 1) HUD 常驻渲染器：候选期只记账，commit 点整批换装 ----
// 层 background|normal|foreground；同层 priority 小者先绘制；同 id 重复声明覆盖旧渲染器。
ClientEvents.hudRender('demo:badge', { layer: 'foreground', priority: 100 }, (ctx, gui) => {
  ctx.rect(4, 18, 96, 12, 0x40000000)
  ctx.text('DEMO LIVE', 6, 20, 0xFF55FF55)
})

// ---- 2) 世界渲染器：与 HUD 同一条注册/呈现/清理边界 ----
// 层 early|normal|late；回调参数为 WorldRenderContextJS（相机位置 / partialTick / 线框助手）。
ClientEvents.worldRender('demo:marker', { layer: 'normal', priority: 0 }, ctx => {
  // 世界坐标以玩家脚下为例（具体助手见 WorldRenderContextJS）
  // ctx.box(...)
})

// ---- 3) Screen 渲染监听：普通监听器（随 CLIENT reload 反注册）----
ClientEvents.screenRender(event => {
  // event.painter 与 hud 同款绘制 API；event.screen 为当前界面（只读使用）
})

// ---- 4) 本代不再声明的 id 随 reload 退役；候选失败时旧 generation 的渲染器保持服务 ----
// 试着把 id 改成数字并 F3+T：注册入口当场显式失败，候选整批拒绝，
// 旧渲染器继续绘制，错误出现在只读面板（事实来自冻结 record，不是第二事实源）。
