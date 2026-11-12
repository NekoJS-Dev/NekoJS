<!-- wiki-page: annotations; locale: cn -->

> **中文** · [English](annotations_us)

<a id="wiki-section-1"></a>
# 注解体系

> NekoJS 的注解控制 JS 可见性与命名（运行时由 `MemberVisibilityQuery`/`NekoJSMemberRemapper` 反射层消费）。所有注解在 `com.tkisor.nekojs.api.annotation` 包（引擎契约包）（`@PlatformAvailability` 在 `api.spec`）。
> 编写或修改注解 Javadoc 时使用英文，本页用中文解释。

<a id="wiki-section-2"></a>
## 注解总览

| 注解 | 目标 | 作用 |
|---|---|---|
| `@RegisterNekoJSPlugin` | TYPE | 标记插件类 |
| `@Remap("jsName")` | METHOD/FIELD/PARAMETER | 重命名运行时方法/字段；合法的 PARAMETER 目标暂无命名消费者 |
| `@RemapByPrefix({"get", "is"})` | TYPE/METHOD/FIELD | 按前缀批量重命名 |
| `@HideFromJS` | TYPE/METHOD/FIELD | 对 JS 不可见 |
| `@CalledByDynamicCode` | TYPE/METHOD/CONSTRUCTOR | 为维护者/IDE 工具记录动态调用（`RetentionPolicy.SOURCE`） |
| `@PlatformAvailability(Scope.ALL\|NF_ONLY\|NF26_ONLY\|CR_ONLY)` | TYPE/METHOD | Spec 接口的平台可用性声明 |
| `@Doc("...")` | TYPE/METHOD/CONSTRUCTOR/FIELD | 英文文档，渲染为 probe `.d.ts` JSDoc；可重复 |
| `@Param(name, value)` | METHOD/CONSTRUCTOR | 参数文档 → `@param` 行；可重复 |
| `@Return("...")` | METHOD | 返回值文档 → `@returns` 行 |
| `@Overload({"id: string"})` | METHOD/CONSTRUCTOR | 手写 `.d.ts` 附加签名；可重复 |
| `@ScriptInput(MyShape.class)` | PARAMETER | 由具有 public 无参构造器的 `ScriptInputShapeProvider` 提供结构化 TypeScript 输入 |
| `@ScriptOutput("{ readonly width: number }")` | METHOD | 为 Java 反射无法描述的返回值声明明确的 TypeScript 类型 |
| `@DeprecatedNekojs` | TYPE/METHOD/CONSTRUCTOR/FIELD | 脚本面弃用 → JSDoc `@deprecated`（编辑器划线） |

除 `@CalledByDynamicCode` 使用 `SOURCE` 外，保留策略为 `RUNTIME`。并非每个注解都有 `@Documented`；命名/可见性/插件/平台注解不能一概而论，该元注解也不决定脚本可调用性。

<a id="wiki-section-3"></a>
## 哪些注解有强制力

按「不写会怎样」分四类：

| 类别 | 注解 | 不写/写错的后果 |
|---|---|---|
| 改变运行时行为 | `@Remap`、`@RemapByPrefix`、`@HideFromJS` | 经 Graal 的 `MemberRemapper.CHAIN`（`NekoJSMemberRemapper`）影响运行时方法/字段查找；无匹配时交给后续 remapper 和宿主访问策略。probe 共享方法命名/隐藏规则，但字段和整类隐藏有下文所述限制 |
| 编译期强制 | `@PlatformAvailability` + Spec 接口 | 见下节：漏覆写 `neko$` 方法则编译 ERROR，但**只在接了 annotation processor 的构建里** |
| 只影响生成的文档 | `@Doc`、`@Param`、`@Return`、`@Overload`、`@DeprecatedNekojs` | 不写就是 JSDoc 为空 / 无附加签名。对能不能调用毫无影响；`@Overload` 会影响类型检查（多了可用的签名） |
| 完全无运行时消费 | `@CalledByDynamicCode` | `RetentionPolicy.SOURCE`，记录动态使用且无运行时消费者；IDE 是否消除 unused 警告取决于具体工具 |

`@RegisterNekoJSPlugin` 用于 NeoForge 注解发现；Fabric addon 需要 manifest 的 `nekojs` entrypoint，共享管理器允许缺失注解元数据。见[插件开发](plugin-development_cn)。

---

<a id="wiki-section-4"></a>
## `@RegisterNekoJSPlugin`

标记插件类（见 [插件开发](plugin-development_cn)）。属性：`clientOnly()`（默认 `false`）、`requiredMods()`（默认 `{}`，AND 语义）、`priority()`（默认 `1000`，数值大先加载；内置 `NekoJSPlugin.CORE_PRIORITY = Integer.MAX_VALUE` 使用最大优先级，相同数值仍按类名/owner 排序）。

<a id="wiki-section-5"></a>
## `@Remap("jsName")`

把 Java 方法/字段重命名成 JS 可见的名字。`PARAMETER` 是合法注解目标，但运行时 remapper 不重命名参数，probe 仍使用反射参数名或 `argN`。

```java
public class FooJS {
    @Remap("addItem")            // 脚本里调 foo.addItem(...) 而不是 foo.addItemInternal(...)
    public void addItemInternal(ItemStack stack) { ... }
}
```

> 用途：Java 关键字冲突（如 `NbtFacade` 的 `byteValue` → JS `byte`）、Java 命名规范与 JS 习惯不一致时。运行时 `MemberRemapper` 消费。

<a id="wiki-section-6"></a>
## `@RemapByPrefix({"get", "is"})`

剥离 `value()` 中的前缀，但**保留剩余部分的大小写**：`getX` 变成 `X`，不是 `x`。只剥离第一个匹配前缀，并要求剩余名称非空。这是成员重命名，不是 Bean 属性别名生成；需要精确的 `x` 名称时使用逐成员的 `@Remap("x")`。另一用途是剥离 Spec 接口的 `neko$` 前缀；见 [Script API 风格](script-api-style_cn)。

**解析顺序与 Bean 属性**：隐藏优先于成员 `@Remap`，之后依次尝试成员前缀、声明类型/接口前缀、fallback。Bean 别名单独从最终可见的访问器名称推导：`neko$getX` → `getX` 可以得到 `x`，仅把 `getX` 剥成 `X` 则不会。只有 getter 的别名只读，写入需要匹配的可见 setter。

```java
@RemapByPrefix({"get", "is"})   // Strip getX to X and isX to X; casing is preserved
public class FooJS { ... }

// Spec 接口模式：neko$ 前缀在平台 Extension 上批量剥离为 JS 名
@RemapByPrefix("neko$")
public interface ItemStackExtension extends ItemStackSpec { ... }
```

<a id="wiki-section-7"></a>
## `@HideFromJS`

在运行时成员查找中隐藏标注的方法/字段；probe 也会省略隐藏的方法，公开字段声明则有下述限制。

```java
public class FooJS {
    @HideFromJS
    public void internalHelper() { ... }   // 脚本看不到这个方法
}
```

标在类型上时，隐藏**声明类**具有该注解的方法/字段；这不保证隐藏继承成员、类加载、类对象或构造器。**probe 限制**：公开字段目前保留 Java 原名，不会自动应用 `@HideFromJS` 或重映射；类型注解也不会自动排除整个类声明及其构造器。显式 Probe 编辑可隐藏/重命名输出，但声明文件不是运行时安全边界。

<a id="wiki-section-8"></a>
## `@CalledByDynamicCode`

标记「会被生成的 JS 代码调用」的类型/方法/构造器。`RetentionPolicy.SOURCE`——记录动态使用，运行时无消费，也不保证 IDE 消除警告。常用于 `NekoScriptModuleLoaderHost` 等被 `internal/script-loader.js` 调用的方法。

<a id="wiki-section-9"></a>
## `@PlatformAvailability` 与 Spec 接口模式

`@PlatformAvailability(Scope.ALL | NF_ONLY | NF26_ONLY | CR_ONLY)` 声明一个 Spec 接口（或方法）的跨平台可用性，位于 `com.tkisor.nekojs.api.spec`。

**Spec 接口模式**（跨平台方法绑定）：

```java
// 契约包 api.spec：Spec 接口声明跨平台脚本表面，neko$ 前缀避免与 MC 原生方法碰撞
@RemapByPrefix("neko$")
@PlatformAvailability(Scope.ALL)
public interface EntitySpec {
    String neko$getId();
    void neko$kill();
    ...
}

// 平台：Extension 接口 extends Spec 并用 MC 类型实现协变返回
@RemapByPrefix("neko$")
public interface EntityExtension extends EntitySpec {
    @Override
    default String neko$getId() { ... }
}
```

- `Scope.ALL`：Spec 所覆盖的全部目标；`NF_ONLY`：NeoForge 1.21.1/26.x；`NF26_ONLY`：仅 NeoForge 26.x。`CR_ONLY` 是保留的旧 Cleanroom 标记，不代表存在受支持的构建节点。Fabric 的可用性仍须单独检查；注解不会补出尚未实现的桥接。
- 覆盖检查由 `SpecCoverageProcessor`（annotation processor）在编译期做：直接继承的已标注 Spec 接口所要求的 `neko$` 方法必须由 Spec 之外提供实现，非 Spec 父接口继承来的实现也可计入；缺少实现是编译 ERROR。**只要接上 `annotationProcessor(project(":common-api-processor"))` 这层就生效**，不需要额外选项。
- 但**平台范围检查要额外开**：Spec 接口级 `Scope` 语义只在编译时传了 `-Anekojs.platform=<平台>` 之后才校验，处理器接受 `nf121`、`nf26` 和旧 `cr`；旧值 `nf` 因无法区分版本而被拒绝。处理器读取 Spec 类型的 scope，不按方法级注解过滤；不传则只做覆盖检查。本仓只在 NeoForge 节点传（`buildSrc/.../nekojs.neoforge-node.gradle.kts`），fabric 节点刻意不传。第三方插件若没接处理器，`@RemapByPrefix("neko$")` + Spec 接口这套写法在你自己的 mod 里就是**纯约定**——好处仍在（`neko$` 前缀避免与 MC 原生方法碰撞、remap 一次剥掉），只是没人替你检查覆盖完整性。

---

<a id="wiki-section-10"></a>
## 文档注解（已实现）

`@Doc` / `@Param` / `@Return`（`com.tkisor.nekojs.api.annotation`）已实现并由内置 probe 消费——`TypeReflector` 反射时读取注解，渲染进生成的 `.d.ts` JSDoc（编辑器签名帮助可见）。**注解文本一律英文**。

| 注解 | 目标 | 作用 |
|---|---|---|
| `@Doc("...")` | 类 / 方法 / 构造器 / 字段 | 描述文本；可重复（每条 = 一个 JSDoc 段落） |
| `@Param(name = "x", value = "...")` | 方法 / 构造器 | 单个参数文档，渲染为 `@param x ...` 行；可重复 |
| `@Return("...")` | 方法 | 返回值文档，渲染为 `@returns ...` 行 |

```java
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

@Doc("Creates an item stack from an id or item-like value.")
@Param(name = "id", value = "item id like 'minecraft:stone', '#tag', or item-like object")
@Return("the resolved stack; never null")
public static ItemStack of(Object id) { ... }
```

- 无注解的成员 docs 为空 → 渲染零输出，与未注解路径的旧产物逐字节一致（`TypeScriptNoopIrGoldenTest` 守护）。
- 与编程式通道的关系：`registerTypeDocs`（`TypeDocsRegister.register(...)`）仍可用于绑定级文档；`ProbeEvents.modifyType` 在生成期的编辑优先于注解。

<a id="wiki-section-11"></a>
## `@Overload`：手写附加签名（已实现）

`.d.ts` 里的重载签名有两个来源，覆盖不同的情况：

1. **自动（适配器驱动，无需注解）**：类型适配器实现 `inputShapes()`（见 [类型适配器](type-adapters_cn)）后，probe 把所有引用该目标类型的方法参数放宽成输入别名联合（如 `$ItemStack_ = $ItemStack | string | {...}`）。同形不同输入的场合全自动。
2. **手写（本注解）**：适配器表达不了的签名——不同参数个数（`of(id)` 与 `of(id, count)`）、参数声明为 `Object` 的工厂方法、各形态语义不同。

```java
@Doc("Creates a stack from an id.")
@Overload({"id: string", "flags: number"})
public static ItemStack of(Object input, int flags) { ... }
```

- `value()` 每项是一个参数，`name: Type` 或 `name?: Type` 原样输出；`returns()` 空则沿用反射返回类型。
- 参数与返回类型是 TypeScript 片段，不走反射链、不自动收集 import——引用的类型须用生成模块里可见的名字（`$Foo` 形式），由注解作者保证。
- Bean 属性别名不发射重载；保留下来的可调用 setter 方法，或因命名冲突改为普通方法输出的访问器，仍可发射重载。
- 仅 TS 后端消费；Python `.pyi` 后端不消费（TS-first）。这些片段是注解示意，不是完整实现：`@Overload` 不创建 Java 重载、参数转换、默认值或分派逻辑，每个声明的调用形式都必须已由运行时实现支持。

<a id="wiki-section-12"></a>
## `@DeprecatedNekojs`：脚本面弃用（已实现）

与 Java 自带的 `@Deprecated` 语义分开：那个常标记引擎内部原因，这个专指"脚本作者别再用"。**Java `@Deprecated` 不会自动触发它**——脚本面弃用要显式标注。

```java
@DeprecatedNekojs(value = "Tag filters moved to Ingredient.matchTag.", replacedBy = "matchTag")
public static IngredientJS anyTag(String tag) { ... }
```

- 渲染为 JSDoc `@deprecated` 行（`value` + `Use <replacedBy> instead.`），编辑器对脚本与 `.d.ts` 里的该成员划删除线。
- 目标：类 / 方法 / 构造器 / 字段。

尚未实现的注解剩三个：`@Example` / `@TypeOverride` / `@Since`（`@Since` 依赖公开 API 的版本线，见 [项目架构](project-architecture_cn) 的待办）。

<a id="wiki-section-13"></a>
## 最佳实践

下面四条都是**约定**，没有任何检查执行它们；照做的收益写在括号里：

1. **公开绑定类的 JS 命名要显式**：用 `@Remap`/`@RemapByPrefix` 让脚本作者看到友好名，而非 `addItemInternal` 这种内部名（否则脚本里和 `.d.ts` 里都是内部名，改名就是破坏性变更）。
2. **内部辅助方法用 `@HideFromJS`**：避免污染脚本 API 面（不标只是脚本能看到多余成员，不会出错）。
3. **跨平台方法集用 Spec 接口**：`@RemapByPrefix("neko$")` + `@PlatformAvailability`；只有在接了 `SpecCoverageProcessor` 的构建里才有编译期覆盖检查（见上节）。
4. **关键字冲突用 `@Remap`**：如 NBT 标量方法 `byteValue` → JS `byte`（Java 关键字不能作方法名）。这条实际上躲不开——不 remap 就没法在 Java 侧写出那个名字。

<a id="wiki-section-14"></a>
## 与 probe 的关系（重要）

- 已实现：`@Remap`/`@RemapByPrefix`/`@HideFromJS` 由运行时 `MemberVisibilityQuery`/`MemberRemapper` 消费（脚本运行时生效），方法声明共享运行时命名/隐藏规则；公开字段和整类隐藏存在上文所述限制。
- 已实现：文档注解（`@Doc`/`@Param`/`@Return`）经 `TypeReflector` → IR docs → `.d.ts` JSDoc（见上方「文档注解」）。
- 已实现：`@ScriptInput(Provider.class)` 仅把标注的方法/构造器参数的 TypeScript 输入替换为 `Provider.inputShape()`。提供者须实现 `ScriptInputShapeProvider` 并具有 public 无参构造器。它不转换或校验运行时值，不改变 Java 参数类型、输出类型或 Python 输出；普通参数保留原有输入投影。声明的形状必须与真实转换器一致。
- 已实现：`@ScriptOutput` 替换方法的 TypeScript 返回类型，对 Bean getter 和继承声明也生效；不转换运行时值，不改变 Python 输出，显式 Probe 类型编辑优先。外部类型用内联 `import('module').Type` 引用，对应模块需由目录或反射闭包提供。如果返回值仍支持 Java 方法，应使用交叉类型保留 Java 类型，例如 JSX 宿主的尺寸 Map。空表达式以 `NEKO-4064` 使反射失败；正常共享 IR 路径可记录 `NEKO-4029` 并省略该类，而非必然中止全部输出。表达式是否有效通过生成声明的类型检查验证。

<a id="wiki-section-15"></a>
## 下一步

- [Probe 类型生成](probe-type-generation_cn) —— 注解如何变成 `.d.ts`。
- [插件开发](plugin-development_cn) —— `registerTypeDocs` 编程式文档通道。

<!-- wiki-nav -->

---

[上一篇: 事件扩展](event-extensions_cn) · [目录](Home) · [下一篇: 项目架构](project-architecture_cn)
