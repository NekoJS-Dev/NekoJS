package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoSourceFile;
import com.tkisor.nekojs.api.compiler.NekoTokenStream;

public record NekoTypeScriptTokenStream(NekoSourceFile source, String erasedSource, String sourceMap) implements NekoTokenStream {
    public NekoTypeScriptTokenStream {
        erasedSource = erasedSource == null ? "" : erasedSource;
    }
}
