package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoLexer;
import com.tkisor.nekojs.api.compiler.NekoSourceFile;
import com.tkisor.nekojs.api.compiler.NekoTokenStream;

public enum NekoTypeScriptLexer implements NekoLexer {
    INSTANCE;

    @Override
    public NekoTokenStream tokenize(NekoSourceFile source) {
        return new NekoTypeScriptTokenStream(source, NekoTypeScriptCompiler.erase(source.path(), source.source()), null);
    }
}
