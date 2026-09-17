# 验证命令与结果（ticket 39，worktree `t39`）

执行日期：2026-09-17（第二轮：双轴审查整改后重跑）。全部在 worktree
`D:\mcmodDemo\NekoJS\.worktrees\t39\NekoJS-mult`（分支 `ticket-39-item-block-mod`）内执行；
代码冻结在整改提交（见 `git log --oneline`，`bc1fb396`）之后，`docs/` 交付物随后单独提交。

| # | 命令 | 结果 | 关键数字（来自 `build/test-results/test/*.xml` 聚合） |
|---|---|---|---|
| 1 | `./gradlew :common:check :common-api-processor:test --console=plain` | BUILD SUCCESSFUL（exit 0） | `:common` 205 suites / 1504 tests / 0 failures / 4 skipped；`common-api-processor` 1 suite / 13 tests / 0 failures |
| 2 | `./gradlew guardLint --console=plain` | BUILD SUCCESSFUL（exit 0） | `guardLint: 守卫块 271，扫描 424 个文件；超限豁免 0 个；警告 0 条`（common 零 MC/loader import） |
| 3 | `./gradlew :26.1.2:build :1.21.1:build :26.1.2-fabric:build :26.2.0:build :26.2.0-fabric:build --console=plain` | BUILD SUCCESSFUL（exit 0） | 五节点全绿（下表）；含 test 编译与执行 |

## 五节点逐节点数字

| 节点 | suites | tests | skipped | failures |
|---|---|---|---|---|
| `:26.1.2` | 61 | 306 | 52 | 0 |
| `:26.2.0` | 61 | 306 | 52 | 0 |
| `:26.1.2-fabric` | 39 | 209 | 24 | 0 |
| `:26.2.0-fabric` | 39 | 209 | 24 | 0 |
| `:1.21.1` | 46 | 213 | 8 | 0 |

## 票 39 fixture 逐类结果（整改后；★ = 本轮新增/升级）

| 节点 | fixture | tests | executed | skipped |
|---|---|---|---|---|
| common | `Ticket39DomainCollectionTest`（root 收集挂载点 + 联合边界 + ★ F8 key-dispatch 拒绝） | 8 | **8** | 0 |
| 26.1.2 / 26.2.0 | `ModificationSetterPropertyParityTest`（★ F1 生产投递形态 + ★ F7 write-only） | 10 | **10** | 0 |
| 26.1.2 / 26.2.0 | `Ticket39ModificationOwnershipTest`（★ F10 网络符号 guard） | 5 | **5** | 0 |
| 26.1.2 / 26.2.0 | `Ticket39ModificationEventSurfaceTest` | 2 | **2** | 0 |
| 26.1.2 / 26.2.0 | `ModificationLegacyCharacterizationTest`（★ F3 registry-free 探针 + ★ 计划层 AC6） | 4 | **2** | 2 |
| 26.1.2 / 26.2.0 | `Ticket39ModificationScriptE2ETest`（registry-gated） | 6 | 0 | 6 |
| 26.1.2 / 26.2.0 | `Ticket39BlockModificationScriptE2ETest`（registry-gated） | 4 | 0 | 4 |
| 26.1.2 / 26.2.0 | `Ticket39ModificationExamplesTest`（registry-gated） | 4 | 0 | 4 |
| 26.1.2 / 26.2.0 | `BlockModificationEventJSTest`（registry-gated） | 6 | 0 | 6 |
| 26.1.2 / 26.2.0 | `ItemModificationComponentsTest`（registry-gated） | 15 | 0 | 15 |
| 两个 fabric 节点 | parity 10 / characterization 2(+2 skip) / surface 2 / ownership 5 / E2E 6 skip / block E2E 4 skip / examples 4 skip | 45 | **19** | 16 |
| 1.21.1 | parity 8（无 food 面）/ characterization 2(+2 skip) / surface 2 / ownership 5 / E2E 6 skip | 25 | **17** | 8 |

**registry-gated skip 数**（26.1.2 / 26.2.0 各 37，与审查 F9 的勘误一致）：
6（block event）+ 15（components）+ 2（characterization 端到端）+ 6（item E2E）+ 4（block E2E）
+ 4（examples）= 37。

## skip 口径与真跑面

- registry-gated fixture 需要 vanilla 注册表（`VanillaRegistryProbe`，无 FML loader 的普通测试
  JVM 下为 false）→ 按 `Assumptions.assumeTrue` 跳过并在报告为 skipped（与既有基线同口径）。
- 真跑面（本机实测五节点）：`common` 8（真实 root + 真实脚本管线 + 真实 Graal）、五节点
  parity 10/10/10/10/8、ownership 5、surface 2、characterization 2 —— 覆盖 candidate/commit、
  联合边界、setter/property parity（含**生产投递形态**）、旧事实探针、计划层 AC6、结构 guard。

## 环境说明（本轮实测，非本票缺陷）

- 本机同时运行了**其它 worktree 的 `:common:test`**（主树与 t16 的 test worker 进程），而
  `TestPlatformInit.ensureInitialized()` 使用**固定名** tmp gameDir
  `<tmp>/nekojs-test-gamedir`（`TestGameDirs.unique` 才是 PID 隔离约定）→ 并行 JVM 互相清扫
  脚本目录 / 争用 `logs/nekojs/*.log` 文件锁，表现为偶发「脚本没跑」的假失败
  （证据：`FileSystemException: ...server.log -> ...old/server.log: 另一个程序正在使用此文件`）。
- 处置：与其它 worktree 错峰重跑；受影响套件（`ScriptReloadGenerationTest`、
  `ReloadMemoryStabilityTest`、`ScriptReloadRegressionTest`、`Ticket07RuntimeThreadsTest`、
  `StartupReloadScriptFileFullReloadTest`）单跑全部绿，随后 `:common:check` 全量绿（205 suites /
  1504 tests / 0 failures）；并行 JVM 的固定 tmp 目录问题是测试基建问题，建议后续票改为
  `TestGameDirs.unique(...)`（不在本票范围，REPORT §12 G6）。

## golden 完整性

本票未修改任何 golden 输入（`versions/*/src/test/resources/golden/block-events-api.txt` 的
`modification` 条目在本票之前即存在）；未执行 `-Dnekojs.golden.regenerate`；
`ApiManifestGoldenTest` 在 `:common:check` 内以只读模式通过。
