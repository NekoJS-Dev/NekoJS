package com.tkisor.nekojs.api.catalog;

import java.nio.file.Path;

/**
 * 类型输出布局：probe 类型声明根目录（{@link #root()}）与 VSCode snippets 路径
 * （{@link #snippetsPath()}）。
 *
 * <p>作为 {@code NekoScriptCatalogSnapshot} 的公共契约 DTO，供内置 probe emitter 与外部
 * 类型工具消费。各 {@code ScriptType} 的实际声明子目录由 {@code WorkspaceGenerator} 以
 * {@code @side-only/<type>} paths 形式表达，不在本记录内——历史上曾有 {@code typeRoot(type)}
 * 返回 {@code <type>/probe-types} 的访问器，但该路径从未与实际输出目录匹配且无调用方，已删除。
 */
public record TypeOutputLayout(
        Path root,
        Path snippetsPath
) {
}
