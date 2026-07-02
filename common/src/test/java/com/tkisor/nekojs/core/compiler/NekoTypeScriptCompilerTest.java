package com.tkisor.nekojs.core.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
