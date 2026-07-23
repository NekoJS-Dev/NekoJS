package com.tkisor.nekojs.core.compiler;


public enum NekoJsxLexer implements NekoLexer {
    INSTANCE;

    @Override
    public NekoTokenStream tokenize(NekoSourceFile source) {
        boolean tsx = ".tsx".equals(source.extension());
        ScriptCompileResult compiled = tsx ? NekoJsxCompiler.compileTsx(source.path(), source.source()) : NekoJsxCompiler.compileJsx(source.path(), source.source());
        return new NekoJsxTokenStream(source, tsx ? "tsx" : "jsx", compiled.code(), compiled.sourceMap());
    }
}
