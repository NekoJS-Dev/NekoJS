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

    /**
     * Strips the trailing {@code export { ... };} block the emitter adds so every .py file is
     * importable. ESM {@code export} cannot appear in a script-mode {@code eval}; the completion
     * value we assert comes from the preceding expression statements and is unaffected. Import-free
     * modules (and bare expression scripts) have no export block, so this is a no-op there. This
     * mirrors {@link CompilerExecutionAssertions#evalAutomatic}, which strips a JSX runtime import
     * line for the same reason.
     */
    private static String asScript(String js) {
        return js.replaceFirst("\\nexport \\{[^}]*\\};\\s*$", "");
    }

    private long evalInt(String src) throws Exception {
        try (var eval = CompilerExecutionAssertions.eval(asScript(py(src)))) {
            return eval.value().asLong();
        }
    }

    private String evalString(String src) throws Exception {
        try (var eval = CompilerExecutionAssertions.eval(asScript(py(src)))) {
            return eval.value().asString();
        }
    }

    private boolean evalBool(String src) throws Exception {
        try (var eval = CompilerExecutionAssertions.eval(asScript(py(src)))) {
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
    void classBodyAssignBecomesStaticField() throws Exception {
        // class C: x = 5 → ES2022 static class field (`var`/plain statements are invalid inside a
        // JS class body); the attribute is readable as C.x like a Python class attribute.
        String js = py("class C:\n    x = 5");
        assertTrue(js.contains("static x = 5;"), "class-body assign → static field: " + js);
        assertEquals(5, evalInt("class C:\n    x = 5\nC.x"));
    }

    @Test
    void classDocstringIsDroppedToComment() throws Exception {
        // A bare string statement (docstring) is illegal inside a JS class body; it is lowered to a
        // comment (Python docstrings have no runtime effect either).
        String js = py("class C:\n    'doc'\n    x = 1");
        assertTrue(js.contains("// docstring: doc"), "docstring → comment: " + js);
        assertFalse(js.contains("\"doc\";"), "docstring must not become an expression statement: " + js);
        assertEquals(1, evalInt("class C:\n    'doc'\n    x = 1\nC.x"));
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
    void importEmitsEsmModuleSyntax() throws Exception {
        // import X → ESM namespace import; NekoModuleResolver probes ./X.py / .js / index.* automatically.
        String js = py("import Item");
        assertTrue(js.contains("import * as Item from './Item';"), "import X → ESM namespace import: " + js);
        js = py("from utils import helper");
        assertTrue(js.contains("import { helper } from './utils';"), "from X import a → ESM named import: " + js);
        js = py("import Foo as Bar");
        assertTrue(js.contains("import * as Bar from './Foo';"), "import X as Y → ESM aliased namespace: " + js);
    }

    @Test
    void dottedImportMapsToRelativePath() throws Exception {
        // a.b.c → ./a/b/c (dotted package path → path segments); leaf segment is the local binding.
        String js = py("import pkg.helpers.math");
        assertTrue(js.contains("from './pkg/helpers/math'"), "dotted module → slash path: " + js);
        assertTrue(js.contains("import * as math"), "dotted import binds the leaf segment: " + js);
        js = py("from a.b import c");
        assertTrue(js.contains("import { c } from './a/b'"), "from a.b import c → named from ./a/b: " + js);
    }

    @Test
    void fromImportMultipleAndAlias() throws Exception {
        String js = py("from utils import a, b");
        assertTrue(js.contains("import { a, b } from './utils';"), "multiple names in one import: " + js);
        js = py("from utils import a as x, b");
        assertTrue(js.contains("import { a as x, b } from './utils';"), "aliased named import: " + js);
    }

    @Test
    void fromImportStarStillUnsupported() {
        // ESM cannot splat a namespace into the current scope, so 'from X import *' stays rejected.
        assertThrows(IllegalArgumentException.class, () -> py("from utils import *"));
    }

    @Test
    void moduleExportsDefinedTopLevelNames() throws Exception {
        // A .py module re-exports every top-level def/class/assign so siblings can import it.
        String js = py("def f():\n    return 1\nclass C:\n    pass\nx = 5");
        assertTrue(js.contains("export { f, C, x };"), "top-level def/class/assign names are exported: " + js);
    }

    @Test
    void bareExpressionScriptHasNoExportBlock() throws Exception {
        // A module that defines no names (e.g. a single expression) stays plain JS, not ESM.
        String js = py("2 + 3");
        assertFalse(js.contains("export"), "no defined names → no export block: " + js);
    }

    @Test
    void importsAreHoistedAboveStatements() throws Exception {
        // ESM requires import declarations before other statements; an import after code still
        // surfaces to the top of the emitted module.
        String js = py("x = 1\nimport utils\ny = 2");
        int importIdx = js.indexOf("import * as utils");
        int stmtIdx = js.indexOf("var x = 1");
        assertTrue(importIdx >= 0 && stmtIdx >= 0, "both import and statement must be present: " + js);
        assertTrue(importIdx < stmtIdx, "import must precede statements: " + js);
    }

    @Test
    void raiseEmitsThrow() throws Exception {
        String js = py("raise ValueError('boom')");
        assertTrue(js.contains("throw "), "raise Expr → throw Expr: " + js);
        // raise MyErr(42) → throw new MyErr(42); except MyErr matches via instanceof, e binds the value.
        String src = """
                class MyErr:
                    def __init__(self, v):
                        self.v = v
                try:
                    raise MyErr(42)
                except MyErr as e:
                    e.v
                """;
        assertEquals(42, evalInt(src));
    }

    @Test
    void bareRaiseIsUnsupported() {
        assertThrows(IllegalArgumentException.class, () -> py("raise"));
    }

    @Test
    void multipleExceptClausesMatchInOrder() throws Exception {
        // except clauses lower to an instanceof chain; the first match wins, like Python.
        String src = """
                class A:
                    pass
                class B:
                    pass
                def classify(err):
                    try:
                        raise err
                    except A:
                        return 'a'
                    except B:
                        return 'b'
                classify(A()) + classify(B())
                """;
        assertEquals("ab", evalString(src));
    }

    @Test
    void unmatchedExceptionRethrowsToOuterCatch() throws Exception {
        // A thrown value matching no except clause is rethrown (Python semantics).
        String src = """
                got = 'none'
                class A:
                    pass
                class B:
                    pass
                try:
                    try:
                        raise A()
                    except B:
                        got = 'inner'
                except:
                    got = 'rethrown'
                got
                """;
        assertEquals("rethrown", evalString(src));
    }

    @Test
    void bareExceptCatchesEverything() throws Exception {
        // A bare except catches any thrown value (even a non-Error like 42).
        assertEquals(99, evalInt("try:\n    raise 42\nexcept:\n    99"));
    }

    @Test
    void exceptWithParenthesizedTypeTuple() throws Exception {
        // except (A, B) as e → e instanceof A || e instanceof B.
        String src = """
                class A:
                    pass
                class B:
                    pass
                try:
                    raise B()
                except (A, B) as e:
                    'matched'
                """;
        assertEquals("matched", evalString(src));
    }

    @Test
    void builtinExceptionNamesMapToPreludeClasses() throws Exception {
        // Builtin exception names now map to real classes from the exception prelude (no longer to
        // plain Error): `except Exception` matches instanceof Error (every prelude class extends
        // Error), and specific types match via __nekoExcIs (instanceof + JS-native Error .name).
        String js = py("try:\n    pass\nexcept Exception as e:\n    pass");
        assertTrue(js.contains("instanceof Error"), "Exception → instanceof Error: " + js);
        js = py("try:\n    pass\nexcept (ValueError, TypeError):\n    pass");
        assertTrue(js.contains("__nekoExcIs(__nekoErr, ValueError) || __nekoExcIs(__nekoErr, TypeError)"),
                "parenthesized builtin types map to __nekoExcIs disjuncts: " + js);
        assertTrue(js.contains("class Exception extends Error"),
                "prelude must define Exception extends Error before subclasses: " + js);
        assertTrue(js.contains("class ValueError extends Exception"),
                "prelude must define ValueError: " + js);
    }

    @Test
    void bareExceptMustBeLast() {
        assertThrows(IllegalArgumentException.class,
                () -> py("try:\n    pass\nexcept:\n    pass\nexcept ValueError:\n    pass"));
    }

    @Test
    void decoratorWrapsTopLevelFunction() throws Exception {
        // @double / def base → base = double(base); calling base() runs the wrapper.
        String src = """
                def double(f):
                    def g():
                        return f() + 1
                    return g
                @double
                def base():
                    return 41
                base()
                """;
        assertEquals(42, evalInt(src));
    }

    @Test
    void multipleDecoratorsApplyBottomUp() throws Exception {
        // @a / @b / def f → f = a(b(f)); nearest decorator (b) applied first.
        String src = """
                def add1(f):
                    return lambda: f() + 1
                def add10(f):
                    return lambda: f() + 10
                @add1
                @add10
                def base():
                    return 0
                base()
                """;
        // add10(base) = ()=>0+10 = 10; add1(that) = ()=>10+1 = 11
        assertEquals(11, evalInt(src));
    }

    @Test
    void classDecoratorWrapsClass() throws Exception {
        String src = """
                def tag(cls):
                    cls.tag = 'x'
                    return cls
                @tag
                class C:
                    def __init__(self):
                        self.v = 5
                C().v
                """;
        assertEquals(5, evalInt(src));
    }

    @Test
    void decoratorWithArgumentsIsRejected() {
        // The parser rejects @deco(...) call-form decorators (args would be silently dropped otherwise).
        assertThrows(IllegalArgumentException.class, () -> py("@deco(1)\ndef f():\n    pass"));
    }

    // ---- generators / yield ----

    @Test
    void generatorYieldsCollectedByList() throws Exception {
        // def g(): yield 1; yield 2 → function* g() {...}; list(g()) spreads the iterator.
        String src = """
                def g():
                    yield 10
                    yield 20
                list(g())[1]
                """;
        String js = py(src);
        assertTrue(js.contains("function* g"), "yield makes the function a generator: " + js);
        assertTrue(js.contains("yield 20"), "yield emits a JS yield: " + js);
        assertEquals(20, evalInt(src));
    }

    @Test
    void generatorIteratedInForLoop() throws Exception {
        String src = """
                def g():
                    yield 1
                    yield 2
                    yield 3
                total = 0
                for v in g():
                    total += v
                total
                """;
        assertEquals(6, evalInt(src));
    }

    @Test
    void generatorWithLoopBody() throws Exception {
        // yield i*i for i in range(4) → 0,1,4,9; index [2] = 4.
        String src = """
                def squares(n):
                    for i in range(n):
                        yield i * i
                list(squares(4))[2]
                """;
        assertEquals(4, evalInt(src));
    }

    @Test
    void yieldFromDelegatesToInnerGenerator() throws Exception {
        // yield from delegates to another iterable: b() yields 1,2 then 3.
        String src = """
                def a():
                    yield 1
                    yield 2
                def b():
                    yield from a()
                    yield 3
                len(list(b()))
                """;
        String js = py(src);
        assertTrue(js.contains("yield* "), "yield from → JS yield*: " + js);
        assertEquals(3, evalInt(src));
    }

    @Test
    void generatorExpressionYieldsLazily() throws Exception {
        // (x for x in xs) lowers to an IIFE generator; sum/any consume it via spread.
        String src = "sum(x * x for x in range(4))";
        String js = py(src);
        assertTrue(js.contains("function*"), "generator expression → function*: " + js);
        assertEquals(14, evalInt(src));   // 0+1+4+9
        assertEquals(3, evalInt("list(x for x in range(10) if x % 3 == 0)[1]"));   // 0,3,6,9 → [1]=3
        assertTrue(evalBool("any(x > 2 for x in [1, 2, 3])"));
    }

    // ---- extended builtins ----

    @Test
    void isinstanceAndType() throws Exception {
        String src = """
                class C:
                    pass
                class D:
                    pass
                c = C()
                isinstance(c, C) and not isinstance(c, D)
                """;
        assertTrue(evalBool(src));
        // type(x) → x.constructor; instances of the same class share a constructor
        assertTrue(evalBool("class K:\n    pass\nK().constructor is K().constructor"));
    }

    @Test
    void isinstanceWithBuiltinExceptionNames() throws Exception {
        // Builtin exception names resolve to prelude classes, so isinstance checks the real class
        // hierarchy (ValueError extends Exception; KeyError extends LookupError) and falls back to
        // a JS-native Error .name match for errors thrown by the JS runtime itself.
        String js = py("isinstance(ValueError('x'), ValueError)");
        assertTrue(js.contains("__nekoExcIs"), "builtin exception isinstance → __nekoExcIs: " + js);
        assertTrue(evalBool("isinstance(ValueError('x'), ValueError)"));
        assertTrue(evalBool("isinstance(ValueError('x'), (ValueError, TypeError))"));
        assertTrue(evalBool("isinstance(ValueError('x'), [ValueError, TypeError])"));
        assertFalse(evalBool("isinstance(1, ValueError)"));
        // follows the prelude hierarchy, not a blanket Error mapping
        assertTrue(evalBool("isinstance(ValueError('x'), Exception)"));
        assertFalse(evalBool("isinstance(ValueError('x'), KeyError)"));
        assertTrue(evalBool("isinstance(KeyError('k'), LookupError)"));
    }

    @Test
    void hexOctBinRepr() throws Exception {
        assertEquals("0xff", evalString("hex(255)"));
        assertEquals("-0x1", evalString("hex(-1)"));
        assertEquals("0o17", evalString("oct(15)"));
        assertEquals("0b1010", evalString("bin(10)"));
        assertEquals("\"hi\"", evalString("repr('hi')"));
    }

    @Test
    void roundAndDivmod() throws Exception {
        assertEquals(4, evalInt("round(3.6)"));
        assertEquals(314, evalInt("round(3.1415, 2) * 100"));   // 3.14 * 100 = 314
        assertEquals(3, evalInt("divmod(17, 5)[0]"));
        assertEquals(2, evalInt("divmod(17, 5)[1]"));
    }

    @Test
    void mapFilterReversedZip() throws Exception {
        // map/filter take the function first (like Python); reversed/zip return arrays.
        assertEquals(4, evalInt("list(map(lambda x: x * 2, [1, 2]))[1]"));         // [2,4][1]=4
        assertEquals(2, evalInt("len(list(filter(lambda x: x > 1, [1, 2, 3])))")); // [2,3]
        assertEquals(3, evalInt("list(reversed([1, 2, 3]))[0]"));                   // [3,2,1]
        assertEquals(2, evalInt("len(list(zip([1, 2], [3, 4])))"));                 // [(1,3),(2,4)]
    }

    @Test
    void formatBuiltinUsesHelper() throws Exception {
        String js = py("format(3.14159, '.2f')");
        assertTrue(js.contains("__nekoFmt"), "format() reuses the format helper: " + js);
        assertEquals("3.14", evalString("format(3.14159, '.2f')"));
    }

    @Test
    void sumAnyAllAcceptIterables() throws Exception {
        // sum/any/all now spread first, so they work on iterables (e.g. a generator).
        String src = """
                def g():
                    yield 1
                    yield 2
                    yield 3
                sum(g())
                """;
        assertEquals(6, evalInt(src));
        assertTrue(evalBool("any([0, 0, 2])"));
        assertTrue(evalBool("all([1, 1])"));
    }

    // ---- match / case (structural pattern matching) ----

    @Test
    void matchLiteralAndWildcard() throws Exception {
        String src = """
                def classify(n):
                    match n:
                        case 0:
                            return 'zero'
                        case 1 | 2 | 3:
                            return 'low'
                        case _:
                            return 'other'
                classify(0) + classify(2) + classify(9)
                """;
        assertEquals("zerolowother", evalString(src));
    }

    @Test
    void matchCaptureAndGuard() throws Exception {
        String src = """
                def sign(n):
                    match n:
                        case 0:
                            return 'Z'
                        case x if x > 0:
                            return 'P'
                        case x:
                            return 'N'
                sign(0) + sign(5) + sign(-3)
                """;
        assertEquals("ZPN", evalString(src));
    }

    @Test
    void matchSequencePattern() throws Exception {
        String src = """
                def head(xs):
                    match xs:
                        case []:
                            return 'empty'
                        case [first, *rest]:
                            return first
                head([]) + head([7, 8, 9])
                """;
        assertEquals("empty7", evalString(src));
    }

    @Test
    void matchMappingPattern() throws Exception {
        String src = """
                def name_of(d):
                    match d:
                        case {'name': n, **rest}:
                            return n
                        case _:
                            return 'unknown'
                name_of({'name': 'neko', 'age': 2}) + name_of({'x': 1})
                """;
        assertEquals("nekounknown", evalString(src));
    }

    @Test
    void matchClassPattern() throws Exception {
        String src = """
                class Point:
                    def __init__(self, x, y):
                        self.x = x
                        self.y = y
                def describe(p):
                    match p:
                        case Point(x=0, y=0):
                            return 'origin'
                        case Point(x=px):
                            return 'x=' + str(px)
                        case _:
                            return 'other'
                describe(Point(0, 0)) + '|' + describe(Point(5, 0))
                """;
        assertEquals("origin|x=5", evalString(src));
    }

    @Test
    void matchIsSoftKeywordAsIdentifier() throws Exception {
        // 'match' used as a variable name must not be mistaken for a match-statement.
        assertEquals(5, evalInt("match = 5\nmatch"));
    }

    // ---- attribute builtins ----

    @Test
    void getattrHasattrSetattr() throws Exception {
        String src = """
                class C:
                    def __init__(self):
                        self.x = 5
                c = C()
                a = getattr(c, 'x')
                setattr(c, 'y', 9)
                b = getattr(c, 'missing', 7)
                h = hasattr(c, 'y')
                str(a) + str(c.y) + str(b) + str(h)
                """;
        assertEquals("597true", evalString(src));
    }

    @Test
    void iterNextOverIterator() throws Exception {
        // next(it) / iter(x) interop with generators (GraalJS iterators).
        String src = """
                def g():
                    yield 1
                    yield 2
                it = iter(g())
                next(it) + next(it)
                """;
        assertEquals(3, evalInt(src));
    }

    // ---- @classmethod / @property ----

    @Test
    void classmethodReceivesClass() throws Exception {
        String src = """
                class Counter:
                    @classmethod
                    def reveal(cls):
                        return cls
                Counter.reveal() is Counter
                """;
        String js = py(src);
        assertTrue(js.contains("static reveal()"), "@classmethod → static method: " + js);
        assertTrue(evalBool(src));
    }

    @Test
    void propertyActsAsGetter() throws Exception {
        String src = """
                class Rect:
                    def __init__(self, w, h):
                        self.w = w
                        self.h = h
                    @property
                    def area(self):
                        return self.w * self.h
                Rect(3, 4).area
                """;
        String js = py(src);
        assertTrue(js.contains("get area()"), "@property → getter: " + js);
        assertEquals(12, evalInt(src));
    }

    // ---- for/else & while/else ----

    @Test
    void forElseRunsWhenNoBreak() throws Exception {
        // else runs if the loop finishes without break (the canonical "not found" pattern).
        String src = """
                def find(xs, target):
                    for x in xs:
                        if x == target:
                            return 'found'
                    else:
                        return 'missing'
                find([1, 2, 3], 2) + find([1, 2, 3], 9)
                """;
        assertEquals("foundmissing", evalString(src));
    }

    @Test
    void forElseSkippedOnBreak() throws Exception {
        // An explicit break in the body suppresses the else.
        String src = """
                state = ''
                for i in range(5):
                    if i == 2:
                        break
                else:
                    state = 'completed'
                state + '|' + str(i)
                """;
        // break at i==2 → state stays '' ; i is 2 → '|2'
        assertEquals("|2", evalString(src));
    }

    @Test
    void whileElseRunsWhenConditionFails() throws Exception {
        String src = """
                n = 0
                while n < 3:
                    n += 1
                else:
                    n = 100
                n
                """;
        assertEquals(100, evalInt(src));
    }

    @Test
    void whileElseSkippedOnBreak() throws Exception {
        String src = """
                n = 0
                hit = 'no'
                while n < 10:
                    n += 1
                    if n == 2:
                        break
                else:
                    hit = 'yes'
                hit
                """;
        assertEquals("no", evalString(src));
    }

    // ---- iterable unpacking (*args / **kw / [1,*xs] / {**a}) ----

    @Test
    void starredCallArgsSpread() throws Exception {
        // f(*args) → f(...args); previously the '*' was silently dropped (bug).
        String src = """
                def add(a, b, c):
                    return a + b + c
                nums = [10, 20, 30]
                add(*nums)
                """;
        String js = py(src);
        assertTrue(js.contains("...nums"), "f(*args) → spread: " + js);
        assertEquals(60, evalInt(src));
        assertEquals(6, evalInt("max(*[1, 2, 3], *[4, 5, 6])"));   // multiple *spreads
    }

    @Test
    void dictSpreadKwargsMerge() throws Exception {
        // f(**a, b=2) → trailing object merges the spread + explicit kwargs.
        String src = """
                def g(**kw):
                    return kw
                base = {'x': 1, 'y': 2}
                g(**base, z=3)['z'] + g(**base, z=3)['x']
                """;
        assertEquals(4, evalInt(src));   // 3 + 1
    }

    @Test
    void listAndDictLiteralSpread() throws Exception {
        assertEquals(10, evalInt("sum([1, *[2, 3], 4])"));         // [1,2,3,4] → 10
        // {**a, **b} merges; 'c' present in both → later spread (b) overrides
        String src = "d = {**{'a': 1, 'c': 9}, **{'b': 2, 'c': 5}}\nd['a'] + d['b'] + d['c']";
        assertEquals(8, evalInt(src));   // 1 + 2 + 5
    }

    @Test
    void minMaxSortedWithKey() throws Exception {
        // key= picks the extremum / orders by a function.
        assertEquals("ccc", evalString("max(['bb', 'a', 'ccc'], key=lambda s: len(s))"));
        assertEquals("a", evalString("min(['bb', 'a', 'ccc'], key=lambda s: len(s))"));
        // sorted by key=-x ascending → keys -3,-2,-1 → x 3,2,1 → [0]=3
        assertEquals(3, evalInt("sorted([3, 1, 2], key=lambda x: -x)[0]"));
    }

    // ---- assert / del / walrus / annotations / try-else / bare raise ----

    @Test
    void assertPassesAndFails() throws Exception {
        assertEquals(1, evalInt("assert True\n1"));
        // assert False raises → caught by bare except (Error mapping)
        assertEquals(99, evalInt("try:\n    assert False\nexcept:\n    99"));
        // assert with message
        String src = """
                try:
                    assert 1 == 2, 'mismatch'
                except Exception as e:
                    e.message
                """;
        // JS Error carries .message; AssertionError maps to Error
        assertEquals("mismatch", evalString(src));
    }

    @Test
    void delRemovesDictKey() throws Exception {
        // del d[k] → delete d[k]; subsequent get returns undefined → default 0.
        String src = """
                d = {'a': 1, 'b': 2}
                del d['a']
                d.get('a', 0)
                """;
        assertEquals(0, evalInt(src));
    }

    @Test
    void delNameIsUnsupported() {
        // `delete x;` is a SyntaxError in ESM strict mode (and JS var bindings cannot be unbound),
        // so del on a plain name is a compile-time error; only del d[k] / del obj.attr are supported.
        assertThrows(IllegalArgumentException.class, () -> py("x = 1\ndel x"));
    }

    @Test
    void delAttributeStillWorks() throws Exception {
        // del obj.attr → delete obj.attr; (valid in strict mode, unlike `delete name`).
        String src = """
                o = {'a': 1, 'b': 2}
                del o.a
                o.get('a', 0)
                """;
        assertEquals(0, evalInt(src));
    }

    @Test
    void walrusAssignsAndYields() throws Exception {
        // (n := 5) assigns n and yields 5; n is 5 afterwards.
        String src = """
                (n := 5)
                n + n
                """;
        String js = py(src);
        assertTrue(js.contains("(n = 5)"), "walrus → (name = value): " + js);
        assertEquals(10, evalInt(src));
    }

    @Test
    void walrusInComprehension() throws Exception {
        // capture length once per item via walrus, reuse in the filter and element.
        String src = "len([y for s in ['a', 'bb', 'ccc'] if (y := len(s)) > 1])";
        assertEquals(2, evalInt(src));   // 'bb'(2), 'ccc'(3) pass; 'a'(1) filtered
    }

    @Test
    void variableAnnotationIsErased() throws Exception {
        // x: int = 5 lowers to a plain assignment; the annotation is discarded.
        String src = "x: int = 5\nx";
        String js = py(src);
        assertFalse(js.contains("int"), "annotation must be erased: " + js);
        assertEquals(5, evalInt(src));
    }

    @Test
    void tryElseRunsWhenNoException() throws Exception {
        // else runs only when the try body flows off the end without an exception (NOT via return,
        // which would skip it — matching Python semantics).
        String src = """
                def classify(x):
                    result = ''
                    try:
                        if x < 0:
                            raise ValueError('neg')
                        result = 'ok'
                    except ValueError:
                        result = 'err'
                    else:
                        result = 'else'
                    return result
                classify(5) + classify(-1)
                """;
        // x=5: no raise, flows off end → else runs ('else'); x=-1: raised+handled → 'err' → 'elseerr'
        assertEquals("elseerr", evalString(src));
    }

    @Test
    void bareRaiseRethrows() throws Exception {
        // bare raise inside except rethrows the current exception to the outer catch.
        String src = """
                log = ''
                try:
                    try:
                        raise ValueError('inner')
                    except ValueError:
                        log = 'caught'
                        raise
                except:
                    log + ':rethrown'
                """;
        assertEquals("caught:rethrown", evalString(src));
    }

    // ---- f-string format specifiers & conversions ----

    @Test
    void fStringFixedPointFormat() throws Exception {
        assertEquals("3.14", evalString("f'{3.14159:.2f}'"));
        assertEquals("3.1", evalString("f'{3.14:.1f}'"));
        assertEquals("25%", evalString("f'{0.25:.0%}'"));
    }

    @Test
    void fStringWidthAndAlignment() throws Exception {
        assertEquals("    hi", evalString("f'{\"hi\":>6}'"));
        assertEquals("hi    ", evalString("f'{\"hi\":<6}'"));
        assertEquals("  hi  ", evalString("f'{\"hi\":^6}'"));
        assertEquals("00000042", evalString("f'{42:08d}'"));
    }

    @Test
    void fStringBaseAndThousands() throws Exception {
        assertEquals("ff", evalString("f'{255:x}'"));
        assertEquals("FF", evalString("f'{255:X}'"));
        assertEquals("1,234", evalString("f'{1234:,}'"));
        assertEquals("1010", evalString("f'{10:b}'"));
    }

    @Test
    void fStringConversionAndPrecision() throws Exception {
        assertEquals("hel", evalString("f'{\"hello\":.3}'"));
        // !r conversion → JSON.stringify on a string yields a quoted form
        assertTrue(evalString("f'{\"abc\"!r}'").contains("abc"));
    }

    @Test
    void fStringPlainInterpolationUnchanged() throws Exception {
        // No spec → still a plain template literal, no __nekoFmt helper needed.
        String js = py("f'{1 + 1}'");
        assertFalse(js.contains("__nekoFmt"), "plain f-string must not need the format helper: " + js);
        assertEquals("2", evalString("f'{1 + 1}'"));
    }

    // ---- slicing with arbitrary step ----

    @Test
    void positiveStepEveryOther() throws Exception {
        assertEquals(9, evalInt("sum([1, 2, 3, 4, 5][::2])"));          // [1,3,5]
        assertEquals("aceg", evalString("'abcdefgh'[::2]"));
        assertEquals(2, evalInt("len([1, 2, 3, 4, 5][1::2])"));          // [2,4]
        assertEquals("bd", evalString("'abcdef'[1:5:2]"));               // indices 1,3
    }

    @Test
    void negativeStepReversesAndSkips() throws Exception {
        assertEquals(5, evalInt("[1, 2, 3, 4, 5][::-2][0]"));            // [5,3,1]
        assertEquals("dcb", evalString("'abcde'[3:0:-1]"));              // indices 3,2,1
        assertEquals("cba", evalString("'abc'[::-1]"));                  // regression: still reverses
    }

    // ---- **kwargs / keyword call args ----

    @Test
    void kwargsCollectedIntoDict() throws Exception {
        // def f(**kw): kw → keyword args gather into the kw dict.
        String src = """
                def f(**kw):
                    return kw
                f(a=1, b=2)['b']
                """;
        assertEquals(2, evalInt(src));
    }

    @Test
    void kwargsAlongsidePositionalAndDefaults() throws Exception {
        // positional beats keyword beats default; remaining kwargs land in the kw dict.
        String src = """
                def g(x, y=10, *rest, **kw):
                    return str(x) + str(y) + str(len(rest)) + str(kw.get('z'))
                g(1, y=2, z=5, w=9)
                """;
        // x=1 (positional), y=2 (keyword, beats default 10), rest=[] (0), kw={'z':5,'w':9}; z→5
        assertEquals("1205", evalString(src));
    }

    @Test
    void kwargsClassInitAndMethod() throws Exception {
        // A class whose __init__ declares **kwargs; constructed and called with keyword args.
        String src = """
                class Bag:
                    def __init__(self, **items):
                        self.items = items
                    def total(self, **opts):
                        s = 0
                        for k in self.items.keys():
                            s += self.items[k]
                        return s + opts.get('bonus', 0)
                b = Bag(apple=1, banana=2)
                b.total(bonus=10)
                """;
        // items apple=1, banana=2 → 3; bonus=10 → 13
        assertEquals(13, evalInt(src));
    }

    @Test
    void kwargsToNonKwFunctionRejected() {
        // Keyword args require the target to declare **kwargs (or be print/sorted).
        assertThrows(IllegalArgumentException.class, () -> py("def f(a):\n    return a\nf(a=1)"));
    }

    @Test
    void superWithKwargsIsRejected() {
        // super().__init__(a=1) used to silently drop the kwarg; kwargs-to-super is unsupported
        // (JS super() takes positionals only) → clear compile-time error instead of wrong behaviour.
        String src = """
                class A:
                    def __init__(self, a):
                        self.a = a
                class B(A):
                    def __init__(self):
                        super().__init__(a=1)
                """;
        assertThrows(IllegalArgumentException.class, () -> py(src));
    }

    @Test
    void printAndSortedKwargsStillWork() throws Exception {
        // Regression: print(sep=) / sorted(reverse=) keep their special-cased handling.
        assertEquals(3, evalInt("sorted([3, 1, 9, 2], reverse=False)[2]"));
        String js = py("print(a, b, sep=':')");
        assertTrue(js.contains(".join(\":\")"), js);
    }

    // ---- multi-clause comprehensions (nested for / multiple if) ----

    @Test
    void nestedForClausesFlatten() throws Exception {
        // [(i,j) for i in range(2) for j in range(2)] → 4 pairs; len 4.
        String src = "len([(i, j) for i in range(2) for j in range(2)])";
        String js = py(src);
        assertTrue(js.contains(".flatMap("), "multiple for-clauses → flatMap: " + js);
        assertEquals(4, evalInt(src));
    }

    @Test
    void nestedForSumProducts() throws Exception {
        // sum of i*j over a 3x3 table = 36
        String src = "sum([i * j for i in range(1, 4) for j in range(1, 4)])";
        assertEquals(36, evalInt(src));
    }

    @Test
    void multipleIfGuardsInOneFor() throws Exception {
        // [x for x in range(20) if x % 2 == 0 if x % 3 == 0] → 0,6,12,18; len 4
        String src = "len([x for x in range(20) if x % 2 == 0 if x % 3 == 0])";
        String js = py(src);
        assertTrue(js.contains("&&"), "multiple if guards conjoin: " + js);
        assertEquals(4, evalInt(src));
    }

    @Test
    void nestedForWithIfGuards() throws Exception {
        // pythagorean triples with hypotenuse < 6: (3,4,5) only → 1
        String src = """
                len([(a, b, c) for a in range(1, 6) for b in range(a, 6) for c in range(b, 6)
                     if a * a + b * b == c * c])
                """;
        assertEquals(1, evalInt(src));
    }

    @Test
    void dictCompWithNestedFor() throws Exception {
        // {'k'+i+j: 1 for i in range(2) for j in range(2)} → 4 entries "k00","k01","k10","k11"
        // (prefix avoids JS object integer-key reordering so insertion order is preserved)
        String src = "d = {'k' + str(i) + str(j): 1 for i in range(2) for j in range(2)}\nlist(d.keys())[0] + list(d.keys())[3]";
        String js = py(src);
        assertTrue(js.contains("Object.fromEntries"), js);
        assertEquals("k00k11", evalString(src));
    }

    // ---- with statement (context managers) ----

    @Test
    void withBindsValueAndRunsExit() throws Exception {
        // A Python-style context manager: __enter__ returns self, __exit__ runs in finally.
        String src = """
                class CM:
                    def __init__(self):
                        self.entered = False
                        self.exited = False
                    def __enter__(self):
                        self.entered = True
                        return self
                    def __exit__(self):
                        self.exited = True
                cm = CM()
                with cm as obj:
                    obj.entered
                cm.exited
                """;
        String js = py(src);
        assertTrue(js.contains("try {") && js.contains("} finally {"), "with → try/finally: " + js);
        assertTrue(js.contains(".__enter__()"), "with calls __enter__: " + js);
        assertTrue(evalBool(src));
    }

    @Test
    void withWithoutManagerJustBinds() throws Exception {
        // No __enter__/__exit__ → the context value is bound as-is (the common binding case).
        String src = """
                with {'a': 1, 'b': 2} as d:
                    d['b']
                """;
        assertEquals(2, evalInt(src));
    }

    @Test
    void withMultipleItemsNest() throws Exception {
        // with a as x, b as y: → nested acquire/release; both exits run.
        String src = """
                log = []
                class CM:
                    def __init__(self, tag):
                        self.tag = tag
                    def __enter__(self):
                        log.append(self.tag + 'in')
                        return self.tag
                    def __exit__(self):
                        log.append(self.tag + 'out')
                with CM('a') as x, CM('b') as y:
                    log.append(x + y)
                log[0] + log[1] + log[2] + log[3] + log[4]
                """;
        // ain, bin, ab, bout, aout → "ainbinabboutaout"
        assertEquals("ainbinabboutaout", evalString(src));
    }

    @Test
    void withBodyReturnPropagates() throws Exception {
        // return inside with must return from the enclosing function (not be trapped by the lowering).
        String src = """
                class CM:
                    def __enter__(self):
                        return self
                    def __exit__(self):
                        pass
                def f():
                    with CM() as c:
                        return 42
                    return 0
                f()
                """;
        assertEquals(42, evalInt(src));
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
    void chainedMembershipRoutesThroughNekoIn() throws Exception {
        // a in b in c → (a in b) && (b in c)：链式第二段成员判断同样必须经 __nekoIn（旧实现发射
        // 裸 JS `in` —— 对字符串/字典右操作数是运行时 TypeError，且不走 dict 键的 hasOwnProperty 语义）。
        String js = py("'a' in 'abc' in ['abc']");
        assertTrue(js.contains("__nekoIn(\"a\", \"abc\")"), "首段 in 必须经 __nekoIn: " + js);
        assertTrue(js.contains("__nekoIn(\"abc\", [\"abc\"])"), "链式第二段 in 必须也经 __nekoIn: " + js);
        assertTrue(evalBool("'a' in 'abc' in ['abc']"));   // 两段都为真
        assertFalse(evalBool("'z' in 'abc' in ['abc']"));  // 首段为假 → 短路为 False
        // dict 形式：x in d1 in d2 → (x in d1) && (d1 in d2)，两段都走 __nekoIn 的 dict 键判断
        assertFalse(evalBool("'a' in {'a': 1} in {'a': 1}"));
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
    void dictAndSetComprehensions() throws Exception {
        assertEquals(4, evalInt("{str(x): x * x for x in range(3)}.get('2')"));
        assertEquals(3, evalInt("len(list({x * 2 for x in range(3)}))"));
    }

    @Test
    void tryExcept() throws Exception {
        String src = """
                try:
                    a = None
                    b = a.foo
                except:
                    b = 99
                b
                """;
        String js = py(src);
        assertTrue(js.contains("try {") && js.contains("} catch"), "try/except → try/catch: " + js);
        assertEquals(99, evalInt(src));
    }

    @Test
    void tryExceptWithBoundName() throws Exception {
        String src = """
                caught = False
                try:
                    None.foo
                except Exception as e:
                    caught = True
                caught
                """;
        assertTrue(evalBool(src));
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

    // ---- landed-fix regression tests (truthiness / mod / strict eq / repetition / sort / exceptions / dict ops) ----

    @Test
    void emptyContainerIsFalsyInConditions() throws Exception {
        // Python truthiness: [] / {} are falsy even though JS arrays/objects are always truthy;
        // `if not []` must take the truthy branch, and the "" / 0 / None scalars stay falsy.
        String src = """
                hits = []
                if not []:
                    hits.append('list')
                if not {}:
                    hits.append('dict')
                if []:
                    hits.append('WRONG')
                if not '' and not 0 and not None:
                    hits.append('scalars')
                str(hits)
                """;
        String js = py(src);
        assertTrue(js.contains("__nekoTruthy"), "conditions must go through __nekoTruthy: " + js);
        assertEquals("list,dict,scalars", evalString(src));
    }

    @Test
    void moduloFollowsDivisorSign() throws Exception {
        // Python % keeps the divisor's sign; JS % keeps the dividend's (JS: -7 % 2 == -1). The
        // __nekoMod helper normalizes both directions.
        assertEquals(1, evalInt("(-7) % 2"));
        assertEquals(-1, evalInt("7 % -2"));
        String js = py("(-7) % 2");
        assertTrue(js.contains("__nekoMod"), "mod must go through __nekoMod: " + js);
    }

    @Test
    void equalityIsStrictNoTypeCoercion() throws Exception {
        // Python == has no type coercion, so "1" == 1 is False; the emitter must use === / !==
        // (JS loose == would coerce and silently produce True).
        assertFalse(evalBool("\"1\" == 1"));
        assertTrue(evalBool("\"1\" != 1"));
        assertTrue(evalBool("1 == 1"));
        String js = py("\"1\" == 1");
        assertTrue(js.contains("==="), "== must emit ===: " + js);
    }

    @Test
    void sequenceRepetitionOperator() throws Exception {
        // [0] * 4 → [0,0,0,0] and "ab" * 3 → "ababab" (JS * on arrays/strings is silent NaN/0);
        // plain numeric multiply must still work.
        assertEquals("0,0,0,0", evalString("str([0] * 4)"));
        assertEquals("ababab", evalString("\"ab\" * 3"));
        assertEquals(12, evalInt("3 * 4"));
        assertEquals(6, evalInt("len([1, 2] * 3)"));   // [1,2,1,2,1,2]
    }

    @Test
    void bareListSortIsNumericAware() throws Exception {
        // JS default sort is lexicographic ([10,2,1] → [1,10,2]); the bare .sort() call must lower
        // to the same numeric-aware comparator as sorted().
        assertEquals("1,2,10", evalString("str([10, 2, 1].sort())"));
    }

    @Test
    void multipleBaseClassesAreRejected() {
        // v1 supports single inheritance only; extra bases must be a positioned compile error
        // (previously silently dropped, losing parent B).
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> py("class A:\n    pass\nclass B:\n    pass\nclass C(A, B):\n    pass"));
        assertTrue(ex.getMessage().contains("multiple base"), "must mention multiple base: " + ex.getMessage());
    }

    @Test
    void builtinExceptionRaiseCatchPreservesMessage() throws Exception {
        // raise ValueError('boom') → throw new ValueError("boom"); except ValueError matches the
        // prelude class; e.message carries the text. JS Error stringification is "Name: message",
        // so str(e) is "ValueError: boom" (message preserved; Python would give just "boom").
        String src = """
                try:
                    raise ValueError('boom')
                except ValueError as e:
                    e.message + '|' + str(e)
                """;
        String js = py(src);
        assertTrue(js.contains("throw new ValueError"), "raise ValueError(...) → throw new ValueError: " + js);
        assertTrue(js.indexOf("class Exception extends Error") < js.indexOf("class ValueError extends Exception"),
                "Exception must be declared before ValueError in the prelude: " + js);
        assertEquals("boom|ValueError: boom", evalString(src));
    }

    @Test
    void nativeJsErrorMatchesByTypeName() throws Exception {
        // A JS-native TypeError (null.foo) is not an instance of the prelude classes; the
        // __nekoExcIs .name fallback lets `except TypeError` catch it, and a mismatched type
        // (`except ValueError`) must rethrow it to an outer catch.
        String src = """
                caught = False
                try:
                    None.foo
                except TypeError:
                    caught = True
                caught
                """;
        assertTrue(evalBool(src));
        String rethrown = """
                caught = 'none'
                try:
                    try:
                        None.foo
                    except ValueError:
                        caught = 'wrong'
                except TypeError:
                    caught = 'rethrown'
                caught
                """;
        assertEquals("rethrown", evalString(rethrown));
    }

    @Test
    void membershipInDictAndDictLen() throws Exception {
        // 'a' in d must use hasOwnProperty for dict literals (old code emitted .includes → runtime
        // TypeError on plain objects); len(dict) counts own keys.
        assertTrue(evalBool("'a' in {'a': 1}"));
        assertFalse(evalBool("'z' in {'a': 1}"));
        assertTrue(evalBool("'z' not in {'a': 1}"));
        assertTrue(evalBool("len({}) == 0"));
        assertEquals(2, evalInt("len({'a': 1, 'b': 2})"));
        String js = py("'a' in {'a': 1}");
        assertTrue(js.contains("__nekoIn"), "in must go through __nekoIn: " + js);
    }

    @Test
    void forOverDictIteratesKeys() throws Exception {
        // for k in d must iterate a dict literal's KEYS; a plain JS object is not iterable, so the
        // __nekoIter helper normalizes it to Object.keys (old code threw a runtime TypeError).
        String src = """
                d = {'a': 1, 'b': 2, 'c': 3}
                keys = []
                for k in d:
                    keys.append(k)
                str(keys)
                """;
        String js = py(src);
        assertTrue(js.contains("__nekoIter"), "for over a dict must go through __nekoIter: " + js);
        assertEquals("a,b,c", evalString(src));
    }

    @Test
    void emitterErrorIncludesPythonSourceLine() {
        // Emitter-side errors (not parser errors) must carry the offending Python source line so
        // mod authors can locate the failing statement.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> py("x = 1\ndel x"));
        assertTrue(ex.getMessage().contains("python source line 2"), ex.getMessage());
        ex = assertThrows(IllegalArgumentException.class, () -> py("def f():\n    import os"));
        assertTrue(ex.getMessage().contains("python source line 2"), ex.getMessage());
    }

    @Test
    void printMapsToConsoleLog() throws Exception {
        String js = py("print('hello')");
        assertTrue(js.contains("console.log("), "print() must map to console.log: " + js);
    }

    // ---- defect-fix regression tests (bare class raise / filter truthiness / sort hijack /
    //      zero division / elif source lines) ----

    @Test
    void raiseBareUserClassIsInstantiated() throws Exception {
        // raise Cls ≡ raise Cls()：裸用户类名必须实例化后抛出（旧实现 throw MyError 抛出类对象
        // 本身，except MyError 的 instanceof 永远不匹配 → 未捕获崩溃）。
        String src = """
                class MyError(Exception):
                    pass
                try:
                    raise MyError
                except MyError:
                    'caught'
                """;
        String js = py(src);
        assertTrue(js.contains("throw new MyError();"), "bare raise must instantiate the class: " + js);
        assertEquals("caught", evalString(src));
        // 函数体内先于类定义发射的 raise 也要实例化（类名在预扫描登记）
        assertEquals("late", evalString("""
                def boom():
                    raise MyError
                class MyError(Exception):
                    pass
                try:
                    boom()
                except MyError:
                    'late'
                """));
    }

    @Test
    void raiseAliasedExceptionClassIsInstantiated() throws Exception {
        // VE = ValueError; raise VE('boom') → throw new VE("boom")（旧实现无 new 调 class →
        // 运行时 TypeError）；裸别名 raise VE 同样按 raise VE() 实例化。
        String src = """
                VE = ValueError
                try:
                    raise VE('boom')
                except ValueError as e:
                    e.message
                """;
        String js = py(src);
        assertTrue(js.contains("throw new VE("), "aliased exception call must be a constructor: " + js);
        assertEquals("boom", evalString(src));
        String bare = """
                VE = ValueError
                try:
                    raise VE
                except ValueError:
                    'bare'
                """;
        assertTrue(py(bare).contains("throw new VE();"), "bare aliased raise must instantiate: " + py(bare));
        assertEquals("bare", evalString(bare));
    }

    @Test
    void filterNoneFiltersByTruthiness() throws Exception {
        // filter(None, seq) 按元素本身的 Python 真值过滤（旧实现 .filter(null) → 运行时
        // TypeError：null 不是函数）。0 / [] 为假，'a' / 1 / [2] 为真。
        String src = """
                kept = list(filter(None, [0, [], 'a', 1, [2]]))
                str(len(kept)) + kept[0] + str(kept[1]) + str(kept[2][0])
                """;
        String js = py(src);
        assertTrue(js.contains("__nekoTruthy"), "filter must use Python truthiness: " + js);
        assertEquals("3a12", evalString(src));
    }

    @Test
    void filterPredicateUsesPythonTruthiness() throws Exception {
        // 谓词返回值同样按 Python 真值判定：lambda x: x 返回 [] 为假、[1] 为真。
        assertEquals(1, evalInt("len(list(filter(lambda x: x, [[], [1]])))"));
        assertEquals(1, evalInt("list(filter(lambda x: x, [[], [1]]))[0][0]"));
        // 普通谓词保持原语义（回归钉）
        assertEquals(2, evalInt("len(list(filter(lambda x: x > 1, [1, 2, 3])))"));
    }

    @Test
    void userClassSortMethodNotHijacked() throws Exception {
        // 用户类自己的 sort 不能被注入比较器实参（旧实现把 comparator 当第一个实参传入 →
        // b.sort() 返回的是比较器函数而非 None）。
        String src = """
                class Box:
                    def sort(self, key=None):
                        return key
                b = Box()
                b.sort() is None
                """;
        String js = py(src);
        assertTrue(js.contains("Array.isArray(b)"), "bare-name receiver gets a runtime guard: " + js);
        assertTrue(evalBool(src));
        // 构造调用接收者（静态可判定）与带实参调用 → 原生直传
        assertEquals(7, evalInt("class Box:\n    def sort(self, key=None):\n        return key\nBox().sort(7)"));
        assertEquals(3, evalInt("class Box:\n    def sort(self, key=None):\n        return key\nb = Box()\nb.sort(3)"));
    }

    @Test
    void bareListSortStillNumericAware() throws Exception {
        // 回归钉：数组接收者仍注入数值比较器（JS 默认字典序会把 [10,2,1] 排成 [1,10,2]）。
        assertEquals("1,2,10", evalString("str([10, 2, 1].sort())"));          // 字面量接收者
        String src = """
                xs = [10, 2, 1]
                xs.sort()
                str(xs)
                """;
        assertEquals("1,2,10", evalString(src));                              // 名字接收者（运行时探测）
    }

    @Test
    void moduloByZeroRaisesZeroDivisionError() throws Exception {
        // Python: 7 % 0 → ZeroDivisionError（JS 静默 NaN）；__nekoMod 现在显式抛出，
        // 且 needsMod 必须连带发射异常 prelude（ZeroDivisionError 类）。
        String src = """
                try:
                    x = 7 % 0
                except ZeroDivisionError:
                    'caught'
                """;
        String js = py(src);
        assertTrue(js.contains("integer division or modulo by zero"), js);
        assertTrue(js.contains("class ZeroDivisionError extends ArithmeticError"),
                "needsMod must pull in the exception prelude: " + js);
        assertEquals("caught", evalString(src));
        assertEquals(1, evalInt("(-7) % 2"));   // 回归：非零取模语义不变
    }

    @Test
    void divisionByZeroRaisesZeroDivisionError() throws Exception {
        // 1/0、1//0、/=、//=、divmod(1, 0) 全部按 Python 抛 ZeroDivisionError（JS 静默 Infinity）。
        assertEquals("div", evalString("try:\n    x = 1 / 0\nexcept ZeroDivisionError:\n    'div'"));
        assertEquals("floordiv", evalString("try:\n    x = 1 // 0\nexcept ZeroDivisionError:\n    'floordiv'"));
        assertEquals("augdiv", evalString("x = 1\ntry:\n    x /= 0\nexcept ZeroDivisionError:\n    'augdiv'"));
        assertEquals("augfloor", evalString("x = 1\ntry:\n    x //= 0\nexcept ZeroDivisionError:\n    'augfloor'"));
        assertEquals("divmod", evalString("try:\n    d = divmod(1, 0)\nexcept ZeroDivisionError:\n    'divmod'"));
        String js = py("1 / 0");
        assertTrue(js.contains("__nekoDiv"), "true division must route through __nekoDiv: " + js);
        js = py("1 // 0");
        assertTrue(js.contains("__nekoFloorDiv"), "floor division must route through __nekoFloorDiv: " + js);
        // 非零除法语义不变（回归钉）
        assertEquals(4, evalInt("8 / 2"));
        assertEquals(3, evalInt("7 // 2"));
    }

    @Test
    void elifConditionErrorReportsElifLine() {
        // elif 头部（条件）里的发射期错误必须报 elif 所在行，而不是外层 if 的行
        //（parser 现在为糖改写出的嵌套 If 登记 srcLines，emitter 的内联 writeIf 同步 curLine）。
        String src = """
                x = 1
                if x == 2:
                    y = 1
                elif x @ 2:
                    y = 2
                """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> py(src));
        assertTrue(ex.getMessage().contains("python source line 4"),
                "elif condition error must report the elif line: " + ex.getMessage());
        // elif 体内语句的行号本来就各自登记（parseSuite → parseStatements）——钉住该行为
        ex = assertThrows(IllegalArgumentException.class, () -> py("x = 1\nif x:\n    pass\nelif x:\n    del x\n"));
        assertTrue(ex.getMessage().contains("python source line 5"),
                "elif body error must report the body line: " + ex.getMessage());
    }

    @Test
    void elifHeaderGetsSourceMapMapping() throws Exception {
        // 内联的 `} else if` 生成行现在有 source map 映射，指回 elif 头部的 Python 行。
        var result = compiler.compileDetailed(Path.of("test.py"),
                "x = 1\nif x:\n    y = 1\nelif x == 2:\n    y = 2\ny\n");
        var obj = com.google.gson.JsonParser.parseString(result.sourceMap()).getAsJsonObject();
        String[] jsLines = result.code().split("\n", -1);
        int elseIfLine = -1;
        for (int i = 0; i < jsLines.length; i++) {
            if (jsLines[i].contains("} else if")) { elseIfLine = i; break; }
        }
        assertTrue(elseIfLine >= 0, "emitted JS must contain an inlined else-if: " + result.code());
        var genToOrig = decodeMappings(obj.get("mappings").getAsString());
        assertEquals(3, genToOrig.get(elseIfLine),
                "the else-if JS line must map back to Python line 4 (elif header)");
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
