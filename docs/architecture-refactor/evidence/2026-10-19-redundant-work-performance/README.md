# 重载重复工作修正的原标准性能复验

**两组重载仍 FAIL，启动 PASS。** 实测代码 `46505ad2c7652cc90fd723766e3c40340d0c7dcb`；该代码已通过[集中检查](../2026-10-19-redundant-work-checks/README.md)。

| 组别 | 全部正式值 ms | 均值 ms | 原门槛 ms | 结果 |
| --- | --- | ---: | ---: | --- |
| reload-1 | 530 / 316 / 344 / 193 / 295 | **335.6** | 285.3 | FAIL |
| reload-2 | 406 / 296 / 224 / 342 / 323 | **318.2** | 285.3 | FAIL |
| startup | 35874 / 37174 / 36587 / 37468 / 36590 | **36738.6** | 41173 | PASS |

预先指定两组各五次重载、启动两次预热加五次正式，全部 17 行有效。十次重载均 COMMIT，无脚本错误；九个游戏会话均经 RCON 正常停服、对应 Gradle 正常完成，没有 forced kill。没有删首值、挑样本、调整计时口径或因成绩择优重跑。

复用 `D:/mcmodDemo/NekoJS-perf-606d1fff`，保留此前分支，另建 `codex/perf-final-46505ad2`。原 sampler SHA-256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`、六个脚本 fixture、JDK 25.0.2、NeoForge 26.1.2、Gradle cache、端口与原 world 路径保持。没有 JFR、QoS/电源/优先级调整；采样期间不并行运行本任务构建、测试或其他游戏。

准备阶段仅运行 `classes / jar / prepareServerRun`，35.934 s PASS；它不冒充重新运行全矩阵。准备后的 1,627 个类及整个 JAR 与主工作区集中检查产物完全相同，采样前后摘要不变。实际 JAR SHA-256 `19c9e6496b5cccee1b3b5230bf7790c41af4c045b9ce6dac2311e67ac811ecb6`，二进制随本归档保存。

这一轮没有旧/新源码同 JVM 交替组件控制。因此不能把相对于 `4dcfe604` 的分数变化单独归因于两项源码修改，也不能从启动均值变化推断唯一原因。原失败记录保留；后续应补可归因诊断，不通过不改源码的正式重跑选择成绩。实际 NeoForge 会话完成了新配方循环，但不能代替所有第三方 codec、Fabric 资源或客户端验收。

## 冻结与复核

```text
python -X utf8 docs/architecture-refactor/evidence/2026-10-19-redundant-work-performance/verify.py
```

复核重新计算原三组门槛，检查 17 行、九次正常停服、sampler/fixtures、1,627 类与实际 JAR，并对照相邻 `../2026-10-19-redundant-work-checks/evidence.zip` 的产物绑定。控制器与判定器逐字节等于上一轮冻结版本，未改 sampler。

归档 67 项、18,127,043 bytes，SHA-256 `cbb3f6556078259b7643b3673c4f30779cec97fe78649253452dcf9c0c9a5ea6`。包含完整原始样本/日志、准备与来源工具、实际 JAR；不含 server.properties、world NBT 或私钥。独立复核 PASS 只证明证据自洽，性能结论仍是 **NOT_PASS**，整体目标尚未完成。
