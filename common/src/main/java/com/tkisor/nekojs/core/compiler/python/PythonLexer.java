package com.tkisor.nekojs.core.compiler.python;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Python tokenizer producing NEWLINE / INDENT / DEDENT tokens (CPython-style off-side rule).
 *
 * <p>v1 scope: spaces-only indentation (a tab is expanded to the next multiple of 8, but mixing
 * tab/space to the same level may not match CPython exactly); single- and triple-quoted strings
 * with {@code f}/{@code r} prefixes; line continuation via {@code () [] {}} implicit joining.
 * Comments ({@code #}) to end of line; blank/comment-only lines emit no tokens.
 */
public final class PythonLexer {

    private final String src;
    private final int n;
    private int pos;            // index into src
    private int line = 1;
    private int col = 1;        // 1-based column

    private final Deque<Integer> indentStack = new ArrayDeque<>();
    private final List<PythonToken> tokens = new ArrayList<>();
    private int parenDepth = 0;
    private boolean atLineStart = true;

    public PythonLexer(String source) {
        this.src = source == null ? "" : source;
        this.n = this.src.length();
        this.indentStack.push(0);
    }

    public List<PythonToken> tokenize() {
        while (pos < n) {
            if (atLineStart && parenDepth == 0) {
                if (handleLineStart()) continue;
            }
            char c = peek();
            if (c == '\n' || c == '\r') {
                consumeNewline();
                continue;
            }
            if (c == ' ' || c == '\t' || c == '\f') { advance(); continue; }
            if (c == '\\') {
                // explicit line continuation: backslash-newline joins lines, emits no NEWLINE,
                // and must NOT set atLineStart (we are mid-expression).
                if (pos + 1 < n && (src.charAt(pos + 1) == '\n' || src.charAt(pos + 1) == '\r')) {
                    advance(); // backslash
                    advanceOverNewline();
                    continue;
                }
                throw error("unexpected '\\'");
            }
            if (c == '#') { while (pos < n && peek() != '\n') advance(); continue; }
            if (isDigit(c) || (c == '.' && pos + 1 < n && isDigit(src.charAt(pos + 1)))) { lexNumber(); continue; }
            if (c == '"' || c == '\'') { lexString(false); continue; }
            if (isIdStart(c)) { lexNameOrStringPrefix(); continue; }
            lexOperator();
        }
        // end of input: close the last logical line if open
        if (!atLineStart) {
            tokens.add(new PythonToken(PythonToken.Type.NEWLINE, "\\n", line, col));
            atLineStart = true;
        }
        while (indentStack.size() > 1) {
            indentStack.pop();
            tokens.add(new PythonToken(PythonToken.Type.DEDENT, "", line, col));
        }
        tokens.add(new PythonToken(PythonToken.Type.EOF, "", line, col));
        return tokens;
    }

    // ---- line-start indentation handling; returns true if the line was blank/comment-only ----
    private boolean handleLineStart() {
        int startLine = line;
        int startCol = col;
        int indent = 0;
        // count leading whitespace (space=1, tab → next multiple of 8)
        while (pos < n) {
            char c = peek();
            if (c == ' ') { indent++; advance(); }
            else if (c == '\t') { indent += 8 - (indent % 8); advance(); }
            else if (c == '\f') { indent = 0; advance(); }
            else break;
        }
        // blank line or comment-only line → skip, emit nothing, stay atLineStart
        if (pos >= n || peek() == '\n' || peek() == '\r' || peek() == '#') {
            while (pos < n && peek() != '\n' && peek() != '\r') advance();
            if (pos < n) advanceOverNewline();
            return true;
        }
        // emit INDENT / DEDENT(s)
        int top = indentStack.peek();
        if (indent > top) {
            indentStack.push(indent);
            tokens.add(new PythonToken(PythonToken.Type.INDENT, "", startLine, startCol));
        } else if (indent < top) {
            while (indentStack.size() > 1 && indentStack.peek() > indent) {
                indentStack.pop();
                tokens.add(new PythonToken(PythonToken.Type.DEDENT, "", startLine, startCol));
            }
            if (indentStack.peek() != indent) {
                throw errorAt(startLine, startCol, "inconsistent indentation (dedent to unknown level)");
            }
        }
        atLineStart = false;
        return false;
    }

    private void consumeNewline() {
        if (peek() == '\r') { advance(); if (pos < n && peek() == '\n') advance(); }
        else { advance(); }
        if (parenDepth == 0) {
            tokens.add(new PythonToken(PythonToken.Type.NEWLINE, "\\n", line, col));
            atLineStart = true;
        }
        // inside brackets: implicit line joining, no NEWLINE, stay !atLineStart
    }

    /** Advances past a physical newline (\r\n or \n) without emitting any token and without
     *  touching atLineStart — used for blank/comment-only lines and backslash continuation. */
    private void advanceOverNewline() {
        if (peek() == '\r') { advance(); if (pos < n && peek() == '\n') advance(); }
        else { advance(); }
    }

    private void lexNumber() {
        int startLine = line, startCol = col;
        int begin = pos;
        boolean isFloat = false;
        if (peek() == '0' && pos + 1 < n && (peek(1) == 'x' || peek(1) == 'X')) {
            // hex literal 0x...
            advance(); advance();
            while (pos < n && (isHexDigit(peek()) || peek() == '_')) advance();
            String text = slice(begin);
            add(PythonToken.Type.INT, Long.toString(parseLong(text.replace("_", ""), 16)), startLine, startCol);
            return;
        }
        while (pos < n && (isDigit(peek()) || peek() == '_')) advance();
        if (pos < n && peek() == '.') {
            isFloat = true; advance();
            while (pos < n && (isDigit(peek()) || peek() == '_')) advance();
        }
        if (pos < n && (peek() == 'e' || peek() == 'E')) {
            isFloat = true; advance();
            if (pos < n && (peek() == '+' || peek() == '-')) advance();
            while (pos < n && (isDigit(peek()) || peek() == '_')) advance();
        }
        String text = slice(begin).replace("_", "");
        if (isFloat) {
            add(PythonToken.Type.FLOAT, text, startLine, startCol);
        } else {
            add(PythonToken.Type.INT, text, startLine, startCol);
        }
    }

    private void lexNameOrStringPrefix() {
        int startLine = line, startCol = col;
        int begin = pos;
        while (pos < n && isIdPart(peek())) advance();
        String name = slice(begin);
        // string prefix: f/r/b (case-insensitive), single char, immediately followed by quote
        if (name.length() == 1 && pos < n && (peek() == '"' || peek() == '\'')) {
            char prefix = Character.toLowerCase(name.charAt(0));
            if (prefix == 'f' || prefix == 'r') {
                lexString(prefix == 'r');
                // rewrite the last token to carry prefix? lexString emits STRING; convert to FSTRING if f
                if (prefix == 'f') {
                    PythonToken t = tokens.get(tokens.size() - 1);
                    tokens.set(tokens.size() - 1, new PythonToken(PythonToken.Type.FSTRING, t.text(), t.line(), t.col()));
                }
                return;
            }
        }
        add(PythonToken.Type.NAME, name, startLine, startCol);
    }

    /** Reads a string literal starting at the current quote; the caller has already consumed any prefix. */
    private void lexString(boolean raw) {
        int startLine = line, startCol = col;
        char quote = peek();
        boolean triple = pos + 2 < n && src.charAt(pos + 1) == quote && src.charAt(pos + 2) == quote;
        int quoteLen = triple ? 3 : 1;
        for (int i = 0; i < quoteLen; i++) advance();
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= n) throw errorAt(startLine, startCol, "unterminated string");
            char c = peek();
            if (triple) {
                if (c == quote && pos + 2 < n && src.charAt(pos + 1) == quote && src.charAt(pos + 2) == quote) {
                    advance(); advance(); advance();
                    break;
                }
            } else {
                if (c == quote) { advance(); break; }
                if (c == '\n' || c == '\r') throw errorAt(line, col, "unterminated string (single-line strings must close on the same line)");
            }
            if (c == '\\' && !raw) {
                advance();
                if (pos >= n) throw errorAt(line, col, "unterminated escape");
                char e = peek();
                sb.append(unescape(e));
                advance();
                continue;
            }
            sb.append(c);
            advance();
        }
        add(PythonToken.Type.STRING, sb.toString(), startLine, startCol);
    }

    private char unescape(char e) {
        return switch (e) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case '\\' -> '\\';
            case '\'' -> '\'';
            case '"' -> '"';
            case '0' -> '\0';
            case 'a' -> '\u0007';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'v' -> '\u000B';
            default -> e;   // unknown escape: keep char literally
        };
    }

    private void lexOperator() {
        int startLine = line, startCol = col;
        String three = pos + 2 < n + 1 ? safeSlice(3) : "";
        String two = pos + 1 < n + 1 ? safeSlice(2) : "";
        if (OP3.contains(three)) { advanceN(3); add(PythonToken.Type.OP, three, startLine, startCol); return; }
        if (OP2.contains(two)) { advanceN(2); add(PythonToken.Type.OP, two, startLine, startCol); return; }
        char c = peek();
        if (SINGLE_OPS.indexOf(c) >= 0) {
            advance();
            add(PythonToken.Type.OP, String.valueOf(c), startLine, startCol);
            if (c == '(' || c == '[' || c == '{') parenDepth++;
            else if (c == ')' || c == ']' || c == '}') { if (parenDepth > 0) parenDepth--; }
            return;
        }
        throw errorAt(line, col, "unexpected character '" + c + "'");
    }

    // ---- primitives ----
    private char peek() { return src.charAt(pos); }
    private char peek(int off) { return src.charAt(pos + off); }
    private void advance() {
        char c = src.charAt(pos++);
        if (c == '\n') { line++; col = 1; } else { col++; }
    }
    private void advanceN(int k) { for (int i = 0; i < k; i++) advance(); }
    private String slice(int begin) { return src.substring(begin, pos); }
    private String safeSlice(int len) { return pos + len <= n ? src.substring(pos, pos + len) : ""; }
    private void add(PythonToken.Type t, String text, int l, int c) {
        tokens.add(new PythonToken(t, text, l, c));
    }
    private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }
    private static boolean isHexDigit(char c) { return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }
    private static boolean isIdStart(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$'; }
    private static boolean isIdPart(char c) { return isIdStart(c) || isDigit(c); }
    private static long parseLong(String text, int radix) {
        return Long.parseLong(text.startsWith("0x") || text.startsWith("0X") ? text.substring(2) : text, radix);
    }

    private IllegalArgumentException error(String msg) { return errorAt(line, col, msg); }
    private static IllegalArgumentException errorAt(int l, int c, String msg) {
        return new IllegalArgumentException("python lex error at line " + l + ", col " + c + ": " + msg);
    }

    // operator tables
    private static final java.util.Set<String> OP3 = java.util.Set.of("**=", "//=", ">>=", "<<=");
    private static final java.util.Set<String> OP2 = java.util.Set.of(
            "**", "//", "<<", ">>", "<=", ">=", "==", "!=", "+=", "-=", "*=", "/=", "%=", "@=",
            "->", "&=", "|=", "^=", ":=");
    private static final String SINGLE_OPS = "+-*/%@=<>!&|^~()[]{},:.;";
}
