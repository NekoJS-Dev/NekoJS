package com.tkisor.nekojs.script.prop;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author ZZZank
 */
public final class ScriptProperties {
    private final Object[] internal;
    public final ScriptPropertyRegistry registry;

    public ScriptProperties(ScriptPropertyRegistry registry) {
        this.registry = registry;
        this.internal = new Object[registry.view().size()];
    }

    public <T> T put(ScriptProperty<T> property, T value) {
        T[] internal = castInternal();
        int index = checkedIndex(property);

        var oldValue = internal[index];
        internal[index] = value;
        return oldValue;
    }

    public <T> Optional<T> get(ScriptProperty<T> property) {
        return Optional.ofNullable(this.<T>castInternal()[checkedIndex(property)]);
    }

    public <T> T getOrDefault(ScriptProperty<T> property) {
        var got = this.<T>castInternal()[checkedIndex(property)];
        return got != null ? got : property.defaultValue;
    }

    public Stream<Map.Entry<ScriptProperty<?>, ?>> stream() {
        return registry.view()
            .values()
            .stream()
            .mapMulti((prop, down) -> {
                var got = internal[prop.ordinal];
                if (got != null) {
                    down.accept(Map.entry(prop, got));
                }
            });
    }

    private int checkedIndex(ScriptProperty<?> property) {
        var index = property.ordinal;
        if (index < 0 || index >= internal.length) {
            throw new IllegalArgumentException("property not registered");
        }
        return index;
    }

    @SuppressWarnings("unchecked")
    private <T> T[] castInternal() {
        return (T[]) internal;
    }
}
