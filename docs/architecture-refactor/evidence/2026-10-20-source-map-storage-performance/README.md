# 紧凑源码映射存储：原标准正式复验

生产提交 `24d35a5d6ddc30f68c9719d9eb25c0ce898f8398`，结论 **NOT_PASS**。使用原采样器、首值、默认系统策略和原门槛，没有按分数选择重跑。

| 组 | 全部正式样本（ms） | 均值（ms） | 门槛（ms） | 结果 |
| --- | --- | --- | --- | --- |
| reload-1 | 428, 208, 211, 308, 292 | 289.4 | ≤285.3 | FAIL |
| reload-2 | 444, 316, 253, 304, 373 | 338.0 | ≤285.3 | FAIL |
| startup | 36657, 36642, 40598, 37827, 31581 | 36661.0 | ≤41173 | PASS |

包含两条启动预热在内的 17 行全部有效；10 次重载均 COMMIT、无脚本错误，九次会话均通过 RCON 正常停服，没有强制结束。测量期间没有本任务的并行构建、测试、组件基准、游戏或分析器。

复用 `D:/mcmodDemo/NekoJS-perf-606d1fff` 的既有 world/config。正式采样器 SHA-256 为 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`。主工作树与性能工作树的 1,626 个生产类及完整 JAR 一致；类数量减少一项来自删除私有 MappingEntry。JAR SHA-256 为 `09857752ee14d6fc7d270b3434a3410c74a81404d7da4e4b46c5f9550731a9db`。

[集中检查和组件对照](../2026-10-20-source-map-storage-checks/README.md)证明映射行为一致、组件分配减少约 49.4%，但六源码墙钟仅节省约 0.063 ms。不能用该小组件或跨会话成绩差值解释完整重载的全部变化。

`evidence.zip` 保留全部原始样本、日志、实际 JAR、源码/类摘要、准备过程、采样器、fixture、控制器和判定器；不包含 world NBT、服务器属性或凭据。此处为源码 runServer 的原验收范围，不冒充安装发行 JAR 的计时。

运行 `python -X utf8 docs/architecture-refactor/evidence/2026-10-20-source-map-storage-performance/verify.py` 可复核归档完整性、样本、原门槛和正常停服。复核器 PASS 表示证据一致，性能结论仍为 NOT_PASS。
