package com.tkisor.nekojs.core.compiler.python;

import com.tkisor.nekojs.core.compiler.python.ast.PythonNode;
import com.tkisor.nekojs.core.compiler.python.ast.PythonNode.Param;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recursive-descent parser turning {@link PythonLexer} tokens into a {@link PythonNode} AST.
 * Covers the v1 Python subset (see {@code ai_arch/} design). Expression precedence is layered
 * (14 levels) following Python's grammar: {@code test → or → and → not → comparison → | → ^ →
 * & → shift → + - → star / div // % @ → unary → ** → postfix → atom}.
 */
public final class PythonParser {

    private final List<PythonToken> tokens;
    private int pos = 0;

    /** Per-function generator flag (top of stack = innermost function). Set when a yield is parsed. */
    private final Deque<boolean[]> fnGen = new ArrayDeque<>();

    /** Node identity → 1-based Python source line, for statement-granularity source maps. */
    private final java.util.IdentityHashMap<PythonNode, Integer> srcLines = new java.util.IdentityHashMap<>();

    public PythonParser(List<PythonToken> tokens) {
        this.tokens = tokens;
    }

    /** Statement nodes → their Python source line (1-based). Populated during {@link #parseModule()}. */
    public java.util.IdentityHashMap<PythonNode, Integer> srcLines() {
        return srcLines;
    }

    public PythonNode parseModule() {
        List<PythonNode> body = parseStatements();
        expect(PythonToken.Type.EOF, "EOF");
        return new PythonNode.Module(body);
    }

    // ---- statements ----

    private List<PythonNode> parseStatements() {
        List<PythonNode> out = new ArrayList<>();
        skipNewlines();
        while (!at(PythonToken.Type.EOF) && !at(PythonToken.Type.DEDENT)) {
            int stmtLine = peek().line();
            if (atOp("@")) {
                List<String> decorators = parseDecorators();
                if (!peek().isKw("def") && !peek().isKw("class")) {
                    throw error("decorator must precede 'def' or 'class' but found '" + peek().text() + "'");
                }
                PythonNode n = parseCompound(decorators);
                srcLines.put(n, stmtLine);
                out.add(n);
            } else if (isCompoundKw()) {
                PythonNode n = parseCompound(List.of());
                srcLines.put(n, stmtLine);
                out.add(n);
            } else {
                List<PythonNode> ss = parseSimpleList();
                for (PythonNode s : ss) srcLines.put(s, stmtLine);
                out.addAll(ss);
            }
            skipNewlines();
        }
        return out;
    }

    /** Parses one or more {@code @dotted.name} decorator lines, each terminated by NEWLINE. */
    private List<String> parseDecorators() {
        List<String> ds = new ArrayList<>();
        while (atOp("@")) {
            advance();
            StringBuilder name = new StringBuilder(expectName());
            while (matchOp(".")) name.append('.').append(expectName());
            if (atOp("(")) {
                throw error("decorator arguments are not supported in v1 (use a bare @name or @module.name)");
            }
            expect(PythonToken.Type.NEWLINE, "NEWLINE after decorator");
            skipNewlines();
            ds.add(name.toString());
        }
        return ds;
    }

    private PythonNode parseCompound(List<String> decorators) {
        String kw = peek().text();
        return switch (kw) {
            case "def" -> parseFunctionDef(decorators);
            case "class" -> parseClassDef(decorators);
            case "if" -> parseIf();
            case "for" -> parseFor();
            case "while" -> parseWhile();
            case "try" -> parseTry();
            case "with" -> parseWith();
            case "match" -> parseMatch();
            default -> throw error("unexpected compound keyword '" + kw + "'");
        };
    }

    private PythonNode parseFunctionDef(List<String> decorators) {
        expectKw("def");
        String name = expectName();
        expectOp("(");
        List<Param> params = parseParams(")");
        expectOp(")");
        if (matchOp("->")) parseTest();   // return-type annotation: parsed and discarded
        expectOp(":");
        fnGen.push(new boolean[]{false});
        List<PythonNode> body = parseSuite();
        boolean isGenerator = fnGen.pop()[0];
        return new PythonNode.FunctionDef(name, params, body, decorators, isGenerator);
    }

    private PythonNode parseClassDef(List<String> decorators) {
        expectKw("class");
        String name = expectName();
        PythonNode base = null;
        if (matchOp("(")) {
            if (!atOp(")")) {
                base = parseTest();
                // skip additional bases / keyword args (v1: single base)
                while (matchOp(",")) {
                    if (atOp(")")) break;
                    parseTest();
                }
            }
            expectOp(")");
        }
        expectOp(":");
        List<PythonNode> body = parseSuite();
        return new PythonNode.ClassDef(name, base, body, decorators);
    }

    private PythonNode parseIf() {
        expectKw("if");
        PythonNode cond = parseTest();
        expectOp(":");
        List<PythonNode> thenBody = parseSuite();
        List<PythonNode> elseBody = new ArrayList<>();
        if (peek().isKw("elif")) {
            // elif is sugar for else: if … — rewrite the token in place and recurse
            PythonToken elif = peek();
            tokens.set(pos, new PythonToken(PythonToken.Type.NAME, "if", elif.line(), elif.col()));
            elseBody.add(parseIf());
        } else if (matchKw("else")) {
            expectOp(":");
            elseBody.addAll(parseSuite());
        }
        return new PythonNode.If(cond, thenBody, elseBody);
    }

    private PythonNode parseFor() {
        expectKw("for");
        PythonNode target = parseTargetList();
        expectKw("in");
        PythonNode iter = parseTestList();
        expectOp(":");
        List<PythonNode> body = parseSuite();
        List<PythonNode> elseBody = List.of();
        if (matchKw("else")) { expectOp(":"); elseBody = parseSuite(); }
        return new PythonNode.For(target, iter, body, elseBody);
    }

    private PythonNode parseWhile() {
        expectKw("while");
        PythonNode cond = parseTest();
        expectOp(":");
        List<PythonNode> body = parseSuite();
        List<PythonNode> elseBody = List.of();
        if (matchKw("else")) { expectOp(":"); elseBody = parseSuite(); }
        return new PythonNode.While(cond, body, elseBody);
    }

    private PythonNode parseWith() {
        expectKw("with");
        List<PythonNode.WithItem> items = new ArrayList<>();
        while (true) {
            PythonNode ctx = parseTest();
            // 'as' binds a single target (a name or a parenthesised tuple); NOT a comma-list,
            // so the comma separating with-items is left for the loop below to consume.
            PythonNode target = matchKw("as") ? parseUnary() : null;
            items.add(new PythonNode.WithItem(ctx, target));
            if (!matchOp(",")) break;
            if (atOp(":")) break;   // trailing comma before ':'
        }
        expectOp(":");
        List<PythonNode> body = parseSuite();
        return new PythonNode.With(items, body);
    }

    private PythonNode parseMatch() {
        expectKw("match");
        PythonNode subject = parseTestList();
        expectOp(":");
        expect(PythonToken.Type.NEWLINE, "NEWLINE after 'match subject:'");
        expect(PythonToken.Type.INDENT, "INDENT to open a match body");
        List<PythonNode.MatchCase> cases = new ArrayList<>();
        skipNewlines();
        while (matchKw("case")) {
            PythonNode.Pattern pattern = parsePattern();
            PythonNode guard = matchKw("if") ? parseOr() : null;
            expectOp(":");
            List<PythonNode> body = parseSuite();
            cases.add(new PythonNode.MatchCase(pattern, guard, body));
            skipNewlines();
        }
        expect(PythonToken.Type.DEDENT, "DEDENT to close 'match'");
        return new PythonNode.Match(subject, cases);
    }

    /** pattern ::= closed ('|' closed)* */
    private PythonNode.Pattern parsePattern() {
        PythonNode.Pattern first = parsePatternAtom();
        if (atOp("|")) {
            List<PythonNode.Pattern> alts = new ArrayList<>(List.of(first));
            while (matchOp("|")) alts.add(parsePatternAtom());
            return new PythonNode.OrPat(alts);
        }
        return first;
    }

    private PythonNode.Pattern parsePatternAtom() {
        if (atOp("[")) return parseSequencePattern();
        if (atOp("{")) return parseMappingPattern();
        PythonToken t = peek();
        if (t.type() == PythonToken.Type.INT || t.type() == PythonToken.Type.FLOAT
                || t.type() == PythonToken.Type.STRING) {
            advance();
            return new PythonNode.LiteralPat(literalTokenToNode(t));
        }
        if (t.isKw("True") || t.isKw("False") || t.isKw("None")) {
            advance();
            return new PythonNode.LiteralPat("True".equals(t.text()) ? new PythonNode.BoolLit(true)
                    : "False".equals(t.text()) ? new PythonNode.BoolLit(false) : new PythonNode.NoneLit());
        }
        if (atOp("-") || atOp("+")) {
            String op = advance().text();
            PythonToken num = peek();
            if (num.type() != PythonToken.Type.INT && num.type() != PythonToken.Type.FLOAT) {
                throw error("expected a number after '" + op + "' in a pattern");
            }
            advance();
            return new PythonNode.LiteralPat(new PythonNode.Unary(op, literalTokenToNode(num)));
        }
        if (t.type() == PythonToken.Type.NAME) {
            StringBuilder name = new StringBuilder(expectName());
            while (matchOp(".")) name.append('.').append(expectName());
            if (atOp("(")) return parseClassPatternRest(name.toString());
            return new PythonNode.CapturePat(name.toString());   // bare name → capture (incl. "_" wildcard)
        }
        throw error("invalid pattern, found '" + t.text() + "'");
    }

    private PythonNode.Pattern parseSequencePattern() {
        expectOp("[");
        List<PythonNode.Pattern> elements = new ArrayList<>();
        String starName = null;
        int starIndex = -1;
        if (!atOp("]")) {
            while (true) {
                if (matchOp("*")) { starName = expectName(); starIndex = elements.size(); }
                else elements.add(parsePattern());
                if (!matchOp(",")) break;
                if (atOp("]")) break;
            }
        }
        expectOp("]");
        return new PythonNode.SequencePat(elements, starName, starIndex);
    }

    private PythonNode.Pattern parseMappingPattern() {
        expectOp("{");
        List<PythonNode> keys = new ArrayList<>();
        List<String> valueNames = new ArrayList<>();
        String restName = null;
        if (!atOp("}")) {
            while (true) {
                if (matchOp("**")) { restName = expectName(); }
                else {
                    PythonToken kt = peek();
                    if (kt.type() != PythonToken.Type.STRING && kt.type() != PythonToken.Type.INT
                            && kt.type() != PythonToken.Type.FLOAT) {
                        throw error("mapping-pattern keys must be literal strings or numbers");
                    }
                    advance();
                    keys.add(literalTokenToNode(kt));
                    expectOp(":");
                    valueNames.add(expectName());   // value is a capture name (incl. '_')
                }
                if (!matchOp(",")) break;
                if (atOp("}")) break;
            }
        }
        expectOp("}");
        return new PythonNode.MappingPat(keys, valueNames, restName);
    }

    private PythonNode.Pattern parseClassPatternRest(String className) {
        expectOp("(");
        Map<String, PythonNode.Pattern> keyword = new LinkedHashMap<>();
        if (!atOp(")")) {
            while (true) {
                if (peek().type() == PythonToken.Type.NAME && pos + 1 < tokens.size()
                        && tokens.get(pos + 1).isOp("=")) {
                    String k = expectName();
                    advance(); // '='
                    keyword.put(k, parsePattern());
                } else {
                    throw error("positional class patterns are not supported (use Cls(attr=pattern))");
                }
                if (!matchOp(",")) break;
                if (atOp(")")) break;
            }
        }
        expectOp(")");
        return new PythonNode.ClassPat(className, keyword);
    }

    private PythonNode literalTokenToNode(PythonToken t) {
        return switch (t.type()) {
            case INT -> new PythonNode.IntLit(Long.parseLong(t.text()));
            case FLOAT -> new PythonNode.FloatLit(Double.parseDouble(t.text()));
            case STRING -> new PythonNode.StrLit(t.text());
            default -> throw error("not a literal token: " + t.text());
        };
    }

    private PythonNode parseTry() {
        expectKw("try");
        expectOp(":");
        List<PythonNode> body = parseSuite();
        List<PythonNode.ExceptClause> excepts = new ArrayList<>();
        while (matchKw("except")) {
            List<PythonNode> types = List.of();
            String name = null;
            if (!atOp(":")) {
                if (matchOp("(")) {           // except (A, B) as e — parenthesized type tuple
                    List<PythonNode> ts = new ArrayList<>();
                    ts.add(parseTest());
                    while (matchOp(",")) {
                        if (atOp(")")) break;
                        ts.add(parseTest());
                    }
                    expectOp(")");
                    types = ts;
                } else {
                    types = List.of(parseTest());
                }
                if (matchKw("as")) name = expectName();
            }
            expectOp(":");
            excepts.add(new PythonNode.ExceptClause(types, name, parseSuite()));
            if (types.isEmpty() && peek().isKw("except")) {
                throw error("bare 'except' must be the last except clause");
            }
        }
        List<PythonNode> elseBody = List.of();
        if (matchKw("else")) { expectOp(":"); elseBody = parseSuite(); }
        List<PythonNode> finallyBody = List.of();
        if (matchKw("finally")) { expectOp(":"); finallyBody = parseSuite(); }
        return new PythonNode.Try(body, excepts, elseBody, finallyBody);
    }

    /** A suite is either an INDENT…DEDENT block (header ended by NEWLINE) or an inline simple statement. */
    private List<PythonNode> parseSuite() {
        if (match(PythonToken.Type.NEWLINE)) {
            expect(PythonToken.Type.INDENT, "INDENT");
            List<PythonNode> body = parseStatements();
            expect(PythonToken.Type.DEDENT, "DEDENT");
            return body;
        }
        return parseSimpleList();
    }

    /** One or more simple statements separated by {@code ;}, terminated by NEWLINE. */
    private List<PythonNode> parseSimpleList() {
        List<PythonNode> out = new ArrayList<>();
        out.add(parseSimple());
        while (matchOp(";")) {
            if (at(PythonToken.Type.NEWLINE) || at(PythonToken.Type.EOF)) break;
            out.add(parseSimple());
        }
        if (!at(PythonToken.Type.EOF)) expect(PythonToken.Type.NEWLINE, "NEWLINE");
        return out;
    }

    private PythonNode parseSimple() {
        if (peek().isKw("import")) { advance(); return parseImport(); }
        if (peek().isKw("from")) { advance(); return parseImportFrom(); }
        String kw = peek().text();
        if (peek().isKw("return")) {
            advance();
            if (atNewlineOrEnd() || atOp(";")) return new PythonNode.Return(null);
            return new PythonNode.Return(parseTestList());
        }
        if (peek().isKw("break")) { advance(); return new PythonNode.Break(); }
        if (peek().isKw("continue")) { advance(); return new PythonNode.Continue(); }
        if (peek().isKw("pass")) { advance(); return new PythonNode.Pass(); }
        if (peek().isKw("raise")) {
            advance();
            if (atNewlineOrEnd() || atOp(";")) return new PythonNode.Raise(null, null);   // bare raise
            PythonNode exc = parseTestList();
            PythonNode from = matchKw("from") ? parseTestList() : null;   // 'from' clause parsed but ignored at emit
            return new PythonNode.Raise(exc, from);
        }
        if (peek().isKw("yield")) {
            advance();
            markGenerator();
            if (atNewlineOrEnd() || atOp(";")) return new PythonNode.Yield(null, false);   // bare yield
            if (matchKw("from")) return new PythonNode.Yield(parseTestList(), true);       // yield from iter
            return new PythonNode.Yield(parseTestList(), false);
        }
        if (peek().isKw("assert")) {
            advance();
            PythonNode cond = parseTest();   // single test (not testlist) so the ',' separates the message
            PythonNode msg = matchOp(",") ? parseTest() : null;
            return new PythonNode.Assert(cond, msg);
        }
        if (peek().isKw("del")) {
            advance();
            List<PythonNode> targets = new ArrayList<>();
            targets.add(parseUnary());
            while (matchOp(",")) {
                if (atNewlineOrEnd() || atOp(";")) break;
                targets.add(parseUnary());
            }
            return new PythonNode.Del(targets);
        }
        // variable annotation: NAME ':' type ['=' value]  (a bare 'name:' can only be an annotation at statement level)
        if (peek().type() == PythonToken.Type.NAME && pos + 1 < tokens.size() && tokens.get(pos + 1).isOp(":")) {
            String name = advance().text();
            advance(); // ':'
            parseTest(); // annotation type — parsed and discarded
            if (matchOp("=")) return new PythonNode.Assign(List.of(new PythonNode.Name(name)), parseTestList());
            return new PythonNode.Pass();   // annotation without assignment → no binding, no-op
        }
        // assignment or expression statement
        PythonNode first = parseTestList();
        if (atAugOp()) {
            String op = advance().text();
            PythonNode value = parseTestList();
            return new PythonNode.AugAssign(first, op, value);
        }
        if (matchOp("=")) {
            List<PythonNode> targets = new ArrayList<>();
            targets.add(first);
            PythonNode value = parseTestList();
            while (matchOp("=")) {
                targets.add(value);
                value = parseTestList();
            }
            return new PythonNode.Assign(targets, value);
        }
        return new PythonNode.ExprStmt(first);
    }

    // ---- expressions ----

    /** test ::= lambda | or_test ['if' or_test 'else' test] */
    PythonNode parseTest() {
        if (peek().isKw("lambda")) return parseLambda();
        PythonNode e = parseOr();
        if (matchKw("if")) {
            PythonNode cond = parseOr();
            expectKw("else");
            PythonNode elseE = parseTest();
            return new PythonNode.Ternary(cond, e, elseE);
        }
        return e;
    }

    private PythonNode parseLambda() {
        expectKw("lambda");
        List<Param> params = parseParams(":");
        expectOp(":");
        PythonNode body = parseTest();
        return new PythonNode.Lambda(params, body);
    }

    private PythonNode parseOr() {
        PythonNode e = parseAnd();
        while (matchKw("or")) e = new PythonNode.Binary("or", e, parseAnd());
        return e;
    }

    private PythonNode parseAnd() {
        PythonNode e = parseNot();
        while (matchKw("and")) e = new PythonNode.Binary("and", e, parseNot());
        return e;
    }

    private PythonNode parseNot() {
        if (matchKw("not")) return new PythonNode.Unary("not", parseNot());
        return parseComparison();
    }

    private PythonNode parseComparison() {
        PythonNode e = parseBitor();
        while (true) {
            String op = comparisonOp();
            if (op == null) break;
            e = new PythonNode.Compare(e, op, parseBitor());
        }
        return e;
    }

    private PythonNode parseBitor() {
        PythonNode e = parseBitxor();
        while (matchOp("|")) e = new PythonNode.Binary("|", e, parseBitxor());
        return e;
    }

    private PythonNode parseBitxor() {
        PythonNode e = parseBitand();
        while (matchOp("^")) e = new PythonNode.Binary("^", e, parseBitand());
        return e;
    }

    private PythonNode parseBitand() {
        PythonNode e = parseShift();
        while (matchOp("&")) e = new PythonNode.Binary("&", e, parseShift());
        return e;
    }

    private PythonNode parseShift() {
        PythonNode e = parseAdd();
        while (true) {
            if (matchOp("<<")) e = new PythonNode.Binary("<<", e, parseAdd());
            else if (matchOp(">>")) e = new PythonNode.Binary(">>", e, parseAdd());
            else break;
        }
        return e;
    }

    private PythonNode parseAdd() {
        PythonNode e = parseMul();
        while (true) {
            if (matchOp("+")) e = new PythonNode.Binary("+", e, parseMul());
            else if (matchOp("-")) e = new PythonNode.Binary("-", e, parseMul());
            else break;
        }
        return e;
    }

    private PythonNode parseMul() {
        PythonNode e = parseUnary();
        while (true) {
            String op = null;
            if (atOp("*")) op = "*";
            else if (atOp("/")) op = "/";
            else if (atOp("//")) op = "//";
            else if (atOp("%")) op = "%";
            else if (atOp("@")) op = "@";
            if (op == null) break;
            advance();
            e = new PythonNode.Binary(op, e, parseUnary());
        }
        return e;
    }

    private PythonNode parseUnary() {
        if (atOp("+") || atOp("-") || atOp("~")) {
            String op = advance().text();
            return new PythonNode.Unary(op, parseUnary());
        }
        return parsePower();
    }

    private PythonNode parsePower() {
        PythonNode e = parsePostfix();
        if (matchOp("**")) return new PythonNode.Binary("**", e, parseUnary()); // right side allows unary, right-assoc
        return e;
    }

    private PythonNode parsePostfix() {
        PythonNode e = parseAtom();
        while (true) {
            if (atOp("(")) {
                advance();
                List<PythonNode> args = parseArgList(")");
                expectOp(")");
                e = new PythonNode.Call(e, args);
            } else if (atOp("[")) {
                advance();
                PythonNode idx = parseSubscript();
                expectOp("]");
                e = new PythonNode.Index(e, idx);
            } else if (matchOp(".")) {
                String attr = expectName();
                e = new PythonNode.Attribute(e, attr);
            } else break;
        }
        return e;
    }

    private PythonNode parseAtom() {
        // walrus / named expression: NAME := test → (name = value). The ':=' is a single OP token.
        if (peek().type() == PythonToken.Type.NAME && pos + 1 < tokens.size()
                && tokens.get(pos + 1).isOp(":=")) {
            String name = advance().text();
            advance(); // ':='
            return new PythonNode.Walrus(name, parseTest());
        }
        // yield-expression (yield is an atom in Python, only meaningful inside a generator function).
        if (peek().type() == PythonToken.Type.NAME && "yield".equals(peek().text())) {
            advance();
            markGenerator();
            if (matchKw("from")) return new PythonNode.Yield(parseTest(), true);
            if (atNewlineOrEnd() || atOp(")") || atOp("]") || atOp("}") || atOp(",") || atOp(":") || atOp("=")) {
                return new PythonNode.Yield(null, false);
            }
            return new PythonNode.Yield(parseTestList(), false);
        }
        PythonToken t = peek();
        switch (t.type()) {
            case INT -> { advance(); return new PythonNode.IntLit(Long.parseLong(t.text())); }
            case FLOAT -> { advance(); return new PythonNode.FloatLit(Double.parseDouble(t.text())); }
            case STRING -> {
                advance();
                StringBuilder sb = new StringBuilder(t.text());
                // implicit adjacent string concatenation: "a" "b" → "ab"
                while (peek().is(PythonToken.Type.STRING)) { sb.append(advance().text()); }
                return new PythonNode.StrLit(sb.toString());
            }
            case FSTRING -> { advance(); return FStringParser.parse(t.text()); }
            case NAME -> {
                advance();
                return switch (t.text()) {
                    case "True" -> new PythonNode.BoolLit(true);
                    case "False" -> new PythonNode.BoolLit(false);
                    case "None" -> new PythonNode.NoneLit();
                    default -> new PythonNode.Name(t.text());
                };
            }
            case OP -> {
                return parseBracketAtom();
            }
            default -> throw error("unexpected token " + t.type() + " '" + t.text() + "' in expression");
        }
    }

    private PythonNode parseBracketAtom() {
        if (matchOp("(")) {
            if (matchOp(")")) return new PythonNode.TupleLit(List.of()); // ()
            PythonNode first = parseTest();
            if (peek().isKw("for")) {
                // generator expression: (expr for ...)
                List<PythonNode.CompClause> clauses = parseCompClauses();
                expectOp(")");
                return new PythonNode.GenExp(first, clauses);
            }
            if (matchOp(",")) {
                List<PythonNode> elems = new ArrayList<>();
                elems.add(first);
                while (!atOp(")")) {
                    elems.add(parseElement());
                    if (!matchOp(",")) break;
                }
                expectOp(")");
                return new PythonNode.TupleLit(elems);
            }
            expectOp(")");
            return first; // parenthesized
        }
        if (matchOp("[")) {
            if (matchOp("]")) return new PythonNode.ListLit(List.of()); // []
            PythonNode first = parseElement();
            if (peek().isKw("for")) return parseListCompRest(first);
            List<PythonNode> elems = new ArrayList<>();
            elems.add(first);
            while (matchOp(",")) {
                if (atOp("]")) break;
                elems.add(parseElement());
            }
            expectOp("]");
            return new PythonNode.ListLit(elems);
        }
        if (matchOp("{")) {
            if (matchOp("}")) return new PythonNode.DictLit(List.of(), List.of()); // {}
            if (atOp("**")) {   // dict with a leading **spread
                List<PythonNode> keys = new ArrayList<>();
                List<PythonNode> values = new ArrayList<>();
                advance();
                keys.add(new PythonNode.Starred(parseTest(), true));
                values.add(new PythonNode.NoneLit());
                while (matchOp(",")) {
                    if (atOp("}")) break;
                    if (matchOp("**")) { keys.add(new PythonNode.Starred(parseTest(), true)); values.add(new PythonNode.NoneLit()); }
                    else { PythonNode k = parseTest(); expectOp(":"); keys.add(k); values.add(parseTest()); }
                }
                expectOp("}");
                return new PythonNode.DictLit(keys, values);
            }
            PythonNode first = parseElement();   // *x (set spread) or a plain test
            if (matchOp(":")) {
                PythonNode val = parseTest();
                if (peek().isKw("for")) {
                    List<PythonNode.CompClause> c = parseCompClauses();
                    expectOp("}");
                    return new PythonNode.DictComp(first, val, c);
                }
                List<PythonNode> keys = new ArrayList<>(List.of(first));
                List<PythonNode> values = new ArrayList<>(List.of(val));
                while (matchOp(",")) {
                    if (atOp("}")) break;
                    if (matchOp("**")) { keys.add(new PythonNode.Starred(parseTest(), true)); values.add(new PythonNode.NoneLit()); }
                    else { keys.add(parseTest()); expectOp(":"); values.add(parseTest()); }
                }
                expectOp("}");
                return new PythonNode.DictLit(keys, values);
            }
            if (peek().isKw("for")) {
                List<PythonNode.CompClause> c = parseCompClauses();
                expectOp("}");
                return new PythonNode.SetComp(first, c);
            }
            List<PythonNode> elems = new ArrayList<>(List.of(first));
            while (matchOp(",")) {
                if (atOp("}")) break;
                elems.add(parseElement());
            }
            expectOp("}");
            return new PythonNode.SetLit(elems);
        }
        throw error("unexpected token '" + peek().text() + "' in expression");
    }

    /** A list/tuple/set element: a {@code *spread} or a plain test. */
    private PythonNode parseElement() {
        if (matchOp("*")) return new PythonNode.Starred(parseTest(), false);
        return parseTest();
    }

    private PythonNode parseListCompRest(PythonNode element) {
        List<PythonNode.CompClause> clauses = parseCompClauses();
        expectOp("]");
        return new PythonNode.ListComp(element, clauses);
    }

    /** One or more {@code for target in iter [if cond]*} clauses (Python allows nested for / multiple if). */
    private List<PythonNode.CompClause> parseCompClauses() {
        List<PythonNode.CompClause> clauses = new ArrayList<>();
        while (matchKw("for")) {
            PythonNode target = parseTargetList();
            expectKw("in");
            PythonNode iter = parseOr();
            clauses.add(new PythonNode.ForComp(target, iter));
            while (matchKw("if")) clauses.add(new PythonNode.IfComp(parseOr()));
        }
        return clauses;
    }

    /** Parses a subscript: a plain index expr, or a slice {@code [lo:up:step]} (any part optional). */
    private PythonNode parseSubscript() {
        if (atOp(":")) return parseSliceRest(null);     // [:stop...]
        PythonNode first = parseTest();
        if (atOp(":")) return parseSliceRest(first);    // [lo:...]
        return first;                                    // plain index
    }

    private PythonNode parseSliceRest(PythonNode lower) {
        expectOp(":");
        PythonNode upper = null;
        if (!atOp("]") && !atOp(":")) upper = parseTest();
        PythonNode step = null;
        if (matchOp(":") && !atOp("]")) step = parseTest();
        return new PythonNode.Slice(lower, upper, step);
    }

    /** A target list for {@code for}/assignment: {@code x} or {@code a, b} (→ TupleLit). */
    private PythonNode parseTargetList() {
        PythonNode first = parseUnary(); // targets are postfix-level: name / attr / index / tuple
        if (matchOp(",")) {
            List<PythonNode> elems = new ArrayList<>();
            elems.add(first);
            while (!atOp(")") && !peek().isKw("in") && !atOp("=") && !atOp(":")) {
                elems.add(parseUnary());
                if (!matchOp(",")) break;
            }
            return new PythonNode.TupleLit(elems);
        }
        return first;
    }

    /** testlist: one expression, or a tuple if a comma follows. */
    private PythonNode parseTestList() {
        PythonNode first = parseTest();
        if (atOp(",")) {
            List<PythonNode> elems = new ArrayList<>();
            elems.add(first);
            while (matchOp(",")) {
                if (atNewlineOrEnd() || atOp(")") || atOp("]") || atOp("}") || atOp("=") || atOp(":")
                        || at(PythonToken.Type.DEDENT)) break;
                elems.add(parseTest());
            }
            return new PythonNode.TupleLit(elems);
        }
        return first;
    }

    private List<PythonNode> parseArgList(String close) {
        List<PythonNode> args = new ArrayList<>();
        if (atOp(close)) return args;
        boolean first = true;
        while (true) {
            if (atOp("**")) { advance(); args.add(new PythonNode.Starred(parseTest(), true)); }
            else if (atOp("*")) { advance(); args.add(new PythonNode.Starred(parseTest(), false)); }
            else if (peek().type() == PythonToken.Type.NAME && pos + 1 < tokens.size()
                    && tokens.get(pos + 1).isOp("=")) {
                // keyword argument: NAME '=' value (but not '==')
                String name = advance().text();
                advance(); // '='
                args.add(new PythonNode.Kwarg(name, parseTest()));
            } else {
                PythonNode e = parseTest();
                if (first && peek().isKw("for")) {
                    // sole generator-expression argument: f(x for x in xs)
                    args.add(new PythonNode.GenExp(e, parseCompClauses()));
                    return args;
                }
                args.add(e);
            }
            first = false;
            if (!matchOp(",")) break;
            if (atOp(close)) break;   // trailing comma
        }
        return args;
    }

    private PythonNode parseImport() {
        List<PythonNode.Spec> specs = new ArrayList<>();
        while (true) {
            String mod = parseDottedName();
            String alias = matchKw("as") ? expectName() : null;
            specs.add(new PythonNode.Spec(mod, alias));
            if (!matchOp(",")) break;
        }
        return new PythonNode.Import(specs);
    }

    private PythonNode parseImportFrom() {
        String module = parseDottedName();
        expectKw("import");
        if (matchOp("*")) return new PythonNode.ImportFrom(module, List.of(), true);
        List<PythonNode.Spec> specs = new ArrayList<>();
        while (true) {
            String name = expectName();
            String alias = matchKw("as") ? expectName() : null;
            specs.add(new PythonNode.Spec(name, alias));
            if (!matchOp(",")) break;
        }
        return new PythonNode.ImportFrom(module, specs, false);
    }

    private String parseDottedName() {
        StringBuilder sb = new StringBuilder(expectName());
        while (matchOp(".")) sb.append('.').append(expectName());
        return sb.toString();
    }

    private List<Param> parseParams(String terminator) {
        List<Param> params = new ArrayList<>();
        while (!atOp(terminator)) {
            if (params.size() > 0) expectOp(",");
            if (atOp(terminator)) break;
            boolean star = false;
            boolean kwDict = false;
            if (matchOp("*")) star = true;
            else if (matchOp("**")) kwDict = true;
            String name = expectName();
            if (terminator.equals(")") && matchOp(":")) parseTest();   // def param annotation (not lambda)
            PythonNode def = null;
            if (matchOp("=")) def = parseTest();
            params.add(new Param(name, def, star, kwDict));
        }
        return params;
    }

    // ---- token helpers ----

    /** Marks the innermost function as a generator (called whenever a {@code yield} is parsed). */
    private void markGenerator() {
        if (!fnGen.isEmpty()) fnGen.peek()[0] = true;
    }

    private PythonToken peek() { return tokens.get(pos); }
    private PythonToken advance() { return tokens.get(pos++); }
    private boolean at(PythonToken.Type t) { return peek().type() == t; }
    private boolean atOp(String op) { return peek().isOp(op); }
    private boolean match(PythonToken.Type t) { if (at(t)) { advance(); return true; } return false; }
    private boolean matchOp(String op) { if (atOp(op)) { advance(); return true; } return false; }
    private boolean matchKw(String kw) { if (peek().isKw(kw)) { advance(); return true; } return false; }
    private void expect(PythonToken.Type t, String what) {
        if (!match(t)) throw error("expected " + what + " but found " + peek().type() + " '" + peek().text() + "'");
    }
    private void expectOp(String op) { if (!matchOp(op)) throw error("expected '" + op + "' but found '" + peek().text() + "'"); }
    private void expectKw(String kw) { if (!matchKw(kw)) throw error("expected '" + kw + "' but found '" + peek().text() + "'"); }
    private String expectName() {
        if (peek().type() != PythonToken.Type.NAME) throw error("expected name but found '" + peek().text() + "'");
        return advance().text();
    }

    private boolean atNewlineOrEnd() {
        return at(PythonToken.Type.NEWLINE) || at(PythonToken.Type.EOF);
    }
    private boolean atAugOp() {
        if (peek().type() != PythonToken.Type.OP) return false;
        String t = peek().text();
        return t.equals("+=") || t.equals("-=") || t.equals("*=") || t.equals("/=") || t.equals("//=")
                || t.equals("%=") || t.equals("**=") || t.equals("@=") || t.equals("&=") || t.equals("|=")
                || t.equals("^=") || t.equals("<<=") || t.equals(">>=");
    }
    private boolean isCompoundKw() {
        PythonToken p = peek();
        if (p.isKw("def") || p.isKw("class") || p.isKw("if") || p.isKw("for")
                || p.isKw("while") || p.isKw("try") || p.isKw("with")) return true;
        // 'match' is a SOFT keyword: it is a match-statement only when the rest of the logical line
        // has the form '<subject> :' (a ':' at bracket-depth 0 before the NEWLINE), so that 'match'
        // used as an ordinary identifier ('match = 5', a bare 'match', 'match(x)' call) still works.
        return p.isKw("match") && looksLikeMatchHeader();
    }

    private boolean looksLikeMatchHeader() {
        int depth = 0;
        for (int i = pos + 1; i < tokens.size(); i++) {
            PythonToken tk = tokens.get(i);
            if (tk.type() == PythonToken.Type.NEWLINE || tk.type() == PythonToken.Type.EOF) return false;
            if (tk.type() == PythonToken.Type.OP) {
                switch (tk.text()) {
                    case "(", "[", "{" -> depth++;
                    case ")", "]", "}" -> depth--;
                    case ":" -> { if (depth == 0) return true; }
                    default -> { }
                }
            }
        }
        return false;
    }
    private void skipNewlines() { while (at(PythonToken.Type.NEWLINE)) advance(); }

    /** Returns the comparison operator at the cursor (handling {@code not in}/{@code is not}), or null. */
    private String comparisonOp() {
        if (peek().isKw("in")) { advance(); return "in"; }
        if (peek().isKw("not") && pos + 1 < tokens.size() && tokens.get(pos + 1).isKw("in")) {
            advance(); advance(); return "not in";
        }
        if (peek().isKw("is")) {
            advance();
            if (matchKw("not")) return "is not";
            return "is";
        }
        if (peek().type() == PythonToken.Type.OP) {
            String t = peek().text();
            switch (t) {
                case "==", "!=", "<", ">", "<=", ">=" -> { advance(); return t; }
                default -> { return null; }
            }
        }
        return null;
    }

    private IllegalArgumentException error(String msg) {
        PythonToken t = peek();
        return new IllegalArgumentException("python parse error at line " + t.line() + ", col " + t.col() + ": " + msg);
    }
}
