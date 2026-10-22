# 7bea6d6d 当前候选完整检查

生产源码 `7bea6d6dcc9d0a70be6886f7ee1c79af9a9dae75`；检出中的后续提交仅变更证据与交接文档。构建与安装准备分别核对生产源码差异，五节点的全部 common/node class 均与安装 JAR 逐字节匹配。26.1.2 的 1640 个类和 JAR 也与该源码的[正式性能采样](../2026-10-22-source-facts-performance/README.md)完全相同。

## 构建

`gradlew.bat -g D:/mcmodDemo/NekoJS/.gradle-perf02 -Dorg.gradle.java.home="C:/Program Files/Java/jdk-25.0.2" --offline --no-parallel :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain`

PASS：9m16s，106 tasks / 33 executed / 73 up-to-date；5070 tests / 271 skipped / 0 failures / 0 errors。包含 common 隔离、处理器、guard 与五节点 platform gate；沿用 Gradle 对未改变输入的有效结果，不声称所有测试都重新执行。

## 新安装运行

五节点均使用全新目录、全新世界及新 JAR，全部运行门禁 PASS，五个进程均经 RCON 正常退出 0。各节点的真实宿主集合检查在初次加载和重载后合计至少出现两次。

| 节点 | 与安装 JAR 匹配的生产类 | 运行 |
|---|---:|---|
| 1.21.1 NeoForge | 1569 | PASS |
| 26.1.2 NeoForge | 1640 | PASS |
| 26.2.0 NeoForge | 1640 | PASS |
| 26.1.2 Fabric | 1583 | PASS |
| 26.2.0 Fabric | 1583 | PASS |

实际检查覆盖注册回调、嵌套 builder 指纹、方块与适用节点的流体放置、完整重载、声明生成、Python AST；NeoForge 另有交易池生命周期，现代 NeoForge 另有空动态 generation 保留物理效果。26.1.2 的原次数/间隔实体查询、重载前后实际效果与实体 NBT 均通过。

准备时显式统一 `pause-when-empty-seconds=-1`，其余运行断言沿用既有工具；该条件写入 manifest。旧 `4405b0b0` 的首次实体查询失败和独立控制实验仍保留，未将本批成功当作旧失败原因已确诊，也未将旧首轮改判为五节点全绿。

## 完整 IDE

1609 个新生成 Python stub 的默认 Pyright：0 errors / 0 warnings，全部通过。
十入口默认严格 TypeScript：`skipLibCheck=false`，全部退出 2、无编译器崩溃，合计 5867 条诊断，完整 IDE gate 继续 FAIL。

| 节点 | server | startup |
|---|---:|---:|
| 1.21.1 | 515 | 516 |
| 26.1.2 | 578 | 579 |
| 26.2.0 | 569 | 570 |
| 26.1.2 Fabric | 646 | 647 |
| 26.2.0 Fabric | 623 | 624 |

本批数据来自本批五节点安装后的实际生成。没有使用旧声明输出替代新测量，也没有缩小默认包含范围或隐藏公开成员。

## 仍开放的范围

重载性能仍为 340.0 / 292.8 ms，超过原 285.3 ms；启动 33486.6 ms 通过。阶段诊断与独立编译实验不替代该正式失败。完整严格 TSC、COMMIT 后激活失败的真实跨进程验收、后续最终源码对应的客户端/TCP/分离回退，以及维护者最终结论仍开放；已冻结的旧候选证据保持原有范围。

归档包含矩阵 XML/日志、源码/JAR/class 绑定、实际运行与退出记录、所有新声明和 stub、完整 IDE 输出及执行工具；排除 JAR/依赖、世界、server.properties、完整启动参数文件和人工签字。共 5002 项，9,128,801 bytes。

离线复核：`python docs/architecture-refactor/evidence/2026-10-23-current-candidate-checks/verify.py`。归档复核 PASS 不表示完整 IDE、性能或整票发布验收通过。
