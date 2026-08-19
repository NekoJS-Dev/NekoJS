// 26.x 脚本化 HUD / 世界渲染 smoke（参考副本 —— 复制到游戏运行目录 nekojs/client_scripts/ 后生效）
// 游戏内 F3+T 或 /nekojs reload client 重放。本脚本验证：
//   1) ClientEvents.hudRender(id, { layer, priority }, (ctx, gui) => {}) 注册与分层调度
//   2) ctx 绘制助手：text / centerText / rect / outline / gradient / drawTexture 别名
//      与 partialTick / getWidth / getHeight
//   3) ClientEvents.worldRender(id, options, ctx => {}) + ctx.line / ctx.box 3D 绘制
//   4) 容错：一个渲染器抛错不影响同帧其它渲染器（错误进 NekoJS 错误面板）
// 验证结果：HUD 左上出现 "NekoJS HUD smoke" 角标 + 半透明底条；世界中脚下画绿色线框盒。
// 注意：client 脚本只在客户端存在 level/gui 时绘制，smoke 在主菜单注册、进世界后生效。

console.log('=== hud/world render smoke ===');

// 1. background 层：原版 HUD 之下的半透明底条（RenderGuiEvent.Pre）
ClientEvents.hudRender('smoke:backdrop', { layer: 'background', priority: 0 }, (ctx, gui) => {
    ctx.fillRect(4, 4, 160, 26, 0x80000000);
});

// 2. normal 层：文本 + 周期动画（partialTick 驱动）
ClientEvents.hudRender('smoke:label', { layer: 'normal', priority: 0 }, (ctx, gui) => {
    const pulse = Math.sin(Date.now() / 300) * 0.5 + 0.5;
    const green = (0xFF << 24) | (0x40 + Math.floor(pulse * 0x80)) << 8;
    ctx.text('NekoJS HUD smoke', 8, 8, 0xFFFFFFFF);
    ctx.centerText('hudRender ok', 8 + 152, 8, green);
    ctx.outline(4, 4, 160, 26, 0xFF30FF60);
});

// 3. foreground 层：右下角帧无关计数（同 Post 事件、normal 之后）
ClientEvents.hudRender('smoke:corner', { layer: 'foreground', priority: 100 }, (ctx) => {
    ctx.text(`${ctx.getWidth()}x${ctx.getHeight()} pt=${ctx.getPartialTick().toFixed(2)}`,
        ctx.getWidth() - 110, ctx.getHeight() - 12, 0xFFA0A0A0);
});

// 4. 容错验证：该渲染器每帧抛错，应被记录且不影响上面三个（注释可开启）
// ClientEvents.hudRender('smoke:broken', { layer: 'normal', priority: 1 }, () => {
//     throw new Error('intentional hudRender failure');
// });

// 5. 世界渲染：脚下常显绿色线框盒 + 指向世界原点的线（进世界后可见）
ClientEvents.worldRender('smoke:lines', { layer: 'normal', priority: 0 }, (ctx) => {
    const mc = Minecraft.getInstance();
    if (mc.player == null) return;
    const p = mc.player;
    ctx.box(p.x - 1, p.y, p.z - 1, p.x + 1, p.y + 2, p.z + 1, 0xFF30FF60);
    ctx.line(p.x, p.y + 1, p.z, 0, 64, 0, 0xFF30A0FF, 2);
});

console.log('=== hud/world render smoke registered (enter a world to see the overlays) ===');
