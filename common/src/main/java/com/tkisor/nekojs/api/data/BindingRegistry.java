package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.script.ScriptType;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author ZZZank
 */
public interface BindingRegistry {

    boolean register(Binding2 binding);

    default boolean register(String name, Object value) {
        return register(Binding2.of(name, value));
    }

    default boolean register(ScriptType targetType, String name, Object value) {
        return targetType == scriptType() && register(name, value);
    }

    Map<String, Binding2> viewRegistered();

    ScriptType scriptType();

    @VisibleForTesting
    final class BindingRegistryImpl implements BindingRegistry {
        private final ScriptType scriptType;
        private final Map<String, Binding2> bindings = new LinkedHashMap<>();

        public BindingRegistryImpl(ScriptType scriptType) {
            this.scriptType = scriptType;
        }

        @Override
        public boolean register(Binding2 binding) {
            return bindings.put(binding.name(), binding) == null;
        }

        @Override
        public Map<String, Binding2> viewRegistered() {
            return Collections.unmodifiableMap(bindings);
        }

        @Override
        public ScriptType scriptType() {
            return scriptType;
        }
    }
}
