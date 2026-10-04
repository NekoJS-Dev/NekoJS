package com.tkisor.nekojs.core.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ValParser} AST 形状测试：字符串字面量、const/let/var 声明、命名/计算成员访问、
 * optional computed、转义解码、畸形输入不挂死。
 */
class ValParserTest {

    private static ValNode.Block parse(String source) {
        return ValParser.parse(source);
    }

    private static ValNode first(ValNode.Block block) {
        assertNotNull(block);
        assertEquals(1, block.stmts().size(), "expected single statement");
        return block.stmts().getFirst();
    }

    @Test
    void stringLiteralKeepsValueAndPosition() {
        ValNode.Block block = parse("const key = 'getServer';");
        ValNode.VarDecl decl = assertInstanceOf(ValNode.VarDecl.class, first(block));
        assertEquals(ValNode.DeclarationKind.CONST, decl.kind());
        ValNode.StringLiteral lit = assertInstanceOf(ValNode.StringLiteral.class, decl.init());
        assertEquals("getServer", lit.value());
    }

    @Test
    void stringLiteralDecodesCommonEscapes() {
        assertEquals("a\nb", stringValue("const s = 'a\\nb';"));
        assertEquals("tA", stringValue("const s = 't\\x41';"));
        assertEquals("q\"u", stringValue("const s = \"q\\\"u\";"));
        assertEquals("a\\b", stringValue("const s = 'a\\\\b';"));
    }

    private static String stringValue(String source) {
        ValNode.VarDecl decl = (ValNode.VarDecl) parse(source).stmts().getFirst();
        return ((ValNode.StringLiteral) decl.init()).value();
    }

    @Test
    void varDeclDistinguishesKinds() {
        assertEquals(ValNode.DeclarationKind.LET,
                ((ValNode.VarDecl) parse("let x = 1;").stmts().getFirst()).kind());
        assertEquals(ValNode.DeclarationKind.VAR,
                ((ValNode.VarDecl) parse("var y = 2;").stmts().getFirst()).kind());
    }

    @Test
    void commaSeparatedDeclarationsKeepEveryBindingAndKind() {
        for (String keyword : List.of("let", "var", "const")) {
            String declarations = keyword.equals("const")
                    ? "firstHost = null, firstRoot = null, secondHost = null, secondRoot = null;"
                    : "firstHost, firstRoot, secondHost, secondRoot;";
            ValNode.Block block = parse(keyword + " " + declarations);
            assertEquals(List.of("firstHost", "firstRoot", "secondHost", "secondRoot"),
                    block.stmts().stream().map(node -> assertInstanceOf(ValNode.VarDecl.class, node).name()).toList());
            assertEquals(List.of("firstHost", "firstRoot", "secondHost", "secondRoot"),
                    List.copyOf(block.scope().keySet()));
            for (ValNode node : block.stmts()) {
                assertEquals(ValNode.DeclarationKind.valueOf(keyword.toUpperCase(java.util.Locale.ROOT)),
                        assertInstanceOf(ValNode.VarDecl.class, node).kind());
            }
        }
    }

    @Test
    void declarationSeparatorsExcludeNestedInitializerCommas() {
        ValNode.Block block = parse("const first = Item.of('stone', 1), "
                + "grouped = (left, right), values = [left, right], "
                + "options = { pair: [left, right] }, callback = (ctx, gui) => ctx.rect(1, 2), last = Utils;");
        assertEquals(List.of("first", "grouped", "values", "options", "callback", "last"),
                block.stmts().stream().map(node -> assertInstanceOf(ValNode.VarDecl.class, node).name()).toList());
        assertEquals(List.of("first", "grouped", "values", "options", "callback", "last"),
                List.copyOf(block.scope().keySet()));
        ValNode.CallExpr call = assertInstanceOf(ValNode.CallExpr.class,
                assertInstanceOf(ValNode.VarDecl.class, block.stmts().getFirst()).init());
        assertEquals(2, call.args().size());
        ValNode.ArrowFunc callback = assertInstanceOf(ValNode.ArrowFunc.class,
                assertInstanceOf(ValNode.VarDecl.class, block.stmts().get(4)).init());
        assertEquals(List.of("ctx", "gui"), callback.params());
    }

    @Test
    void multilineDeclaratorsAndInitializerBoundariesPreserveFollowingStatements() {
        ValNode.Block block = parse("let first = 'comma,inside', /* separator */\n"
                + " second = Utils\nsecond.getServer();\n"
                + "{ var localFirst, localSecond; localSecond.bindRoot(); }");
        assertEquals(4, block.stmts().size());
        assertEquals(List.of("first", "second"), List.copyOf(block.scope().keySet()));
        assertInstanceOf(ValNode.CallExpr.class, block.stmts().get(2));
        ValNode.Block nested = assertInstanceOf(ValNode.Block.class, block.stmts().get(3));
        assertEquals(List.of("localFirst", "localSecond"), List.copyOf(nested.scope().keySet()));
        assertEquals(3, nested.stmts().size());
    }

    @Test
    void compoundInitializersRetainEveryMemberAndOriginalOffset() {
        for (String expression : List.of("[Utils.serverTel()]", "true && Utils.serverTel()",
                "1 + Utils.serverTel()", "true ? Utils.serverTel() : Item.empty()",
                "(1 + Utils.serverTel())")) {
            String source = "const value = " + expression + ", later = Utils;";
            ValNode.Block block = parse(source);
            assertEquals(List.of("value", "later"), List.copyOf(block.scope().keySet()));
            ValNode.VarDecl declaration = assertInstanceOf(ValNode.VarDecl.class, block.stmts().getFirst());
            ValNode.MemberAccess member = findMember(declaration.init(), "serverTel");
            assertNotNull(member, expression);
            assertEquals(source.indexOf("serverTel"), member.start(), expression);
            if (declaration.init() instanceof ValNode.Block fragments) {
                assertTrue(fragments.scope().isEmpty(), "expression fragments must not declare operands");
            }
        }
    }

    @Test
    void prefixOperatorsAfterNewlinesStartSeparateStatements() {
        for (String operator : List.of("!", "++", "--")) {
            String source = "let value = Utils\n" + operator + "missing.call();";
            ValNode.Block block = parse(source);
            ValNode.VarDecl declaration = assertInstanceOf(ValNode.VarDecl.class, block.stmts().getFirst());
            assertEquals("Utils", assertInstanceOf(ValNode.Identifier.class, declaration.init()).name());
            assertNotNull(findMember(block, "call"), operator);
            assertTrue(declaration.end() <= source.indexOf(operator));
        }
        ValNode.VarDecl declaration = assertInstanceOf(ValNode.VarDecl.class,
                parse("const value = 1\n + Utils.serverTel();").stmts().getFirst());
        assertNotNull(findMember(declaration.init(), "serverTel"));
    }

    private static ValNode.MemberAccess findMember(ValNode node, String name) {
        if (node instanceof ValNode.MemberAccess member) {
            if (name.equals(member.member())) return member;
            return findMember(member.object(), name);
        }
        if (node instanceof ValNode.CallExpr call) {
            ValNode.MemberAccess member = findMember(call.callee(), name);
            if (member != null) return member;
            for (ValNode argument : call.args()) {
                member = findMember(argument, name);
                if (member != null) return member;
            }
        }
        if (node instanceof ValNode.Block block) {
            for (ValNode statement : block.stmts()) {
                ValNode.MemberAccess member = findMember(statement, name);
                if (member != null) return member;
            }
        }
        if (node instanceof ValNode.VarDecl declaration) return findMember(declaration.init(), name);
        return null;
    }

    @Test
    void quotedBracketNormalizedToNamedMemberAccess() {
        ValNode.Block block = parse("e['getServer']()");
        ValNode.CallExpr call = assertInstanceOf(ValNode.CallExpr.class, first(block));
        ValNode.MemberAccess access = assertInstanceOf(ValNode.MemberAccess.class, call.callee());
        assertEquals("getServer", access.member());
        assertTrue(access.bracket());
    }

    @Test
    void doubleQuotedBracketNormalized() {
        ValNode.Block block = parse("e[\"server\"]");
        ValNode.MemberAccess access = assertInstanceOf(ValNode.MemberAccess.class, first(block));
        assertEquals("server", access.member());
        assertTrue(access.bracket());
    }

    @Test
    void computedBracketKeepsKeyExpression() {
        ValNode.Block block = parse("e[key]()");
        ValNode.CallExpr call = assertInstanceOf(ValNode.CallExpr.class, first(block));
        ValNode.ComputedMemberAccess computed =
                assertInstanceOf(ValNode.ComputedMemberAccess.class, call.callee());
        ValNode.Identifier key = assertInstanceOf(ValNode.Identifier.class, computed.key());
        assertEquals("key", key.name());
    }

    @Test
    void optionalComputedBracketMarkedOptional() {
        ValNode.Block block = parse("e?.[key]");
        ValNode.ComputedMemberAccess computed =
                assertInstanceOf(ValNode.ComputedMemberAccess.class, first(block));
        assertTrue(computed.optional());
    }

    @Test
    void optionalChainedMember() {
        ValNode.Block block = parse("e?.getServer()");
        ValNode.CallExpr call = assertInstanceOf(ValNode.CallExpr.class, first(block));
        ValNode.MemberAccess access = assertInstanceOf(ValNode.MemberAccess.class, call.callee());
        assertEquals("getServer", access.member());
    }

    @Test
    void chainedCallsKeepNestedShape() {
        ValNode.Block block = parse("e.getPlayer().getServer()");
        ValNode.CallExpr outer = assertInstanceOf(ValNode.CallExpr.class, first(block));
        ValNode.MemberAccess outerAccess = assertInstanceOf(ValNode.MemberAccess.class, outer.callee());
        assertEquals("getServer", outerAccess.member());
        ValNode.CallExpr inner = assertInstanceOf(ValNode.CallExpr.class, outerAccess.object());
        ValNode.MemberAccess innerAccess = assertInstanceOf(ValNode.MemberAccess.class, inner.callee());
        assertEquals("getPlayer", innerAccess.member());
    }

    @Test
    void parenthesizedArrowParametersAndGroupingKeepDistinctShapes() {
        ValNode.ArrowFunc twoParameters = assertInstanceOf(
                ValNode.ArrowFunc.class, first(parse("(ctx, gui) => ctx.rect()")));
        assertEquals(List.of("ctx", "gui"), twoParameters.params());

        ValNode.ArrowFunc noParameters = assertInstanceOf(
                ValNode.ArrowFunc.class, first(parse("() => console.log('ok')")));
        assertEquals(List.of(), noParameters.params());

        ValNode.Identifier grouped = assertInstanceOf(
                ValNode.Identifier.class, first(parse("(value)")));
        assertEquals("value", grouped.name());
    }

    @Test
    void malformedInputDoesNotHang() {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            parse("TestEvents.started((e) => { ; ; @#$ { ( ( }");
            parse("e[;]");
            parse("e[");
            parse("e.getServer( ; }");
            parse("const x = 'unclosed");
            parse("?.[");
        });
    }

    @Test
    void computedKeyWithConstStringAliasIsPreserved() {
        ValNode.Block block = parse("const k = 'getServer'; e[k]()");
        assertEquals(2, block.stmts().size());
        ValNode.CallExpr call = assertInstanceOf(ValNode.CallExpr.class, block.stmts().get(1));
        ValNode.ComputedMemberAccess computed =
                assertInstanceOf(ValNode.ComputedMemberAccess.class, call.callee());
        ValNode.Identifier key = assertInstanceOf(ValNode.Identifier.class, computed.key());
        assertEquals("k", key.name());
    }

    @Test
    void emptyIdentForUnparseablePartsDoesNotThrow() {
        // 模板字符串与畸形括号不应抛异常
        List<ValNode> stmts = parse("const s = `x${e.getServeer()}`;").stmts();
        assertTrue(stmts.size() >= 1);
    }
}
