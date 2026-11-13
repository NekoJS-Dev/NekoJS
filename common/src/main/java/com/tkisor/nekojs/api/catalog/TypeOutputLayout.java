package com.tkisor.nekojs.api.catalog;

import java.nio.file.Path;

/**
 * Type output layout: the root of the probe type declarations ({@link #root()}) and the VSCode
 * snippets path ({@link #snippetsPath()}).
 *
 * <p>It is the public DTO of {@code NekoScriptCatalogSnapshot}, consumed by the built-in probe
 * emitters and by external type tools. {@code WorkspaceGenerator} expresses the actual declaration
 * subdirectory of each {@code ScriptType} as {@code @side-only/<type>} paths, so it is not part of
 * this record.
 */
public record TypeOutputLayout(
        Path root,
        Path snippetsPath
) {
}
