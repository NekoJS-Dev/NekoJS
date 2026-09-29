# 2026-09-29 票 26 真机验证会话(维护者操作)

主会话 agent 启动 dev 客户端(`:26.1.2:runClient`,主树 mult@197853fd),维护者按提示操作,
agent 从 `run/logs/latest.log` 与截图采集证据。冒烟脚本:`t26_realmachine_smoke.js`(本目录副本)。

## 结果

| 项 | 结果 | 证据 |
|---|---|---|
| keybind 注册 | `KeyBindEvents.register('t26:dash','key.keyboard.r')` 返回句柄;两次 CLIENT 加载幂等(同句柄) | log `T26-REGISTERED ... handle=true` ×2 |
| 输入事件链 | 真实按键:PRESSED(down=true)/HELD(按住每 tick)/RELEASED 完整循环;单按+按住+连按均触发 | log 共 **209** 个 T26 输入标记 |
| consumeClick 真实状态源(AC5 缺口) | `dashKey.consumeClick()` 轮询在真实按键下消费点击,与事件链同帧 | log `T26-CONSUMECLICK poll consumed a click`(每次单按恰一条) |
| HUD 常驻 | 监听器(painter.text)与 hudRender 常驻渲染器(rect+text)均上屏,维护者目视确认 | 截图 `2026-09-29_10.16.58.png` |
| CLIENT reload 生命周期 | `/nekojs reload client` → scheduled → keybind 幂等重注册 → `reload committed: generation=3 phase=COMMIT`;HUD 文字跨代际稳定(维护者确认,无闪烁/消失/重复) | log reload 三行 + 截图 |

注:reload 响应中的 "(2 error(s) remain)" 为会话早期残留的**服务端**历史错误(旧冒烟脚本
故意错误,已在会话中清理 run 目录),与 CLIENT reload 无关。

## 新发现缺陷(记录待裁)

**D6 — 脚本侧 ARGB 颜色字面量渲染为白色**:`painter.text(..., 0xFFFFFF00)`(黄)与
`ctx.text(..., 0xFF55FF55)`(绿)在真机均渲染为白色(维护者目视)。根因假设:JS 无符号数
≥ 2³¹(所有 `0xFF......` 色)经 Graal 转入 Java `int` 参数时饱和为 `Integer.MAX_VALUE`
(0x7FFFFFFF = 半透明白)而非按位回绕;JVM 测试直接传 Java int(负数)故从未暴露。
影响面:PainterJS/HudRenderContextJS 等所有接受 int 颜色的脚本面。修法候选:颜色参数改收
Number 并按 uint32 掩码,或文档改用负数色值/提供颜色助手;属票 26 域生产行为变更,待维护者裁定。

## 供给的票

- 票 26:AC5「consumeClick 可从脚本调用者 Interface 观察(真实按键状态源)」真机证据闭合;
  AC4/AC9 的真实按键/HUD 上屏腿补齐。D6 为新缺陷记录。
