# W0：五节点 source/artifact manifest（静态快照）

> 生成日期：2026-09-08
>
> 状态：**静态 source/config inventory，W0 尚未完整完成**。本文件只记录工作树中可直接复现的源码、Gradle 配置、节点属性和 CI 接线；**没有运行 Gradle，也不把已有 `build/`、`processed/`、`versions/*/build/` 或历史日志当作生成真相**。artifact output、jar entries、test discovered/skip/count、runtime smoke 和最终 capability 值仍未验证。

## 1. 五节点与节点本地文件计数

计数均为节点本地原始文件数，不含共享 `src/`、`common/`，也不代表 Gradle 实际 source set 解析结果。

| node | loader/support | 节点本地 root | main `.java` | main resources | main templates | test `.java` | test resources |
|---|---|---|---:|---:|---:|---:|---:|
| `26.1.2` | NeoForge / primary | `versions/26.1.2/src/main` | 3 | 3 | 0 | 0 | 1 |
| `26.2.0` | NeoForge / secondary | `versions/26.2.0/src/main` | 3 | 3 | 0 | 0 | 1 |
| `1.21.1` | NeoForge / experimental | `versions/1.21.1/src/main` | 59 | 2 | 0 | 0 | 1 |
| `26.1.2-fabric` | Fabric / experimental | `versions/26.1.2-fabric/src/main` | 61 | 4 | 1 | 0 | 2 |
| `26.2.0-fabric` | Fabric / experimental | 无自有 root；借用 `versions/26.1.2-fabric/src/main` | 0 | 0 | 0 | 0 | 0 |

- `26.1.2` 是 `stonecutter.gradle.kts:10-11` 的 active node；五节点集合来自 `settings.gradle.kts:34-43`。
- `26.2.0-fabric` 的 node id、Stonecutter 基准 `26.2.0` 与 `deps.minecraft=26.2` 是不同角色的现状；本清单不重命名、不推断终态。
- 旧清单中的 `61/6/6/66` 是 `versions/<node>/src/main` 的**总文件数**，不是 `.java` 数；本表已按 `.java`、resources、templates、test 分开。

## 2. 共享 source/resource roots（静态计数）

| root | 文件数 | 当前角色/消费者 |
|---|---:|---|
| `src/main/java` | 301 `.java` | Stonecutter 共享 MC-facing tree；NeoForge/Fabric 节点均消费 |
| `src/main/resources` | 1 | `META-INF/accesstransformer.cfg`；NeoForge MDG 消费，Fabric 可见但过滤 |
| `src/main/resources-legacy` | 1 | `nekojs.mixins.json`；仅 `modern=false` 的 `1.21.1` 消费 |
| `src/main/resources-modern` | 4 | 26.x mixins/interface injection/`pack.mcmeta`；Fabric 可见但过滤其中 NeoForge 项 |
| `src/main/templates` | 1 | `META-INF/neoforge.mods.toml`；NeoForge metadata 输入 |
| `src/test/java` | 31 `.java` | 共享 MC-facing tests；受 Stonecutter guards/节点 convention 影响 |
| `common/src/main/java` | 507 `.java` | MC/loader-free engine、api、runtime、script、eventbus、probe |
| `common/src/main/resources` | 20 | engine resources |
| `common/src/main/templates` | 1 | `api-runtime.properties` |
| `common/src/test/java` | 182 `.java` | engine/contract tests |
| `common/src/test/resources` | 33 | golden/probe fixtures |
| `common-api-processor/src/main/java` | 1 `.java` | `SpecCoverageProcessor` |
| `common-api-processor/src/main/resources` | 1 | `META-INF/services/javax.annotation.processing.Processor` |
| `common-api-processor/src/test/java` | 1 `.java` | processor 测试 |

NeoForge convention 的共享/节点 source-set 说明见 `buildSrc/src/main/kotlin/nekojs.neoforge-node.gradle.kts:1-7,52-53,78-89`；Fabric 的借用 root 与过滤见 `buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts:88-97`。

## 3. 每节点有效 source roots

### NeoForge：`1.21.1`、`26.1.2`、`26.2.0`

- 共享：`src/main/java`、`src/main/resources`、`src/main/templates`，以及按 `modern = !deps.minecraft.startsWith("1.")` 选择的 `src/main/resources-modern`（26.x）或 `src/main/resources-legacy`（1.21.1）。
- 节点本地：`versions/<node>/src/main/{java,resources}`；测试为共享 `src/test/java` 加节点本地 `src/test`。
- 引擎/处理器：`:common`、`:common-api-processor`；NeoForge 节点把 `:common` 同时放进 runtime 和 ModDev mod source set。
- 证据：`nekojs.neoforge-node.gradle.kts:28-36,78-89,111-115,153-162`；入口 `build.gradle.kts:1-6`。

### Fabric：`26.1.2-fabric`、`26.2.0-fabric`

- 共享：Stonecutter 自动挂载 `src/main/java`；Fabric convention 读取 `deps.fabric_source_node`，把 `versions/<value>/src/main` 作为 Java/resources/templates/access widener root。
- `26.1.2-fabric` 当前 `fabric_source_node=26.1.2-fabric`，使用自身节点本地 root。
- `26.2.0-fabric` 当前 `fabric_source_node=26.1.2-fabric`，没有自己的 `src/main`；使用 `26.2.0-fabric/gradle.properties` 展开 metadata，但源码、资源、mixins、模板来自借用 root。
- 证据：`versions/26.1.2-fabric/gradle.properties:1-6`、`versions/26.2.0-fabric/gradle.properties:1-6`、`nekojs.fabric-node.gradle.kts:21-25,88-97,99-127`；入口 `fabric.gradle.kts:1-6`。

## 4. Resources、mixins、metadata/templates、providers

| 类别 | `1.21.1` | `26.1.2` | `26.2.0` | `26.1.2-fabric` | `26.2.0-fabric` |
|---|---|---|---|---|---|
| shared mixin/config | legacy `nekojs.mixins.json` | modern `nekojs.mixins.json` + `nekojs-dynamic.mixins.json` | 同 26.1.2 | 可见 shared，但过滤 NeoForge 项 | 同左，借用 source root |
| metadata template | shared `neoforge.mods.toml` | shared `neoforge.mods.toml` | shared `neoforge.mods.toml` | `versions/26.1.2-fabric/src/main/templates/fabric.mod.json` | 借用同一模板 |
| compat providers | 2 个 ServiceLoader 文件 | 3 个 | 3 个 | 无节点本地 provider | 无节点本地 provider |
| runtime fixtures | 1 个 golden resource | 1 个 golden resource | 1 个 golden resource | 2 个 `fabric-runtime-smoke` fixture | 借用同一 fixture |

- NeoForge provider：`1.21.1` 为 `Nf1211ClientCompat`/`Nf1211PlatformCompat`；`26.1.2`、`26.2.0` 各为 `Nf261*`/`Nf262*` 的 Client/Platform/Version 三个实现。
- Fabric 资源：`nekojs-fabric.mixins.json`、`nekojs-fabric-shared.mixins.json`、`nekojs-fabric-dynamic.mixins.json`、`nekojs-fabric.accesswidener`；模板为 `fabric.mod.json`。
- Fabric Java 过滤：`nekojs.fabric-node.gradle.kts:96` 排除 `**/NeoForge*.java`。
- Fabric resource 过滤：`nekojs.fabric-node.gradle.kts:154-164` 排除 `META-INF/accesstransformer.cfg`、`META-INF/neoforge.mods.toml`、`neoforge.mods.toml`、`nekojs.mixins.json`、`nekojs-dynamic.mixins.json`、`nekojs.interface_injection.json`。
- Fabric 最终 artifact gate：`nekojs.fabric-node.gradle.kts:213-278` 检查必需 entries、禁止 NeoForge 前缀/类名和错误的 `fabric-api` 下限；`check` 依赖该任务。

## 5. Processor 与两层 capability

### 5.1 processor 接线

| node/loader | annotationProcessor path | platform option | 证据 |
|---|---|---|---|
| NeoForge `1.21.1` | `:common-api-processor` + `:common` | `-Anekojs.platform=nf121` | `nekojs.neoforge-node.gradle.kts:159-162,243-250`；`versions/1.21.1/gradle.properties:4` |
| NeoForge `26.1.2` | 同上 | `-Anekojs.platform=nf26` | 同 convention；`versions/26.1.2/gradle.properties:4` |
| NeoForge `26.2.0` | 同上 | `-Anekojs.platform=nf26` | 同 convention；`versions/26.2.0/gradle.properties:4` |
| Fabric `26.1.2-fabric` | 仅 Lombok；未挂 `:common-api-processor` | 不传 `-Anekojs.platform` | `nekojs.fabric-node.gradle.kts:79-81,280-285` |
| Fabric `26.2.0-fabric` | 同左，借用 convention | 同左 | 同左 |

`SpecCoverageProcessor` 当前接受 `nf26`、`nf121`、`cr`，拒绝旧值 `nf`；证据：`common-api-processor/src/main/java/com/tkisor/nekojs/api/spec/processor/SpecCoverageProcessor.java:105-118,204-209`。

### 5.2 运行期平台 capability（`IPlatform` 层）

`common/src/main/java/com/tkisor/nekojs/platform/PlatformCapability.java:3-14` 当前枚举 10 项：

`TAGS`、`RESOURCE_PACKS`、`CLIENT_SCREENS`、`CLIENT_KEYBINDS`、`CLIENT_RENDERERS`、`RECIPE_HOT_RELOAD`、`RECIPE_SCHEMA_AWARE`、`NETWORK_CUSTOM_CHANNEL`、`NBT_BINARY_IO`、`RECIPE_VIEWER`。

| 实现 | 源码声明 | 语义 |
|---|---|---|
| `IPlatform` | `common/src/main/java/com/tkisor/nekojs/platform/IPlatform.java:11-110` | 运行期平台接口；`capabilities()` 默认空集，`nbtBinaryCodec()` 默认 unsupported，`registryQueryService()` 默认 `null` |
| `NeoForgePlatform` | `src/main/java/com/tkisor/nekojs/platform/NeoForgePlatform.java:71-83`；`versions/1.21.1/src/main/java/com/tkisor/nekojs/platform/NeoForgePlatform.java:69-80` | 当前声明全部 10 项 capability |
| `FabricPlatform` | `versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/platform/FabricPlatform.java:74-76` | 当前只声明 `TAGS`、`RESOURCE_PACKS`、`NETWORK_CUSTOM_CHANNEL`；NBT/registry 使用 `IPlatform` 默认未实现语义 |

脚本侧 `PlatformFacade.capabilities()` 把枚举映射为小写连字符 ID，见 `common/src/main/java/com/tkisor/nekojs/core/api/facade/DefaultPlatformFacade.java:66-75`。NeoForge 初始化见 `src/main/java/com/tkisor/nekojs/NekoJSMod.java:56-59`；Fabric 初始化见 `versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/NekoJSFabricMod.java:69-75`。

### 5.3 编译期 `@PlatformAvailability` spec（与运行期 capability 不同层）

当前 `common/src/main/java/com/tkisor/nekojs/api/spec/inject/` 有 10 个 spec 注解：6 个 `ALL`、4 个 `NF_ONLY`；没有当前声明的 `NF26_ONLY`/`CR_ONLY`。以下短名均位于该目录：

- `ALL`：`BlockSpec:15`、`EntitySpec:25`、`ItemSpec:15`、`ItemStackSpec:20`、`LivingEntitySpec:25`、`PlayerSpec:34`。
- `NF_ONLY`：`BlockStateSpec:12`、`LevelSpec:22`、`MutableComponentSpec:27`、`ServerSpec:15`。
- 枚举与语义：`common/src/main/java/com/tkisor/nekojs/api/spec/PlatformAvailability.java:20,28-36`；processor 的 scope 检查见 `SpecCoverageProcessor.java:121-158,204-209`。
- 这一层是**编译期 spec 覆盖声明**，不是 `PlatformCapability` 的运行期能力清单，也不是最终 `supported/partial/unavailable` 矩阵。

## 6. test guard / assume 静态清单

以下仅为源码声明，**不声称 discovered/skip/count 实测结果**。表中 `S=` `src/test/java/com/tkisor/nekojs/`，`C=` `common/src/test/java/com/tkisor/nekojs/`。

| 类型 | 关键位置 | 静态语义 |
|---|---|---|
| 整文件 Stonecutter guard | `S/wrapper/registry/BuilderTagTest.java:2-3,264-265`；`S/wrapper/pdata/PDataSyncAcceptTest.java:1,82`；`S/wrapper/event/server/ItemModificationComponentsTest.java:2-3,342-343`；`S/dynamic/DynamicRegistryBuilderTest.java:2-3,91-92`；`S/wrapper/event/server/BlockModificationEventJSTest.java:2-3,194-195`；`S/api/recipe/IngredientActionRegistryTest.java:2-3,166-167`；`S/js/type_adapter/RegistryAutoAdapterScannerTest.java:1,142`；`S/bindings/event/client/KeyBindEventsTest.java:2-3,216-217` 等 | 按 `neoforge`/`>=26` 选择是否编译 |
| vanilla registry/FML assume | `S/testfixture/VanillaRegistryProbe.java:9-13`；`S/api/recipe/IngredientActionRegistryTest.java:39,105`；`S/wrapper/event/server/BlockModificationEventJSTest.java:46-47`；`S/wrapper/event/server/ItemModificationComponentsTest.java:51`；`S/wrapper/registry/BuilderTagTest.java:168`；`S/bindings/static_access/BindingHelpersTest.java:51,61`；`S/js/type_adapter/RegistryAutoAdapterScannerTest.java:60,74,92,128` | 裸 JVM/无 FML Loader 时跳过 |
| client class assume | `S/bindings/event/client/KeyBindEventsTest.java:104,117,130,142,152,171,188,191` | 裸 JUnit 缺少 vanilla/client 类时跳过 |
| filesystem assume | `C/core/api/json/JsonFileStoreTest.java:93,103,114`；`C/core/api/nbt/NbtFileStoreTest.java:91,101,112`；`C/core/fs/NekoJSPathsTest.java:46,122`；`C/wrapper/DataGeneratorJSPathTest.java:90`；`C/probe/ProbeOutputCommitterTest.java:168` | symlink/junction/Windows 文件系统不可用时跳过 |
| golden regenerate abort | `C/core/api/ApiManifestGoldenTest.java:44-48`；`C/probe/LegacyProbeCompatibilityTest.java:88`；`C/probe/LegacyProbeTreeTest.java:57`；`C/probe/ProbeOutputCompatibilityTest.java:52`；`C/probe/ProbeTypeScriptFixtureWriterTest.java:115`；`C/probe/TypeScriptNoopIrGoldenTest.java:132,143,167,295` | 显式重生成后暂停断言 |
| JUnit tag | `C/core/api/nbt/NbtFileStoreTest.java:27`；`C/core/api/Phase3AFacadeIntegrationTest.java:31`；`S/platform/nbt/NeoForgeNbtBinaryCodecTest.java:33` | `nbt-smoke` 过滤标签 |

当前源码扫描未发现 `@Disabled` 或 `@Ignore`。Fabric convention 显式设置 `failOnNoDiscoveredTests = true`（`nekojs.fabric-node.gradle.kts:169-176`）；NeoForge `tasks.test` 见 `nekojs.neoforge-node.gradle.kts:261-267`，本清单不据此推断实际发现数。

## 7. generated task → 输出 → 消费者

| task/输入 | 声明输出 | 消费者/门禁 | 证据 |
|---|---|---|---|
| `stonecutterGenerate` | `versions/<node>/build/generated/stonecutter/{main,test}` | NeoForge `createMinecraftArtifacts`；AT/template 的 `stonecutterProcessed` | `nekojs.neoforge-node.gradle.kts:68-74,176-180,201-208` |
| NeoForge `generateModMetadata` | `build/generated/sources/modMetadata/META-INF/neoforge.mods.toml` | `sourceSets.main.resources` → `processResources`/`jar` | `nekojs.neoforge-node.gradle.kts:183-211,226-239` |
| Fabric `generateModMetadata` | `build/generated/sources/modMetadata/fabric.mod.json` | `sourceSets.main.resources` → `processResources`/`jar` → Loom remap | `nekojs.fabric-node.gradle.kts:109-127,179-208` |
| Fabric `extractIcuClasses` | `build/generated/icuClasses` | main source-set output → dev run/`jar` | `nekojs.fabric-node.gradle.kts:129-146,192-207` |
| NeoForge `data` run | `versions/<node>/src/generated/resources` | `sourceSets.main.resources` | `nekojs.neoforge-node.gradle.kts:134-140,78-80` |
| `jar` | `versions/<node>/build/libs/*.jar` | CI artifact 收集/上传；Fabric 另经 `verifyFabricRuntimeArtifact` | `nekojs.neoforge-node.gradle.kts:226-239`；`nekojs.fabric-node.gradle.kts:192-208,213-278`；`.github/workflows/ci-build.yml:148-199` |
| `verifyDevModSourceSets` | 无独立产物 | NeoForge `check` 门禁 | `nekojs.neoforge-node.gradle.kts:283-320` |
| `verifyFabricRuntimeArtifact` | 无独立产物 | Fabric `check` 门禁，读取最终 jar | `nekojs.fabric-node.gradle.kts:213-278` |

当前工作树没有 `versions/*/src/generated/resources` 目录；已有的 `build/generated`、`versions/*/processed` 仅作历史存在，**不作为本清单输出事实**。

## 8. CI node/task 清单

| CI 位置 | 当前节点/任务 |
|---|---|
| `.github/workflows/ci-build.yml:141-146` | `1.21.1`、`26.1.2`、`26.2.0`、`26.1.2-fabric`、`26.2.0-fabric` 的 `build` |
| `.github/workflows/ci-build.yml:134-136` | 三个 NeoForge 节点的 `nbtSmokeTest` |
| `.github/workflows/ci-build.yml:215-223` | Fabric `26.1.2-fabric`、`26.2.0-fabric` 的 artifact/development smoke matrix |
| `.github/workflows/ci-build.yml:306-317` | 当前 Fabric smoke fixture 仍引用 `versions/26.1.2-fabric/src/test/resources/fabric-runtime-smoke` |
| `.github/workflows/ci-build.yml:631-670` | 仅 `26.1.2` 的 `runGameTestServer` 冒烟 |
| `.github/workflows/ci-build.yml:369-400,593-619` | `common` 的 JDK 21 与 Windows 复跑 |

## 9. 复现这些静态信息的只读 PowerShell

```powershell
# 节点本地文件计数
$nodes = '1.21.1','26.1.2','26.2.0','26.1.2-fabric','26.2.0-fabric'
foreach ($n in $nodes) {
  Get-ChildItem "versions/$n/src" -Recurse -File -ErrorAction SilentlyContinue |
    Group-Object { $_.DirectoryName -replace '.*\\src\\', '' } |
    Select-Object Name, Count
}

# 根/共享 roots 计数
Get-ChildItem src/main/java,src/main/resources,src/main/resources-legacy,src/main/resources-modern,src/main/templates,src/test/java,common/src/main/java,common/src/test/java -Recurse -File |
  Group-Object Extension | Select-Object Name, Count

# 配置与节点属性
Get-Content settings.gradle.kts,stonecutter.gradle.kts,build.gradle.kts,fabric.gradle.kts
Get-Content buildSrc/src/main/kotlin/nekojs.neoforge-node.gradle.kts,buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts
Get-Content versions/*/gradle.properties

# processor/capability/PlatformAvailability 声明
rg -n 'PlatformAvailability|PlatformCapability|capabilities\(\)|annotationProcessor|Anekojs\.platform' common/src/main src versions buildSrc/src/main/kotlin

# test guards/assume/tag
rg -n 'assumeTrue|assumeFalse|Assumptions|@Tag|@Disabled|@Ignore|//\? if' src/test common/src/test

# CI 节点/task 列表
rg -n 'node:|matrix:|run:|fixture_dir|Upload|artifact' .github/workflows/ci-build.yml
```

## 10. W0 当前限制

- 本文件完成的是**静态 source/config inventory**；W0 仍不是完整基线。
- 未运行 Gradle，因此未验证 project/source-set 实际解析、task graph、编译、check、测试发现、skip 原因和计数。
- 未验证 artifact output、jar entries、metadata 实际展开值、runtime smoke、Fabric 真实 mods 运行和性能基线。
- `PlatformCapability` 与 `@PlatformAvailability` 只是源码声明；`supported`/`partial`/`unavailable` 的最终矩阵仍需 P0/P3 证据。
- `26.2.0-fabric` 的 source bridge、W8 目标 `src/fabric` 迁移、旧 fixture 路径同步均未执行。
- 已有 `build/`、`processed/`、历史日志和生成目录不得替代上述验证。
