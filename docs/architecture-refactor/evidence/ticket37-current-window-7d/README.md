# 当前 7d 官方 JAR 的真实加入、abort 和恢复窗口

源码 `7d77308bfa400721304b20940ada91092180a1ed`，NeoForge 26.2 官方 JAR SHA256 `2a64c34ca2d7abc3e8b2cd6402b17f204b3c10b4d15fcd1c2ebc917b3dd9aff2`。两次专属环境各安装四份相同 JAR，每份 1592 个 Neko 类与当前正式编译输出逐字节一致；第二次使用全新 world/profile。没有生产源码、安全检查、协议或公开接口变更。

第二次真实事务结果通过：C 初始配置追赶至第 2 代；代理暂缓 C 的真实 PREPARE，第 3 代等待 ACK 时 D 加入配置阶段。D 的 PREPARE 尚未交付即断线，服务端记录 joined-mid-transaction 和 disconnected-before-ack，整批 ABORTED，activatedGeneration 保持 2，该代没有 COMMIT，新物品 `ticket37_b7:abort_item` 不存在。恢复重载至第 4 代，C 的一份真实 ACK、一份 activation-report 和物品 give 成功。随后再次暂缓 C 的 PREPARE，E 在第 5 代等待 ACK 时加入，接收原始 PREPARE/COMMIT，服务端收到两份真实 ACK、两份 activation-report；E 完成配置任务并实际登录，give 成功。

首次尝试失败必须保留：D 握手被暂缓约 38 秒，放行后连接关闭，没有 D PREPARE；验收脚本退出 1，C 的代理 8 秒等待上限随后到期断线。这一轮实际因 C 断线 abort，不能用于证明 D 加入窗口。第二次仅改变调度，让证明脚本先于 D/E 启动持续等待；代理期限和转发实现不变，握手实际等待约 0.257/0.234 秒。结果支持“等待过久”这一解释，但没有确定客户端或服务端哪一层超时机制。没有覆盖失败日志或伪造判定。

代理解析 VarInt、可选压缩与现有动态同步 JSON，记录帧 SHA256/长度，转发原帧；没有改字节或伪造客户端回复。D 预期断线的帧不转发，其余已转发动态帧都有对应原始帧记录。结束时代理日志出现关闭套接字的 OSError；这是正常停止的原始记录，不删去。无 HTTP bridge、跨进程 GLFW 激活或键鼠操作。隐藏控制台并不表示在另一个 Windows 桌面测试。

第一轮 server/C/D 和第二轮 server/C/D/E JVM 全部直接退出 0：客户端由自己 CLIENT tick 上的 `Minecraft.stop()` 关闭，服务端 RCON 正常 stop；第二轮各代理/证明/客户端 wrapper 和 orchestrator 也均退出 0。没有强杀，没有 hs_err，25995–25998 端口归档时均空闲。未重复完整构建矩阵：此轮只新增实机验收与证据；此前精确源码的矩阵结果见 [Node 修复验收](../ticket37-node-assert-repair/README.md)。

运行命令与实际 helper 源码在 ZIP 的 `helpers/`：`prepare-current-window-7d-attempt2.py`，`run-current-window-7d-attempt2.py server/C`，`proxy-current-window-7d-attempt2.py C 25997`，`orchestrate-current-window-attempt2.py`；后者顺序启动真实 D/E 代理、证明脚本和客户端，正常关闭本轮所有进程。第一次 helper、失败输出、两次完整应用日志、原始代理 JSONL、RCON 实际响应、fixture、进程/退出记录、源码/制品绑定均冻结。

`current-window-evidence.zip`：129 个条目，321556 bytes，SHA256 `a5b279e763b9240b63db6e1900749b270ebec4d1f3321f983b700288bbfa4a45`。`manifest.json` 列出每个条目哈希；`runtime-summary.json` 记录范围、判定和类绑定。独立运行 `python docs/architecture-refactor/evidence/ticket37-current-window-7d/verify.py`：PASS，核对 ZIP、首次失败、原始状态/阶段/ACK、give、帧关联与正常退出。密码、server.properties、world、启动参数文件、二进制、原始 JFR/hs_err 和无关用户文件不入库；实际两份 RCON 密码扫描通过。

本包只证明当前 NeoForge 26.2 的上述窗口。资源包下载进行中的加入/重载、实际第一像素帧、其他客户端节点、剩余领域及磁盘故障验收仍未完成；完整默认 IDE/Pyright 与正式重载性能仍 FAIL。维护者结论 NOT_RECORDED，总体 NOT_COMPLETE，自动续跑 PAUSED，工具权限字段问题 UNRESOLVED。A22/A28 边界和发布/回滚要求不变，没有发布授权或整体人类验收通过结论。
