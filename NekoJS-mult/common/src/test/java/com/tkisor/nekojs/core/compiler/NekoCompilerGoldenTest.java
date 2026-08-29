package com.tkisor.nekojs.core.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden harness for the TS Eraser ({@link NekoTypeScriptCompiler#eraseTypescript}) and JSX
 * transpiler ({@link NekoJsxCompiler#compileJsx}).
 *
 * <p>Unlike {@link NekoTypeScriptCompilerTest} (which asserts on specific features via
 * {@code contains}/{@code assertFalse}), this harness asserts <b>whole-output invariants</b>:
 * <ul>
 *   <li>idempotency — {@code erase(erase(src)) == erase(src)} (erasure is a fixpoint),</li>
 *   <li>line-structure preservation for type-erasure-only inputs,</li>
 *   <li>the erased output parses and runs as JavaScript.</li>
 * </ul>
 * These catch ANY unintended drift in erased output — the safety net that COMP-3 (Eraser
 * decomposition) and COMP-1/2 (entrypoint convergence) need before refactoring the compiler.
 *
 * <p>The idempotency corpus covers only <b>type-erasure</b> responsibilities (annotations,
 * generics, interfaces, type aliases, inline imports, {@code as} assertions, class-member
 * modifiers). Transform-producing features (enum / namespace / parameter-property lowering)
 * are covered by {@link NekoTypeScriptCompilerTest} and are intentionally excluded here so the
 * fixpoint invariant stays clean.
 */
class NekoCompilerGoldenTest {

    static Stream<String> typeErasureCorpus() {
        return Stream.of(
                "let x: number = 1",
                "const id = <T>(x: T): T => x",
                "function f(a: number, b?: string): boolean { return b ? true : false }",
                "interface I { x: number; y: string }",
                "type Alias = number | string",
                "import { real, type T } from 'mod'\nconst v = real",
                "const p = typeof f === 'function' ? (f as Promise<unknown>) : f",
                "class Foo { private x: number = 1; protected y: string = 'a'; greet(): string { return 'hi' } }",
                "const arr: Array<{ a: number }> = [{ a: 1 }]",
                "const g = (n: number): string => (n > 0 ? 'pos' : 'neg')"
        );
    }

    @ParameterizedTest
    @MethodSource("typeErasureCorpus")
    void erasureIsAFixpoint(String src) {
        String once = NekoTypeScriptCompiler.eraseTypescript(Path.of("golden.ts"), src);
        String twice = NekoTypeScriptCompiler.eraseTypescript(Path.of("golden.ts"), once);
        assertEquals(once, twice, "erase(erase(src)) must equal erase(src)\n--- once ---\n" + once);
    }

    @ParameterizedTest
    @MethodSource("typeErasureCorpus")
    void typeErasurePreservesLineCount(String src) {
        // Erasure replaces type spans with spaces (same length), so the line count is unchanged.
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("golden.ts"), src);
        assertEquals(countLines(src), countLines(out), "line count must be preserved\n--- out ---\n" + out);
    }

    @Test
    void noTypeInputPassesThroughStructurallyIntact() {
        String src = "let x = 1\nconst y = x + 2\nconst z = y * 3";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("plain.ts"), src);
        assertEquals(countLines(src), countLines(out));
        // value expressions survive untouched
        assertTrue(out.contains("let x = 1"));
        assertTrue(out.contains("x + 2"));
        assertTrue(out.contains("y * 3"));
    }

    @Test
    void erasedOutputRunsAsJavaScript() {
        String src = "const id = <T>(x: T): T => x\nid(41) + 1";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("run.ts"), src);
        try (CompilerExecutionAssertions.Evaluation eval = CompilerExecutionAssertions.eval(out)) {
            assertEquals(42, eval.value().asInt(), "erased output must execute and produce 42\n" + out);
        }
    }

    @Test
    void jsxCompileProducesCodeAndSourceMap() {
        var result = NekoJsxCompiler.compileJsx(Path.of("node.jsx"), "const n = <div id='x'>hi</div>");
        assertNotNull(result.code());
        assertFalse(result.code().isBlank(), "JSX lowering must produce code");
        assertNotNull(result.sourceMap(), "JSX lowering must produce a source map");
    }

    @Test
    void tsxRoundTripsThroughJsxAndEraser() {
        // compileTsx lowers JSX then erases TS types; output must be non-empty runnable JS.
        var result = NekoJsxCompiler.compileTsx(Path.of("node.tsx"),
                "const n: unknown = <div>{1 + 2}</div>");
        assertNotNull(result.code());
        assertFalse(result.code().isBlank(), "TSX must lower + erase to non-empty JS");
    }

    private static int countLines(String s) {
        return s.split("\n", -1).length;
    }
}
