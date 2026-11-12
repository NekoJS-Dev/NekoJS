<!-- wiki-page: build-system; locale: cn -->

> **中文** · [English](build-system_us)

<a id="wiki-section-1"></a>
# 构建系统

> 本页讲 NekoJS 单仓自身的构建：多版本 × 多加载器的组织、怎么编译各平台、守卫纪律、CI。**插件作者不需要读本页**（除非好奇）；贡献代码 / 发版需要。结构判定与守卫纪律见 [ADR-0007](https://github.com/NekoJS-Dev/NekoJS/blob/master/docs/adr/0007-module-boundaries.md) 和 [ADR-0008](https://github.com/NekoJS-Dev/NekoJS/blob/master/docs/adr/0008-guard-discipline.md)。

<a id="wiki-section-2"></a>
## 仓库形态

Stonecutter 多版本 × 多加载器**单仓**：一棵版本共享树、每节点参数、两个加载器 convention plugin，以及共享源码选择 convention。`settings.gradle.kts` 里的版本图：

```kotlin
stonecutter {
    versions("1.21.1", "26.1.2", "26.2.0")                    // NeoForge 节点（根分支，共享 src/）
    version("26.1.2-fabric", "26.1.2").buildscript = "fabric.gradle.kts"
    version("26.2.0-fabric", "26.2.0").buildscript = "fabric.gradle.kts"
}
```

- **共享树 `src/`**：所有节点共用单副本。版本差异 = `//? if >=26` 守卫 / replacements / 节点目录孪生；加载器差异 = `//? if neoforge` 整文件守卫（fabric 侧 = else 分支 + `src/fabric` raw loader root，由 fabric convention 显式挂载、不经 stonecutter 预处理，票 31 迁入）。
- **`versions/<node>/`**：节点参数（`gradle.properties` 的 `deps.*`）+ 节点专属源码（差异大到守卫不划算的孪生文件，如 26.x 拆出去前的 GUI / `PostEffectManager`）。
- **构建逻辑不在节点脚本里**：入口脚本只做声明，全部构建逻辑住 buildSrc。

节点 ID 不一定等于 Minecraft 依赖版本：`26.2.0-fabric` 当前指定 `deps.minecraft=26.2` 和 Fabric API `0.159.0+26.2`；Gradle 任务路径仍使用节点 ID。

<a id="wiki-section-3"></a>
## 入口脚本与 convention plugin

两个入口脚本各只有一行声明：

| 入口 | convention plugin | 承载内容 |
|---|---|---|
| `build.gradle.kts` | `buildSrc/.../nekojs.neoforge-node.gradle.kts` | MDG（neoForge 块/runs）、AT 与 mods.toml 喂送、era 资源分层、fat-jar、`verifyDevModSourceSets`、编译/测试约定 |
| `fabric.gradle.kts` | `nekojs.fabric-node.gradle.kts` | loom-back-compat（体内 apply，从控制器 `apply false` 声明读 Loom 版本）、ICU4J 类提取、night-config bundled、Fabric fat-jar、`verifyFabricRuntimeArtifact` |
| 由两个加载器 convention 应用 | `nekojs.node-sources.gradle.kts` | 节点内同相对路径 Java/资源优先于共享、raw Fabric 与 Stonecutter 生成根，包括 active 节点 |

约定俗成的三条纪律（历史上踩过的坑，已封死在插件体并就地注释）：

1. 节点在 settings 里先于 `:common` 注册 → 入口/插件必须 `evaluationDependsOn`。
2. stonecutter 预处理产物是 `stonecutterGenerate` 的输出又是 MDG 的输入 → 显式 `dependsOn`（neoforge-node 已写）。
3. fat-jar 里对 `:common` runtimeClasspath 的装配必须 `from(Closure)` 执行期求值，配置期解析会撞无锁解析。

**给 convention plugin 加东西时**：stonecutter 扩展不在 buildSrc 编译类路径（`stonecutter.process` 走反射桥）、loom/loomx 扩展走 `withGroovyBuilder` 动态访问；版本目录经 `buildSrc/settings.gradle.kts` 导入根 `gradle/libs.versions.toml`，依赖坐标保持单一事实源。

<a id="wiki-section-4"></a>
## 节点参数（versions/&lt;node&gt;/gradle.properties）

每节点一份，`deps.*` 前缀。两个容易混的键：

| 键 | 含义 | 例 |
|---|---|---|
| `deps.platform` | 守卫常量（`constants.match` 的事实源），取值 `neoforge` 或 `fabric` | `neoforge` |
| `deps.loader_version` | loader 版本号（仅 fabric 节点作为依赖坐标） | `0.19.3` |
| `deps.minecraft` / `deps.neo` / `deps.java` | 版本轴参数 | `26.1.2` / `26.1.2.71` / `25` |

<a id="wiki-section-5"></a>
## 守卫与改名（写共享树必读）

先分清哪些是任务会拦的、哪些是写法建议。

**guardLint 真的会拦**（`stonecutter.gradle.kts`，8 条规则，`./gradlew guardLint`，CI 强制）。硬失败的六条：守卫配对（`//? if` 数 == 闭合数）、守卫不落在 Java 文本块内、守卫分支首行不以 `/*` 开头、**单文件 `//? if` ≤ 20**、模块边界（`com.tkisor.nekojs.api.*` 零 MC/Loader import，common 其余部分零 MC/Loader import）、恒假常量（守卫常量必须至少在一个节点取真）。Graal 依赖属于 common 允许范围。加上 wrapper 层零 loader import（规则 7，当前 `wrapperLoaderImportHardFail = true`，所以也是硬失败；整文件 loader 守卫的 wrapper 是显式平台面，只做提示性列出）。

密度超 20 的**唯一**放行方式是在文件里写一行普通 Java 注释 `// guard-exempt(20): 理由`（不带 `//?` 前缀——那是 stonecutter 指令语法）。有理由就转为豁免并每次输出到豁免清单，没有就报错。

扫描范围各规则不同：守卫类规则只看 `src/**/*.java`（共享树），模块边界规则看 `common/src/main/java` 下的 `com/tkisor/nekojs/api/**`（L1，按包前缀取材）与全树（L2），恒假常量规则读 `versions/*/gradle.properties` 的 `deps.platform`。

**只告警不拦**：连续守卫段 > 8 行（规则 5，"方法级密度"的代理指标）。

**下面是写法建议，没有任何任务检查**：
- **新代码默认零守卫**（26.x 基准，ADR-0008 修订）：版本差异走版本 facade（`platform/compat/` 的 `McClientCompat` / `McPlatformCompat`，实现按版本三份、类名刻意不同防 drift、`META-INF/services` 注册）或节点孪生文件。这是密度限制之下的推荐处置路径，不是门禁——写满 20 条守卫的文件同样过 guardLint。
- **整文件拆分**建议只用于几乎无共享逻辑的文件（操作法：共享树纯 26.x + `versions/1.21.1/src` 已求值孪生）。

**replacements 是配置，不是纪律**：`!mc_ids` 吃纯改名（`ResourceLocation`↔`Identifier` 等，全局默认启用）；`mc_legacy_api` 默认关，仅文件级 `//~ mc_legacy_api` 局部启用（门槛：该文件 token 单侧不出现）。

**sandboxCheck**：聚合 guardLint、Java 子项目（包括各节点）的 `check` 和外部 addon 夹具隔离检查，提交前本地跑一遍。它不会启动 CI 的 Fabric 游戏服务器 smoke。

<a id="wiki-section-6"></a>
## 日常命令

```bash
./gradlew guardLint                          # 守卫 + 边界 lint（秒级）
./gradlew :26.1.2:build                      # 单节点构建（active 节点 IDE 直编）
./gradlew :26.1.2-fabric:verifyFabricRuntimeArtifact # 校验 Fabric fat jar 的入口和 loader 隔离
./gradlew :26.2.0-fabric:build                # 构建 26.2 Fabric 节点
./gradlew sandboxCheck                       # 全节点门禁（提交前）
./gradlew switchVersion -Pnode=26.2.0        # 切 active 节点（后需 IDE 重新 sync）
./gradlew :common:check                      # 引擎层测试
./gradlew :addons:nekujs:build                # 独立的 NeoForge 1.21.1 兼容 addon
```

**NekuJS 单独交付：** `:addons:nekujs` 是普通 addon 子项目，不会因某个 NekoJS 节点构建就自动嵌入该节点 JAR。其 `build` 运行不需要第三方 JAR 的普通契约和 auditor 测试；`fixtureTest`、`expandedFixtureTest`、`compatProofTest` 和 `runTestClient` 使用本地外部输入，默认目录为 `addons/nekujs/1211nekujs`，可用 `-PnekujsFixturesDir=/path/to/fixtures` 指定。扩展夹具检查是单独选择的任务，不应把普通 `build` 或静态审计当作全部 addon 的实际游戏兼容验收。第三方夹具不随产物分发。

active 节点 = IDE 里直接编译/运行的节点（`stonecutter.gradle.kts` 的 `stonecutter active "…"` 行）。

验证层级需要分开：节点构建编译、打包 Minecraft/加载器实现并运行已配置的检查；JVM 测试不等于运行游戏。两个加载器 convention 都把独立的 `platformGateTest` 覆盖检查挂入 `check`。NeoForge 还挂入 `nativeRecipeProbeTest`（独立 JVM 内的真实注册表/配方检查）；`nbtSmokeTest` 也是 JVM `Test` 任务，不是游戏服务器 smoke。CI 的 Fabric `runServer` smoke 仅检查 startup/server-started 夹具，不代表完整 API、客户端画面、重启或多人验收。

<a id="wiki-section-7"></a>
## 加新 MC 版本（三步）

1. `versions/<新版本>/gradle.properties`——复制相邻节点，改 `deps.minecraft` / `deps.neo` / `deps.jei`（curse 文件 id）/ `deps.mc_range` / `deps.neo_range` / `deps.java`。
2. `settings.gradle.kts` 版本图加节点名。
3. `.github/workflows/ci-build.yml` 的构建矩阵加一行（手写列表，节点数少不做生成）。

<a id="wiki-section-8"></a>
## CI

`.github/workflows/ci-build.yml`（手写节点矩阵）：`:common:check` / `:common-api-processor:test` → `guardLint` → NeoForge `nbtSmokeTest` → `npm run test:probe-types` → 各节点 `build`（含 Fabric 26.1.2/26.2.0）→ 每个 Fabric matrix leg 先校验下载的 Fabric jar，再运行 development server smoke。发版由提交标题 `update <版本>` 触发，GitHub Release 和 CurseForge 任务都依赖两个 Fabric smoke leg；Fabric 文件仍明确排除，直到单独开启发布。runtime smoke 使用版本库中的 fixture，看到 startup/server-started markers 后主动停止。JDK 供给：CI 由 `actions/setup-java` 提供，仓库不再钉 `org.gradle.java.home`；本机用户在用户级 `~/.gradle/gradle.properties` 设置或导出 `JAVA_HOME`。

节点构建后，CI 还运行 `npm run test:probe-native-jsx`、`python3 -m unittest discover -s tools -p test_active_node_sources.py`、`python3 tools/verify-active-node-sources.py --target build/active-source-matrix`，以及 `tools/nekojs-ci-gates.py` 中的源码根/覆盖检查。回归覆盖恢复、失败/陈旧摘要检测及保留已有 probe 路径；验证器通过 `tools/verify-node-source-selection.init.gradle` 检查真实 compile/resource 任务输入。版本节点必须选择自己的覆盖文件，不能静默编译共享孪生。

<a id="wiki-section-9"></a>
## 已知边界

- Fabric 的加载器/API 与测试面有意小于 NeoForge。两个 Fabric 节点均挂载不经过 Stonecutter 预处理的 raw loader root `src/fabric`；原 `deps.fabric_source_node` bridge 已删除。版本差异走节点覆盖/facade。CI 按 Fabric leg 校验下载制品与 development-server smoke，`verifyFabricRuntimeArtifact` 仍是 `check` 门禁；这些检查不等于所有 API 的画面/玩法验收。
- 不支持 Forge 1.20.1。它的 API 与共享树差了一个时代，守卫和 replacements 桥接不了，硬做等于维护第二套代码库，所以骨架已经从仓库里移除（需要的话可以从 git 历史找回）。
- Cleanroom 1.12.2 不在当前仓库构建图内；此处不验证其他分支的行为。

- [项目架构](project-architecture_cn) —— 模块背后的设计。

<!-- wiki-nav -->

---

[上一篇: Probe 类型生成](probe-type-generation_cn) · [目录](Home)
