
# Minecraft 通用注册表事件系统

本文档描述 NekoJS 的通用注册表事件系统设计与实现。

---

## 1. 现状概览

### 1.1 已完成部分（neoforge-26.2）

| 模块 | 说明 |
|------|------|
| `RegistryInfo` | 注册表元信息（基础类型、原始类型、资源键） |
| `RegistryInfos` | 全局注册表信息管理器，支持反射扫描与手动添加 |
| `RegistryObjectBuilder` | 注册表对象构建器抽象基类 |
| `RegistryObjectType` | 注册表对象类型（类型标识 + 工厂函数） |
| `RegistryObjectTypes` | 按注册表分组的类型集合 |
| `RegistryObjectTypeRegistry` | 类型注册管理器（Scope 模式） |
| `RegistryEventJS` | JavaScript 事件包装，支持 `custom()` / `register()` |

插件系统：
| 类 | 说明 |
|----|------|
| `RegistrySupportPlugin` | 插件接口（`registerRegistryInfo` + `registerRegistryObjectType`） |
| `RegistryInfosPlugin` | 扩展点提供者，协调两阶段初始化 |
| `RegistryInfosRegistry` | 收集扫描类和额外信息 |
| `BuiltinRegistrySupport` | 内置支持（扫描 `Registries.class`，注册 Item 类型） |

已实现的 Builder：
- `ItemBuilderJS`（基础的物品构建器，支持 stackSize/damage/fireResistant/rarity/food/burning 等）

### 1.2 待完成部分

1. 跨平台改造：当前仅在 neoforge-26.2，需扩展到其他版本
   - RegistryEventJS应当避免引用neoforge的RegisterEvent，而
2. 更多 Builder 实现：
   - Item的更多变体，比如axe/hoe/shovel等工具，helmet/chestplate等装甲
   - `BlockBuilderJS`（方块构建器）
   - `FluidBuilderJS`（流体构建器）
   - 其他注册表类型（EntityType、Potion、Enchantment 等）
3. 事件绑定：与 NeoForge `RegisterEvent` 的集成
4. 处理“连带注册”，比如注册Block时，若无明确要求，也需要注册BlockItem；注册Fluid同时也可能还需要注册：流体方块，流动的流体方块，流体桶。

处理“连带注册”的一些思路：添加`builder.additionalBuilders()`提供其他需要注册的builder，在事件发布完成之后与直接注册的builder一起处理；添加`builder.handleAdditionalRegistry(Consumer<RegistryObjectBuilder>)`，也是在发布完成后处理

---

## 2. 扩展点相关

### 2.1 注册系统使用流程（两阶段插件加载）

```
1. nekojs:registry_infos 扩展点
   → 各 RegistrySupportPlugin.registerRegistryInfo()
   → 收集 classesToScan + additionalInfos
   → finish 回调 → 构建 RegistryInfos 实例

2. nekojs:registry_object_types 扩展点
   → 各 RegistrySupportPlugin.registerRegistryObjectType()
   → 通过 Scope 注册 RegistryObjectType
   → finish 回调 → 构建 RegistryObjectTypes 不可变映射
```

### 2.2 暴露的问题与改进方向

注册表事件系统尝试通过扩展点来减少hack，但是总体使用体验非常糟糕，也暴露了一些设计问题：
1. 扩展点目前可以插桩的位置太少，只有NekoJSPlugin.init()提供了一个所有扩展点都完成加载之后的时机。这个注册系统在设计时registry_object_types依赖于registry_infos的结果（registry.build()得到RegistryInfos），特意添加了onFinish callback，这才勉强可用。
2. 扩展点存在前后依赖或者需要的情况难以处理。目前registry_infos扩展点是提前初始化registry的实例，并在自己的onFinish里初始化另一个扩展点的registry。一个扩展点需要的资源在需要之前就初始化了，同时又干涉另一个扩展点的初始化。
3. 内置扩展点高度依赖于NekoPluginExtensionContext，一旦需要添加内置扩展点就需要修改NekoPluginExtensionContext，进而影响runtime、bootstrap等等，反而显得耦合严重。

总得来讲，扩展点需要改造，应当提供一个类似于stream Collector的initializer/merger/finisher系统：

```java
void loadExt(ExtensionPoint<T> ext) {
    var intermediate = ext.initializer().get();
    for (var plugin: plugins) {
        if (ext.shouldExableFor(plugin)) {
            ext.merger.apply(intermediate, plugin)
       }
    }
    ext.result = new SomeKindOfHolder<>(ext, ext.finisher().apply(intermediate));
}
```

扩展点注册之后应当返回一个持有扩展点，并可以获取加载结果的对象。

同时应当允许扩展点在initializer里使用另一个扩展点的结果，此外允许finisher仅接受结果（以下仅为示例）：

```java
Supplier<B> buildInitializer(ExtensionPoint<A> another, Func<A, B> action) {
    return () -> action.apply(another.resultOrThrow());
}

Function<A, A> noTransformFinisher(Consumer<A> action) {
    return (value) -> {
        action.accespt(result);
        return result;
    }
}
```

---

## 3. 语法约束与设计决策

### 3.1 Builder 模式使用 public field

```java
// ✅ 推荐：使用 public field
public class ItemBuilderJS extends RegistryObjectBuilder<Item> {
    public int maxStackSize = 64;
    public Rarity rarity = Rarity.COMMON;
    // ...
}

// ❌ 不推荐：链式调用方法
public class ItemBuilderJS extends RegistryObjectBuilder<Item> {
    public ItemBuilderJS maxStackSize(int size) { ... return this; }
    public ItemBuilderJS rarity(Rarity r) { ... return this; }
}
```

原因：方法返回类型是固定类型而非 `this`，涉及子类时返回类型无法正确表示自身。

### 3.2 Scope 模式注册类型

```java
// 推荐方式：使用 try-with-resources
try (var scope = registry.scope(Registries.ITEM)) {
    scope.register("basic", ItemBuilderJS::new);
}

// 另一种方式：直接注册 RegistryObjectType
registry.register(new RegistryObjectType.Impl<>(...));
```

### 3.3 不可变集合

```java
// 最终输出使用不可变集合
public Map<Identifier, Supplier<T>> viewProviders() {
    return Collections.unmodifiableMap(providers);
}
```

---

## 4. API 使用示例

### 4.1 脚本端使用

```javascript
// 注册表事件处理
RegistryEvents.register((event) => {
    // 使用内置类型创建
    event.custom("my_item", "basic", (builder) => {
        builder.maxStackSize = 16;
        builder.rarity = "RARE";
    });

    // 直接注册 Supplier
    event.register("my_custom_item", () => {
        return new MyCustomItem();
    });
});
```

### 4.2 插件开发

```java
@RegisterNekoJSPlugin
public class MyRegistryPlugin implements RegistrySupportPlugin {

    @Override
    public void registerRegistryInfo(RegistryInfosRegistry registry) {
        // 添加需要扫描的类
        registry.addClassesToScan(Registries.class);

        // 或手动添加
        registry.addAdditionalInfo(MyCustom.class, MY_CUSTOM_KEY);
    }

    @Override
    public void registerRegistryObjectType(RegistryObjectTypeRegistry registry) {
        try (var scope = registry.scope(Registries.ITEM)) {
            scope.register("my_type", MyItemBuilder::new);
        }
    }
}
```

### 4.3 自定义 Builder

```java
public class MyItemBuilder extends RegistryObjectBuilder<MyItem> {
    public String customProperty = "default";

    public MyItemBuilder(RegistryInfo<MyItem> info, Identifier id) {
        super(info, id);
    }

    @Override
    public MyItem build() {
        return new MyItem(id, customProperty);
    }
}
```

---

## 5. 相关文件

| 文件 | 说明 |
|------|------|
| `platforms/neoforge-26.2/src/main/java/com/tkisor/nekojs/wrapper/registry/base/` | 核心实现 |
| `platforms/neoforge-26.2/src/main/java/com/tkisor/nekojs/wrapper/registry/base/impl/` | Builder 实现 |
| `platforms/neoforge-26.2/src/main/java/com/tkisor/nekojs/wrapper/registry/base/plugin/` | 插件系统 |