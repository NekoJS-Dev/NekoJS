package com.tkisor.nekojs.core.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TypeScript erasure output must parse in GraalJS, including module-mode import/export cases.
 * Cases that do not require enum, namespace, or parameter-property lowering also preserve line
 * count because phase-one erasure replaces source characters with equal-length whitespace.
 */
class TypeScriptErasureParseCorpusTest {

    private record CorpusCase(String name, String source, boolean module, boolean lineInvariant) {}

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
            new CorpusCase("keyof-typeof-indexed-access", """
                type Keys = keyof Config;
                type Ctor = typeof SomeClass;
                type First = Config['host'];
                """, false, true),
            new CorpusCase("conditional-and-mapped-types", """
                type IsString<T> = T extends string ? true : false;
                type Select<T> = T extends string ? { value: T } : { value: number };
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
                  [key: string]: string | number;
                  [idx: number]: number;
                }
                """, false, true),
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
                  reset();
                  reset(value: number): void;
                  reset(value?: number): void {}
                }
                """, false, true),
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
            new CorpusCase("enum-heterogeneous-and-const", """
                enum Mixed { A = 1, B = 'two', C = 3 }
                const enum Const { X }
                """, false, false),
            new CorpusCase("enum-exported-in-module", """
                export enum Status { Ok = 200, NotFound = 404 }
                export const label: string = 'x';
                """, true, false),
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
            new CorpusCase("catch-clause-type-annotations", """
                try { run() } catch (e: unknown) { handle(e) } finally { done() }
                """, false, true),
            new CorpusCase("for-of-with-typed-iterable", """
                for (const item of list as string[]) { consume(item) }
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


    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void erasedOutputParsesAndKeepsLineCount(CorpusCase c) {
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

    /** Confirms switch case bodies and labels retain their behavior after erasure. */
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

    /** Confirms floating-point enum values are not truncated during lowering. */
    @Test
    void enumFloatValueSurvivesBehaviorally() {
        String erased = NekoTypeScriptCompiler.eraseTypescript(Path.of("enum.ts"),
                "enum Mode { Half = 0.5, Next }\n[Mode.Half, Mode.Next]");
        try (CompilerExecutionAssertions.Evaluation eval = CompilerExecutionAssertions.eval(erased)) {
            assertEquals(0.5, eval.value().getArrayElement(0).asDouble(), "Half must stay 0.5");
            assertEquals(1.5, eval.value().getArrayElement(1).asDouble(), "Next must auto-increment from 0.5");
        }
    }

    @Test
    void callbackAndObjectTypeErasurePreserveValues() {
        String erased = NekoTypeScriptCompiler.eraseTypescript(Path.of("typed-values.ts"), """
            function apply(value: number, transform: (input: number) => number): number {
              return transform(value);
            }
            const config: { scale: number; label: string } = { scale: 3, label: 'three' };
            config.label + ':' + apply(config.scale, value => value * 2)
            """);

        try (CompilerExecutionAssertions.Evaluation eval = CompilerExecutionAssertions.eval(erased)) {
            assertEquals("three:6", eval.value().asString(), "type erasure must preserve callback and object values");
        }
    }

    @Test
    void indexedAccessAfterTypeAssertionPreservesValue() {
        String erased = NekoTypeScriptCompiler.eraseTypescript(Path.of("assertion-indexed-access.ts"), """
            const read = (target: { value: string }, key: string) =>
              (target as unknown as Record<string | symbol, unknown>)[key];
            read({ value: 'stone' }, 'value')
            """);

        try (CompilerExecutionAssertions.Evaluation eval = CompilerExecutionAssertions.eval(erased)) {
            assertEquals("stone", eval.value().asString(), "type assertions must end before the runtime member access");
        }
    }

    @Test
    void classMethodOverloadSignaturesPreserveImplementation() {
        String erased = NekoTypeScriptCompiler.eraseTypescript(Path.of("method-overload.ts"), """
            class Formatter {
              field = Number('8');
              format(value: number): string;
              format(value: string): string;
              format(value: number | string): string { return String(value); }
            }
            const formatter = new Formatter();
            formatter.format(7) + ':' + formatter.field
            """);

        try (CompilerExecutionAssertions.Evaluation eval = CompilerExecutionAssertions.eval(erased)) {
            assertEquals("7:8", eval.value().asString(), "overload signatures must disappear while calls and field initializers remain");
        }
    }

    @Test
    void thisParameterErasurePreservesCallBehavior() {
        String erased = NekoTypeScriptCompiler.eraseTypescript(Path.of("this-parameter.ts"), """
            function describe(this: { prefix: string }, value: string): string {
              return this.prefix + value;
            }
            describe.call({ prefix: 'item:' }, 'stone')
            """);

        try (CompilerExecutionAssertions.Evaluation eval = CompilerExecutionAssertions.eval(erased)) {
            assertEquals("item:stone", eval.value().asString(), "the TypeScript-only this parameter must not affect calls");
        }
    }

    @Test
    void nestedNamespaceExportsRemainAccessible() {
        String erased = NekoTypeScriptCompiler.eraseTypescript(Path.of("nested-namespace.ts"), """
            namespace Outer {
              export namespace Inner {
                export const value: number = 4;
              }
              export const offset: number = 2;
            }
            Outer.Inner.value + Outer.offset
            """);

        try (CompilerExecutionAssertions.Evaluation eval = CompilerExecutionAssertions.eval(erased)) {
            assertEquals(6, eval.value().asInt(), "nested namespace values must be attached to their parent namespace");
        }
    }

    @Test
    void namespaceRegexTextIsNotTreatedAsAnExport() {
        String erased = NekoTypeScriptCompiler.eraseTypescript(Path.of("namespace-regex.ts"), """
            namespace RegexHolder {
              export const pattern = /export namespace Ghost/;
            }
            RegexHolder.pattern.source
            """);

        try (CompilerExecutionAssertions.Evaluation eval = CompilerExecutionAssertions.eval(erased)) {
            assertEquals("export namespace Ghost", eval.value().asString(), "namespace lowering must leave regex contents unchanged");
        }
    }

    @Test
    void typedForOfIterablePreservesIteration() {
        String erased = NekoTypeScriptCompiler.eraseTypescript(Path.of("typed-for-of.ts"), """
            let total = 0;
            for (const item of [1, 2, 3] as number[]) total += item;
            total
            """);

        try (CompilerExecutionAssertions.Evaluation eval = CompilerExecutionAssertions.eval(erased)) {
            assertEquals(6, eval.value().asInt(), "erasing the iterable assertion must preserve the for-of expression");
        }
    }

    private static int lineCount(String s) {
        return (int) s.lines().count();
    }
}
