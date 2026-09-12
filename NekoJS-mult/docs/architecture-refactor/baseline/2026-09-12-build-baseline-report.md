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
- 主仓库当时含用户未提交 WIP（编辑器移除 + 错误仪表盘专项，约 50 个 M/D/?? 文件）。**基线全部在不含 WIP 的隔离 worktree 采集**，满足与 02 号性能基线票并行的隔离要求：本票未触碰 02 的 checkout、caches 与 run 目录；如 02 需绑定同源 revision，建议绑定 **`8301dc14`**（或其父 `9f702195`——W0 只改 `gradle.properties` 注释区与 CI 步骤，不含任何构建行为差异；W0 的失败证据即来自 `9f702195` 状态）。
- 第二个 commit（本报告与证据，`docs(baseline): ...`）只含文档，不改变源 revision 语义。

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

1. `gradle.properties`：删除 pin 行；原注释替换为：启动器/daemon JVM 需 17+（推荐 25）；本仓库不再钉本机路径；本机用户在**用户级** `C:\Users\11515\.gradle\gradle.properties` 设 `org.gradle.java.home`（项目级属性优先级更高，可被个别项目覆盖）或设 `JAVA_HOME`；CI 由 setup-java 提供；toolchain 由 foojay 供给、与此处无关。
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
| `26.1.2-fabric` | Fabric / experimental | 25 / jdk-25.0.2 | **✗ processResources 冷构建失败**（§7.1） | 无产物 | — |
| `26.2.0-fabric` | Fabric / experimental | 25 / jdk-25.0.2 | jar ✓（remapJar/check **未到达**） | 17,397,689 B / `9572bf56cdda062c` / 7399（remap 前中间 jar） | fabric.mod.json 展开值实测（fabric-api>=0.159.0+26.2、minecraft ~26.2、java>=25）；fabric mixin 29+13+1 |
| `:common` | 引擎 | 25 | check ✓ | 1,675,966 B / `db6f7e9eb3fe674f` / 969 | `nekojs/api-runtime.properties`（api.version=0.12.0） |

依赖解析版本（runtimeClasspath）：五节点 graal 均为 `curse.maven:graal-1504336:8762962`（fabric convention 的 8762963 重定向未在节点 runtimeClasspath 生效，因解析发生在 `:common`）；jei 7420587/7920926/8300448；fabric-loader 0.19.3；fabric-api 0.155.2+26.1.2 / 0.159.0+26.2；icu4j 77.1 / 78.3；night-config 3.8.0/3.8.3/3.9.0（neo）、3.8.3（fabric）。

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
| fabric 节点 test | — | — | — | **未运行**（构建中止，见 §7.1；`failOnNoDiscoveredTests=true` 行为未验证） |

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

### 7.2 运行时 smoke

`runServer` / `runGameTestServer` 未运行——运行时 smoke 不属于本票范围，属 34 号票（P4 五节点整体验证）；CI 的 gametest-smoke job 接线未改动。

## 8. 对后续迁移有影响的差异（按工单要求四类，附可执行处置项）

### 8.1 Fabric 源所有权

- **实测事实**：① 26.1.2-fabric 的节点本地资源被两套机制注入（§7.1）；② `26.2.0-fabric` 无自有源，`deps.fabric_source_node=26.1.2-fabric` 借用 root（srcDirs 实测确认）；③ 共享树的 `resources-legacy/-modern/templates` 对 fabric 节点生成 stonecutter 副本但**不被任何 sourceSet 挂载**（只挂载含 AT 的 `main/resources` 且被 exclude）——冗余生成目录；④ Graal 8762963 重定向在节点 runtimeClasspath 不生效（`:common` 内解析为 8762962）。
- **处置项（owner：后续票 31/32）**：为 fabric 节点声明资源根单一所有权（convention 或 stonecutter 二选一），或显式设置 `processResources.duplicatesStrategy`；把 26.2.0-fabric 的 source bridge 收口（31 号票范围）；核对 Graal fabric 构建（8762963）实际进入最终 jar 的通道；清理或显式化"生成但不挂载"的 fabric 资源目录。

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
| 2 | `:26.1.2-fabric:build` 冷构建失败（未通过、未放宽） | **failed，已诊断** | §7.1；3 次失败日志 + eachFile 跟踪 | 后续票 31/32（构建修复）；本轮 follow-up 记录 owner：zcode-agent |
| 3 | fabric 两节点 `test` / `verifyFabricRuntimeArtifact` / `remapJar` 未执行（随 §7.1 中止） | **not-verified** | 构建日志任务列表 | zcode-agent（失败修复后按 §10 复现序列补采） |
| 4 | 1.21.1 编译 toolchain 具体路径为推断（唯一 JDK 21） | **not-verified（低风险）** | `win-java-toolchains.log` 检测列表 | zcode-agent（可用 `--info` 复核） |
| 5 | CI 远程（push 后 Linux 干净 runner）未验证 | **not-verified**（本票无 push/gh 权限，红线 1） | 本地 PyYAML 校验 + grep 记录 | 维护者（push 后由 CI 本身验证） |
| 6 | `runServer`/`runGameTestServer` 运行时 smoke | **范围外**（34 号票） | §7.2 | 34 号票 |
| 7 | 性能采样 | **范围外**（02 号票） | §9 | 02 号票 |
| 8 | `wiki/构建系统.md`（仓库根共享文档）仍描述已移除的 CI sed workaround；AGENTS.md/README.md/CONTEXT.md 有用户未提交改动不能改 | **follow-up 文档修改** | §3.4 grep 记录 | zcode-agent（WIP 合并后）或维护者 |
| 9 | Graal fabric 构建（8762963）进入最终 jar 的实际通道 | **not-verified** | 实测 manifest §0 | 后续票 31/32 |

## 11. 复现方式（从零复现本基线的完整命令序列）

```bash
# 0) 前置：Windows x64，PATH java 可为任意（W0 后不再依赖）；本机需 JDK 17+ 可被发现
#    （本机现用：用户级 C:\Users\11515\.gradle\gradle.properties 的 org.gradle.java.home=jdk-25.0.2）

# 1) 隔离 worktree（绑定 revision 8301dc14，不含任何工作区 WIP）
cd D:/mcmodDemo/NekoJS/NekoJS-mult
git worktree add ../NekoJS-w0-baseline 8301dc14

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
# 预期：前三条 SUCCESSFUL；五节点 build 在 :26.1.2-fabric:processResources 失败（§7.1）

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

证据文件索引：`w0-config-archive-2026-09-12/logs/`（13 个日志/记录文件）、[实测 manifest](node-source-artifact-manifest-measured-2026-09-12.md)、本报告。
