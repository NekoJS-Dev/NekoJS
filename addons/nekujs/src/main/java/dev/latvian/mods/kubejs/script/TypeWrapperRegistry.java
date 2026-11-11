package dev.latvian.mods.kubejs.script;

import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.latvian.mods.rhino.util.wrap.TypeWrapperFactory;
import dev.latvian.mods.rhino.util.wrap.DirectTypeWrapperFactory;
import dev.latvian.mods.rhino.util.wrap.TypeWrapperValidator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/** Collects conversion definitions; the native adapter registry owns their execution. */
public final class TypeWrapperRegistry {
    @FunctionalInterface public interface ContextFromFunction<T> extends BiFunction<Context, Object, T> {}
    @FunctionalInterface public interface RegistriesFromFunction<T> extends BiFunction<RegistryAccessContainer, Object, T> {}
    private final ScriptType type;
    private final Map<Class<?>, ContextFromFunction<?>> wrappers = new LinkedHashMap<>();
    private final Map<Class<?>, TypeWrapperFactory<?>> factories = new LinkedHashMap<>();
    private final Map<Class<?>, TypeWrapperValidator> validators = new LinkedHashMap<>();
    public TypeWrapperRegistry(ScriptType type) { this.type = Objects.requireNonNull(type); }
    public ScriptType scriptType() { return type; }
    public <T> boolean hasTypeWrapper(Class<T> target) { return wrappers.containsKey(target); }
    public <T> void register(Class<T> target, TypeWrapperValidator validator, ContextFromFunction<T> factory) {
        Objects.requireNonNull(factory);
        registerDefinition(target, validator, factory, (context, value, type) -> factory.apply(context, value));
    }
    private <T> void registerDefinition(Class<T> target, TypeWrapperValidator validator,
            ContextFromFunction<T> view, TypeWrapperFactory<T> factory) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(validator);
        if (wrappers.putIfAbsent(target, view) != null) {
            throw new IllegalArgumentException("[NEKO-4262] Duplicate legacy type wrapper: " + target.getName());
        }
        factories.put(target, factory);
        validators.put(target, validator);
    }
    public <T> void register(Class<T> target, TypeWrapperValidator validator, TypeWrapperFactory<T> factory) {
        Objects.requireNonNull(factory);
        TypeInfo targetType = TypeInfo.of(target);
        registerDefinition(target, validator, (context, value) -> factory.wrap(context, value, targetType), factory);
    }
    public <T> void register(Class<T> target, TypeWrapperFactory<T> factory) {
        register(target, TypeWrapperValidator.ALWAYS_VALID, factory);
    }
    public <T> void register(Class<T> target, RegistriesFromFunction<T> factory) {
        Objects.requireNonNull(factory);
        register(target, (ContextFromFunction<T>) (context, value) ->
                factory.apply(RegistryAccessContainer.of(context), value));
    }
    public <T> void register(Class<T> target, ContextFromFunction<T> factory) {
        register(target, TypeWrapperValidator.ALWAYS_VALID, factory);
    }
    public <T> void register(Class<T> target, TypeWrapperValidator validator, DirectTypeWrapperFactory<T> factory) {
        Objects.requireNonNull(factory);
        register(target, validator, (TypeWrapperFactory<T>) factory);
    }
    public <T> void register(Class<T> target, DirectTypeWrapperFactory<T> factory) {
        register(target, TypeWrapperValidator.ALWAYS_VALID, factory);
    }
    public Map<Class<?>, ContextFromFunction<?>> definitions() { return Map.copyOf(wrappers); }
    public Map<Class<?>, TypeWrapperFactory<?>> factories() { return Map.copyOf(factories); }
    public Map<Class<?>, TypeWrapperValidator> validators() { return Map.copyOf(validators); }
}
