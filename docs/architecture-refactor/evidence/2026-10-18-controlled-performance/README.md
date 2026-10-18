# 2026-10-18 原采样器、原门槛与显式运行条件

本次保留原 `bench/perf/sample.ps1`、固定 fixture、RCON/marker 口径及重载 285.3 ms / 启动 41173 ms 门槛。唯一新增的运行条件是：隔离工作树新启动的游戏 Java 进程显式使用 HighQoS。**这不是生产源码优化，也不会撤销此前默认后台条件下的正式 FAIL。**

## 原因与预先固定范围

[上一轮四十条配对控制](../2026-10-17-reload-performance/cpu-placement/README.md)显示，临时控制游戏 Server thread 时，original / HighQoS 的 marker 均值为 319.16 / 230.87 ms，二十对 RCON 均下降。为验证这能否解释原门槛在本机的失守，本轮改用可审计的进程条件控制，分别测量当前源码 `6e5bb8f0` 和重新构建的历史源码 `3400e97e`。

每份源码均预先指定：两组各五条重载，然后两次预热加五次正式启动。全部首值、失败和原始输出保留；不按结果补跑同条件组。两份源码串行运行，其间不运行本任务其他构建、测试或游戏。实际测试类、产物与 fixture 按摘要绑定。

相同条件包括 NeoForge 26.1.2、JDK 25.0.2、固定 Gradle 缓存、端口 25871/25872、`--console=plain`、RCON 正常停服、原有各自隔离 world。历史 world 和旧二进制已丢失，历史组沿用本轮重建得到的 owned world，不冒充原验收环境；两个工作树的 world 并非字节相同。

## 条件控制边界

外部 sidecar 只考虑启动后新出现的 Java 进程。它内部读取命令行，仅当包含当前隔离项目路径及 serverRunProgramArgs / serverRunVmArgs 文件名时才操作；输出仅保留归属结论、PID、父 PID 和策略，不输出命令行。随后核验 JDK 可执行路径、通过进程句柄绑定创建时间、记录原策略，并持续读回。

使用 Windows [SetProcessInformation](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-setprocessinformation) 的 execution-speed 控制，保留原其他位，仅置 ControlMask bit 0、清 StateMask bit 0，即 HighQoS。未更改电源方案、CPU 亲和性、进程优先级、timer-resolution 位、Gradle daemon 或其他用户进程。

策略在 JVM 创建并完成归属查询后应用，最初的发现延迟完整记录；不声称从创建第一条指令起就受控。每个进程都必须在日志 Done 前完成应用。游戏正常退出后，该进程范围内的策略随其生命周期结束；控制器异常结束时，对仍存活的已绑定进程恢复原策略并读回。旧 PID 不会通过已有句柄指向另一个进程。

进程 HighQoS 与上一轮 thread HighQoS 是两个明确区分的控制范围，不能把两轮分数直接相减当作源码收益。Windows [QoS 文档](https://learn.microsoft.com/en-us/windows/win32/procthread/quality-of-service)说明未显式指定时系统会自动推断 QoS；此前策略位为零并不能排除这种调度影响。

## 结果与复核

两份源码各完成九次游戏会话，均正常 RCON 停服；所有样本有效，未删除首值或重测择优。每个游戏进程的策略均在 Done 前应用，最大发现及应用延迟为当前组 0.889 秒、历史组 1.115 秒。

| 源码与显式条件 | 重载 1：五个正式值（ms） | 均值 / 判定 | 重载 2：五个正式值（ms） | 均值 / 判定 |
| --- | --- | --- | --- | --- |
| `6e5bb8f0`，进程 HighQoS | 300、195、219、235、219 | **233.6 / PASS** | 293、136、284、233、269 | **243.0 / PASS** |
| `3400e97e`，进程 HighQoS | 442、308、246、236、263 | **299.0 / FAIL** | 381、255、279、356、282 | **310.6 / FAIL** |

当前组启动正式值为 25162、26456、24143、24314、24812 ms，均值 **24977.4 ms，PASS**；历史组为 24818、25423、24982、24569、25109 ms，均值 **24980.2 ms，PASS**。归档也保留两组各两次启动预热。两份源码的 world 不同，因此不能把两组差值全归于源码优化，也不能重建最初历史环境的因果结论。

历史组首次分析工具错误地要求新版 `phase=COMMIT` 响应，因而将成功重载标为 INVALID。旧源码成功响应实际为 `NekoJS server scripts reloaded. - no errors.`；现按精确版本的响应契约校验，同时仍要求原 marker、无错误和正常停服。首次分析结果保留为 `judgment-first-response-rejection.json`；未改样本、指标或门槛。修正后历史两组仍 FAIL，当前组结论不变。

`evidence.zip` 含 138 项，大小 23,305,323 字节，SHA-256 为 `148de3879132347ad8a2bc12a013a56ec646de0d77266cdd5ad8d689fc5d00c9`。包含原输出、采样元数据、策略控制器及读回、输入摘要、实际运行 jar、运行前后类摘要与首次分析错误。文本中的测试凭据已脱敏；二进制未经字节替换。归档摘要可独立重算；脱敏输入的原摘要绑定不能脱离原本地输入重新计算。

复核通过：

```powershell
python docs/architecture-refactor/evidence/2026-10-18-controlled-performance/verify.py --require-current-pass
```

复核器重新计算六组原指标和门槛、全部样本有效性、十八次会话的进程策略覆盖及 Done 前应用时间，并检查实际 jar 与冻结 main 类一致、运行前后类一致及采样器摘要。检查通过表示**显式进程策略下当前组通过**，没有把原默认条件的 FAIL 改为 PASS。

## 剩余边界

原默认后台条件的结果与显式 HighQoS 条件分别保存。运行条件得到控制不等于默认后台性能缺口已通过产品修改消除；本次没有引入 Windows 专用生产依赖或改变普通 reload 行为。Script API、Plugin API、信任、wire、持久数据及资源所有权不变。脚本包原生内存、真实客户端 A→B→A、签名同步和维护者整体验收仍需各自证据。
