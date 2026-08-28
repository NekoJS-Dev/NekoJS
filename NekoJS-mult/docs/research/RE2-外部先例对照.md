# RE2 外部先例对照：扩展点依赖 · 内置扩展自包含注册 · 通用注册表派生

- 日期：2026-08-28
- 票：[#40](https://github.com/NekoJS-Dev/NekoJS/issues/40)（wayfinder:research）
- 痛点背景：[PR #37](https://github.com/NekoJS-Dev/NekoJS/pull/37) 及其开发文档 [ZZZank/NekoJS `genericregistry` 分支 `docs/MINECRAFT_REGISTRY.md`](https://github.com/ZZZank/NekoJS/blob/genericregistry/docs/MINECRAFT_REGISTRY.md)
- 服务对象：架构决策票 **E1（扩展点模型）** 与 **R1（通用注册表模型）**
- 代码基线：master `621f465`（W7 wrap-up）；路径均相对 `NekoJS-mult/`。注意：master 已落地 Collector 形态的 `NekoPluginExtensionPoint`（见 §0），本报告同时承担"验证既有方向"与"为 R1 提供蓝本"两个职能
- 方法：结论全部来自一手来源——上游项目源码（kube-mods/kubejs、本地 Gradle 缓存中的 NeoForge 26.1.2.71 sources）、官方规范/文档（OSGi SCR、Spring Framework、IntelliJ Platform SDK、Oracle javadoc、pf4j、KubeJS/CraftTweaker 官方 wiki）。引用处均给出 URL 或源码路径

---

## 0. NekoJS 待解问题回顾（对照基准）

PR #37 文档（genericregistry 分支 `docs/MINECRAFT_REGISTRY.md` §2.2）暴露的三个问题：

| # | 问题 | 文档原意 |
|---|------|---------|
| P1 | 扩展点间依赖难表达 | `registry_object_types` 依赖 `registry_infos` 的产物，靠特设 onFinish callback "勉强可用"；一个点提前初始化资源、又干涉另一个点的初始化 |
| P2 | 插桩点太少 | 只有 `NekoJSPlugin.init()` 提供"所有点完成后"的时机 |
| P3 | 内置扩展点改核心类 | 添加内置扩展点需要修改 `NekoPluginExtensionContext`，连锁影响 runtime/bootstrap |
| P4 | 连带注册未建模 | 注册 Block 需自动带出 BlockItem；Fluid 需带出 still/flowing/方块/桶。设想是 `builder.additionalBuilders()` 或 `builder.handleAdditionalRegistry(Consumer)` |

**master 现状**（`common/src/main/java/com/tkisor/nekojs/core/plugin/`）：P1/P2 已按 Collector 形态重构落地——`NekoPluginExtensionPoint` 是 `id + pluginType + enabled 谓词 + initializer(累积器工厂) + collector + finisher` 的 record，bootstrap 按"点优先执行序"逐点收集，跨点依赖 = 在 initializer/collector 里经 `NekoPluginExtensionContext.result()` 读先注册点的已完成产物（`NekoPluginExtensionContext.java:8-9`、`NekoPluginExtensionProvider.java:24-30`）。P3 部分解决：14 个内置点的*产物贡献*已走内置插件（`NekoCommonBuiltinPlugin` 等 `@RegisterNekoJSPlugin`），但*点本身的定义*仍硬编码在 `BuiltinPluginExtensionPoints.builtIn()`。P4（连带注册）随 PR #37 的通用注册表一并待做。

---

## 一、扩展点间依赖与加载时序（对应 P1/P2，服务 E1）

### 1.1 IntelliJ Platform：依赖在插件粒度，顺序在扩展粒度，值传递靠"惰性查询"

IntelliJ 是"扩展点"这个词的出处，但它的模型与 NekoJS 需要的不同——它**不提供扩展点间的数据传递**：

- **插件级依赖**：`<depends>com.example.another-plugin</depends>`，依赖必须显式声明；可选依赖用 `optional="true"` + `config-file="xxx.xml"`（把依赖该插件的扩展挪进单独描述符，依赖不在就不加载那份描述符）——这是"按条件整块启用一组扩展"的成熟做法（[Plugin Dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)）。
- **扩展级排序**：所有扩展通用属性 `id` / `order` / `os`；`order` 取值 `first` / `last` / `before <id>` / `after <id>`，可组合（`order="after extensionY, before extensionX"`）；且明确**不保证**多个 `first` 之间的次序（[Plugin Configuration File](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html)）。
- **无加载期数据流**：扩展是声明式描述符（XML），实现类**在被查询时才实例化**（平台指南要求扩展实现无状态、构造器无副作用、不做静态初始化，退出机制是构造器抛 `ExtensionNotApplicableException`，[Plugin Extensions](https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html)）。跨扩展点的"依赖"实际发生在运行期：消费者查询自己的点拿到列表，列表元素再惰性查询别的服务/点。`with` 元素只做类型约束（限定某个属性里的类名必须实现某接口），不表达时序。

**对 NekoJS 的启示（E1）**：
1. IntelliJ 把"依赖"（plugin depends）与"顺序"（extension order）拆成两个正交概念，且都**只有声明、没有数据通道**——因为它的扩展是纯描述符。NekoJS 的扩展点收集需要**产物**（如 `RegistryInfos` 实例），所以不能照搬"无数据流"模型；但"依赖声明与顺序声明分离"值得保留：点优先执行序隐含了顺序（注册序），`result()` 隐含了数据。
2. `optional + config-file` 的"条件启用整组扩展"是 NekoJS `enabled` 谓词（环境过滤）的声明式加强版；若未来出现"装了某 mod 才启用某点"的需求，可借鉴"点分组 + 组级条件"而非点级谓词堆叠。
3. IntelliJ 明确警告 `first`/`last` 无全序保证——对应 NekoJS 应当**避免**给扩展点集合提供"插队"API（如 `registerFirst`），统一用注册顺序 + 显式 `result()` 依赖，可预测性更好。

### 1.2 OSGi Declarative Services（SCR）：依赖 = 引用满足，激活推迟

OSGi SCR 是"组件 A 依赖组件 B 的结果"最严格的工业解法，核心是**把依赖做成声明式引用，由运行时解序**：

- 组件配置"当且仅当每个引用都 satisfied"才可激活；引用 satisfied 的条件是可选基数（`0..1`/`0..n`）或目标服务数达到最小基数（§112.3，[OSGi Compendium R8 §112 Service Component Specification](https://docs.osgi.org/specification/osgi.cmpn/8.0.0/service.component.html)）。
- **立即组件**在 satisfied 后马上激活；**延迟组件**先注册服务占位、不实例化类，消费者请求该服务时才真正激活（"Delayed activation allows for delayed class loading and object creation until needed"，§112.1.1）——用占位打破启动顺序死锁。
- 必需引用无目标服务 → 组件休眠不激活；激活后失去绑定服务 → 必须反激活。**环依赖由 SCR 检测并失败**，除非把其中一条引用改 optional（§112.3.11）。

**对 NekoJS 的启示（E1）**：
1. NekoJS 的 `context.result(point)` 返回 null（未执行/被跳过）相当于 SCR 的"optional 引用"。目前点优先执行序下"后注册读先注册"天然无环；但第三方经 `NekoPluginExtensionProvider` 注册点时**注册顺序 = 依赖方向**是隐式约定，没有环检测——若 A 的 provider 先注册、却引用 B 的产物，只会拿到 null 并可能 NPE。SCR 的做法（声明引用、运行时拓扑排序、成环 fail-fast）提示 E1 至少应提供**显式声明依赖**的 API（如 `point.dependsOn(otherPoint)`），bootstrap 据此排序 + 检环，把"注册顺序碰巧正确"变成"声明正确"。
2. SCR 用"服务占位 + 延迟激活"解启动死锁，对应 NekoJS 的 `NekoPluginExtensionHandle`（注册即返回句柄、事后取产物）已经具备同型语义——这是对既有设计的正面佐证。

### 1.3 Spring：隐式依赖定序 + 显式 depends-on + 分阶段 BeanFactory

Spring 把"初始化顺序"分三层解决：

- **隐式**（注入即依赖）："if bean A has a dependency on bean B, the Spring IoC container completely configures bean B prior to invoking the setter method on bean A"（[Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)）——依赖方拿到的永远是**完全配置好的协作者**。
- **显式**（无数据传递的顺序依赖）：`depends-on` "can explicitly force one or more beans to be initialized before the bean using this element is initialized"，典型动机是"触发静态初始化器"这类间接依赖；singleton 场景下销毁序自动反转（[Using depends-on](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-dependson.html)）。
- **分阶段**：容器分 `BeanFactoryPostProcessor`（改容器本身）→ `BeanPostProcessor`（改 bean）→ 普通 bean 的固定相位，相位间不可越级依赖。

**对 NekoJS 的启示（E1）**：
1. Spring 的核心经验：**把"依赖某产物"表达为"消费该产物"**（构造器/setter 注入），顺序由容器推导——NekoJS `clientEventsPoint` 在 initializer 里 `context.result(events)`（`BuiltinPluginExtensionPoints.java:150-164`）正是这个形态的等价物，方向正确。
2. `depends-on` 存在的理由值得注意：有些依赖只是"副作用必须先发生"（静态初始化、锁注入），不消费数据。NekoJS 的 `probe_backends` finisher 做 `lock()+setInstance()`（`BuiltinPluginExtensionPoints.java:171-180`）就是这类；E1 若引入显式依赖声明，应区分"读产物"与"仅需先完成"两种。
3. Spring 相位不可越级 ↔ NekoJS 点优先执行序"后注册点只可见先注册点产物"：**可见性单向**是防止顺序混乱的关键不变量，文档化并加断言（如 `result()` 查询未完成的点直接抛 `IllegalStateException` 而非静默 null——当前查询"后注册点"返回 null 的设计宽松，保留给可选依赖，但建议提供 `resultOrThrow` 区分两种意图；PR #37 文档的示例里正是 `another.resultOrThrow()`）。

### 1.4 pf4j：两阶段生命周期 + 依赖求解

pf4j 是 Java 生态最轻量的插件框架参照：`loadPlugins()` → `startPlugins()` 两阶段；插件经 manifest `Plugin-Dependencies: x, y, z` 声明依赖（SemVer 约束），每个插件一个 Parent-Last ClassLoader，类加载器里含"被依赖插件的类"；扩展用 `@Extension` 注解 + 编译期生成 `META-INF/extensions.idx` 索引发现（[pf4j Getting Started](https://pf4j.org/doc/getting-started.html)）。依赖求解器保证被依赖者先 start（start 顺序由依赖图决定）。

**对 NekoJS 的启示**：NekoJS 单 classloader + 注解扫描（`@RegisterNekoJSPlugin` + priority）比 pf4j 简单，够用；pf4j 真正值得借鉴的只有一点——**load 与 start 分离**（先发现全部、再按依赖序启动）与 NekoJS 的"注册窗口 → freeze → 逐点收集"同构，证明这种两窗口设计在独立生态里也站得住。

### 1.5 Collector 式 initializer/merger/finisher：先例与已知坑

PR #37 提出、master 已实现的形态，最直接先例是 [`java.util.stream.Collector`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collector.html)（supplier / accumulator / combiner / finisher + characteristics）。官方 API Note 给出的顺序语义等价式与 NekoJS bootstrap 完全同构：

```java
A container = collector.supplier().get();
for (T t : data) collector.accumulator().accept(container, t);
return collector.finisher().apply(container);
```

语义对照与已知坑（全部有 javadoc 条文依据）：

| Collector 概念 | NekoJS 对应 | 已知坑（javadoc 原则 → NekoJS 对策） |
|---|---|---|
| `supplier()` 新建可变容器 | `initializer`（每轮 bootstrap 新建累积器） | Collector 要求容器**不得跨流复用**；NekoJS 文档同样强调"绝不能返回跨轮共享的可变单例"（`NekoPluginExtensionPoint.java:36-38`），reload 可重入依赖这一点 |
| `accumulator(A,T)` | `collector(P,A)`（PR #37 叫 merger） | 注意 NekoJS/PR#37 的 merger 是 `(累积器, 插件)`，比 Collector 的 `(累积器, 元素)` 多了"谁贡献的"维度——合并策略（首胜/覆盖/fail-fast）必须显式。JDK 自己就不统一：`Collectors.toMap` 重复键抛异常，NekoJS `NodeModulesBucket` 首胜+warn（`BuiltinPluginExtensionPoints.java:274-291`）、`RecipeNamespacesBucket` fail-fast（`:294-309`）、KubeJS 类型注册 warn+替换——**没有默认正解，必须每点声明** |
| `finisher(A,R)` 产物变换 | `finisher`（累积器 → 不可变产物 + 装配副作用） | Collector 规则："Once a result is passed to the combiner or finisher function, it is never passed to the accumulator function again"——**finish 之后的容器不可再收集**。NekoJS 对应不变量：产物发布后累积器冻结（各 finisher 的 `freeze()/lock()/snapshot()`）；建议 bootstrap 在 finish 后把累积器引用置空，把该规则从约定变成机制 |
| `IDENTITY_FINISH` 特征 | 退化工厂 `of(id, pluginType, collector)`（finisher 恒等） | Collector 用特征标记避免无谓包装；NekoJS 已用重载工厂区分，等价成立 |
| `combiner(A,A)` | **无**（单线程 bootstrap，不需要） | Collector 的 combiner 只为并行归约存在；NekoJS bootstrap 天然串行（点优先），省略是合理简化，不是缺陷 |
| `CONCURRENT` 特征 | 无（插件收集按 priority 序串行） | Collector 要求非并发容器"serially thread-confined"；NekoJS 串行收集同理由成立。若未来并行收集插件（不太可能），需重新评估 |

**结论**：PR #37 的 initializer/merger/finisher 是 Collector 语义在"插件收集"域的忠实移植，master 的 `NekoPluginExtensionPoint<P,A,R>` 三泛型（插件类型/累积器/产物）比 Collector 还多表达了"按插件类型过滤"（`pluginType.isInstance`）与"按环境跳过"（`enabled` 谓词）两个正交维度，无先例违背。真正要补的只有两点：**依赖显式化**（§1.2 的环检测/声明）与**merge 策略每点显式**（上表 accumulator 行）。

---

## 二、内置扩展自包含注册（对应 P3，服务 E1/R1）

各框架让"内置"与"第三方"走同一条路径的手法，高度一致——**核心自己也是一条普通条目**：

| 框架 | 内置如何注册 | 出处 |
|---|---|---|
| IntelliJ | 平台自身功能以 bundled plugin 形式发布（`$PRODUCT_ROOT$/plugins/`），平台命名空间 `com.intellij` 只是"内置插件的 id 前缀"；第三方扩展平台点 = `defaultExtensionNs="com.intellij"`，扩展别的插件 = 用其插件 id 作命名空间——同一机制，无特权通道 | [Plugin Dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)、[Plugin Extensions](https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html) |
| Eclipse | 平台 bundle（如 `org.eclipse.core.runtime`）在自己的 `plugin.xml` 里声明 extension points 与 extensions，经同一个 `IExtensionRegistry` 暴露；第三方 plugin.xml 与之同构 | [IExtensionRegistry API](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/IExtensionRegistry.html)、[Vogella 教程](https://www.vogella.com/tutorials/EclipseExtensionPoint/article.html) |
| JDK ServiceLoader | 命名模块用 `provides S with Impl` 声明，类路径用 `META-INF/services/S` 文件——**JDK 自己的模块也用同一指令提供实现**；两机制撞名时按模块优先去重 | [ServiceLoader javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html) |
| pf4j | "system extension"：应用 jar 里的 `@Extension` 类与插件 jar 的扩展走同一 `getExtensions()` 发现，单 classloader 模式下等价于"带注解发现的 ServiceLoader"，且随时可切回多 classloader 而**不改应用代码** | [pf4j System Extension](https://pf4j.org/doc/system-extension.html) |
| KubeJS | 自己 jar 里的 `kubejs.plugins.txt` 与所有 mod 的同格式：一行 FQCN + 可选条件（`client` / 依赖 modid）。内置插件 `BuiltinKubeJSPlugin`、`BuiltinKubeJSClientPlugin client`、`ArchitecturyIntegration architectury` 就写在文件头三行 | 源码 [`src/main/resources/kubejs.plugins.txt`](https://github.com/kube-mods/kubejs/blob/main/src/main/resources/kubejs.plugins.txt)、发现逻辑 [`KubeJSPlugins.java`](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/plugin/KubeJSPlugins.java) |
| NekoJS（现状） | 内置插件 `NekoCommonBuiltinPlugin` / `NekoProbeBuiltinPlugin` 走 `@RegisterNekoJSPlugin` 与第三方同批收集 ✓；但 14 个内置**扩展点定义**仍硬编码于 `BuiltinPluginExtensionPoints.builtIn()` ✗ | `common/src/main/java/com/tkisor/nekojs/core/plugin/BuiltinPluginExtensionPoints.java` |

**对 NekoJS 的启示（E1）**：

1. **"内置 = 同机制下的一条普通条目"是所有成熟框架的共识**（IntelliJ/Eclipse/ServiceLoader/pf4j/KubeJS 五家全部如此，无一为例外）。NekoJS 内置插件已达标；P3 的真正缺口只剩"内置扩展点的定义"这一处特权代码。
2. 补法有先例可循且成本低：让"点定义"也变成某内置插件经 `NekoPluginExtensionProvider.registerPluginExtensionPoints` 注册的贡献（例如把 `BuiltinPluginExtensionPoints` 拆成若干个实现 provider 的内置插件，每个自带自己的点）。副作用是内置点将与第三方点在**同一个注册序里排队**（内置插件按 priority 决定先后）——IntelliJ 的经验表明这不是问题（平台与插件本就同一序），但 NekoJS 若要保住"内置 `nekojs:*` 永远先于自定义点"的当前保证（`NekoPluginExtensionProvider.java:26-27`），给内置插件更高 priority 或保留"内置点先注册"的固定相位即可，二选一应在 E1 里明确写下。
3. KubeJS 的"行内条件"（`client`、modid）是零成本的内置/第三方统一表达：同一文件里既写内置也写集成开关。NekoJS 的 `enabled` 谓词已覆盖环境维度，"第三方 mod 在场才启用"维度若出现，可照抄这种声明式行内条件。

---

## 三、通用注册表派生与连带注册（对应 P4，服务 R1）

### 3.1 KubeJS（当前主分支源码精读，1.21+ NeoForge）

这是与 PR #37 意图最接近的现成系统，一套机制覆盖 20+ 种注册表。四个部件：

**(1) 类型模型：`BuilderType` + 每注册表 `Info`（default + named types）**

```java
// src/main/java/dev/latvian/mods/kubejs/registry/BuilderType.java
public record BuilderType<T>(Identifier type,
    Class<? extends BuilderBase<? extends T>> builderClass, BuilderFactory factory) {}
```

`BuilderTypeRegistryHandler` 用一个惰性全局 `Map<ResourceKey, Info>` 聚合所有插件贡献（[BuilderTypeRegistryHandler.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/registry/BuilderTypeRegistryHandler.java)）：

```java
public static final Lazy<Map<ResourceKey<?>, Info<?>>> INFO = Lazy.identityMap(map -> {
    var handler = new BuilderTypeRegistryHandler(map);
    KubeJSPlugins.forEachPlugin(handler, KubeJSPlugin::registerBuilderTypes);
    KubeJSPlugins.forEachPlugin(handler, KubeJSPlugin::registerServerRegistries);
});
```

每个 `Info` 持有：`defaultType`（无类型名时的兜底 builder 工厂）、`types`（`Identifier → BuilderType` 命名类型）、`fallbackLookup`（按 path 弱匹配）。内置类型全在 `BuiltinKubeJSPlugin.registerBuilderTypes` 里走**与 addon 完全相同的回调**注册（[BuiltinKubeJSPlugin.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/plugin/builtin/BuiltinKubeJSPlugin.java)）：`registry.addDefault(Registries.ITEM, ItemBuilder.class, ItemBuilder::new)`、`registry.of(Registries.ITEM, reg -> reg.add(KubeJS.id("sword"), SwordItemBuilder.class, ...))`（剑/镐/斧/盔甲等 12 种 item 变体、15 种 block 变体、fluid 等）。冲突策略是 **warn + 后者替换**（default 被替换、named 同名被替换都只告警）。

**(2) 存储模型：注册表键 → 构建器仓库，先攒后建**

`RegistryObjectStorage` 是全局 `Map<ResourceKey<? extends Registry<?>>, RegistryObjectStorage>`（[RegistryObjectStorage.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/registry/RegistryObjectStorage.java)）：脚本期创建的所有 builder 只进仓库（重复 key 抛 `IllegalArgumentException`），**对象创建完全推迟到平台注册事件**。

**(3) 事件绑定：每注册表一次 `RegisterEvent`**

`RegistryEventHandler`（NeoForge `@EventBusSubscriber`）以 `EventPriority.LOW` 订阅 NeoForge `RegisterEvent`（低优先级 = 让其他 mod 的常规监听先跑），对每个注册表：先 `StartupEvents.REGISTRY.post(...)` 发布脚本事件（`RegistryKubeEvent`），再把仓库里所有非 dummy builder 以 `event.register(registryKey, builder.id, builder::createTransformedObject)` **供应者**形式注册（[RegistryEventHandler.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/registry/RegistryEventHandler.java)）。脚本侧 `event.create('my_item')` → 查 default type；`event.create('x', 'sword')` → 查命名类型。

**(4) 连带注册：`createAdditionalObjects` + 事件后置钩子（对 R1 最关键）**

- `BuilderBase`（所有 builder 基类，实现 `Supplier<T>`）有空方法 `createAdditionalObjects(AdditionalObjectRegistry registry)`；
- `AdditionalObjectRegistry` 是单个方法接口：`<T> void add(ResourceKey<Registry<T>> registry, BuilderBase<? extends T> builder)`——**"向任意其他注册表追加 builder"**；
- `RegistryKubeEvent` 自身实现该接口，并在 `afterPosted(EventResult)`（脚本事件发布完毕后）遍历本轮创建的 builder 调用之（[RegistryKubeEvent.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/registry/RegistryKubeEvent.java)）：

```java
@Override
public void afterPosted(EventResult result) {
    for (var c : created) { c.createAdditionalObjects(this); }
}
```

具体派生（`FluidBuilder.java`）：构造器里**预创建**四个子 builder（`fluidType`、`flowingFluid = new FlowingFluidBuilder(this)`、`block = new FluidBlockBuilder(this)`、`bucketItem = new FluidBucketItemBuilder(this)`），`noBucket()`/`noBlock()` 把对应字段置 null，最后：

```java
@Override
public void createAdditionalObjects(AdditionalObjectRegistry registry) {
    registry.add(NeoForgeRegistries.Keys.FLUID_TYPES, fluidType);
    registry.add(Registries.FLUID, flowingFluid);
    if (block != null) registry.add(Registries.BLOCK, block);
    if (bucketItem != null) registry.add(Registries.ITEM, bucketItem);
}
```

`BlockBuilder` 同理：`itemBuilder` 默认 `new BlockItemBuilder(this, id)`（`noItem()` 置 null、`item(...)` 定制），连带注册到 ITEM，另可连带 BLOCK_ENTITY_TYPE（[BlockBuilder.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/block/BlockBuilder.java) `createAdditionalObjects`）。

**为什么跨注册表依赖不炸**：三个机制配合——(a) 所有 builder 先躺在各自仓库里，等平台事件逐注册表来"抽干"；(b) 对象创建是 `event.register(..., builder::createTransformedObject)` 的**懒供应者**，`FluidBuilder.createProperties()` 里对 bucket/block 的引用也全部走 `Supplier`（`BuilderBase implements Supplier<T>`），注册表间的构造期依赖被推迟到"该注册表被抽干的那一刻"；(c) NeoForge 保证事件顺序（见 3.2）。用户侧文档确认行为：建 fluid 默认得桶（`noBucket()` 可去掉）、默认得方块（`noBlock()`）、flowing 变体隐含（[KubeJS Fluid Registry](https://kubejs.com/wiki/tutorials/fluid-registry)）。

### 3.2 NeoForge `RegisterEvent`：跨注册表顺序的真实实现（本地源码证据）

NeoForge 自己怎么保证"Block 先于 BlockItem 注册"？答案是**人工维护的顺序表**而非通用拓扑排序（`neoforge-26.1.2.71-sources.jar` 内 `net/neoforged/neoforge/registries/GameData.java`，同文件见 [GitHub neoforged/NeoForge](https://github.com/neoforged/NeoForge/blob/1.21.x/src/main/java/net/neoforged/neoforge/registries/GameData.java)）：

```java
public static Set<Identifier> getRegistrationOrder() {
    Set<Identifier> ordered = new LinkedHashSet<>();
    ordered.add(Registries.ATTRIBUTE.identifier());
      // Vanilla order is incorrect, both Item and MobEffect depend on Attribute at construction time.
    ordered.add(Registries.DATA_COMPONENT_TYPE.identifier());
      // Vanilla order is incorrect, Item depends on data components at construction time.
    ordered.add(Registries.PARTICLE_TYPE.identifier());
      // Vanilla order is incorrect, both Block and MobEffect depend on ParticleType at construction time.
    ordered.addAll(BuiltInRegistries.getVanillaRegistrationOrder());
    ordered.addAll(BuiltInRegistries.REGISTRY.keySet().stream().sorted(Identifier::compareNamespaced).toList());
    return ordered;
}
```

即：**三个已知被构造期依赖的注册表手工提到最前（带注释解释），其余按 vanilla 序，modded 按字典序**；事件逐注册表 post，失败聚合成 suppressed 异常并回滚到 vanilla 状态。另外两个相关细节：

- `RegisterEvent.register(key, name, supplier)` 在 key 与当前事件不匹配时是**静默 no-op**（`RegisterEvent.java:52-56`）——"在错误的事件里注册别的注册表"不会报错，这是平台 API 的一个坑，脚本引擎层不应模仿；
- 全部注册表抽干后还有一个 `BlockEntityTypeAddBlocksEvent`（`GameData.java:103`）——**需要引用已完成注册表的逻辑放在"全部完成后"的后置事件**，与 KubeJS `afterPosted` 同构。

### 3.3 CraftTweaker / ContentTweaker：builder 层级 + 独立加载相位

CraftTweaker 核心长期聚焦"改"而非"造"（配方/物品属性修改，ZenCode/括号解析 `<item:...>`）；创建新内容在 1.14–1.18 由 addon ContentTweaker 承担（[CraftTweaker 文档站](https://docs.blamejared.com/)）：

- 脚本必须声明独立加载相位 `#loader contenttweaker`，且"no crafttweaker scripts are allowed in #loader contenttweaker"——**内容创建与常规修改分相位执行**，避免在注册窗口外动注册表（[Simple Walkthrough](https://docs.blamejared.com/1.16/en/mods/contenttweaker/SimpleWalkthrough)）；
- builder 体系：`new BlockBuilder(...).withType<BlockBuilderStairs>().build("name")`——`withType<>` 切换特化 builder（单向切换："once you do the withType call, there is no going back"），`build(name)` 才真正登记，名字同时决定 model/纹理/lang key（`block.contenttweaker.<name>`）；
- 与 KubeJS 相比无跨注册表自动派生的等价物（walkthrough 无 BlockItem 自动注册的记载），流体 builder 也只收颜色/纹理参数——**连带注册这件事上 KubeJS 的 `createAdditionalObjects` 是更完善的先例**。

### 3.4 对照表：PR #37 设计 vs KubeJS 现状

| 维度 | NekoJS PR #37（genericregistry 分支） | KubeJS（kube-mods/kubejs main） | 评价 |
|---|---|---|---|
| 注册表元信息 | `RegistryInfo` + `RegistryInfos`（反射扫描 + 手动添加，两阶段插件收集） | 无独立元信息层；`ResourceKey` 本身即键，类型注册直接按 `ResourceKey` 分组 | KubeJS 更薄；NekoJS 需要 `RegistryInfos` 是因为要支撑跨版本/反射扫描，属额外负担而非优点 |
| 类型注册 | `RegistryObjectType`（类型标识+工厂）按注册表分组，Scope try-with-resources 注册 | `BuilderType`（id+builder类+工厂），`Info.defaultType` + `Info.types`，`registry.of(key, callback)` 或 `addDefault` | 几乎同构；KubeJS 额外有 **default type**（`event.create('id')` 不写类型名）与按 path 的 fallback 弱匹配 |
| 冲突策略 | 同 id 扩展点注册 fail-fast（沿用扩展点语义） | 类型同名 warn+替换；对象重复 key fail-fast | 两者在"对象层 fail-fast"一致；类型层 KubeJS 宽松（addon 可覆盖内置类型） |
| 事件绑定 | `RegistryEventJS.custom()/register()`，待与 NeoForge `RegisterEvent` 集成 | `RegistryEventHandler` 订阅 `RegisterEvent`（LOW），每注册表 post 一次脚本事件后抽干仓库 | PR #37 待完成项，KubeJS 给出完整蓝本 |
| 内置注册 | `BuiltinRegistrySupport` 专门类 + `RegistrySupportPlugin` 双方法 | `BuiltinKubeJSPlugin` 走与 addon 相同的 `registerBuilderTypes` 回调 | KubeJS 内置无特权类；PR #37 的 `BuiltinRegistrySupport` 建议降格为普通内置插件的贡献 |
| 连带注册 | 设想中：`builder.additionalBuilders()` 或 `builder.handleAdditionalRegistry(Consumer)` | 已实现：`builder.createAdditionalObjects(AdditionalObjectRegistry)`，事件 `afterPosted` 后置统一处理 | PR #37 的第二个设想与 KubeJS 现行实现**逐字对应**；子 builder 预创建 + `noBucket()/noBlock()` 抑制 + Supplier 懒引用是配套必需品 |

### 3.5 对 R1 的启示（结论）

1. **连带注册采 PR #37 的第二方案（`handleAdditionalRegistry(Consumer)` 形态）**，即 KubeJS 验证多年的 `createAdditionalObjects(AdditionalObjectRegistry)` 模型：单个方法接口 + 事件发布完毕后统一回调。不建议 `additionalBuilders()` 直返集合——回调式让派生方拿到"注册目标"抽象，天然支持按需/条件派生（如 `noBucket()` 时根本不构造 bucket builder）。
2. **派生产物必须"预创建子 builder + 可置 null + 跨引用走 Supplier"三件套**：`FluidBuilder` 构造器即建 `fluidType/flowingFluid/block/bucketItem`，opt-out 是置 null 而非事后删除；对象构造期互引全部经 `Supplier<T>`（`BuilderBase implements Supplier<T>`）。NekoJS 的 `RegistryObjectBuilder` 若直接实现 `Supplier<T>`，`additional` 子 builder 的互引问题即自动消解。
3. **顺序问题应交给"先攒后建"消解，不要在引擎内做注册表拓扑排序**：全局 `注册表键 → builder 仓库`，脚本期只攒 builder；对象创建放到平台注册事件回调里以 supplier 形式注册。跨注册表顺序信任平台的 `RegisterEvent` 序（NeoForge 是人工维护顺序表——连 NeoForge 都不做通用拓扑排序，引擎侧更不应尝试）。
4. **需要读"已完成注册表"的逻辑放后置事件**：NeoForge `BlockEntityTypeAddBlocksEvent`（全部注册表抽干后）与 KubeJS `afterPosted` 均为此模式；R1 若有"注册完读取注册表内容再派生"的需求（如按已注册方块生成挖掘等级），应设独立的 after-all 钩子而非在单个注册表事件里读别的注册表。
5. **default type 值得抄**：`event.create('my_item')` 不带类型名走 default，是脚本体验的关键（PR #37 的 `event.custom("my_item", "basic", ...)` 目前必须写类型名）。建议 `RegistryObjectTypes` 每注册表支持一个默认类型 + 命名类型集合。
6. **类型层冲突策略建议从 fail-fast 放宽为"内置可被覆盖 + warn"**（对象层保持 fail-fast）：这是 KubeJS addon 生态的实际运转方式（addon 用更强 builder 替换内置类型），与 NekoJS 扩展点"id 冲突 fail-fast"不冲突——扩展点 id 是引擎级命名空间，注册表类型名是贡献内容，语义不同。
7. **跨版本建议**：`RegistryEventJS` 不直接引用 NeoForge `RegisterEvent`（PR #37 文档 §1.2 已列）正确；KubeJS 的分层可抄：`RegistryEventHandler`（平台适配层，每 loader 一个）→ `RegistryKubeEvent`（平台无关事件）→ `RegistryObjectStorage`（平台无关仓库）。

---

## 四、总对照与建议清单

### 4.1 E1（扩展点模型）建议

| # | 建议 | 先例依据 |
|---|------|---------|
| E1-1 | 保留 Collector 形态三段式（已落地，验证通过）；finish 后累积器引用置空，把"finish 后不再收集"从约定变机制 | Collector javadoc 限制条款 |
| E1-2 | 增加显式依赖声明（`dependsOn(point)` / initializer 引用即依赖），bootstrap 做拓扑排序 + 环检测 fail-fast；区分"读产物"与"仅需先完成"两种依赖 | OSGi SCR §112.3.11、Spring depends-on |
| E1-3 | `result()` 语义分档：可选依赖返回 null，必需依赖提供 `resultOrThrow`（查询未完成点抛 `IllegalStateException`） | PR #37 文档示例 `resultOrThrow`、Spring"完全配置好的协作者"原则 |
| E1-4 | 不提供扩展点"插队"API（first/last），统一注册序 + 依赖声明 | IntelliJ `order` 属性的已知无保证问题 |
| E1-5 | 内置扩展点定义迁移到内置插件的 provider 贡献，消除 `BuiltinPluginExtensionPoints` 特权代码；是否保留"内置点永远在前"作为固定相位需在 E1 定夺 | §2 五框架一致做法 |
| E1-6 | 每个扩展点显式声明 merge 策略（首胜/覆盖/fail-fast），不用隐式默认 | Collector `toMap` vs KubeJS warn+replace 的分歧 |

### 4.2 R1（通用注册表模型）建议

| # | 建议 | 先例依据 |
|---|------|---------|
| R1-1 | 连带注册采用 `createAdditionalObjects(AdditionalObjectRegistry)` 回调模型，事件发布完毕后统一执行 | KubeJS `RegistryKubeEvent.afterPosted` |
| R1-2 | 派生子 builder 预创建 + `noBucket()/noBlock()/noItem()` 抑制 + `Builder implements Supplier` 懒互引 | KubeJS `FluidBuilder`/`BlockBuilder` |
| R1-3 | 全局 `注册表键 → builder 仓库` 先攒后建，对象创建放平台注册事件；不在引擎内做注册表拓扑排序 | KubeJS `RegistryObjectStorage`、NeoForge `GameData.getRegistrationOrder` |
| R1-4 | after-all 后置钩子承载"读已完成注册表再派生"的需求 | NeoForge `BlockEntityTypeAddBlocksEvent`、KubeJS `afterPosted` |
| R1-5 | 每注册表支持 default type + 命名类型；类型层冲突"内置可覆盖 + warn"，对象层 fail-fast | KubeJS `Info.defaultType` 与冲突策略 |
| R1-6 | 平台适配层（订阅 RegisterEvent）与平台无关仓库/事件分层，跨版本不泄漏 NeoForge 类型 | KubeJS `RegistryEventHandler` ↔ `RegistryKubeEvent` 分层 |

---

## 五、来源清单

**一手源码**
- KubeJS（kube-mods/kubejs，main 分支）：[BuilderType.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/registry/BuilderType.java)、[BuilderTypeRegistryHandler.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/registry/BuilderTypeRegistryHandler.java)、[BuilderBase.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/registry/BuilderBase.java)、[AdditionalObjectRegistry.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/registry/AdditionalObjectRegistry.java)、[RegistryEventHandler.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/registry/RegistryEventHandler.java)、[RegistryKubeEvent.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/registry/RegistryKubeEvent.java)、[RegistryObjectStorage.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/registry/RegistryObjectStorage.java)、[FluidBuilder.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/fluid/FluidBuilder.java)、[BlockBuilder.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/block/BlockBuilder.java)、[BuiltinKubeJSPlugin.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/plugin/builtin/BuiltinKubeJSPlugin.java)、[KubeJSPlugins.java](https://github.com/kube-mods/kubejs/blob/main/src/main/java/dev/latvian/mods/kubejs/plugin/KubeJSPlugins.java)、[kubejs.plugins.txt](https://github.com/kube-mods/kubejs/blob/main/src/main/resources/kubejs.plugins.txt)
- NeoForge 26.1.2.71（本地 Gradle 缓存 sources jar，`C:/Users/11515/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/26.1.2.71/.../neoforge-26.1.2.71-sources.jar`）：`net/neoforged/neoforge/registries/GameData.java`（getRegistrationOrder）、`net/neoforged/neoforge/registries/RegisterEvent.java`（key 不匹配静默 no-op）
- NekoJS master `621f465`：`common/src/main/java/com/tkisor/nekojs/core/plugin/`（`NekoPluginExtensionPoint.java`、`BuiltinPluginExtensionPoints.java`、`NekoPluginExtensionContext.java`、`NekoPluginExtensionProvider.java`）
- PR #37 设计文档：[ZZZank/NekoJS `genericregistry` 分支 `docs/MINECRAFT_REGISTRY.md`](https://github.com/ZZZank/NekoJS/blob/genericregistry/docs/MINECRAFT_REGISTRY.md)

**官方文档/规范**
- [IntelliJ Platform SDK：Plugin Configuration File](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html)（order/id/os、extensionPoint 属性、optional config-file）
- [IntelliJ Platform SDK：Plugin Extensions](https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html)（defaultExtensionNs、扩展实现守则、ExtensionNotApplicableException）
- [IntelliJ Platform SDK：Plugin Dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)（bundled plugins、com.intellij.modules.*、optional+config-file）
- [OSGi Compendium R8 §112 Declarative Services Specification](https://docs.osgi.org/specification/osgi.cmpn/8.0.0/service.component.html)（引用满足、基数、延迟激活、环检测）
- [Spring Framework Reference：Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)（协作者完全配置原则）、[Using depends-on](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-dependson.html)（强制初始化序、销毁序反转）
- [Java SE 21 `java.util.stream.Collector` javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collector.html)（四函数、特征、结合律/同一性、限制条款）
- [Java SE 21 `java.util.ServiceLoader` javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html)（provides/uses、META-INF/services、惰性、去重）
- [pf4j：Getting Started](https://pf4j.org/doc/getting-started.html)（依赖、两阶段、extensions.idx）、[System Extension](https://pf4j.org/doc/system-extension.html)（应用 jar 内置扩展）
- [NeoForge docs：Registries](https://docs.neoforged.net/docs/concepts/registries/)（RegisterEvent 时机、DeferredRegister 关系）、[Mod Files](https://docs.neoforged.net/docs/gettingstarted/modfiles/)（依赖 ordering="AFTER"/循环依赖告警）
- [Eclipse IExtensionRegistry API](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/IExtensionRegistry.html)、[Vogella：Eclipse Extension Points](https://www.vogella.com/tutorials/EclipseExtensionPoint/article.html)
- [KubeJS Wiki：Fluid Registry](https://kubejs.com/wiki/tutorials/fluid-registry)（noBucket/noBlock、thick/thin 预设）、[Item Registry](https://kubejs.com/wiki/tutorials/item-registry)、[Block Registry](https://kubejs.com/wiki/tutorials/block-registry)
- [CraftTweaker 文档站](https://docs.blamejared.com/)、[ContentTweaker Simple Walkthrough (1.16)](https://docs.blamejared.com/1.16/en/mods/contenttweaker/SimpleWalkthrough)（#loader 相位、withType、build(name)）
