# 实现收口版本的原标准性能复验

**重载两组仍 FAIL，启动 PASS。** 实测提交 `4dcfe6045f4622ab677b885154735a979d5fb714`，在复用的 `D:/mcmodDemo/NekoJS-perf-606d1fff` 工作树、`codex/perf-final-4dcfe604` 分支执行；目录名字不代表仍使用旧源码。

| 组别 | 全部正式值 ms | 均值 ms | 原门槛 ms | 结论 |
| --- | --- | ---: | ---: | --- |
| reload-1 | 400 / 330 / 302 / 222 / 175 | **285.8** | 285.3 | FAIL |
| reload-2 | 267 / 316 / 285 / 286 / 279 | **286.6** | 285.3 | FAIL |
| startup | 24530 / 24411 / 24315 / 24238 / 23758 | **24250.4** | 41173 | PASS |

没有四舍五入改判、剔除首值或按成绩择优重测。预先指定两组各五次重载，启动两次预热加五次正式，全部 17 行有效；十次重载均 COMMIT，九会话正常 RCON stop，所有游戏对应 Gradle 均正常完成，无 forced kill。判定工具按 NOT_PASS 返回 2 是预期结果，不是采集失败。

原 sampler SHA-256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`、六脚本 fixture、NeoForge 26.1.2、JDK 25.0.2、Gradle cache、25871/25872 端口及计时口径均保持。没有 JFR、QoS controller 或系统策略调整；正式测量期间没有并行本任务构建、测试或其他游戏。新工作树准备只运行 `classes / jar / prepareServerRun`，18 秒 PASS，不将准备过程冒充重新运行全矩阵。

准备后主工作区与性能工作树的 **1,627 个编译类及整个 JAR 完全相同**；采样前后类与 fixture 摘要保持，并逐类对照 JAR。实际 JAR SHA-256 `e3faf53f34f769544f3061452d3c1a361c2d4904a5b31152c9b193e613987dab`。该代码已通过[集中检查](../2026-10-19-implementation-checks/README.md)。主线随后新增的 `abf5ca99` 仅提交检查文档和证据，不改变被测代码。

共享预检解析去掉了一次重复解析，但本次没有同会话旧/新源码交替控制，不能把与历史 D 盘 318.0 / 318.4 ms 的全部差值归因给它。原 C 盘及 D 盘失败成绩仍保留。当前整体性能目标仍未完成，下一步继续定位可归因的实际重复工作；不降低门槛、跳过当前 schema/信任/codec 校验或复用独立 Context。

## 冻结与复核

```text
python -X utf8 docs/architecture-refactor/evidence/2026-10-19-preflight-performance/verify.py
```

PASS：重新计算全部三组及原门槛，检查 17 行、九次正常停服、原采样器/fixtures、1,627 类与实际 JAR、与构建证据的来源绑定。`evidence.zip` 为 66 项、18,036,768 bytes，SHA-256 `acee36d9b82eb82627392a46de9a80ed9dc428e1bab6f2e2ed859649c24e76d5`。包含原始日志、样本、实际 JAR、准备绑定和工具；不包含 server.properties、world NBT 或私钥。控制器仅还原旧归档误脱敏的公开 Gradle cache 路径并规范文本换行，sampler 未变。

复核需要相邻的 `../2026-10-19-implementation-checks/evidence.zip`，以确认实测 JAR 与完整构建记录为同一产物。归档验证 PASS 只表示证据自洽，**性能结论仍为 NOT_PASS**；Fabric 实机资源验收和维护者整体接受也仍未完成。
