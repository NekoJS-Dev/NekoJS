package com.tkisor.nekojs.core.compiler;


public enum NekoJsxLexer implements NekoLexer {
    INSTANCE;

    @Override
    public NekoTokenStream tokenize(NekoSourceFile source) {
        return tokenize(source, false);
    }

    public NekoTokenStream tokenize(NekoSourceFile source, boolean automaticRuntime) {
        boolean tsx = ".tsx".equals(source.extension());
        ScriptCompileResult compiled = tsx
            ? NekoJsxCompiler.compileTsx(source.path(), source.source(), automaticRuntime)
            : NekoJsxCompiler.compileJsx(source.path(), source.source(), automaticRuntime);
        return new NekoJsxTokenStream(source, tsx ? "tsx" : "jsx", compiled.code(), compiled.sourceMap());
    }
}
