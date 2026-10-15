# 26.2 完成渲染后的首帧像素验收

候选生产源码 `0965e29b`，官方 JAR SHA256 `2138513affd763d19a938a0a6cd5f89994fa712b10e7dc5805dc95d4c20d1e65`，与本批五节点 reviewed 清单相同。NeoForge 26.2.0.75 / JDK 25.0.2，专属 `build/domain-first-post-frame-0965-3`，新世界和独立客户端；启动器库/资源仅只读复用已校验缓存，不运行用户 profile，不装 HTTP/MCP bridge，不改安全配置。

## 帧边界与结果

CLIENT owner tick 在玩家/世界存在且 `minecraft.gui.overlay() === null` 时调用 `PostEffects.set` 或 `clear`，返回后写入一次性 arm。STARTUP 的 `NativeEvents` 监听在首个 `RenderFrameEvent.Post` 同步消费 arm 并立即 `Screenshot.grab(minecraft, false)`；没有十 tick 延迟。记录回调与 set 时的 `minecraft.isSameThread()`，截图读回的 `ScreenshotEvent` 另行记录，PNG 文件最后单独校验。

实际 fixture 缓存的 .75 patched-client、NeoForge universal 和 bus JAR 均有 SHA256 与原始 javap 输出。`Minecraft.runTick` 偏移 517–526 调用 `GameRenderer.render`，529–533 调用 `ClientHooks.fireRenderFramePost`，576 才进入 present 段；事件总线直接同步调用监听器。`Screenshot.grab(Minecraft, false)` 选择主 render target，提交纹理复制，GPU 读回产生 NativeImage 后发布 ScreenshotEvent，随后异步写盘。因此这里证明的是**成功 set/clear 返回后首个完成渲染的主 render target 世界画面**，没有证明屏幕 present 或显示器像素。文件时间没有用作帧边界。

使用具有既有资源的 `minecraft:invert` 声明覆盖（blurRadius=16 / blurRounds=1），没有假定任意新 inline id 可激活。固定黑白墙的 ROI 为 `[230,50,630,170]`，清晰图横向灰度梯度平方均值 442.88，模糊图 13.94；校验器在最终轮开始前固定阈值：清晰 >100，模糊 0.5–50，并拒绝加载遮罩。各对照还经实际图片复核。

| 阶段 | PostEffect generation | clear / blur 的 Post 帧号 | 结果 |
|---|---:|---:|---|
| 资源重载前 | 1 | 1812 / 1852 | 首帧清晰 / 模糊，PASS |
| 实际资源重载后 | 2 | 1956 / 1993 | 首帧清晰 / 模糊，PASS |
| 候选故意抛错后 | 保持 2 | 2984（仅观察） | 没有再次 set，后续画面仍模糊，PASS |
| 移除故障输入并恢复 | 3 | 3349 / 3391 | 首帧清晰 / 模糊，PASS |

失败候选的 retained 截图是失败后的后续观察帧，不称为拒绝发生时的首帧；它只写截图 arm，没有再次激活效果。上述 generation 指 PostEffect generation，与脚本运行时 diagnostic 的 generation 分开。七次截图 request 均记录 game thread=true、overlay=false；本轮图像读回也恰好在同一 Post 帧号观察到，这只是本次读回结果，没有外推 GPU 延迟保证。客户端通过 owner tick 正常 stop，服务器随后 RCON stop，两者 exitCode=0。

原始对照：[clear 首帧](frame-clear.png)、[blur 首帧](frame-blur.png)。完整七张图、日志、源/class 绑定、javap、截图工具及历史失败见 [frame-evidence.zip](frame-evidence.zip)；[清单](frame-manifest.json)、[摘要](frame-summary.json)。归档不含 JAR、世界、启动参数或口令。

## 无效尝试与范围

首次专属 fixture 误用被禁止的 `java.lang.Thread`，STARTUP/CLIENT 脚本未加载；服务器正常停止，因 tick stop 也未加载，核对 PID、Java 可执行文件与专属参数路径后终止了这一测试客户端（原始 exitCode=4294967295）。该轮 NOTGREEN，原日志与结果保留，没有改变 allowThreads/反射/文件权限。

第二轮先验证了无资源 custom id 被显式拒绝，再改用有资源的覆盖。两张 clear 图片仍是资源加载遮罩，虽然当时机械截图记录为 PASS，像素复核判定它们无效；没有用它们验收世界首帧。最终第三轮在全新目录加入 overlay 前置条件与固定世界 ROI 检查，完整重跑上述四阶段，通过后才计入表格。所有中间图片和失败记录仍在归档。

本次只闭合所列 26.2 render-target 首帧与后续失败保留证据。1.21.1、26.1.2 客户端首帧、屏幕 present、任意新 inline id 及其他能力仍按原矩阵处理，票 28 继续 in-progress；不是正式发布或维护者整票 sign-off。
