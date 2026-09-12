# 2026-09-12 五节点构建与契约基线报告（工单 01）

工单：[01: P0 五节点构建与契约基线](../implementation-tickets/01-build-baseline.md)。本报告汇总 W0（去 pin）修复与五节点实测基线；逐节点事实详表见[实测 manifest](node-source-artifact-manifest-measured-2026-09-12.md)，改动前归档见 `w0-config-archive-2026-09-12/`。

## 1. 执行环境

| 项 | Windows host | Linux（Docker） |
|---|---|---|
| OS | Microsoft Windows [版本 10.0.26200.9168]，x64 | eclipse-temurin:25-jdk 容器（image ID `dcf835e52330`，digest `sha256:dcf835e52330939b6c9f90ecab8aafcbcaa8fbf48423db44de884cf978c10144`，JAVA_VERSION=jdk-25.0.4+7） |
| JAVA_HOME | 空 | `/opt/java/openjdk`（镜像内置 JDK 25） |
| PATH `java` | Zulu 8（1.8.0_502 / 8.96.0.19-CA-win64） | temurin 25.0.4+7 |
| Gradle daemon JVM | Oracle JDK `25.0.2`（`C:\Program Files\Java\jdk-25.0.2`，来源：**用户级** `~/.gradle/gradle.properties` 的 `org.gradle.java.home`，W0 落地项） | 单次 fork 的 daemon，JDK 25.0.4+7（`--no-daemon`） |
| Gradle | wrapper 9.6.0（缓存 `C:\Users\11515\.gradle`） | wrapper 9.6.0（容器本地 `/root/.gradle`，全新缓存，**未挂载 Windows 缓存**） |
| 插件 | Stonecutter 0.9.7、Fabric Loom 1.17.20、foojay resolver 1.0.0、MDG（moddev） | 同左（全新下载） |
| 本机已装 JDK | `jdk-25.0.2`、`graalvm-jdk-25`、`jdk-21.0.10`（另检出 Zulu 8/25、Temurin 8/17 auto-provisioned；**graalvm-jdk-25 未被 toolchain 检测发现**） | — |

Gradle 缓存位置：Windows `C:\Users\11515\.gradle`（本轮未清理，基线构建大量命中 UP-TO-DATE/FROM-CACHE 属预期并如实记录执行计数）；容器内 `/root/.gradle` 为本轮全新生成，用后随 `--rm` 丢弃。

## 2. revision 绑定

- 改动前 HEAD：`9f7021954fa61af0e312941c9c365b3a2f300620`（master，`ci(fabric): separate smoke artifacts from releases`）。
- W0 commit：**`8301dc144f763e2e09b1edd2d7c76a61e383df64`**（`build: unpin machine-local org.gradle.java.home, drop CI sed workaround (ticket 01 W0)`；仅 `NekoJS-mult/gradle.properties` + `NekoJS-mult/.github/workflows/ci-build.yml` 两个文件，+5/−23 行）。
- 基线构建 revision：worktree `D:/mcmodDemo/NekoJS/NekoJS-w0-baseline`（detached → ff 到 `8301dc14`）。
- **仓库结构说明**：git 仓库根为 `D:/mcmodDemo/NekoJS`，本工单项目是其中被跟踪的 `NekoJS-mult/` 子目录；worktree 中对应 `NekoJS-w0-baseline/NekoJS-mult`。
- 主仓库当时含用户未提交 WIP（编辑器移除 + 错误仪表盘专项，约 50 个 M/D/?? 文件）。**基线全部在不含 WIP 的隔离 worktree 采集**，满足与 02 号性能基线票并行的隔离要求：本票未触碰 02 的 checkout、caches 与 run 目录；如 02 需绑定同源 revision，建议绑定 **`14de611f`**（五节点冷构建可复现全绿的闭合 revision）。中间 revision 说明：`8301dc14` 仅含 W0（当时 26.1.2-fabric 冷构建仍失败，见 §7.1）；`9f702195` 为改动前状态（pin 存在，W0 失败证据采集自该状态）；`8301dc14` → `14de611f` 之间只有 fabric convention 修复，NeoForge 三 jar 逐字节不变（§7.1 修复后小节）。
- 第二、三个 commit（本报告与证据，`docs(baseline): ...`）只含文档，不改变源 revision 语义。

## 3. W0 归档与修复摘要

### 3.1 归档（改动前）

位置：`w0-config-archive-2026-09-12/`（README、`gradle.properties.before`、`failure-evidence.md`、`logs/`）。

- 旧配置关键行：`org.gradle.java.home=C:/Program Files/Java/jdk-25.0.2`（`gradle.properties:18`，上方 4 行中文注释说明动机）。
- 原启动命令：本机直接 `./gradlew`（pin 存在所以可用）；CI 侧反向 workaround：5 处 `Drop local org.gradle.java.home pin` 步骤用 `sed` 删行。
- 失败证据（真实 stderr，exit=1）：在隔离 worktree 临时删 pin 后、JAVA_HOME 空 + PATH java=Zulu 8 环境跑 `gradlew.bat help`：

  > `Gradle requires JVM 17 or later to run. Your build is currently configured to use JVM 8.`

  注意：`gradlew.bat --version` 在同一环境**不**触发失败（只打印 launcher 信息，exit=0），JVM 17+ 校验发生在执行任务时——证据以 `help` 为准（`failure-evidence.md` 有完整说明）。
- 回滚方式：`git revert` W0 commit；或手工回填 `gradle.properties.before` 第 14–18 行；本机一次性设置删除用户级文件中对应行即可。

### 3.2 修复

1. `gradle.properties`：删除 pin 行；原注释替换为：启动器/daemon JVM 需 17+（推荐 25）；本仓库不再钉本机路径；本机用户在**用户级** `C:\Users\11515\.gradle\gradle.properties` 设 `org.gradle.java.home` 或设 `JAVA_HOME`；CI 由 setup-java 提供；toolchain 由 foojay 供给、与此处无关。优先级口径（2026-09-12 code-review 更正）：**命令行 `-D` > 用户级 > 项目级**——用户级条目作用于该用户全部 Gradle 构建，项目需差异化时用命令行参数或 `gradle/daemon-jvm.properties`（daemon JVM criteria），项目级 `gradle.properties` **不能**覆盖用户级。
2. 用户级 `C:\Users\11515\.gradle\gradle.properties`（本机一次性设置，**不进 git**）写入 `org.gradle.java.home=C:/Program Files/Java/jdk-25.0.2`。验证（主仓库，JAVA_HOME 为空）：

   ```text
   Launcher JVM:  1.8.0_502 (Azul Systems, Inc. 25.502-b07)
   Daemon JVM:    C:\Program Files\Java\jdk-25.0.2 (from org.gradle.java.home)
   ```

   daemon 真实启动在 worktree 的 `./gradlew help` 上验证（BUILD SUCCESSFUL）。
3. `.github/workflows/ci-build.yml`：移除全部 5 处 `Drop local org.gradle.java.home pin` 步骤（含专属注释与 `shell: bash` 变体）。PyYAML 结构校验通过（`YAML OK`）。CI 侧不再需要任何对 `gradle.properties` 的临时改动。
4. 全仓 grep：被跟踪文件中 `org.gradle.java.home` 仅剩两处**注释性/历史性提及**（`gradle.properties` 新注释、仓库根 `wiki/构建系统.md` 的历史描述）；无任何赋值行（`^org.gradle.java.home=` 计数 0）。

## 4. Windows / Linux 配置阶段验证与平台差异

| 项 | Windows（worktree） | Linux（Docker，全新缓存） |
|---|---|---|
| 命令 | `./gradlew help --console=plain` + `./gradlew projects` | `./gradlew help --console=plain --no-daemon`（挂载 worktree，容器本地缓存） |
| 结果 | **BUILD SUCCESSFUL in 27s**（10 tasks: 4 executed, 6 from cache） | **BUILD SUCCESSFUL in 6m 57s**（10 tasks 全 executed，含 Gradle 发行版下载、buildSrc 编译、依赖下载） |
| 项目图 | 7 项目：root `NekoJS` + `:1.21.1` `:26.1.2` `:26.2.0` `:26.1.2-fabric` `:26.2.0-fabric` `:common` `:common-api-processor` | 同左（同一配置逻辑） |
| JVM 来源 | launcher=wrapper 客户端（Zulu 8），daemon=jdk-25.0.2（用户级配置） | launcher=daemon=temurin 25.0.4+7（JAVA_HOME，无需任何 gradle.properties） |
| stonecutter/loom | 0.9.7 / 1.17.20，`Parsed unknown MC version 26.1.2 / 26.2` 提示一致 | 同左 |
| git 依赖 | worktree `.git` 文件在上级（不在挂载目录内），配置阶段未探测 git，无错误 | 同左；容器内 `safe.directory` 预置未产生可见影响 |
| 告警面 | fabric 编译期 Graal `System::load` restricted-method warning（JDK 25 native access）；Gradle 10 deprecation 提示 | wrapper/native-platform `System::load` warning；**stonecutter `replace(Pair,Pair)` Kotlin deprecation ×6（Windows 热缓存未重现，属首次编译控制器脚本的告警，非平台固有差异）**；Gradle 10 deprecation 提示 |
| 路径/换行 | `D:\...` 反斜杠、problems-report `file:///D:/...`、`gradlew.bat` | `/ws` 正斜杠、problems-report `file:///ws/...`、`./gradlew`（bash）直接可用 |

结论：**两个平台配置阶段均通过**；配置行为与插件版本解析一致，差异集中在 JVM 供给方式（用户级配置 vs JAVA_HOME）、缓存冷热与路径表示。无"仅本机可运行"的因素残留（pin 已移除并由两种文档化机制替代）。

## 5. 五节点事实（汇总，实测）

完整口径见[实测 manifest](node-source-artifact-manifest-measured-2026-09-12.md)。要点：

| node | 支持/平台 | deps.java / 实际 toolchain | 构建结果 | 产物（SHA-256 前 16 位 / entries） | jar 内 metadata 实测 |
|---|---|---|---|---|---|
| `1.21.1` | NeoForge / experimental | 21 / Oracle jdk-21.0.10（registry，唯一 JDK 21，推断） | build ✓ check ✓ | 2,403,604 B / `fbd1bb9eefcd7731` / 1383 | mods.toml deps 3 块（neoforge/minecraft/graalmc）；mixin ×16；AT 78 条 |
| `26.1.2` | NeoForge / **primary** | 25 / jdk-25.0.2 | build ✓ check ✓ verifyDevModSourceSets ✓ | 2,517,347 B / `7d3ce6d2e3a435c5` / 1436 | 同上（AT 69 条；mixin 17+1） |
| `26.2.0` | NeoForge / secondary | 25 / jdk-25.0.2 | build ✓ check ✓ | 2,517,367 B / `ca41eab78ebe0b97` / 1436 | 同 26.1.2 |
| `26.1.2-fabric` | Fabric / experimental | 25 / jdk-25.0.2 | ~~✗ 冷构建失败~~ → **build ✓ check ✓**（2026-09-12 修复 `14de611f` 后复测，见 §7.1 修复后小节） | 17,397,691 B / `3060a2b5b1f6a692` / 7399 | fabric.mod.json 展开值实测（fabric-api>=0.155.2+26.1.2、fabricloader>=0.19.3、minecraft ~26.1.2、java>=25）；fabric mixin 29+13+1；无 AT/mods.toml/NeoForge 类 |
| `26.2.0-fabric` | Fabric / experimental | 25 / jdk-25.0.2 | **build ✓ check ✓**（verifyFabricRuntimeArtifact ✓；remapJar 任务在本 Loom 配置下不存在，`jar` 输出即最终产物） | 17,397,689 B / `9572bf56cdda062c` / 7399（最终 jar，逐字节同修复前） | fabric.mod.json 展开值实测（fabric-api>=0.159.0+26.2、minecraft ~26.2、java>=25）；fabric mixin 29+13+1；无 AT/mods.toml/NeoForge 类 |
| `:common` | 引擎 | 25 | check ✓ | 1,675,966 B / `db6f7e9eb3fe674f` / 969 | `nekojs/api-runtime.properties`（api.version=0.12.0） |

依赖解析版本（runtimeClasspath）：五节点 graal 均为 `curse.maven:graal-1504336:8762962`（fabric convention 的 8762963 重定向未在节点 runtimeClasspath 生效，因解析发生在 `:common`）；jei 7420587/7920926/8300448；fabric-loader 0.19.3；fabric-api 0.155.2+26.1.2 / 0.159.0+26.2；icu4j 77.1 / 78.3；night-config 3.8.0/3.8.3/3.9.0（neo）、3.8.3（fabric）。

> **2026-09-12 更正**：上面 graal 一句对 fabric 两节点不成立——归档日志与修复后复测（`2026-09-12-fabric-build-fix/logs/win-fix-deps-*-fabric-runtimeClasspath.log`）均显示 fabric 节点 runtimeClasspath 解析为 `curse.maven:graal-1504336:8762962 -> 8762963`，convention 重定向**生效**（`8762962` 是 requested 版本，`-> 8762963` 是 resolutionStrategy 的最终解析结果，当初误读了箭头记法）。NeoForge 三节点仍为 8762962（无重定向）。**实际 Graal 版本（AC2 要求逐节点记录）**：两个 curse 文件 id 同为 **GraalMC 25.1.3.7** 的不同 loader 构建（NeoForge=8762962、fabric=8762963，版本映射见 `gradle/libs.versions.toml` graal 条目注释），五节点一致。

源/资源/mixin/processor/capability 口径：见实测 manifest §1–§5（手写版计数全部复验一致；新增 jar 内 AT 差异 13 条、mods.toml graalmc 依赖块、processor 零生成物等实测项）。

## 6. 测试发现/执行汇总与 golden 只读清单

| 范围 | suites | tests | skipped | failed |
|---|---:|---:|---:|---:|
| `:common` | 177 | 1336 | 2（`TypeScriptNoopIrGoldenTest` ×2：`RecipeEventJS not on test classpath` assume） | 0 |
| `:common-api-processor` | 1 | 13 | 0 | 0 |
| `:1.21.1` test | 17 | 58 | 0 | 0 |
| `:26.1.2` / `:26.2.0` test | 29 / 29 | 137 / 137 | 34 / 34（6 suites：ItemModificationComponentsTest×15、IngredientActionRegistryTest×6、BlockModificationEventJSTest×6、RegistryAutoAdapterScannerTest×4、BindingHelpersTest×2、BuilderTagTest×1；原因：裸 JVM 无 FML/vanilla registry 的 `TestAbortedException` assume） | 0 |
| nbtSmokeTest ×3 节点 | 1×3 | 8×3 | 0 | 0 |
| guardLint | — | — | — | 0 问题（守卫块 252 / 332 文件 / 豁免 0 / 警告 0） |
| fabric 节点 test（2026-09-12 修复后实测，两节点相同） | 8 | 37 | 6（`BindingHelpersTest`×2 + `RegistryAutoAdapterScannerTest`×4，与 NeoForge 26.x 同源的裸 JVM assume） | 0 |

**golden/契约基线只读清单**（本轮零修改）：

- `common/src/test/resources/nekojs/golden/api-manifest-core.json`
- `common/src/test/resources/nekojs/probe/legacy-bindings.expected.d.ts`、`legacy-events.expected.d.ts`、`probe/legacy-tree/**`
- `versions/{1.21.1,26.1.2,26.2.0}/src/test/resources/golden/block-events-api.txt`
- 本轮所有测试运行未触发 golden regenerate-abort 语义，普通验证未更新任何基线文件。

## 7. 构建失败证据与诊断（如实记录，未放宽验收）

### 7.1 `:26.1.2-fabric:processResources` 冷构建确定性失败

- 现象：五节点 `build` 命令在 `:26.1.2-fabric:processResources` 失败：

  > `Entry nekojs-fabric-dynamic.mixins.json is a duplicate but no duplicate handling strategy has been set.`

  干净复现（`cleanProcessResources` + `processResources`）exit=1；共 3 次执行均失败（日志 `win-build-4-five-node-build.log`、`win-build-4b-fabric-processResources-repro.log`）。
- 根因（init 脚本 `eachFile` 跟踪，证据 `logs/win-fabric-duplicate-copyspec-trace.log`：4 个文件各出现两次、FROM 路径完全相同）：同一目录 `versions/26.1.2-fabric/src/main/resources` 的 4 个文件被两次作为拷贝源——stonecutter 对版本化节点自动挂载节点本地资源，fabric convention 又按 `deps.fabric_source_node` 注入一次；`srcDirs`（Set）按路径去重掩盖了执行层两个独立 `from()`（最终 srcDirs 实测见 `logs/win-fabric-srcdirs-final.log`）。`26.2.0-fabric` 不触发（其节点目录无 `src/main/resources`，借用 root 只注入一次）。
- 与用户现状的关系：主仓库最后完整 fabric jar 为 2026-09-07；主仓库 `versions/26.1.2-fabric/build/resources/main`（mtime 2026-09-11 00:13）只留有"第一组 4 个文件、无 fabric.mod.json"的部分输出，与一次失败执行的残留一致——**该失败在用户机器上已发生但被增量状态掩盖**。CI（Linux 干净 runner）在本 revision 上预期同样失败；本票无 push/gh 权限，未远程核实 CI 状态（not-verified，owner zcode-agent/follow-up）。
- 处置：属"Fabric 源所有权"类差异（见 §8），修复归后续票 31/32；本票不改构建行为、不放宽命令。

#### 2026-09-12 修复后（follow-up 验证）

- 修复 commit：**`14de611f`**（`fix(fabric): skip self-referential source-node injection in fabric-node convention`）——fabric convention 的源根注入仅在 `fabric_source_node != project.name`（bridge 节点 26.2.0-fabric → 26.1.2-fabric）时进行；自源节点 26.1.2-fabric 依赖 stonecutter 挂载。属最小接线修复，未设 `duplicatesStrategy`、未放宽命令；before/after 代码摘录见 `2026-09-12-fabric-build-fix/README.md`。
- **冷构建全绿**：隔离 worktree（detached @ `14de611f`，不含用户 WIP）按 §11 命令序列复测，五节点 `build` BUILD SUCCESSFUL，`:26.1.2-fabric:processResources` 冷执行成功（修复前 3 次全败）；`:common:check`、`:common-api-processor:test`、`guardLint`、三节点 `nbtSmokeTest` 全部通过。
- **eachFile 单次拷贝证据**：`cleanProcessResources` + `processResources` 配 init 脚本 `eachFile` 跟踪（证据 `2026-09-12-fabric-build-fix/logs/win-fix-fabric-copyspec-trace.log` + `trace-copyspec.init.gradle`），两 fabric 节点的 4 个 mixin/accesswidener 文件各出现**一次**（修复前各两次、FROM 路径相同），`fabric.mod.json` 来自 modMetadata 输出——duplicate 消失的直接证明。
- **NeoForge 三 jar 无回归**：`:1.21.1` / `:26.1.2` / `:26.2.0` 产物 SHA-256 与本基线记录**逐字节一致**（完整 hash 见 `2026-09-12-fabric-build-fix/README.md`：`fbd1bb9eefcd7731…`、`7d3ce6d2e3a435c5…`、`ca41eab78ebe0b97…`）——修复只触碰 fabric-node convention，NeoForge 构建行为零变化。
- fabric 两节点补采事实（产物 hash/entries、metadata、mixin、test 8/37/6/0、verifyFabricRuntimeArtifact ✓、remapJar 任务不存在、Graal 通道结论）已并入 §5/§6、§10 与实测 manifest。
- 31 号票的"Fabric 源根所有权迁移"（convention/stonecutter 二选一收口）可在本修复基础上重做，本修复不构成约束。
- **边界决策留痕**（2026-09-12，code-review 要求）：`14de611f` 触碰了 buildSrc convention，超出 W0 的物理落点（settings.gradle.kts、stonecutter.gradle.kts、节点 properties），且与 §7.1 原处置"修复归 31/32、本票不改构建行为"相反——同日反转的理由：五节点冷构建 100% 失败使 01 的 What to build（"生成五节点实际源/产物、测试发现……可复现基线"）不可交付，而 31 号票被 01 阻塞，等待即死锁；修复属最小接线（6 行 + 注释），不设 duplicatesStrategy、未放宽命令，NeoForge 三 jar 与 26.2.0-fabric jar 逐字节不变，不触数据、功能或发布边界。决策人：zcode-agent（工单 01 执行授权内）；回滚方式：`git revert 14de611f`（fabric 节点将回到冷构建失败状态）。

### 7.2 运行时 smoke

`runServer` / `runGameTestServer` 未运行——运行时 smoke 不属于本票范围，属 34 号票（P4 五节点整体验证）；CI 的 gametest-smoke job 接线未改动。

## 8. 对后续迁移有影响的差异（按工单要求四类，附可执行处置项）

### 8.1 Fabric 源所有权

- **实测事实**：① 26.1.2-fabric 的节点本地资源被两套机制注入（§7.1；**已由 `14de611f` 修复**，见 §7.1 修复后小节）；② `26.2.0-fabric` 无自有源，`deps.fabric_source_node=26.1.2-fabric` 借用 root（srcDirs 实测确认）；③ 共享树的 `resources-legacy/-modern/templates` 对 fabric 节点生成 stonecutter 副本但**不被任何 sourceSet 挂载**（只挂载含 AT 的 `main/resources` 且被 exclude）——冗余生成目录；④ ~~Graal 8762963 重定向在节点 runtimeClasspath 不生效~~（**2026-09-12 更正**：fabric 节点 runtimeClasspath 实际解析为 `8762962 -> 8762963`，重定向生效——见 §5 更正注）。
- **处置项（owner：后续票 31/32）**：~~为 fabric 节点声明资源根单一所有权（convention 或 stonecutter 二选一），或显式设置 `processResources.duplicatesStrategy`~~——**重复注入已由 `14de611f` 以最小接线修复解决**（自源节点跳过重复注入；证据 `2026-09-12-fabric-build-fix/`）；源根所有权的整体收口（26.2.0-fabric 的 source bridge 收口）仍归 31 号票，可在该修复基础上重做；~~核对 Graal fabric 构建（8762963）实际进入最终 jar 的通道~~——**已闭环**（2026-09-12 实测：fabric 最终 jar 不携带 Graal 类，dev classpath 解析 8762963；`fabric.mod.json` depends 未声明 graal，生产环境供给方式是否需要显式声明归 31/32 评估）；清理或显式化"生成但不挂载"的 fabric 资源目录（保留给 31/32）。

### 8.2 CI 用途子集

- **实测事实**：W0 后 CI 不再 sed 改 `gradle.properties`（5 步移除，YAML 校验通过）；CI 全量构建在干净 runner 上会命中 §7.1 的 fabric 失败（干跑推定，未远程验证）；CI `common-recheck`（JDK 21）与 Windows 复跑 job 保留。
- **处置项（owner：后续票 33）**：33 号票接手"CI 用途子集与 Fabric processor 延期替代 gate"时，必须先决定 fabric processResources 失败的归属与修复（8.1），否则五节点 build gate 无法在 CI 转绿；gametest-smoke job 维持 34 号票验证范围。

### 8.3 契约覆盖

- **实测事实**：golden 全部只读（§6 清单），普通验证未更新基线；`SpecCoverageProcessor` 为纯校验、零生成物；**fabric 两节点未接 `:common-api-processor`、不传 `-Anekojs.platform`**（convention 注释明示"接线是待办"），即 spec 覆盖在 fabric 侧无编译期门禁；`ExtensionCoverageTest` 等 spec/契约测试在共享测试树中只在 NeoForge 节点执行（fabric test 未运行）。
- **处置项（owner：后续票 33/34）**：fabric processor 接线或显式延期替代 gate（33 号票明确允许延期，但需落 gate）；P4（34 号票）把 fabric spec 覆盖纳入能力矩阵核对。

### 8.4 数据保护

- **实测事实**：本票未触碰任何用户数据/脚本/持久化文件；全部构建在隔离 worktree（不含用户 WIP）中执行，构建产物与日志均落盘到本目录，未进入性能采样输入；主仓库用户 WIP 的 9 个保护文件与 AGENTS.md/README.md/CONTEXT.md 未被修改或暂存；`build/`、`.gradle/` 等构建状态可复现、非用户数据。
- **处置项**：无新增义务。对 03 号票（数据保护基线）的提示：本票的 worktree 隔离模式（worktree + 独立缓存）可作为其取证模板；02 号票取性能输入时不得复用本票的 `w0-config-archive-2026-09-12/logs/`（缓存目录、构建日志不作为性能输入）。

## 9. 性能采样声明

**性能采样不属于 W0**：本轮未做任何计时/采样/阈值冻结；性能基线由独立的 P0 票 [02: P0 独立性能基线](../implementation-tickets/02-perf-baseline.md) 承担，阈值在 P4 前依据该基线决定。本票只保证：与 02 并行时隔离 checkout/caches/run 目录，且构建产物与日志不泄漏进性能采样输入（§2、§8.4）。

## 10. not-verified / deferred 清单（每项独立证据状态 + owner）

| # | 项 | 状态 | 证据 | owner |
|---|---|---|---|---|
| 1 | Linux 仅验证**配置阶段**，未做 Linux 全量构建 | **scope 内完成**（工单验收只要求配置阶段），非 deferred | `logs/linux-docker-config-help.log`（BUILD SUCCESSFUL in 6m57s） | — |
| 2 | `:26.1.2-fabric:build` 冷构建失败（未通过、未放宽） | **已解决**（2026-09-12，修复 commit `14de611f`：冷构建全绿，未放宽任何命令） | §7.1 修复后小节；`2026-09-12-fabric-build-fix/logs/win-fixbuild-4-five-node-build.log` + eachFile 跟踪 | —（31 号票源根所有权收口仍待做，不受本修复约束） |
| 3 | fabric 两节点 `test` / `verifyFabricRuntimeArtifact` / `remapJar` 未执行（随 §7.1 中止） | **已补采**（2026-09-12 修复后）：test 8/37/6/0 两节点相同；verifyFabricRuntimeArtifact ✓、check ✓；remapJar 在本 Loom 配置下**不存在**（`jar` 即最终产物） | `2026-09-12-fabric-build-fix/logs/`（test-results、copyspec-trace、remapjar-notfound、jar-facts-output） | —（baseline 中"remap 前中间 jar"表述已在实测 manifest 更正） |
| 4 | 1.21.1 编译 toolchain 具体路径为推断（唯一 JDK 21） | **not-verified（低风险）** | `win-java-toolchains.log` 检测列表 | zcode-agent（可用 `--info` 复核） |
| 5 | CI 远程（push 后 Linux 干净 runner）未验证 | **not-verified**（本票无 push/gh 权限，红线 1） | 本地 PyYAML 校验 + grep 记录 | 维护者（push 后由 CI 本身验证） |
| 6 | `runServer`/`runGameTestServer` 运行时 smoke | **范围外**（34 号票） | §7.2 | 34 号票 |
| 7 | 性能采样 | **范围外**（02 号票） | §9 | 02 号票 |
| 8 | `wiki/构建系统.md` 仍描述已移除的 CI sed workaround | **已解决**（2026-09-12：CI 段落改为"CI 由 setup-java 提供，仓库不再钉 org.gradle.java.home"）；AGENTS.md/README.md/CONTEXT.md 无需修改（本轮无相关内容） | `NekoJS-mult/wiki/构建系统.md` CI 节 | — |
| 9 | Graal fabric 构建（8762963）进入最终 jar 的实际通道 | **已闭环**（2026-09-12 实测）：fabric 最终 jar **不含** Graal 类（`org/graalvm/**`、`com/oracle/truffle/**` 均 0 条，fat-jar 装配显式过滤 graal-1504336）；dev/runtime classpath 解析 `8762962 -> 8762963`（convention 重定向生效，§5 基线结论系误读，已更正）；jar 携带 icu4j 提取类 5,762 条 + night-config 3.8.3 272 条；`fabric.mod.json` depends 未声明 graal | `2026-09-12-fabric-build-fix/logs/`（jar-facts-output.txt、deps 两日志） | —（是否在 fabric.mod.json 显式声明 graal 依赖归 31/32 评估） |

## 11. 复现方式（从零复现本基线的完整命令序列）

```bash
# 0) 前置：Windows x64，PATH java 可为任意（W0 后不再依赖）；本机需 JDK 17+ 可被发现
#    （本机现用：用户级 C:\Users\11515\.gradle\gradle.properties 的 org.gradle.java.home=jdk-25.0.2）

# 1) 隔离 worktree（绑定 revision 14de611f——五节点可复现全绿的闭合 revision，不含任何工作区 WIP。
#    若要复现修复前的失败基线，改绑 8301dc14：五节点 build 将在 :26.1.2-fabric:processResources 失败，见 §7.1）
cd D:/mcmodDemo/NekoJS/NekoJS-mult
git worktree add ../NekoJS-w0-baseline 14de611f

# 2) Windows 配置阶段
cd D:/mcmodDemo/NekoJS/NekoJS-w0-baseline/NekoJS-mult
./gradlew help --console=plain          # 配置 7 项目
./gradlew projects --console=plain      # 节点图
./gradlew :common:javaToolchains --console=plain

# 3) 标准命令（与 CI 相同）
./gradlew :common:check :common-api-processor:test --console=plain
./gradlew guardLint --console=plain
./gradlew :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest --console=plain
./gradlew :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
# 预期（@14de611f）：四条全部 SUCCESSFUL（2026-09-12 修复后实测；Windows 缓存温热时部分任务
# FROM-CACHE/UP-TO-DATE 属预期并如实记录）

# 4) Linux 配置阶段（Docker，全新缓存）
docker pull eclipse-temurin:25-jdk
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "D:/mcmodDemo/NekoJS/NekoJS-w0-baseline/NekoJS-mult:/ws" -w /ws \
  eclipse-temurin:25-jdk bash -lc "git config --global --add safe.directory /ws 2>/dev/null; ./gradlew help --console=plain --no-daemon"

# 5) 事实采集（依赖树/工具链/jar 解析）与收尾
#    ./gradlew :<node>:dependencies --configuration runtimeClasspath --console=plain
#    jar SHA-256/entries/mixin/metadata 解析脚本见 logs/ 记录
git -C D:/mcmodDemo/NekoJS/NekoJS-w0-baseline merge --ff-only master   # 期间 master 如有推进
git worktree remove --force ../NekoJS-w0-baseline && git worktree prune
```

证据文件索引：`w0-config-archive-2026-09-12/logs/`（19 个日志/记录文件）、[实测 manifest](node-source-artifact-manifest-measured-2026-09-12.md)、本报告、[fabric 修复验证目录](2026-09-12-fabric-build-fix/README.md)（2026-09-12 follow-up：冷构建复测日志、eachFile 跟踪、jar 解析脚本与输出）。
