<!-- wiki-page: type-adapters; locale: us -->

> **English** · [中文](type-adapters_cn)

<a id="wiki-section-1"></a>
# Type adapters

A type adapter converts a JS value passed by a script into the Java type a method expects. For example, when a Java method takes an `ItemStack`, adapters let a script pass `'minecraft:stone'`, `'16x minecraft:stone'` or `{ id: 'minecraft:stone', count: 2 }` directly.

Write an adapter when your plugin exposes Java methods and you want scripts to pass simpler values. This page is for Java developers writing NekoJS plugins.

<a id="wiki-section-2"></a>
## Minimal example

Say your plugin has a position type and a binding method that takes it:

```java
package com.example.myaddon;

public record MyPos(int x, int y) {}
```

This adapter lets scripts pass `{ x: 1, y: 2 }` or the string `'1,2'`:

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
        // Only reached when host is already a MyPos
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

Register it in your plugin:

```java
@Override
public void registerAdapters(JSTypeAdapterRegistry registry) {
    registry.register(new MyPosAdapter());
}
```

Now any Java method with a `MyPos` parameter can be called like this:

```javascript
MyAddon.teleport({ x: 1, y: 2 })
MyAddon.teleport('3,4')
```

For how plugins are written and discovered, see [Plugin development](plugin-development_us).

<a id="wiki-section-3"></a>
## How it works

At startup, NekoJS installs one GraalJS `HostAccess.targetTypeMapping` per registered adapter. When a Java method needs a `T` and the script passes a JS value, GraalJS asks the adapter whether it can handle the value, and if so, calls it to convert.

A few facts that affect how you write adapters:

- Mappings are keyed by the **exact target class**. An adapter registered for `MyPos.class` does not apply to subclasses or interfaces.
- Mappings only see the raw class, not type arguments. A `Box<T>` adapter is registered for `Box.class`, and NekoJS will not convert the inner `T` for you.
- All script contexts share the same mappings. Adapters are registered once when plugins load; an ordinary reload does not re-register them.
- A `ClassCastException` thrown inside an adapter is wrapped as `NEKO-5202`, so it isn't mistaken for an overload mismatch and silently skipped.

<a id="wiki-section-4"></a>
## Which API to use

There are two interfaces, both registered through the same `JSTypeAdapterRegistry`:

| Interface | Dependencies | Use it for |
|---|---|---|
| `JsTypeAdapter<T>` (new, recommended) | No Minecraft, no Graal | New adapters |
| `JSTypeAdapter<T>` (legacy) | Graal `Value` | Existing code; cases that need to read `Value` directly |

Each interface has a template base class that dispatches on the input kind for you:

| Class | You must implement | You can override |
|---|---|---|
| `BaseJsTypeAdapter<T>` (new) | Constructor taking the target class, `fromHostObject` | `fromString`, `acceptOther`/`fromOther`, `acceptNull`/`defaultValue`, `supportsString`, `precedence`, `inputShapes` |
| `AbstractJSTypeAdapter<T>` (legacy) | `getTargetClass`, `fromHostObject` | `fromString`, `acceptOther`/`fromOther`, `acceptNull`/`defaultValue`, `getPrecedence`, `inputShapes` |
| `JsTypeAdapter<T>` (implemented directly) | `targetType`, `supports`, `convert`, `precedence` | `inputShapes` |
| `JSTypeAdapter<T>` (implemented directly) | `getTargetClass`, `test`, `apply` | `getPrecedence`, `inputShapes`, `syntaxDoc` |

The base classes are optional; implementing the interface directly works just as well. The built-in `ComponentAdapter` is a complete example written with `BaseJsTypeAdapter`.

<a id="wiki-section-5"></a>
### How BaseJsTypeAdapter dispatches

| Script passes | `supports` decides by | `convert` calls |
|---|---|---|
| `null` | `acceptNull()`, `false` by default | `defaultValue()` |
| A string | Trying `fromString` once; a `ValueConversionException` means "not accepted" | `fromString` |
| A Java object | Accepted only if it is an instance of the target type | `fromHostObject` |
| Any other JS value (object, array, number) | `acceptOther`, `false` by default | `fromOther` |

This leads to three rules:

- **Throw `ValueConversionException` for a malformed string.** `supports` only catches that exception. Anything else (such as `NumberFormatException`) goes straight to the script instead of giving other adapters a chance.
- **Don't return `null` from `fromHostObject` or `fromOther`.** `null` counts as a failed conversion and throws. To say "not accepted", return `false` from `supports`.
- **Java objects of other types never reach `fromHostObject`.** To accept another Java type (say, turning a `Vec3` into your position), implement `JsTypeAdapter` directly or use the legacy `AbstractJSTypeAdapter`, which passes every host object to `fromHostObject` and treats `null` as "not accepted".

<a id="wiki-section-6"></a>
### Shortcuts without a class

`JSTypeAdapterRegistry` also has two default methods:

```java
// Register a legacy adapter from two lambdas
registry.register(MyPos.class,
    value -> value.hasMember("x") && value.hasMember("y"),
    value -> new MyPos(value.getMember("x").asInt(), value.getMember("y").asInt()));

// Let ItemLike parameters accept everything the Item adapter accepts
registry.registerAlias(ItemLike.class, Item.class, Item::asItem);
```

Without an explicit precedence, `registerAlias` uses the precedence of the source type's adapter.

On the Minecraft side there is also `TypeAdapterDsl` (`com.tkisor.nekojs.js.type_adapter`):

- `TypeAdapterDsl.registerCodec(registry, Foo.class, Foo.CODEC)`: turns the JS value into JSON and parses it with a Mojang `Codec`. Precedence `LOWEST`.
- `TypeAdapterDsl.registerRecord(registry, MyRecord.class)`: builds a record from a JS object by component name. Every field is required unless you pass a `Map` of defaults. Unknown fields in the object are an error.

<a id="wiki-section-7"></a>
## Precedence

When several adapters can handle the same target type, precedence decides. `ConversionPrecedence` has only four levels, from low to high: `LOWEST`, `LOW`, `HIGH`, `HIGHEST`. There is no `NORMAL`.

| API | Default precedence |
|---|---|
| Legacy `JSTypeAdapter` and `AbstractJSTypeAdapter` | `LOWEST` |
| `BaseJsTypeAdapter` | `LOWEST` |
| `JsTypeAdapter` implemented directly | No default; you must return one |

Before you pick a level, check the built-in adapter you want to override: `ItemStackAdapter` and `FluidStackAdapter` use `LOW`, `RecipeFilterAdapter` uses `HIGH`, and `CodecAdapter`, `RecipeJsonValueAdapter` and most base-class adapters use `LOWEST`.

**Within one level, the order is not defined.** The registry is a plain list with no duplicate checks and no logging. All adapters at the same level go to GraalJS, and GraalJS decides which one wins; NekoJS makes no promise. Use a different level when you need a predictable result.

<a id="wiki-section-8"></a>
## Editor completion: inputShapes

`inputShapes()` tells `/nekojs probe` which forms the adapter accepts. Probe generates an input alias such as `$MyPos_` and uses it wherever a `MyPos` parameter appears, so the editor won't flag `'1,2'` as an error.

It only affects the editor, not runtime behavior. Conversion works without it, but the `.d.ts` parameter stays `$MyPos` and the editor underlines string arguments.

Common shape factories (all on `AdapterInputShape`):

| Factory | Meaning | Generated TypeScript |
|---|---|---|
| `self()` | The target type itself | `$MyPos` |
| `host(cls)` | Another Java type | `$Bar` |
| `string()` | Any string | `string` |
| `literal(text)` | One fixed string | `"*"` |
| `number()` / `bool()` | Number / boolean | `number` / `boolean` |
| `arrayOf(shape)` | An array | `T[]` |
| `object(Slot...)` | An object; `Slot.req` is required, `Slot.opt` optional | `{ x: number, y?: number }` |
| `union(shape...)` | Several forms in one position | `A \| B` |
| `registry(type)` | A registry ID, with completion | `RegistryTypes.Item` |
| `registryTag(type)` | A registry tag ID (without `#`) | `RegistryTypes.ItemTag` |
| `namespace()` | A namespace (mod IDs plus namespaces seen in registries) | `RegistryTypes.Namespace` |
| `template(prefix, hole)` / `template(prefix, hole, suffix)` | A fixed prefix/suffix around a completable part | A template literal type |
| `raw(ts)` | TypeScript text output as-is | As written |

There are also `named(suffix, shape)` (names part of a shape for reuse) and `callable(params, result)` (a callback).

> **Don't put a bare `string` in a union.** Once a TypeScript union contains `string`, every string literal member is absorbed and the editor stops offering ID completion. To accept forms like `#tag` and `@mod` and keep completion, use template shapes:
>
> ```java
> AdapterInputShape.union(
>     AdapterInputShape.registry("Item"),                                       // minecraft:stone
>     AdapterInputShape.template("#", AdapterInputShape.registryTag("Item")),  // #minecraft:planks
>     AdapterInputShape.template("@", AdapterInputShape.namespace()),          // @create
>     AdapterInputShape.literal("*"))
> ```

`raw(ts)` only emits text and collects no imports, so any type name in it must already exist in the generated declarations. `host(cls)` records the dependency for you.

<a id="wiki-section-9"></a>
## Built-in adapters

This table lists common built-in adapters. It describes the usual forms, not every rule each converter checks.

| Target type | Accepted forms | Platform |
|---|---|---|
| `ItemStack` | `'minecraft:stone'`, `'16x minecraft:stone'`, a bracketed string with components, `{ id or item, count?, components? }`, `ItemStack`, `Item`, an ID | All |
| `Item` (and `ItemLike`) | Item ID, `Item`, `ItemStack`, `Block`, an ID | All |
| `Ingredient` | ID, `'#tag'`, `'@mod'`, `'*'`, `'/regex/'`, arrays, objects, stacks, ingredients | All |
| `SizedIngredient` | Same as above, with a count | NeoForge only |
| `FluidStack` / `FluidIngredient` / `SizedFluidIngredient` | Fluid ID, `{ fluid, amount }` | NeoForge only |
| `Block` / `BlockState` | Block ID; `BlockState` also accepts a `Block` | All |
| `BlockPos` / `Vec3` | `{ x, y, z }`, `[x, y, z]`, `BlockPos`, `Vec3`. `BlockPos` does not accept strings | All |
| `CompoundTag` | A plain JS object | All |
| `Component` | A string, a `Component`, text built with `Text` | All |
| `Identifier` (`ResourceLocation` on 1.21.1) | A string | All |
| `TagKey` | `'namespace:path'`, optionally with `#`, `'registry\|tag'`, `{ type, tag }`; the item registry by default | All |
| `Holder`, `EntityType`, `MobEffect`, `Potion`, `SoundEvent`, `ParticleType`, `BlockEntityType`, `CreativeModeTab` | A registry ID string | All |
| Other vanilla registry types | A registry ID string (generated automatically) | All |
| `RecipeFilter` | A filter object; see [Recipe system](recipe-system_us) | All |

The adapters for "other vanilla registry types" are generated: `RegistryAutoAdapterScanner` scans `BuiltInRegistries` (plus `Registries` on NeoForge) and adds an ID lookup adapter, at `LOWEST`, for every registry type without a dedicated one. If your mod has its own registry holder class, call `RegistryAutoAdapterScanner.installInto(registry, List.of(MyRegistries.class))`.

A structured `ItemStack` input may use `id` or `item`, not both. `components` are parsed with the current registry context: the running server first, then the client connection, then the built-in registries. To remove a component, use `!namespace:id` as the key. A component parse failure reports `NEKO-4054`.

<a id="wiki-section-10"></a>
## Native recipe method arguments

Recipe handlers and builder methods are called through reflection, which adds one layer on top of normal adapters:

- `List<T>`, `Collection<T>` and `Iterable<T>` parameters accept a JS array; each element is converted as `T`.
- `Map<K, V>` parameters accept a JS object; keys and values are both converted.
- Nested containers of these kinds are handled recursively; the innermost values still go through adapters.
- `Optional<T>` parameters convert the value passed in, and `null` becomes empty. Only trailing consecutive `Optional` parameters can be omitted.

Arguments that are already Java collections are passed as-is, without converting each element. `Set`, concrete collection classes and other generic containers get no such handling and only use the normal adapter mapping.

If a recipe schema field defines its own `RecipeFieldValueCodec`, that codec encodes the whole field and adapters are not involved. See [Recipe system](recipe-system_us).

<a id="wiki-section-11"></a>
## Testing

Adapters written against the new interface don't depend on Graal, so unit tests can call `supports`/`convert` directly. To use real JS values, wrap a Graal `Value` as a `JsValueView` with NekoJS's internal `GraalValueView`:

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

`Context` and `Value` come from NekoJS's relocated Graal package, `graal.graalvm.polyglot`.

<a id="wiki-section-12"></a>
## Keeping adapters platform-neutral

Nothing forces an adapter to be cross-platform, but the built-in adapters are layered like this, and you can follow the same layout:

- Logic that only uses `JsTypeAdapter`/`BaseJsTypeAdapter` can live in loader-independent code.
- Parts that use Minecraft types (`instanceof ItemStack` and so on) live on the Minecraft side, where the platform provides the host objects.
- Types that exist on only one loader (such as NeoForge's fluid classes) get adapters registered only on that platform.

<a id="wiki-section-13"></a>
## Next steps

- [Plugin development](plugin-development_us): `registerAdapters` and the other plugin hooks.
- [Probe type generation](probe-type-generation_us): how `inputShapes` become `.d.ts` aliases.
- [Annotations](annotations_us): use `@Overload` for signatures an adapter can't express.

<!-- wiki-nav -->

---

[Previous: Plugin development](plugin-development_us) · [Contents](Home) · [Next: Event extensions](event-extensions_us)
