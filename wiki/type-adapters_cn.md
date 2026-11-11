<!-- wiki-page: type-adapters; locale: cn -->

> **中文** · [English](type-adapters_us)

<a id="wiki-section-1"></a>
# 类型适配器

> 适配器让 Java 方法**自动接受多种 JS 输入形态**——比如一个 Java 方法声明参数是 `ItemStack`，脚本可以传字符串 `'minecraft:stone'`、传 `{id, count}` 对象、传 `ItemStack` 实例，适配器负责把它们都转成 `ItemStack`。适配器有两代 SPI：**新 SPI `JsTypeAdapter`（推荐）**与**旧 SPI `JSTypeAdapter`（Graal `Value`，兼容保留）**，见下文「两代 API」。

<a id="wiki-section-2"></a>
## 为什么需要适配器

GraalJS 的 `HostAccess` 提供了 `targetTypeMapping(C, T, predicate, function)` 机制：当 Java API 期望类型 `T` 时，GraalJS 会自动把传入的 JS 值（`Value`）通过 mapping 转成 `T`。NekoJS 的 `JSTypeAdapter` 就是这个 mapping 的载体。

NekoJS 在 bootstrap 时通过 `NekoSharedHostAccess` 为每个已注册适配器安装 `Value.class` → 目标类映射，使用其接受谓词、转换器和优先级。脚本 Context 共用这份 HostAccess 配置。新 SPI 通过 `NewAdapterBridge` 接入同一映射；两代 SPI 注册的都不是参数化 Java `Type`。见[映射注册](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/core/NekoSharedHostAccess.java#L49-L65)、[桥接层](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/core/bridge/NewAdapterBridge.java#L18-L39)和 [Context 配置](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/core/NekoSandboxFactory.java#L138)。

<a id="wiki-section-3"></a>
### 原生配方参数：guest 容器与 host 对象

反射调用的原生配方 handler 和 builder 方法使用 `RecipeReflectionUtil.convertArgs`，读取方法的泛型参数类型。就集合而言，它显式处理的 guest 容器转换包括：

- 参数化的 `List<T>`、`Collection<T>` 和 `Iterable<T>`：遍历 guest 数组，生成新的 Java list。
- 参数化的 `Map<K, V>`：遍历 guest 对象成员或 hash entries，生成新的 Java map，同时转换键和值。
- 嵌套的受支持容器会递归处理；叶值仍通过 `Value.as(rawClass)` 使用当前 Context 中由 runtime root 注册的适配器。`Optional<T>` 会递归转换传入的 guest 值；guest null 转为空值，只有末尾连续的 `Optional` 参数可以省略。

已有的 Java host 集合**不会被重建，也不会逐元素转换**：它走原始 `Value.as` 路径，类型可赋值时保留对象身份。`Set`、具体集合类及其他不受支持的泛型容器同样回退到 Graal 的原始 `Value.as`；上述显式遍历**不代表**它们都支持 guest 输入。任意 `Box<T>` 适配器注册的仍是 `Box.class`；NekoJS 不会推断其内部 `T` 的转换。

来源：[转换与原始回退](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeReflectionUtil.java#L70-L152)、[末尾可选参数](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeReflectionUtil.java#L183-L188)、[handler 分派](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/RecipeNamespaceProxy.java#L108-L122)和 [builder 分派](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/wrapper/SchemaRecipeBuilder.java#L230-L245)。[原生运行时测试](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/test/java/com/tkisor/nekojs/wrapper/NativeRecipeHandlerRuntimeTest.java#L78-L147)覆盖 guest 数组/map 与 host 身份；[原始泛型适配器测试](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/test/java/com/tkisor/nekojs/probe/ir/TypeScriptCollectionInputTest.java#L259-L308)展示了 `Box<T>` 的限制。

<a id="wiki-section-4"></a>
## 内置适配器

NekoJS 已注册了大量适配器（见 `src/main/java/com/tkisor/nekojs/js/type_adapter/`）：

| 适配器 | 目标类型 | 接受的 JS 输入 |
|---|---|---|
| `ItemAdapter` / `ItemStackAdapter` | `ItemStack` | 字符串 id（`"minecraft:stone"`）、`"1x minecraft:stone"`、`{id, count}`、`ItemStack` |
| `IngredientAdapter` | `Ingredient` | 字符串、tag（`"#minecraft:planks"`）、数组、`Ingredient` |
| `SizedIngredientAdapter` | `SizedIngredient` | 同上 + 数量 |
| `BlockAdapter` | `Block` | 字符串 id |
| `BlockPosAdapter` | `BlockPos` | `{x, y, z}`、`[x, y, z]`、字符串 |
| `Vec3Adapter` | `Vec3` | `{x, y, z}`、`[x, y, z]` |
| `CompoundTagAdapter` | `CompoundTag` | JS 对象（自动转 NBT） |
| `ComponentAdapter` | `Component` | 字符串、`MutableComponent` |
| `FluidStackAdapter` / `FluidIngredientAdapter` / `SizedFluidIngredientAdapter` | 流体类型 | 字符串、`{fluid, amount}` |
| `IdentifierAdapter` | `Identifier`/`ResourceLocation` | 字符串 |
| `TagKeyAdapter` | `TagKey` | `"namespace:path"`、可选 `#`、`"registry\|tag"` 或 `{type, tag}`（默认物品注册表） |
| `EntityTypeAdapter` | `EntityType` | 字符串 id |
| `MobEffectAdapter` / `PotionAdapter` / `ParticleTypeAdapter` / `SoundEventAdapter` / `CreativeModeTabAdapter` / `BlockEntityTypeAdapter` | 各注册表类型 | 字符串 id |
| `CodecAdapter<T>` | 显式注册的目标类 | 字符串或 guest 对象/数组经已注册 codec 解析；匹配的 host 实例 |
| `DataComponentsAdapter`（静态工具，非注册适配器） | `DataComponentPatch` | JS 对象 → data component patch（NeoForge / Fabric 26.x） |
| `RecipeFilterAdapter` | `RecipeFilter` | JS 对象（见 [配方系统 - 过滤器](recipe-system_cn#wiki-section-3)） |
| `RecipeJsonValueAdapter` | `RecipeJsonValue` | — |

<a id="wiki-section-5"></a>
### 配方字段 codec 是独立的转换边界

`CodecAdapter<T>` 是显式注册、优先级为 `LOWEST` 的适配器，其 codec 通过 `JsonOps.INSTANCE` 解析 JSON。它不会自动为所有带 codec 的类型发现适配器。schema 字段的 `RecipeFieldValueCodec` 则编码**整个字段**：`RecipeSchemaHost.convertField` 会先调用该 encoder，再考虑普通标量/数组路径。没有字段 codec 时，默认数组路径还会把单值包装成一个元素；自定义 encoder 自行决定接受哪些形态。

字段 codec 的 `inputShape` 与可选的名义 `inputType` 描述工具侧输入，并不是另一份 HostAccess 注册。例如，原生 Minecraft schema scanner 为原版合成的原料和键提供整个字段的 list/map encoder，通过 `host.encodeField` 处理元素。不要仅凭生成的输入别名推断运行时转换。来源：[CodecAdapter](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/src/main/java/com/tkisor/nekojs/js/type_adapter/CodecAdapter.java#L24-L68)、[字段 codec 契约](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/api/recipe/definition/RecipeFieldValueCodec.java#L11-L24)、[整个字段的分派](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/api/recipe/RecipeSchemaHost.java#L42-L62)和[原生合成字段 encoder](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/src/main/java/com/tkisor/nekojs/api/recipe/definition/MinecraftRecipeSchemaScanner.java#L57-L110)。

<a id="wiki-section-6"></a>
## 两代 API

适配器现在有**两代 SPI**：

- **新 SPI（推荐）**：`JsTypeAdapter<T>`（`common/src/main/java/com/tkisor/nekojs/api/data/JsTypeAdapter.java`，对外契约包 `com.tkisor.nekojs.api.*`）——**无 MC/Graal 依赖**。签名：`targetType()` / `supports(JsValueView, ConversionContext)` / `convert(JsValueView, ConversionContext)` / `precedence()`。推荐基类是 `BaseJsTypeAdapter<T> implements JsTypeAdapter<T>`（模板方法：`fromString`/`fromHostObject`/`acceptOther`/`fromOther`）。已迁移示例：`src/main/java/com/tkisor/nekojs/js/type_adapter/ComponentAdapter.java`。
- **旧 SPI（兼容保留）**：`common` 的 `JSTypeAdapter<T>`/`AbstractJSTypeAdapter`——依赖 Graal `Value`（`test(Value)`/`apply(Value)`）。

两代适配器都经同一个注册表进场：`JSTypeAdapterRegistry`（`common/src/main/java/com/tkisor/nekojs/api/data/JSTypeAdapterRegistry.java`）同时提供 `register(JSTypeAdapter<T>)` 和 `register(JsTypeAdapter<T>)` 两个重载，插件拿到的就是它。

**新适配器请优先用新 SPI**；旧 SPI 继续可用（下文旧示例照常编译），两代的 `precedence()`/`getPrecedence()` 都返回 `ConversionPrecedence`。

<a id="wiki-section-7"></a>
### 必须实现什么

| SPI | 没有 default，必须实现 | 有 default，可以不管 |
|---|---|---|
| 新 `JsTypeAdapter<T>` | `targetType()`、`supports(JsValueView, ConversionContext)`、`convert(...)`、`precedence()` | `inputShapes()`（默认空表） |
| 旧 `JSTypeAdapter<T>` | `getTargetClass()`、`test(Value)`、`apply(Value)` | `getPrecedence()`（默认 `LOWEST`）、`inputShapes()`、`syntaxDoc()` |
| `BaseJsTypeAdapter<T>` | `fromHostObject(Object)`，以及构造器传 `targetType` | 其余全部（`precedence()` 默认 `LOWEST`，`fromString`/`fromOther` 默认抛 `ValueConversionException`，`acceptNull`/`acceptOther` 默认 `false`，`supportsString` 默认 `true`） |

基类只是省事的模板，**不是必经之路**：注册表接的是接口，直接 implements 接口完全等价。连类都不想写的话，`JSTypeAdapterRegistry` 还有个 default 重载 `register(Class<T> target, Predicate<Value> filter, Function<Value, T> converter)`，三个 lambda 就是一个旧 SPI 适配器。

<a id="wiki-section-8"></a>
## 写一个自定义适配器

下面三种 adapter 是**替代实现**，不要作为同名类一起编译。每个实现都使用同包下单独的 `MyPos.java`：

```java
package com.example.myaddon;

public record MyPos(int x, int y) {}
```

<a id="wiki-section-9"></a>
### 步骤 1（旧 SPI）：实现 `JSTypeAdapter<T>`

```java
package com.example.myaddon;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import graal.graalvm.polyglot.Value;
import java.util.List;

public class MyPosAdapter implements JSTypeAdapter<MyPos> {

    @Override
    public Class<MyPos> getTargetClass() {
        return MyPos.class;
    }

    @Override
    public ConversionPrecedence getPrecedence() {
        return ConversionPrecedence.LOW;   // 低于内置；多个适配器匹配同一类型时高优先级赢
    }

    @Override
    public boolean test(Value value) {
        // 判断这个 JS 值能否被本适配器处理
        return value.hasMembers() && value.hasMember("x") && value.hasMember("y");
    }

    @Override
    public MyPos apply(Value value) {
        int x = value.getMember("x").asInt();
        int y = value.getMember("y").asInt();
        return new MyPos(x, y);
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        // 给 probe 生成 $MyPos_ 输入别名用；object(Slot...) 声明接受的字段
        return List.of(
            AdapterInputShape.object(
                AdapterInputShape.Slot.req("x", AdapterInputShape.number()),
                AdapterInputShape.Slot.req("y", AdapterInputShape.number()))
        );
    }
}
```

<a id="wiki-section-10"></a>
### 步骤 2（旧 SPI）：或继承 `AbstractJSTypeAdapter`（模板方法，更省事）

```java
package com.example.myaddon;

import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import graal.graalvm.polyglot.Value;

public class MyPosAdapter extends AbstractJSTypeAdapter<MyPos> {
    @Override public Class<MyPos> getTargetClass() { return MyPos.class; }

    @Override
    protected MyPos fromString(String text) {
        String[] parts = text.split(",");
        return new MyPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    @Override
    protected MyPos fromHostObject(Object host) {
        return host instanceof MyPos position ? position : null;
    }

    @Override
    protected boolean acceptOther(Value value) {
        return value.hasMembers() && value.hasMember("x") && value.hasMember("y")
                && value.getMember("x").fitsInInt() && value.getMember("y").fitsInInt();
    }

    @Override
    protected MyPos fromOther(Value value) {
        return new MyPos(value.getMember("x").asInt(), value.getMember("y").asInt());
    }
}
```

`AbstractJSTypeAdapter` 的 `final test`/`apply` 自动分派到 `defaultValue`/`acceptNull`/`fromString`/`fromHostObject`/`acceptOther`/`fromOther`。设计为 MC 无关（`fromHostObject` 收到的是 `Object`，平台代码做 `instanceof`）。它在 `common/`，依赖 Graal `Value`。

<a id="wiki-section-11"></a>
### 步骤 2b（新 SPI，推荐）：继承 `BaseJsTypeAdapter<T>`

新 SPI 的基类 `BaseJsTypeAdapter<T>`（同在契约包 `api.data`，无 MC/Graal 依赖）提供同一套模板方法，`supports`/`convert` 已按 `JsValueView` + `ConversionContext` 实现好：

```java
package com.example.myaddon;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.BaseJsTypeAdapter;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.api.data.JsValueView;
import java.util.List;

public class MyPosAdapter extends BaseJsTypeAdapter<MyPos> {
    public MyPosAdapter() {
        super(MyPos.class);
    }

    @Override
    protected MyPos fromString(String s) {
        // 将 "1,2" 转为 MyPos(1,2)
        String[] parts = s.split(",");
        return new MyPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    @Override
    protected MyPos fromHostObject(Object host) {
        // 只有 supports() 判定过 host instanceof MyPos 时才会走到这里
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
            AdapterInputShape.object(
                AdapterInputShape.Slot.req("x", AdapterInputShape.number()),
                AdapterInputShape.Slot.req("y", AdapterInputShape.number()))
        );
    }
}
```

写法与旧 SPI 几乎一一对应（`test`/`apply` → `supports`/`convert`，`getPrecedence()` → `precedence()`）。迁移实例见 `src/main/java/com/tkisor/nekojs/js/type_adapter/ComponentAdapter.java`。

> 基类的 host object 分支比旧基类**收窄**了：`supports` 对 host object 只在 `targetType.isAssignableFrom(host.getClass())` 时放行，所以 `fromHostObject` 收到的一定已经是目标类型，不需要（也不可能）在里面处理 `Map` 之类的其它形态。不匹配的 Java host object 会直接被拒绝；普通非 host 的 JS 对象才走 `acceptOther`/`fromOther`。而且 `fromHostObject` 返回 `null` **不是**「不识别」的合法表达：基类会把它当失败，抛 `ValueConversionException`（`BaseJsTypeAdapter.convert`）。要表达「不接受」就让 `supports` 返回 false。

<a id="wiki-section-12"></a>
### 步骤 3：注册

```java
@Override
public void registerAdapters(JSTypeAdapterRegistry registry) {
    registry.register(new MyPosAdapter());
}
```

> `registerAdapters` 的参数是 `JSTypeAdapterRegistry`，它同时有 `register(JSTypeAdapter<T>)` 和 `register(JsTypeAdapter<T>)` 两个重载——上面两代适配器都能直接传进去。

注册后，声明 `void foo(MyPos pos)` 的 Java 方法可接收 `{x: 1, y: 2}`。两个基类实现还支持 `"1,2"`；第一个直接实现接口的示例仅接收对象。这些是输入数据形状，不是回调 builder 配置。

<a id="wiki-section-13"></a>
## AdapterInputShape（probe 别名）

`inputShapes()` 告诉 probe 这个适配器接受什么形态的输入，probe 据此生成 `$MyPos_` 类型别名，让脚本的 `.d.ts` 准确反映「这个参数可以传字符串或对象」。

它是**纯编辑器面的**，两代 SPI 都有 default 空实现：不写照样能转换，代价只是 `.d.ts` 里该参数退化成目标类型本身，脚本传字符串会被编辑器标红（运行时仍然正常）。

`AdapterInputShape` 是 sealed 接口，14 个变体（见 `api/AdapterInputShape.java`）：

| 变体 | 含义 | 渲染成 |
|---|---|---|
| `String` | 接受任意字符串（自由文本字段） | `string` |
| `Literal(text)` | 单个字符串字面量 | `"*"` |
| `Number` | 接受数字 | `number` |
| `Boolean` | 接受布尔 | `boolean` |
| `Self` | 接受目标类型本身 | `$Foo` |
| `Host` | 接受某个 Java host 类型 | `$Bar` |
| `ArrayOf(shape)` | 接受数组 | `T[]` |
| `Object(field...)` | 接受对象（指定字段） | `{ a?: ..., b: ... }` |
| `Union(shape...)` | 单个字段接受多种写法 | `(A \| B)` |
| `Registry(typeName)` | 接受注册表 id 字符串 | `RegistryTypes.Item` |
| `RegistryTag(typeName)` | 接受该注册表的标签 id（不含 `#`） | `RegistryTypes.ItemTag` |
| `Namespace` | 接受 id 命名空间（mod 表 + 注册表命名空间） | `RegistryTypes.Namespace` |
| `Template(prefix, hole, suffix)` | 固定前后缀 + 可补全的洞 | <code>\`#${RegistryTypes.ItemTag}\`</code> |
| `Raw` | 其它原始形态 | 原样输出 |

`AdapterAliasGenerator` 把这些渲染成 `$Foo_` 联合类型。顶层 `Self` 保留 host `$Foo`；嵌套的 `Self` 和 `Host` 形状可以使用输入别名，以描述嵌套位置经过转换的值。这只是元数据，并不是递归转换任意 host 对象的指令（[形状渲染](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/AdapterAliasGenerator.java#L202-L232)）。

<a id="wiki-section-14"></a>
### 名义 host 类型与 guest 输入别名

对原生 Java 参数，host `T` 与 guest 输入 `T_` 扮演不同角色。若 `$Payload_` 包含字符串，`List<Payload>` 参数接受 host `$List<$Payload>` 或 guest `$Payload_[]`，**不是**元素仍需转换的 host `$List<$Payload_>`。嵌套的受支持容器遵循同一区分；返回值保留 host 类型。

规范的集合输入别名是 `$List_`、`$Collection_`、`$Iterable_`、`$Map_` 和 `$Set_`。当一组类型实参能同时表示 host 与 guest 两个分支时，probe 复用命名集合别名；host 与输入实参不同时，分别渲染两个分支。Map 的对象输入使用可选的 `Record` 键，并将其限制为 TypeScript `PropertyKey`；这种编辑器表示并不承诺覆盖运行时 hash map 的所有键。`$Set_` 仍只接受 host，具体集合类的别名也是如此；名称带下划线本身并不承诺 guest 转换。见[集合形状](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/probe/types/TypeAliasRegistry.java#L79-L142)、[规范别名选择](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/probe/types/TypeAliasRegistry.java#L233-L254)和[具体类的 host 别名](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/IndexFileGenerator.java#L382-L439)。

泛型适配器别名保留显式的原生 host 实参：声明为 `Box<Payload>` 的参数使用 `$Box_<$Payload>`，不是 `$Box_<$Payload_>`。原始 Java 类型仍使用擦除后的默认值（`T = any`），而仅含类信息的 `host(Box.class)` 形状无法提供特定类型实参。这些擦除形式以及 `$Box_<T>` 一类别名，都不会让运行时适配器推断内部 `T` 的转换方式。由转换器负责的 schema `inputType` 声明是另一份契约，可以有意投影输入实参。来源：[注册别名实参](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/probe/types/TypeAliasRegistry.java#L265-L284)、[泛型别名默认值](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/AdapterAliasGenerator.java#L137-L155)、[仅类信息的 host 形状擦除](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/AdapterAliasGenerator.java#L216-L228)、[原生参数与声明输入的投影](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/main/java/com/tkisor/nekojs/probe/ir/TypeScriptClassRenderer.java#L924-L1008)和[契约测试](https://github.com/NekoJS-Dev/NekoJS/blob/stonecutter/common/src/test/java/com/tkisor/nekojs/probe/ir/TypeScriptDeclaredInputModelTest.java#L17-L41)。

> **别在联合里放裸 `string`。** TypeScript 一旦看到联合里有 `string`，就会把其它字符串字面量成员吞掉，编辑器不再给任何 id 补全——这正是早期 `$Ingredient_` 里原料位置没补全、而 `$ItemStack_` 里结果位置有补全的原因。想同时接纳 `@mod`、`*`、`/regex` 这类非 id 语法又保住补全，把它们写成字面量/模板字面量类型：
>
> ```java
> registry("Item"),                     // minecraft:stone（有补全）
> template("#", registryTag("Item")),   // #minecraft:planks（有补全）
> template("@", namespace()),           // @create（有补全）
> literal("*"),                          // *
> template("/", string())               // /wool$/（正则无穷，无法补全）
> ```
>
> 这条限制是**按联合**生效的，不是按对象：`{ output: RegistryTypes.Item, id: string }` 里的 `id` 用裸 `string` 不影响 `output` 的补全。
>
> `RegistryTypes.XTag` 由 probe 从注册表当前已绑定的标签生成；`RegistryTypes.Namespace` 是加载器 mod 列表（`NekoCatalogPlatformProvider.modIds()`）与全部注册表条目 id 的 `:` 前缀的并集——前者覆盖没往该注册表注册东西的 mod，后者覆盖脚本/数据包自造的命名空间。两者为空时都回退成 `string`，模板类型因此退化为「任意字符串」而不是 `never`。

<a id="wiki-section-15"></a>
## Precedence（优先级）

多个适配器匹配同一目标类型时，按 `precedence()`（旧 SPI `getPrecedence()`）决定。`ConversionPrecedence` 枚举只有 **4 个级别**（没有 `NORMAL`），顺序 `LOWEST < LOW < HIGH < HIGHEST`：

| 级别 | 用途 |
|---|---|
| `HIGHEST` | 平台/核心适配器 |
| `HIGH` | 内置通用适配器 |
| `LOW` | 兜底 |
| `LOWEST` | 最后手段 |

「用途」这一列是内置适配器的自我分层，**不是校验规则**：没有任何代码检查你是谁、用了哪一级。缺省值分两种情况——旧 SPI 的 `getPrecedence()` 与 `BaseJsTypeAdapter` 都默认 `LOWEST`；新 SPI 直接 implements `JsTypeAdapter` 时 `precedence()` 没有 default，必须自己写出来。

<a id="wiki-section-16"></a>
### 冲突策略

代码这边能确定的只有一条：`JSTypeAdapterRegistry.Impl` 就是一个 `ArrayList`，同一 `(目标类型, precedence)` 组合注册时**不做任何去重或冲突校验**，不报错、不打日志。注册表按顺序把全部适配器喂给 Graal 的 `HostAccess.targetTypeMapping`，注册顺序跟随插件加载顺序（priority 越高越先加载）。

同 precedence 层级内**最终哪个胜出由 Graal 决定，不由本仓决定**——这里没有相关代码，也没有测试钉住它。所以别依赖同层级的相对顺序，要确定性就用不同的 precedence。

下面几条是据此得出的建议，没有代码执行它们：

- 尽量别让多个 addon 在同一类型上声明相同 precedence（那等于把「谁生效」交给插件加载顺序）。
- addon 适配器用 `LOW`/`LOWEST` 比较安全——内置通用适配器在 `HIGH`，平台核心在 `HIGHEST`，你选低一级就是明确让内置优先；反过来想**盖掉**内置行为就得选比它高的级别，这是可行的、没人拦你。

<a id="wiki-section-17"></a>
## 测试适配器

下面针对步骤 2 的旧 SPI `AbstractJSTypeAdapter` 示例测试对象和字符串转换。

```java
@Test
void myPosAdapterConvertsObjectAndString() {
    MyPosAdapter adapter = new MyPosAdapter();
    Value obj = Value.asValue(Map.of("x", 1, "y", 2));
    assertTrue(adapter.test(obj));
    assertEquals(new MyPos(1, 2), adapter.apply(obj));

    Value str = Value.asValue("3,4");
    assertTrue(adapter.test(str));
    assertEquals(new MyPos(3, 4), adapter.apply(str));
}
```

<a id="wiki-section-18"></a>
## 平台无关性

适配器**能**做到跨平台，但没有机制要求它跨平台——一个只在某个加载器上编译的适配器照样能注册、能工作。下面是内置适配器为了跨平台而采用的分层，你想复用这套排布就照着放：
- 新 SPI `JsTypeAdapter`/`BaseJsTypeAdapter` 在契约包 `com.tkisor.nekojs.api.data`，无 MC/Graal 依赖；旧 SPI `JSTypeAdapter` 在 `com.tkisor.nekojs.api`，`AbstractJSTypeAdapter` 在 `com.tkisor.nekojs.api.data`（均依赖 Graal `Value`），无 MC 依赖。这两层的「无 XX 依赖」由 guardLint 的边界规则对**本仓**强制，你的 mod 不受它约束。
- MC 相关逻辑（`instanceof ItemStack` 等）在 `fromHostObject` 里，平台提供 host 对象。
- 平台专属适配器（如 `ItemAdapter`）放在平台 sourceSet。

<a id="wiki-section-19"></a>
## 下一步

- [插件开发](plugin-development_cn) —— `registerAdapters` 钩子。
- [Probe 类型生成](probe-type-generation_cn) —— `AdapterInputShape` 如何变成 `.d.ts` 别名。
- [注解体系](annotations_cn) —— `@Overload` 手写适配器表达不了的签名；适配器声明的 `inputShapes()` 会自动放宽参数类型，见上。
- 维护者完整 Java/JS 示例与目录边界：`docs/maintenance/adding-events-adapters.md`。

<!-- wiki-nav -->

---

[上一篇: 插件开发](plugin-development_cn) · [目录](Home) · [下一篇: 事件扩展](event-extensions_cn)
