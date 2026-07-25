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
            ValNode s = parseStatement(root);
            if (s != null) stmts.add(s);
        }
        return root;
    }

    private ValNode parseStatement(ValNode.Block block) {
        skipWsCmt();
        if (pos >= n) return null;
        int start = pos;
        if (matchKw("const") || matchKw("let") || matchKw("var")) return parseVarDecl(start, block);
        if (matchKw("function") && peekAfterKw("function") == '(') return parseFuncDecl(start);
        ValNode e = parseExpr();
        skipSemi();
        return e;
    }

    // ---- declarations ----

    private ValNode.VarDecl parseVarDecl(int start, ValNode.Block block) {
        skipWs();
        String name = readIdent();
        if (name == null) { pos = start + 1; return null; }
        skipWs();
        ValNode init = null;
        if (peek() == '=') { pos++; skipWs(); init = parseExpr(); }
        skipSemi();
        ValNode.VarDecl decl = new ValNode.VarDecl(name, init, start, pos);
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
        if (params == null) { while (pos < n && peek() != ')' && peek() != '}') pos++; if (peek() == ')') pos++; params = List.of(); }
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
        if (c == '\'' || c == '"') { skipStr(); return new ValNode.Identifier("", pos, pos); }
        if (c == '`') { skipTpl(); return new ValNode.Identifier("", pos, pos); }
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
            if (c == '.') { pos++; skipWs(); int ms = pos; String m = readIdent(); if (m == null) break; obj = new ValNode.MemberAccess(obj, m, false, ms, pos); }
            else if (c == '?' && peek(1) == '.') { pos += 2; skipWs(); int ms = pos; String m = readIdent(); if (m == null) break; obj = new ValNode.MemberAccess(obj, m, false, ms, pos); }
            else if (c == '[') { pos++; skipWs(); char q = peek(); if (q == '\'' || q == '"') { pos++; int ms = pos; skipTo(q); String m = src.substring(ms, pos - 1); pos++; obj = new ValNode.MemberAccess(obj, m, true, ms, pos); } else skipBrackets(); }
            else if (c == '(') {
                pos++; skipWs();
                List<ValNode> args = new ArrayList<>();
                while (pos < n && peek() != ')') {
                    ValNode a = parseExpr(); if (a != null) args.add(a);
                    skipWs(); if (peek() == ',') pos++; skipWs();
                }
                if (peek() == ')') pos++;
                obj = new ValNode.CallExpr(obj, args, pos, pos);
            }
            else break;
        }
        return obj;
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
        while (pos < n) { skipWsCmt(); if (pos >= n || peek() == '}') { pos++; break; } ValNode s = parseStatement(block); if (s != null) stmts.add(s); }
        return new ValNode.Block(stmts, block.parent(), block.scope(), start, pos);
    }

    private List<String> tryArrowParams() {
        int saved = pos;
        if (peek() == '(') {
            pos++; skipWs();
            if (peek() == ')') { pos++; return List.of(); }
        }
        List<String> ps = parseParenParams();
        if (ps != null) return ps;
        pos = saved; return null;
    }

    private List<String> parseParenParams() {
        if (peek() != '(') return null;
        pos++; skipWs();
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
