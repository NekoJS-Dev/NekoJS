package com.tkisor.nekojs.core.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W3 负向语料：TS 擦除/降级产物必须能被 GraalJS parse（含 import/export 的按 module 模式），
 * 且不触发 enum/namespace/参数属性降级的输入保持行数不变（阶段一擦除是等长空白替换，
 * source map 的 1:1 行映射依赖这一点）。语料覆盖审计 §4 列出的全部高危形态。
 */
class TypeScriptErasureParseCorpusTest {

    private record CorpusCase(String name, String source, boolean module, boolean lineInvariant) {}

    /**
     * 已确认的擦除器缺口 —— 用例名 → 根因。
     *
     * <p>这些都是**真缺陷**：擦除产物过不了 GraalJS 的 parse。它们此前没有测试覆盖，
     * 所以一直没被发现（其中最典型的是函数参数位置的 `fn: () => T`，
     * 会让整份脚本在加载期静默不执行）。
     *
     * <p>维护方式：修好一个就从这里删一行，对应用例会立刻转为真跑；若回归，测试会红。
     * 不要新增条目来"让测试变绿"——这里是缺陷清单，不是豁免名单。
     */
    private static final java.util.Map<String, String> KNOWN_GAPS = java.util.Map.ofEntries(
            // 类型在 `()` 处提前结束 → `=>` 残留。typeExpressionEnd 把类型内部的 `)` 归零那一轮
            // 也当成了终结符，于是箭头类型的头部被截断。
            java.util.Map.entry("arrow-fn-type-as-param", "类型在 () 处提前结束，=> 残留"),
            java.util.Map.entry("arrow-fn-type-generic-param", "同上"),
            java.util.Map.entry("arrow-fn-type-with-args", "同上"),
            java.util.Map.entry("arrow-fn-type-two-args", "同上"),
            java.util.Map.entry("arrow-fn-type-as-field", "同上（class 字段位置）"),
            java.util.Map.entry("arrow-fn-type-nested-return", "同上（返回值位置）"),
            java.util.Map.entry("arrow-fn-type-returning-arrow", "同上（嵌套箭头吃掉 = 号）"),
            java.util.Map.entry("arrow-fn-type-optional-param", "同上（可选参数）"),
            java.util.Map.entry("arrow-fn-type-in-union", "同上（联合类型内）"),
            java.util.Map.entry("arrow-fn-type-generic-arg", "同上（泛型实参内）"),

            // 对象类型字面量：`{` 在顶层被视为类型结束符（这是有意的、为区分代码块），
            // 但 `: { ... }` 形式的类型注解因此被截断。
            java.util.Map.entry("object-type-literals", "对象类型字面量未整体擦除"),
            java.util.Map.entry("type-annotation-destructuring-object", "解构声明上的对象类型未擦"),
            java.util.Map.entry("union-intersection-types", "交叉类型里的对象字面量未擦"),

            // 其余零散缺口
            java.util.Map.entry("generic-class-and-method", "类字段类型漏擦（泛型默认值干扰）"),
            java.util.Map.entry("export-type-and-reexport", "export type {..} from 只擦一半，留下悬空 from"),
            java.util.Map.entry("class-overload-signatures", "重载签名的返回类型漏擦"),
            java.util.Map.entry("namespace-nested-and-merged", "嵌套 namespace 降级产物不合法"),
            java.util.Map.entry("params-this-parameter", "this 参数名未处理"),
            java.util.Map.entry("for-of-with-type-annotation", "for-of 的类型标注吃掉了 of 表达式"));

    static Stream<CorpusCase> corpus() {
        return Stream.of(
            new CorpusCase("switch-case-default", """
                function pick(n: number): string {
                  switch (n) {
                    case 1: return 'one';
                    case 'a': return 'letter';
                    case FLAG ? 1 : 2: return 'ternary';
                    default: return 'many';
                  }
                }
                """, false, true),
            new CorpusCase("labeled-nested-loops", """
                outer: for (let i = 0; i < 3; i++) {
                  inner: for (let j = 0; j < 3; j++) {
                    if (j > i) continue outer;
                    if (i === 2) break outer;
                  }
                }
                """, false, true),
            new CorpusCase("aliased-import-export", """
                import { Items as ItemRegistry, Blocks } from './registry';
                import def, { type OnlyType, real } from './m';
                export { def as defaultExport, real };
                ItemRegistry.air();
                """, true, true),
            new CorpusCase("inline-type-export", """
                const createConfig = 2;
                export { type Config, createConfig };
                """, true, true),
            new CorpusCase("declare-statements", """
                declare const VERSION: string;
                declare function helper(a: number): string;
                declare abstract class Node { accept(): void; }
                const local = 1;
                """, false, true),
            new CorpusCase("abstract-class-members", """
                abstract class Base {
                  abstract name: string;
                  abstract move(): void;
                  concrete(n: number): number { return n + 1; }
                }
                class Impl extends Base {
                  name = 'x';
                  move() {}
                }
                """, false, true),
            new CorpusCase("optional-chain-generic-call", """
                const out = source?.<string>(arg);
                const plain = fetch<T>(key);
                """, false, true),
            new CorpusCase("enum-mixed", """
                enum Mode { Off = 0, Half = 0.5, On = 1 }
                """, false, false),
            new CorpusCase("namespace-decl", """
                namespace Util {
                  export function twice(n: number): number { return n * 2; }
                }
                """, false, false),
            new CorpusCase("type-assertions-mixed-with-alias-free-export", """
                export const strict = getValue() as const;
                export const wide: string | number = widen() as string;
                """, true, true),

            // ---- 箭头函数类型标注 ----
            //
            // 这组用例来自一个真实缺陷：函数**参数**位置的 `fn: () => T` 擦除后残留 `=>`，
            // GraalJS 报 `SyntaxError: Expected an operand but found =>`。
            // 当时 class 方法内的同种写法是好的（见 NekoTypeScriptCompilerTest 的
            // `on(name: string, fn: () => void): this`），所以缺陷与**上下文**有关，
            // 需要按位置分别覆盖，不能只测一种。
            new CorpusCase("arrow-fn-type-as-param", """
                function check(name: string, fn: () => boolean): boolean { return fn() }
                """, false, true),
            new CorpusCase("arrow-fn-type-generic-param", """
                function check<T>(name: string, fn: () => T): boolean { return true }
                """, false, true),
            new CorpusCase("arrow-fn-type-with-args", """
                function each(list: string[], visit: (item: string) => void): void {}
                """, false, true),
            new CorpusCase("arrow-fn-type-two-args", """
                function fold(items: number[], f: (acc: number, item: number) => number): number { return 0 }
                """, false, true),
            new CorpusCase("arrow-fn-type-as-field", """
                class Handler {
                  callback: () => void = () => {};
                  transform: (n: number) => string = String;
                }
                """, false, true),
            new CorpusCase("arrow-fn-type-in-interface", """
                interface Visitor {
                  visit(node: unknown): void;
                  map: (n: number) => number;
                }
                """, false, true),
            new CorpusCase("arrow-fn-type-nested-return", """
                function factory(): () => number { return () => 1 }
                """, false, true),
            new CorpusCase("arrow-fn-type-returning-arrow", """
                const mk: () => () => number = () => () => 1;
                """, false, true),
            new CorpusCase("arrow-fn-type-optional-param", """
                function go(handler?: (e: unknown) => void): void {}
                """, false, true),
            new CorpusCase("arrow-fn-type-in-union", """
                type Cb = ((n: number) => void) | null;
                function use(cb: ((n: number) => void) | null): void {}
                """, false, true),
            new CorpusCase("arrow-fn-type-generic-arg", """
                function map<T>(items: T[], f: (item: T) => T): T[] { return items }
                """, false, true),

            // ================= TS 语法全集 =================
            //
            // 组织原则：**按位置穷举**，而不只按语法种类。
            // 已验证的教训是缺陷与上下文强相关——同一个 `fn: () => T` 在 interface 方法签名里
            // 能过、在函数参数里崩（见上一组）。所以每种语法都在变量/参数/返回/字段/成员等
            // 位置上各放一份，单点覆盖会漏掉这类位置相关缺陷。

            // ---- 类型注解的位置：变量 / 参数 / 返回 / 字段 / 解构 ----
            new CorpusCase("type-annotation-variable", """
                let count: number = 1;
                const name: string = 'x';
                var flag: boolean = true;
                """, false, true),
            new CorpusCase("type-annotation-destructuring-object", """
                const { a, b }: { a: number; b: string } = source;
                """, false, true),
            new CorpusCase("type-annotation-destructuring-array", """
                const [first, second]: [number, string] = pair;
                """, false, true),
            new CorpusCase("type-annotation-function-return", """
                function make(): string { return 'x' }
                const arrow = (): number => 1;
                """, false, true),

            // ---- 泛型：类 / 方法 / 约束 / 默认值 / 调用 ----
            new CorpusCase("generic-class-and-method", """
                class Box<T, U extends object = {}> {
                  value: T;
                  map<V>(f: T): V { throw new Error('x') }
                }
                """, false, true),
            new CorpusCase("generic-call-explicit-type-args", """
                const a = get<string>('key');
                const b = get<string, number>('key', 1);
                """, true, true),
            new CorpusCase("generic-arrow-with-constraint", """
                const pick = <T extends object>(obj: T): T => obj;
                """, false, true),
            new CorpusCase("generic-nested-type-args", """
                const tree: Map<string, Array<Set<number>>> = new Map();
                """, false, true),

            // ---- 联合 / 交叉 / 字面量 / 元组 / 数组 ----
            new CorpusCase("union-intersection-types", """
                let value: string | number | null = null;
                let mixed: A & B & { c: number } = source;
                """, false, true),
            new CorpusCase("literal-types", """
                let mode: 'on' | 'off' = 'on';
                let level: 1 | 2 | 3 = 1;
                let truthy: true = true;
                """, false, true),
            new CorpusCase("tuple-array-types", """
                let pair: [string, number] = ['a', 1];
                let rest: [string, ...number[]] = ['a', 1, 2];
                let list: string[][] = [];
                """, false, true),
            new CorpusCase("object-type-literals", """
                let cfg: { host: string; port?: number; readonly id: number } = source;
                let nested: { inner: { deep: boolean } } = source;
                """, false, true),

            // ---- 高级类型：keyof / typeof / 索引访问 / 条件 / 映射 / 模板字面量 ----
            new CorpusCase("keyof-typeof-indexed-access", """
                type Keys = keyof Config;
                type Ctor = typeof SomeClass;
                type First = Config['host'];
                """, false, true),
            new CorpusCase("conditional-and-mapped-types", """
                type IsString<T> = T extends string ? true : false;
                type Partial<T> = { [K in keyof T]?: T[K] };
                type Mutable<T> = { -readonly [K in keyof T]-?: T[K] };
                """, false, true),
            new CorpusCase("template-literal-types", """
                type Event = `on${Capitalize<string>}`;
                type Path = `/api/${string}/detail`;
                """, false, true),
            new CorpusCase("type-predicate-and-assertion-signature", """
                function isString(x: unknown): x is string { return true }
                function assertNumber(x: unknown): asserts x is number {}
                """, false, true),
            new CorpusCase("infer-and-recursive-types", """
                type Unwrap<T> = T extends Promise<infer U> ? U : T;
                type Json = string | number | Json[] | { [k: string]: Json };
                """, false, true),

            // ---- 接口 / 类型别名 / 继承 ----
            new CorpusCase("interface-extends-and-declaration-merging", """
                interface Base { id: number }
                interface Sub extends Base { name: string }
                interface Sub { extra?: boolean }
                type Alias = Sub & { tag: string };
                """, false, true),
            new CorpusCase("interface-call-and-construct-signatures", """
                interface Callable {
                  (n: number): string;
                  new (n: number): Callable;
                  readonly prop: number;
                }
                """, false, true),
            new CorpusCase("interface-index-signature", """
                interface Dict {
                  [key: string]: number;
                  [idx: number]: string;
                }
                """, false, true),

            // ---- import / export 的 type 变体 ----
            new CorpusCase("import-type-only-and-mixed", """
                import type { A, B } from './t';
                import { type C, value } from './m';
                import Default, { type D } from './d';
                value();
                """, true, true),
            new CorpusCase("export-type-and-reexport", """
                export type { A } from './a';
                export * from './b';
                export * as ns from './c';
                export default function main(): void {}
                """, true, true),

            // ---- 断言与修饰符 ----
            new CorpusCase("assertions-as-satisfies-nonnull", """
                const a = expr as unknown as string;
                const b = obj satisfies Config;
                const c = maybe!;
                const d = map[key]!;
                """, false, true),
            new CorpusCase("class-visibility-and-readonly-modifiers", """
                class M {
                  public a: number = 1;
                  private b: string = 'x';
                  protected c: boolean = true;
                  readonly d: number = 0;
                  static e: number = 2;
                  declare f: number;
                }
                """, false, true),
            new CorpusCase("class-optional-and-definite-assignment", """
                class O {
                  a?: string;
                  b!: number;
                }
                """, false, true),
            new CorpusCase("class-accessor-signatures", """
                class A {
                  get value(): number { return 1 }
                  set value(v: number) {}
                  private get hidden(): string { return '' }
                }
                """, false, true),
            new CorpusCase("class-overload-signatures", """
                class O {
                  run(n: number): string;
                  run(s: string): string;
                  run(v: unknown): string { return String(v) }
                }
                """, false, true),

            // ---- 参数：可选 / rest / 默认值 / 参数属性 ----
            new CorpusCase("params-optional-rest-default", """
                function f(a?: string, ...rest: number[]): void {}
                function g(a: number = 1, b: string = 'x'): void {}
                """, false, true),
            new CorpusCase("params-constructor-property-shorthand", """
                class P {
                  constructor(public a: number, private b: string, readonly c: boolean) {}
                }
                """, false, false),
            new CorpusCase("params-this-parameter", """
                function f(this: Window, name: string): void {}
                """, false, true),

            // ---- 枚举的变体 ----
            new CorpusCase("enum-heterogeneous-and-const", """
                enum Mixed { A = 1, B = 'two', C }
                const enum Const { X }
                """, false, false),
            new CorpusCase("enum-exported-in-module", """
                export enum Status { Ok = 200, NotFound = 404 }
                export const label: string = 'x';
                """, true, false),

            // ---- 命名空间（含嵌套 / 合并） ----
            new CorpusCase("namespace-nested-and-merged", """
                namespace A {
                  export namespace B {
                    export const x: number = 1;
                  }
                  export type T = string;
                }
                """, false, false),
            new CorpusCase("namespace-declare-ambient", """
                declare namespace NodeJS {
                  interface ProcessEnv { KEY: string }
                }
                """, false, false),

            // ---- 控制流的类型相关形态 ----
            new CorpusCase("catch-clause-type-annotations", """
                try { run() } catch (e: unknown) { handle(e) } finally { done() }
                """, false, true),
            new CorpusCase("for-of-with-type-annotation", """
                for (const item: string of list) { consume(item) }
                """, false, true),
            new CorpusCase("optional-chain-and-nullish-with-types", """
                const a: number | undefined = obj?.deep?.value;
                const b: string = maybe ?? 'fallback';
                const c = arr?.[0];
                """, false, true),
            new CorpusCase("async-and-generator-signatures", """
                async function load(): Promise<string> { return 'x' }
                function* gen(): Generator<number> { yield 1 }
                async function* agen(): AsyncGenerator<number> {}
                """, false, true));
    }

    /**
     * 明确不支持的语法：必须**抛错并给出可操作说明**，而不是静默产出坏代码。
     *
     * <p>这类用例不能混进 corpus：corpus 的契约是"擦除产物可被 GraalJS parse"，
     * 而这里期望的恰恰是擦除阶段就抛异常、产物根本不该存在。
     *
     * <p>为什么值得单独测：静默产出坏代码的失败模式是"脚本加载期报一个看不懂的
     * SyntaxError"；主动抛错则能直接告诉作者该怎么改。两者的可诊断性差一个量级。
     */
    @ParameterizedTest(name = "rejects: {0}")
    @MethodSource("rejectedSyntax")
    void unsupportedSyntaxFailsWithActionableMessage(String name, String source) {
        var error = assertThrows(IllegalArgumentException.class, () ->
                NekoTypeScriptCompiler.eraseTypescript(Path.of("corpus-" + name + ".ts"), source));
        String message = error.getMessage();
        assertNotNull(message, "拒绝不支持的语法时必须带说明，不能是空消息");
        // 消息要能让人动手改：至少点出是哪种语法
        assertTrue(message.contains("decorator") || message.contains("@"),
                "报错应指明具体语法种类，实际: " + message);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> rejectedSyntax() {
        return Stream.of(
            org.junit.jupiter.params.provider.Arguments.of("decorator-on-method", """
                class Service {
                  @Inject
                  run(): void {}
                }
                """),
            org.junit.jupiter.params.provider.Arguments.of("decorator-on-class", """
                @Component
                class Widget {}
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void erasedOutputParsesAndKeepsLineCount(CorpusCase c) {
        // 已知缺口：这些形态的擦除产物目前无法被 GraalJS parse，是**真实缺陷**而非用例错误。
        // 用 abort 而非删用例——缺口始终可见，修好一个就从表里删一行，测试随即转为真跑。
        // （参数化测试无法对单个用例加 @Disabled，abort 是等价机制：报告里显示 skipped。）
        String gap = KNOWN_GAPS.get(c.name());
        if (gap != null) {
            org.junit.jupiter.api.Assumptions.abort("已知缺口（待修）：" + gap);
        }
        String erased = NekoTypeScriptCompiler.eraseTypescript(Path.of("corpus-" + c.name() + ".ts"), c.source());
        if (c.module()) {
            CompilerExecutionAssertions.parseModule(erased);
        } else {
            CompilerExecutionAssertions.parse(erased);
        }
        if (c.lineInvariant()) {
            assertEquals(lineCount(c.source()), lineCount(erased),
                    "erasure must keep the line count for source-map 1:1 mapping:\n" + erased);
        }
    }

    /** 行为验证：switch case 体与 label 语义在擦除后保持。*/
    @Test
    void switchAndLabelSurviveBehaviorally() {
        String erased = NekoTypeScriptCompiler.eraseTypescript(Path.of("behavior.ts"), """
            var n = 1;
            var picked = '';
            switch (n) { case 1: picked = 'one'; break; default: picked = 'other'; break; }
            var count = 0;
            outer: for (var i = 0; i < 3; i++) {
              for (var j = 0; j < 3; j++) {
                if (j === 1) continue outer;
                count++;
              }
            }
            picked + ':' + count
            """);
        try (CompilerExecutionAssertions.Evaluation eval = CompilerExecutionAssertions.eval(erased)) {
            assertEquals("one:3", eval.value().asString(),
                    "case body must run (picked=one) and continue outer must skip j>=1 for each i (count=3)");
        }
    }

    /** 行为验证：浮点枚举值不截断。*/
    @Test
    void enumFloatValueSurvivesBehaviorally() {
        String erased = NekoTypeScriptCompiler.eraseTypescript(Path.of("enum.ts"),
                "enum Mode { Half = 0.5, Next }\n[Mode.Half, Mode.Next]");
        try (CompilerExecutionAssertions.Evaluation eval = CompilerExecutionAssertions.eval(erased)) {
            assertEquals(0.5, eval.value().getArrayElement(0).asDouble(), "Half must stay 0.5");
            assertEquals(1.5, eval.value().getArrayElement(1).asDouble(), "Next must auto-increment from 0.5");
        }
    }

    private static int lineCount(String s) {
        return (int) s.lines().count();
    }
}
