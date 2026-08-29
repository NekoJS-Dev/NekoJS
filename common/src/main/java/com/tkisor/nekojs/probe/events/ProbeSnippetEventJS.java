package com.tkisor.nekojs.probe.events;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code probe.snippets} 事件：脚本登记编辑器片段（VSCode {@code .code-snippets}）。当前仅 TS backend 消费
 * （合并进 {@code nekojs/.vscode/nekojs.code-snippets}，probe 拥有的片段名替换，用户片段保留）。
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
