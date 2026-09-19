package com.tkisor.nekojs.core.compiler;


import java.util.Set;

/**
 * TSX language identity: TypeScript erasure plus JSX lowering (`.tsx`).
 *
 * <p>Kept separate from {@link NekoJsxLanguagePlugin} because preparation identity, cache keys and
 * diagnostics must attribute a `.tsx` module to its own language instead of collapsing it into
 * `jsx`. Both plugins share {@link NekoJsxLexer} (which erases TypeScript before lowering JSX when
 * the source extension is `.tsx`), so there is still exactly one TSX transformation pipeline.
 */
public enum NekoTsxLanguagePlugin implements NekoLanguagePlugin {
    INSTANCE;

    @Override
    public String id() {
        return "tsx";
    }

    @Override
    public Set<String> extensions() {
        return Set.of(".tsx");
    }

    @Override
    public NekoLexer lexer() {
        return NekoJsxLexer.INSTANCE;
    }

    @Override
    public NekoParser parser() {
        return NekoJsxParser.INSTANCE;
    }

    @Override
    public NekoAstLowering lowering() {
        return NekoEsmToUnifiedIrLowering.INSTANCE;
    }
}
