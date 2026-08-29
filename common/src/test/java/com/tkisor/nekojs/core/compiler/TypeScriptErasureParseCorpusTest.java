package com.tkisor.nekojs.core.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W3 负向语料：TS 擦除/降级产物必须能被 GraalJS parse（含 import/export 的按 module 模式），
 * 且不触发 enum/namespace/参数属性降级的输入保持行数不变（阶段一擦除是等长空白替换，
 * source map 的 1:1 行映射依赖这一点）。语料覆盖审计 §4 列出的全部高危形态。
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
                """, true, true));
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
