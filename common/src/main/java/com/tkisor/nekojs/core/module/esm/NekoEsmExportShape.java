package com.tkisor.nekojs.core.module.esm;

import java.util.Set;

public record NekoEsmExportShape(Set<String> names, Set<String> ambiguous, boolean unknown) {
    public NekoEsmExportShape {
        names = names == null ? Set.of() : Set.copyOf(names);
        ambiguous = ambiguous == null ? Set.of() : Set.copyOf(ambiguous);
    }

    public static NekoEsmExportShape of(Set<String> names) {
        return of(names, Set.of());
    }

    public static NekoEsmExportShape of(Set<String> names, Set<String> ambiguous) {
        return new NekoEsmExportShape(names, ambiguous, false);
    }

    public static NekoEsmExportShape unresolved() {
        return new NekoEsmExportShape(Set.of(), Set.of(), true);
    }

    public boolean has(String name) {
        return unknown || names.contains(name);
    }

    public boolean ambiguous(String name) {
        return !unknown && ambiguous.contains(name);
    }
}
