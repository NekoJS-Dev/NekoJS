package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.core.module.esm.NekoEsmParser;

import java.util.Set;

public record NekoLegacyLanguagePlugin(String id, Set<String> extensions, IScriptCompiler compiler) implements NekoLanguagePlugin {
    public NekoLegacyLanguagePlugin {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Legacy language id must not be blank");
        }
        if (compiler == null) {
            throw new IllegalArgumentException("Legacy compiler must not be null");
        }
        extensions = extensions == null ? Set.of() : Set.copyOf(extensions);
    }

    @Override
    public NekoLexer lexer() {
        return NekoRawSourceLexer.INSTANCE;
    }

    @Override
    public NekoParser parser() {
        return this::parse;
    }

    @Override
    public NekoAstLowering lowering() {
        return NekoEsmToUnifiedIrLowering.INSTANCE;
    }

    private NekoSourceAst parse(NekoTokenStream tokens) throws Exception {
        ScriptCompileResult compiled = compiler.compileDetailed(tokens.source().path(), tokens.source().source());
        String code = compiled.code();
        return new NekoEsmSourceAst(tokens.source(), id, code, compiled.sourceMap(), new NekoEsmParser(tokens.source().path(), code).parse());
    }
}
