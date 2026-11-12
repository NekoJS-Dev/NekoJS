<!-- wiki-page: annotations; locale: cn -->

> **中文** · [English](annotations_us)

<a id="wiki-section-1"></a>
# 注解

NekoJS 的注解用在暴露给脚本的 Java 类上，主要做两件事：

- **控制脚本看到的成员**：改名、隐藏。运行时生效。
- **补充编辑器里的类型和说明**：只影响 `/nekojs probe` 生成的声明文件，不影响脚本能否调用。

注解都在 `com.tkisor.nekojs.api.annotation` 包里，`@PlatformAvailability` 在 `com.tkisor.nekojs.api.spec`。注解里的说明文字请用英文。

<a id="wiki-section-2"></a>
## 示例

```java
import com.tkisor.nekojs.api.annotation.*;

@Doc("Helpers for my addon.")
public class MyHelperJS {
    @Remap("add")
    @Doc("Adds two integers.")
    @Param(name = "a", value = "first number")
    @Param(name = "b", value = "second number")
    @Return("the sum")
    public int addInternal(int a, int b) {
        return a + b;
    }

    @HideFromJS
    public void resetCache() { }
}
```

脚本里调用 `MyHelper.add(1, 2)`，看不到 `addInternal` 和 `resetCache`。执行 `/nekojs probe` 后，编辑器里 `add` 会显示说明和参数文档。

<a id="wiki-section-3"></a>
## 总览

| 注解 | 用在 | 作用 | 生效位置 |
|---|---|---|---|
| `@Remap("name")` | 方法、字段 | 改成指定的脚本名 | 运行时和声明 |
| `@RemapByPrefix({...})` | 类、方法、字段 | 去掉名字的前缀 | 运行时和声明 |
| `@HideFromJS` | 类、方法、字段 | 对脚本隐藏 | 运行时和声明 |
| `@Doc("...")` | 类、方法、构造器、字段 | 说明文字，可重复 | 声明 |
| `@Param(name, value)` | 方法、构造器 | 参数说明，可重复 | 声明 |
| `@Return("...")` | 方法 | 返回值说明 | 声明 |
| `@Overload({...})` | 方法、构造器 | 额外的 TypeScript 签名，可重复 | TypeScript 声明 |
| `@DeprecatedNekojs` | 类、方法、构造器、字段 | 标记为脚本不应再使用 | 声明 |
| `@ScriptInput(Provider.class)` | 参数 | 指定参数接受的输入形状 | TypeScript 声明 |
| `@ScriptOutput("...")` | 方法 | 指定返回值的 TypeScript 类型 | TypeScript 声明 |
| `@RegisterNekoJSPlugin` | 类 | 标记插件类 | 插件发现 |
| `@PlatformAvailability` | 接口、方法 | 声明 Spec 接口要在哪些平台实现 | 编译期检查 |
| `@CalledByDynamicCode` | 类、方法、构造器 | 标记被生成的代码调用 | 仅源码 |

还有几个较少用的声明注解，见下文“其他声明注解”。

<a id="wiki-section-4"></a>
## 改名和隐藏

这三个注解在运行时改变脚本看到的成员名，生成的方法声明也使用相同的名字。

<a id="wiki-section-5"></a>
### @Remap

把一个方法或字段改成指定的脚本名：

```java
public class NbtHelperJS {
    @Remap("byte")              // byte is a Java keyword, so the Java method needs another name
    public NbtValue byteValue(Number value) { ... }
}
```

常见用途：脚本名是 Java 关键字（如内置 NBT API 的 `byte`），或者想让 Java 内部名和脚本名不同。

`@Remap` 也可以写在参数上，但目前不会生效：参数名在声明里仍取自 Java 反射。

<a id="wiki-section-6"></a>
### @RemapByPrefix

批量去掉名字前缀。写在类上时，对这个类声明的所有成员生效：

```java
@RemapByPrefix("neko$")
public interface ItemStackExtension extends ItemStackSpec {
    default String neko$getId() { ... }   // scripts see getId(), and the Bean property id
}
```

- 只去掉第一个匹配的前缀，剩下的部分不能为空。
- **大小写不变**：`@RemapByPrefix("get")` 会把 `getName` 变成 `Name`，不是 `name`。需要小写名字时，对单个成员用 `@Remap("name")`。
- 主要用途是去掉 `neko$` 前缀（见下文 Spec 接口）。去掉 `neko$` 后得到 `getId`，脚本还能通过 Bean 属性 `id` 读取。

<a id="wiki-section-7"></a>
### @HideFromJS

对脚本隐藏一个方法或字段：

```java
public class TimerJS {
    @HideFromJS
    public void close() { ... }   // engine-internal; scripts cannot call it
}
```

写在类上时，隐藏**这个类自己声明的**方法和字段。它不隐藏继承来的成员，也不隐藏类本身和构造器。

<a id="wiki-section-8"></a>
### 优先顺序

一个成员同时有多个注解时，按这个顺序决定脚本名：

1. `@HideFromJS`（成员上或声明类上）：直接隐藏。
2. 成员上的 `@Remap`。
3. 成员上的 `@RemapByPrefix`。
4. 声明类或它实现的接口上的 `@RemapByPrefix`。
5. 都没有时使用 Java 原名。

<a id="wiki-section-9"></a>
## 补全文档

这些注解只影响 `/nekojs probe` 生成的声明，不改变脚本能否调用、怎样调用。

<a id="wiki-section-10"></a>
### @Doc、@Param、@Return

```java
@Doc("Creates an item stack from an id or item-like value.")
@Param(name = "id", value = "item id like 'minecraft:stone', '#tag', or an item-like object")
@Return("the resolved stack; never null")
public static ItemStack of(Object id) { ... }
```

- `@Doc` 可以写多个，每个是一段说明。
- `@Param` 写在**方法或构造器**上，不是参数上；`name` 要和参数名对应。
- 输出为 JSDoc 的说明、`@param` 和 `@returns` 行。
- 需要给全局绑定写说明，或注解写不了的情况，用插件的 `registerTypeDocs`，见 [插件开发](plugin-development_cn)。
- 脚本里的 `ProbeEvents.modifyType` 修改优先于注解。

<a id="wiki-section-11"></a>
### @Overload

为方法额外声明 TypeScript 签名。适用于反射推不出来的情况：参数个数不同、参数声明为 `Object`、不同形式含义不同。

```java
@Doc("Creates a stack from an id.")
@Overload({"id: string"})
@Overload(value = {"item: $ItemStack", "count?: number"}, doc = "Copy with a count.")
public static ItemStack of(Object input, int count) { ... }
```

- `value` 的每一项是一个参数，按 `name: Type` 或 `name?: Type` 原样输出。
- `returns` 为空时使用反射得到的返回类型；`doc` 是这个签名的说明。
- 类型名必须在生成的模块里可见，一般用 `$Name` 形式。NekoJS 不检查也不自动导入。
- 它只是声明：Java 实现必须真的支持这些调用形式。
- 如果只是同一个参数接受多种输入（字符串、对象等），写类型适配器就够了，不需要 `@Overload`。见 [类型适配器](type-adapters_cn)。
- 只影响 TypeScript 声明，Python `.pyi` 不使用。

<a id="wiki-section-12"></a>
### @DeprecatedNekojs

告诉脚本作者不要再用某个成员。编辑器会给它加删除线。

```java
@DeprecatedNekojs(value = "Tag filters moved to Ingredient.matchTag.", replacedBy = "matchTag")
public static IngredientJS anyTag(String tag) { ... }
```

输出为 `@deprecated Tag filters moved to Ingredient.matchTag. Use matchTag instead.`。

它和 Java 的 `@Deprecated` 是分开的：只写 `@Deprecated` 不会在脚本声明里显示弃用。

<a id="wiki-section-13"></a>
### @ScriptInput 和 @ScriptOutput

当参数是 `Map<String, Object>` 这类宽泛类型，或返回值的实际结构 Java 类型表达不了时，用这两个注解写出准确的 TypeScript 类型。

```java
public boolean register(String id, @ScriptInput(PostEffectOptions.class) Map<String, Object> options) { ... }

@ScriptOutput("import('java:java/util').$Map<string, unknown> & { readonly width: number; readonly height: number }")
public Map<String, Object> measure() { ... }
```

- `@ScriptInput` 的类必须实现 `ScriptInputShapeProvider`，并有公开的无参构造器。它的 `inputShape()` 用 `AdapterInputShape` 描述输入形状。
- `@ScriptOutput` 写一个 TypeScript 类型表达式，也会作用于 Bean getter 和继承的方法。引用外部类型用内联 `import('module').Type`。返回值仍可调用 Java 方法时，像上例一样和 Java 类型取交叉类型。
- 两者都只影响 TypeScript 声明，不转换也不检查运行时的值。声明必须和真实行为一致。
- `@ScriptOutput` 的表达式为空时，生成报 `NEKO-4064`。

<a id="wiki-section-14"></a>
### 其他声明注解

| 注解 | 用在 | 作用 |
|---|---|---|
| `@ScriptNullable` | 方法、参数 | 注册表 builder 的 getter 或 setter 参数可以是 `null`，声明里加上 `\| null` |
| `@ScriptInternalConstructor` | 构造器 | 不在声明里输出这个构造器，用于只由引擎创建的对象。不改变运行时能否调用 |
| `@ScriptTypeView(Interface.class)` | 类 | 返回值和事件对象在声明里显示为指定的公开接口，而不是实现类 |
| `@ScriptNativeBridge` | 类 | 允许 `stable()` API 模块暴露 Minecraft 或加载器类型 |
| `@ContractReceiver` | 类 | NekoJS 内部使用，标记可作为扩展方法接收者的数据类型 |

<a id="wiki-section-15"></a>
## @RegisterNekoJSPlugin

在 NeoForge 上标记插件类。Fabric 还需要 `nekojs` entrypoint。参数 `priority`（默认 `1000`，大的先运行）、`clientOnly`（默认 `false`）、`requiredMods`（默认 `{}`，全部存在才加载）。详见 [插件开发](plugin-development_cn)。

<a id="wiki-section-16"></a>
## Spec 接口和 @PlatformAvailability

NekoJS 用 Spec 接口描述“每个平台都要实现的一组脚本方法”，再由各平台的 Extension 接口实现。方法名加 `neko$` 前缀，避免和 Minecraft 自己的方法冲突，再用 `@RemapByPrefix("neko$")` 去掉前缀给脚本使用。

```java
// Spec: the cross-platform script surface
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.ALL)
public interface EntitySpec {
    String neko$getId();
    void neko$kill();
}

// Platform Extension: implements the spec with Minecraft types
@RemapByPrefix("neko$")
public interface EntityExtension extends EntitySpec {
    @Override
    default String neko$getId() { ... }

    @Override
    default void neko$kill() { ... }
}
```

`Scope` 的取值：

| 取值 | 含义 |
|---|---|
| `ALL` | 所有平台都要实现 |
| `NF_ONLY` | 只有 NeoForge（1.21.1 和 26.x） |
| `NF26_ONLY` | 只有 NeoForge 26.x |
| `CR_ONLY` | 旧的 Cleanroom 标记，目前没有对应的受支持构建 |

Fabric 的支持情况需要单独确认，`@PlatformAvailability` 本身不会实现任何方法。

**编译期检查**：NekoJS 自己的 NeoForge 构建接入了 `common-api-processor` 注解处理器，它会检查：

- 直接继承 Spec 的 Extension 接口必须自己实现 Spec 的每个 `neko$` 方法，漏掉就编译报错。
- 编译时传了 `-Anekojs.platform=nf26` 或 `nf121`（`cr` 也接受）后，还会检查当前平台要求的 Spec 是否都有 Extension 实现。旧值 `nf` 会报错。

NekoJS 的 Fabric 构建目前没有接入这个处理器。第三方插件通常也没有，这时 Spec 接口只是一种写法约定：`neko$` 前缀和改名照样有用，但没有编译期检查。

<a id="wiki-section-17"></a>
## @CalledByDynamicCode

标记会被生成的 JS 代码或反射调用的类、方法和构造器，提醒维护者不要当成无用代码删掉。它只保留在源码里，运行时没有任何作用。IDE 是否因此不再提示“未使用”取决于具体工具。

<a id="wiki-section-18"></a>
## 注意事项

- **公开字段的声明不受改名和隐藏影响。** 运行时 `@Remap`、`@RemapByPrefix`、`@HideFromJS` 对字段都生效，但生成的声明里字段仍是 Java 原名，标了 `@HideFromJS` 也会出现。需要时用脚本里的 `ProbeEvents.modifyType` 调整声明。
- **声明文件不是安全边界。** 在声明里隐藏或改名不会限制运行时访问；要真正隐藏，用 `@HideFromJS`。

下一步：

- [Probe 类型生成](probe-type-generation_cn)：注解怎样变成 `.d.ts`。
- [插件开发](plugin-development_cn)：用 `registerTypeDocs` 补充声明。

<!-- wiki-nav -->

---

[上一篇: 事件扩展](event-extensions_cn) · [目录](Home) · [下一篇: 项目架构](project-architecture_cn)
