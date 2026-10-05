<!-- wiki-page: annotations; locale: us -->

> **English** · [中文](annotations_cn)

<a id="wiki-section-1"></a>
# Annotations

> NekoJS annotations control JS visibility and naming, consumed at runtime by the `MemberVisibilityQuery` / `NekoJSMemberRemapper` reflection layer. All annotations live in the engine contract package `com.tkisor.nekojs.api.annotation`, except `@PlatformAvailability`, which lives in `api.spec`.
> All annotation Javadoc is written in English; this page explains their usage.

<a id="wiki-section-2"></a>
## Annotation overview

| Annotation | Target | Purpose |
|---|---|---|
| `@RegisterNekoJSPlugin` | TYPE | Mark a plugin class |
| `@Remap("jsName")` | METHOD/FIELD/PARAMETER | Rename a Java name to a JS name |
| `@RemapByPrefix({"get", "is"})` | TYPE/METHOD/FIELD | Rename members in bulk by prefix |
| `@HideFromJS` | TYPE/METHOD/FIELD | Hide from JS |
| `@CalledByDynamicCode` | TYPE/METHOD/CONSTRUCTOR | Mark calls from generated JS (`RetentionPolicy.SOURCE`, suppressing IDE unused warnings) |
| `@PlatformAvailability(Scope.ALL\|NF_ONLY\|CR_ONLY)` | TYPE/METHOD | Declare platform availability on Spec interfaces |
| `@Doc("...")` | TYPE/METHOD/CONSTRUCTOR/FIELD | English documentation rendered as probe `.d.ts` JSDoc; repeatable |
| `@Param(name, value)` | METHOD/CONSTRUCTOR | Parameter documentation rendered as `@param` lines; repeatable |
| `@Return("...")` | METHOD | Return-value documentation rendered as `@returns` lines |
| `@Overload({"id: string"})` | METHOD/CONSTRUCTOR | Additional handwritten `.d.ts` signatures; repeatable |
| `@DeprecatedNekojs` | TYPE/METHOD/CONSTRUCTOR/FIELD | Script-facing deprecation rendered as JSDoc `@deprecated`, shown struck through in editors |

All annotations use `@Retention(RUNTIME)` (except `@CalledByDynamicCode`, which uses `SOURCE`) and `@Documented`.

<a id="wiki-section-3"></a>
## Which annotations are enforced?

They fall into three categories according to what happens when omitted:

| Category | Annotation | Consequence of omission or incorrect use |
|---|---|---|
| Changes runtime behavior | `@Remap`, `@RemapByPrefix`, `@HideFromJS` | Without them, original Java names are exposed and everything is visible. They act during member lookup through Graal's `MemberRemapper.CHAIN` (`NekoJSMemberRemapper`). Scripts cannot call `@HideFromJS` members, and probe does not emit them. |
| Compile-time enforcement | `@PlatformAvailability` + Spec interfaces | See below: missing `neko$` overrides produce a compile ERROR, but **only in builds with the annotation processor configured**. |
| Generated documentation only | `@Doc`, `@Param`, `@Return`, `@Overload`, `@DeprecatedNekojs` | Without them, JSDoc is empty and there are no extra signatures. They do not affect runtime callability. `@Overload` affects type checking by adding available signatures. |
| Not consumed at runtime | `@CalledByDynamicCode` | `RetentionPolicy.SOURCE`; only prevents IDE unused warnings. Omitting it changes no behavior. |

`@RegisterNekoJSPlugin` is the only annotation that **must be present**: platform loaders scan only annotated classes; see [Plugin development](plugin-development_us).

---

<a id="wiki-section-4"></a>
## `@RegisterNekoJSPlugin`

Marks a plugin class; see [Plugin development](plugin-development_us). Properties: `clientOnly()` (default `false`), `requiredMods()` (default `{}`, AND semantics), and `priority()` (default `1000`, larger values load first; built-in `NekoJSPlugin.CORE_PRIORITY = Integer.MAX_VALUE` guarantees first place).

<a id="wiki-section-5"></a>
## `@Remap("jsName")`

Renames Java members/parameters to their JS-visible names.

```java
public class FooJS {
    @Remap("addItem")            // Scripts call foo.addItem(...), not foo.addItemInternal(...)
    public void addItemInternal(ItemStack stack) { ... }
}
```

> Useful for Java keyword conflicts, such as `NbtFacade`'s `byteValue` becoming JS `byte`, or differences between Java naming conventions and JS usage. Consumed by the runtime `MemberRemapper`.

<a id="wiki-section-6"></a>
## `@RemapByPrefix({"get", "is"})`

Renames members of a class in bulk by prefix. `value()` lists prefixes to **remove**. Common uses include turning `getXXX` / `isXXX` into property-style names and remapping the `neko$` prefix on Spec interfaces:

```java
@RemapByPrefix({"get", "is"})   // Map getX to x and isX to x
public class FooJS { ... }

// Spec interface pattern: strip the neko$ prefix on platform Extensions to expose JS names
@RemapByPrefix("neko$")
public interface ItemStackExtension extends ItemStackSpec { ... }
```

<a id="wiki-section-7"></a>
## `@HideFromJS`

Makes a member completely invisible to JS: scripts cannot call it, and probe does not emit it.

```java
public class FooJS {
    @HideFromJS
    public void internalHelper() { ... }   // Scripts cannot see this method
}
```

It can also annotate a class, hiding the whole class from JS.

<a id="wiki-section-8"></a>
## `@CalledByDynamicCode`

Marks types/methods/constructors called by generated JS code. It uses `RetentionPolicy.SOURCE`: it is purely an IDE hint, with no runtime consumer. Typical uses are methods in `NekoScriptModuleLoaderHost` called by `internal/script-loader.js`.

<a id="wiki-section-9"></a>
## `@PlatformAvailability` and the Spec interface pattern

`@PlatformAvailability(Scope.ALL | NF_ONLY | CR_ONLY)` declares cross-platform availability for a Spec interface or method. It lives in `com.tkisor.nekojs.api.spec`.

**Spec interface pattern** for cross-platform method bindings:

```java
// api.spec contract package: declare the cross-platform script interface;
// the neko$ prefix avoids collisions with native Minecraft methods
@RemapByPrefix("neko$")
@PlatformAvailability(Scope.ALL)
public interface EntitySpec {
    String neko$getId();
    void neko$kill();
    ...
}

// Platform: Extension extends Spec and uses Minecraft types for covariant returns
@RemapByPrefix("neko$")
public interface EntityExtension extends EntitySpec {
    @Override
    default String neko$getId() { ... }
}
```

- `Scope.ALL`: available on every platform; `NF_ONLY`: NeoForge 1.21.1/26.x only; `CR_ONLY`: Cleanroom 1.12.2 only.
- `SpecCoverageProcessor`, an annotation processor, checks coverage at compile time. Each platform `Extension` must override every `neko$` method in the Spec interface; one missing method produces a compile ERROR. **Configuring `annotationProcessor(project(":common-api-processor"))` enables this**, with no extra option required.
- **Platform-scope checks need an additional option**: `Scope` semantics, determining which platform should implement which members, are checked only when compilation passes `-Anekojs.platform=<平台>`. Without it, only override coverage is checked. This repository passes the option only for NeoForge nodes (`buildSrc/.../nekojs.neoforge-node.gradle.kts`); Fabric nodes deliberately omit it. In an addon without the processor, `@RemapByPrefix("neko$")` + Spec interfaces are **only a convention**. The advantages remain, including avoiding native method collisions and stripping prefixes in one place, but nothing verifies complete override coverage for you.

---

<a id="wiki-section-10"></a>
## Documentation annotations (implemented)

`@Doc` / `@Param` / `@Return` (`com.tkisor.nekojs.api.annotation`) are implemented and consumed by built-in probe. `TypeReflector` reads them during reflection and emits them into generated `.d.ts` JSDoc, visible in editor signature help. **Annotation text must be English**.

| Annotation | Target | Purpose |
|---|---|---|
| `@Doc("...")` | Class / method / constructor / field | Descriptive text; repeatable, with each annotation becoming a JSDoc paragraph |
| `@Param(name = "x", value = "...")` | Method / constructor | Documentation for one parameter, rendered as `@param x ...`; repeatable |
| `@Return("...")` | Method | Return-value documentation rendered as `@returns ...` |

```java
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

@Doc("Creates an item stack from an id or item-like value.")
@Param(name = "id", value = "item id like 'minecraft:stone', '#tag', or item-like object")
@Return("the resolved stack; never null")
public static ItemStack of(Object id) { ... }
```

- Unannotated members have empty docs and emit no documentation, remaining byte-for-byte identical to the old unannotated output (`TypeScriptNoopIrGoldenTest` protects this).
- Programmatic documentation remains available through `registerTypeDocs` (`TypeDocsRegister.register(...)`) for binding-level docs. Generation-time edits through probe's `modify_type` event take precedence over annotations.

<a id="wiki-section-11"></a>
## `@Overload`: handwritten additional signatures (implemented)

Overload signatures in `.d.ts` have two sources for different cases:

1. **Automatic, adapter-driven, without annotations**: when a type adapter implements `inputShapes()` (see [Type adapters](type-adapters_us)), probe broadens every parameter referencing that target type to a union input alias such as `$ItemStack_ = $ItemStack | string | {...}`. Different input forms with the same shape are handled automatically.
2. **Handwritten with this annotation**: signatures an adapter cannot express, including different parameter counts (`of(id)` versus `of(id, count)`), factory parameters declared as `Object`, or input forms with different semantics.

```java
@Doc("Creates a stack from an id.")
@Overload({"id: string"})
@Overload(value = {"item: $ItemStack", "count?: number"}, returns = "string", doc = "Copy with a count.")
public static ItemStack of(Object input, int flags) { ... }
```

- Each `value()` entry is a parameter. `name: Type` or `name?: Type` is emitted unchanged; an empty `returns()` uses the reflected return type.
- Parameter and return types are TypeScript fragments. They do not go through reflection or automatic import collection. The annotation author must use names visible in the generated module, such as `$Foo`.
- Getter/setter property accessors do not emit overloads. Use an ordinary method if you need additional signatures.
- Only the TS backend consumes these; Python `.pyi` does not (TS-first).

<a id="wiki-section-12"></a>
## `@DeprecatedNekojs`: script-facing deprecation (implemented)

This is separate from Java's `@Deprecated`, which often expresses an engine-internal reason. This annotation specifically tells script authors not to use a member. **Java `@Deprecated` does not trigger it automatically**; script-facing deprecation requires an explicit annotation.

```java
@DeprecatedNekojs(value = "Tag filters moved to Ingredient.matchTag.", replacedBy = "matchTag")
public static IngredientJS anyTag(String tag) { ... }
```

- Rendered as a JSDoc `@deprecated` line (`value` + `Use <replacedBy> instead.`). Editors strike through the member in scripts and `.d.ts`.
- Targets: class / method / constructor / field.

Three annotations remain unimplemented: `@Example` / `@TypeOverride` / `@Since`. `@Since` depends on a public API version boundary; see the unfinished work in [Project architecture](project-architecture_us).

<a id="wiki-section-13"></a>
## Best practices

The following four items are **conventions**, with no checks enforcing them. Their benefits are explained in parentheses:

1. **Make JS naming explicit on public binding classes**: use `@Remap` / `@RemapByPrefix` to expose friendly names rather than internal names such as `addItemInternal`. Otherwise, scripts and `.d.ts` use the internal name, and renaming it is a breaking change.
2. **Hide internal helpers with `@HideFromJS`**: avoid cluttering the Script API. Omitting it merely exposes extra members; it is not an error.
3. **Use Spec interfaces for cross-platform method sets**: `@RemapByPrefix("neko$")` + `@PlatformAvailability`. Compile-time coverage is checked only in builds with `SpecCoverageProcessor`, as described above.
4. **Use `@Remap` for keyword conflicts**: for example, NBT scalar `byteValue` becomes JS `byte` because a Java keyword cannot be a method name. This is effectively unavoidable: without remapping, Java cannot declare that name.

<a id="wiki-section-14"></a>
## Relationship to probe (important)

- Implemented: `@Remap` / `@RemapByPrefix` / `@HideFromJS` are consumed by runtime `MemberVisibilityQuery` / `MemberRemapper` and affect script execution. Generated `.d.ts` names and visibility match runtime behavior.
- Implemented: documentation annotations (`@Doc` / `@Param` / `@Return`) flow through `TypeReflector` to IR docs and `.d.ts` JSDoc, as described above.

<a id="wiki-section-15"></a>
## Next steps

- [Probe type generation](probe-type-generation_us): how annotations become `.d.ts`.
- [Plugin development](plugin-development_us): the programmatic `registerTypeDocs` documentation channel.

<!-- wiki-nav -->

---

[Previous: Event extensions](event-extensions_us) · [Contents](Home) · [Next: Project architecture](project-architecture_us)
