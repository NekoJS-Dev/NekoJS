# 验证命令与结果（ticket 07，2026-09-15，worktree t07）

环境：Windows + Git Bash；JDK 由用户级 gradle.properties 钉为 jdk-25.0.2
（common 测试 JVM 按 toolchain 跑 jdk-21.0.10，与 master 基线一致）；
分支 `ticket-07-runtime-threads`，基线 `47f4a444`（master）。

全部命令在 `D:\mcmodDemo\NekoJS\.worktrees\t07\NekoJS-mult` 下执行。

| # | 命令 | 结果 | 摘要 |
|---|---|---|---|
| 1 | `./gradlew :common:check --console=plain`（最终以 `:common:test --rerun` + `:common:check` 复核） | BUILD SUCCESSFUL | 195 个测试类 / **1448 tests**，failures 0，errors 0（XML 时间戳 01:16:25，含全部票 07 改动后的最终源） |
| 2 | `./gradlew :common-api-processor:test --rerun --console=plain` | BUILD SUCCESSFUL | 1 个测试类 / **13 tests**，failures 0（01:21:04） |
| 3 | `./gradlew guardLint --console=plain` | BUILD SUCCESSFUL | `守卫块 248，扫描 331 个文件；超限豁免 0 个；警告 0 条` |
| 4 | `./gradlew :26.1.2:check --console=plain`（最终以 `:26.1.2:test --rerun` 复核） | BUILD SUCCESSFUL | 38 个测试类 / **173 tests**，failures 0，errors 0（01:18:54，含全部票 07 改动后的最终源） |
| 5 | `npm run test:probe-types` | **未运行（不适用）** | 本票零 probe/declaration 面改动：`git diff master..HEAD --stat` 只含 `common/src/{main,test}/java`、`common/src/test` 与本 docs 目录，无 .d.ts / probe 产物变更 |

## 复现命令

```bash
cd D:/mcmodDemo/NekoJS/.worktrees/t07/NekoJS-mult
./gradlew :common:test --rerun --console=plain
./gradlew :common:check --console=plain
./gradlew :common-api-processor:test --rerun --console=plain
./gradlew guardLint --console=plain
./gradlew :26.1.2:test --rerun --console=plain
./gradlew :26.1.2:check --console=plain
```

## 票 07 相关测试类逐一结果（:common:test，最终 rerun）

| 测试类 | tests | 失败 | 覆盖 |
|---|---|---|---|
| `com.tkisor.nekojs.core.SyncEvalWatchdogTest` | 5 | 0 | 墙钟 interrupt 终止空循环（1.32s）/ 健康脚本不受扰 / 超时关闭 noop / 并发进入被 Graal 拒绝（AC8 探针）/ 跨线程 interrupt 打断在途求值 |
| `com.tkisor.nekojs.core.lifecycle.ScriptLifecycleGateTest` | 11 | 0 | 门契约全量（串行/重入/嵌套放行/回调拒绝/close 优先/关闭后拒绝/嵌套 close 拒绝/隔离失败标记） |
| `com.tkisor.nekojs.script.Ticket07RuntimeThreadsTest` | 11 | 0 | AC1–AC9 fixture（见 REPORT.md §2 矩阵） |
| `com.tkisor.nekojs.script.Ticket06RunawayProbeTest` | 1 | 0 | **@Disabled 已移除**：时间窗 while(true) 终止路径转正（票 06 缺陷的修复证据） |
| `com.tkisor.nekojs.core.error.DefaultErrorTrackerTest` | 7 | 0 | 含新增 `recordOfWorldPackScriptOutsideRootDoesNotThrow`（WORLD 包 relativize IAE 修复回归） |
| 既有回归（`ScriptReloadRegressionTest` / `ScriptReloadGenerationTest` / `ReloadMemoryStabilityTest` / `NekoRuntimeRootLifecycleTest` / `NekoRuntimeRootReloadResultTest` / `StartupReloadScriptFileFullReloadTest` 等 190 类） | — | 0 | 票 06/05 行为保持（EventBusJS 分发点 monitor 删除后全部仍绿） |
