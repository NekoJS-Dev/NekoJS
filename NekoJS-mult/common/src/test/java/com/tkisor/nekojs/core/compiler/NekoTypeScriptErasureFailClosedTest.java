package com.tkisor.nekojs.core.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 12 缺陷 B（AC4）：<strong>未终止的 TS 类型构造必须 fail closed</strong>，而不是静默地把
 * 文件剩余部分擦成空白。
 *
 * <p>历史缺陷：{@code Eraser.statementOrBlockDeclarationEnd} 扫到 {@code \{} 后调用
 * {@code matchingCloseBrace}，未闭合时返回 -1，代码随即 {@code return length}——整个文件被擦成空白，
 * 作者写在后面的 {@code SIDE_EFFECT()} 凭空消失且没有任何提示。这直接违反该文件自身的
 * fail-closed 哲学（装饰器与派生类参数属性都是明确抛 {@code IllegalArgumentException}）。
 *
 * <p>本测试同时钉住<strong>边界</strong>：修 fail-closed <em>不得</em>收紧合法 TS——
 * 合法的 interface/type/泛型/泛型箭头/跨行泛型类型标注必须继续擦除成功并可执行。
 * 宁可漏报也不能把能跑的脚本判死。
 */
class NekoTypeScriptErasureFailClosedTest {

    private static final Path FILE = Path.of("fail-closed.ts");

    /** 擦除必须在<strong>准备阶段</strong>以带 file + 行列的 IllegalArgumentException 失败。 */
    private static IllegalArgumentException eraseFailsClosed(String source) {
        return assertThrows(IllegalArgumentException.class,
                () -> NekoTypeScriptCompiler.eraseTypescript(FILE, source),
                "未终止的类型构造必须 fail closed，不得静默擦掉后续代码");
    }

    private static void assertLocatedAt(IllegalArgumentException failure, String position) {
        String message = String.valueOf(failure.getMessage());
        assertTrue(message.contains("fail-closed.ts"),
                "错误必须携带原始 TS 文件：" + message);
        assertTrue(message.contains(position),
                "错误必须携带原始行列 " + position + "：" + message);
    }

    // ---- 未终止形态：必须抛错 ----

    @Test
    void unterminatedInterfaceBodyFailsClosedInsteadOfSwallowingFollowingCode() {
        IllegalArgumentException failure = eraseFailsClosed(
                "interface Foo {\n"
                + "  bar: string;\n"
                + "\n"
                + "SIDE_EFFECT();\n"
                + "const x = 1;\n");

        // 位置 = interface 关键字起点（1-based 列 1）
        assertLocatedAt(failure, "1:1");
        assertTrue(String.valueOf(failure.getMessage()).toLowerCase().contains("unterminated"),
                "消息必须说明是未终止构造：" + failure.getMessage());
    }

    @Test
    void unterminatedTypeAliasObjectBodyFailsClosed() {
        IllegalArgumentException failure = eraseFailsClosed(
                "const a = 1;\n"
                + "const b = 2;\n"
                + "type T = {\n"
                + "  a: string;\n"
                + "\n"
                + "SIDE_EFFECT();\n");

        assertLocatedAt(failure, "3:1");  // type 关键字起点
    }

    /**
     * 未终止的泛型参数列表不能让整个类型标注（连带初始化表达式）被擦掉。
     * {@code const x: Array<string = SIDE_EFFECT();} 的 {@code <} 永远没有配对的 {@code >}，
     * 历史行为是 {@code typeExpressionEnd} 跑到文件末尾，把 {@code SIDE_EFFECT()} 一起擦掉——
     * 产出的 JS 语义已变。
     */
    @Test
    void unterminatedGenericTypeAnnotationFailsClosedInsteadOfErasingInitializer() {
        IllegalArgumentException failure = eraseFailsClosed("const x: Array<string = SIDE_EFFECT();\n");

        assertLocatedAt(failure, "1:9");  // 类型标注的 `:` 处
    }

    @Test
    void unterminatedExportInterfaceFailsClosed() {
        IllegalArgumentException failure = eraseFailsClosed("export interface I {\n  a: string;\n");

        assertLocatedAt(failure, "1:8");  // interface 关键字起点
    }

    @Test
    void unterminatedDeclareClassBodyFailsClosed() {
        IllegalArgumentException failure = eraseFailsClosed("declare class C {\n  a: string;\n");

        assertLocatedAt(failure, "1:1");  // declare 关键字起点
    }

    // ---- 静默丢代码的第二类路径：拆解后未消费 fail-closed 标记（code-review 发现） ----

    /**
     * 静默<strong>删除</strong>代码的第二类路径：{@code eraseDeclare} 对 {@code const/let/var/function}
     * 也走 {@code statementOrBlockDeclarationEnd}，而该类形态不在 {@code scanPredicates} 的
     * {@code declareHasBody} 名单里——拆解器只置位不抛，调用点又漏检，于是 {@code eraseRange} 把
     * 文件其余部分整段擦掉。{@code abstract m(): { a: number }} 同类（成员走同一条拆解）。
     *
     * <p>修复方式不是逐个调用点补检查，而是在唯一的擦除写入口 {@code eraseRange} 里强制
     * 「置位必须先消费」，保证任何漏检路径都不可能提交半擦除结果。
     */
    @Test
    void declarationFormsWithUnclosedTypeBodiesFailClosedInsteadOfSilentlyDeletingFollowingCode() {
        String[] mustThrow = {
                "declare const c: {\n  a: number;\n\nSIDE_EFFECT();\n",
                "declare let x: {\n  a: number;\n\nSIDE_EFFECT();\n",
                "declare var v: {\n  a: number;\n\nSIDE_EFFECT();\n",
                "declare function f(): {\n  a: number;\n\nSIDE_EFFECT();\n",
                "abstract class A {\n  abstract m(): {\n    a: number;\n\nSIDE_EFFECT();\n",
                "class C implements I\nSIDE_EFFECT();\n",
        };
        for (String source : mustThrow) {
            // 断言必须能抓住「吞掉后续语句」：只断言「没删空」不够——
            // 旧实现正是把 SIDE_EFFECT() 一起擦掉后才通过那种弱断言。
            eraseFailsClosed(source);
        }
    }

    /**
     * 仍然静默通过的形态，<strong>逐条按实测</strong>标注：它们都保留了后续语句，
     * 因此只会在执行期以硬 {@code SyntaxError} 可见地失败。
     *
     * <p>断言「后续语句仍在」而不是「产物非空」——后者抓不住静默丢代码。
     */
    @Test
    void stillSilentFormsKeepFollowingCodeAndOnlyFailAtExecutionTime() {
        String[] stillSilent = {
                "class C<T { x = 1; }\nSIDE_EFFECT();\n",
                "function f<T { return 1; }\nSIDE_EFFECT();\n",
                "function f<T\nSIDE_EFFECT();\n",
                "foo<Bar(1);\nSIDE_EFFECT();\n",
                "const f = <T, (x: T) => x;\nSIDE_EFFECT();\n",
                "class C implements I {\n  x = 1;\nSIDE_EFFECT();\n",
                "class C implements { x = 1; }\nSIDE_EFFECT();\n",
                "const c = { a: 1 } satisfies { a: number };\nSIDE_EFFECT();\n",
                "class C { [key: string]: number; }\nSIDE_EFFECT();\n",
                "import A = require('mod');\nSIDE_EFFECT();\n",
        };
        for (String source : stillSilent) {
            String erased = NekoTypeScriptCompiler.eraseTypescript(FILE, source);
            assertTrue(erased.contains("SIDE_EFFECT"),
                    "仍然静默通过的形态必须保留后续语句（否则就是静默丢代码，属于必须 fail closed 的一类）：\n"
                            + source + "\n产物：\n" + erased);
        }
    }

    // ---- 边界：合法 TS 必须继续擦除成功（防止过度收紧）----

    /** 合法 TS：擦除必须成功（不抛），且剩余代码仍是非空可解析 JS。 */
    private static void assertErasesAndParses(String source, String survivingCode) {
        String erased = NekoTypeScriptCompiler.eraseTypescript(FILE, source);
        assertTrue(erased.contains(survivingCode),
                "合法 TS 擦除不得吞掉后续代码 " + survivingCode + "：\n" + erased);
        CompilerExecutionAssertions.parse(erased);
    }

    @Test
    void wellFormedInterfaceStillErases() {
        assertErasesAndParses("interface Foo { bar: string }\nconst keep = 1;\n", "const keep = 1;");
    }

    @Test
    void wellFormedTypeAliasStillErases() {
        assertErasesAndParses("type T = string\nconst keep = 1;\n", "const keep = 1;");
        assertErasesAndParses("type T = { a: string }\nconst keep = 1;\n", "const keep = 1;");
    }

    @Test
    void genericFunctionStillErasesAndRuns() {
        String erased = NekoTypeScriptCompiler.eraseTypescript(FILE,
                "function id<T>(x: T): T { return x }\nglobalThis.result = id(41) + 1;\n");
        try (CompilerExecutionAssertions.Evaluation eval = CompilerExecutionAssertions.eval(erased + "\nglobalThis.result")) {
            assertEquals(42, eval.value().asInt(), "泛型函数擦除后必须可执行：\n" + erased);
        }
    }

    @Test
    void genericArrowStillErasesAndRuns() {
        String erased = NekoTypeScriptCompiler.eraseTypescript(FILE,
                "const id = <T,>(x: T) => x\nglobalThis.result = id(41) + 1;\n");
        try (CompilerExecutionAssertions.Evaluation eval = CompilerExecutionAssertions.eval(erased + "\nglobalThis.result")) {
            assertEquals(42, eval.value().asInt(), "泛型箭头擦除后必须可执行：\n" + erased);
        }
    }

    @Test
    void multiLineGenericTypeAnnotationStillErases() {
        assertErasesAndParses("const m: Map<\n  string,\n  number\n> = new Map();\nconst keep = 1;\n", "const keep = 1;");
        assertErasesAndParses("const m: Map<string, number> = new Map();\nconst keep = 1;\n", "const keep = 1;");
    }

    @Test
    void trailingTypeAnnotationAtEndOfFileStillErases() {
        assertErasesAndParses("let x: number", "let x");
    }

    @Test
    void wellFormedClassWithAbstractMembersStillErases() {
        assertErasesAndParses(
                "abstract class Base {\n"
                + "  abstract name: string;\n"
                + "  abstract move(): void;\n"
                + "  concrete(): number { return 1; }\n"
                + "}\n"
                + "class Impl extends Base {\n"
                + "  name = 'x';\n"
                + "  move() {}\n"
                + "}\n",
                "class Impl extends Base");
    }
}
