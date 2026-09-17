# 验证命令与结果（ticket 16，2026-09-17）

全部在 worktree 项目根 `NekoJS-mult` 执行；分支 `ticket-16-registry-dynamic`，认领基线 `230587cc`。
原始日志（未入库，构建产物）：`build/t16-logs/{1-common-check,2-guardlint,3-26.1.2-build,4-1.21.1-build}.log`；
双轴审查整改轮的复跑日志：`build/t16-logs/r{6-common-check,7-guardlint,8-builds,9-node-tests}.log`。

## 双轴审查整改轮复跑（2026-09-17，最终证据）

```bash
./gradlew :common:check :common-api-processor:test --console=plain --no-build-cache
# BUILD SUCCESSFUL in 2m 7s — common: 213 suites / 1539 tests / 0 failures / 0 errors / 4 skipped
#                          processor: 1 suite / 13 tests / 0 failures

./gradlew guardLint --console=plain
# BUILD SUCCESSFUL — guardLint: 守卫块 265，扫描 415 个文件；超限豁免 0 个；警告 0 条

./gradlew :26.1.2:build :1.21.1:build --console=plain --no-build-cache
# BUILD SUCCESSFUL — 节点测试再强制真实执行（cleanTest + --no-build-cache）：
#   :26.1.2:test — 54 suites / 271 tests / 0 failures / 0 errors / 36 skipped
#   :1.21.1:test — 41 suites / 188 tests / 0 failures / 0 errors / 0 skipped
#（r3 轮已真跑同一份代码；r8 UP-TO-DATE、r9 FROM-CACHE 后由 r10 强制复跑确认。）
```

## 完成前验证（工单要求；同一份代码的早前一轮）

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
# BUILD SUCCESSFUL — 41 suites / 188 tests / 0 failures / 0 errors / 0 skipped
#（共享代码有改动：common/src/main 计划面 + src/main 触发点的行内 >=26 守卫 + 孪生注释同步。
#  守卫的因果见 REPORT §10 P1：1.21.1 的编译单元是 versions/1.21.1/src 孪生文件，共享文件不
#  参与其编译，因此本跑不是"修断裂"的证据；该守卫保护的是孪生文件重提取路径。）
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
#   common/src/test/resources/nekojs/dynamic/dynamic-builders.expected.d.ts（33 行）
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

## 验证环境与跨 suite 隔离（2026-09-17 双轴审查整改轮实测）

**整轮 `:common:test` 前置**：清空 `java.io.tmpdir/nekojs-test-gamedir`（或其他进程未占用时等同的干净状态）。

依据（同一份代码，只换 tmp 目录状态）：

```text
r1  :common:check（陈旧 tmp 目录）  -> FAILED：ScriptReloadGenerationTest 3 例
r4  :common:test  --no-build-cache（陈旧，且前一轮被 kill） -> FAILED：本票 facade 套件 8 例；
                                     该轮后在途 fixture `server_scripts/entry.js`（Ticket07 的
                                     `Ticket07Events.ping(...)`）残留，运行最终被人工终止（挂起）
r5  :common:test（清空 tmp 目录后）  -> BUILD SUCCESSFUL：213 suites / 1539 tests / 0 failures / 4 skipped
A   :script.* + core.dynamic.* + wrapper.*（残留在场） -> BUILD SUCCESSFUL（说明残留非确定性触发）
```

机制（现场证据）：固定 game dir 被多个 suite 共用，各自留下状态——
`server_scripts/entry.js`（`Ticket07RuntimeThreadsTest` 等只清 @BeforeEach 的类）、
`server_packs/<hash>/packs_demo/**` 与 `config/trusted-servers.json`（pack 同步 suite）。
`TestPlatformInit.ensureInitialized(Path)` 只认第一个初始化者、`NekoJSPaths.INSTANCE` 是进程级
static 缓存，因此**本票新增的 `uniqueGameDir` 命名在整轮 suite 中当前是惰性的**（实测：
`/tmp/nekojs-dynamic-registry-*` 全为空目录，真实脚本树在 `/tmp/nekojs-test-gamedir/nekojs/`）——
真正的 per-class 隔离需要 helper 下沉 + `NekoJSPaths` 复位（REPORT §11 G9）。

结论：该失败类别是既有**跨 suite 隔离债**（`TestGameDirs` javadoc 记录的同源 flake），与本票
代码无关；本票验证一律「先清空 tmp game dir，再整轮跑」。
