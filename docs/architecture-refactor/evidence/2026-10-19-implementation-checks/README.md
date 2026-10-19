# 实现收口后的集中检查

实测源码 `4dcfe6045f4622ab677b885154735a979d5fb714`，覆盖配方 JSON 隔离、Fabric 目录资源桥、同步拒绝后的资源刷新、成对预检共享解析和空 ITEM 阶段完成门控。主代理执行检查；没有并行运行游戏或性能采样。

| 检查 | 结果 |
| --- | --- |
| 定向 common + 两个 Fabric 节点 | 131 tests，0 failures/errors，0 skipped；1m 55s，exit 0 |
| common isolation/check、处理器测试、guardLint、五节点 build | 15m 49s，exit 0；guardLint 377 块 / 559 文件 / 0 警告 |
| 全量普通测试 | 5,005 次执行，0 failures/errors，271 skipped |
| 五节点独立 platformGateTest | 15 次执行，0 failures/errors，0 skipped |
| 源码来源探针与四项 CI 一致性 gate | exit 0；四项均 pass |

全量共 5,020 次测试执行。定向结果另存，不与全量相加冒充独立测试数量。日志包含编译器、原生访问和弃用警告，不宣称零编译警告。检查前后两份已有未提交文件的字节摘要相同，未纳入本次提交或改写 golden。

主要命令（均使用已有 `D:/mcmodDemo/NekoJS/.gradle-perf02`、JDK 25.0.2、`--offline --no-parallel --console=plain`）：

```text
gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build
gradlew.bat -I tools/nekojs-source-roots.init.gradle :1.21.1:help :26.1.2:help :26.2.0:help :26.1.2-fabric:help :26.2.0-fabric:help
python -X utf8 tools/nekojs-ci-gates.py all --out build/priority-closeout/2026-10-19-4dcfe604-verification/ci-gates.json
python -X utf8 docs/architecture-refactor/evidence/2026-10-19-implementation-checks/verify.py
```

common 测试使用构建中配置的 Java 21 toolchain，26.x 节点使用其 Java 25 配置。归档包含原始日志、定向及全量 XML、平台报告、提交源码快照和制品摘要。没有重复打包实际 JAR，不能据本归档独立检查 JAR 内容。`evidence.zip` 为 956 项、1,054,782 bytes，SHA-256 `26e97a73e48e946f49fe24b9a8b09cb5157b6e26e02f7ddcd031f3d291ba5868`；独立复核 PASS。

**仍未完成：** 当前版本原标准性能复验、Fabric Mixin 实际应用与首次客户端模型、实际标签/语言合并、WORLD 双世界切换、远端拒绝/断线资源撤回、原版 `/reload` 和 F3+T。没有新增 GameTest、NBT smoke 或客户端实机证据，也没有维护者整体接受结论。此处通过不改变已冻结的性能 FAIL。
