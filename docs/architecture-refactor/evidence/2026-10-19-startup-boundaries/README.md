# 启动请求、Gradle 与游戏 JVM 边界

**结论：控制器两次失败；第二次录制可恢复并验证身份，游戏正常停服。诊断不改判正式 FAIL。** 没有修改生产代码、系统策略或正式采样器。

## 运行结果与失败

源提交为 `606d1fff6b14e4a513bf16e247b0ec37437195c6`。首次临时 init 同时作用于 `buildSrc`，其中没有 `:26.1.2` 项目，Gradle 配置失败，未启动游戏。另建 v2，增加目标工程存在性检查；没有覆盖原失败目录。

v2 仅在 `neoForge.runs.server.jvmArguments` 加入启动 JFR。真实游戏 PID **18976**，从 JVM 启动即启用 `profile`，没有用全局 JVM 环境变量。控制器在 Done 时因为命令行未直接含 JFR 参数而拒绝身份检查；启动器实际使用参数文件。它随后正常 RCON 停服、Gradle 成功，`forcedKill=false`、`killed=false`，控制器仍以 **1** 退出。

录制通过 `dumponexit` 保存。事后分析读到 `jdk.JVMInformation.pid=18976`、准确启动时间，以及包含预期唯一输出路径的 JVM 参数；它与游戏 stdout 的 `Use jcmd 18976 JFR.dump name=nekoStartup` 一致。只导出 PID／路径匹配布尔值，不导出整条 JVM 参数。**没有把事后恢复描述成控制器通过或在 Done 时成功停止 JFR**。

## 边界与观察

| 边界 | UTC |
| --- | --- |
| 启动请求 | 2026-10-18 18:26:36.200280 |
| runServer task action | 18:26:44.5281875 |
| JFR 报告的 JVM 创建 | 18:26:44.728 |
| 约略 Done 观察 | 18:27:43.235280 |

启动请求到 JVM 创建约 **8527.72 ms**；创建到约略 Done 约 **58507.28 ms**；原 session 的 `doneWallMs=67035`。Done 绝对时点由请求记录加原 session 墙钟推算，请求记录略早于该内部 stopwatch，不作为纳秒精度边界。该带 JFR 的单会话不作为正式成绩。

原始录制延伸到正常退出；分析只计入事件开始时间不晚于约略 Done 的事件。JFR 启动自身约有一秒初始化间隙；不能据样本缺失断定间隙内无工作。跨边界的 duration 不裁剪，不把并行 compilation／I/O／monitor duration 的总和当作总启动时间。

截止窗口内 main / modloading-worker-0 / Server thread 的有效 stack sample 分别为 **991 / 344 / 31**。主线程包含 Mixin/ASM、类加载、原版 block shape bootstrap；modloading 线程包含 NekoJS root、Context、Node builtin 等初始化。AccessTransformerList.getTargets 分别出现于 main **111**、modloading **92** 个 inclusive 样本；这些是调用栈计数，不能当作独占毫秒或与父子栈相加。

Luna 限定范围只读调查后，主代理复核 canonical AT 为 **111 行、63 条指令、44 个类目标**（含版本预处理分支的源码总量）。它经既有 Stonecutter 预处理交给 NeoForge；未发现仓库代码动态追加超大 AT 列表。没有据 loader 样本删权限、修改外部库或将全部启动耗时归于 NekoJS。

现有证据不支持单一热点结论。下一项是工程位置控制：同一提交、逐类一致的 C 深层工作树与 D 盘近原工程位置，用原采样器比较；盘符／深度和会话时间同时变化，因此不能仅据先后差值证明路径长度的因果关系。

## 恢复、证据与限制

专用 C checkout 源码保持 clean；无 init 重新执行 `:26.1.2:prepareServerRun --offline --console=plain` PASS，生成 run 配置已无 JFR 参数，采样绑定的 1,625 个 main class 摘要不变。没有剩余 owned game 或 test worker。封包首次因默认 GBK 读取 UTF-8 日志失败，显式 UTF-8 后成功；游戏日志与成绩未修改。

```text
python docs/architecture-refactor/evidence/2026-10-19-startup-boundaries/verify.py
```

复核 PASS，控制器状态仍为 `FAILED_BUT_RECORDING_RECOVERED`。归档 **516,965 bytes**，SHA-256 `1479028865cd790f88fcc06b6e322f6911feb04d617ea50827df807eb8d077dc`。逐项摘要、两次控制器失败、正常停服、PID、时间计算、样本总和、AT 数量及类恢复绑定都可重算。

原始 JFR SHA-256 为 `cce6de35c233334e3292352be2f92a3db21f8058803d535cbb03ba046c5c0970`，仅保留于 ignored 目录，未放入归档，因为可能包含 JVM properties／环境。归档含完整分析、分析工具和失败日志，但不能脱离原始 JFR 独立重新提取栈；这一限制不等于已验证所有栈判断或真人接受。正式完整制品／测试见[解码器复验证据](../2026-10-19-source-map-decoder/README.md)。
