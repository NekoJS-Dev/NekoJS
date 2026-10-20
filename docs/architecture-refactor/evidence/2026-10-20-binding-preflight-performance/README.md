# 当前实现的原标准正式复验

`26844b7e65a6e71f7623af631192b03bbd5543b4` 的预定三组全部 PASS。没有调整采样器、fixture、首值、门槛、world 路径或系统策略；本版本只运行这一次正式 cohort。

| 组 | 全部正式值（ms） | 均值 | 原门槛 | 结论 |
| --- | --- | ---: | ---: | --- |
| reload-1 | 384, 208, 184, 180, 266 | 244.4 | 285.3 | PASS |
| reload-2 | 424, 327, 282, 106, 102 | 248.2 | 285.3 | PASS |
| startup | 22571, 21577, 22957, 23449, 25031 | 23117.0 | 41173 | PASS |

另外两条启动预热为 19682 / 22407 ms，也完整保存。17 行均有效、十次重载均 COMMIT / no errors，九次会话均正常 RCON 停服，没有强杀、超时或删除首值。正式测量期间本任务没有并行构建、测试、组件基准、其他游戏或 profiler。

继续使用 D:/mcmodDemo/NekoJS-perf-606d1fff，分支 codex/perf-final-26844b7e。原采样器 SHA-256 为 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`。1626 个生产类及整个 JAR 与主工作区检查后产物逐字节相同；JAR SHA-256 为 `e2c37b41c5c2492c4ea5a261e099654f906a51cb5bc6058dae7b687adb68d2e4`。归档保存实际 JAR、逐类绑定、全部样本/会话日志、fixture 和控制器，`verify.py` 可独立重算三组门槛并检查九次正常停服。

这是既有 source runServer 口径，不是安装 JAR 后的独立启动计时。组件诊断表明本轮两项局部改动收益较小；会话间波动仍明显，不能把与 [24d35a5d 的失败](../2026-10-20-source-map-storage-performance/README.md)之差全部归因于它们，也不能承诺每次重载低于门槛。原标准采用五次均值，此处按原标准判定，不改为逐次门槛或重复采样择优。

[定向/common 隔离与五节点运行产物检查](../2026-10-20-binding-preflight-checks/README.md)绑定同一源码。先前五节点完整构建与测试、CI gate 仍分别保留原提交边界，没有冒充本轮重新运行。后续源变更若影响已测路径，需要重新判断绑定是否仍成立。脚本包整体维护者验收、社区外部处置和 Fabric 原生客户端资源验收仍独立未完成。

复核：`python -X utf8 docs/architecture-refactor/evidence/2026-10-20-binding-preflight-performance/verify.py`。预期 evidenceVerification=PASS，三组 verdict=PASS。
