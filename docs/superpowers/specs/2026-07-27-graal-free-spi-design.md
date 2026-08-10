# Graal-free Conversion SPI

**Date:** 2026-07-27  
**Status:** implemented (2026-08) — `JsValueView` / `JsTypeAdapter` / `ConversionPrecedence` / `AbstractJsTypeAdapter` 已在 `common-api/.../api/data/`；桥三件套（`GraalValueView`/`LegacyAdapterBridge`/`NewAdapterBridge`）在 `common/.../core/bridge/`；`common-api` 零 Graal import（grep 验证）。注：仅 `ComponentAdapter` 已迁新 SPI，其余 ~27 平台适配器仍 legacy（见 `.zcode/audit-improvement/05-adapter-marshalling.md` ADAPT-1）。  
**Context:** `:common-api` module skeleton exists; no actual API packages have been migrated yet because `JSTypeAdapter` and related conversion types leak Graal types (`graal.graalvm.polyglot.Value`, `HostAccess.TargetMappingPrecedence`) into the public SPI.

## Goal

Define a Graal-free replacement for the JS value conversion SPI (`JSTypeAdapter`, `AbstractJSTypeAdapter`, `JSTypeAdapterRegistry`) so that conversion contracts can live in `:common-api` without depending on Graal. The runtime (`:common-runtime`) bridges Graal types to the new SPI internally.

## Non-goals

- Do NOT migrate all ~30 platform adapters to the new SPI in this phase. Only the SPI types and bridge infrastructure are created. Adapters migrate incrementally later.
- Do NOT touch `EventBusJS`, `EventGroupJS`, `ScriptEventGroupJS`, or `RecipeSchemaHost`. These are JS-side bridge implementations that belong in `:common-runtime`, not in `:common-api`.
- Do NOT change the JS-side behavior. The new SPI is an internal refactoring of the Java adapter surface; all existing scripts and bindings continue to work.

## Design

### New types in `:common-api` (`com.tkisor.nekojs.api.data`)

#### `JsValueView` — replaces `graal.graalvm.polyglot.Value`

```
public interface JsValueView {
    boolean isNull();
    boolean isString();
    boolean isNumber();
    boolean isBoolean();
    boolean isHostObject();
    boolean isArray();

    String asString();
    int asInt();
    double asDouble();
    boolean asBoolean();
    <T> T asHostObject(Class<T> type);  // typed access

    boolean hasMember(String key);
    JsValueView getMember(String key);
    JsValueView getArrayElement(long index);
    long getArraySize();
    Collection<String> getMemberKeys();
}
```

Stability: `@Since("1.1.0-preview")`. Only stable JS-type-query and read operations. No Graal `Value` exposure, no `execute()`, no `getContext()`, no `getMetaObject()`.

#### `ConversionPrecedence` — replaces `HostAccess.TargetMappingPrecedence`

```
public enum ConversionPrecedence {
    LOWEST,
    LOW,
    HIGH,
    HIGHEST
}
```

#### `ConversionContext` — new concept, opaque context

```
public final class ConversionContext {
    private static final ConversionContext EMPTY = new ConversionContext();

    public static ConversionContext empty() { return EMPTY; }

    private ConversionContext() {}
}
```

Accepts no configuration in this phase. Reserved for future extension (e.g., `ScriptType` awareness, strict-mode flags).

#### `JsTypeAdapter<T>` — replaces `JSTypeAdapter<T>`

```
public interface JsTypeAdapter<T> {
    Class<T> targetType();

    boolean supports(JsValueView value, ConversionContext context);

    T convert(JsValueView value, ConversionContext context);

    ConversionPrecedence precedence();

    default List<AdapterInputShape> inputShapes() { return List.of(); }
}
```

Key differences from old `JSTypeAdapter<T>`:
- Does NOT extend `Predicate<Value>` or `Function<Value, T>`. Callers use `supports()` + `convert()`.
- `precedence()` returns `ConversionPrecedence` instead of `HostAccess.TargetMappingPrecedence`.
- `AdapterInputShape` is reused as-is (already Graal-free).

#### `AbstractJsTypeAdapter<T>` — convenience base class

```
public abstract class AbstractJsTypeAdapter<T> implements JsTypeAdapter<T> {
    private final Class<T> targetType;

    protected AbstractJsTypeAdapter(Class<T> targetType) { this.targetType = targetType; }

    @Override public Class<T> targetType() { return targetType; }
    @Override public ConversionPrecedence precedence() { return ConversionPrecedence.LOWEST; }

    protected boolean acceptNull() { return false; }
    protected T defaultValue() { return null; }
    protected T fromString(String s) { throw new ValueConversionException("not supported"); }
    protected abstract T fromHostObject(Object host);

    protected boolean acceptOther(JsValueView value) { return false; }
    protected T fromOther(JsValueView value) { throw new ValueConversionException("not supported"); }

    @Override
    public final boolean supports(JsValueView value, ConversionContext ctx) {
        if (value.isNull()) return acceptNull();
        if (value.isString()) return true;
        if (value.isHostObject()) {
            Object host = value.asHostObject(Object.class);
            return host != null && targetType.isAssignableFrom(host.getClass());
        }
        return acceptOther(value);
    }

    @Override
    public final T convert(JsValueView value, ConversionContext ctx) {
        if (value.isNull()) return defaultValue();
        if (value.isString()) return fromString(value.asString());
        if (value.isHostObject()) return fromHostObject(value.asHostObject(Object.class));
        return fromOther(value);
    }
}
```

#### `JsTypeAdapterRegistry` — replaces `JSTypeAdapterRegistry`

```
public interface JsTypeAdapterRegistry {
    <T> void register(JsTypeAdapter<T> adapter);

    Collection<JsTypeAdapter<?>> view();
}
```

Convenience `register(Class, Predicate<Value>, Function<Value,T>)` is removed — it leaked Graal `Value`. If convenience is needed later, it can take `JsValueView`-based lambdas.

### Bridge types in `:common-runtime` (`com.tkisor.nekojs.core.bridge`)

These are internal to `:common-runtime` and have no stability guarantees.

#### `GraalValueView` — `implements JsValueView`, wraps a Graal `Value`

```
final class GraalValueView implements JsValueView {
    private final Value delegate;

    GraalValueView(Value delegate) { this.delegate = delegate; }

    static JsValueView wrap(Value v) { return v == null || v.isNull() ? null : new GraalValueView(v); }

    // delegates all methods to equivalent Value.* calls
    boolean isNull() { return delegate.isNull(); }
    String asString() { return delegate.asString(); }
    // ...
}
```

#### `LegacyAdapterBridge` — wraps an old `JSTypeAdapter<T>` as a new `JsTypeAdapter<T>`

```
final class LegacyAdapterBridge<T> implements JsTypeAdapter<T> {
    private final JSTypeAdapter<T> legacy;

    @Override public Class<T> targetType() { return legacy.getTargetClass(); }

    @Override public boolean supports(JsValueView value, ConversionContext ctx) {
        if (!(value instanceof GraalValueView gv)) return false;
        return legacy.test(gv.unwrap());
    }

    @Override public T convert(JsValueView value, ConversionContext ctx) {
        if (!(value instanceof GraalValueView gv)) throw new ValueConversionException("unsupported value type");
        return legacy.apply(gv.unwrap());
    }

    @Override public ConversionPrecedence precedence() {
        return mapPrecedence(legacy.getPrecedence());
    }
}
```

#### `NewAdapterBridge` — wraps a new `JsTypeAdapter<T>` as an old `JSTypeAdapter<T>`

Used by the runtime's `JSTypeAdapterRegistry.Impl` to accept new adapters through the old wiring, so `NekoJSPlugin` can register new adapters without the runtime noticing.

### Impact on existing types

| Type | Location | Change |
|------|----------|--------|
| `JSTypeAdapter` | `common/api` | Unchanged. Eventually deprecated, not moved yet. |
| `AbstractJSTypeAdapter` | `common/api/data` | Unchanged. |
| `JSTypeAdapterRegistry` | `common/api/data` | Unchanged (but internally accepts both old + new adapters via bridge). |
| `AdapterCatalogEntry` | `common/api/catalog` | `HostAccess.TargetMappingPrecedence` → `ConversionPrecedence` for the record field. |
| `EventGroup`, `EventBus`, etc. | `common/api/event` | Already Graal-free, no changes. |
| `Binding`, `BindingRegistry` | `common/api/data` | Already Graal-free, no changes. |
| `NekoJSPlugin` | `common/api` | No signature changes. New adapter registration flows through existing `registerAdapters` + `JSTypeAdapterRegistry`. |

### Verification

1. `:common-api` compiles with NO Graal imports (`grep -r "graal\." common-api/src` returns empty).
2. `:common` compiles and all `common:test` tests pass.
3. All four platforms compile.
4. `NewAdapterBridge` unit test: register a new-style adapter into `JSTypeAdapterRegistry.Impl`, verify it serves conversions.

---

## Open Questions

- **Q:** Should `JsTypeAdapter<T>` extend `java.util.function.Predicate<JsValueView>` for lambda convenience?  
  **Resolved:** No. The `supports`/`convert` split is intentional to avoid confusion with old `JSTypeAdapter` which conflated both.

- **Q:** Should `JsValueView` include `isProxyObject()` or `isExecutable()`?  
  **Resolved:** No. Proxy/executable detection is runtime-internal. The SPI only needs to distinguish JS primitive types and host objects for conversion purposes.
