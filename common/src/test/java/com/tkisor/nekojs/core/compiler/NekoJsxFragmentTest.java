package com.tkisor.nekojs.core.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NekoJsxFragmentTest {

    @Test
    void lowersEmptyAndNestedClassicFragments() {
        String empty = NekoJsxCompiler.compileJsx(Path.of("empty.jsx"),
            "globalThis.result = <></>").code();
        String nested = NekoJsxCompiler.compileJsx(Path.of("nested.jsx"),
            "globalThis.result = <><>inner</><span/></>").code();
        String runtime = "globalThis.__nekoJsxFactory = (type, props, ...children) => ({ type, children });\n"
            + "globalThis.__nekoJsxFragment = (...children) => children;\n";

        try (CompilerExecutionAssertions.Evaluation evaluation =
                 CompilerExecutionAssertions.eval(runtime + empty + "; JSON.stringify(globalThis.result)")) {
            assertEquals("[]", evaluation.value().asString());
        }
        try (CompilerExecutionAssertions.Evaluation evaluation =
                 CompilerExecutionAssertions.eval(runtime + nested + "; JSON.stringify(globalThis.result)")) {
            assertEquals("[[\"inner\"],{\"type\":\"span\",\"children\":[]}]", evaluation.value().asString());
        }
    }

    @Test
    void lowersFragmentInsideExpressionChild() {
        String out = NekoJsxCompiler.compileJsx(Path.of("expression.jsx"),
            "globalThis.result = <div>{true && <>value</>}</div>").code();
        String runtime = "globalThis.__nekoJsxFactory = (type, props, ...children) => ({ type, children });\n"
            + "globalThis.__nekoJsxFragment = (...children) => children;\n";

        try (CompilerExecutionAssertions.Evaluation evaluation =
                 CompilerExecutionAssertions.eval(runtime + out + "; JSON.stringify(globalThis.result)")) {
            assertEquals("{\"type\":\"div\",\"children\":[[\"value\"]]}", evaluation.value().asString());
        }
    }

    @Test
    void rejectsElementClosingTagForFragment() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> NekoJsxCompiler.compileJsx(Path.of("mismatch.jsx"), "const value = <>x</div>"));

        assertTrue(error.getMessage().contains("expected '</>'"), error.getMessage());
    }
}
