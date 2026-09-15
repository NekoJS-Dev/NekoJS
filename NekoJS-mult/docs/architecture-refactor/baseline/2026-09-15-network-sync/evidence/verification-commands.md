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
| 26.2.0 | `./gradlew :26.2.0:test --tests "com.tkisor.nekojs.network.*" --tests "com.tkisor.nekojs.platform.compat.ScriptPayloadRegistrationShapeTest"` | 同上五类 13+8+9+9+2 全 0 失败（golden hex 与 26.1.2 全等） |
| 1.21.1 | `./gradlew :1.21.1:test --tests "com.tkisor.nekojs.network.*"` | 13+8+9+9 全 0 失败（golden hex 全等；ScriptPayloadRegistrationShapeTest 由 `//? if neoforge { //? if >=26` 守卫在 1.21.1 剥离） |
| 26.1.2-fabric | `./gradlew :26.1.2-fabric:test`（与 26.2.0-fabric 并行同跑） | 全套 114/0；含 FabricNetworkRegistrationOnceTest 1/0、network 四类 13+8+9+9 全 0 |
| 26.2.0-fabric | `./gradlew :26.2.0-fabric:test`（并行同跑） | 全套 114/0（fabric-only 类构成与 26.1.2-fabric 相同） |

并行说明：仓库 `gradle.properties` 开了 `org.gradle.parallel=true`，两 fabric 节点的 test JVM 会并行。
首轮并行跑时 NetworkGenerationRoutingTest 出现跨 JVM 互清脚本目录（共享 tmp gameDir）的连带失败，
已通过「gameDir 按测试 JVM 的 CWD 哈希唯一化」修复（NetworkGenerationRoutingTest.initPlatform），
修复后双 fabric 并行全绿（上表结果即修复后并行实测）。

## 复现（golden 单类）

```bash
./gradlew :26.1.2:test --tests "com.tkisor.nekojs.network.PayloadWireFormatGoldenTest" --console=plain
# 13 tests: 6 encode-hex + 6 decode-roundtrip + 1 id 钉住，全绿
```
