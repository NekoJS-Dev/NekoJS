package com.tkisor.nekojs.core.compiler;


public record NekoTypeScriptTokenStream(NekoSourceFile source, String erasedSource, String sourceMap) implements NekoTokenStream {
    public NekoTypeScriptTokenStream {
        erasedSource = erasedSource == null ? "" : erasedSource;
    }
}
