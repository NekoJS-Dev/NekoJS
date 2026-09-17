# 验证命令与结果（ticket 39，worktree `t39`）

执行日期：2026-09-17。全部在 worktree `D:\mcmodDemo\NekoJS\.worktrees\t39\NekoJS-mult`
（分支 `ticket-39-item-block-mod`）内执行，代码冻结在提交 `41b2bc2c` + `8c928a98`
（测试/主代码）之后；`docs/` 交付物随后单独提交。

| # | 命令 | 结果 | 关键数字（来自 `build/test-results/test/*.xml` 聚合） |
|---|---|---|---|
| 1 | `./gradlew :common:check :common-api-processor:test --console=plain` | BUILD SUCCESSFUL（exit 0） | `:common` 205 suites / 1503 tests / 0 failures / 4 skipped；`common-api-processor` 1 suite / 13 tests / 0 failures（另以 `:common:test --rerun-tasks` 强制全量重跑复核：exit 0，同一计数） |
| 2 | `./gradlew guardLint --console=plain` | BUILD SUCCESSFUL（exit 0） | `guardLint: 守卫块 269，扫描 423 个文件；超限豁免 0 个；警告 0 条`（common 零 MC/loader import；本票 common 侧只新增纯 JVM 契约） |
| 3 | `./gradlew :26.1.2:build --console=plain` | BUILD SUCCESSFUL（exit 0） | 61 suites / 300 tests / 0 failures / 52 skipped |
| 4 | `./gradlew :26.1.2-fabric:build --console=plain` | BUILD SUCCESSFUL（exit 0） | 39 suites / 203 tests / 0 failures / 24 skipped |
| 5 | `./gradlew :1.21.1:build --console=plain` | BUILD SUCCESSFUL（exit 0） | 46 suites / 207 tests / 0 failures / 8 skipped |

## 票 39 fixture 逐类结果

| 节点 | fixture | tests | executed | skipped |
|---|---|---|---|---|
| common | `Ticket39DomainCollectionTest`（root 收集挂载点 + 联合边界，合成 Adapter） | 7 | **7** | 0 |
| 26.1.2 | `ModificationSetterPropertyParityTest`（真实 GraalJS setter/property parity） | 7 | **7** | 0 |
| 26.1.2 | `Ticket39ModificationOwnershipTest`（无 static 快照/无 restore-all 入口/owner 归属） | 4 | **4** | 0 |
| 26.1.2 | `Ticket39ModificationEventSurfaceTest`（catalog/golden 面） | 2 | **2** | 0 |
| 26.1.2 | `Ticket39ModificationScriptE2ETest`（脚本→Adapter，item） | 6 | 0 | 6 |
| 26.1.2 | `Ticket39BlockModificationScriptE2ETest`（脚本→Adapter，block） | 4 | 0 | 4 |
| 26.1.2 | `Ticket39ModificationExamplesTest`（examples/ 同源执行） | 4 | 0 | 4 |
| 26.1.2 | `BlockModificationEventJSTest`（collect→preflight→apply 直驱） | 6 | 0 | 6 |
| 26.1.2 | `ItemModificationComponentsTest`（Adapter 组件写入） | 15 | 0 | 15 |
| 26.1.2 | `ModificationLegacyCharacterizationTest`（旧顺序重放 + 声明移除翻转） | 2 | 0 | 2 |
| 26.1.2-fabric | 同上（`ModificationLegacyCharacterizationTest` / parity / surface / ownership / E2E / examples） | 29 | **13** | 16 |
| 1.21.1 | parity（无 food 面 → 5）/ surface / ownership | 11 | **11** | 0 |
| 1.21.1 | item 脚本 E2E | 6 | 0 | 6 |

**skip 口径说明**：无 FML loader 的普通 Gradle 测试 JVM 里 `VanillaRegistryProbe` 为 false，
registry-gated fixture 按 `Assumptions.assumeTrue` 跳过（与既有基线同口径：票 14 证据里
`:26.1.2:check` 也是 34 skipped）。真跑面由 registry-free fixture 承担：common 7 个
（真实 root + 真实脚本管线）、parity 7 个（真实 GraalJS Context）、ownership 4 个、
surface 2 个、fabric 同批、1.21.1 同批。registry-gated fixture 需在 ModDev/开发环境
（或 CI 的 GameTest smoke）执行，见 REPORT §12 G1。

## 原始日志

命令输出（`--console=plain`）逐条保存在执行者本地 `t39logs/cmd{1..5}.log`；本目录只登记
上表与 REPORT 引用的判定数字，不把逐行 Gradle 噪声入库。

## golden 完整性

本票未修改任何 golden 输入：`versions/*/src/test/resources/golden/block-events-api.txt`
的 `modification` 条目在本票之前即存在；未执行 `-Dnekojs.golden.regenerate`
（`:common:check` 内 `ApiManifestGoldenTest` 以只读模式通过，`--rerun-tasks` 复核同一结果）。
