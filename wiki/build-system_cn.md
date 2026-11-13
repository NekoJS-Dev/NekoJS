<!-- wiki-page: build-system; locale: cn -->

> **中文** · [English](build-system_us)

<a id="wiki-section-1"></a>
# 构建系统

本页讲 NekoJS 仓库自己怎么构建：多个 Minecraft 版本和加载器怎么共用一份代码，怎么编译、检查和发版。给 NekoJS 贡献代码或负责发版时需要读；写插件不需要。

<a id="wiki-section-2"></a>
## 快速上手

```bash
./gradlew :26.1.2:build                      # 构建一个节点
./gradlew guardLint                          # 检查守卫写法和模块边界，几秒钟
./gradlew :common:check                      # 引擎层测试和隔离检查
./gradlew sandboxCheck                       # 提交前跑：guardLint + 所有节点的 check
./gradlew switchVersion -Pnode=26.2.0        # 切换 IDE 里直接编译的节点
```

构建产物在 `versions/<节点>/build/libs/`。

环境要求：

- 运行 Gradle 需要 JDK 17 以上，推荐 25。仓库不固定本机 JDK 路径，请在用户级 `~/.gradle/gradle.properties` 里设置 `org.gradle.java.home`，或者设置 `JAVA_HOME`。
- 编译用的 JDK 由各节点的 `deps.java` 决定（1.21.1 是 21，26.x 是 25），通过 Gradle toolchain 自动获取。

<a id="wiki-section-3"></a>
## 节点

NekoJS 用 [Stonecutter](https://stonecutter.kikugie.dev/) 在一个仓库里构建多个版本。每个 Minecraft 版本加加载器的组合叫一个**节点**。`settings.gradle.kts` 里定义了五个：

```kotlin
stonecutter {
    kotlinController = true
    create(rootProject) {
        versions("1.21.1", "26.1.2", "26.2.0")                                // NeoForge
        version("26.1.2-fabric", "26.1.2").buildscript = "fabric.gradle.kts"  // Fabric
        version("26.2.0-fabric", "26.2.0").buildscript = "fabric.gradle.kts"
    }
}
```

| 节点 | 加载器 | Java |
|---|---|---|
| `1.21.1` | NeoForge 21.1 | 21 |
| `26.1.2` | NeoForge 26.1.2 | 25 |
| `26.2.0` | NeoForge 26.2.0 | 25 |
| `26.1.2-fabric` | Fabric | 25 |
| `26.2.0-fabric` | Fabric | 25 |

Gradle 任务路径用的是节点 ID，比如 `:26.2.0-fabric:build`。节点 ID 不一定等于 Minecraft 版本：`26.2.0-fabric` 依赖的是 `deps.minecraft=26.2`。

除了这五个节点，还有两个普通子项目 `common`、`common-api-processor`（不分版本），以及独立发布的 `:addons:nekujs`。

<a id="wiki-section-4"></a>
### active 节点

IDE 里能直接编译和运行的那个节点叫 active 节点，写在 `stonecutter.gradle.kts` 的 `stonecutter active "…"` 这一行。active 节点直接编译共享的 `src/`；其他节点编译 Stonecutter 预处理后的副本。

切换 active 节点用 `./gradlew switchVersion -Pnode=<节点>`，然后在 IDE 里重新加载 Gradle 项目。不要手改那一行。

<a id="wiki-section-5"></a>
## 源码怎么组织

| 位置 | 内容 |
|---|---|
| `src/main/` | 所有节点共用的一份源码，按 26.x 的写法写 |
| `src/main/resources-modern` / `resources-legacy` | 26.x 和 1.21.1 各自的 AT、mixin 配置等资源 |
| `src/fabric/` | Fabric 专用的代码、资源和模板，由 Fabric 约定直接挂载，不经 Stonecutter 预处理 |
| `versions/<节点>/gradle.properties` | 节点参数 |
| `versions/<节点>/src/` | 节点专属源码 |

节点专属文件会覆盖共享文件：如果 `versions/<节点>/src/` 下有一个和共享树相对路径相同的文件，这个节点就用自己的版本，不编译共享的那份。这对 active 节点同样成立，由 `nekojs.node-sources` 约定实现。

节点专属源码有两种用途：

- 版本门面的具体实现，例如 `platform/compat/` 下 `McPlatformCompat`、`McClientCompat` 的各版本实现。
- 版本差异大到不值得写守卫的“孪生文件”：共享树放 26.x 版本，`versions/1.21.1/src/` 放同名的 1.21.1 版本。注意节点目录里的文件**不经过**守卫求值，必须是已经求值好的纯 1.21.1 代码。代价是两份要同步维护。

<a id="wiki-section-6"></a>
### 节点参数

`versions/<节点>/gradle.properties` 里的键都以 `deps.` 开头，例如 26.1.2 节点：

```properties
deps.minecraft=26.1.2
deps.neo=26.1.2.71
deps.jei=7920926
deps.platform_tag=nf26
deps.mc_range=[26.1.2,26.2)
deps.neo_range=[26,)
deps.java=25
deps.platform=neoforge
```

两个容易混淆的键：

| 键 | 含义 | 例子 |
|---|---|---|
| `deps.platform` | 加载器常量，守卫里 `neoforge` / `fabric` 的取值来源 | `neoforge` |
| `deps.loader_version` | 加载器版本号，只有 Fabric 节点用作依赖坐标 | `0.19.3` |

<a id="wiki-section-7"></a>
## 构建脚本

两个入口脚本都只有一行，构建逻辑全部在 `buildSrc` 的约定插件里：

| 入口 | 约定插件 | 负责 |
|---|---|---|
| `build.gradle.kts` | `nekojs.neoforge-node` | ModDev（MDG）配置和运行配置、AT 与 `neoforge.mods.toml`、按时代分层的资源、fat jar、`verifyDevModSourceSets`、`verifyNeoForgeRuntimeArtifact` |
| `fabric.gradle.kts` | `nekojs.fabric-node` | Loom、按加载器选择 Graal 依赖、night-config 打包、Fabric fat jar、`verifyFabricRuntimeArtifact` |
| 两个约定插件都会应用 | `nekojs.node-sources` | 节点专属文件优先于共享树、`src/fabric` 和 Stonecutter 生成的源码 |

每个 NeoForge 或 Fabric 节点只生成一个供客户端和专用服务器共用的模组 fat jar，内嵌 `common` 的运行时类；不再生成单独的 `-server` jar。Graal 不内嵌，由玩家另装的 Graal 前置模组提供。

NekoJS 模组 jar 不内嵌 ICU4J。两个加载器均要求 GraalMC 25.5.0 或更高：NeoForge 使用 `maven.modrinth:bjcrgPcA:O8qtoV6d`，Fabric 使用 CurseForge 文件 9555615。两个固定版本均提供重定位 ICU，支持开发与安装环境中的 GraalJS；该运行时无需单独提取 ICU 或外部原包名 ICU 依赖。

修改约定插件时要注意：

1. 节点在 settings 里比 `:common` 先注册，所以约定插件要 `evaluationDependsOn(":common")`。
2. Stonecutter 预处理的输出是 MDG 的输入，需要显式 `dependsOn("stonecutterGenerate")`。
3. fat jar 装配 `:common` 的 runtimeClasspath 必须用 `from(Closure)` 在执行期求值，在配置期解析会出问题。
4. Stonecutter 扩展不在 buildSrc 的编译类路径上，要通过反射访问；Loom 扩展用 `withGroovyBuilder` 动态访问。
5. 依赖坐标统一写在根目录的 `gradle/libs.versions.toml`，buildSrc 通过 `buildSrc/settings.gradle.kts` 导入。

<a id="wiki-section-8"></a>
## 守卫与改名

共享树的代码按 26.x 写。需要区分版本或加载器时，有三种办法，优先级从高到低：

1. **版本门面**：在共享树里调用 `McPlatformCompat` 之类的门面，各版本的实现放在节点目录。新代码默认用这种方式。
2. **节点孪生文件**：只用于几乎没有共享逻辑的文件。
3. **Stonecutter 守卫**：`//? if >=26 { … //?}` 或 `//? if neoforge { … //?}`。只在前两种不合适时使用。

纯改名不需要守卫，由 `stonecutter.gradle.kts` 里的 replacements 处理：

- `!mc_ids` 组默认启用，处理 `ResourceLocation` ↔ `Identifier`、`GuiGraphics` ↔ `GuiGraphicsExtractor` 这类改名。入选条件是这个名字在某一个版本里完全不出现。
- `mc_legacy_api` 组默认关闭，只在验证过的文件里用 `//~ mc_legacy_api` 启用。它包含调用形状不同的改名，比如 `displayClientMessage` ↔ `sendSystemMessage`。

> replacements 的规则必须写成字面的 `replace(...)` 调用。用循环生成的规则会悄悄失效，编译也不报错。

<a id="wiki-section-9"></a>
### guardLint 检查什么

`./gradlew guardLint` 在 CI 里强制执行。以下情况会直接失败：

- `//? if` 和闭合标记数量不配对。
- 守卫写在 Java 文本块（`"""`）里。
- 守卫分支的第一行以 `/*` 开头。
- 单个文件里 `//? if` 超过 20 个。确实需要时，在文件里写一行普通 Java 注释 `// guard-exempt(20): 理由`，会被列进豁免清单。
- `common`（包括 `com.tkisor.nekojs.api.*`）import 了 Minecraft 或加载器的包（`net.minecraft`、`net.neoforged`、`net.fabricmc`、`net.minecraftforge`、`com.mojang`）。Graal 可以用。
- `wrapper` 包里有守卫之外的加载器 import。整文件包在加载器守卫里的 wrapper 不算违规。
- 守卫用到的加载器常量在所有节点都不成立（这个分支永远不会编译）。

连续守卫段超过 8 行只告警，不会失败。

守卫类规则只检查 `src/**/*.java`；模块边界规则检查 `common/src/main/java`。

<a id="wiki-section-10"></a>
## 检查与测试

| 任务 | 做什么 |
|---|---|
| `guardLint` | 上一节的守卫和边界检查 |
| `:common:check` | 引擎层测试，加上 `checkCommonIsolation` 等隔离检查。只跑 `:common:test` 不能代替它 |
| `:<节点>:check` | 节点测试、`platformGateTest`（spec 和事件覆盖）、产物校验；NeoForge 节点还有 `nativeRecipeProbeTest` |
| `:<节点>:build` | 编译、打包，并跑上面的 check |
| `sandboxCheck` | `guardLint` + 所有节点的 `check` + `verifyExternalAddonIsolation`（确认测试用的 addon 夹具没有混进正式 jar） |
| `:<NeoForge 节点>:nbtSmokeTest` | NBT 相关的 JVM 测试 |

这些都是 JVM 测试和产物检查，不会启动游戏。CI 里的 Fabric `runServer` 冒烟测试也只确认服务端能启动、启动脚本能执行，不等于完整的游戏内验收。

<a id="wiki-section-11"></a>
## 独立 addon：NekuJS

`:addons:nekujs` 是一个让部分 KubeJS addon 跑在 NekoJS 上的兼容层，只支持 NeoForge 1.21.1。它单独打包成 `nekujs-neoforge-1.21.1-<版本>.jar`，不会被嵌进任何 NekoJS 节点的 jar。

```bash
./gradlew :addons:nekujs:build
```

`build` 只跑不需要第三方 jar 的契约测试。`fixtureTest`、`expandedFixtureTest`、`compatProofTest`、`runTestClient` 需要本地的第三方 jar，默认放在 `addons/nekujs/1211nekujs`，也可以用 `-PnekujsFixturesDir=/path/to/fixtures` 指定。这些第三方 jar 不随产物分发。

<a id="wiki-section-12"></a>
## 加一个新的 Minecraft 版本

1. 复制一个相近节点的 `versions/<节点>/`，修改 `gradle.properties` 里的 `deps.minecraft`、`deps.neo`（或 Fabric 的 `deps.fabric_api`）、`deps.jei`、`deps.mc_range`、`deps.neo_range`、`deps.java`。
2. 在 `settings.gradle.kts` 的 `stonecutter` 块里加上新节点。
3. 更新写死节点列表的地方：`.github/workflows/ci-build.yml` 的构建命令（Fabric 节点还有 smoke 矩阵），以及 `tools/nekojs-ci-gates.py`。
4. 跑 `./gradlew :<新节点>:build` 和 `./gradlew sandboxCheck`。

<a id="wiki-section-13"></a>
## CI 与发版

`.github/workflows/ci-build.yml` 的主流程：

1. `:common:check`、`:common-api-processor:test`
2. `guardLint`
3. 三个 NeoForge 节点的 `nbtSmokeTest`
4. `npm run test:probe-types`（对生成的声明文件做 TypeScript 类型检查）
5. 五个节点的 `build`
6. 原生配方 probe、JSX 声明、各节点源码覆盖选择、构建门禁报告（`tools/nekojs-ci-gates.py`）等额外检查
7. 每个 Fabric 节点：通过 `jar` 任务构建并校验本 job 的最终产物（26.x 无需重映射），再跑开发服务端冒烟测试；常规 Fabric Dev-Jars 仍作为 CI 产物，不上传单独的冒烟 jar 产物。

另有一个 job 用 JDK 21 再跑一次 `:common:check`，防止依赖 Gradle 守护进程的 JDK 版本。

**发版**由提交标题触发：

| 提交标题 | 通道 |
|---|---|
| `update <版本>` | release |
| `update beta <版本>` | beta |
| `update alpha <版本>` | alpha |

发版会创建 GitHub Release 并发布到 CurseForge，前提是构建和 Fabric 冒烟都通过。Fabric jar 目前只作为 CI 产物，不随正式版发布。

<a id="wiki-section-14"></a>
## 已知边界

- Fabric 支持的加载器 API 和测试范围有意比 NeoForge 小。
- 不支持 Forge 1.20.1。它和共享树差了一个时代，守卫和改名桥接不了，所以骨架已经移除，需要的话可以从 git 历史找回。
- Cleanroom 1.12.2 不在本仓库的构建范围内。

<a id="wiki-section-15"></a>
## 接下来

- [项目架构](project-architecture_cn)：各个模块是怎么配合的。
- [插件开发](plugin-development_cn)：写一个 NekoJS 插件 mod。

<!-- wiki-nav -->

---

[上一篇: Probe 类型生成](probe-type-generation_cn) · [目录](Home) · [下一篇: JSX 进阶控件与工作区](jsx-client-ui-advanced_cn)
