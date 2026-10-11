# 精确 93f4 客户端与双人验收：部分通过，整体失败

2026-10-11 两轮专属测试，2026-10-12 补齐退出检查和归档。生产源码 `93f4b259ffafb6466918582fcfa950c2c2480d77`，测试 checkout `045c8d15d373313d9ad641b7df08bb684ebf7570` 仅比该源码增加文档。六份实际安装的官方 NeoForge26.2 JAR 均为 SHA256 `59d97eb34b670639e278f9a0558cd03e813e5b556061c209346837d6d1c24447`；逐份检查此前修复的十四个编译条目。NeoForge26.2.0.75/JDK25.0.2，GraalMC 和 Minecraft MCP 0.4.2，专属新世界、合成离线身份，没有动态 ID 客户端预注册。无生产源码、HostAccess/ClassFilter、路径门禁、协议或公开 API 变更。

## 有限通过项

- 初轮服务器动态事务 generation3 正常 COMMIT，两个实际 participant 的 ACK 与 `activated` 回报完整对应；先前 generation2 配置 catch-up 完成。RCON 的脚本 generation2 不等于动态事务 generation3。不能仅依据 RCON reload 响应作结论。
- 初轮 A 的声明 generation1/2/3 第一个 HUD 回调均读到声明；第2/3代读到 `current=minecraft:invert/active=true`。故意抛错的候选保留 `RELOAD_CANCEL` 和原始原因，旧第2代 callback 继续可用；移走专属失败 fixture 后安装第3代，状态恢复。这是状态验收，不认证首个像素帧。
- 续跑 A 第1代资源覆盖的实际像素清晰与模糊不同，分别见 [清晰世界](a-clear.png) 和 [模糊世界](a-blur.png)。Computer Use 观察到条纹墙清晰→模糊，并实际发送 F3+T；新第2代首个 HUD 回调读到声明和 active 状态。原始游戏 API 截图和状态回读均保留。

## 截图验收纠正

先前对“B 两张截图”“重载后 clear/blur 及恢复画面”的通过表述过早；归档逐项核验发现四组文件完全相同：初轮 `inline_rejected.png/clear_after_reload.png`、`recovered_clear.png/recovered_blur.png`，续跑 `resumed_a_reload_clear.png/resumed_a_reload_blur.png`、`resumed_b_clear.png/resumed_b_blur.png`。续跑 B 的两张实际都是另一玩家近距离挡住视野的清晰像素；A 重载后的两张实际都是清晰条纹墙。不能把成功状态回读和之后的重复/不对应截图拼成像素切换通过。

全部十四张原图保留，没有覆盖或删除重复图。截图 helper 只等待状态日志后0.25秒，缺少新帧标识与像素对应检查；截图可能未对应目标动作，具体机制未确定。初轮 `retained_after_rejection.png` 包含 Mojang 重载遮罩，也不单独认证稳定重载后画面。后续验收需要同时核对 frame freshness、动作状态及实际世界像素。完整 post-reload/B 像素切换仍 NOT VERIFIED，首帧及跨节点视觉 NOT VERIFIED/NOT RUN。

## 失败与工具恢复

- 任意 inline ID 的声明存在，但 `PostEffects.set('ticket37_client93:inline_blur')` 返回 false；当前 `PostEffectManager.set` 明确拒绝只有声明、没有 `post_effect` 资源的 ID，先前 active 保持。实际 resource-backed contract 的拒绝已观察，任意 inline ID 激活未通过，不能通过改 fixture 或生产安全配置掩盖它。
- 初轮 B 桥接端口与 A 冲突，stderr 保存 `Address already in use: bind`；B 的 API 绑定和截图 helper 失败，不是截图通过。安装桥接器的实际 `McpConfig` 字节码支持 `mcp.port`/`MC_MCP_PORT`；续跑用 JVM 参数分别指定9876/9875，两个接口实际按 PID 绑定。没有修改系统环境或桥接器制品。
- 初轮非原子信号写入曾被 CLIENT callback 读到空字符串；原始首次 helper 及日志保留。续跑只改变测试 helper，以临时文件原子替换信号，未改变生产路径检查。续跑 PLAN 仍是原计划文本；其端口说明不足，实际 runner 和 binding 记录确定独立 HTTP 端口。
- Computer Use 曾对遮挡窗口返回无关应用画面；这些画面不保存为证据。窗口操作使用新返回窗口和观察，实际激活恢复成功后才输入。两次物理 Esc 中断均停止当轮操作；没有向失效 B 窗口重试输入。
- 续跑 B 在尝试激活窗口执行 F3+T 时发生原生 `EXCEPTION_ACCESS_VIOLATION`，实际退出1，Render thread 的 `glfw.dll+0x10fa1` / `RenderSystem.pollEvents`。工具报告激活失败，重新列窗口 B 已消失。不能证明 F3+T 被交付，也不能因发生顺序断定工具、驱动或 NekoJS 是根因。原始 hs_err 留本地、归档 hash；stdout/stderr/直接退出保留。此前旧源码的同 frame 崩溃没有因此解决。
- 续跑 A 关闭窗口后长期留在 `DestroyJavaVM`，八个非 daemon `pool-13-thread-*` 等待普通线程池任务，尚未确定该线程池 owner；不能依据通用线程名归因桥接器或 NekoJS。2026-10-12 的 jcmd 实际线程记录保留。核实专属 PID32976、可执行路径、专属 argfile 与父 runner 后，强制清理该唯一 JVM，真实退出 `4294967295`，不是正常退出0。`forced-cleanup.json` 保留原因和身份检查。未清理用户游戏或其他进程。

## 退出与证据

初轮 B 有真实退出0；初轮 A/server 没有 direct exit 文件，后续确认进程已消失，退出原因/代码 NOT RECORDED，不伪造正常关闭。续跑服务器 RCON stop 正常退出0，B 崩溃1，A 强制清理4294967295。四个专属端口25981/25982/9876/9875可重新绑定。本轮专属客户端和服务器已经清理，没有持续构建/测试作业。

[原始包](client-evidence.zip) 131 entries、3062995 bytes、SHA256 `19f99ed0d5a97607d2540fd42fb77f94de2ce1a30d5e36583f1eee4e45006091`；[逐项清单](manifest.json)、[运行结论](runtime-summary.json)、[独立验证源码](verify.py) 与[实际验证结果](archive-verification.json)核对读回、六份 JAR/十四个编译条目、真实两个 ACK、重复图保留、直接退出及两轮精确凭据扫描。排除 properties/密码、world、账户/launcher参数、制品/库/资源、raw hs_err 和无关桌面画面。两张展示图与 ZIP 字节一致。

实际准备/启动/API/信号/归档脚本全部在 ZIP；执行入口为 `prepare-client93-visual.py`、`run-client93.py`、`prepare-client93-resumed.py` 及生成的 `*-resumed.py`。它们操作固定专属路径且有防覆写断言，复现必须使用新专属目录。Windows 输入只使用 computer-use；游戏状态与截图使用按实际 PID 核验的 Minecraft HTTP API。执行 `python docs/architecture-refactor/evidence/ticket37-client93-visual/verify.py` 验证本地制品和包；只携带 ZIP 时无法重做已排除的本地制品/精确凭据检查。

本次仅技术验收与证据整理，不重跑已通过的源码矩阵，不外推其他节点，不发布制品。技术审查接受上述有限结论，完整客户端验收 FAIL。完整 IDE、当前正式重载性能、native/退出根因、inline ID、多人下载窗口、首帧、跨节点视觉、其余领域与最终维护者/发布回滚仍未完成。真实维护者通过结论 NOT RECORDED；整体 NOT COMPLETE；自动续跑 PAUSED；工具权限字段问题 UNRESOLVED；A22/A28 精确授权边界保留。
