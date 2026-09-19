package com.tkisor.nekojs.core.compiler;

/**
 * Compile-time (erase/lower) diagnostic that keeps the authored line/column of the source it was
 * raised for. Language frontends already know the exact index; this type carries it across the
 * preparation seam so {@code NekoModuleError} can publish the authored position instead of only a
 * formatted message.
 *
 * <p>Extends {@link IllegalArgumentException} and preserves the exact message text of the previous
 * plain exceptions, so existing message-based assertions are unaffected.
 */
public final class NekoCompileException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final int line;
    private final int column;

    public NekoCompileException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    /** Authored 1-based line, or {@code -1} when unknown. */
    public int line() {
        return line;
    }

    /** Authored 1-based column, or {@code -1} when unknown. */
    public int column() {
        return column;
    }
}
