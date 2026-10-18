# Source-map 解码的临时分配

范围：只改 `SourceMapRegistry` 的 VLQ mapping 解码，不改路径规范化、信任、schema 检查、source-map revision、Context 隔离或代际状态。主代理实现和验证；不创建 PR。

[同会话 JFR 诊断](../architecture-refactor/evidence/2026-10-19-reload-stage-jfr/README.md)多次采到映射解码及其分配，但样本稀疏，不作为精确 CPU 百分比。旧实现对每条生成行和每个 segment 切字符串，并为每段生成 `List<Integer>`；新实现扫描原字符串，复用五个 primitive 字段槽，只分配最终需要的映射记录和行表。全部字符仍参与校验，包括超过五个字段的尾段；非法字符不应用部分增量。保留既有不完整尾字段的容错，不在性能改动中收紧格式规则。

## 组件反馈闭环

原实现与冻结参考实现先做同进程对照：六份原 fixture、100 次预热、三十对轮换样本。两臂均约 2,051,936 bytes 分配，预设“分配至少下降 20%”的诊断信号为 FAIL，证明它能识别尚无改进的版本。该 20% 是组件实验信号，不是用户的 285.3 ms 重载门槛。

新实现对照包含六份 fixture 的 identity map、十三个边界输入和固定随机种子的 10,000 个输入，总计 10,019 个；解码结果与冻结旧实现相同。三十对中，旧／新平均分配为 **2,051,940 / 490,385.6 bytes**，约下降 **76.1%**；平均墙钟约 **2.517 / 1.269 ms**。这些是已充分预热、没有完整 Minecraft 的组件结果，不代表每次真实重载必然节省相同时间。旧／旧与旧／新两个会话的时间也不能跨会话相减；线程 CPU 计数分辨率有限。

新回归通过公开 registry 查询检查密集列、空行与尾空行、非法段原子拒绝、额外字段／不完整尾字段容错，以及跨行 source/column/name 的正负增量。前三项在旧源码也通过，用来锁定兼容行为；性能红绿来自组件分配信号，整体性能红绿仍来自原正式采样器。

## 验证与交付边界

实现提交：`606d1fff6b14e4a513bf16e247b0ec37437195c6`。完整验证已通过：

```text
gradlew.bat --offline :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
python tools/nekojs-ci-gates.py all --out <evidence>/ci-gates.json
```

第一条包含 common isolation、普通测试及节点 platform gate，总计 **4,989 tests、0 failures/errors、271 skipped**，10m43s、106 tasks。common 为 2,380 项；处理器 13 项；五节点含 platform gate 分别为 442 / 675 / 675 / 402 / 402 项。第二条四个 gate 全部 PASS；CI 报告一致性与上述实际测试结果分别保留，未据跳过项声称运行通过。没有改 declaration golden 或生成文件。该阶段未另跑游戏 GameTest、NBT smoke 或 TypeScript 编译；没有改它们的对应行为。

随后在 clean 专用性能 checkout 上，按原采样器预先指定两组各五次重载、两次预热加五次正式启动。保留首值、失败、端口和缓存，期间不并行本任务构建、测试或其他游戏，不使用外部 QoS controller。制品、实际编译类和正式源码均绑定至上述提交。

本改动是小幅 CPU／分配优化，不能只凭组件通过宣布重载目标完成。维护者接受、包长期内存和 Fabric 目录包资源桥仍分别验收。

正式 cohort 已完成：两组均值 **601.2 / 527.4 ms**，启动 **60346.8 ms**，三组均 FAIL，全部样本有效，九会话正常停服。没有依据组件收益改判整体门槛，也不能把跨会话差值归因于该解码器。主线对应提交为 `280128e1`，源码／测试与实测提交相同。完整原始记录、制品、XML 和重算器见[冻结证据](../architecture-refactor/evidence/2026-10-19-source-map-decoder/README.md)。后续[启动边界诊断](2026-10-19-startup-boundaries.md)单独运行，不替代本次正式结果。
