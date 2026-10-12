# 当前 d109：并发窗口 GLFW 崩溃最小复现

当前官方 NekoJS 制品没有修复此原生问题。本轮在两个真实客户端并发时再次触发 `glfw.dll+0x10fa1`，随后独立 GLFW 程序、无 OpenGL/回调程序及打印真实窗口指针的程序均复现同一故障。指针控制证明：A 的故障寄存器 RBX 等于 B 进程实际创建的 GLFW 窗口指针，而不是 A 的窗口指针；故障读取地址恰好是该指针加 `0x1f0`。单 GLFW 进程的三次实际 Sky 激活和正常退出通过。

这证明本次复现不依赖 Minecraft、NeoForge、Graal、NekoJS、HTTP bridge、OpenGL 上下文或 guest 回调。它不证明第三方 DLL、窗口工具或所有历史崩溃已经修好。本轮没有修改生产源码、第三方二进制、安全权限或用户环境。

## 精确环境及实际过程

- 生产源码 `d10912ebbf24d9d5df75a908dc7a5529da09979a`；checkout `e5ec1e4c0a4fc405ead0c1f80c46d198e692536e`，差异仅文档。
- NeoForge `26.2.0.75`、Oracle JDK `25.0.2`、LWJGL Java 包 `3.4.1`。实际 `glfwGetVersionString()` 为 `3.5.0 Win32 WGL Null EGL OSMesa VisualC DLL`，不能把 LWJGL 包版本写成 GLFW 版本。
- 官方 JAR SHA256 `fde6c16ba1b6fbca43dfc87aff4bf9709c1925bcb2d72c307f2e73346922f092`，专属 server/A/B 三份实际复制品一致。最小程序复用 A 实际加载的同一 GLFW DLL；Java包及三个原生 DLL 哈希保存在各次 `inputs.json`。
- 新专属世界、合成离线账号、127.0.0.1:25983/RCON25984。A/B 均不含 HTTP bridge，权限配置沿用既有 fixture。
- A/B 均真实完成 generation2 配置 catch-up 并入服。A 初次精确标题绑定失败是加载标题变为 Multiplayer，并非崩溃；刷新后用先前实际返回的窗口 ID 重新绑定。第一次捕获被其他窗口遮挡，没有归档为画面证据。
- 激活 A 返回 `failed to activate captured window`。随后刷新确认窗口消失，直接 JVM 退出码1，实际 hs_err 为 Render thread `glfwPollEvents` / `+0x10fa1`。因此计划中的三轮 A/B 切换、F2/F3T、多人大重载均没有执行，不能标为通过。
- B 经 tick owner 的 `Minecraft.stop()` 正常退出0（0.703秒）；服务器 RCON stop 正常退出0。没有强制清理，也没有操作未知或用户进程。

## 最小程序与控制

| 场景 | 实际操作和结果 |
|---|---|
| 两个独立 OpenGL GLFW JVM | A 被 B 遮挡，观察后调用一次 Sky activate；A `+0x10fa1` / 直接1，B 文件信号正常0 |
| 两个无 OpenGL、无回调 GLFW JVM | 同一激活路径；A `+0x10fa1` / 直接1，B 正常0 |
| 同一无 API 程序，增加实际窗口指针打印 | 同一故障，A RBX `0x1e9691506b0` 等于 B 的实际窗口指针，A 自己的指针为 `0x2bffe44e160`；读取 `0x1e9691508a0`，偏移 `0x1f0` |
| 仅一个相同无 API JVM | 三次分开的 observe/action/refresh 激活调用均成功，文件信号关闭，直接0 |

前三组不是“无异常即通过”的抽象单测：它们实际创建 Windows GLFW 窗口，并由相同 Sky 动作触发与真实客户端相同的 DLL 故障。删除第二个 GLFW 进程后的单进程对照没有复现。没有绕开窗口工具另写 Windows 输入脚本。

实际 DLL 反汇编保存 `GetActiveWindow` → `GetPropW` → `GetKeyState` → `cmp byte ptr [rbx + 0x1f0], 1`。最后一条正是崩溃偏移 `0x10fa1`。GLFW 官方[Win32事件轮询源码](https://github.com/glfw/glfw/blob/master/src/win32_window.c)也包含读取 active window 的 `GLFW` property 后检查 modifier key 的路径；此移动分支只是机制参考，未声称它与所装 DLL 的源提交逐字绑定。具体是谁共享了输入队列、工具内部是否调用 AttachThreadInput，没有检查或修改工具实现，仍不能据此断言。

## 证据与复跑

[运行结果](runtime-summary.json)、[清单](manifest.json)、[原始证据压缩包](native-focus-evidence.zip)、[独立验证器](verify.py)。压缩包75条，62516字节，SHA256 `9d4b60a824673bdc8088b53ffa0f7f52d227d79ff5dd4225cf3cc4e48ce604e3`。保留全部原始失败、实际退出记录、最小 Java/Python 源码、DLL反汇编和四份 hs_err 的信号摘录及原文件哈希。

完整 hs_err（含环境/参数）、RCON密码、world、启动参数、二进制及无关屏幕不入库。`build/ticket37-native-pair-d109` 为本地原始环境；helper 位于 `build/ticket37-config-sync-repair`。fresh-root/assert/exclusive-create 防止覆盖历史环境，直接重跑旧准备器会拒绝已有目录；再次复现必须另建独占路径。

```powershell
# Already executed with the bundled Python. Source copies are retained in the ZIP.
python build/ticket37-config-sync-repair/prepare-native-pair-d109.py
python build/ticket37-config-sync-repair/run-native-pair-d109.py server
python build/ticket37-config-sync-repair/run-native-pair-d109.py A
python build/ticket37-config-sync-repair/run-native-pair-d109.py B
python build/ticket37-config-sync-repair/run-native-focus-probe.py prepare
python build/ticket37-config-sync-repair/run-native-noapi-probe.py prepare
python build/ticket37-config-sync-repair/prepare-native-pointer-control.py
python docs/architecture-refactor/evidence/ticket37-native-focus-d109/verify.py
```

实际 Windows 操作使用 computer-use 技能的刷新/唯一返回窗口绑定规则。原生窗口准备与游戏内 tick-owner/RCON语义测试不承担 Windows 输入。最小程序含300秒自动结束上限；所有剩余实例均提前通过自有文件信号关闭。

## 剩余工作

本次并发原生验收 **FAIL**；缩小故障及证据完整性 **PASS**。第三方 GLFW/Sky组合没有修复。后续多人协议测试使用真实并发客户端，但避开跨 GLFW 进程的窗口激活；原生画面通过游戏截图入口和各客户端自己的执行线程取得，不能用状态日志替代像素验收。单客户端 Computer Use 可以继续。

本轮未重新执行 current d109 全量 IDE、正式性能、跨节点视觉、下载窗口或第一像素验收；真实维护者结论未记录。A22/A28及发布授权边界、自动续跑暂停、工具权限字段问题未解决均不变，整体未完成。
