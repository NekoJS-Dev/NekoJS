<!-- wiki-page: type-adapters; locale: cn -->

> **中文** · [English](type-adapters_us)

<a id="wiki-section-1"></a>
# 类型适配器

类型适配器把脚本传进来的 JS 值转换成 Java 方法需要的参数类型。比如一个 Java 方法的参数是 `ItemStack`，有了适配器，脚本就可以直接传 `'minecraft:stone'`、`'16x minecraft:stone'` 或 `{ id: 'minecraft:stone', count: 2 }`。

如果你的插件公开了 Java 方法，并且希望脚本用更简单的写法传参，就写一个适配器。本页面向写 NekoJS 插件的 Java 开发者。

<a id="wiki-section-2"></a>
## 最小示例

假设插件里有一个坐标类，和一个接收它的绑定方法：

```java
package com.example.myaddon;

public record MyPos(int x, int y) {}
```

下面的适配器让脚本可以传 `{ x: 1, y: 2 }` 或字符串 `'1,2'`：

```java
package com.example.myaddon;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.BaseJsTypeAdapter;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.api.data.JsValueView;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

public class MyPosAdapter extends BaseJsTypeAdapter<MyPos> {
    public MyPosAdapter() {
        super(MyPos.class);
    }

    @Override
    protected MyPos fromString(String text) {
        String[] parts = text.split(",");
        if (parts.length != 2) {
            throw new ValueConversionException(MyPos.class, "'x,y'", text, "expected two numbers");
        }
        try {
            return new MyPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException e) {
            throw new ValueConversionException(MyPos.class, "'x,y'", text, "expected two numbers", e);
        }
    }

    @Override
    protected MyPos fromHostObject(Object host) {
        // 只有 host 已经是 MyPos 时才会走到这里
        return (MyPos) host;
    }

    @Override
    protected boolean acceptOther(JsValueView value) {
        return value.hasMember("x") && value.hasMember("y");
    }

    @Override
    protected MyPos fromOther(JsValueView value) {
        return new MyPos(value.getMember("x").asInt(), value.getMember("y").asInt());
    }

    @Override
    public ConversionPrecedence precedence() {
        return ConversionPrecedence.LOW;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
            AdapterInputShape.self(),
            AdapterInputShape.string(),
            AdapterInputShape.object(
                AdapterInputShape.Slot.req("x", AdapterInputShape.number()),
                AdapterInputShape.Slot.req("y", AdapterInputShape.number())));
    }
}
```

在插件里注册：

```java
@Override
public void registerAdapters(JSTypeAdapterRegistry registry) {
    registry.register(new MyPosAdapter());
}
```

之后，任何参数类型是 `MyPos` 的 Java 方法都能这样调用：

```javascript
MyAddon.teleport({ x: 1, y: 2 })
MyAddon.teleport('3,4')
```

插件的写法和发现方式见 [插件开发](plugin-development_cn)。

<a id="wiki-section-3"></a>
## 工作原理

NekoJS 在启动时为每个已注册的适配器向 GraalJS 安装一条 `HostAccess.targetTypeMapping`：当 Java 方法需要类型 `T` 而脚本传入一个 JS 值时，GraalJS 先问适配器“能不能处理”，能处理就调用它做转换。

几点直接影响你写代码的事实：

- 映射按**精确的目标类**登记。适配器注册的是 `MyPos.class`，不会自动作用于它的子类或接口。
- 映射只看原始类，不看泛型参数。`Box<T>` 的适配器注册的是 `Box.class`，NekoJS 不会替你转换里面的 `T`。
- 所有脚本 Context 共用同一份映射。适配器在插件加载时注册一次，普通 reload 不会重新注册。
- 适配器里抛出的 `ClassCastException` 会被包装成 `NEKO-5202`，这样它不会被误当成“重载不匹配”而悄悄跳过。

<a id="wiki-section-4"></a>
## 选择哪种写法

有两套接口，都通过同一个 `JSTypeAdapterRegistry` 注册：

| 接口 | 依赖 | 适合 |
|---|---|---|
| `JsTypeAdapter<T>`（新，推荐） | 无 Minecraft、无 Graal 依赖 | 新写的适配器 |
| `JSTypeAdapter<T>`（旧） | 依赖 Graal `Value` | 已有代码；需要直接读 `Value` 的场景 |

每套接口各有一个模板基类，帮你按输入类型分派：

| 类 | 你必须实现 | 可以覆盖 |
|---|---|---|
| `BaseJsTypeAdapter<T>`（新） | 构造器传入目标类、`fromHostObject` | `fromString`、`acceptOther`/`fromOther`、`acceptNull`/`defaultValue`、`supportsString`、`precedence`、`inputShapes` |
| `AbstractJSTypeAdapter<T>`（旧） | `getTargetClass`、`fromHostObject` | `fromString`、`acceptOther`/`fromOther`、`acceptNull`/`defaultValue`、`getPrecedence`、`inputShapes` |
| `JsTypeAdapter<T>`（直接实现） | `targetType`、`supports`、`convert`、`precedence` | `inputShapes` |
| `JSTypeAdapter<T>`（直接实现） | `getTargetClass`、`test`、`apply` | `getPrecedence`、`inputShapes`、`syntaxDoc` |

基类不是必须的，直接实现接口完全等价。内置的 `ComponentAdapter` 是一个用 `BaseJsTypeAdapter` 写的完整例子。

<a id="wiki-section-5"></a>
### BaseJsTypeAdapter 怎样分派

| 脚本传入 | `supports` 的判断 | `convert` 调用 |
|---|---|---|
| `null` | `acceptNull()`，默认 `false` | `defaultValue()` |
| 字符串 | 先试着调用一次 `fromString`，抛 `ValueConversionException` 就算不接受 | `fromString` |
| Java 对象 | 对象是目标类型的实例才接受 | `fromHostObject` |
| 其他 JS 值（对象、数组、数字） | `acceptOther`，默认 `false` | `fromOther` |

由此得出三条规则：

- **字符串格式不对时，抛 `ValueConversionException`。** `supports` 只捕获这一种异常。抛别的异常（比如 `NumberFormatException`）会直接传给脚本，而不是让其他适配器有机会处理。
- **`fromHostObject` 和 `fromOther` 不要返回 `null`。** 返回 `null` 会被当成转换失败并抛出异常。要表示“不接受”，就在 `supports` 阶段返回 `false`。
- **其他类型的 Java 对象不会进入 `fromHostObject`。** 想接受别的 Java 类型（比如让 `Vec3` 转成你的坐标），直接实现 `JsTypeAdapter`，或者使用旧的 `AbstractJSTypeAdapter`——它把所有 host 对象都交给 `fromHostObject`，返回 `null` 表示不接受。

<a id="wiki-section-6"></a>
### 不写类的快捷方式

`JSTypeAdapterRegistry` 还提供两个默认方法：

```java
// 用两个 lambda 注册一个旧接口适配器
registry.register(MyPos.class,
    value -> value.hasMember("x") && value.hasMember("y"),
    value -> new MyPos(value.getMember("x").asInt(), value.getMember("y").asInt()));

// 让 ItemLike 参数复用 Item 适配器的全部输入写法
registry.registerAlias(ItemLike.class, Item.class, Item::asItem);
```

`registerAlias` 不指定优先级时，沿用来源类型适配器的优先级。

Minecraft 侧还有 `TypeAdapterDsl`（`com.tkisor.nekojs.js.type_adapter`）：

- `TypeAdapterDsl.registerCodec(registry, Foo.class, Foo.CODEC)`：把 JS 值转成 JSON，再用 Mojang `Codec` 解析。优先级 `LOWEST`。
- `TypeAdapterDsl.registerRecord(registry, MyRecord.class)`：JS 对象按 record 组件名构造 record。所有字段必填，可以另传一个默认值 `Map`。对象里多出未知字段会报错。

<a id="wiki-section-7"></a>
## 优先级

多个适配器都能处理同一个目标类型时，按优先级选择。`ConversionPrecedence` 只有四级，从低到高：`LOWEST`、`LOW`、`HIGH`、`HIGHEST`。没有 `NORMAL`。

| 写法 | 默认优先级 |
|---|---|
| 旧接口 `JSTypeAdapter` 与 `AbstractJSTypeAdapter` | `LOWEST` |
| `BaseJsTypeAdapter` | `LOWEST` |
| 直接实现 `JsTypeAdapter` | 没有默认值，必须自己返回 |

选优先级前先看你要覆盖的内置适配器用的是哪一级：`ItemStackAdapter` 和 `FluidStackAdapter` 是 `LOW`，`RecipeFilterAdapter` 是 `HIGH`，`CodecAdapter`、`RecipeJsonValueAdapter` 和大多数基类适配器是 `LOWEST`。

**同一级别没有确定的先后。** 注册表只是一个列表，不检查重复，也不打日志。同级适配器全部交给 GraalJS，最终谁生效由 GraalJS 决定，NekoJS 不保证。需要确定结果时，请使用不同的级别。

<a id="wiki-section-8"></a>
## 编辑器补全：inputShapes

`inputShapes()` 告诉 `/nekojs probe` 这个适配器接受哪些写法。probe 会生成一个输入别名，比如 `$MyPos_`，所有参数类型为 `MyPos` 的位置都会用它，编辑器就不会把 `'1,2'` 标成错误。

它只影响编辑器，不影响运行时。不写也能正常转换，只是 `.d.ts` 里参数仍是 `$MyPos`，传字符串会被编辑器标红。

常用的形状工厂（都在 `AdapterInputShape` 上）：

| 工厂 | 含义 | 生成的 TypeScript |
|---|---|---|
| `self()` | 目标类型本身 | `$MyPos` |
| `host(cls)` | 另一个 Java 类型 | `$Bar` |
| `string()` | 任意字符串 | `string` |
| `literal(text)` | 一个固定字符串 | `"*"` |
| `number()` / `bool()` | 数字 / 布尔值 | `number` / `boolean` |
| `arrayOf(shape)` | 数组 | `T[]` |
| `object(Slot...)` | 对象，`Slot.req` 必填，`Slot.opt` 可选 | `{ x: number, y?: number }` |
| `union(shape...)` | 某个位置接受多种写法 | `A \| B` |
| `registry(type)` | 注册表 ID，带补全 | `RegistryTypes.Item` |
| `registryTag(type)` | 注册表标签 ID（不含 `#`） | `RegistryTypes.ItemTag` |
| `namespace()` | 命名空间（模组 ID 和注册表里出现的命名空间） | `RegistryTypes.Namespace` |
| `template(prefix, hole)` / `template(prefix, hole, suffix)` | 固定前后缀加一个可补全的部分 | 模板字面量类型 |
| `raw(ts)` | 原样输出的 TypeScript 文本 | 原样 |

另外还有 `named(suffix, shape)`（给形状的一部分起名，便于复用）和 `callable(params, result)`（回调函数）。

> **不要在联合里放裸 `string`。** TypeScript 联合里只要有 `string`，其他字符串字面量就会被吞掉，编辑器不再给出任何 ID 补全。想接受 `#tag`、`@mod` 这类写法又保住补全，用模板形状：
>
> ```java
> AdapterInputShape.union(
>     AdapterInputShape.registry("Item"),                                       // minecraft:stone
>     AdapterInputShape.template("#", AdapterInputShape.registryTag("Item")),  // #minecraft:planks
>     AdapterInputShape.template("@", AdapterInputShape.namespace()),          // @create
>     AdapterInputShape.literal("*"))
> ```

`raw(ts)` 只输出文本，不会帮你收集 import，文本里的类型名必须在生成的声明里已经存在。`host(cls)` 会自动记录依赖。

<a id="wiki-section-9"></a>
## 内置适配器

下表列出常用的内置适配器，描述的是常见写法，不是每个转换器的全部规则。

| 目标类型 | 接受的写法 | 平台 |
|---|---|---|
| `ItemStack` | `'minecraft:stone'`、`'16x minecraft:stone'`、带组件的方括号字符串、`{ id 或 item, count?, components? }`、`ItemStack`、`Item`、ID | 全部 |
| `Item`（以及 `ItemLike`） | 物品 ID、`Item`、`ItemStack`、`Block`、ID | 全部 |
| `Ingredient` | ID、`'#标签'`、`'@模组'`、`'*'`、`'/正则/'`、数组、对象、物品栈、配料 | 全部 |
| `SizedIngredient` | 同上并带数量 | 仅 NeoForge |
| `FluidStack` / `FluidIngredient` / `SizedFluidIngredient` | 流体 ID、`{ fluid, amount }` | 仅 NeoForge |
| `Block` / `BlockState` | 方块 ID；`BlockState` 还接受 `Block` | 全部 |
| `BlockPos` / `Vec3` | `{ x, y, z }`、`[x, y, z]`、`BlockPos`、`Vec3`。`BlockPos` 不接受字符串 | 全部 |
| `CompoundTag` | 普通 JS 对象 | 全部 |
| `Component` | 字符串、`Component`、`Text` 生成的文本 | 全部 |
| `Identifier`（1.21.1 上是 `ResourceLocation`） | 字符串 | 全部 |
| `TagKey` | `'namespace:path'`、可带 `#`、`'registry\|tag'`、`{ type, tag }`；默认物品注册表 | 全部 |
| `Holder`、`EntityType`、`MobEffect`、`Potion`、`SoundEvent`、`ParticleType`、`BlockEntityType`、`CreativeModeTab` | 注册表 ID 字符串 | 全部 |
| 其他原版注册表类型 | 注册表 ID 字符串（自动生成） | 全部 |
| `RecipeFilter` | 筛选对象，见 [配方系统](recipe-system_cn) | 全部 |

最后一类原版注册表类型的适配器是自动生成的：`RegistryAutoAdapterScanner` 扫描 `BuiltInRegistries`（NeoForge 还有 `Registries`），给没有专用适配器的注册表类型补一个按 ID 查找的适配器，优先级 `LOWEST`。你的模组有自己的注册表 holder 类时，可以调用 `RegistryAutoAdapterScanner.installInto(registry, List.of(MyRegistries.class))`。

结构化 `ItemStack` 输入只能写 `id` 或 `item` 其中一个。`components` 用当前的注册表上下文解析：优先运行中的服务端，其次客户端连接，最后是内置注册表。移除组件用 `!命名空间:ID` 作为键。组件解析失败报 `NEKO-4054`。

<a id="wiki-section-10"></a>
## 原生配方方法的参数

配方处理器和 builder 方法通过反射调用，参数转换比普通适配器多一层：

- `List<T>`、`Collection<T>`、`Iterable<T>` 参数接受 JS 数组，每个元素按 `T` 转换。
- `Map<K, V>` 参数接受 JS 对象，键和值都会转换。
- 嵌套的以上容器会递归处理，最里层的值仍交给适配器。
- `Optional<T>` 参数会转换传入的值，`null` 变成空值。只有末尾连续的 `Optional` 参数可以省略。

已经是 Java 集合的参数原样传入，不会逐个转换元素。`Set`、具体集合类和其他泛型容器没有这层处理，只走普通的适配器映射。

配方 schema 字段如果定义了自己的 `RecipeFieldValueCodec`，整个字段由它编码，适配器不参与。详见 [配方系统](recipe-system_cn)。

<a id="wiki-section-11"></a>
## 测试

新接口的适配器不依赖 Graal，可以直接在单元测试里调用 `supports`/`convert`。要用真实的 JS 值，可以借用 NekoJS 内部的 `GraalValueView` 把 Graal `Value` 包装成 `JsValueView`：

```java
@Test
void myPosAdapterAcceptsObjectAndString() {
    try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
        MyPosAdapter adapter = new MyPosAdapter();

        JsValueView obj = GraalValueView.wrap(context.eval("js", "({ x: 1, y: 2 })"));
        assertTrue(adapter.supports(obj, ConversionContext.empty()));
        assertEquals(new MyPos(1, 2), adapter.convert(obj, ConversionContext.empty()));

        JsValueView text = GraalValueView.wrap(context.eval("js", "'3,4'"));
        assertEquals(new MyPos(3, 4), adapter.convert(text, ConversionContext.empty()));

        JsValueView bad = GraalValueView.wrap(context.eval("js", "'oops'"));
        assertFalse(adapter.supports(bad, ConversionContext.empty()));
    }
}
```

`Context` 和 `Value` 来自 NekoJS 重定位后的 Graal 包 `graal.graalvm.polyglot`。

<a id="wiki-section-12"></a>
## 平台无关的放法

适配器不强制跨平台，但内置适配器是这样分层的，你可以照着放：

- 只依赖 `JsTypeAdapter`/`BaseJsTypeAdapter` 的逻辑可以放在与加载器无关的代码里。
- 用到 Minecraft 类型（`instanceof ItemStack` 等）的部分放在 Minecraft 侧，由平台提供 host 对象。
- 只在某个加载器上存在的类型（比如 NeoForge 的流体类），适配器只在那个平台注册。

<a id="wiki-section-13"></a>
## 下一步

- [插件开发](plugin-development_cn)：`registerAdapters` 和其他插件钩子。
- [Probe 类型生成](probe-type-generation_cn)：`inputShapes` 怎样变成 `.d.ts` 别名。
- [注解体系](annotations_cn)：适配器表达不了的签名可以用 `@Overload` 手写。

<!-- wiki-nav -->

---

[上一篇: 插件开发](plugin-development_cn) · [目录](Home) · [下一篇: 事件扩展](event-extensions_cn)
