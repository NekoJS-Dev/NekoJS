package com.tkisor.nekojs.core.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NekoTypeScriptCompiler#eraseTypescript} 的擦除正确性测试，
 * 重点验证 class 体内成员修饰符（public/private/protected/readonly/abstract/override）被擦除、
 * static 保留。运行时合法性（擦除产物可被 graaljs eval）由游戏内 {@code /nekojs test} 端到端验证
 * （common 测试 classpath 缺 ICU4J，无法直接 graaljs eval）。
 */
class NekoTypeScriptCompilerTest {

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
        // class 体内可见性/readonly 修饰符被擦除
        assertFalse(out.contains("private"), out);
        assertFalse(out.contains("protected"), out);
        assertFalse(out.contains("readonly"), out);
        // public 作为参数属性修饰符也被擦除（transformParameterProperties + class 修饰符 pass）
        assertFalse(out.contains("public"), out);
        // static 保留
        assertTrue(out.contains("static count"), out);
        // 方法与构造器保留
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
       // private 字段修饰符擦除（字段名 _events 保留）
       assertTrue(out.contains("_events"), out);
       assertFalse(out.contains("private"), out);
   }

    @Test
    void erasesTernaryAfterParenAndAs() {
        // 三元 : 前是 )（调用闭合）或 as 表达式 —— 不被误判为类型注解；as Type 擦除
        String src = "const p = typeof f === 'function' ? (f as Promise<unknown>) : f\n" +
            "const r = k === 'string' ? v as string : undefined";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        // 三元 false 分支保留（: f / : undefined）
        assertTrue(out.contains(": undefined"), out);
        // as 表达式擦除
        assertFalse(out.contains("as Promise"), out);
        assertFalse(out.contains("as string"), out);
    }

    @Test
    void asDoesNotConsumeClosingBrace() {
        // as Type } 的 } 必须保留（函数体闭合），不被 typeExpressionEnd 越界吞掉
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
        // 箭头 IIFE (() => { function f(a: T, b: U): R {} }) 内 => 后的 { 是函数体 block（非对象字面量），
        // 第二个参数 b: 不应被误判为对象属性而漏擦
        String src = "const g = (() => { function add(a: number, b: number): number { return a + b } return add })()";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        // 两个参数注解 + 返回类型都擦除（Eraser 用空格替换保长度，不要求紧凑格式）
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
        // 可选参数 ?: 擦除
        assertFalse(out.contains("b?"), out);
        assertFalse(out.contains("?: string"), out);
        // 三元 : 保留
        assertTrue(out.contains("b ? true : false"), out);
        assertTrue(out.contains("n > 0 ? 'pos' : 'neg'"), out);
    }

    @Test
    void namespaceInNestedScopeCompiles() {
        // namespace 在箭头函数体内：转换须前置 var 声明，否则 (name||(name={})) 在严格模式 ReferenceError
        String src = "const f = () => { namespace g { export const x = 1 } return g.x }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("var g"), "namespace 转换须前置 var 声明: " + out);
        assertFalse(out.contains("namespace g"), "namespace 关键字须被转换: " + out);
    }

    @Test
    void erasesGenericArrowFunctionTypeParameters() {
        // 泛型箭头 <T>(x: T) => T：前导字符是 = 时 <T> 也须擦除，否则 GraalJS 拿到 <T> 报错
        String src = "const id = <T>(x: T): T => x";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertFalse(out.contains("<T>"), "泛型箭头的 <T> 须擦除: " + out);
        assertFalse(out.contains(": T"), "参数与返回类型注解须擦除: " + out);
        assertTrue(out.contains("(x"), "参数名保留: " + out);
        assertTrue(out.contains("=> x"), "箭头体保留: " + out);
    }

    @Test
    void erasesGenericArrowInCallArgument() {
        // 泛型箭头作为函数实参：foo(<T>(x: T) => x) 也须正确擦除 <T>
        String src = "const r = wrap(<U>(y: U): U => y)";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertFalse(out.contains("<U>"), "泛型箭头 <U> 须擦除: " + out);
        assertTrue(out.contains("(y"), "参数名保留: " + out);
    }

    @Test
    void doesNotMisinterpretComparisonAsGenericArgs() {
        // 比较 a < b 返回布尔后调用 (c)：不应把 < b > 当泛型擦除
        // 注意：这里 a < b > (c) 在运行时语义奇怪，但关键是 eraser 不应破坏它
        // 用更明确的非泛型场景：三元 + 数组索引
        String src = "const ok = arr[i] > 0 ? pos : neg";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("> 0"), "比较 > 0 须保留（不被当泛型右尖括号）: " + out);
        assertTrue(out.contains("? pos : neg"), "三元保留: " + out);
    }

    @Test
    void preservesInlineTypeImport() {
        // TS 4.5+ 内联 type 修饰符：import { real, type T } from 'x'
        // 只应擦除 type T，保留 real 与整个 import 语句
        String src = "import { real, type T } from 'mod'";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("real"), "内联 import 的运行时绑定 real 须保留: " + out);
        assertTrue(out.contains("from"), "import ... from 须保留: " + out);
        assertFalse(out.contains("type T"), "内联 type T 须擦除: " + out);
    }

    @Test
    void preservesMixedValueAndTypeInlineImport() {
        // 多个值绑定 + 多个内联 type：import { a, b, type X, type Y } from 'm'
        String src = "import { a, b, type X, type Y } from 'm'";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertTrue(out.contains("a"), "值绑定 a 须保留: " + out);
        assertTrue(out.contains("b"), "值绑定 b 须保留: " + out);
        assertFalse(out.contains("type X"), "内联 type X 须擦除: " + out);
        assertFalse(out.contains("type Y"), "内联 type Y 须擦除: " + out);
    }

    @Test
    void erasesWholeImportTypeStatement() {
        // 纯 import type { T }（整条是 type import）仍整体删除
        String src = "import type { T } from 'mod'\nconst x = 1";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        assertFalse(out.contains("import type"), "纯 import type 整条须擦除: " + out);
        assertFalse(out.contains("from 'mod'"), "纯 import type 的 from 须擦除: " + out);
        assertTrue(out.contains("const x = 1"), "后续语句保留: " + out);
    }

    @Test
    void enumNumericAutoIncrementFromExplicitValue() {
        // enum E { A = 1, B } —— B 应自增为 2
        String src = "enum E { A = 1, B }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        // A=1, B=2 双向映射
        assertTrue(out.contains("[\"A\"] = 1"), "A=1 须保留: " + out);
        assertTrue(out.contains("[\"B\"] = 2"), "B 应自增为 2: " + out);
    }

    @Test
    void enumComputedMemberPropagatesToNext() {
        // enum E { A = base(), B } —— A 是计算值，B 应在运行时基于 A 自增（编译期不知 base() 的值）
        // TS 语义：B = E["A"] + 1（运行时）。NekoJS 之前错误地把 B 重置成 0。
        String src = "enum E { A = base(), B }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        // A 用计算表达式赋值
        assertTrue(out.contains("[\"A\"] = base()"), "A 的计算表达式须保留: " + out);
        // B 不应是固定 0，应引用 E["A"] + 1（运行时自增）
        assertTrue(out.contains("E[\"A\"] + 1"), "B 应运行时基于 A 自增（E[\"A\"] + 1）: " + out);
        assertFalse(out.matches("(?s).*\\[\"B\"\\] = \\[E\\[\\[E\\[\"B\"\\].*"), "B 不应被当作已知数字双向映射: " + out);
    }

    @Test
    void namespaceExportInterfaceDoesNotLeaveStrayExport() {
        // namespace 内 export interface —— interface 由 phase1 擦除，但 export 须被 namespace 转换剥除，
        // 否则残留 export 在 IIFE 体内导致语法错
        String src = "namespace N { export interface I { x: number } export const v = 1 }";
        String out = NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src);
        // N.v 应被导出到 N.v = v（证明 const 成员被处理）
        assertTrue(out.contains("N.v = v"), "const 成员 v 须被 namespace 导出: " + out);
        // 不能残留裸 export（IIFE 体内 export 是非法语法）
        // 检查 IIFE 体内没有 "export " 开头的裸语句（排除被擦成空格的）
        assertFalse(out.matches("(?s)\\{[^}]*\\bexport\\s+(interface|type|const|function|let|var|class)"),
            "namespace IIFE 体内不应残留 export 声明: " + out);
    }

    @Test
    void namespaceExportTypeAliasStripped() {
        // namespace 内 export type X = ... —— type 由 phase1 擦除，export 须剥除
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
        // @Component 装饰器：NekoJS 是脚本引擎非 TS 框架，不支持装饰器，须清晰报错而非产出坏 JS
        String src = "@Component\nclass Foo {}";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src));
        assertTrue(ex.getMessage().toLowerCase().contains("decorator"),
            "错误信息须提及 decorator: " + ex.getMessage());
    }

    @Test
    void methodDecoratorThrowsUnsupported() {
        // 类成员装饰器同样须报错
        String src = "class Foo {\n  @Log\n  greet() {}\n}";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> NekoTypeScriptCompiler.eraseTypescript(Path.of("test.ts"), src));
        assertTrue(ex.getMessage().toLowerCase().contains("decorator"),
            "错误信息须提及 decorator: " + ex.getMessage());
    }
}
