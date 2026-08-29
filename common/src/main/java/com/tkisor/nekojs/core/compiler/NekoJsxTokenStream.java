package com.tkisor.nekojs.core.compiler;


public record NekoJsxTokenStream(
        NekoSourceFile source,
        String languageId,
        String transformedSource,
        String sourceMap
) implements NekoTokenStream {
    public NekoJsxTokenStream {
        languageId = languageId == null || languageId.isBlank() ? "jsx" : languageId;
        transformedSource = transformedSource == null ? "" : transformedSource;
    }
}
