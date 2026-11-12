<!-- wiki-page: project-architecture; locale: cn -->

> **中文** · [English](project-architecture_us)

<a id="wiki-section-1"></a>
# 项目架构

本页写给给 NekoJS 本体贡献代码的人，以及想弄清内部结构的插件作者。写脚本不需要读。

先用一个例子把各部分串起来，再按模块分别讲。

<a id="wiki-section-2"></a>
## 一个脚本是怎么跑起来的

以 `server_scripts/recipes.py` 里的这段代码为例：

```python
from nekojs import ServerEvents

def add_recipes(event):
    event.shapeless('minecraft:dirt', ['minecraft:sand'])

ServerEvents.recipes(add_recipes)
```

从游戏启动到配方生效，依次经过：

1. **插件发现**：加载器入口找到所有 `@RegisterNekoJSPlugin` 插件，包括内置的 `PythonTranspilerPlugin`。
2. **插件运行时**：`NekoPluginBootstrap` 逐个扩展点收集插件贡献，得到不可变的 `NekoPluginRuntime`。`PythonTranspilerPlugin` 在这一步通过 `ScriptCompilersPoint` 把 `.py` 注册给 `PythonToJsCompiler`；`ServerEvents` 事件组和各种绑定也在这一步收集。
3. **运行时根**：`NekoRuntimeRoot` 为每个 Script Type 建一个 `ScriptManager`，并持有共享的 Graal `Engine`、沙盒工厂和模块缓存。
4. **编译**：`ScriptManager` 发现 `recipes.py`，按扩展名交给 `PythonToJsCompiler`，得到 JavaScript 和源码映射。
5. **执行**：沙盒里的 GraalJS Context 执行这段代码。`ServerEvents.recipes` 是一个 `EventBusJS`，调用它只是登记回调。
6. **事件派发**：服务端加载配方时，平台侧的 `RecipeManagerMixin` 触发 `ServerEvents.recipes`，回调拿到 `RecipeEventJS`，`event.shapeless` 生成配方。

`/nekojs reload` 只重做第 4 到第 6 步：建一套新的脚本代，成功后替换旧的。第 2 步的插件运行时不会重建。

<a id="wiki-section-3"></a>
## 源码目录

```text
NekoJS/
├── common/                  # 引擎层，不依赖 Minecraft 和加载器
│   └── src/main/java/com/tkisor/nekojs/
│       ├── api/             #   公开契约：插件接口、适配器、事件、catalog
│       ├── core/            #   Graal Context、编译器、模块、Node shim、插件引导
│       ├── script/          #   脚本生命周期：ScriptManager、reload
│       ├── probe/           #   类型声明生成
│       ├── eventbus/        #   事件总线实现
│       ├── bindings/        #   与 Minecraft 无关的绑定
│       └── platform/        #   平台抽象（IPlatform）
├── common-api-processor/    # 注解处理器：编译期检查 spec 覆盖
├── src/                     # 版本树：所有 Minecraft 节点共用的一份源码
│   ├── main/java/           #   NeoForge 与共享代码
│   ├── main/resources*/     #   AT、mixin、interface injection（按 26.x / 1.21.1 分层）
│   └── fabric/              #   Fabric 专用代码和资源，不经 Stonecutter 预处理
├── versions/<节点>/          # 每个节点的参数和专属源码
├── addons/nekujs/           # 独立发布的 NeoForge 1.21.1 兼容 addon
└── buildSrc/                # 节点构建约定
```

两条边界由构建强制：

- `common`（包括 `com.tkisor.nekojs.api.*`）不能 import Minecraft 或加载器的类，可以直接用 Graal。`guardLint` 和 `:common:checkCommonIsolation` 都会检查。
- 和 Minecraft 打交道的代码放在版本树。版本差异优先用版本门面（如 `McPlatformCompat`、`McClientCompat`）或节点专属文件，不在业务代码里写内联版本守卫。

节点、守卫和构建任务的细节见 [构建系统](build-system_cn)。

<a id="wiki-section-4"></a>
## 启动顺序

两个加载器入口（NeoForge 的 `NekoJSMod`、Fabric 的 `NekoJSFabricMod`）先各自完成插件发现，再调用共用的 `NekoRuntimeAssembly`。它按固定顺序装配：

1. `NekoPluginRuntime.bootstrapOwned`：引导插件运行时。
2. 触发插件的 `init` 钩子。
3. 接上加载器自己的事件面（NeoForge 的 `IEventBus` 或 Fabric callback）。
4. 建引擎上下文：编译器表、`SandboxConfig`、`ClassFilter`、错误跟踪器、`NekoCoreContext`（含共享 `Engine`）、`NekoSandboxFactory`。
5. 建模块管线和模块缓存，沙盒工厂与运行时根共用同一份。
6. 构造 `NekoRuntimeRoot`。
7. 为每个自动加载的 Script Type 建 `ScriptManager` 并发现脚本。
8. 运行启动脚本，然后触发 `initStartup` 钩子。

<a id="wiki-plugin-runtime"></a>
<a id="wiki-section-5"></a>
## 插件运行时

内置功能和第三方插件走同一套扩展机制。下面是内置的 `PythonTranspilerPlugin`，它把 `.py` 注册成一种脚本语言：

```java
@RegisterNekoJSPlugin
public final class PythonTranspilerPlugin implements NekoJSPlugin, ScriptCompilersPoint.Contributor {

    @Override
    public void registerScriptCompilers(ScriptCompilerRegistry registry) {
        registry.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
    }
}
```

`registerScriptCompilers` 是 `NekoJSPlugin` 上的一个 Plugin Hook。它背后是扩展点 `nekojs:script_compilers`，定义在 `ScriptCompilersPoint`。插件可以直接覆写 `NekoJSPlugin` 的钩子，也可以显式实现 `ScriptCompilersPoint.Contributor`，两种写法由同一个扩展点收集，效果相同。

<a id="wiki-section-6"></a>
### 插件发现

| 加载器 | 发现方式 |
|---|---|
| NeoForge | `NeoForgePluginLoader` 扫描 FML 注解数据，找出所有 `@RegisterNekoJSPlugin` 类 |
| Fabric | `FabricPluginLoader` 先登记一份内置插件清单，再读取 `fabric.mod.json` 里声明的 `nekojs` entrypoint |

Fabric 没有全类路径注解扫描。新增一个没有平台守卫的内置插件时，要同时把它加进 `FabricPluginLoader` 的内置清单，否则 Fabric 上不会加载。

找到的类交给 `NekoJSBasePluginManager`：按 `clientOnly` 和 `requiredMods` 过滤，实例化，再按 `priority` 从高到低排序（默认 1000）。

<a id="wiki-section-7"></a>
### 引导过程

`NekoPluginBootstrap` 以扩展点为单位工作。对每个点：建累积器，依次让每个插件贡献，执行 finisher 得到产物并冻结，然后进入下一个点。

- **内置点先注册**。`NekoBuiltinPointsPlugin` 是全部内置扩展点的总清单，由 bootstrap 直接构造并最先执行，不受插件 priority 影响。新增内置扩展点 = 一个自包含的 `XxxPoint` 文件 + 这份清单里的一行。
- **点之间可以有依赖**。后面的点可以读取前面点的产物。bootstrap 冻结时按依赖做拓扑排序，有环或依赖了未注册的点会立刻报错。
- **第三方可以定义自己的扩展点**。实现 `NekoPluginExtensionProvider`，在 `registerPluginExtensionPoints` 里注册 `NekoPluginExtensionPoint`，返回的 handle 在引导完成后取产物。写法见 [插件开发](plugin-development_cn)。

所有产物汇总到 `NekoPluginRuntime`。它在进程里只引导一次，之后只读。普通 reload 只切换脚本代，不会重新引导插件运行时，所以插件的产物（绑定、适配器、事件组、编译器等）在 reload 之间保持不变。

给 `NekoJSPlugin` 加新的收集型钩子时，必须同时提供配对的 Point。`PluginHookPairingTest` 会检查这一点。

<a id="wiki-section-8"></a>
## 脚本运行时与 reload

`NekoRuntimeRoot` 是运行时唯一的所有者，只由加载器入口和命令、reload、关闭这些边界持有。它对外提供 `reload`、`reloadFile`、`runTests`、`errors`、`close` 等生命周期方法，没有全局 `get()`，也不应该往普通业务类里传。

资源按生命周期分两类：

| 归属 | 内容 | 什么时候释放 |
|---|---|---|
| 运行时根 | 插件运行时、`ScriptManager`、共享 `Engine`、模块缓存 | 游戏关闭 |
| 脚本代 | 某一次加载的 GraalJS Context、监听器、计时器等 | 被新的一代替换，或加载失败 |

完整 reload 是事务式的：先建一套候选代，所有阶段通过后在拥有线程上一次性切换；任何一步失败，候选代的资源全部关闭，当前代保持不变。启动脚本不走这个流程。

几个核心类：

- **`NekoSharedEngine`**：进程级共享的 Graal `Engine`。
- **`NekoCoreContext`**：不可变 record，打包 engine、`SandboxConfig`、`ClassFilter` 和错误跟踪器。
- **`NekoSandboxFactory`**：配置 `HostAccess`、类过滤、受限文件系统和 JS 选项，创建 Context。脚本的 `console` 输出接到对应 Script Type 的日志。
- **`NekoSharedHostAccess`**：构建 `HostAccess`，把类型转换注册为 `targetTypeMapping`。
- **`ClassFilter`**：禁止脚本访问的高危 Java 类，例如 `Runtime`、`Process`、`ClassLoader`、反射、`java.io`、`java.nio`、`java.net`。

<a id="wiki-section-9"></a>
## 编译前端与模块

每种脚本语言是一个 `IScriptCompiler`，经 `nekojs:script_compilers` 扩展点注册，按扩展名选择。

| 前端 | 类 | 做什么 |
|---|---|---|
| TypeScript | `NekoTypeScriptCompiler` | 擦除类型注解、`type` / `interface`、泛型、`as` / `satisfies`；`enum` 和 `namespace` 转成 IIFE。不做类型检查 |
| JSX | `NekoJsxCompiler` | 支持 classic 工厂和导入 `nekojs/jsx-runtime` 的自动模式 |
| Python | `PythonToJsCompiler` | lexer → parser → emitter，把 Python 子集转成 JavaScript 并生成源码映射；由 `PythonTranspilerPlugin` 注册 |

模块相关：

- **ESM**：`core/module/esm/` 有自己的 lexer、parser、linker 和求值器。
- **模块失效**：`ModuleReloadCoordinator` 让受影响模块在当前代里的缓存、链接和依赖记录失效。它不是文件监视器，保存文件不会触发任何东西。
- **`java:` 模块**：`NekoModuleResolver` 把 `java:包/路径` 解析成合成的 ESM 模块。
- **Node 兼容层**：`core/node/` 用 Java 加 JS 实现 `fs`、`path`、`buffer`、`crypto`、`process`、`timers`、`os` 等模块。见 [Node.js 兼容](nodejs-compatibility_cn)。

<a id="wiki-event-system"></a>
<a id="wiki-section-10"></a>
## 事件系统

接口在 `api/event/`，实现在 `eventbus/`。

- **`EventGroup`**：一个命名的事件组，例如 `ServerEvents`。用 `group.server("name", EventClass)`、`client(...)`、`startup(...)` 声明事件。
- **`EventBus` / `CancellableEventBus` / `DispatchEventBus`**：三种总线。dispatch 版按 key 分发，例如按物品或方块 id。
- **`EventBusJS`**：面向脚本的 `ProxyExecutable`，让脚本可以写 `ServerEvents.recipes(cb)`。
- **加载器桥**：`EventBusForgeBridge` 把 NeoForge `IEventBus` 上的事件接到 `EventBusJS`；Fabric 用 `FabricEventBusBridge` 接 Fabric callback。

自定义事件的写法见 [事件扩展](event-extensions_cn)。

<a id="wiki-section-11"></a>
## 配方系统

- **`RecipeTypeDefinition`**：配方类型的 schema（namespace、type、字段、构造方式）。
- **`RecipeTypeDefinitionStorage`**：把四层定义合并，优先级从低到高：自动发现 < 插件覆盖（`registerRecipeSchemas`）< 数据包 JSON < 脚本注册（`event.registerSchema`）。
- **`RecipeSchemaAutoDiscovery` + 各版本的 `MinecraftRecipeSchemaScanner`**：反射 `RecipeSerializer.codec()` 自动生成 schema。
- **`RecipeNamespaceProxy` / `SchemaRecipeBuilder` / `FallbackNamespaceProxy`**：脚本侧 `event.recipes.<ns>.<type>(...)` 的代理层。依次尝试 handler 方法、schema，最后用原始 JSON 兜底。
- **`RecipeManagerMixin`**：缓存原始配方 JSON，并把脚本配方应用进去。

<a id="wiki-section-12"></a>
## Probe

`ProbeCoordinator` 做一次共享的类收集，把结果交给每个已注册的 `ProbeBackend`，各自渲染声明文件。backend 通过 `ProbeBackendRegistry` 注册，配置来自 `probe.toml`（`ProbeConfig` / `ProbeConfigLoader`），写盘统一走 `ProbeOutputCommitter`。内置 TypeScript 和 Python 两个 backend。见 [Probe 类型生成](probe-type-generation_cn)。

<a id="wiki-section-13"></a>
## 平台抽象

引擎层通过几个接口拿到平台信息。它们不是 Java `ServiceLoader`，而是平台入口启动时手动初始化一次，例如 `Platform.init(new NeoForgePlatform())`。

| 接口 | 位置 | 作用 |
|---|---|---|
| `IPlatform` + `Platform` | `common/.../platform/` | 环境、版本、已加载模组、平台能力、probe 默认扫描包等 |
| `NekoIdCompat` | `common/.../platform/` | 在 `NekoId` 和平台原生 ID（`Identifier` / `ResourceLocation`）之间转换 |
| `NekoCatalogPlatformProvider` | `common/.../api/catalog/` | 给 probe 提供注册表字面量、配方 namespace handler 等；每个平台实现一个 |

`IPlatform.capabilities()` 返回 `PlatformCapability` 集合，目前有 11 个值：`TAGS`、`RESOURCE_PACKS`、`CLIENT_SCREENS`、`CLIENT_KEYBINDS`、`CLIENT_RENDERERS`、`RECIPE_HOT_RELOAD`、`RECIPE_SCHEMA_AWARE`、`NETWORK_CUSTOM_CHANNEL`、`NBT_BINARY_IO`、`RECIPE_VIEWER`、`CAPABILITIES`。脚本通过全局 `Platform.capabilities()` 读到的是小写、用连字符的字符串列表，例如 `"recipe-viewer"`。这些值只用于让脚本判断某个特性能不能用。引擎内部按 `IPlatform` 的默认方法裁剪功能（没实现的平台返回空或 `null`），不看这些标志。

spec 的平台范围是另一套机制：用 `@PlatformAvailability(Scope)` 声明，取值 `ALL`、`NF_ONLY`（26.x 和 1.21.1）、`NF26_ONLY`、`CR_ONLY`（遗留值，不对应当前任何节点）。NeoForge 节点编译时接入 `SpecCoverageProcessor`，参数 `-Anekojs.platform=nf26|nf121`。Fabric 节点没有接这个注解处理器，改由 `platformGateTest` 检查覆盖。

<a id="wiki-section-14"></a>
## 给原版类加接口

| 节点 | 做法 |
|---|---|
| NeoForge 26.x | `interfaceInjectionData`（`nekojs.interface_injection.json`）加上一个空的 `@Mixin` 类 `implements XxxExtension` |
| NeoForge 1.21.1 | 只用 Mixin `implements`，这个版本用不了 interface injection |
| Fabric | 只用 Mixin `implements`，mixin 配置在 `src/fabric/resources` |

例如 `MixinBlock` 是一个空的 `@Mixin(Block.class)` 抽象类，只声明 `implements BlockExtension`。

<a id="wiki-section-15"></a>
## 设计原则

1. **数据优先**：配方和数据尽量用 JSON，脚本只写逻辑。
2. **能直接暴露原版类就不包装**：少造 wrapper。
3. **不让 Graal `Value` 泄漏**：API 边界用具体 Java 类型，`Value` 只出现在互操作层。
4. **引擎层不依赖 Minecraft**：由构建期检查强制。
5. **插件即 API**：内置功能和第三方插件用同一套扩展点。

<a id="wiki-section-16"></a>
## 还没做完的事

- 文档注解 `@Doc`、`@Param`、`@Return`、`@Overload`、`@DeprecatedNekojs` 已实现；`@Example`、`@TypeOverride`、`@Since` 还没有。`@Since` 要等公开 API 有版本线。见 [注解体系](annotations_cn)。
- 编译器里有两个很大的手写改写器：TypeScript 擦除器（`NekoTypeScriptCompiler` 里的 `Eraser`）和 `PythonEmitter`。拆分只为可读性，不影响 API。
- 公开 API 还没有 SemVer 冻结线。

<a id="wiki-section-17"></a>
## 接下来

- [插件开发](plugin-development_cn)：写一个 NekoJS 插件 mod。
- [构建系统](build-system_cn)：节点、守卫、构建任务和 CI。
- [类型适配器](type-adapters_cn)、[事件扩展](event-extensions_cn)、[Probe 类型生成](probe-type-generation_cn)、[注解体系](annotations_cn)。

<!-- wiki-nav -->

---

[上一篇: 注解体系](annotations_cn) · [目录](Home) · [下一篇: Probe 类型生成](probe-type-generation_cn)
