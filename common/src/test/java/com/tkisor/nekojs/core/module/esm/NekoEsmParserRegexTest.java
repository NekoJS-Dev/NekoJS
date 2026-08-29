package com.tkisor.nekojs.core.module.esm;

import com.tkisor.nekojs.core.compiler.NekoSourceLexerBase;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the COMP-5 unified regex/division disambiguator and the ESM parser's
 * handling of a regex literal following a {@code }} (TEST-1e). Pure in-memory — no Platform
 * fixture required.
 */
class NekoEsmParserRegexTest {

    @Test
    void looksLikeRegexStartAfterCloseBrace() {
        // '}' is in the regex-allowing set, so a '/' right after '}' starts a regex.
        String s = "}/x/";
        assertTrue(NekoSourceLexerBase.looksLikeRegexStart(s, s.length(), 1));
    }

    @Test
    void looksLikeRegexStartAfterOpenBraceAndOperators() {
        assertTrue(looksAt(0, "{ /x/"));   // '{'
        assertTrue(looksAt(0, "= /x/"));   // '='
        assertTrue(looksAt(0, "( /x/"));   // '('
        assertTrue(looksAt(0, ", /x/"));   // ','
        assertTrue(looksAt(0, "; /x/"));   // ';'
        assertTrue(looksAt(0, "[ /x/"));   // '['
    }

    @Test
    void looksLikeRegexStartFalseAfterOperand() {
        // after an identifier or number, '/' is division, not a regex
        assertFalse(looksAt(0, "a / x"));
        assertFalse(looksAt(0, "1 / 2"));
        assertFalse(looksAt(0, ") / 2"));
    }

    @Test
    void parseRegexAfterFunctionCloseBraceDoesNotThrow() {
        // After a function block's closing '}', the regex literal must be consumed, not misread.
        String src = "function f() { return 1 }\n/x/.test('y')";
        assertDoesNotThrow(() -> new NekoEsmParser(Path.of("test.js"), src).parse());
    }

    @Test
    void parseRegexContainingBraceDoesNotConfuseScopeTracking() {
        // A '}' inside the regex body must NOT close a real block scope, nor crash the parser.
        String src = "function f() { return 1 }\n/}/.test('}')";
        NekoEsmModuleAst ast = assertDoesNotThrow(
                () -> new NekoEsmParser(Path.of("test.js"), src).parse());
        assertNotNull(ast);
    }

    private static boolean looksAt(int ignored, String src) {
        int slash = src.indexOf('/');
        return NekoSourceLexerBase.looksLikeRegexStart(src, src.length(), slash);
    }
}
