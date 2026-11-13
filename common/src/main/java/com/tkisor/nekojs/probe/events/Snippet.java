package com.tkisor.nekojs.probe.events;

/**
 * An editor snippet collected by the {@code probe.snippets} event (the fields of the VSCode
 * {@code .code-snippets} format). Only the TS backend consumes it today, writing it to
 * {@code nekojs/.vscode/nekojs.code-snippets}.
 */
public record Snippet(String name, String prefix, String body, String description) {
    public Snippet {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("snippet name must not be blank");
        if (prefix == null || prefix.isBlank()) throw new IllegalArgumentException("snippet prefix must not be blank");
        if (body == null) body = "";
    }
}
