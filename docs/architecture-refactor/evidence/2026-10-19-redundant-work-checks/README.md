# 重载重复工作修正的集中检查

实测代码提交 `46505ad2c7652cc90fd723766e3c40340d0c7dcb`。先完成实现，再执行本轮检查；未更新 golden、放宽门禁或修改两份已有工作区文件的字节。

| 检查 | 结果 |
| --- | --- |
| 成员/组合预检、schema 所有权及两个 NeoForge 配方隔离/生命周期定向检查 | 89 tests，0 failures/errors/skipped，51.101 s |
| common isolation/check、处理器、guardLint、五节点 build | PASS，465.847 s |
| 全量 JUnit 与平台 gate 执行 | 5,021 次，0 failures/errors，271 skipped |
| 重新导出实际 source roots | PASS，2.545 s |
| 四项 CI 一致性 gate | 全部 PASS，0.206 s |

全量包含 common 2,385、处理器 13、1.21.1 442、两个 26.x NeoForge 各 675、两个 Fabric 各 408，以及五节点各 3 项 platformGateTest。它是执行次数，不是不同测试的去重数量；定向 89 次不再叠加。跳过项原样保留，不作为执行通过。

```text
gradlew.bat -g D:/mcmodDemo/NekoJS/.gradle-perf02 -Dorg.gradle.java.home=C:/Program Files/Java/jdk-25.0.2 --offline --no-parallel --console=plain :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build
```

guardLint 为 377 守卫块、559 文件、零警告；日志仍有编译器/弃用警告。受保护的 `index.d.ts` 与 `dynamic-registry-events.expected.d.ts` 的原始摘要前后相等。完整参数、定向命令、控制器、原始日志、XML、节点 gate/source roots、变更源码与五节点 JAR 摘要已冻结；本归档不包含 JAR 二进制。

```text
python -X utf8 docs/architecture-refactor/evidence/2026-10-19-redundant-work-checks/verify.py
```

归档 952 项、1,048,752 bytes，SHA-256 `b6ff210b55f5ced1536509925c09a7394a877ebdcfc602590557ec72c519a530`。独立复核只校验冻结证据，不启动 Gradle 或游戏。

本轮检查未运行游戏、性能采样、Mixin/codec 实机或 Fabric 客户端资源验收。它不能改判此前重载 FAIL；后续正式采样应绑定本次实际产物，并继续使用原 sampler、全部首值与 285.3 / 41173 ms 门槛。
