# 单次重载线程策略原型：未通过，不合入

原型提交 `8ca66bf43ce7abb0665e038dc5d07a732aa0159a`，基于 `3c94d8c8`，仅保留在 `codex/reload-thread-policy-prototype`。stonecutter 没有引入 Windows 线程策略。实现范围和预先指定的验收规则见[原型计划](../../../research/2026-10-19-scoped-reload-policy.md)。

## 原标准正式结果

未修改原采样器、fixture、端口、缓存和门槛；保留冷首次、两次启动预热及全部失败。正式期间没有并行本任务构建、测试、其他游戏或外部 QoS controller。

| 组 | 五个正式值（ms） | 均值（ms） | 门槛（ms） | 判定 |
|---|---|---:|---:|---|
| reload-1 | 464, 371, 280, 436, 429 | 396.0 | 285.3 | FAIL |
| reload-2 | 546, 422, 361, 263, 366 | 391.6 | 285.3 | FAIL |
| startup | 62061, 61940, 61856, 60820, 61776 | 61690.6 | 41173 | FAIL |

九个 JVM 会话、十七条样本均有效且正常 RCON 停服。两组 RCON 自身均值为 312.4 / 319.2 ms，已经超过重载门槛；日志转发尾段不能单独解释全部缺口。九个会话没有 NEKO-1019/1020 策略不可用或恢复失败日志。不同日期、会话的历史默认值不能用作本轮原型的因果收益对照。启动约 61 秒也不能仅凭总时间归因于这段重载 scope。

## 功能验证与适用限制

- 原型用 Minecraft 已有 JNA，只在同步 SERVER 命令内设置 owner thread execution-speed 位，并恢复原始三个字段；非 Windows、CLIENT、其他线程不设置。没有进程或全局电源/优先级/affinity 修改。
- 红例为未接线的命令 source trace；native 九项已通过。接线后十四项通过。
- `gradlew.bat --offline guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain`：PASS，2646 tests，0 failures/errors，267 skipped。五节点 native 九项均实际执行。
- 仅修正一条可能误报“没有 hint”的警告文案后，同一五节点构建和 guardLint 再次 PASS；每节点 native 九项及命令接线五项，共七十项均执行通过。归档复核器检查两次 helper 源码只有该文案差异。
- 四项 CI gate 最终 PASS。首次 declaration/nodes 因缺少生成报告输入失败，补运行既有生成任务后通过；保留首次失败，不改断言。
- 正式组结束后运行真实 native 隔离观测：owner `[1,0,0] → [1,1,0] → [1,0,0]`；已有线程及 scope 内新建线程均保持 `[1,0,0]`。此项证明本机恢复和隔离，不能替代性能通过，也不保证所有旧 Windows 支持该查询。
- 本轮不改 common；先前完整 common 检查证据复用，不把五节点结果当作重新执行 common:check。未运行新的 GameTest，未做发布或维护者接受。

## 冻结与独立复核

`evidence.zip` 包含原始正式样本、所有会话日志、环境/源码/fixture/1630 类绑定、精确原型 JAR、补丁、测试 XML、CI 初始失败和最终报告、native 隔离原始点及工具。693 项，18,867,269 bytes，SHA-256：

`5801b28d521d553d4d38b1d56edac723720f32c96231b82e4431de81b4b414e9`

凭据以 `<REDACTED>` 替换并保留原始摘要映射；不含私钥、server.properties、world NBT 或完整 classpath。归档工具首次遇到 CP936 文本未生成归档，补编码识别后成功；复核器首次错误假定每条 reload 行都有 timeout 字段，按原 schema 的可选字段修正。两者都未改变测量值和验收规则。

在仓库根运行 `python docs/architecture-refactor/evidence/2026-10-19-scoped-reload-policy/verify.py`，应得 `evidenceVerification: PASS`、`performanceVerdict: NOT_PASS`、`promotedToStonecutter: false`。复核通过表示证据自洽；三个性能组均为 FAIL，原型不合入。

下一步在主线实现的同一游戏会话内关联 root、配方、命令返回的墙钟与 owner CPU 计时，增加定向 JFR 采样。诊断插桩必须还原，最终仍用未改采样器复验，不把诊断时序当正式通过。
