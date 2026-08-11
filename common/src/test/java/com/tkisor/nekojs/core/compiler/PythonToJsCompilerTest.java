package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for {@link PythonToJsCompiler}: each case transpiles a Python snippet, asserts
 * a key JS fragment is present, then executes the emitted JS via GraalJS and asserts the result.
 * Lives in this package to access the package-private {@link CompilerExecutionAssertions} helper,
 * mirroring the other compiler tests.
 */
class PythonToJsCompilerTest {

    private final PythonToJsCompiler compiler = new PythonToJsCompiler();

    private String py(String src) throws Exception {
        return compiler.compile(Path.of("test.py"), src);
    }

    private long evalInt(String src) throws Exception {
        try (var eval = CompilerExecutionAssertions.eval(py(src))) {
            return eval.value().asLong();
        }
    }

    private String evalString(String src) throws Exception {
        try (var eval = CompilerExecutionAssertions.eval(py(src))) {
            return eval.value().asString();
        }
    }

    private boolean evalBool(String src) throws Exception {
        try (var eval = CompilerExecutionAssertions.eval(py(src))) {
            return eval.value().asBoolean();
        }
    }

    @Test
    void canCompileHandlesPyExtension() {
        assertTrue(compiler.canCompile(".py"));
        assertTrue(compiler.canCompile("py"));
        assertTrue(compiler.canCompile(".PY"));
        assertFalse(compiler.canCompile(".ts"));
        assertFalse(compiler.canCompile(null));
    }

    @Test
    void functionAndReturn() throws Exception {
        String js = py("def f(x):\n    return x * 2\nf(21)");
        assertTrue(js.contains("function f(x)"), js);
        assertTrue(js.contains("return"), js);
        assertEquals(42, evalInt("def f(x):\n    return x * 2\nf(21)"));
    }

    @Test
    void arithmeticPrecedence() throws Exception {
        assertEquals(14, evalInt("2 + 3 * 4"));
        assertEquals(-6, evalInt("2 - 2 * 4"));
        assertEquals(16, evalInt("2 ** 4"));
    }

    @Test
    void ifElifElse() throws Exception {
        String src = """
                def grade(s):
                    if s >= 90:
                        return 'A'
                    elif s >= 80:
                        return 'B'
                    else:
                        return 'C'
                grade(85)
                """;
        String js = py(src);
        assertTrue(js.contains("else if"), "elif must lower to else if: " + js);
        assertEquals("B", evalString(src));
        assertEquals("A", evalString(src.replace("grade(85)", "grade(95)")));
        assertEquals("C", evalString(src.replace("grade(85)", "grade(50)")));
    }

    @Test
    void forRangeSum() throws Exception {
        String src = """
                total = 0
                for i in range(5):
                    total += i
                total
                """;
        String js = py(src);
        assertTrue(js.contains("for (var i of"), js);
        assertTrue(js.contains("Array.from"), js);
        assertEquals(10, evalInt(src)); // 0+1+2+3+4
    }

    @Test
    void whileLoop() throws Exception {
        String src = """
                n = 1
                while n < 16:
                    n *= 2
                n
                """;
        assertEquals(16, evalInt(src));
    }

    @Test
    void fStringInterpolation() throws Exception {
        String src = """
                name = 'neko'
                f"hi {name}!"
                """;
        String js = py(src);
        assertTrue(js.contains("`hi ${name}!`"), "f-string must emit a template literal: " + js);
        assertEquals("hi neko!", evalString(src));
    }

    @Test
    void listComprehension() throws Exception {
        // [x*x for x in range(4)] = [0,1,4,9]; index [2] = 4
        String src = "[x * x for x in range(4)][2]";
        String js = py(src);
        assertTrue(js.contains(".map("), js);
        assertEquals(4, evalInt(src));
    }

    @Test
    void listComprehensionWithFilter() throws Exception {
        // [x for x in range(10) if x > 5] = [6,7,8,9]; index [1] = 7
        String src = "[x for x in range(10) if x > 5][1]";
        String js = py(src);
        assertTrue(js.contains(".filter("), js);
        assertEquals(7, evalInt(src));
    }

    @Test
    void tupleUnpacking() throws Exception {
        String src = """
                a, b = 1, 2
                a + b
                """;
        String js = py(src);
        assertTrue(js.contains("var [a, b] ="), js);
        assertEquals(3, evalInt(src));
    }

    @Test
    void recursion() throws Exception {
        String src = """
                def fact(n):
                    if n <= 1:
                        return 1
                    return n * fact(n - 1)
                fact(5)
                """;
        assertEquals(120, evalInt(src));
    }

    @Test
    void nestedFunctionClosure() throws Exception {
        String src = """
                def adder(n):
                    def add(x):
                        return x + n
                    return add(10)
                adder(5)
                """;
        assertEquals(15, evalInt(src));
    }

    @Test
    void dictIndexing() throws Exception {
        String src = """
                d = {'a': 1, 'b': 2}
                d['b']
                """;
        assertEquals(2, evalInt(src));
    }

    @Test
    void ternaryExpression() throws Exception {
        String src = """
                x = 5
                'big' if x > 3 else 'small'
                """;
        assertEquals("big", evalString(src));
        assertEquals("small", evalString(src.replace("x = 5", "x = 1")));
    }

    @Test
    void membershipAndBoolean() throws Exception {
        assertTrue(evalBool("3 in [1, 2, 3]"));
        assertFalse(evalBool("5 in [1, 2, 3]"));
        assertTrue(evalBool("not False"));
        assertTrue(evalBool("True and 1 < 2"));
    }

    @Test
    void printMapsToConsoleLog() throws Exception {
        String js = py("print('hello')");
        assertTrue(js.contains("console.log("), "print() must map to console.log: " + js);
    }

    @Test
    void syntaxErrorThrowsWithFileContext() {
        assertThrows(IllegalArgumentException.class, () -> py("def f(:\n    pass"));
    }
}
