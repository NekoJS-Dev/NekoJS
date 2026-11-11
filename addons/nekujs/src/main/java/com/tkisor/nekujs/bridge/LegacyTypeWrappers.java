package com.tkisor.nekujs.bridge;

import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.TypeWrapperRegistry;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.latvian.mods.rhino.util.wrap.TypeWrapperFactory;
import dev.latvian.mods.rhino.util.wrap.TypeWrapperValidator;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Collects side-eligible definitions and selects only the active managed phase's wrapper. */
final class LegacyTypeWrappers {
    private LegacyTypeWrappers() {}

    private record Definition(TypeWrapperFactory<?> factory, TypeWrapperValidator validator) {}

    static void register(JSTypeAdapterRegistry registry, boolean client, Consumer<TypeWrapperRegistry> collect) {
        Map<Class<?>, EnumMap<ScriptType, Definition>> definitions = new LinkedHashMap<>();
        for (ScriptType type : ScriptType.values()) {
            if (type == ScriptType.CLIENT && !client) continue;
            var wrappers = new TypeWrapperRegistry(type);
            collect.accept(wrappers);
            var validators = wrappers.validators();
            wrappers.factories().forEach((target, wrapper) -> definitions.computeIfAbsent(target,
                    ignored -> new EnumMap<>(ScriptType.class)).put(type, new Definition(wrapper, validators.get(target))));
        }
        definitions.forEach((target, wrappers) -> registerAdapter(registry, target, Map.copyOf(wrappers)));
    }

    private static <T> void registerAdapter(JSTypeAdapterRegistry registry, Class<T> target,
            Map<ScriptType, Definition> wrappers) {
        TypeInfo targetType = TypeInfo.of(target);
        registry.register(target, value -> {
            ScriptType type = currentPhase();
            var wrapper = type == null ? null : wrappers.get(type);
            return wrapper != null && !value.canExecute() && wrapper.validator().isValid(input(value), targetType);
        }, value -> {
            ScriptType type = currentPhase();
            if (type == null) {
                throw new IllegalStateException("[NEKO-4263] Legacy conversion requires a managed production script phase");
            }
            var wrapper = wrappers.get(type);
            if (wrapper == null) throw new IllegalStateException("[NEKO-4263] Legacy conversion is unavailable in phase " + type);
            Object input = input(value);
            var context = new KubeJSContext(type, (name, binding) -> {
                throw new UnsupportedOperationException("[NEKO-4264] Scope mutation is not available during type conversion");
            });
            Object result = wrapper.factory().wrap(context, input, targetType);
            if (!target.isInstance(result)) throw new IllegalArgumentException("[NEKO-4261] Legacy wrapper returned an invalid " + target.getName());
            return target.cast(result);
        });
    }

    private static Object input(Value value) {
        return value.isHostObject() ? value.asHostObject() : value.as(Object.class);
    }

    private static ScriptType currentPhase() {
        Context context;
        try {
            context = Context.getCurrent();
        } catch (IllegalStateException noActiveContext) {
            return null;
        }
        var type = ScriptContextRegistry.scriptTypeOf(context);
        if (type == null) return null;
        return switch (type) {
            case STARTUP -> ScriptType.STARTUP;
            case SERVER -> ScriptType.SERVER;
            case CLIENT -> ScriptType.CLIENT;
            case TEST -> null;
        };
    }
}
