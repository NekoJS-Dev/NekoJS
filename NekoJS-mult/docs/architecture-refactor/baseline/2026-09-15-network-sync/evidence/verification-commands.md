# 票 17 验证命令与结果（全部在 worktree D:\mcmodDemo\NekoJS\.worktrees\t17\NekoJS-mult 内执行）

## 必跑四门（工单指定）

| 命令 | 结果 |
|---|---|
| `./gradlew :common:check :common-api-processor:test --console=plain` | BUILD SUCCESSFUL（1m17s，0 failed task） |
| `./gradlew guardLint --console=plain` | BUILD SUCCESSFUL；`守卫块 253，扫描 399 个文件；超限豁免 0 个；警告 0 条` |
| `./gradlew :26.1.2:build --console=plain` | BUILD SUCCESSFUL；`:26.1.2:test --rerun` 实测 220 tests / 34 skipped / 0 failures / 0 errors |
| `./gradlew :26.1.2-fabric:build --console=plain` | BUILD SUCCESSFUL（含 verifyFabricRuntimeArtifact）；test 114 tests / 6 skipped / 0 failures / 0 errors |

## 跨节点 network fixture 专项（五节点）

| 节点 | 命令 | 结果（tests/failures） |
|---|---|---|
| 26.1.2 | `./gradlew :26.1.2:test --rerun` | PayloadWireFormatGoldenTest 13/0；NetworkGenerationRoutingTest 8/0；NetworkPayloadDefenseLineTest 9/0；NetworkRegistrationSourceTraceTest 9/0；ScriptPayloadRegistrationShapeTest 2/0 |
| 26.2.0 | `./gradlew :26.2.0:test --tests "com.tkisor.nekojs.network.*" --tests "com.tkisor.nekojs.platform.compat.ScriptPayloadRegistrationShapeTest"` | 同上五类 13+8+9+9+2 全 0 失败（golden hex 与 26.1.2 全等）† |
| 1.21.1 | `./gradlew :1.21.1:test --tests "com.tkisor.nekojs.network.*"` | 13+8+9+9 全 0 失败（golden hex 全等；ScriptPayloadRegistrationShapeTest 由 `//? if neoforge { //? if >=26` 守卫在 1.21.1 剥离）† |
| 26.1.2-fabric | `./gradlew :26.1.2-fabric:test`（与 26.2.0-fabric 并行同跑） | 全套 114/0；含 FabricNetworkRegistrationOnceTest 1/0、network 四类 13+8+9+9 全 0 |
| 26.2.0-fabric | `./gradlew :26.2.0-fabric:test`（并行同跑） | 全套 114/0（fabric-only 类构成与 26.1.2-fabric 相同）† |

† **勘误（审查必修-1）**：本表 26.2.0 / 1.21.1 / 26.2.0-fabric 三行的 trace 计数是**旧版 7 用例**的运行结果——
方向钉住用例（`neoforgePayloadDirectionsStayOnLegacyRegistration` / `fabricPayloadDirectionsStayOnLegacyRegistration`）
在最后一个实现提交 70f0f859 才加入，上述三节点当时跑的是加用例前的文件。终版数字见下方「审查整改复跑」。

并行说明：仓库 `gradle.properties` 开了 `org.gradle.parallel=true`，两 fabric 节点的 test JVM 会并行。
首轮并行跑时 NetworkGenerationRoutingTest 出现跨 JVM 互清脚本目录（共享 tmp gameDir）的连带失败，
已通过「gameDir 按测试 JVM 的 CWD 哈希唯一化」修复（NetworkGenerationRoutingTest.initPlatform），
修复后双 fabric 并行全绿（上表结果即修复后并行实测）。

## 复现（golden 单类）

```bash
./gradlew :26.1.2:test --tests "com.tkisor.nekojs.network.PayloadWireFormatGoldenTest" --console=plain
# 13 tests: 6 encode-hex + 6 decode-roundtrip + 1 id 钉住，全绿
```

## 审查整改复跑（终版文件状态，含全部整改提交）

| 命令 | 结果 |
|---|---|
| `./gradlew :26.1.2:test :26.2.0:test :1.21.1:test --tests "com.tkisor.nekojs.network.*" --tests "com.tkisor.nekojs.platform.compat.ScriptPayloadRegistrationShapeTest" --console=plain` | 三节点全绿：trace 终版 9/0（含 common 扫描扩展）、golden 13/0、routing 8/0、defense 9/0；shape 2/0（26.x）在 1.21.1 守卫剥离 |
| `./gradlew :26.1.2:test :26.2.0:test :1.21.1:test --rerun --console=plain` | 全套：26.1.2 = 220/0、26.2.0 = 220/0、1.21.1 = 139/0（KeyBind/QueryTool/EventBusForgeBridge 的 gameDir 唯一化改动一并覆盖） |
| `./gradlew :26.1.2-fabric:test :26.2.0-fabric:test --rerun`（连续 7 轮，含 6 轮稳定性循环） | 每轮双节点全套 114/0；**首轮整改验证时曾复现一次 RoutingTest 偶发失败**（候选脚本零执行），根因与修复见 REPORT §6-R5 修订 |

flake 根因摘要：`QueryToolDeclarationParityTest`/`KeyBindEventsTest`/`EventBusForgeBridge*Test` 的 Platform 桩
用**固定名** tmp gameDir，Platform 初始化先到先得——RoutingTest 寄生在同 JVM 更早初始化的固定名目录上，
并行 test JVM（双 fabric 节点、跨 worktree）共享该目录并在 `@BeforeEach` 清脚本时互删 fixture。
修复 = `TestGameDirs.unique(base)`（base+PID）统一替换全部固定名 gameDir（含 RoutingTest 自身），
跨 JVM 目录相撞在结构上不可能；6 轮连续并行复跑全绿。
