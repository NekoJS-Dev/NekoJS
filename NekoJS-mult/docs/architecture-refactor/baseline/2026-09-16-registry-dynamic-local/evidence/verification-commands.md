# 验证命令与结果（ticket 16，2026-09-17）

全部在 worktree 项目根 `NekoJS-mult` 执行；分支 `ticket-16-registry-dynamic`，认领基线 `230587cc`。
原始日志（未入库，构建产物）：`build/t16-logs/{1-common-check,2-guardlint,3-26.1.2-build,4-1.21.1-build}.log`。

## 完成前验证（工单要求）

```bash
./gradlew :common:check :common-api-processor:test --console=plain
# BUILD SUCCESSFUL — common: 213 suites / 1539 tests / 0 failures / 0 errors / 4 skipped
#                     processor: 1 suite / 13 tests / 0 failures
#（ticket 15 关票基线 201/1478；本票 +12 suites / +61 tests：plan 语义 9 + parity 6 + 惰性 4 +
#  facade 6 + reload 6 + 候选惰性 5 + 声明 parity 4 + 示例 1 + 声明 golden 2 等）

./gradlew guardLint --console=plain
# BUILD SUCCESSFUL — guardLint: 守卫块 265，扫描 415 个文件；超限豁免 0 个；警告 0 条
#（common 零 MC/loader import 未放宽；新增行内 `//? if >=26 {` 守卫在 neoforge 文件内，
#  guardLint 无警告）

./gradlew :26.1.2:build --console=plain
# BUILD SUCCESSFUL in 59s — 54 suites / 271 tests / 0 failures / 0 errors / 36 skipped
#（:26.1.2:compileJava 真编译 + :26.1.2:test 真跑，非 UP-TO-DATE）

./gradlew :1.21.1:build --console=plain
# BUILD SUCCESSFUL in 1m 7s — 41 suites / 188 tests / 0 failures / 0 errors / 0 skipped
#（共享代码有改动：common/src/main 计划面 + src/main 触发点行内守卫；此跑验证 1.21.1
#  不再因 facade 引用断裂而失败——见 REPORT §10 P1）
```

## 未在本票运行（留给主会话五节点合并门）

```bash
./gradlew :26.2.0:build --console=plain
./gradlew :26.2.0-fabric:build --console=plain
./gradlew :26.1.2-fabric:build --console=plain
# 未跑：common/src/main 与 src/main 均有改动，按工单口径「五节点全量由主会话合并后跑」，
# 本票不伪报（REPORT §11 G5）。
```

## golden 生成（common 树 `:common:regenerateGoldens` 路径）

```bash
# 先落 placeholder 使 /nekojs/dynamic/ 资源目录在 classpath 可解析（ProbeGoldenSupport.resourceDir
# 走 getResource），再显式再生成；随后逐行审阅 diff：
./gradlew :common:regenerateGoldens \
    --tests "com.tkisor.nekojs.probe.DynamicRegistryEventsDeclarationGoldenTest" --console=plain
# BUILD SUCCESSFUL — 产出两份新 golden：
#   common/src/test/resources/nekojs/dynamic/dynamic-registry-events.expected.d.ts（11 行）
#   common/src/test/resources/nekojs/dynamic/dynamic-builders.expected.d.ts（31 行）
# 旧新 diff/原因/影响/审阅记录：REPORT §8；登记行：managed-surface REGENERATE.md §1。
# 既有 golden 零变化：git status 确认 api-manifest/probe-ts/legacy-tree/startup-builders 未动；
# :common:check 全绿即证（legacy/probe golden 测试一起跑过）。
```

## 快速迭代（单文件过滤示例）

```bash
./gradlew :common:test --tests "com.tkisor.nekojs.core.dynamic.*" \
    --tests "com.tkisor.nekojs.probe.DynamicRegistryEventsDeclarationGoldenTest" --console=plain
# BUILD SUCCESSFUL — 55 用例（本票面）
```
