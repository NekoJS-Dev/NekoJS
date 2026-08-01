package com.tkisor.nekojs.core.compiler;

import java.util.*;

public final class ValParser {
    private final String src;
    private final int n;
    private int pos;

    private ValParser(String source) { this.src = source; this.n = source.length(); this.pos = 0; }

    public static ValNode.Block parse(String source) {
        return new ValParser(source).parseProgram();
    }

    // ---- entry ----

    private ValNode.Block parseProgram() {
        List<ValNode> stmts = new ArrayList<>();
        ValNode.Block root = new ValNode.Block(stmts, null, new LinkedHashMap<>(), 0, n);
        while (pos < n) {
            skipWsCmt();
            if (pos >= n) break;
            int before = pos;
            ValNode s = parseStatement(root);
            if (s != null) stmts.add(s);
            if (pos == before) {
                // 防死循环：parseExpr 失败会恢复 pos，若语句未被消费则强制前进，
                // 让未知语法（如 ';' 开头的表达式）被跳过而不是挂死 preflight。
                pos++;
            }
        }
        return root;
    }

    private ValNode parseStatement(ValNode.Block block) {
        skipWsCmt();
        if (pos >= n) return null;
        int start = pos;
        if (matchKw("const")) return parseVarDecl(ValNode.DeclarationKind.CONST, start, block);
        if (matchKw("let")) return parseVarDecl(ValNode.DeclarationKind.LET, start, block);
        if (matchKw("var")) return parseVarDecl(ValNode.DeclarationKind.VAR, start, block);
        if (matchKw("function") && peekAfterKw("function") == '(') return parseFuncDecl(start);
        ValNode e = parseExpr();
        skipSemi();
        return e;
    }

    // ---- declarations ----

    private ValNode.VarDecl parseVarDecl(ValNode.DeclarationKind kind, int start, ValNode.Block block) {
        skipWs();
        String name = readIdent();
        if (name == null) { pos = start + 1; return null; }
        skipWs();
        ValNode init = null;
        if (peek() == '=') { pos++; skipWs(); init = parseExpr(); }
        skipSemi();
        ValNode.VarDecl decl = new ValNode.VarDecl(kind, name, init, start, pos);
        if (block != null) block.scope().put(name, decl);
        return decl;
    }

    private ValNode parseFuncDecl(int start) {
        pos += 8; skipWs();
        String name = readIdent();
        skipWs();
        if (peek() != '(') return null;
        pos++;
        List<String> params = parseParenParams();
        if (params == null) {
            int depth = 1;
            while (pos < n && depth > 0) {
                char c = peek();
                if (c == '(' || c == '{' || c == '[') depth++;
                else if (c == ')' || c == '}' || c == ']') depth--;
                pos++;
            }
            params = List.of();
        }
        skipWs();
        ValNode.Block body = parseBlock();
        List<ValNode> stmts = body != null ? body.stmts() : List.of();
        return new ValNode.FuncDecl(name != null ? name : "", params, stmts, start, pos);
    }

    // ---- expressions ----

    private ValNode parseExpr() {
        int saved = pos;
        ValNode lhs = parseMemberChain();
        if (lhs instanceof ValNode.Identifier id) {
            if (match("=>")) { skipWs(); return parseArrowBody(List.of(id.name()), saved); }
            // 括号箭头: saved 指向 '('
        }
        // 检查括号参数箭头
        if (lhs == null) { pos = saved; return null; }
        return lhs;
    }

    private ValNode parseMemberChain() {
        ValNode obj = parsePrimary();
        if (obj == null) return null;
        return parseSuffix(obj);
    }

    private ValNode parsePrimary() {
        skipWsCmt();
        if (pos >= n) return null;
        char c = peek();
        if (isIdStart(c)) {
            int start = pos; String name = readIdent();
            if (name != null) return new ValNode.Identifier(name, start, pos);
        }
        if (c == '\'' || c == '"') return parseStringLiteral();
        if (c == '`') { int start = pos; skipTpl(); return new ValNode.Identifier("", start, pos); }
        if (Character.isDigit(c) || (c == '.' && Character.isDigit(peek(1)))) return parseNumberLiteral();
        if (c == '{') return parseBlock();
        if (c == '(') {
            pos++; skipWs();
            List<String> params = tryArrowParams();
            if (params != null && match("=>")) { skipWs(); return parseArrowBody(params, pos); }
            ValNode expr = parseExpr();
            skipWs(); if (peek() == ')') pos++;
            return expr;
        }
        pos++; return null;
    }

    private ValNode parseSuffix(ValNode obj) {
        while (pos < n) {
            skipWs();
            char c = peek();
            if (c == '.') {
                pos++; skipWs(); int ms = pos; String m = readIdent(); if (m == null) break;
                obj = new ValNode.MemberAccess(obj, m, false, ms, pos);
            } else if (c == '?' && peek(1) == '.') {
                pos += 2; skipWs();
                if (peek() == '[') {
                    obj = parseBracketAccess(obj, true);
                } else {
                    int ms = pos; String m = readIdent(); if (m == null) break;
                    obj = new ValNode.MemberAccess(obj, m, false, ms, pos);
                }
            } else if (c == '[') {
                obj = parseBracketAccess(obj, false);
            } else if (c == '(') {
                pos++; skipWs();
                List<ValNode> args = new ArrayList<>();
                while (pos < n && peek() != ')') {
                    int beforeArg = pos;
                    ValNode a = parseExpr(); if (a != null) args.add(a);
                    skipWs(); if (peek() == ',') pos++; skipWs();
                    if (pos == beforeArg) pos++; // 防死循环：未消费的实参（如 ';'）强制跳过
                }
                if (peek() == ')') pos++;
                obj = new ValNode.CallExpr(obj, args, pos, pos);
            } else break;
        }
        return obj;
    }

    private ValNode parseBracketAccess(ValNode obj, boolean optional) {
        int start = pos;
        if (peek() == '[') pos++;
        skipWs();
        if (peek() == '\'' || peek() == '"') {
            ValNode.StringLiteral literal = parseStringLiteral();
            skipWs();
            if (peek() == ']') pos++;
            return new ValNode.MemberAccess(obj, literal.value(), true, literal.start(), pos);
        }
        ValNode key = parseExpr();
        skipWs();
        if (peek() == ']') pos++;
        if (key == null) {
            key = new ValNode.Identifier("", start, pos);
        }
        return new ValNode.ComputedMemberAccess(obj, key, optional, start, pos);
    }

    private ValNode.ArrowFunc parseArrowBody(List<String> params, int start) {
        List<ValNode> body = new ArrayList<>();
        if (peek() == '{') { ValNode.Block b = parseBlock(); body.addAll(b.stmts()); }
        else { ValNode e = parseExpr(); if (e != null) body.add(e); }
        return new ValNode.ArrowFunc(params, body, start, pos);
    }

    private ValNode.Block parseBlock() {
        if (peek() != '{') return null;
        int start = pos; pos++;
        List<ValNode> stmts = new ArrayList<>();
        ValNode.Block block = new ValNode.Block(stmts, null, new LinkedHashMap<>(), start, 0);
        while (pos < n) {
            skipWsCmt(); if (pos >= n || peek() == '}') { pos++; break; }
            int before = pos;
            ValNode s = parseStatement(block); if (s != null) stmts.add(s);
            if (pos == before) pos++; // 防死循环：未消费的语句（如裸操作符）强制跳过
        }
        return new ValNode.Block(stmts, block.parent(), block.scope(), start, pos);
    }

    private List<String> tryArrowParams() {
        int saved = pos;
        if (peek() == '(') {
            pos++; skipWs();
            if (peek() == ')') { pos++; return List.of(); }
            List<String> ps = parseParenParams();
            if (ps != null) return ps;
        }
        pos = saved; return null;
    }

    private List<String> parseParenParams() {
        List<String> params = new ArrayList<>();
        while (pos < n) {
            if (isIdStart(peek())) { String n = readIdent(); if (n != null) params.add(n); }
            else if (peek() == ')' || peek() == '{' || peek() == '[') { return null; }
            skipWs();
            if (peek() == ',') { pos++; skipWs(); continue; }
            if (peek() == ')') { pos++; return params; }
            return null;
        }
        return null;
    }

    private ValNode.NumberLiteral parseNumberLiteral() {
        int start = pos;
        while (pos < n) {
            char c = peek();
            if (Character.isDigit(c) || c == '.' || c == 'x' || c == 'X'
                    || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                    || c == 'e' || c == 'E' || c == '+' || c == '-') {
                pos++;
            } else {
                break;
            }
        }
        return new ValNode.NumberLiteral(src.substring(start, pos), start, pos);
    }

    private ValNode.StringLiteral parseStringLiteral() {
        int start = pos;
        char quote = peek();
        pos++;
        StringBuilder value = new StringBuilder();
        while (pos < n) {
            char c = src.charAt(pos++);
            if (c == quote) {
                return new ValNode.StringLiteral(value.toString(), start, pos);
            }
            if (c != '\\' || pos >= n) {
                value.append(c);
                continue;
            }
            char escaped = src.charAt(pos++);
            switch (escaped) {
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'v' -> value.append('\u000B');
                case '0' -> value.append('\0');
                case '\n' -> { /* line continuation */ }
                case '\r' -> { if (peek() == '\n') pos++; }
                case 'x' -> value.append(readHexEscape(2, 'x'));
                case 'u' -> value.append(readHexEscape(4, 'u'));
                default -> value.append(escaped);
            }
        }
        return new ValNode.StringLiteral(value.toString(), start, pos);
    }

    private char readHexEscape(int digits, char prefix) {
        if (pos + digits > n) return prefix;
        int value = 0;
        for (int i = 0; i < digits; i++) {
            int digit = Character.digit(src.charAt(pos + i), 16);
            if (digit < 0) return prefix;
            value = value * 16 + digit;
        }
        pos += digits;
        return (char) value;
    }

    // ---- lexer utils ----

    private void skipWs() { while (pos < n && Character.isWhitespace(src.charAt(pos))) pos++; }
    private void skipWsCmt() {
        while (pos < n) {
            char c = src.charAt(pos);
            if (Character.isWhitespace(c)) { pos++; continue; }
            if (c == '/' && pos + 1 < n) { if (src.charAt(pos + 1) == '/') { skipLine(); continue; } if (src.charAt(pos + 1) == '*') { skipBlock(); continue; } }
            break;
        }
    }
    private void skipLine() { pos += 2; while (pos < n && src.charAt(pos) != '\n') pos++; }
    private void skipBlock() { pos += 2; while (pos + 1 < n && !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) pos++; if (pos + 1 < n) pos += 2; }
    private void skipSemi() { skipWs(); if (pos < n && src.charAt(pos) == ';') pos++; }
    private void skipStr() { char q = src.charAt(pos); pos++; while (pos < n) { if (src.charAt(pos) == '\\') { pos += 2; continue; } if (src.charAt(pos) == q) { pos++; return; } pos++; } }
    private void skipTpl() { pos++; while (pos < n) { if (src.charAt(pos) == '\\') { pos += 2; continue; } if (src.charAt(pos) == '`') { pos++; return; } pos++; } }
    private void skipBrackets() { int d = 1; pos++; while (pos < n && d > 0) { char c = src.charAt(pos); if (c == '[') d++; else if (c == ']') d--; pos++; } }
    private void skipTo(char t) { while (pos < n) { if (src.charAt(pos) == '\\') { pos += 2; continue; } if (src.charAt(pos) == t) { pos++; return; } pos++; } }
    private char peek() { return pos < n ? src.charAt(pos) : '\0'; }
    private char peek(int a) { return pos + a < n ? src.charAt(pos + a) : '\0'; }
    private boolean match(String kw) {
        int end = pos + kw.length(); if (end > n) return false;
        for (int i = 0; i < kw.length(); i++) if (src.charAt(pos + i) != kw.charAt(i)) return false;
        if (end < n && isIdPart(src.charAt(end))) return false;
        pos = end; return true;
    }
    private boolean matchKw(String kw) { int saved = pos; boolean ok = match(kw); if (!ok) pos = saved; return ok; }
    private char peekAfterKw(String kw) { int i = pos + kw.length(); while (i < n && Character.isWhitespace(src.charAt(i))) i++; return i < n ? src.charAt(i) : '\0'; }
    private String readIdent() { int s = pos; while (pos < n && isIdPart(src.charAt(pos))) pos++; return pos > s ? src.substring(s, pos) : null; }
    private static boolean isIdStart(char c) { return c == '_' || c == '$' || Character.isLetter(c); }
    private static boolean isIdPart(char c) { return c == '_' || c == '$' || Character.isLetterOrDigit(c); }
}
