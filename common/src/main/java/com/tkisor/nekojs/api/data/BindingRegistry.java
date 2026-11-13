package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.ScriptType;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author ZZZank
 */
public interface BindingRegistry {

    boolean register(Binding binding);

    /** Registers a module-only export; legacy registrations remain globals. */
    default boolean register(Binding binding, java.util.function.Consumer<BindingExposure> configure) {
        throw new UnsupportedOperationException("[NEKO-4057] This binding registry does not support API modules");
    }

    default java.util.List<ModuleBinding> moduleBindings() {
        return java.util.List.of();
    }

    default boolean register(String name, Object value) {
        return register(Binding.of(name, value));
    }

    default boolean register(ScriptType targetType, String name, Object value) {
        return targetType == scriptType() && register(name, value);
    }

    Map<String, Binding> viewRegistered();

    ScriptType scriptType();

    @VisibleForTesting
    final class BindingRegistryImpl implements BindingRegistry {
        private static final org.slf4j.Logger LOGGER =
                org.slf4j.LoggerFactory.getLogger("nekojs.bootstrap");

        private final ScriptType scriptType;
        private final Map<String, Binding> bindings;
        private final Map<String, ModuleBinding> modules;
        private final String owner;

        public BindingRegistryImpl(ScriptType scriptType) {
            this(scriptType, null, new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        private BindingRegistryImpl(ScriptType scriptType, String owner, Map<String, Binding> bindings,
                                    Map<String, ModuleBinding> modules) {
            this.scriptType = scriptType;
            this.owner = owner;
            this.bindings = bindings;
            this.modules = modules;
        }

        public BindingRegistryImpl scopedTo(String owner) {
            return new BindingRegistryImpl(scriptType, owner, bindings, modules);
        }

        @Override
        public boolean register(Binding binding, java.util.function.Consumer<BindingExposure> configure) {
            java.util.Objects.requireNonNull(configure, "configure");
            BindingExposure exposure = new BindingExposure();
            configure.accept(exposure);
            var category = exposure.freeze();
            if (owner == null) {
                throw new IllegalArgumentException("[NEKO-4057] API module registration requires a resolved mod owner");
            }
            if (category == BindingExposure.Category.STABLE) {
                com.tkisor.nekojs.core.api.ModulePublicTypes.validateStable(binding.valueType(), binding.value() instanceof Class<?>);
            }
            ModuleBinding entry = new ModuleBinding(owner, category, scriptType, binding);
            String key = entry.moduleId() + ":" + binding.name();
            if (modules.putIfAbsent(key, entry) != null) {
                throw new IllegalArgumentException("[NEKO-4057] Duplicate API module export: " + key);
            }
            return true;
        }

        @Override
        public java.util.List<ModuleBinding> moduleBindings() {
            return java.util.List.copyOf(modules.values());
        }

        @Override
        public boolean register(Binding binding) {
            if (bindings.containsKey(binding.name())) {
                // First wins: the binding registered first keeps the name, and later registrations
                // (usually from third-party plugins) are silently dropped. Log a warning so the plugin
                // author can see that the registration was rejected.
                LOGGER.warn(
                        "[NEKO-4001] 同名绑定 '{}' 已注册，后者被忽略（首胜），被拒绝绑定的 valueType: {} — duplicate binding name ignored",
                        binding.name(),
                        binding.valueType().getName());
                return false;
            }
            bindings.put(binding.name(), binding);
            return true;
        }

        @Override
        public Map<String, Binding> viewRegistered() {
            return Collections.unmodifiableMap(bindings);
        }

        @Override
        public ScriptType scriptType() {
            return scriptType;
        }
    }
}
