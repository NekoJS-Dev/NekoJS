package com.tkisor.nekojs.core.compiler.python;

/**
 * A single token produced by {@link PythonLexer}. Operators and punctuation are all
 * {@link Type#OP} carrying their literal {@link #text()}; keywords are {@link Type#NAME}
 * tokens whose text the parser recognizes.
 */
public record PythonToken(Type type, String text, int line, int col) {

    public enum Type {
        NAME,       // identifiers AND keywords (parser distinguishes by text)
        INT,        // 123
        FLOAT,      // 1.5, 1e3, .5
        STRING,     // "..." / '...' (value already unescaped)
        FSTRING,    // f"..." raw inner content (parser splits on {…})
        OP,         // operators / punctuation, text() is the operator
        NEWLINE,    // end of logical line
        INDENT,     // increase of block indentation
        DEDENT,     // decrease of block indentation
        EOF
    }

    public boolean is(Type t) { return type == t; }

    /** True if this is an OP/keyword whose text equals {@code op}. */
    public boolean isOp(String op) { return type == Type.OP && text.equals(op); }

    /** True if this is a NAME token with the given keyword text. */
    public boolean isKw(String kw) { return type == Type.NAME && text.equals(kw); }
}
