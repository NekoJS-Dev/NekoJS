# 2026-10-18 调用链控制与 Gradle 静态证据

本包是定向反证，**不替代正式游戏性能复验，不改变 466.4 / 469.4 ms 的 FAIL 或 285.3 ms 门槛**。没有改生产源码、原采样器、fixture 或 Gradle 运行配置。

## RCON 空响应端点

主代理启动一个只监听本机临时端口的受控 RCON 端点，执行原 `bench/perf/rcon.py`，只返回固定 `control-ok`。不启动游戏、不访问外部服务器。端点实际核验认证包与命令包；临时随机凭据只在进程环境中传递，没有写入结果。

Windows PowerShell 5.1 从采样器 AST 读取原 `Invoke-Rcon` 函数，只替换其脚本目录表达式。每轮交替先后，各臂二十条，包含首值：

| 对照 | 原 PowerShell native 调用 | 同一可执行文件的直接 .NET Process 调用 |
|---|---:|---:|
| 第一组，D 盘原客户端 | 48.25 ms | 59.65 ms |
| 第二组，D 盘原客户端 | 48.88 ms | 54.65 ms |
| 第二组，C 盘性能工作树客户端 | 49.71 ms | 55.11 ms |

第二组四臂八十条，C / D 客户端文件摘要相同。原调用路径差不到 1 ms，不支持以 Python 启动、PowerShell native 调用或 C 盘客户端文件路径解释正式 RCON 的数百毫秒。空端点不包含 Minecraft 处理，不能把这里的均值直接从游戏成绩中扣除。

最初控制运行在第一条 native 查询后失败：`Get-Command python` 返回两条 Application，传给 .NET FileName 的数组无法启动。修正控制器为选择第一条实际 native 使用的可执行文件后完成两组；失败日志和一次已完成连接保留，未混入成功统计。原采样器没有修改。归档文本中匹配运行凭据的路径片段已替换，`redactions.json` 记录原摘要。

## Gradle 9.6.0 的实际字节码

对本机已有发行包作 `javap` 静态读取，归档 jar / class 摘要与结果：

- `OutputEventRenderer.addPlainConsole` 和合并 stderr 的 plain 变体均调用 `throttled()`。
- `ThrottlingOutputEventListener` 默认读取内部 `org.gradle.internal.console.throttle`，缺省值 **100 ms**，按固定频率发送 UpdateNow；UpdateNow 属 InteractiveEvent，会触发队列渲染。
- daemon 的 `LogToClient.AsynchronousLogDispatcher` 空队列时 sleep **10 ms**，不是这里另有一个已证实的 100 ms dispatcher sleep。

结合[同条命令三 sink 观察](../output-visibility/README.md)，这些是 plain 输出转发存在延迟机制的证据。仍不能把观察到的 80.04 ms 全部当作某个内部类的独占耗时，也不能证明修改内部 property 是受支持的生产修复。没有设置该 property、绕开 daemon、换计时文件或降低门槛。

已有正式记录还能约束归因：历史基线 RCON / marker 均值为 139.2 / 228.2 ms，返回后增加约 89.0 ms；当前两组分别为 413.6 / 466.4 和 405.0 / 469.4 ms，增加约 52.8 / 64.4 ms。主要增长发生在 RCON 返回前，而且当前 RCON 本身已超过 285.3 ms。因此即使去掉返回后的全部日志等待，也不能让这两组通过；不能把 Gradle 输出节流当作已定位的主要回归原因。

## 复核与下一步

```powershell
python docs/architecture-refactor/evidence/2026-10-17-reload-performance/invocation-controls/verify.py
```

完整性 PASS，独立重算 120 条成功控制命令，保留最初失败；静态检查只验证当前二进制机制，不执行 Gradle 内部类。

精确旧源码 `3400e97e` 已在独立工作树离线构建成功；[同机重建对照](../historical-reference-3400/README.md)的启动 / 两组重载均超过原门槛。原历史 world 与二进制缺失，不能把它当作原环境的精确重放或据此批准当前版本。[四十条真实游戏调用控制](../real-invocation-control/README.md)也未支持调用器是主要回归原因；后续回到生产模块准备与重载工作本身。
