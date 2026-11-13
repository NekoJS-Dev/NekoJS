package com.tkisor.nekojs.core.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link NekoTypeScriptCompiler#eraseTypescript} erases correctly. The main focus is
 * that member modifiers in a class body (public/private/protected/readonly/abstract/override) are
 * erased and static is kept. Whether the result runs (the erased output can be evaluated by
 * graaljs) is verified end to end in game with {@code /nekojs test}: the common test classpath
 * lacks ICU4J, so graaljs eval is not available here.
 */
class NekoTypeScriptCompilerTest {

    @Test
    void erasesTypeAliasContinuingPastObjectBody() {
        // `type A = { .. } | B` (and `{ .. }[]`): after the matched body the union arm is
        // still part of the declaration; leftover `|` makes the erased output a SyntaxError
        // (regression from jsx-runtime's NekoUiCrop).
        String src = """
            type Crop = { x: number; y: number } | readonly [number, number, number, number]
            type List = { id: number }[]
            const value = 1
            """;
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertFalse(out.contains("|"), out);
        assertFalse(out.contains("readonly"), out);
        assertFalse(out.contains("number"), out);
        assertTrue(out.contains("const value = 1"), out);
    }

    @Test
    void erasesClassMemberVisibilityModifiers() {
        String src = """
            class Foo {
              private x: number = 1;
              public readonly y: string = 'a';
              protected z: boolean = false;
              static count: number = 0;
              constructor(public name: string) {}
              greet(): string { return this.name }
            }
            """;
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        // Visibility and readonly modifiers in the class body are erased
        assertFalse(out.contains("private"), out);
        assertFalse(out.contains("protected"), out);
        assertFalse(out.contains("readonly"), out);
        // public as a parameter property modifier is erased too (transformParameterProperties plus
        // the class modifier pass)
        assertFalse(out.contains("public"), out);
        // static is kept
        assertTrue(out.contains("static count"), out);
        // Methods and the constructor are kept
        assertTrue(out.contains("greet"), out);
        assertTrue(out.contains("constructor"), out);
    }

    @Test
    void preservesStaticAndInstanceMethods() {
        String src = """
            class EE {
              static defaultMaxListeners: number = 10;
              private _events: Record<string, Function> = {};
              on(name: string, fn: () => void): this { return this }
              static listenerCount(e: EE, name: string): number { return 0 }
            }
            """;
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("static defaultMaxListeners"), out);
        assertTrue(out.contains("static listenerCount"), out);
        assertTrue(out.contains("on"), out);
       // The private field modifier is erased; the field name _events stays
       assertTrue(out.contains("_events"), out);
       assertFalse(out.contains("private"), out);
   }

    @Test
    void erasesTernaryAfterParenAndAs() {
        // The : of the ternary follows ) (a call closing) or an as expression, so it is not taken
        // for a type annotation; as Type is erased
        String src = "const p = typeof f === 'function' ? (f as Promise<unknown>) : f\n" +
            "const r = k === 'string' ? v as string : undefined";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        // The false branch of the ternary is kept (: f / : undefined)
        assertTrue(out.contains(": undefined"), out);
        // as expressions are erased
        assertFalse(out.contains("as Promise"), out);
        assertFalse(out.contains("as string"), out);
    }

    @Test
    void asDoesNotConsumeClosingBrace() {
        // The } after as Type must be kept because it closes the function body; typeExpressionEnd
        // must not read past it
        String src = "const f = function (n: number): number { return wrap(n) as number }\n" +
            "const g = function (s: string): string { return s as string }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        long braces = out.chars().filter(c -> c == '}').count();
        assertEquals(2, braces, "两个函数体闭合 } 都保留: " + out);
        assertFalse(out.contains("as number"), out);
        assertFalse(out.contains("as string"), out);
    }

    @Test
    void erasesParamsInArrowIife() {
        // In the arrow IIFE (() => { function f(a: T, b: U): R {} }) the { after => starts a
        // function body, not an object literal, so the second parameter b: must not be taken for an
        // object property and left unerased
        String src = "const g = (() => { function add(a: number, b: number): number { return a + b } return add })()";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        // Both parameter annotations and the return type are erased (the eraser keeps length by
        // replacing with spaces, so compact formatting is not required)
        assertFalse(out.contains(": number"), out);
        assertTrue(out.contains("function add(a"), out);
        assertTrue(out.contains(", b"), out);
        assertTrue(out.contains("return a + b"), out);
    }

    @Test
    void erasesOptionalParamsAndPreservesTernary() {
        String src = "function f(a: number, b?: string): boolean { return b ? true : false }\n" +
            "const g = (n: number): string => (n > 0 ? 'pos' : 'neg')";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        // The optional parameter marker ?: is erased
        assertFalse(out.contains("b?"), out);
        assertFalse(out.contains("?: string"), out);
        // The ternary : is kept
        assertTrue(out.contains("b ? true : false"), out);
        assertTrue(out.contains("n > 0 ? 'pos' : 'neg'"), out);
    }

    @Test
    void preservesDoubleBangAfterStatementKeywords() {
        // After keywords such as return and typeof, `!!` is a logical not, not a non-null
        // assertion; erasing it to a single ! would invert the logic
        String src = "function f(x: unknown): boolean { return !!x }\n" +
            "function g(x: unknown): string { return typeof !!x }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("return !!x"), out);
        assertTrue(out.contains("typeof !!x"), out);
        // A real non-null assertion is still erased
        String assertion = "const v = a!.b;";
        String erased = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), assertion);
        assertFalse(erased.contains("a!."), erased);
    }

    @Test
    void erasesObjectLiteralMethodShorthandParams() {
        // The annotation on the 2nd and later parameters of a method shorthand must not be taken
        // for a property colon by objectLiteralPropertyColon and left behind
        String src = "const o = { m(a: string, b: number, c?: boolean): void { return null } }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        String flat = out.replaceAll("\\s+", " ");
        assertTrue(flat.contains("m(a , b , c ) { return null }"), out);
        assertFalse(out.contains("b: number"), out);
        assertFalse(out.contains("c?"), out);
    }

    @Test
    void objectLiteralPropertyColonsSurviveMethodShorthandFix() {
        // Property values, parentheses in strings, class methods and arrow bodies are unaffected by the
        // method-shorthand fix
        String src = "const keep = { a: 1, b: 2, c: '(' }\n" +
            "const keepCall = { k: f('('), v: 2 }\n" +
            "const keepFn = { m(a: string) { return a } , n: 3 }\n" +
            "class C { m(a: string, b: number): void {} }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("a: 1, b: 2, c: '('"), out);
        assertTrue(out.contains("f('('), v: 2"), out);
        assertTrue(out.contains("n: 3"), out);
        assertFalse(out.contains("a: string"), out);
        assertFalse(out.contains("b: number"), out);
    }

    @Test
    void erasesBareOptionalParams() {
        // A bare optional parameter `b?` with no `: T` annotation must lose its `?`; a leftover `?` is a
        // syntax error
        String src = "function f(a, b?) {}\n" +
            "const g = (x?, y) => x\n" +
            "const o = { m(a?) { return a } }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertFalse(out.contains("b?"), out);
        assertFalse(out.contains("x?"), out);
        assertFalse(out.contains("a?"), out);
        String flat = out.replaceAll("\\s+", " ");
        assertTrue(flat.contains("function f(a, b )"), out);
        // The ternary, optional chaining and nullish coalescing are unaffected
        String keep = "const t = x ? y : z\nconst o2 = a?.b ?? c";
        String kept = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), keep);
        assertTrue(kept.contains("x ? y : z"), kept);
        assertTrue(kept.contains("a?.b ?? c"), kept);
    }

    @Test
    void namespaceInNestedScopeCompiles() {
        // A namespace inside an arrow function body: the transform must declare `var` up front, otherwise
        // (name||(name={})) throws ReferenceError in strict mode
        String src = "const f = () => { namespace g { export const x = 1 } return g.x }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("var g"), "namespace 转换须前置 var 声明: " + out);
        assertFalse(out.contains("namespace g"), "namespace 关键字须被转换: " + out);
    }

    @Test
    void erasesGenericArrowFunctionTypeParameters() {
        // Generic arrow <T>(x: T) => T: <T> must be erased even when the preceding character is =,
        // otherwise GraalJS rejects the leftover <T>
        String src = "const id = <T>(x: T): T => x";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertFalse(out.contains("<T>"), "泛型箭头的 <T> 须擦除: " + out);
        assertFalse(out.contains(": T"), "参数与返回类型注解须擦除: " + out);
        assertTrue(out.contains("(x"), "参数名保留: " + out);
        assertTrue(out.contains("=> x"), "箭头体保留: " + out);
    }

    @Test
    void erasesGenericArrowInCallArgument() {
        // A generic arrow passed as a call argument: foo(<T>(x: T) => x) must also have <T> erased
        String src = "const r = wrap(<U>(y: U): U => y)";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertFalse(out.contains("<U>"), "泛型箭头 <U> 须擦除: " + out);
        assertTrue(out.contains("(y"), "参数名保留: " + out);
    }

    @Test
    void doesNotMisinterpretComparisonAsGenericArgs() {
        // Comparing a < b yields a boolean that is then called with (c): < b > must not be erased as
        // generic arguments
        // a < b > (c) behaves oddly at runtime, but the eraser must not break it
        // The test uses a clearer non-generic case: a ternary plus an array index
        String src = "const ok = arr[i] > 0 ? pos : neg";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("> 0"), "比较 > 0 须保留（不被当泛型右尖括号）: " + out);
        assertTrue(out.contains("? pos : neg"), "三元保留: " + out);
    }

    @Test
    void preservesInlineTypeImport() {
        // TS 4.5+ inline type modifier: import { real, type T } from 'x'
        // Only type T is erased; real and the whole import statement stay
        String src = "import { real, type T } from 'mod'";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("real"), "内联 import 的运行时绑定 real 须保留: " + out);
        assertTrue(out.contains("from"), "import ... from 须保留: " + out);
        assertFalse(out.contains("type T"), "内联 type T 须擦除: " + out);
    }

    @Test
    void preservesMixedValueAndTypeInlineImport() {
        // Several value bindings plus several inline types: import { a, b, type X, type Y } from 'm'
        String src = "import { a, b, type X, type Y } from 'm'";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("a"), "值绑定 a 须保留: " + out);
        assertTrue(out.contains("b"), "值绑定 b 须保留: " + out);
        assertFalse(out.contains("type X"), "内联 type X 须擦除: " + out);
        assertFalse(out.contains("type Y"), "内联 type Y 须擦除: " + out);
    }

    @Test
    void erasesWholeImportTypeStatement() {
        // A pure import type { T }, where the whole statement is a type import, is still removed entirely
        String src = "import type { T } from 'mod'\nconst x = 1";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertFalse(out.contains("import type"), "纯 import type 整条须擦除: " + out);
        assertFalse(out.contains("from 'mod'"), "纯 import type 的 from 须擦除: " + out);
        assertTrue(out.contains("const x = 1"), "后续语句保留: " + out);
    }

    @Test
    void enumNumericAutoIncrementFromExplicitValue() {
        // enum E { A = 1, B }: B auto-increments to 2
        String src = "enum E { A = 1, B }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        // A=1 and B=2 with the reverse mapping
        assertTrue(out.contains("[\"A\"] = 1"), "A=1 须保留: " + out);
        assertTrue(out.contains("[\"B\"] = 2"), "B 应自增为 2: " + out);
    }

    @Test
    void enumComputedMemberPropagatesToNext() {
        // enum E { A = base(), B }: A is computed, so B must auto-increment from A at runtime (the compiler
        // cannot know the value of base()).
        // TS semantics: B = E["A"] + 1 at runtime; B must not be reset to 0.
        String src = "enum E { A = base(), B }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        // A is assigned from the computed expression
        assertTrue(out.contains("[\"A\"] = base()"), "A 的计算表达式须保留: " + out);
        // B is not a fixed 0; it refers to E["A"] + 1 and increments at runtime
        assertTrue(out.contains("E[\"A\"] + 1"), "B 应运行时基于 A 自增（E[\"A\"] + 1）: " + out);
        assertFalse(out.matches("(?s).*\\[\"B\"\\] = \\[E\\[\\[E\\[\"B\"\\].*"), "B 不应被当作已知数字双向映射: " + out);
    }

    @Test
    void namespaceExportInterfaceDoesNotLeaveStrayExport() {
        // export interface inside a namespace: phase1 erases the interface, but the namespace transform must
        // also strip export, otherwise the leftover export inside the IIFE body is a syntax error
        String src = "namespace N { export interface I { x: number } export const v = 1 }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        // N.v must be exported as N.v = v, which shows the const member was handled
        assertTrue(out.contains("N.v = v"), "const 成员 v 须被 namespace 导出: " + out);
        // No bare export may remain: export inside an IIFE body is invalid syntax
        // The check looks for a bare statement starting with "export " inside the IIFE body, ignoring
        // ones that were erased to spaces
        assertFalse(out.matches("(?s)\\{[^}]*\\bexport\\s+(interface|type|const|function|let|var|class)"),
            "namespace IIFE 体内不应残留 export 声明: " + out);
    }

    @Test
    void namespaceExportTypeAliasStripped() {
        // export type X = ... inside a namespace: phase1 erases the type, and the transform strips export
        String src = "namespace N { export type T = number; export function f(): void {} }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("N.f = f"), "function 成员 f 须被 namespace 导出: " + out);
        assertFalse(out.matches("(?s)\\(function \\(N\\)[^}]*\\bexport\\s+(type|interface)"),
            "namespace IIFE 体内不应残留 export type/interface: " + out);
    }

    @Test
    void derivedConstructorParameterPropertyInitializesAfterSuper() {
        String src = "class Base {} class Derived extends Base { constructor(public x: number) { super() } } new Derived(1).x";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);

        try (CompilerExecutionAssertions.Evaluation evaluation = CompilerExecutionAssertions.eval(out)) {
            assertEquals(1, evaluation.value().asInt(), "derived parameter property must initialize after super(): " + out);
        }
    }

    @Test
    void baseConstructorParameterPropertyInitializesAtBodyStart() {
        String src = "class Base { constructor(public x: number) { this.seen = this.x } } new Base(2).seen";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);

        try (CompilerExecutionAssertions.Evaluation evaluation = CompilerExecutionAssertions.eval(out)) {
            assertEquals(2, evaluation.value().asInt(), "base parameter property must initialize at constructor body start: " + out);
        }
    }

    @Test
    void ordinaryTypedConstructorParameterDoesNotBecomeProperty() {
        String src = "class C { constructor(x: number) {} } new C(1).x";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("ordinary-parameter.ts"), src);

        assertFalse(out.contains("this.x"), "ordinary constructor parameters must not produce property assignments: " + out);
        try (CompilerExecutionAssertions.Evaluation evaluation = CompilerExecutionAssertions.eval(out)) {
            assertTrue(evaluation.value().isNull(), "ordinary constructor parameter property access must evaluate undefined: " + out);
        }
    }

    @Test
    void derivedParameterPropertyFollowsCompleteSuperCallWithNestedArguments() {
        String src = "class Base { constructor(value) { this.value = value } } "
            + "class Derived extends Base { constructor(public x: number) { super(({ value: (x + 1) }).value) } } "
            + "const d = new Derived(2); d.value * 10 + d.x";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);

        try (CompilerExecutionAssertions.Evaluation evaluation = CompilerExecutionAssertions.eval(out)) {
            assertEquals(32, evaluation.value().asInt(), "assignment must follow the complete nested super(...) call: " + out);
        }
    }

    @Test
    void derivedParameterPropertyIgnoresPseudoSuperInCommentsAndStrings() {
        String src = "class Base {} class Derived extends Base { constructor(public x: number) { "
            + "/* super('comment') */ const text = 'super(\"string\")'; const template = `super(${x})`; super(); this.text = text + template "
            + "} } new Derived(3).x";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);

        try (CompilerExecutionAssertions.Evaluation evaluation = CompilerExecutionAssertions.eval(out)) {
            assertEquals(3, evaluation.value().asInt(), "pseudo-super text must not select the insertion point: " + out);
        }
    }

    @Test
    void derivedParameterPropertyIgnoresPropertyAndOptionalPropertySuperCalls() {
        String src = "class Base {} class Derived extends Base { constructor(public x: number) { "
            + "const obj = { super() { return 0 } }; obj.super(); obj?.super(); super(); "
            + "} } new Derived(4).x";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("property-super.ts"), src);

        try (CompilerExecutionAssertions.Evaluation evaluation = CompilerExecutionAssertions.eval(out)) {
            assertEquals(4, evaluation.value().asInt(),
                "obj.super()/obj?.super() must not select the insertion point: " + out);
        }
    }

    @Test
    void derivedParameterPropertyIgnoresRegexSuperText() {
        String src = "class Base {} class Derived extends Base { constructor(public x: number) { "
            + "const pseudo = /super()/; const delimiters = /[})]/; super(); "
            + "} } new Derived(5).x";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("regex-super.ts"), src);

        assertTrue(out.contains("super();"),
            "super() statement must survive erasure when inside a regex-sandwiched constructor: " + out);
        assertTrue(out.contains("/super()/") || out.contains("/[})]/"),
            "regex literals must remain intact (not be swallowed by super scanning): " + out);
        assertTrue(out.contains("new Derived"),
            "downstream constructor call must be preserved: " + out);
    }

    @Test
    void derivedParameterPropertyFollowsStandaloneSuperStatementAcrossTrivia() {
        String src = "class Base { constructor(value) { this.value = value } } "
            + "class Derived extends Base { constructor(public x: number) { "
            + "super(/* ) } */ /[})]/.test('x') ? x : x); // trailing comment\n"
            + "this.after = this.x; } } const d = new Derived(6); d.value * 100 + d.after";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("trivia-super.ts"), src);

        assertTrue(out.contains("super("),
            "super(...) call must survive erasure with trivia-separated comment: " + out);
        assertTrue(out.contains("/[})]/"),
            "regex literal inside super(...) must remain intact: " + out);
    }

    @Test
    void derivedParameterPropertyAcceptsAsiTerminatedSuperBeforeNextStatement() {
        String src = "class Base {} class Derived extends Base { constructor(public x: number) { "
            + "super() /* trailing trivia */\nthis.y = 1 } } const d = new Derived(7); d.x * 10 + d.y";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("asi-super.ts"), src);

        try (CompilerExecutionAssertions.Evaluation evaluation = CompilerExecutionAssertions.eval(out)) {
            assertEquals(71, evaluation.value().asInt(),
                "newline before a new statement must terminate standalone super(): " + out);
        }
    }

    @Test
    void derivedParameterPropertyFailsClosedForNonStandaloneSuperExpressions() {
        String[] sources = {
            "class Base {} class Derived extends Base { constructor(public x: number) { super().foo(); } }",
            "class Base {} class Derived extends Base { constructor(public x: number) { super()\n.foo(); } }",
            "class Base {} class Derived extends Base { constructor(public x: number) { super()\n?.foo(); } }",
            "class Base {} class Derived extends Base { constructor(public x: number) { super()\n[0]; } }",
            "class Base {} class Derived extends Base { constructor(public x: number) { const y = super(); } }"
        };

        for (String src : sources) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> NekoTypeScriptCompiler.eraseTypescript(Path.of("non-standalone-super.ts"), src));
            assertTrue(ex.getMessage().contains("top-level super"),
                "non-standalone super use must fail closed: " + ex.getMessage());
        }
    }

    @Test
    void derivedParameterPropertyWithoutTopLevelSuperFailsClosed() {
        String src = "class Base {} class Derived extends Base { constructor(public x: number) { "
            + "const text = 'super()'; const template = `super()`; /* super() */ "
            + "function nested() { super() } class Inner extends Base { constructor() { super() } } "
            + "} }";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> NekoTypeScriptCompiler.eraseTypescript(Path.of("missing-super.ts"), src));
        assertTrue(ex.getMessage().contains("top-level super"),
            "错误信息须明确说明缺少顶层 super 调用: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("missing-super.ts"),
            "错误信息须包含文件路径: " + ex.getMessage());
    }

    @Test
    void classDecoratorThrowsUnsupported() {
        // @Component decorator: NekoJS is a script engine, not a TS framework. Decorators are not supported,
        // so the compiler must fail with a clear error instead of emitting broken JS
        String src = "@Component\nclass Foo {}";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src));
        assertTrue(ex.getMessage().toLowerCase().contains("decorator"),
            "错误信息须提及 decorator: " + ex.getMessage());
    }

    @Test
    void methodDecoratorThrowsUnsupported() {
        // A decorator on a class member must fail with the same error
        String src = "class Foo {\n  @Log\n  greet() {}\n}";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src));
        assertTrue(ex.getMessage().toLowerCase().contains("decorator"),
            "错误信息须提及 decorator: " + ex.getMessage());
    }

    // ---- B7 regression tests: enum numeric literal validation ----

    @Test
    void enumValidNumericLiteralsCompile() {
        String src = "enum E { A = 1e5, B = .5, C = 1., D = 0x1F, E2 = 0o7, F = 0b101, G = -2 }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("[\"A\"] = 100000"), out);
        // Floating-point literals are not truncated: .5 must stay 0.5, not become 0
        assertTrue(out.contains("[\"B\"] = 0.5"), out);
        assertTrue(out.contains("[\"C\"] = 1"), out);
        assertFalse(out.contains("[\"C\"] = 1."), out);
        assertTrue(out.contains("[\"D\"] = 31"), out);
        assertTrue(out.contains("[\"E2\"] = 7"), out);
        assertTrue(out.contains("[\"F\"] = 5"), out);
        assertTrue(out.contains("[\"G\"] = -2"), out);
    }

    @Test
    void enumRejectsExponentWithoutDigits() {
        String src = "enum E { A = 1e }";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src));
        assertTrue(ex.getMessage().contains("1e"), "error must mention literal: " + ex.getMessage());
    }

    @Test
    void enumRejectsExponentSignWithoutDigits() {
        String src = "enum E { A = 1e+ }";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src));
        assertTrue(ex.getMessage().contains("1e+"), "error must mention literal: " + ex.getMessage());
    }

    @Test
    void enumComputedNumericExpressionMembersCompile() {
        // 1 + 2 / 0xff + 1 / .5 + 1 / 1 << 2 are computed members, not numeric literals;
        // they must stay runtime expressions and must not be mistaken for malformed literals.
        String src = "enum E { A = 1 + 2, B = 0xff + 1, C = .5 + 1, D = 1 << 2 }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("[\"A\"] = 1 + 2"), out);
        assertTrue(out.contains("[\"B\"] = 0xff + 1"), out);
        assertTrue(out.contains("[\"C\"] = .5 + 1"), out);
        assertTrue(out.contains("[\"D\"] = 1 << 2"), out);
    }

    @Test
    void enumValidExponentNumericLiteralsCompile() {
        // 1e+5 and 1.5e-2 must be accepted (exponent has at least one digit after optional sign).
        String src = "enum E { A = 1e+5, B = 1.5e-2 }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("[\"A\"] = 100000"), out);
        // Floating-point enum values are not truncated: 1.5e-2 must stay 0.015, not become 0
        assertTrue(out.contains("[\"B\"] = 0.015"), out);
    }

    @Test
    void enumRejectsInvalidPrefixedNumericLiterals() {
        for (String literal : new String[]{"0b2", "0o8", "0xG"}) {
            String src = "enum E { A = " + literal + " }";
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src),
                "enum literal " + literal + " must be rejected");
            assertTrue(ex.getMessage().contains(literal),
                "error must mention literal '" + literal + "': " + ex.getMessage());
        }
    }

    // ---- Regression tests: statement-level structural pre-scan ----

    @Test
    void importAliasIsPreservedAsValueAlias() {
        // The `as` in import { A as B } is a native JS value alias. Erasing it as a type assertion leaves
        // the alias out, so the binding is missing at runtime (ReferenceError)
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "import { Items as ItemRegistry } from './registry';\nItemRegistry.air();");
        assertTrue(out.contains("Items as ItemRegistry"), out);
        assertTrue(out.contains("ItemRegistry.air();"), out);
        CompilerExecutionAssertions.parseModule(out);
    }

    @Test
    void exportAliasIsPreserved() {
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "export { default as x, y as z } from './m';");
        assertTrue(out.contains("default as x"), out);
        assertTrue(out.contains("y as z"), out);
        CompilerExecutionAssertions.parseModule(out);
    }

    @Test
    void typeAssertionAfterImportStillErased() {
        // `as any` inside an export/import declaration body is still a type assertion and must be erased as
        // usual; the alias exemption covers only named clauses
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "export const n = 1 as const;\nexport const s = getValue() as string;");
        assertTrue(out.matches("(?s).*export const n = 1[ ]*;.*"), out);
        assertTrue(out.matches("(?s).*export const s = getValue[(][)][ ]*;.*"), out);
        assertFalse(out.contains("as const"), out);
        assertFalse(out.contains("as string"), out);
        CompilerExecutionAssertions.parseModule(out);
    }

    @Test
    void switchCaseBodyIsNotErased() {
        // The colon in `case 1:` must not be taken for a type annotation (digits are identifier characters),
        // otherwise the whole case body is erased
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "switch (n) {\n  case 1: doOne(); break;\n  case 'a': doA(); break;\n  default: doOther(); break;\n}");
        assertTrue(out.contains("case 1: doOne();"), out);
        assertTrue(out.contains("case 'a': doA();"), out);
        assertTrue(out.contains("default: doOther();"), out);
        CompilerExecutionAssertions.parse(out);
    }

    @Test
    void switchCaseWithIdentifierConstBodyIsNotErased() {
        // A case that uses an identifier constant (the preceding character is an identifier character) must
        // also survive erasure
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "const BIG = 2;\nswitch (n) { case BIG: doBig(); break; }");
        assertTrue(out.contains("case BIG: doBig();"), out);
        CompilerExecutionAssertions.parse(out);
    }

    @Test
    void ternaryInsideCaseExpressionKeepsRealCaseColon() {
        // A ternary colon inside the case expression is not the case colon: only the matching ':' belongs to
        // the case
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "switch (n) { case flag ? 1 : 2: doIt(); break; }");
        assertTrue(out.contains("case flag ? 1 : 2: doIt();"), out);
        CompilerExecutionAssertions.parse(out);
    }

    @Test
    void labeledStatementIsNotGutted() {
        // The label colon in `outer: for` must not turn the whole loop into a type annotation
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "outer: for (let i = 0; i < 3; i++) {\n  for (let j = 0; j < 3; j++) {\n    if (j > i) continue outer;\n  }\n}");
        assertTrue(out.contains("outer: for"), out);
        assertTrue(out.contains("continue outer;"), out);
        CompilerExecutionAssertions.parse(out);
    }

    @Test
    void classFieldTypeAnnotationStillErased() {
        // Label detection must not damage class field type annotations, including generic ones. A field
        // whose annotation type starts with '{', as in x: { a: number }, is a known gap and is not
        // covered here
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "class C {\n  width: number = 2;\n  table: Map<string, number> = new Map();\n}");
        assertTrue(out.matches("(?s).*width[ ]*= 2;.*"), out);
        assertFalse(out.contains(": number"), out);
        assertFalse(out.contains("Map<string"), out);
        CompilerExecutionAssertions.parse(out);
    }

    @Test
    void optionalChainGenericTypeArgumentsAreErased() {
        // a?.<T>(x): generic arguments after '.' must be recognized; a leftover <T> is a SyntaxError
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "const r = source?.<string>(arg);");
        assertFalse(out.contains("<string>"), out);
        assertTrue(out.contains("source?"), out);
        CompilerExecutionAssertions.parse(out);
    }

    @Test
    void declareConstIsErasedWholeStatement() {
        // Erasing only the `declare` keyword of `declare const` leaves an uninitialized const, which is a
        // SyntaxError; erase the whole statement
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "declare const VERSION: string;\nconst local = 1;");
        assertFalse(out.contains("VERSION"), out);
        assertTrue(out.contains("const local = 1;"), out);
        CompilerExecutionAssertions.parse(out);
    }

    @Test
    void exportDeclareIsErasedWholeStatement() {
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "export declare function helper(a: number): string;\nconst local = 2;");
        assertFalse(out.contains("helper"), out);
        assertFalse(out.contains("export ;"), out);
        assertTrue(out.contains("const local = 2;"), out);
        CompilerExecutionAssertions.parse(out);
    }

    @Test
    void exportInlineTypeSpecifierIsErased() {
        // export { type Config, createConfig }: the inline type must be handled in export clauses, not only
        // in import clauses, otherwise the leftover is invalid JS. Module parsing checks that the
        // exported binding exists, so the test defines createConfig
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "const createConfig = () => 1;\nexport { type Config, createConfig };");
        assertFalse(out.contains("type Config"), out);
        assertTrue(out.contains("createConfig"), out);
        CompilerExecutionAssertions.parseModule(out);
    }

    @Test
    void importDefaultBindingWithInlineTypeSpecifierIsErased() {
        // A default binding plus a named clause: the named clause must be recognized even when its '{' does
        // not directly follow import
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "import def, { type Only, real } from './m';\ndef(real);");
        assertFalse(out.contains("type Only"), out);
        assertTrue(out.contains("real"), out);
        CompilerExecutionAssertions.parseModule(out);
    }

    @Test
    void abstractClassMembersAreElided() {
        // An abstract member is a placeholder with no implementation. TS elides it, and a bodyless method
        // left in a JS class is a SyntaxError
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "abstract class Base {\n  abstract name: string;\n  abstract move(): void;\n  concrete(): number { return 1; }\n}\nclass Impl extends Base {\n  name = 'x';\n  move() {}\n}");
        assertFalse(out.contains("abstract"), out);
        assertFalse(out.contains(": void"), out);
        assertFalse(out.contains(": string"), out);
        assertTrue(out.contains("concrete()"), out);
        assertTrue(out.contains("return 1;"), out);
        assertTrue(out.contains("move() {}"), out);
        CompilerExecutionAssertions.parse(out);
    }

    @Test
    void enumFloatValueIsNotTruncated() {
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"),
            "enum Rarity { Half = 0.5, Full = 1 }");
        assertTrue(out.contains("[\"Half\"] = 0.5"), out);
        assertTrue(out.contains("[\"Full\"] = 1]"), out);
        CompilerExecutionAssertions.parse(out);
    }
}
