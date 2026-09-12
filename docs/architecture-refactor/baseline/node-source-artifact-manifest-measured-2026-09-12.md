# W0：五节点 source/artifact manifest（实测快照，2026-09-12）

> 生成日期：2026-09-12
>
> 状态：**实测（measured）快照**。与 2026-09-08 的[手写静态清单](node-source-artifact-manifest.md)逐节对齐，便于 diff。本文件全部事实来自 2026-09-12 在隔离 worktree（`NekoJS-w0-baseline`，revision `8301dc14` = `9f702195` + W0 commit）实际执行的 Gradle 构建、Gradle 报告任务（`projects`/`javaToolchains`/`dependencies`）与 jar 内容解析；不抄写文档。原始日志与脚本输出见 `w0-config-archive-2026-09-12/logs/`。
>
> 执行环境：Windows 10.0.26200 x64，daemon JVM = Oracle JDK 25.0.2（用户级 `org.gradle.java.home`，W0 修复后），Gradle wrapper 9.6.0，Stonecutter 0.9.7，Fabric Loom 1.17.20，foojay resolver 1.0.0。基线构建结果：**26.1.2-fabric:processResources 冷构建确定性失败**（见 §11，属基线事实而非本清单缺陷）。

## 0. 节点与版本事实（实测）

| node | loader | loader/neo 版本 | deps.java | runtimeClasspath 关键解析版本 |
|---|---|---|---|---|
| `1.21.1` | NeoForge | deps.neo=21.1.227（MDG 解析） | 21 | graal-1504336:**8762962**；jei-238222:7420587；night-config 3.8.0/3.8.3 |
| `26.1.2` | NeoForge | deps.neo=26.1.2.71 | 25 | graal-1504336:8762962；jei-238222:7920926；night-config 3.8.0/3.8.3 |
| `26.2.0` | NeoForge | deps.neo=26.2.0.57 | 25 | graal-1504336:8762962；jei-238222:8300448；night-config 3.8.0/3.8.3/3.9.0 |
| `26.1.2-fabric` | Fabric | fabric-loader:**0.19.3** | 25 | fabric-api:**0.155.2+26.1.2**；graal-1504336:8762962；icu4j:77.1；night-config 3.8.3 |
| `26.2.0-fabric` | Fabric | fabric-loader:0.19.3 | 25 | fabric-api:**0.159.0+26.2**；graal-1504336:8762962；icu4j:**78.3**；night-config 3.8.3 |

- 解析版本来源：`./gradlew :<node>:dependencies --configuration runtimeClasspath`（日志 `deps-<node>-runtimeClasspath.log`）。
- **Graal**：五节点 runtimeClasspath 均解析为 `curse.maven:graal-1504336:8762962`。fabric convention 的 `useVersion("8762963")` 重定向（`nekojs.fabric-node.gradle.kts:59-65`）在节点 runtimeClasspath 上**未生效**——该依赖经 `:common` 的 runtimeElements 传递，解析发生在 `:common` 的 configuration 内。实际参与 fabric 装配的 Graal 版本口径需 31/32 号票确认。
- **JVM facts**：daemon JVM = `C:\Program Files\Java\jdk-25.0.2`（来源：用户级 `~/.gradle/gradle.properties` 的 `org.gradle.java.home`，`--version` 输出 `Daemon JVM: ... (from org.gradle.java.home)`）；wrapper launcher JVM = Zulu 8（1.8.0_502，仅 wrapper 客户端）。`javaToolchains` 检测到 6 个 JVM：Zulu 8（registry）、Temurin 8（auto-provisioned）、Temurin 17（auto-provisioned）、Oracle JDK 21.0.10（registry）、Zulu 25（registry）、Oracle JDK 25.0.2（registry + Current JVM）。**本机安装的 `C:/Program Files/Java/graalvm-jdk-25` 未被 toolchain 自动检测发现**。1.21.1 的 deps.java=21 对应唯一 registry 检测的 21 = Oracle jdk-21.0.10（按检测列表推断，未逐任务打印 toolchain 路径）。

## 1. 五节点与节点本地文件计数（实测，与手写版一致）

| node | main `.java` | main resources | main templates | test `.java` | test resources |
|---|---:|---:|---:|---:|---:|
| `26.1.2` | 3 | 3 | 0 | 0 | 1 |
| `26.2.0` | 3 | 3 | 0 | 0 | 1 |
| `1.21.1` | 59 | 2 | 0 | 0 | 1 |
| `26.1.2-fabric` | 61 | 4 | 1 | 0 | 2 |
| `26.2.0-fabric` | 0（无自有 src） | 0 | 0 | 0 | 0 |

## 2. 共享 source/resource roots（实测，与手写版一致）

| root | 文件数 | 实测消费者 |
|---|---:|---|
| `src/main/java` | 301 `.java` | 全节点（active 26.1.2 直接挂原始目录；其余节点挂 `build/generated/stonecutter/main/java` 副本 + 节点本地原始目录） |
| `src/main/resources` | 1（`META-INF/accesstransformer.cfg`） | NeoForge：AT 经 `stonecutterProcessed` 消费；jar 内实测：26.x=69 条、1.21.1=78 条；Fabric：挂载后被 `processResources` exclude |
| `src/main/resources-legacy` | 1（`nekojs.mixins.json`） | 仅 1.21.1（原始目录挂载，实测 srcDirs 确认） |
| `src/main/resources-modern` | 4（`nekojs.mixins.json`、`nekojs-dynamic.mixins.json`、`nekojs.interface_injection.json`、`pack.mcmeta`） | 26.x NeoForge（原始目录挂载） |
| `src/main/templates` | 1（`META-INF/neoforge.mods.toml`） | NeoForge `generateModMetadata`（经 `stonecutterProcessed`） |
| `src/test/java` | 31 `.java`（27 个带整文件守卫） | 共享测试树，经 stonecutter 预处理 |
| `common/src/main/java` | 507 `.java` | `:common`（fat-jar 内嵌进各节点） |
| `common/src/main/resources` | 20 | `:common` |
| `common/src/main/templates` | 1（**实际路径 `common/src/main/templates/nekojs/api-runtime.properties`**，手写版未写 `nekojs/` 子目录） | `:common` ProcessResources；jar 内实测 `nekojs/api-runtime.properties`（api.version=0.12.0） |
| `common/src/test/java` | 182 `.java` | `:common` test |
| `common/src/test/resources` | 33 | golden/probe fixtures（§6） |
| `common-api-processor/src/main/java` | 1 `.java` | `SpecCoverageProcessor` |
| `common-api-processor/src/main/resources` | 1 | `META-INF/services/javax.annotation.processing.Processor` |
| `common-api-processor/src/test/java` | 1 `.java` | 13 tests 全过 |

## 3. 每节点有效 source roots（`projectsEvaluated` 阶段 srcDirs 实测，证据 `w0-config-archive-2026-09-12/logs/win-fabric-srcdirs-final.log`）

| node | main java srcDirs | main resources srcDirs |
|---|---|---|
| `1.21.1` | `versions/1.21.1/src/main/java`；`build/generated/stonecutter/main/java` | 节点 res；`build/generated/stonecutter/main/resources`；`versions/1.21.1/src/generated/resources`（目录不存在，挂载为空）；**`src/main/resources-legacy`（原始）**；modMetadata；`build/generated/stonecutter/generated/resources`（空） |
| `26.1.2`（active） | 节点 java；**`src/main/java`（原始，无生成副本）** | 节点 res；**`src/main/resources`（原始）**；`versions/26.1.2/src/generated/resources`（空）；`src/main/resources-modern`（原始）；modMetadata |
| `26.2.0` | 节点 java；`build/generated/stonecutter/main/java` | 节点 res；`build/generated/stonecutter/main/resources`；`versions/26.2.0/src/generated/resources`（空）；`src/main/resources-modern`（原始）；modMetadata；`build/generated/stonecutter/generated/resources`（空） |
| `26.1.2-fabric` | 节点 java（61 个，fabric 实现）；`build/generated/stonecutter/main/java`（294 个） | **节点 res（与 convention 的 `fabric_source_node` root 同一路径，双重注入，见 §11）**；`build/generated/stonecutter/main/resources`（仅 `META-INF/accesstransformer.cfg`，被 exclude）；modMetadata（`fabric.mod.json`） |
| `26.2.0-fabric` | `versions/26.2.0-fabric/src/main/java`（不存在）；`build/generated/stonecutter/main/java`（301 个）；**借用 `versions/26.1.2-fabric/src/main/java`** | 节点 res（不存在）；`build/generated/stonecutter/main/resources`；**借用 `versions/26.1.2-fabric/src/main/resources`（单次注入）**；modMetadata |
| `:common` | `common/src/main/java` | `common/src/main/resources`；`common/src/main/templates` |

- 共享 java 树的生成副本实测文件数：1.21.1=**242**、26.1.2=301（active，未被消费）、26.2.0=301、26.1.2-fabric=**294**、26.2.0-fabric=301。1.21.1 少 59 个、26.1.2-fabric 少 7 个 = 整文件守卫在该版本取值为假时 stonecutter 不落盘。
- `:common` 的 `nekojs/api-runtime.properties` 展开后进 common jar（实测存在）。

## 4. Resources、mixins、metadata/templates、providers（实测）

| 类别 | `1.21.1` | `26.1.2` | `26.2.0` | `26.1.2-fabric` | `26.2.0-fabric` |
|---|---|---|---|---|---|
| jar 内 mixin config（refs 实测） | `nekojs.mixins.json` ×16 | `nekojs.mixins.json` ×17（16+1 client）；`nekojs-dynamic.mixins.json` ×1 | 同 26.1.2 | `nekojs-fabric.mixins.json` ×29；`nekojs-fabric-shared.mixins.json` ×13；`nekojs-fabric-dynamic.mixins.json` ×1 | 同左（remap 前中间 jar 实测） |
| metadata（jar 内实测） | `META-INF/neoforge.mods.toml`：deps 3 块 = neoforge `[21.1,22.0)` / minecraft `[1.21,1.22)` / **graalmc**；mixins config 2 条 | deps = neoforge `[26,)` / minecraft `[26.1.2,26.2)` / graalmc；loaderVersion `[4,)` | deps = neoforge `[26,)` / minecraft `[26.2.0,26.3)` / graalmc | `fabric.mod.json`：depends fabricloader>=0.19.3、fabric-api>=0.155.2+26.1.2、minecraft ~26.1.2、java>=25 | 同左，fabric-api>=0.159.0+26.2、minecraft ~26.2 |
| jar 内 AT 条目 | **78** | 69 | 69 | 无（exclude 验证成立） | 无 |
| ServiceLoader providers（源） | 2（McClientCompat$Impl / McPlatformCompat$Impl） | 3（+McVersionCompat$Impl） | 3 | 无节点本地 provider | 无 |
| test 资源 | `golden/block-events-api.txt` | 同左 | 同左 | `fabric-runtime-smoke/{server,startup}_scripts/fabric_ci_smoke.js` ×2 | （借用 source root，fixture 随之借用） |

- 1.21.1 与 26.x 的 AT 差异实测：1.21.1 多 11 条（`AbstractScrollWidget`/`AbstractSelectionList` scroll 系 + `WorldSelectionList.clearEntries` + `MobEffect$AttributeTemplate` 的 `ResourceLocation` 形态 ×2）；26.x 独有 2 条（同 `AttributeTemplate` 但 `Identifier` 形态，mc_ids 改名语义）。69−2+11=78 ✓。
- 手写版未记录：mods.toml 存在**第三个依赖块 graalmc**；fabric.mod.json 的 depends 展开值。

## 5. Processor 与两层 capability（实测）

### 5.1 processor 接线

| node/loader | annotationProcessor | platform option | 实测证据 |
|---|---|---|---|
| NeoForge 三节点 | `:common-api-processor` + `:common`（+lombok） | `-Anekojs.platform=nf121`/`nf26` | convention 源码接线（`nekojs.neoforge-node.gradle.kts:159-162,243-251`） |
| Fabric 两节点 | 仅 lombok，**未挂 `:common-api-processor`** | 不传 `-Anekojs.platform` | `nekojs.fabric-node.gradle.kts:79-81,280-286` 注释明示"接线是待办" |

- **`SpecCoverageProcessor` 生成物实测为零**：各节点 `build/generated/sources/annotationProcessor` 目录存在但为空——它是纯校验型 processor（校验 `@PlatformAvailability` scope vs `-Anekojs.platform`），不产生文件。手写版未记录这一点。

### 5.2 运行期平台 capability（`PlatformCapability` 10 项：TAGS、RESOURCE_PACKS、CLIENT_SCREENS、CLIENT_KEYBINDS、CLIENT_RENDERERS、RECIPE_HOT_RELOAD、RECIPE_SCHEMA_AWARE、NETWORK_CUSTOM_CHANNEL、NBT_BINARY_IO、RECIPE_VIEWER）

| 实现 | 实测声明 |
|---|---|
| `IPlatform`（common:67-68） | 默认 `Set.of()`（空集） |
| `NeoForgePlatform`（共享 src:71-83；1.21.1 节点版:69-81） | 两者均声明全部 10 项 |
| `FabricPlatform`（26.1.2-fabric:74-76） | 仅 `TAGS`、`RESOURCE_PACKS`、`NETWORK_CUSTOM_CHANNEL` 3 项 |

### 5.3 编译期 `@PlatformAvailability` spec（实测 10 个注解）

- ALL（6）：`BlockSpec`、`EntitySpec`、`ItemSpec`、`ItemStackSpec`、`LivingEntitySpec`、`PlayerSpec`。
- NF_ONLY（4）：`BlockStateSpec`、`LevelSpec`、`MutableComponentSpec`、`ServerSpec`。
- 枚举 `Scope` 含 `NF26_ONLY`、`CR_ONLY`，当前**无任何注解使用**。

## 6. 测试发现/跳过/结果（实测）与 golden 只读清单

| 模块/节点 | suites | tests | skipped | failed | 备注 |
|---|---:|---:|---:|---:|---|
| `:common` test（daemon JDK 25） | 177 | 1336 | 2 | 0 | skip 为 filesystem assume（symlink 等） |
| `:common-api-processor` test | 1 | 13 | 0 | 0 | |
| `:1.21.1` test | 17 | 58 | **0** | 0 | 整文件守卫过滤后发现面更小 |
| `:26.1.2` test | 29 | 137 | **34** | 0 | 6 个 suite 跳过：`ItemModificationComponentsTest`×15、`IngredientActionRegistryTest`×6、`BlockModificationEventJSTest`×6、`RegistryAutoAdapterScannerTest`×4、`BindingHelpersTest`×2、`BuilderTagTest`×1；原因 `TestAbortedException`（裸 JVM 无 FML/vanilla registry assume） |
| `:26.2.0` test | 29 | 137 | 34 | 0 | 与 26.1.2 完全相同 |
| `:26.1.2-fabric` / `:26.2.0-fabric` test | — | — | — | — | **未运行**：构建在 26.1.2-fabric:processResources 失败中止（§11）；`failOnNoDiscoveredTests=true` 行为未验证 |
| nbtSmokeTest（三 NeoForge 节点） | 1/节点 | 8/节点 | 0 | 0 | `nbt-smoke` tag 全过 |
| guardLint | — | — | — | — | 守卫块 252，扫描 332 文件，豁免 0，警告 0 |

**golden/契约基线只读清单**（本轮只读未改）：

- `common/src/test/resources/nekojs/golden/api-manifest-core.json`（1 个，`ApiManifestGoldenTest` 消费）
- `common/src/test/resources/nekojs/probe/*.expected.d.ts`（2 个）+ `probe/legacy-tree/**` fixture
- `versions/{1.21.1,26.1.2,26.2.0}/src/test/resources/golden/block-events-api.txt`（3 个）
- 普通 test 运行未更新任何 golden（`ApiManifestGoldenTest` 的 regenerate-abort 语义未被触发）。

## 7. generated task → 输出 trace（实测）

| task | 实测输出 | 消费者 |
|---|---|---|
| `stonecutterGenerate` | `versions/<n>/build/generated/stonecutter/main/{java,resources,resources-legacy,resources-modern,templates}`；java 文件数见 §3；resources 类合计 7 文件（AT 1 + legacy mixins 1 + modern 4 + 模板 1） | NeoForge `createMinecraftArtifacts`；fabric 节点仅 main/resources 被挂载（其余目录生成但未挂载） |
| `stonecutterGenerateTest` | `build/generated/stonecutter/test/java`：1.21.1=31、26.2.0=31、26.1.2=0（active 挂原始目录） | 节点 test source set |
| NeoForge `generateModMetadata` | `build/generated/sources/modMetadata/META-INF/neoforge.mods.toml` | jar 内实测存在 |
| Fabric `generateModMetadata` | `.../modMetadata/fabric.mod.json` | jar 内实测存在（26.2.0-fabric 中间 jar） |
| Fabric `extractIcuClasses` | `build/generated/icuClasses`（icu4j 77.1/78.3 类，解释 fabric jar 7399 entries vs neoforge ~1436） | main source set output → jar |
| `jar` | `build/libs/`（§8 实测表） | CI artifact；Fabric 另有 remapJar |
| `verifyDevModSourceSets` | 无产物 | 三 NeoForge 节点 check 中执行通过 |
| `verifyFabricRuntimeArtifact` | 无产物 | **未执行**（构建中止，见 §11） |

当前 worktree 无 `versions/*/src/generated/resources` 目录（挂载存在、目录不存在，datagen 未跑）。

## 8. 产物实测（2026-09-12，worktree 构建）

| node | 产物 | 大小 | SHA-256（前 16 位） | entries |
|---|---|---:|---|---:|
| `1.21.1` | `nekojs-neoforge-1.21.1-1.1.0-preview3.jar` | 2,403,604 B | `fbd1bb9eefcd7731` | 1383 |
| `26.1.2` | `nekojs-neoforge-26.1.2-1.1.0-preview3.jar` | 2,517,347 B | `7d3ce6d2e3a435c5` | 1436 |
| `26.2.0` | `nekojs-neoforge-26.2.0-1.1.0-preview3.jar` | 2,517,367 B | `ca41eab78ebe0b97` | 1436 |
| `26.2.0-fabric` | `nekojs-fabric-26.2-1.1.0-preview3.jar`（**remap 前中间 jar**；`remapJar`/`check` 未到达） | 17,397,689 B | `9572bf56cdda062c` | 7399 |
| `26.1.2-fabric` | **无产物**（processResources 失败） | — | — | — |
| `:common` | `nekojs-1.1.0-preview3.jar` | 1,675,966 B | `db6f7e9eb3fe674f` | 969 |

完整 SHA-256 与 jar 内 mixin/metadata 解析脚本输出见基线报告 §5 与 `logs/`。

## 9. CI node/task 清单（W0 后）

- 2026-09-12 W0 commit `8301dc14` 移除了 5 处 `Drop local org.gradle.java.home pin`（sed）步骤（原约 116、245、394、613、660 行），其余节点/任务接线与手写版 §8 一致：五节点 `build`、三 NeoForge `nbtSmokeTest`、Fabric artifact/development smoke matrix、仅 26.1.2 的 `runGameTestServer` 冒烟、common 的 JDK 21 与 Windows 复跑。

## 10. 复现方式（实测命令序列）

```bash
# 隔离 worktree（不含用户 WIP）
git worktree add ../NekoJS-w0-baseline <revision>   # 本轮 = 8301dc14
cd ../NekoJS-w0-baseline/NekoJS-mult
./gradlew help --console=plain                      # 配置 7 个项目
./gradlew projects --console=plain                  # 节点图
./gradlew :common:check :common-api-processor:test --console=plain
./gradlew guardLint --console=plain
./gradlew :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest --console=plain
./gradlew :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
# → 26.1.2-fabric:processResources 失败（DuplicateFileCopyException），复现率 100%（3 次均失败）
```

## 11. 本轮新发现（手写版没有的基线事实）

### 11.1 `:26.1.2-fabric:processResources` 冷构建确定性失败

- 错误：`Entry nekojs-fabric-dynamic.mixins.json is a duplicate but no duplicate handling strategy has been set.`（`tasks.jar` 有 `DuplicatesStrategy.EXCLUDE`，`processResources` 没有）。
- **根因实测**（init 脚本 `eachFile` 跟踪，证据 `logs/win-fabric-duplicate-copyspec-trace.log`：4 个文件各出现两次、FROM 路径完全相同）：同一目录 `versions/26.1.2-fabric/src/main/resources` 的 4 个文件被**两次**作为拷贝源——stonecutter 对版本化节点的节点本地资源挂载与 fabric convention 的 `fabricSourceRoot.resolve("resources")` srcDir 注入叠加（两套注入机制，srcDirs 集合去重掩盖了这一点；最终 srcDirs 见 `logs/win-fabric-srcdirs-final.log`）。
- `26.2.0-fabric` 不触发：其节点目录无 `src/main/resources`（借用 root 只经 convention 注入一次）。
- 失败是 revision 状态属性（三次执行均失败），但主仓库 `versions/26.1.2-fabric/build/resources/main`（mtime 2026-09-11 00:13）恰好只留下"第一组 4 个文件、无 fabric.mod.json"的部分输出——与一次失败执行的残留一致；最后完整 fabric jar 产物为 2026-09-07 21:52/22:37。**用户侧增量状态掩盖了该失败**。
- 处置建议：归入"Fabric 源所有权"类（后续票 31/32 范围）：为 fabric 节点显式声明节点/借用资源根的单一所有权（convention 或 stonecutter 二选一），或对 `processResources` 设 `duplicatesStrategy`。本基线不修改构建行为。

### 11.2 其他新事实

- toolchain 未检测到本机 GraalVM JDK 25（`javaToolchains` 报告无 `graalvm-jdk-25`）。
- Graal 解析版本在 fabric 节点 runtimeClasspath 上为 8762962，convention 的 8762963 重定向未作用（经 `:common` 传递解析）。
- `:1.21.1` test 0 跳过 vs 26.x 34 跳过：1.21.1 的整文件守卫把 assume 重的 suite 过滤掉了（发现面 17 suites vs 29）。
- W0 后被跟踪文件中 `org.gradle.java.home` 仅剩注释性提及；`wiki/构建系统.md`（仓库根共享 wiki，非本目录）仍描述旧 sed workaround，待 follow-up 更新。

## 12. 手写版 vs 实测版逐项差异表

| # | 项 | 手写版（2026-09-08） | 实测版（2026-09-12） | 判定 |
|---|---|---|---|---|
| 1 | §1 节点本地文件计数（59/3/3/61/0 等） | 静态计数 | 实测逐目录计数 | **一致** |
| 2 | §2 共享 roots 计数（301/1/1/4/1/31、507/20/1/182/33、1/1/1） | 静态计数 | 实测 | **一致**；补充：common templates 实际路径多一层 `nekojs/` 子目录（`common/src/main/templates/nekojs/api-runtime.properties`） |
| 3 | §3 effective source roots | 静态描述（convention 证据行号） | Gradle srcDirs 实测 | **一致且更细**：active 节点挂原始目录；非 active 挂生成副本；1.21.1 挂 legacy 原始目录；fabric 节点共享资源仅生成副本中的 AT（其余被 exclude）；**发现 fabric 借用 root 双重注入**（手写无） |
| 4 | §4 mixin 清单 | 文件级清单 | 文件级一致 + 每文件 refs 数（16/17+1/1/29/13/1） | **一致**（手写未给 refs 数） |
| 5 | §4 metadata | 模板路径与展开属性 | jar 内实测展开值 + deps 3 块含 **graalmc** + fabric depends 实测值 | **一致**；手写缺 graalmc 依赖块与展开值 |
| 6 | §4 AT | 未给条目数 | jar 内 69/69/78（1.21.1），差异 13 条逐条列出 | 实测新增 |
| 7 | §5.1 processor 接线 | nf121/nf26 + fabric 未挂 | 源码 + 构建行为一致；**生成物为零文件（纯校验）** | **一致**（手写未记录零生成物） |
| 8 | §5.2 capability | 10/10/3 | 10/10/3（行号微移） | **一致** |
| 9 | §5.3 spec 注解 | 6 ALL + 4 NF_ONLY | 6 ALL + 4 NF_ONLY；NF26_ONLY/CR_ONLY 零使用 | **一致** |
| 10 | §6 test 发现/跳过 | 明确声明"不声称实测" | 实测：common 1336/2 skip；processor 13；1.21.1 58/0；26.x 137/34 skip（6 suites 名单+原因）；fabric 未运行；nbt 8×3 | 实测新增（与手写 assume 清单语义吻合：FML/vanilla assume 即 26.x 跳过来源） |
| 11 | §7 generated trace | 任务→输出→消费者静态表 | 同构 + 实测文件数（242/301/301/294/301、test 31/0/31）与"生成但未挂载"目录 | **一致**且补充：fabric 的 resources-legacy/-modern/templates 生成目录不被任何 sourceSet 挂载 |
| 12 | §8 CI | 行号引用含 5 处 sed 步骤 | W0 已移除 5 步，其余一致 | **差异（预期内，W0 所致）** |
| 13 | 产物/版本 | 声明"未验证 artifact output" | jar 名/大小/SHA-256/entries/mixin/metadata 全实测 | 实测新增（手写版标记的缺口被补上） |
| 14 | 失败诊断 | 无 | 26.1.2-fabric:processResources 冷构建失败 + 根因 + owner 建议 | 实测新增 |
| 15 | `26.2.0-fabric` 源借用 | "无自有 root；借用 26.1.2-fabric" | srcDirs 实测确认借用 java/resources；`deps.fabric_source_node=26.1.2-fabric` | **一致** |

## 13. 实测口径限制

- 本清单基于 revision `8301dc14`、Windows 主机、JDK 25 daemon；Linux 仅完成配置阶段验证（见基线报告 §4），全量 Linux 构建不在本轮范围。
- 1.21.1 编译 toolchain 具体路径按检测列表推断（唯一 JDK 21），未逐任务打印。
- `26.2.0-fabric` 的 jar 是 remap 前中间产物，其 remap 后形态、`verifyFabricRuntimeArtifact`、fabric `test` 均因 §11.1 失败未执行。
- runtime smoke（`runServer`/`runGameTestServer`）不属于本票（34 号票范围），未运行。
- 性能采样不在 W0（02 号独立性能基线票）。
