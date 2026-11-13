package com.tkisor.nekojs.probe.events;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code probe.snippets} event: scripts register editor snippets (VSCode {@code .code-snippets}). Only the TS
 * backend consumes them today; they are merged into {@code nekojs/.vscode/nekojs.code-snippets}, where snippets
 * owned by probe replace each other by name and user snippets are kept.
 */
public final class ProbeSnippetEventJS {
    private final List<Snippet> snippets = new ArrayList<>();

    public void add(String name, String prefix, String body) {
        add(name, prefix, body, null);
    }

    public void add(String name, String prefix, String body, String description) {
        snippets.add(new Snippet(name, prefix, body, description));
    }

    public List<Snippet> snippets() {
        return List.copyOf(snippets);
    }
}
