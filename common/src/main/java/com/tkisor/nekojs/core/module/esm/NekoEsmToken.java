package com.tkisor.nekojs.core.module.esm;

public record NekoEsmToken(
        NekoEsmTokenKind kind,
        String text,
        String value,
        NekoEsmSpan span
) {
    public boolean text(String expected) {
        return expected != null && expected.equals(text);
    }

    public boolean identifier(String expected) {
        return kind == NekoEsmTokenKind.IDENTIFIER && text(expected);
    }
}
