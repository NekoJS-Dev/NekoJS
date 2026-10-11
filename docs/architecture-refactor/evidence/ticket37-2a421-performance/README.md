# 精确 2a421c18 启动与重载性能：PASS

已提交源码 `2a421c18bd36dc59d0006db8e68d515f0122ce8e`，节点 26.1.2，专属 clean 隔离 checkout。预声明的两组重载和一组启动全部完成并通过原门槛。

| 预定组 | 全部五个正式样本 (ms) | 均值 (ms) | 门槛 (ms) | sampler exit | 结论 |
|---|---|---:|---:|---:|---|
|reload-confirmed-1|185,254,120,218,218|199.0|285.3|0|PASS|
|reload-confirmed-2|202,221,223,236,220|220.4|285.3|0|PASS|
|startup-confirmed|23644,23500,22814,22770,22979|23141.4|41173|0|PASS|

启动预热 19443/23276ms 保留；全部首样本保留，未混 revision、改门槛或追加替代组。两组重载按各自完整五个样本判断。历史 b7、0856、cb5 的失败继续保留，本结果不推断 Python 类型修复导致速度改善，也不认证其他节点或客户端性能。

沿用原 sampler SHA256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`。原 fixtures 全部 SHA 与实际 cb5 采样时的捕获一致，Git 事实也没有 fixture 改动。JDK25、隔离 Gradle cache、启动/重载命令、数据集、预热/样本数、RCON 与 marker 口径不变。见 [预声明计划](PLAN.md) 和 [逐项摘要](summary.json)。

## 执行与源码绑定

复用既有专属 checkout，经 clean、绝对路径、所有相关目录 reparse、进程和端口检查后，正常切换精确提交。使用先前已验证的 Python subprocess 将输出直接重定向并取得真实 OS wait returncode；三个 sampler 及外层 runner 都直接 exit0。子 PowerShell 的模块目录设置沿用既有捕获修正，所有原 guard 和固定 sampler 保持。

原参数：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode reload -Node 26.1.2 -Warmup 2 -Samples 5 -Reloads 5 -GradleUserHome D:/mcmodDemo/NekoJS/.gradle-perf02 -ServerPort 25871 -RconPort 25872
```

startup 组仅 Mode=startup。采样期间无并行构建、测试或其他专属游戏。五节点实机与完整 IDE 编译已在采样前退出。性能 checkout 编译后的 ApiTypeRefPyRenderer、PythonClassRenderer、PythonProbeBackend 三个修改后类，与本轮已经五节点验收的主工作区构建字节完全一致；逐类 SHA 位于 summary。

九个会话均正常 RCON 关闭，没有 forced_kill/killed/timeout；结束后原 owned guard exit0，无残留专属游戏，25871/25872 可独占绑定。sampler 的 pid 字段记录 cmd.exe 的 Gradle 启动器，不能冒称实际服务器 JVM PID；完整原日志和停止事实均保留。未停止用户进程，也未处理 unrelated world。

## 归档与范围

[完整原始证据](2a421-performance-evidence.zip) 含 49 项、1,192,500 字节，SHA256 `b49a4fedf7ac8daf694ff56dba1e35bfc56415e781d1a65b5aa7421760fa87cf`。原 JSONL、完整 stdout/stderr、每组调用/guard/退出、环境、fixture SHA 与 runner 均保留。hash/readback、完成日志与原样本字节一致性通过，见 [验证记录](archive-verification.json)。打包控制台在完成后单独保留；属性密码、world、二进制和 raw JFR 排除。

凭据扫描使用当前专属环境的实际密码值；固定缓存目录 `.gradle-perf02` 的文本与该基准密码偶然共享子串，仅将这个确定的目录名排除误判，仍检查其余完整内容及任何 rcon.password 属性行。没有将密码写进新增材料。性能通过不代表工具权限字段入口问题已修复，该问题仍 UNRESOLVED。

本轮 Callable 源码和实机证据已推送并核验远端 `01565e85d180ac0de89be3f6d098cb176b83da54`。推送曾等待现有账户选择，按用户此前授权用 computer-use 选择已登录 Tki-sor 并继续，随后真实 push exit0/远端 SHA 一致；没有新账户或权限扩张。

整体仍未完成：完整 IDE 仍 FAIL、旧版 8 类型遗漏 PARTIAL；持久化移动间歇失败、native 崩溃、fireResistant/剩余领域、资源下载窗口与跨节点第一帧/视觉、发布/回滚及真实维护者结论仍独立待验收。维护者结论 NOT RECORDED，自动续跑保持 PAUSED；未发布或扩大 A22/A28 授权。
