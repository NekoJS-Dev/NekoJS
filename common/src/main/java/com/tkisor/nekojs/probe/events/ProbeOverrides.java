package com.tkisor.nekojs.probe.events;

import java.util.List;

/**
 * Immutable bundle of the results collected by the {@code probe.add_global} and {@code probe.snippets} events,
 * passed to each backend through {@link com.tkisor.nekojs.probe.ProbeContext#overrides()}.
 */
public record ProbeOverrides(List<GlobalDecl> globals, List<Snippet> snippets) {
    public ProbeOverrides {
        globals = List.copyOf(globals == null ? List.of() : globals);
        snippets = List.copyOf(snippets == null ? List.of() : snippets);
    }

    public static ProbeOverrides empty() {
        return new ProbeOverrides(List.of(), List.of());
    }
}
