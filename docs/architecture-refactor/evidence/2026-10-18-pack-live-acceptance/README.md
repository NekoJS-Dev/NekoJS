# NeoForge 26.2 脚本包真实客户端验收

2026-10-18，在同一实际客户端 PID 25128（创建于 12:57:26.3646991 UTC）完成 A→B→A 与真实 TCP 签名同步。源码快照为 `4181b6ed9d1c5658789fd0a983d4c6827710682f`，其中产品修复为 `32504630`；之后的编译器提交只增加测试。开始/结束 1625 个 main class 摘要一致。完整流程与准备失败见[客户端执行记录](../../../research/2026-10-18-pack-client-acceptance.md)。

## 通过的行为

| 场景 | 实际结果 |
| --- | --- |
| 同 ID 世界 A→B→A | SERVER activation 1/5/9，CLIENT 3/7/11；每次 probe 私有 count=1，consumer 取得当前世界和当前 provider activation。CLIENT 在登录后自动执行，没有显式 reload 命令 |
| 为来源授权再次进入 A | SERVER 13、CLIENT 15，count=1；这是额外步骤，不混入前三次切换 |
| 四次世界退出 | 每次实际 Save & Quit 后独立观察 8 秒，没有该世界旧 tick/timer/capability marker |
| 未信任服务器 | 真实连接被拒绝，未执行 signed entry |
| 原签名首次连接 | SERVER_CACHE:packs:signedacceptance CLIENT activation=17，tick/timer 执行；服务端确认一名玩家，公钥 pin 等于原密钥 |
| 原签名、内容篡改 | 实际网络拒绝 signature mismatch，无新增 entry，篡改 marker 未执行 |
| 同 keyId 更换有效签名公钥 | 新包签名独立有效，实际网络仍按旧 pin 拒绝；无新增 entry |
| 恢复原始包与密钥 | 脚本/清单摘要恢复，再次连接 activation=18，tick/timer 执行，原 pin 保留 |
| 两次远端断线 | 实际 Disconnect 后各观察 8 秒，无旧 signed 回调；服务端确认无人在线 |
| 独立服务端退出 | RCON 正常 stop，Gradle exit 0 |

来源身份遵循实际归一化结果 `kubernetes.docker.internal`（本机 loopback 的反向名称）。MCP 的游戏命令反射失败，未通过 `/nekojs trust` 操作成功；主代理仅在 owned 配置上调用现有 `PackSyncTrustStore.trustServer`，没有放宽信任或验签。因此**游戏命令授权路径未验收**。

## 完整客户端内存观察

本轮实际运行 GraalMC 25.1.3.7，保留九组 OS 计数及 HotSpot NMT 原始输出。以下均为进程快照；Private 包含堆，NMT 也不覆盖全部第三方原生分配。

| 阶段 | Private MiB | NMT 非堆 committed MiB | 线程 | 句柄 |
| --- | ---: | ---: | ---: | ---: |
| A1 | 3213.55 | 480.05 | 92 | 1547 |
| menu1 | 3191.31 | 475.51 | 116 | 1656 |
| B1 | 2967.17 | 486.20 | 140 | 1788 |
| menu2 | 3937.03 | 483.06 | 116 | 1655 |
| A2 | 4257.83 | 493.93 | 147 | 1833 |
| menu3 | 4186.90 | 495.30 | 118 | 1675 |
| 远端有效连接 2 | 3895.36 | 508.67 | 113 | 2005 |
| menu4 | 3895.62 | 521.31 | 113 | 2009 |
| menu4 后显式 GC 观察 | 3325.66 | 483.89 | 113 | 2009 |

不同阶段包含游戏缓存、JEI、MCP 线程与不等时长；堆提交增长不能直接归于 Context，也没有新设内存门槛。最后一次显式 GC 是单独观察，不替换前面的原值。既有 common 五十次重载与 250/250 旧 Context 回收证据继续保留；这轮实机没有在 guest 保留旧 capability handle 并调用，不冒充那项实际验证。**完整游戏内 1/4/8 包的独立 Context 成本预算、长期稳定性仍未验收。**

## 控制与退出限制

MCP 0.4.2 不完整支持 26.2；只修复 owned 测试副本和额外测试 adapter。共享屏幕 owner、列表 Entry 选择、原生截图均有窄适配；未更改生产依赖或全局缓存。用户看到按钮后明确回复“已点击 MCP Take Over”，日志记录 `MCP_ACCEPTANCE_MANUAL_TAKEOVER active=true` 后才执行受控操作。没有自动开启或绕过人工激活。

保留了列表注入 V1 的 NoClassDefFoundError/超时，修正为在 owned addon 模块定义 helper 后 V2 才成功。早期黑图/纯天空为失败的反射截图，不能充当游戏画面证明；`client-signed-native-capture.png` 是实际远端暂停菜单，`client-final-world-list-native.png` 是实际 A/B 存档列表，主代理已逐张观察。名字含 world-verified 的图仍为暂停菜单，不能解释为无遮挡世界画面。

客户端实际点击 Quit Game，Minecraft/FML 已停止，但 MCP HTTP executor 的非 daemon 线程阻止 JVM 自行退出，并出现关闭后 watchdog 类加载异常。保留 thread dump；在确认 Render thread 已结束后，只关闭 owned MCP 服务及其实际 executor，未 kill 或调用 System.exit，最终 Gradle exit 0。**原生菜单退出通过，无辅助 JVM 退出失败，需要 MCP 测试工具清理。**准备期被强制结束的客户端同样不算正常退出通过。

只读调查代理曾违反限制，用 jar 解压临时创建/删除根目录 META-INF 文件；主代理立即停止其工具操作并核对 git diff/文件，未发现当前跟踪文件改动或残留。该核对不能证明更早的 ignored 文件历史。所有产品实现、测试与运行操作仍由主代理完成。

## 冻结与重算

```powershell
python docs/architecture-refactor/evidence/2026-10-18-pack-live-acceptance/verify.py
```

PASS：独立重算归档摘要、四轮世界 entry/consumer、六个 quiet 窗口、签名拒绝窗口、恢复摘要和 pin、九组 NMT/OS 数值、生产 class 绑定及退出限制。最初分析器按 UTF-8 读取 PowerShell 的 UTF-16 NMT 输出失败；仅修正解码后成功，原始日志未改、数值未重采样。

`evidence.zip` 为 264 项、2,325,895 bytes，SHA-256 `6b65111638f86b762b1b9418c44d497d81b5445bfca100ae6c8e41776c6d438c`。包含完整准备失败、动作 journal、原始客户端/服务器日志、fixture、测试 adapter 源码和绑定；排除私钥、server.properties、世界/玩家 NBT、第三方 jar 和完整依赖 classpath。已扫描本任务两份实际 RCON 凭据；仅文本脱敏，不修改二进制或客户端原始 byte 窗口。

维护者整体接受结论：**尚未收到，不由代理代签。**本轮不包含 Fabric WORLD 支持、其他节点图形验收、Fabric 目录资源桥或默认重载性能通过；最新默认性能仍为 466.4/469.4 ms FAIL。
