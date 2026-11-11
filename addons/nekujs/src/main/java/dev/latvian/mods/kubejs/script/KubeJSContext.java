package dev.latvian.mods.kubejs.script;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Per-call native bridge, without an owned or retained JavaScript context. */
public final class KubeJSContext extends Context {
    private final ScriptType type;
    private final BiConsumer<String, Object> bindings;
    public KubeJSContext(ScriptType type, BiConsumer<String, Object> bindings) {
        this.type = Objects.requireNonNull(type);
        this.bindings = Objects.requireNonNull(bindings);
    }
    public ScriptType getType() { return type; }
    public void addToScope(Scriptable scope, String name, Object value) { bindings.accept(name, value); }
}
