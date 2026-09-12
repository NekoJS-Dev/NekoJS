# W0 配置归档（2026-09-12）：移除本机 `org.gradle.java.home` pin 之前的状态

本目录是工单 [01: P0 五节点构建与契约基线](../../implementation-tickets/01-build-baseline.md) W0 工作项的改动前归档：在删除被跟踪的本机 Gradle/JDK 配置**之前**，记录旧配置内容、来源 revision、原启动命令、失败输出和缓存/工具链状态，使清理动作可审计、可回滚。

## 1. 归档时间与来源 revision

- 归档日期：2026-09-12
- 改动前 HEAD：`9f7021954fa61af0e312941c9c365b3a2f300620`（master，commit 标题 `ci(fabric): separate smoke artifacts from releases`）
- 仓库结构说明：git 仓库根为 `D:/mcmodDemo/NekoJS`（内含原版单版本项目的跟踪文件）；本工单操作的多版本项目是同仓库内被跟踪的 `NekoJS-mult/` 子目录。本文所称"主仓库"均指 `NekoJS-mult/` 项目。
- 主仓库当时含大量用户未提交 WIP（编辑器移除 + 错误仪表盘专项，约 50 个 M/D/?? 文件）；W0 改动与其隔离，未触碰任何 WIP 文件。

## 2. 改动前环境

| 项 | 值 |
|---|---|
| 操作系统 | Microsoft Windows [版本 10.0.26200.9168]（win32 10.0.26200 x64） |
| shell | Git Bash |
| `JAVA_HOME` | 空（未设置） |
| PATH 上的 `java` | Zulu 8：`openjdk version "1.8.0_502"`，`Zulu 8.96.0.19-CA-win64` |
| 本机已装 JDK | `C:/Program Files/Java/jdk-25.0.2`、`C:/Program Files/Java/graalvm-jdk-25`、`C:/Program Files/Java/jdk-21.0.10` |
| Gradle wrapper | 9.6.0（`gradle/wrapper/gradle-wrapper.properties` distributionUrl） |

PATH 上是 Zulu 8 正是当年加 pin 的动机：Gradle 9 需要 JVM 17+ 才能运行，直接 `./gradlew` 会以
`Gradle requires JVM 17 or later to run` 失败（复现见 [failure-evidence.md](failure-evidence.md)）。

## 3. 旧配置内容（`gradle.properties.before`）

完整副本见 [gradle.properties.before](gradle.properties.before)。关键行（第 14–18 行）：

```properties
# 启动器 JVM 固定到 JDK 25：本机 PATH 上的 `java` 是 Zulu 8，直接跑 ./gradlew 会被
# "Gradle requires JVM 17 or later" 挡住，每条命令都得先 export JAVA_HOME 很烦。
# toolchain 只管编译用的 JDK，管不到启动器，所以这里显式钉住。
# （换机器时改这一行；CI 上应删掉本行并由 setup-java 提供）
org.gradle.java.home=C:/Program Files/Java/jdk-25.0.2
```

来源：该 pin 是本机 Windows 机器的一次性手工配置（把启动器 JVM 钉到本机绝对路径 `C:/Program Files/Java/jdk-25.0.2`），随仓库被跟踪提交，导致换机器/CI 必须各自 workaround。

## 4. 原启动命令

pin 存在时，本机直接可用（无需 `export JAVA_HOME`）：

```bash
./gradlew help --console=plain
./gradlew :common:check :common-api-processor:test
./gradlew guardLint
./gradlew :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest
./gradlew :1.21.1:build :26.1.2:build :26.1.2-fabric:build :26.2.0:build :26.2.0-fabric:build
```

CI 侧（`.github/workflows/ci-build.yml`）则相反：setup-java 提供了 JDK，但被跟踪的 pin 指向不存在的本机路径，所以 CI 有 5 处 `Drop local org.gradle.java.home pin` 步骤（约 116、245、394、613、660 行）用 `sed -i '/^org.gradle.java.home=/d' gradle.properties` 临时删除该行——同一份配置在两类环境各需一种对抗性 workaround。

## 5. 缓存 / 工具链状态（改动前）

- `~/.gradle`（`C:\Users\11515\.gradle`）已存在，含 `caches`、`daemon`、`jdks`、`wrapper`、`native`、`workers`、`kotlin-profile`、`notifications`、`android` 等目录——本机有大量历史构建缓存。
- toolchain 供给不依赖 pin：`settings.gradle.kts` 应用了 `org.gradle.toolchains.foojay-resolver-convention` 1.0.0，编译用 toolchain（deps.java=21/25）由 foojay resolver 自动探测/下载；pin 只影响 Gradle **启动器/daemon** JVM。
- Gradle daemon 缓存（`~/.gradle/daemon`）与构建缓存（`~/.gradle/caches`）在改动前后均保持不变；本 W0 不清理任何缓存。

## 6. 失败输出

见 [failure-evidence.md](failure-evidence.md)：在隔离 worktree（`../NekoJS-w0-baseline`，detached HEAD = 上述 revision，不含用户 WIP）中临时删除 pin 行后，于 JAVA_HOME 为空、PATH java=Zulu 8 的环境运行 `gradlew.bat help --console=plain` 的真实输出（`Gradle requires JVM 17 or later to run. Your build is currently configured to use JVM 8.`，exit=1）。验证后已用 `git checkout -- NekoJS-mult/gradle.properties` 恢复 worktree 内该文件。

## 7. 回滚方式

- 恢复被跟踪配置：`git revert` W0 commit，或把 `gradle.properties.before` 第 14–18 行内容手工加回 `gradle.properties`。
- 恢复 CI workaround：同上 revert 即可（5 处步骤在同一 commit 中移除）。
- 本机一次性设置（不进 git）：用户级 `C:\Users\11515\.gradle\gradle.properties` 写入 `org.gradle.java.home=C:/Program Files/Java/jdk-25.0.2`——删除该文件中对应行即可回退本机行为。
