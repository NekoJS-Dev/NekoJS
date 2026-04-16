package com.tkisor.nekojs.script.prop;

import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author ZZZank
 */
public interface ScriptPropertyRegistry {

    void register(ScriptProperty<?> property);

    Map<String, ScriptProperty<?>> view();

    @ApiStatus.Internal
    class Impl implements ScriptPropertyRegistry {
        private final Map<String, ScriptProperty<?>> all = new LinkedHashMap<>();

        @Override
        public void register(ScriptProperty<?> property) {
            if (all.containsKey(property.name)) {
                throw new IllegalArgumentException(String.format("property '%s' already registered", property.name));
            }
            property.ordinal = all.size();
            all.put(property.name, property);
        }

        @Override
        public Map<String, ScriptProperty<?>> view() {
            return Collections.unmodifiableMap(all);
        }
    }
}
