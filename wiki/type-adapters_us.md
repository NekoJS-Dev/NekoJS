<!-- wiki-page: type-adapters; locale: us -->

> **English** · [中文](type-adapters_cn)

<a id="wiki-section-1"></a>
# Type adapters

> Adapters let Java methods **accept multiple JS input forms automatically**. For example, when a Java method declares an `ItemStack` parameter, a script can supply `'minecraft:stone'`, an `{id, count}` object, or an `ItemStack` instance; the adapter converts each form to `ItemStack`. There are two SPI generations: the **new `JsTypeAdapter` SPI (recommended)** and the **legacy `JSTypeAdapter` SPI (Graal `Value`, retained for compatibility)**. See “Two API generations” below.

<a id="wiki-section-2"></a>
## Why adapters are needed

GraalJS `HostAccess` provides `targetTypeMapping(C, T, predicate, function)`: when a Java API expects type `T`, GraalJS automatically converts the incoming JS value (`Value`) to `T` through that mapping. NekoJS `JSTypeAdapter` carries the mapping.

At bootstrap, NekoJS uses `NekoSharedHostAccess` to register all `JSTypeAdapter` instances in each Context as `HostAccess.targetTypeMapping(Value.class, T, adapter, adapter)`.

<a id="wiki-section-3"></a>
## Built-in adapters

NekoJS registers many adapters; see `src/main/java/com/tkisor/nekojs/js/type_adapter/`:

| Adapter | Target type | Accepted JS input |
|---|---|---|
| `ItemAdapter` / `ItemStackAdapter` | `ItemStack` | String id (`"minecraft:stone"`), `"1x minecraft:stone"`, `{id, count}`, `ItemStack` |
| `IngredientAdapter` | `Ingredient` | String, tag (`"#minecraft:planks"`), array, `Ingredient` |
| `SizedIngredientAdapter` | `SizedIngredient` | The same forms, with a count |
| `BlockAdapter` | `Block` | String id |
| `BlockPosAdapter` | `BlockPos` | `{x, y, z}`, `[x, y, z]`, string |
| `Vec3Adapter` | `Vec3` | `{x, y, z}`, `[x, y, z]` |
| `CompoundTagAdapter` | `CompoundTag` | JS object, converted to NBT automatically |
| `ComponentAdapter` | `Component` | String, `MutableComponent` |
| `FluidStackAdapter` / `FluidIngredientAdapter` / `SizedFluidIngredientAdapter` | Fluid types | String, `{fluid, amount}` |
| `IdentifierAdapter` | `Identifier`/`ResourceLocation` | String |
| `TagKeyAdapter` | `TagKey` | `"#namespace:path"` |
| `EntityTypeAdapter` | `EntityType` | String id |
| `MobEffectAdapter` / `PotionAdapter` / `ParticleTypeAdapter` / `SoundEventAdapter` / `CreativeModeTabAdapter` / `BlockEntityTypeAdapter` | Corresponding registry types | String id |
| `CodecAdapter` | Any codec type | Parsed through the codec |
| `DataComponentsAdapter` (static utility, not a registered adapter) | `DataComponentPatch` | JS object to data component patch (NeoForge only) |
| `RecipeFilterAdapter` | `RecipeFilter` | JS object; see [Recipe system: filters](recipe-system_us#wiki-section-3) |
| `RecipeJsonValueAdapter` | `RecipeJsonValue` | — |

<a id="wiki-section-4"></a>
## Two API generations

Adapters currently have **two SPI generations**:

- **New SPI (recommended)**: `JsTypeAdapter<T>` (`common/src/main/java/com/tkisor/nekojs/api/data/JsTypeAdapter.java`, in the public contract package `com.tkisor.nekojs.api.*`), with **no Minecraft/Graal dependency**. Its methods are `targetType()` / `supports(JsValueView, ConversionContext)` / `convert(JsValueView, ConversionContext)` / `precedence()`. The recommended base class is `BaseJsTypeAdapter<T> implements JsTypeAdapter<T>`, with template methods `fromString` / `fromHostObject` / `acceptOther` / `fromOther`. A migrated example is `src/main/java/com/tkisor/nekojs/js/type_adapter/ComponentAdapter.java`.
- **Legacy SPI (retained for compatibility)**: common's `JSTypeAdapter<T>` / `AbstractJSTypeAdapter`, depending on Graal `Value` (`test(Value)` / `apply(Value)`).

Both generations use the same registry: `JSTypeAdapterRegistry` (`common/src/main/java/com/tkisor/nekojs/api/data/JSTypeAdapterRegistry.java`) provides overloads for both `register(JSTypeAdapter<T>)` and `register(JsTypeAdapter<T>)`. This is the registry plugins receive.

**Prefer the new SPI for new adapters**. The legacy SPI remains available, and the older examples below still compile. Both `precedence()` and legacy `getPrecedence()` return `ConversionPrecedence`.

<a id="wiki-section-5"></a>
### Required implementations

| SPI | Required: no default | Optional: has a default |
|---|---|---|
| New `JsTypeAdapter<T>` | `targetType()`, `supports(JsValueView, ConversionContext)`, `convert(...)`, `precedence()` | `inputShapes()` (empty list by default) |
| Legacy `JSTypeAdapter<T>` | `getTargetClass()`, `test(Value)`, `apply(Value)` | `getPrecedence()` (default `LOWEST`), `inputShapes()`, `syntaxDoc()` |
| `BaseJsTypeAdapter<T>` | `fromHostObject(Object)`, plus passing `targetType` to the constructor | Everything else: `precedence()` defaults to `LOWEST`; `fromString` / `fromOther` throw `ValueConversionException` by default; `acceptNull` / `acceptOther` default to `false`; `supportsString` defaults to `true` |

The base class is a convenience template, **not a requirement**: the registry accepts the interface, and implementing it directly is equivalent. To avoid writing a class, `JSTypeAdapterRegistry` also has a default overload, `register(Class<T> target, Predicate<Value> filter, Function<Value, T> converter)`; three lambdas define a legacy SPI adapter.

<a id="wiki-section-6"></a>
## Write a custom adapter

<a id="wiki-section-7"></a>
### Step 1 (legacy SPI): implement `JSTypeAdapter<T>`

```java
package com.example.myaddon;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import graal.graalvm.polyglot.Value;

public class MyPosAdapter implements JSTypeAdapter<MyPos> {

    @Override
    public Class<MyPos> getTargetClass() {
        return MyPos.class;
    }

    @Override
    public ConversionPrecedence getPrecedence() {
        return ConversionPrecedence.LOW;   // Below built-ins; higher precedence wins when multiple adapters match a type
    }

    @Override
    public boolean test(Value value) {
        // Check whether this adapter can handle the JS value
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
        // Used by probe to generate the $MyPos_ input alias; object(Slot...) declares accepted fields
        return List.of(
            AdapterInputShape.object(
                AdapterInputShape.Slot.req("x", AdapterInputShape.number()),
                AdapterInputShape.Slot.req("y", AdapterInputShape.number()))
        );
    }
}
```

<a id="wiki-section-8"></a>
### Step 2 (legacy SPI): alternatively, extend `AbstractJSTypeAdapter` (convenient template methods)

```java
public class MyPosAdapter extends AbstractJSTypeAdapter<MyPos> {
    @Override public Class<MyPos> getTargetClass() { return MyPos.class; }

    @Override protected boolean acceptNull() { return false; }

    @Override
    protected MyPos fromString(String s) {
        // Convert "1,2" to MyPos(1,2)
        String[] parts = s.split(",");
        return new MyPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    @Override
    protected MyPos fromHostObject(Object host) {
        // Already a Java MyPos, Map, or another host object
        if (host instanceof MyPos p) return p;
        if (host instanceof java.util.Map<?,?> m) {
            return new MyPos((int) m.get("x"), (int) m.get("y"));
        }
        throw new ValueConversionException("Cannot convert to MyPos: " + host);
    }

    @Override
    protected boolean acceptOther(Value value) {
        return value.isString() || (value.hasMembers() && value.hasMember("x"));
    }
}
```

`AbstractJSTypeAdapter`'s `final test` / `apply` automatically dispatch to `defaultValue` / `acceptNull` / `fromString` / `fromHostObject` / `acceptOther` / `fromOther`. It is Minecraft-independent: `fromHostObject` receives `Object`, and platform code performs `instanceof` checks. It lives in `common/` and depends on Graal `Value`.

<a id="wiki-section-9"></a>
### Step 2b (new SPI, recommended): extend `BaseJsTypeAdapter<T>`

The new SPI base class `BaseJsTypeAdapter<T>`, also in the `api.data` contract package with no Minecraft/Graal dependency, supplies the same template methods. `supports` / `convert` are already implemented using `JsValueView` + `ConversionContext`:

```java
public class MyPosAdapter extends BaseJsTypeAdapter<MyPos> {
    public MyPosAdapter() {
        super(MyPos.class);
    }

    @Override
    protected MyPos fromString(String s) {
        // Convert "1,2" to MyPos(1,2)
        String[] parts = s.split(",");
        return new MyPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    @Override
    protected MyPos fromHostObject(Object host) {
        // Reached only after supports() verifies host instanceof MyPos
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

The form maps almost directly to the legacy SPI: `test` / `apply` become `supports` / `convert`, and `getPrecedence()` becomes `precedence()`. See `src/main/java/com/tkisor/nekojs/js/type_adapter/ComponentAdapter.java` for a migration example.

> The base class's host-object branch is **narrower** than the legacy base class: `supports` accepts a host object only when `targetType.isAssignableFrom(host.getClass())`. Thus `fromHostObject` always receives the target type; it does not need to, and cannot, handle other forms such as `Map` there. Those go through `acceptOther` / `fromOther`. Returning `null` from `fromHostObject` is **not** a valid way to indicate an unrecognized value: the base class treats it as failure and throws `ValueConversionException` (`BaseJsTypeAdapter.convert`). To reject a value, make `supports` return false.

<a id="wiki-section-10"></a>
### Step 3: register

```java
@Override
public void registerAdapters(JSTypeAdapterRegistry registry) {
    registry.register(new MyPosAdapter());
}
```

> `registerAdapters` receives `JSTypeAdapterRegistry`, which has overloads for both `register(JSTypeAdapter<T>)` and `register(JsTypeAdapter<T>)`. Either generation shown above can be passed directly.

Any Java method declaring `void foo(MyPos pos)` can then receive `{x: 1, y: 2}` or `"1,2"` from a script.

<a id="wiki-section-11"></a>
## AdapterInputShape (probe aliases)

`inputShapes()` tells probe which input forms the adapter accepts. Probe uses it to generate a `$MyPos_` type alias so the script's `.d.ts` accurately states that a parameter can accept a string or object.

This is **editor-only information**, with an empty default implementation in both SPI generations. Conversion works without it; the consequence is that `.d.ts` uses only the target type itself, so the editor flags string arguments even though they work at runtime.

`AdapterInputShape` is a sealed interface with 14 variants; see `api/AdapterInputShape.java`:

| Variant | Meaning | Rendered as |
|---|---|---|
| `String` | Any string, for free-text fields | `string` |
| `Literal(text)` | A single string literal | `"*"` |
| `Number` | A number | `number` |
| `Boolean` | A boolean | `boolean` |
| `Self` | The target type itself | `$Foo` |
| `Host` | A Java host type | `$Bar` |
| `ArrayOf(shape)` | An array | `T[]` |
| `Object(field...)` | An object with specified fields | `{ a?: ..., b: ... }` |
| `Union(shape...)` | Multiple forms for one field | `(A \| B)` |
| `Registry(typeName)` | A registry id string | `RegistryTypes.Item` |
| `RegistryTag(typeName)` | A tag id from that registry, without `#` | `RegistryTypes.ItemTag` |
| `Namespace` | An id namespace from mod lists and registry namespaces | `RegistryTypes.Namespace` |
| `Template(prefix, hole, suffix)` | Fixed prefix/suffix around a completable placeholder | <code>\`#${RegistryTypes.ItemTag}\`</code> |
| `Raw` | Another raw form | Output unchanged |

`AdapterAliasGenerator` renders these as `$Foo_` union types.

> **Do not include bare `string` in the union.** When TypeScript encounters `string` in a union, it absorbs the other string literal members and the editor stops offering id completion. This caused the earlier difference between ingredients using `$Ingredient_` with no completion and outputs using `$ItemStack_` with completion. To accept non-id syntax such as `@mod`, `*`, and `/regex` while retaining completion, use literals or template literal types:
>
> ```java
> registry("Item"),                     // minecraft:stone (with completion)
> template("#", registryTag("Item")),   // #minecraft:planks (with completion)
> template("@", namespace()),           // @create (with completion)
> literal("*"),                          // *
> template("/", string())               // /wool$/ (unbounded regex values cannot be completed)
> ```
>
> This restriction applies **per union**, not per object: bare `string` for `id` in `{ output: RegistryTypes.Item, id: string }` does not affect completion for `output`.
>
> `RegistryTypes.XTag` is generated from tags currently bound in the registry. `RegistryTypes.Namespace` is the union of loader mod ids (`NekoCatalogPlatformProvider.modIds()`) and the `:` prefixes of all registry entry ids. The former includes mods that added nothing to that registry; the latter includes namespaces created by scripts or data packs. When either set is empty, it falls back to `string`, so template types accept arbitrary strings rather than becoming `never`.

<a id="wiki-section-12"></a>
## Precedence (priority)

When multiple adapters match the same target type, `precedence()` (legacy `getPrecedence()`) decides their precedence. `ConversionPrecedence` has exactly **4 levels**, with no `NORMAL`: `LOWEST < LOW < HIGH < HIGHEST`.

| Level | Purpose |
|---|---|
| `HIGHEST` | Platform/core adapters |
| `HIGH` | Built-in general adapters |
| `LOW` | Fallback |
| `LOWEST` | Last resort |

The “Purpose” column describes how built-in adapters divide their responsibilities; it is **not validation**. No code checks who you are or which level you choose. There are two default cases: legacy `getPrecedence()` and `BaseJsTypeAdapter` default to `LOWEST`; when implementing the new `JsTypeAdapter` interface directly, `precedence()` has no default and must be supplied.

<a id="wiki-section-13"></a>
### Conflict policy

The repository code establishes only this: `JSTypeAdapterRegistry.Impl` is an `ArrayList` and performs **no deduplication or conflict validation** for the same `(target type, precedence)` pair. It neither reports an error nor logs a message. The registry passes all adapters in order to Graal's `HostAccess.targetTypeMapping`; registration follows plugin loading order, with higher-priority plugins loading first.

**Graal decides which adapter ultimately wins within one precedence level, not this repository**. There is no local implementation or test fixing that behavior. Do not rely on relative order at the same level; use different precedence levels for deterministic selection.

The following are recommendations derived from that behavior; no code enforces them:

- Avoid having multiple addons declare the same precedence for the same type, which effectively leaves selection to plugin loading order.
- `LOW` / `LOWEST` are safer for addon adapters: built-in general adapters use `HIGH`, and platform/core adapters use `HIGHEST`. Choosing a lower level explicitly gives built-ins priority. To **override** built-in behavior, choose a higher level; this is allowed and is not blocked.

<a id="wiki-section-14"></a>
## Test adapters

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

<a id="wiki-section-15"></a>
## Platform independence

Adapters **can** be cross-platform, but no mechanism requires that: an adapter compiled for one loader can still register and work. Built-in adapters use the following layering for cross-platform support; use the same organization if it suits your plugin:
- New `JsTypeAdapter` / `BaseJsTypeAdapter` live in the `com.tkisor.nekojs.api.data` contract package with no Minecraft/Graal dependency. Legacy `JSTypeAdapter` / `AbstractJSTypeAdapter` live in `com.tkisor.nekojs.js.type_adapter`, depend on Graal `Value`, and have no Minecraft dependency. guardLint enforces these dependency boundaries **in this repository**, not in your mod.
- Minecraft-specific logic, such as `instanceof ItemStack`, belongs in `fromHostObject`; the platform supplies host objects.
- Platform-specific adapters, such as `ItemAdapter`, belong in the platform sourceSet.

<a id="wiki-section-16"></a>
## Next steps

- [Plugin development](plugin-development_us): the `registerAdapters` hook.
- [Probe type generation](probe-type-generation_us): how `AdapterInputShape` becomes `.d.ts` aliases.
- [Annotations](annotations_us): use `@Overload` for signatures adapters cannot express; an adapter's declared `inputShapes()` automatically broadens parameter types, as described above.
- Maintainer Java/JS examples and source-placement guide: `docs/maintenance/adding-events-adapters.md`.

<!-- wiki-nav -->

---

[Previous: Plugin development](plugin-development_us) · [Contents](Home) · [Next: Event extensions](event-extensions_us)
