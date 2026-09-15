# 验证命令与结果（ticket 15，2026-09-15）

全部在 worktree 项目根 `NekoJS-mult` 执行；分支 `ticket-15-registry-startup`。

## 完成前验证（工单要求）

```bash
./gradlew :common:check :common-api-processor:test --console=plain
# BUILD SUCCESSFUL — common: 201 suites / 1478 tests / 0 failures / 0 errors / 4 skipped
#（ticket 14 关票基线 200/1474；本票 +1 suite = RegistryBuilderDeclarationParityTest 4 用例）

./gradlew guardLint --console=plain
# BUILD SUCCESSFUL — 守卫块 255，扫描 403 个文件；超限豁免 0 个；警告 0 条
#（common 零 MC/loader import 未放宽；新增 wrapper 文件的 loader import 都在整文件/行内守卫内）

./gradlew :26.1.2:build --console=plain
# BUILD SUCCESSFUL — 全量 test：45 suites / 206 tests / 0 failures / 36 skipped
#（本票 +5 suites / +27 tests：Parity 8（2 skip：BlockBuilder 需 vanilla）/ E2E 9 /
#  Negatives 5 / Golden 4（fluid 用例在 fabric 节点被守卫剥离）/ Example 1）

./gradlew :1.21.1:build --console=plain
# BUILD SUCCESSFUL — 全量 test：33 suites / 125 tests / 0 failures / 0 skipped
#（改动了带版本守卫的共享代码：mc_legacy_api 标记新增于 2 个共享测试文件 + 6 个主代码文件）
```

## 跨节点定向检查（本票自行加跑，非工单硬性要求）

```bash
./gradlew :26.2.0:test --tests "com.tkisor.nekojs.wrapper.registry.gen.RegistryBuilderSurfaceGoldenTest" --console=plain
# BUILD SUCCESSFUL（26.x golden 与 26.1.2 一致——预处理副本同源）

./gradlew :26.1.2-fabric:test --tests "com.tkisor.nekojs.wrapper.registry.gen.RegistryBuilderSurfaceGoldenTest" --console=plain
# BUILD SUCCESSFUL — 3 用例（fluid 用例被 loader 守卫剥离；26.x golden 对 fabric 成立：
# golden 只冻 loader 无关面）+ :26.1.2-fabric:compileJava 通过（FabricRegistryAdapter 接线编译）

./gradlew :26.2.0-fabric:build --console=plain   # 由主会话合并后统一执行（五节点全量 gate）
```

## golden 生成（root 树手动路径，query 域 ticket 25 同款）

```bash
# 生成方式：临时 scratch（已删除）以生产同款 fixture types 驱动
# RegistryBuilderSurfaces.derive(...) → RegistryBuilderTsRenderer.render(...) 写入 build/tmp；
# 26.1.2 输出提升为 src/test/resources/golden/registry/startup-builders.d.ts（161 行）；
# 1.21.1 输出提升为 startup-builders-1.21.1.d.ts（152 行）。
# 复核：RegistryBuilderSurfaceGoldenTest.tsDeclarationIsByteStableAndMatchesGolden 在
# 四个可测节点全部通过（26.2.0 / 26.2.0-fabric 由主会话统一复核）。
```

## 快速迭代（单文件过滤示例）

```bash
./gradlew :26.1.2:test --tests "com.tkisor.nekojs.wrapper.registry.*" --console=plain
```
