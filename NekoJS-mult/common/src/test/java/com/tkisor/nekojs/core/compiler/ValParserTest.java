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
