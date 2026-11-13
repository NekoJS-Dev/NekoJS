package com.tkisor.nekojs.probe.events;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code probe.add_global} event: scripts register extra global declarations (name plus type). Each backend
 * renders them in its own language: TS writes them to {@code @manual/globals.d.ts} ({@code declare const Name: T;}),
 * Python adds them to {@code nekojs/__init__.pyi}.
 *
 * <p>The type argument accepts a string (one containing {@code .} becomes a Java fully qualified name SYMBOL,
 * otherwise it is a primitive type name) or a {@link com.tkisor.nekojs.api.surface.ApiTypeRef}.
 */
public final class ProbeAddGlobalEventJS {
    private final List<GlobalDecl> globals = new ArrayList<>();

    /** Registers a global: {@code add("MyFlag", "boolean")} / {@code add("Helper", "com.example.Helper")}. */
    public void add(String name, Object typeDesc) {
        globals.add(new GlobalDecl(name, ProbeModifyTypeEventJS.resolveType(typeDesc)));
    }

    public List<GlobalDecl> globals() {
        return List.copyOf(globals);
    }
}
