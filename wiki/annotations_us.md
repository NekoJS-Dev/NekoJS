<!-- wiki-page: annotations; locale: us -->

> **English** · [中文](annotations_cn)

<a id="wiki-section-1"></a>
# Annotations

> NekoJS annotations control JS visibility and naming, consumed at runtime by the `MemberVisibilityQuery` / `NekoJSMemberRemapper` reflection layer. All annotations live in the engine contract package `com.tkisor.nekojs.api.annotation`, except `@PlatformAvailability`, which lives in `api.spec`.
> Write authored annotation Javadoc in English; this page explains their usage.

<a id="wiki-section-2"></a>
## Annotation overview

| Annotation | Target | Purpose |
|---|---|---|
| `@RegisterNekoJSPlugin` | TYPE | Mark a plugin class |
| `@Remap("jsName")` | METHOD/FIELD/PARAMETER | Rename a runtime method/field; the legal PARAMETER target has no naming consumer |
| `@RemapByPrefix({"get", "is"})` | TYPE/METHOD/FIELD | Rename members in bulk by prefix |
| `@HideFromJS` | TYPE/METHOD/FIELD | Hide from JS |
| `@CalledByDynamicCode` | TYPE/METHOD/CONSTRUCTOR | Document dynamic calls for maintainers/IDE tooling (`RetentionPolicy.SOURCE`) |
| `@PlatformAvailability(Scope.ALL\|NF_ONLY\|NF26_ONLY\|CR_ONLY)` | TYPE/METHOD | Declare platform availability on Spec interfaces |
| `@Doc("...")` | TYPE/METHOD/CONSTRUCTOR/FIELD | English documentation rendered as probe `.d.ts` JSDoc; repeatable |
| `@Param(name, value)` | METHOD/CONSTRUCTOR | Parameter documentation rendered as `@param` lines; repeatable |
| `@Return("...")` | METHOD | Return-value documentation rendered as `@returns` lines |
| `@Overload({"id: string"})` | METHOD/CONSTRUCTOR | Additional handwritten `.d.ts` signatures; repeatable |
| `@ScriptInput(MyShape.class)` | PARAMETER | Explicit structural TypeScript input supplied by a public no-argument `ScriptInputShapeProvider` |
| `@ScriptOutput("{ readonly width: number }")` | METHOD | Explicit TypeScript return type for values Java reflection cannot describe |
| `@DeprecatedNekojs` | TYPE/METHOD/CONSTRUCTOR/FIELD | Script-facing deprecation rendered as JSDoc `@deprecated`, shown struck through in editors |

Retention is `RUNTIME` except `@CalledByDynamicCode` (`SOURCE`). `@Documented` is not universal: naming/visibility/plugin/platform annotations do not all carry it; inspect the declaration rather than assuming it affects callability.

<a id="wiki-section-3"></a>
## Which annotations are enforced?

They fall into four categories according to what happens when omitted:

| Category | Annotation | Consequence of omission or incorrect use |
|---|---|---|
| Changes runtime behavior | `@Remap`, `@RemapByPrefix`, `@HideFromJS` | They affect runtime method/field lookup through Graal's `MemberRemapper.CHAIN` (`NekoJSMemberRemapper`); without a match, lookup falls through to the remaining remappers and host-access policy. Probe shares method naming/hiding, but has field/class limitations described below. |
| Compile-time enforcement | `@PlatformAvailability` + Spec interfaces | See below: missing `neko$` overrides produce a compile ERROR, but **only in builds with the annotation processor configured**. |
| Generated documentation only | `@Doc`, `@Param`, `@Return`, `@Overload`, `@DeprecatedNekojs` | Without them, JSDoc is empty and there are no extra signatures. They do not affect runtime callability. `@Overload` affects type checking by adding available signatures. |
| Not consumed at runtime | `@CalledByDynamicCode` | `RetentionPolicy.SOURCE`; documents dynamic use without a runtime consumer. IDE warning behavior depends on the IDE/tooling. |

`@RegisterNekoJSPlugin` enables NeoForge annotation discovery. Fabric addons instead need the `nekojs` manifest entrypoint; the shared manager accepts absent annotation metadata. See [Plugin development](plugin-development_us).

---

<a id="wiki-section-4"></a>
## `@RegisterNekoJSPlugin`

Marks a plugin class; see [Plugin development](plugin-development_us). Properties: `clientOnly()` (default `false`), `requiredMods()` (default `{}`, AND semantics), and `priority()` (default `1000`, larger values load first; built-in `NekoJSPlugin.CORE_PRIORITY = Integer.MAX_VALUE` uses the maximum priority; equal priorities still use the class-name/owner tie-breaker).

<a id="wiki-section-5"></a>
## `@Remap("jsName")`

Renames Java methods/fields to their JS-visible names. `PARAMETER` is a legal annotation target, but the runtime remapper does not rename parameters, and probe still uses reflected parameter names or `argN`.

```java
public class FooJS {
    @Remap("addItem")            // Scripts call foo.addItem(...), not foo.addItemInternal(...)
    public void addItemInternal(ItemStack stack) { ... }
}
```

> Useful for Java keyword conflicts, such as `NbtFacade`'s `byteValue` becoming JS `byte`, or differences between Java naming conventions and JS usage. Consumed by the runtime `MemberRemapper`.

<a id="wiki-section-6"></a>
## `@RemapByPrefix({"get", "is"})`

Strips the prefixes in `value()` while **preserving the remaining casing**: `getX` becomes `X`, not `x`. Only the first matching prefix is stripped, and only if a nonempty remainder remains. This is member renaming, not Bean-property alias generation. Use a per-member `@Remap("x")` for that exact spelling. The `neko$` prefix on Spec interfaces is another use; see [Script API style](script-api-style_us).

**Resolution and Bean properties:** hiding wins over member `@Remap`, then member-prefix rules, then declaring-type/interface prefix rules, then fallback. Bean aliases are derived separately from the resulting visible accessor names: `neko$getX` → `getX` can expose `x`, while stripping `get` to `X` alone does not. A getter-only alias is read-only; a matching visible setter is required for writing.

```java
@RemapByPrefix({"get", "is"})   // Strip getX to X and isX to X; casing is preserved
public class FooJS { ... }

// Spec interface pattern: strip the neko$ prefix on platform Extensions to expose JS names
@RemapByPrefix("neko$")
public interface ItemStackExtension extends ItemStackSpec { ... }
```

<a id="wiki-section-7"></a>
## `@HideFromJS`

Hides an annotated method/field during runtime member lookup. Probe also omits hidden methods; public-field declarations have the limitation below.

```java
public class FooJS {
    @HideFromJS
    public void internalHelper() { ... }   // Scripts cannot see this method
}
```

On a type, it hides methods/fields whose **declaring class** carries the annotation; this does not promise to hide inherited members, class loading, the class object, or constructors. **Probe limitation:** public fields currently retain their original Java name and are not automatically removed by `@HideFromJS`/remapping; a type annotation does not automatically exclude the whole class declaration or its constructors. Explicit Probe edits can hide/rename output, but declarations are not a runtime security boundary.

<a id="wiki-section-8"></a>
## `@CalledByDynamicCode`

Marks types/methods/constructors called by generated JS code. It uses `RetentionPolicy.SOURCE`: it documents dynamic use, with no runtime consumer or guaranteed IDE warning suppression. Typical uses are methods in `NekoScriptModuleLoaderHost` called by `internal/script-loader.js`.

<a id="wiki-section-9"></a>
## `@PlatformAvailability` and the Spec interface pattern

`@PlatformAvailability(Scope.ALL | NF_ONLY | NF26_ONLY | CR_ONLY)` declares cross-platform availability for a Spec interface or method. It lives in `com.tkisor.nekojs.api.spec`.

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

- `Scope.ALL`: every target covered by the Spec; `NF_ONLY`: NeoForge 1.21.1/26.x; `NF26_ONLY`: NeoForge 26.x only. `CR_ONLY` is a retained legacy Cleanroom marker, not a supported build node. Fabric availability must be checked separately; this annotation does not implement a missing bridge.
- `SpecCoverageProcessor`, an annotation processor, checks coverage at compile time. For directly extended annotated Spec interfaces, required `neko$` methods must have implementations outside the Spec; implementations inherited from non-Spec parent interfaces can count. A missing implementation produces a compile ERROR. **Configuring `annotationProcessor(project(":common-api-processor"))` enables this**, with no extra option required.
- **Platform-scope checks need an additional option**: Spec-interface `Scope` semantics are checked only when compilation passes `-Anekojs.platform=<platform>`. Accepted processor tags are `nf121`, `nf26` and the legacy `cr`; the old `nf` value is rejected because it cannot distinguish Minecraft versions. The processor reads the Spec type's scope, not method-level scope filters. Without the option, only override coverage is checked. This repository passes the option only for NeoForge nodes (`buildSrc/.../nekojs.neoforge-node.gradle.kts`); Fabric nodes deliberately omit it. In an addon without the processor, `@RemapByPrefix("neko$")` + Spec interfaces are **only a convention**. The advantages remain, including avoiding native method collisions and stripping prefixes in one place, but nothing verifies complete override coverage for you.

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
- Programmatic documentation remains available through `registerTypeDocs` (`TypeDocsRegister.register(...)`) for binding-level docs. Generation-time edits through `ProbeEvents.modifyType` take precedence over annotations.

<a id="wiki-section-11"></a>
## `@Overload`: handwritten additional signatures (implemented)

Overload signatures in `.d.ts` have two sources for different cases:

1. **Automatic, adapter-driven, without annotations**: when a type adapter implements `inputShapes()` (see [Type adapters](type-adapters_us)), probe broadens every parameter referencing that target type to a union input alias such as `$ItemStack_ = $ItemStack | string | {...}`. Different input forms with the same shape are handled automatically.
2. **Handwritten with this annotation**: signatures an adapter cannot express, including different parameter counts (`of(id)` versus `of(id, count)`), factory parameters declared as `Object`, or input forms with different semantics.

```java
@Doc("Creates a stack from an id.")
@Overload({"id: string", "flags: number"})
public static ItemStack of(Object input, int flags) { ... }
```

- Each `value()` entry is a parameter. `name: Type` or `name?: Type` is emitted unchanged; an empty `returns()` uses the reflected return type.
- Parameter and return types are TypeScript fragments. They do not go through reflection or automatic import collection. The annotation author must use names visible in the generated module, such as `$Foo`.
- Bean property aliases do not emit overloads. Retained callable setter methods, or accessors emitted as ordinary methods after a naming conflict, can still emit them.
- Only the TS backend consumes these; Python `.pyi` does not (TS-first). These snippets are annotation sketches, not complete implementations: `@Overload` adds no Java overload, argument conversion, defaults, or dispatch. Every advertised call form must already work in the runtime implementation.

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

- Implemented: `@Remap` / `@RemapByPrefix` / `@HideFromJS` are consumed by runtime `MemberVisibilityQuery` / `MemberRemapper` and affect script execution. Method declarations share runtime naming/hiding rules; public fields and whole-class hiding have the limitations described above.
- Implemented: documentation annotations (`@Doc` / `@Param` / `@Return`) flow through `TypeReflector` to IR docs and `.d.ts` JSDoc, as described above.
- Implemented: `@ScriptInput(Provider.class)` replaces only the annotated method/constructor parameter's TypeScript input with `Provider.inputShape()`. The provider must implement `ScriptInputShapeProvider` and have a public no-argument constructor. It does not convert or validate runtime values, change the Java parameter type, broaden output types, or change Python output. Ordinary parameters keep their normal input projection; keep the declared shape consistent with the actual converter.
- Implemented: `@ScriptOutput` replaces a method's TypeScript return type, including Bean getters and inherited declarations. It does not convert runtime values or change Python output. Explicit Probe type edits take precedence. Use inline `import('module').Type` references for external types; the referenced module must already be supplied by the catalog or reflection closure. Include the Java type in an intersection when its methods remain available, as the JSX host does for its dimension Maps. Blank expressions fail reflection with `NEKO-4064`; the normal shared-IR path can log `NEKO-4029` and omit that class rather than abort every output. Consumers should type-check generated declarations to validate the expression.

<a id="wiki-section-15"></a>
## Next steps

- [Probe type generation](probe-type-generation_us): how annotations become `.d.ts`.
- [Plugin development](plugin-development_us): the programmatic `registerTypeDocs` documentation channel.

<!-- wiki-nav -->

---

[Previous: Event extensions](event-extensions_us) · [Contents](Home) · [Next: Project architecture](project-architecture_us)
