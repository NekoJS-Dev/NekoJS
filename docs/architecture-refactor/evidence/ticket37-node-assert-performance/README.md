# 精确7d77308b正式性能：重载 FAIL，启动 PASS

在专属干净 checkout 对 `7d77308bfa400721304b20940ada91092180a1ed` 执行预先声明的两个重载组和一个启动组。使用原 sample.ps1 SHA256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`、原 fixtures、JDK25.0.2、NeoForge26.1.2、固定缓存和端口25871/25872。source/path/reparse/port/process 守卫在准备、每组之前和结束后均通过。正式采样期间没有并行构建、测试或游戏验收作业，没有丢首样本或按分数重试。

| 预声明组 | 正式五值 ms | 均值 ms | 固定门槛 ms | 结果 |
|---|---|---:|---:|---|
| reload-confirmed-1 | 394,376,236,343,359 | 341.6 | 285.3 | FAIL |
| reload-confirmed-2 | 337,359,359,212,311 | 315.6 | 285.3 | FAIL |
| startup-confirmed | 24972,25512,26578,25733,26182 | 25795.4 | 41173 | PASS |

启动 warmup23904/26811ms保留。重载五值全部正式样本，不能以第二值起算或替换为其他指标。三 sampler 和外层直接退出0只表示采集成功，性能整体仍 FAIL。九次会话均正常 RCON stop，没有 forced kill、killed 或 timeout；结束后专属端口可绑定，最终 owned guard 退出0。

本 checkout 的1592个实际编译 Neko class 与主工作区及五节点安装验收中的新26.1.2官方 JAR逐字相等，JAR SHA256 `eea76f127890dd86f0fc29a4a0376a27a765805206a4d145c887ec696f545886`。原 fixture hash 与历史cb5逐项相等；没有套用旧制品 hash。[此前63失败](../ticket37-manual-item-performance/README.md)及[d109历史通过](../ticket37-current-d109-performance/README.md)保留，不能继承或混 revision。此次并非单变量时间控制，不把分数变化归因于 Probe 补丁或认定全由环境造成。

实际命令由包内预声明 runner 顺序执行，Mode 为 reload/reload/startup，每组参数不变：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode reload -Node 26.1.2 -Warmup 2 -Samples 5 -Reloads 5 -GradleUserHome D:/mcmodDemo/NekoJS/.gradle-perf02 -ServerPort 25871 -RconPort 25872
```

source guard、完整 raw stdout/stderr/JSONL、所有直接退出及源码/fixture/编译绑定见[结果](summary.json)。[不可变证据](node-assert-performance-evidence.zip)：48 entries、1191721 bytes，SHA256 `de14ec5c3a012669402b8a13657db0d65fb7da14c54d27fcffe86b6ad6921141`。[独立验证器](verify.py)核验完整 hash、原始五值/第一值、warmup、门槛、实际停服及退出；其 PASS 表示证据完整，性能结果仍 FAIL。

下一步对同一源码做明确标记的阶段诊断，区分命令往返、重载主体和 stdout proxy 观察；诊断不能替代正式结果，不绕过路径安全检查或改变门槛。完整 IDE/Pyright、其他当前多人/下载/首像素/跨节点/领域/磁盘和真实维护者及发布回滚验收仍未完成。自动续跑暂停，权限字段问题未解决，A22/A28与发布授权边界不变，整体 NOT COMPLETE。
