# 同会话命令／CPU 阶段与 JFR 诊断

主线基线 `4550a385` 的唯一临时插桩位于 NeoForge 26.1.2 命令实现：root、recipe、command 三段的墙钟和 owner CPU，并发出对应 JFR 事件。无线程或进程策略，不修改采样器、fixture、权限、Context 隔离或候选提交。它是独立诊断会话，不能替代正式原门禁。

预先指定二十对在线人数查询／重载，轮换先后顺序，间隔三秒；全部四十条成功，二十次重载均 COMMIT。首个重载保留。JFR 在 Done 后启动，配置 ExecutionSample 和 NativeMethodSample 为 2 ms；实际只获得 root 131、recipe 75 个栈样本，样本数不能当作耗时或精确 CPU 占比。

| 同会话观测 | 平均墙钟 ms | 平均 owner CPU ms |
|---|---:|---:|
| root | 228.101 | 223.438 |
| recipe | 63.777 | 59.375 |
| command | 293.874 | 286.719 |
| 完整 reload RCON | 444.102 | — |
| 轻量 list RCON | 149.100 | — |
| 原样等待 stdout marker | 481.221 | — |

`reload RCON − command` 为 150.229 ms，与轻量查询相近。这个差值包括客户端启动、网络、命令调度及返回，不是一个已定位的“服务器等待”计时，也不证明每条查询都能精确抵消对应重载的全部外部开销。阶段 CPU 接近墙钟，支持继续检查实际计算，而非只优化日志转发。CPU 计数分辨率有限；JFR、阶段日志和诊断查询本身也改变运行负载。

栈观测继续指向 Windows 路径规范化、当前 schema 的预检和 source-map 解码；recipe 栈则主要是 recipe codec 及数据结构访问。没有发现单一已证实能消除全部差额的函数。后续解码分配对照和生产修复必须单独绑定，不用此诊断分数宣布达标。

首次控制命令误用了本版已变更的 `time query gametime`，诊断断言失败且尚无重载样本；finally 中 JFR 停止、服务器正常停服、Gradle 成功退出，保留完整失败日志。改用此前已验证的 `list` 后得到上述完整会话，没有按成绩择优重跑。完整会话也正常 RCON 停服，未 kill。

命令原始字节已还原并经摘要检查，随后 `:26.1.2:build` PASS；1625 个 main 类没有诊断标记或临时 JFR Event 类。之后开始的 decoder 候选是另一个阶段；不声称其 common 类仍等同于本轮原始基线。

`evidence.zip` 为 404,486 bytes、45 项，SHA-256 `09a5aba9ae125afe93bb5f43c55d6cd0ea6af00daea60390ae7fe4e95d683749`。包含四十条原始观察、六十条 JFR 阶段导出、原始阶段日志、栈/分配采样导出、两次会话日志、源码/类绑定、插桩补丁、工具与还原记录。原始 JFR 可能携带环境和程序属性，仅留在 ignored 工作目录，归档保留其摘要和分析导出，因此此归档不能从二进制重新提取栈；复核器可独立核对阶段导出与 stderr、重算全部表格和会话判定。凭据脱敏，未收录私钥、server.properties 或完整 classpath。

在仓库根运行 `python docs/architecture-refactor/evidence/2026-10-19-reload-stage-jfr/verify.py`；应得 `evidenceVerification: PASS`，原正式重载判定仍为 FAIL。
