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
        // language id 取 JSX 语言插件的 id（.jsx 与 .tsx 同属一个 frontend）：
        // prepared module 的 identityOf 由同一个插件 id 推出，两层必须一致（工单 12 AC1）。
        return new NekoJsxTokenStream(source, NekoJsxLanguagePlugin.INSTANCE.id(),
                compiled.code(), compiled.sourceMap());
    }
}
