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
    void classWithInitAndMethod() throws Exception {
        String src = """
                class Counter:
                    def __init__(self, start=0):
                        self.count = start
                    def bump(self):
                        self.count += 1
                        return self.count
                c = Counter(10)
                c.bump()
                """;
        String js = py(src);
        assertTrue(js.contains("class Counter"), js);
        assertTrue(js.contains("constructor(start = 0)"), "__init__→constructor, self dropped: " + js);
        assertTrue(js.contains("this.count"), "self→this: " + js);
        assertEquals(11, evalInt(src));
    }

    @Test
    void classInheritanceAndSuper() throws Exception {
        String src = """
                class Animal:
                    def __init__(self, name):
                        self.name = name
                class Dog(Animal):
                    def __init__(self, name, trick):
                        super().__init__(name)
                        self.trick = trick
                    def describe(self):
                        return self.name + self.trick
                Dog('rex', 'sit').describe()
                """;
        String js = py(src);
        assertTrue(js.contains("class Dog extends Animal"), js);
        assertTrue(js.contains("super(name)"), "super().__init__→super(): " + js);
        assertEquals("rexsit", evalString(src));
    }

    @Test
    void classStaticMethod() throws Exception {
        String src = """
                class Math2:
                    @staticmethod
                    def double(x):
                        return x * 2
                Math2.double(21)
                """;
        String js = py(src);
        assertTrue(js.contains("static double(x)"), "@staticmethod→static method: " + js);
        assertEquals(42, evalInt(src));
    }

    @Test
    void builtins() throws Exception {
        assertEquals(7, evalInt("abs(-7)"));
        assertEquals(1, evalInt("min([3, 1, 2])"));
        assertEquals(9, evalInt("max(1, 9, 5)"));
        assertEquals(10, evalInt("sum([1, 2, 3, 4])"));
        assertEquals(42, evalInt("int('42')"));
        assertEquals("7", evalString("str(7)"));
        assertEquals(5, evalInt("len('hello')"));
        assertFalse(evalBool("bool(0)"));
        assertTrue(evalBool("bool(1)"));
        assertEquals(1, evalInt("sorted([3, 1, 2])[0]"));
    }

    @Test
    void importEmitsGlobalThisLookup() throws Exception {
        String js = py("import Item");
        assertTrue(js.contains("var Item = globalThis.Item;"), "import X → globalThis.X: " + js);
        js = py("from utils import helper");
        assertTrue(js.contains("var helper = globalThis.utils.helper;"), "from X import a → globalThis.X.a: " + js);
        js = py("import Foo as Bar");
        assertTrue(js.contains("var Bar = globalThis.Foo;"), "import X as Y: " + js);
    }

    // ---- audit-driven regression tests (crashes & wrong output on common code) ----

    @Test
    void blockMayStartWithBlankOrCommentLine() throws Exception {
        assertEquals(5, evalInt("def f():\n\n    return 5\nf()"));
        assertEquals(5, evalInt("def f():\n    # a comment\n    return 5\nf()"));
    }

    @Test
    void backslashLineContinuation() throws Exception {
        assertEquals(3, evalInt("1 + \\\n    2"));
    }

    @Test
    void chainedComparison() throws Exception {
        assertTrue(evalBool("1 < 2 < 3"));
        assertFalse(evalBool("3 < 2 < 1"));
        assertTrue(evalBool("0 <= 0 <= 10"));
    }

    @Test
    void negativeIndex() throws Exception {
        assertEquals(30, evalInt("[10, 20, 30][-1]"));
        assertEquals(20, evalInt("[10, 20, 30][-2]"));
        assertEquals("c", evalString("'abc'[-1]"));
    }

    @Test
    void fStringWithSpecialCharsInInterpolation() throws Exception {
        // ':' inside a quoted string within an interpolation must not terminate it (audit A3)
        assertEquals("a:b", evalString("f\"{'a:b'}\""));
    }

    @Test
    void trailingCommaInCallAndLiteral() throws Exception {
        assertEquals(6, evalInt("sum([1, 2, 3,])"));
    }

    @Test
    void moreBuiltinsAndKwargs() throws Exception {
        assertTrue(evalBool("any([0, 0, 10])"));
        assertTrue(evalBool("all([1, 1, 1])"));
        assertFalse(evalBool("all([1, 0, 1])"));
        assertEquals(3, evalInt("sorted([3, 1, 2], reverse=True)[0]"));
        String js = py("print(a, b, sep=':')");
        assertTrue(js.contains(".join(\":\")"), "print sep kwarg → join: " + js);
    }

    @Test
    void slicing() throws Exception {
        assertEquals(2, evalInt("[1, 2, 3, 4][1:3][0]"));
        assertEquals(3, evalInt("sum([1, 2, 3, 4][:2])"));
        assertEquals("el", evalString("'hello'[1:3]"));
        assertEquals("cba", evalString("'abc'[::-1]"));
        assertEquals(3, evalInt("[1, 2, 3][::-1][0]"));
    }

    @Test
    void methodMappings() throws Exception {
        assertEquals("hi", evalString("'HI'.lower()"));
        assertEquals("HI", evalString("'hi'.upper()"));
        assertEquals("x", evalString("'  x  '.strip()"));
        assertEquals(3, evalInt("len('a,b,c'.split(','))"));
        assertEquals("xbc", evalString("'abc'.replace('a', 'x')"));
        assertEquals(2, evalInt("len({'a': 1, 'b': 2}.keys())"));
        assertEquals(2, evalInt("{'a': 1, 'b': 2}.get('b')"));
        assertEquals(9, evalInt("{'a': 1}.get('z', 9)"));
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

    @Test
    void sourceMapIsEmittedAndWellFormed() throws Exception {
        var result = compiler.compileDetailed(Path.of("test.py"), "x = 1\n");
        assertNotNull(result.sourceMap(), "compileDetailed must emit a source map");
        var obj = com.google.gson.JsonParser.parseString(result.sourceMap()).getAsJsonObject();
        assertEquals(3, obj.get("version").getAsInt());
        assertTrue(obj.get("sources").getAsJsonArray().get(0).getAsString().contains("test.py"));
        assertFalse(obj.get("mappings").getAsString().isEmpty(), "mappings must be non-empty");
        assertNotNull(obj.get("sourcesContent"), "sourcesContent (original source) must be present");
    }

    @Test
    void sourceMapMapsStatementsToPythonLines() throws Exception {
        // Two top-level statements on Python lines 1 and 2 → JS lines 0 and 1, mapped back.
        var result = compiler.compileDetailed(Path.of("test.py"), "x = 1\ny = 2\n");
        var obj = com.google.gson.JsonParser.parseString(result.sourceMap()).getAsJsonObject();
        var genToOrig = decodeMappings(obj.get("mappings").getAsString());
        assertEquals(0, genToOrig.get(0), "JS line 0 ← Python line 1 (origLine 0)");
        assertEquals(1, genToOrig.get(1), "JS line 1 ← Python line 2 (origLine 1)");
    }

    @Test
    void sourceMapMapsFunctionDefToItsLine() throws Exception {
        // def on line 1; the emitted `function f(...) {` (JS line 0) must map back to Python line 1.
        var result = compiler.compileDetailed(Path.of("test.py"), "def f(x):\n    return x\nf(1)\n");
        var obj = com.google.gson.JsonParser.parseString(result.sourceMap()).getAsJsonObject();
        var genToOrig = decodeMappings(obj.get("mappings").getAsString());
        assertEquals(0, genToOrig.get(0), "the function header line must map to Python line 1");
    }

    /** Minimal v3 source-map mappings decoder → generated line (0-based) → original line (0-based). */
    private static java.util.Map<Integer, Integer> decodeMappings(String mappings) {
        java.util.Map<Integer, Integer> out = new java.util.HashMap<>();
        String[] lines = mappings.split(";", -1);
        int srcIdx = 0, origLine = 0, origCol = 0;
        for (int genLine = 0; genLine < lines.length; genLine++) {
            String line = lines[genLine];
            int genCol = 0;
            if (!line.isEmpty()) {
                for (String seg : line.split(",")) {
                    int[] vals = decodeVlqSegment(seg);
                    genCol += vals[0];
                    if (vals.length >= 4) {
                        srcIdx += vals[1];
                        origLine += vals[2];
                        origCol += vals[3];
                        out.put(genLine, origLine);
                    }
                }
            }
        }
        return out;
    }

    private static final String BASE64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private static int[] decodeVlqSegment(String seg) {
        java.util.List<Integer> vals = new java.util.ArrayList<>();
        for (int i = 0; i < seg.length(); ) {
            int shift = 0, v = 0, digit;
            do {
                digit = BASE64.indexOf(seg.charAt(i++));
                v |= (digit & 0x1f) << shift;
                shift += 5;
            } while ((digit & 0x20) != 0);
            boolean neg = (v & 1) != 0;
            vals.add(neg ? -(v >>> 1) : (v >>> 1));
        }
        return vals.stream().mapToInt(Integer::intValue).toArray();
    }
}
