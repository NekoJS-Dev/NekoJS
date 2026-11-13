<!-- wiki-page: plugin-development; locale: cn -->

> **中文** · [English](plugin-development_us)

<a id="wiki-section-1"></a>
# 插件开发指南

本页面向想要通过 Java 扩展 NekoJS 功能的开发者。

<a id="wiki-section-2"></a>
## 🚀 开发环境与入口

插件本质上是 Minecraft 模组。在 `build.gradle` 中依赖对应版本与加载器的 NekoJS 发布 jar。两个加载器均要求 GraalMC 25.5.0 或更高；NeoForge 使用 Modrinth Maven，Fabric 可使用 CurseMaven：

```groovy
repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = "https://api.modrinth.com/maven"
            }
        }
        filter { includeGroup("maven.modrinth") }
    }
    // Used only by the Fabric alternative below.
    maven {
        url = "https://cursemaven.com"
        content { includeGroup("curse.maven") }
    }
}
dependencies {
    // NeoForge: GraalMC 25.5.0.
    compileOnly "maven.modrinth:bjcrgPcA:O8qtoV6d"
    // Fabric: replace the NeoForge dependency with this CurseForge file.
    // compileOnly "curse.maven:graal-1504336:9555615"
    compileOnly files("libs/nekojs-<version>.jar")
}
```

使用 `@RegisterNekoJSPlugin` 注解标记你的主类。

<a id="wiki-section-3"></a>
## 🏗 插件生命周期

插件运行主要分三阶段：

1. **Bootstrap (引导)**：加载模组。
2. **Register (注册)**：通过 `registerPluginExtensionPoints` 注册你的扩展点。
3. **Callback (回调)**：执行 `init` 或 `afterScriptsLoaded` 等生命周期钩子。

<a id="wiki-section-4"></a>
## 🔌 核心扩展机制

插件通过“扩展点（Extension Point）”与 NekoJS 核心交互：

```java
@RegisterNekoJSPlugin
public class MyAddonPlugin implements NekoPluginExtensionProvider {
    private static NekoPluginExtensionHandle<List<Machine>> machines;

    @Override
    public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {
        // 注册扩展点
        machines = registry.register(MachinesPoint.POINT);
    }

    public static List<Machine> machines() {
        return machines.result(); // 插件引导后可用
    }
}
```

**开发守则：**
- **不要缓存 Registry**：只能在 `registerPluginExtensionPoints` 期间向传入的 `registry` 写入，之后写入无效且会报错。
- **ID 全局唯一**：建议命名为 `<模组ID>:<名称>`。
- **冲突策略**：注册时必须指定合并策略（如 `append()` 或 `failFast()`）。

<a id="wiki-section-5"></a>
## 🛠 开发进阶

<a id="wiki-section-6"></a>
### 1. 跨平台建议
- 尽量使用 NekoJS 的平台无关类型（如 `NekoId`）而非原版 `ResourceLocation`。
- 功能差异请使用 `Platform.capabilities()` 判断，不要按加载器名硬编码。

<a id="wiki-section-7"></a>
### 2. 内置插件参考
想了解 NekoJS 是怎么实现内置功能的，可查阅源码中对应的 `...BuiltinPlugin.java` 或 `NekoBuiltinPointsPlugin`。

<!-- wiki-nav -->

---

[上一篇: Node.js 兼容](nodejs-compatibility_cn) · [目录](Home) · [下一篇: 类型适配器](type-adapters_cn)
