# 世界包 CLIENT 登录加载修复

结果：**NeoForge 本地世界加入后的 CLIENT 自动加载已修复；同进程 A→B→A、签名网络及完整内存验收仍未完成。**

原始客户端 A 只有 SERVER entry，初始 CLIENT 资源 reload 早于世界包发布。新增接线检查在原实现上失败后，在共享 26.x 与 1.21.1 的 NekoJSClient 上注册 LoggingIn listener；仅 memory connection 且 WORLD 集合非空时，使用已有事务 CLIENT reload。远端包继续由 configuration-phase 同步路径激活，失败记入 NEKO-1017 与错误跟踪器。没有新增 Script API、wire、持久化格式或运行时所有者。

实际客户端 PID 9932 的 client-launch-6.log 首次记录 Render thread 自动执行 A 的 CLIENT probe（activation=3/count=1）和 consumer（world=A/count=1）。这轮没有执行 CLIENT reload 命令。此前 PID 29460 的 client-launch-5.log 只有 SERVER entry。1625 个 main 类的前后哈希仅 NekoJSClient.class 改变；common 生产类完全相同。

验证：

- 定向接线测试：原实现 1 failed；修复后整个 PackSyncBridgeSourceTraceTest 通过。
- `gradlew.bat guardLint :1.21.1:build :26.1.2:build :26.2.0:build --console=plain`：PASS，3m14s，67 tasks。三节点 XML 分别为 450 / 683 / 683 tests，0 failures/errors，31 / 81 / 81 skipped；未将 skipped 计为已执行通过。
- `python tools/nekojs-ci-gates.py subsets|processor|declaration|nodes --json`：分别 PASS。
- `python docs/architecture-refactor/evidence/2026-10-18-client-world-login/verify.py`：PASS，独立核对 ZIP 内所有哈希、red/green XML、节点结果与修复前后实际 entry。

`evidence.zip` 为 1,596,666 bytes、515 entries，SHA-256 `b5159d765f184adb4cd0ed0bb1b08c6e8fe7cefd0d6174c1e9136145cccd7dc4`。包含本轮源码 diff、节点测试、首次失败、准备脚本、fixture、屏幕兼容适配来源和实际截图/日志；详细清单在 manifest.json。仅文本中已知 RCON 凭据被替换，二进制未做字节替换；私钥、server.properties、存档 NBT/玩家数据和 vendor jar 均未收录。

测试依赖限制与所有失败记录见[完整执行记录](../../../research/2026-10-18-pack-client-acceptance.md)：GraalMC 图标元数据、MCP pack.mcmeta 和 26.2 Gui screen owner 只在 owned 测试副本修正。当前 MCP v3 能打开并枚举真实 PauseScreen，但没有接管按钮，label 也未正确读取；人工接管未激活，继续调查。JEI 的旧 Foreground 事件引用失败由其自身捕获，不能声称可选集成通过。

本证据在客户端仍运行、停留暂停菜单时冻结，不包含正常退出/清理或完整切世界结论。服务端通过 RCON 正常停止；前几次兼容准备客户端为验证创建时间后强制结束，不能计作正常退出。默认运行条件的重载性能 FAIL 仍有效。
