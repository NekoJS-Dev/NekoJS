# 启动边界诊断

当前 source-map 解码候选 `606d1fff` 的原标准正式复验按计划运行。新启动样本约 60 秒；不能把 Minecraft `Done (1.xs)` 的内部计时等同于从 Gradle 启动命令开始的 `wall_done_ms`。诊断在完整正式 cohort 结束、冻结源码和制品绑定后单独进行，不替代、重跑或改判正式成绩。

## 可证伪假设

1. **游戏 JVM 的普通加载／初始化占主要增量**。预测：游戏创建较早，NekoJS 进入前后存在大量 loader、Mixin、类加载或编译活动，JFR 中能定位到具体栈。不能仅凭日志缺少 NekoJS 字样就判定与 NekoJS 无关。
2. **游戏创建前的 Gradle 配置／任务准备占主要增量**。预测：从原启动请求到 `runServer` task action 和游戏进程创建之间的间隔较大，而游戏创建到 Done 较短。
3. **NekoJS 自身初始化和脚本预检占主要增量**。预测：游戏 JVM 的主要样本及持续时间窗口包含 NekoJS 初始化调用，能进一步缩小到具体功能；保留权限、schema 和候选失败边界。
4. **调度、文件系统或其他等待拖长墙钟**。预测：长窗口与相应阻塞／原生操作相关，CPU 负载较低或与样本栈不匹配。JFR 样本数不是独占毫秒；稀疏样本或未触发 threshold 不能证明不存在等待。

## 方法及限制

只读 Luna 定位到 NeoForge convention 的 `neoForge.runs.server.jvmArguments` 模型；主代理通过 ignored 临时 Gradle init 配置，仅为专用 26.1.2 游戏 JVM 添加 `-XX:StartFlightRecording`。不设置全局 JVM 环境变量，不改源码、Gradle convention、Windows 电源／QoS 策略或正式采样器。该 init 接点是否可用仍需真实启动验证，失败必须保留。

使用原 fixture 部署与停服函数、同一 owned run 目录、缓存、JDK 和端口。启动请求、task action、游戏进程创建、Done 观察时点分别记录；正常 RCON stop。JFR `profile` 自带开销，此单会话只用于边界诊断，不作为性能成绩；不把并行线程的 compilation / I/O duration 相加成总启动时间。

原始 JFR 可能包含 JVM properties 和运行环境，因此仅在 ignored 诊断目录保留，归档其摘要与脱敏分析。导出线程、方法、时间和事件计数，不导出 JVM command line、环境变量、server.properties、world NBT 或密钥。维护者接受仍需真人结论。

## 实际结果

两次控制器均退出 1。首次 `buildSrc` 无目标工程，未启动游戏；v2 游戏通过参数文件启动，Done 后身份检查未在命令行直接找到 JFR 参数，随后正常 RCON 停服，录制由 dumponexit 写出。事后 JFR 的 PID／唯一录制路径匹配与游戏 stdout 一致；保留失败，不冒充 Done 时停止录制成功。

请求到游戏 JVM 为 **8.528 秒**，JVM 到约略 Done 为 **58.507 秒**；包含框架／原版／NekoJS 的多种初始化栈，未确定单一根因。恢复正常 run 配置 PASS，1,625 个编译类保持原正式摘要。完整记录和独立复核见[冻结证据](../architecture-refactor/evidence/2026-10-19-startup-boundaries/README.md)。

下一项工程位置对照预先指定两组各五次重载、两次预热加五次正式启动，仍使用原 sampler。D 盘新 checkout 位于 `D:/mcmodDemo/NekoJS-perf-606d1fff`，源码同为 `606d1fff`，1,625 个实际编译类与 C 盘一致；受控 world/config/mods、server.properties、eula 共 42 个文件按摘要复制，未覆盖已存在目标。盘符、路径深度及测试时点不同，顺序 cohort 不足以单独证明路径长度因果；不替代已保留的 C 盘 FAIL。

工程位置对照已完成：两边整个 JAR 亦逐摘要相同。D 盘两组重载 **318.0 / 318.4 ms（FAIL）**，启动 **25001.8 ms（PASS）**，17 行有效、九会话正常停服。完整原始证据和跨归档制品复核见[位置控制记录](../architecture-refactor/evidence/2026-10-19-project-location/README.md)。源码优化的组件通过、位置差异与原重载失败分别记录，总目标仍未完成。
