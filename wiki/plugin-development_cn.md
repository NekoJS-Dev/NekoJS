<!-- wiki-page: plugin-development; locale: cn -->

> **中文** · [English](plugin-development_us)

<a id="wiki-section-1"></a>
# 插件开发

NekoJS 插件是一个普通的 Minecraft 模组，它用 Java 给脚本添加新功能：新的全局绑定、事件组、类型适配器、配方命名空间、补全声明等。

NekoJS 自己的内置功能也是用同一套插件接口实现的，所以插件能做的事情和内置功能一样多。

<a id="wiki-section-2"></a>
## 第一个插件

下面的插件给所有脚本加一个全局绑定 `MyHelper`。

```java
package com.example.myaddon;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;

@RegisterNekoJSPlugin
public class MyAddonPlugin implements NekoJSPlugin {
    @Override
    public void registerBinding(BindingRegistry registry) {
        registry.register("MyHelper", new MyHelperJS());
    }
}
```

```java
package com.example.myaddon;

import com.tkisor.nekojs.api.annotation.Doc;

public class MyHelperJS {
    @Doc("Adds two integers.")
    public int add(int a, int b) {
        return a + b;
    }
}
```

在脚本里使用：

```javascript
// server_scripts/test.js
console.info(MyHelper.add(1, 2)) // 3
```

验证步骤：

1. 把插件模组、NekoJS 和 Graal 一起放进 `mods/`，启动游戏。
2. 进入世界，查看 `logs/nekojs/server.log` 是否输出 `3`。
3. 执行 `/nekojs probe`，在 VS Code 里输入 `MyHelper.`，应能看到 `add` 和它的说明。

<a id="wiki-section-3"></a>
## 让 NekoJS 发现插件

插件类必须实现 `NekoJSPlugin`，是具体类（不是接口或抽象类），并且有公开的无参构造器。不同加载器的发现方式不同：

| 加载器 | 怎么注册 |
|---|---|
| NeoForge | 在类上标注 `@RegisterNekoJSPlugin`，NekoJS 扫描所有模组找到它 |
| Fabric | 在 `fabric.mod.json` 里声明 `nekojs` entrypoint。只有注解不会被发现 |

Fabric 的 entrypoint 片段（合并进你的 `fabric.mod.json`）：

```json
{
  "entrypoints": {
    "nekojs": ["com.example.myaddon.MyAddonPlugin"]
  }
}
```

两个平台都支持时，注解和 entrypoint 都写上。Fabric 上如果类也有注解，会读取注解里的 `priority`、`requiredMods`、`clientOnly`；没有注解时优先级是 1000。

包名、类名和一个模组里有几个插件类都没有限制。

<a id="wiki-section-4"></a>
### @RegisterNekoJSPlugin 的参数

```java
@RegisterNekoJSPlugin(
    priority = 1000,              // 越大越先运行，默认 1000
    clientOnly = false,           // true 时专用服务器上不加载
    requiredMods = { "create" }   // 列出的模组都存在才加载
)
```

| 参数 | 默认值 | 作用 |
|---|---|---|
| `priority` | `1000` | 插件运行顺序，大的先运行。内置核心插件用 `NekoJSPlugin.CORE_PRIORITY`（`Integer.MAX_VALUE`），总是最先 |
| `clientOnly` | `false` | 只在客户端加载，专用服务器上跳过 |
| `requiredMods` | `{}` | 列出的模组全部存在时才加载，适合写联动插件 |

priority 相同时，按插件类的完整类名排序。这个顺序是稳定的，但改类名就会变，不要依赖它。

插件被跳过（缺少前置模组或 `clientOnly`）时只记 debug 日志。插件类无法实例化时会记 error 日志 `Failed to instantiate plugin ...`。

<a id="wiki-section-5"></a>
## 钩子在什么时候运行

`NekoJSPlugin` 的方法都有默认空实现，只覆写需要的就行。钩子分两类：

- **注册钩子**（`registerXxx`）：在游戏启动时 NekoJS 初始化插件期间各调用一次，把内容登记进 NekoJS。只能在钩子运行期间往传进来的 registry 里写；不要把 registry 存起来以后用，之后写入的内容不会生效，部分 registry 还会抛 `IllegalStateException`。
- **回调钩子**（`init`、`afterScriptsLoaded` 等）：在对应时刻被调用，覆写即生效。

普通的 `/nekojs reload` 不会重新调用注册钩子。修改插件后要重启游戏。

<a id="wiki-section-6"></a>
## 钩子一览

<a id="wiki-section-7"></a>
### 绑定、类型和模块

| 钩子 | 作用 |
|---|---|
| `registerBinding(BindingRegistry)` | 注册全局绑定，见下文 |
| `registerAdapters(JSTypeAdapterRegistry)` | 注册类型适配器，让 Java 参数接受字符串、对象等多种 JS 输入。见 [类型适配器](type-adapters_cn) |
| `registerTypeDocs(TypeDocsRegister)` | 补充或替换补全声明，见下文 |
| `registerNodeModules(NodeModuleRegister)` | 注册可以 `require('id')` 的 JS 模块 |
| `registerNodeTypeDocs(TypeDocsRegister)` | 给这些模块提供 `declare module` 声明 |

<a id="wiki-section-8"></a>
### 事件

| 钩子 | 作用 |
|---|---|
| `registerEvents(EventGroupRegistry)` | 注册事件组（服务端和通用） |
| `registerClientEvents(EventGroupRegistry)` | 注册只在客户端存在的事件组 |

写法见 [事件扩展](event-extensions_cn)。

<a id="wiki-section-9"></a>
### 配方

| 钩子 | 作用 |
|---|---|
| `registerRecipeNamespaces(RecipeNamespaceRegister)` | 注册 `event.recipes.<namespace>.<method>(...)` 的 Java 实现 |
| `registerRecipeSchemas(RecipeSchemaRegister)` | 注册或修正配方类型的字段定义 |
| `registerRecipeLifecycleHooks(RecipeLifecycleRegister)` | 注册配方加载前后的回调；默认实现会接上 `beforeRecipeLoading` 和 `afterRecipes` |

配方 schema 的优先级从高到低：脚本里的 `event.registerSchema` > 数据驱动 > 插件 > 自动扫描。

<a id="wiki-section-10"></a>
### 脚本语言、属性和工作区

| 钩子 | 作用 |
|---|---|
| `registerScriptCompilers(ScriptCompilerRegistry)` | 注册新的脚本语言或文件扩展名 |
| `registerScriptProperty(ScriptPropertyRegistry)` | 注册新的脚本属性（`// key: value`），见 [脚本属性](script-properties_cn) |
| `registerProbeBackends(ProbeBackendRegistry)` | 注册新的补全声明生成器，见 [Probe 类型生成](probe-type-generation_cn) |
| `modifyWorkspaceConfig(JSConfigModel, String)` | 在写出各脚本目录的 `jsconfig.json` 前修改它 |
| `registerApiSurface(ApiContributionRegistry)` | 提供 API 表面元数据 |

<a id="wiki-section-11"></a>
### 生命周期回调

| 方法 | 什么时候调用 |
|---|---|
| `init()` | 插件初始化完成后，启动脚本加载之前 |
| `initStartup()` | 启动脚本加载完成后 |
| `afterInit()` | 所有模组初始化完成后（NeoForge 的 `FMLLoadCompleteEvent`） |
| `beforeScriptsLoaded(ScriptType)` | 某类脚本完整加载或完整重载之前；单文件重载不触发 |
| `afterScriptsLoaded(ScriptType)` | 某类脚本完整加载或完整重载之后；单文件重载不触发 |
| `beforeRecipeLoading(RecipeLifecycleContext)` | 配方加载前，可以修改原始配方 JSON |
| `afterRecipes(RecipeLifecycleContext)` | 配方全部加载完成后 |

如果覆写了 `registerLifecycleHooks` 或 `registerRecipeLifecycleHooks` 却没有调用默认实现，对应的便捷回调就不会再被调用。

<a id="wiki-section-12"></a>
### 数据和资源生成

| 方法 | 作用 |
|---|---|
| `attachServerData` / `attachLevelData` / `attachPlayerData` | 给服务器、维度、玩家挂只存在内存里的自定义数据，不保存 |
| `generateData(DataGeneratorJS)` | 生成数据包内容，在脚本的 `ServerEvents.generateData` 之前运行，和脚本共用同一个 generator |
| `generateAssets(DataGeneratorJS)` | 生成资源包内容，在脚本的 `ClientEvents.generateAssets` 之前运行 |
| `generateLang(LangGeneratorJS)` | 生成语言文件条目 |
| `generatedLangs()` | 声明要为哪些语言调用 `generateLang`，默认 `en_us` |

`generateLang` 会对本轮涉及的**每种**语言各调用一次，包括其他插件和脚本要求的语言。只生成一种语言时，在回调里先判断 `generator.getLang()`：

```java
@Override
public void generateLang(LangGeneratorJS generator) {
    if (!"zh_cn".equals(generator.getLang())) return;
    // add zh_cn entries here
}
```

<a id="wiki-section-13"></a>
## 注册全局绑定

`registerBinding` 对每种脚本类型各调用一次，传入的 registry 对应其中一种类型。

```java
@Override
public void registerBinding(BindingRegistry registry) {
    // 所有脚本类型都可用
    registry.register("MyHelper", new MyHelperJS());

    // 只在客户端脚本里可用
    registry.register(ScriptType.CLIENT, "MyClientHelper", new MyClientHelperJS());

    // 绑定一个 Java 类：脚本里可以访问静态成员，也可以 new
    registry.register("MyMath", MyMath.class);
}
```

- 值可以是对象实例（脚本调用它的公开方法）或 `Class`（脚本访问静态成员）。
- **同名绑定先注册的生效。** 后注册的会被忽略，`register` 返回 `false`，日志出现 `NEKO-4001` 警告。内置插件优先级最高，所以不能用同名绑定替换 `Item`、`Ingredient` 这类内置绑定。
- 名字没有格式要求，但它是脚本的全局变量名，建议带上模组特征以免冲突。
- 专用服务器上不会收集 `CLIENT` 类型的绑定。

<a id="wiki-section-14"></a>
### 导出为 API 模块

除了全局变量，也可以把绑定只作为模块导出，让脚本用 `import` 引入：

```java
registry.register(Binding.of("MyHelper", new MyHelperJS()), exposure -> exposure.versioned());
```

```javascript
import { MyHelper } from 'nekojs/api/version/myaddon'
```

- 模块路径里的 `myaddon` 是插件所属模组的 ID，由加载器提供。
- `versioned()` 导出到 `nekojs/api/version/<modid>`；`stable()` 导出到 `nekojs/api/stable/<modid>`。必须且只能选一个。
- `stable()` 会检查公开类型里有没有 Minecraft 或加载器的类，有就拒绝。需要暴露原生类型的类标注 `@ScriptNativeBridge` 才能通过。
- 这种写法**不会**同时创建全局变量。
- 缺少模组归属、导出名不合法或重复导出，都会报 `NEKO-4057`。

模块的用法见 [模块系统](module-system_cn#wiki-api-modules)。

<a id="wiki-section-15"></a>
### 绑定的值类型

`Binding.of(name, value)` 根据值本身推断类型。值是代理对象（如 `ProxyObject`）时，Java 反射看不到它的成员，加载前的成员检查和补全生成都会出错。这时用三参数形式指明真正承载成员的类：

```java
registry.register(Binding.of("MyBlock", new DelegatingBinding(
        new MyBlockHelperJS(),                          // 提供 of / empty
        net.minecraft.world.level.block.Block.class,    // 其余静态成员委托给它
        Set.of("of", "empty")
), MyBlockHelperJS.class));
```

`DelegatingBinding` 先在 helper 上查找列出的成员，其余转给目标类的静态成员。内置的 `Item` 绑定就是这样实现的。它只是代理对象，`Java.type(...)` 拿到的原生类上没有这些扩展方法。

`Binding.close(ScriptType)` 在该类脚本运行环境被重置或关闭时调用，不是每次 reload 都调用。

<a id="wiki-section-16"></a>
## 补全和文档

写插件时，有两种方式让绑定在编辑器里有提示和说明：

1. **注解**：在类和方法上标 `@Doc`、`@Param`、`@Return` 等。见 [注解](annotations_cn)。
2. **`registerTypeDocs`**：用代码登记，适合注解写不了的情况。

给全局绑定写说明，或指定它在声明里的类型：

```java
@Override
public void registerTypeDocs(TypeDocsRegister docs) {
    docs.register(TypeDocCatalogEntry.binding(
            "MyHelper",            // binding name
            null,                  // type override; null uses the reflected type
            "Math helpers for my addon.",
            List.of("MyHelper.add(1, 2)")));
}
```

`TypeDocsRegister` 还有几种更进阶的登记方式：

| 方法 | 用途 |
|---|---|
| `register(TypeDocCatalogEntry)` | 全局绑定的说明、示例和类型覆盖 |
| `registerManualDeclaration(ManualDeclarationCatalogEntry)` | 手写一段全局 TypeScript 声明 |
| `registerClassDeclaration(ClassDeclarationCatalogEntry)` | 用手写声明替换某个 Java 类生成的 TypeScript 声明 |
| `registerBeanAliasExclusion(...)` / `registerBeanMethodProjection(...)` | 调整 Bean 属性在声明里的样子 |

手写声明里用到 Java 类型时，要用 `withReferences` 显式指明：

```java
docs.registerManualDeclaration(ManualDeclarationCatalogEntry.of(
        "myaddon:stack", "type MyStack = StackValue;", "A script-visible item stack.", List.of())
        .withReferences(Map.of("StackValue",
                ManualDeclarationCatalogEntry.TypeReference.host(ItemStack.class))));
```

`TypeReference.host(...)` 引用 Java 类型本身；`TypeReference.input(...)` 引用这个类型的输入别名（适配器接受的所有输入形式）。

替换类声明时，Java 反射表达不了的泛型关系可以手写：

```java
docs.registerClassDeclaration(ClassDeclarationCatalogEntry.of(
        RpcBuilder.class,
        """
        export class $RpcBuilder<T extends Record<string, 'string' | 'int'> = {}> {
            schema<const S extends Record<string, 'string' | 'int'>>(value: S): $RpcBuilder<S>;
            fn(callback: (args: { [K in keyof T]: T[K] extends 'string' ? string : number }) => void): void;
        }
        """));
```

写类声明时注意：

- 只写类体，不要加 `declare module`。类名保持生成器的格式 `$JavaName`（嵌套类是 `$Outer$Inner`）。
- 不要重复定义 `$JavaName_` 这样的输入别名，生成器会自动提供。
- 声明里用到其他 Java 类型时，用带 `Set<Class<?>>` 的 `of` 重载列出依赖。依赖被隐藏或无法生成时报 `NEKO-4033`。
- 同一个类有多份替换时，优先级最高的生效；优先级相同但内容不同，报 `NEKO-4032`。
- 替换只影响 TypeScript 声明。声明必须和 Java 的实际行为一致，NekoJS 不会检查。

内置插件的手写声明集中在 `NekoCommonManualDeclarations`，可以作为参考。

<a id="wiki-section-17"></a>
## 自定义扩展点

如果你的插件要让**其他插件**往它这里登记内容（比如你提供一个机器框架，让别的模组注册机器），可以定义自己的扩展点。它和 NekoJS 内置钩子用的是同一套机制。

```java
public final class MachinesPoint {
    public static final String ID = "myaddon:machines";

    // 其他插件实现这个接口来贡献内容
    public interface Contributor extends NekoJSPlugin {
        default void registerMachines(MachineCollector collector) {}
    }

    public static final NekoPluginExtensionPoint<Contributor, MachineCollector, List<Machine>> POINT =
            NekoPluginExtensionPoint.<Contributor, MachineCollector, List<Machine>>builder(ID, Contributor.class)
                    .merge(MergePolicy.append())
                    .initializer(ctx -> new MachineCollector())
                    .collector(Contributor::registerMachines)
                    .finish(MachineCollector::snapshot)
                    .build();
}
```

定义扩展点的插件同时实现 `NekoPluginExtensionProvider`，在 `registerPluginExtensionPoints` 里注册它：

```java
@RegisterNekoJSPlugin
public class MyAddonPlugin implements NekoPluginExtensionProvider {
    private static NekoPluginExtensionHandle<List<Machine>> machines;

    @Override
    public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {
        machines = registry.register(MachinesPoint.POINT);
    }

    public static List<Machine> machines() {
        return machines.result();   // available after plugin bootstrap
    }
}
```

规则：

- `initializer`、`collector`、`merge`、`finish` 四个都必须设置，缺一个 `build()` 就抛 `IllegalStateException`。
- `merge` 没有默认值，要自己选冲突策略：`append()`、`firstWin()`、`overrideWarn()`、`failFast()`。
- ID 必须全局唯一，和内置的 `nekojs:*` 也不能重复，重复时抛 `IllegalArgumentException`。建议用 `<你的 modid>:<名字>`。
- 只能在 `registerPluginExtensionPoints` 里注册扩展点，之后注册抛 `IllegalStateException`。
- 需要用到另一个扩展点的结果时，用 `dependsOn(...)` 声明依赖。
- `MachineCollector`、`Machine` 是你自己的类型，不是 NekoJS API。

每个内置钩子都对应 `com.tkisor.nekojs.core.plugin` 包里的一个 `XxxPoint`。直接覆写 `NekoJSPlugin` 的钩子，和实现对应的 `XxxPoint.Contributor` 接口，效果完全相同。

<a id="wiki-section-18"></a>
## 跨平台插件

- 尽量使用 NekoJS 的平台无关类型，例如用 `NekoId` 代替 Minecraft 的 `Identifier` / `ResourceLocation`。
- 运行时功能差异用 `Platform.capabilities()` 判断，不要按加载器名硬编码。
- 只适用于某个平台的插件用 `requiredMods` 或 `clientOnly` 控制加载。
- 如果要在多个平台的 Extension 接口里实现同一组方法，可以使用 Spec 接口模式，见 [注解](annotations_cn)。

<a id="wiki-section-19"></a>
## Gradle 依赖

NekoJS 目前没有发布 Maven 制品，插件直接依赖 NekoJS 的发布 jar。Graal 从 CurseMaven 获取：

```groovy
repositories {
    maven { url = "https://cursemaven.com" }
}

dependencies {
    // Graal 25.1.3.7. File 8762962 is the NeoForge build; use 8762963 for Fabric.
    compileOnly "curse.maven:graal-1504336:8762962"
    // The NekoJS release jar for your Minecraft version and loader.
    compileOnly files("libs/nekojs-<version>.jar")
}
```

NekoJS 的公开 API 目前没有语义化版本保证。升级 NekoJS 后请重新编译并测试插件。

<a id="wiki-section-20"></a>
## 参考：内置插件

内置功能分布在几个插件里，适合作为参考：

- `common/.../core/plugin/NekoCommonBuiltinPlugin.java`：平台无关的绑定、编译器、脚本属性和类型文档。
- `src/main/java/.../core/NekoJSCorePlugin.java`：Minecraft 相关的绑定、适配器和事件。
- `NekoBuiltinPointsPlugin`：注册所有内置扩展点。
- Fabric 另有 `FabricCorePlugin`。

下一步：

- [类型适配器](type-adapters_cn)：让 Java 方法接受多种 JS 输入。
- [事件扩展](event-extensions_cn)：注册自定义事件组。
- [注解](annotations_cn)：控制成员名、可见性和补全说明。
- [Probe 类型生成](probe-type-generation_cn)：绑定怎样生成到 `.d.ts`。

<!-- wiki-nav -->

---

[上一篇: Node.js 兼容](nodejs-compatibility_cn) · [目录](Home) · [下一篇: 类型适配器](type-adapters_cn)
