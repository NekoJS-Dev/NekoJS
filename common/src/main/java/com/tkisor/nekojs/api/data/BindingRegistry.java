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
        private final Map<String, Binding> bindings = new LinkedHashMap<>();

        public BindingRegistryImpl(ScriptType scriptType) {
            this.scriptType = scriptType;
        }

        @Override
        public boolean register(Binding binding) {
            if (bindings.containsKey(binding.name())) {
                // 首胜语义：同名绑定以先注册者为准，后注册者（通常是第三方插件）被静默挤掉，
                // 这里打 warn 让插件作者能感知到注册被拒绝，而不是毫无知觉
                LOGGER.warn(
                        "同名绑定 '{}' 已注册，后者被忽略（首胜），被拒绝绑定的 valueType: {}",
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
