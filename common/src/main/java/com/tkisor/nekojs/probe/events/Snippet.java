package com.tkisor.nekojs.probe.events;

/**
 * {@code probe.snippets} 事件收集的编辑器片段（VSCode {@code .code-snippets} 格式字段）。
 * 当前仅 TS backend 消费（写入 {@code nekojs/.vscode/nekojs.code-snippets}）。
 */
public record Snippet(String name, String prefix, String body, String description) {
    public Snippet {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("snippet name must not be blank");
        if (prefix == null || prefix.isBlank()) throw new IllegalArgumentException("snippet prefix must not be blank");
        if (body == null) body = "";
    }
}
