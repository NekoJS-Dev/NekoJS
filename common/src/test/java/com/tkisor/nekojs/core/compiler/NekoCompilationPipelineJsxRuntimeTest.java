package com.tkisor.nekojs.core.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NekoCompilationPipelineJsxRuntimeTest {

    @Test
    void sharedJsxPluginAndPipelineUseTheRuntimeRequestedForEachCompilation() throws Exception {
        NekoCompilationPipeline pipeline = new NekoCompilationPipeline();
        NekoLanguagePlugin jsx = NekoJsxLanguagePlugin.INSTANCE;

        String classicBefore = pipeline.compile(
            Path.of("classic-before.jsx"), "const node = <div/>", ".jsx", jsx).code();
        String automaticTsx = pipeline.compile(
            Path.of("automatic.tsx"), "const node: unknown = <div/>", ".tsx", jsx, true).code();
        String classicAfter = pipeline.compile(
            Path.of("classic-after.jsx"), "const node = <div/>", ".jsx", jsx).code();

        assertClassic(classicBefore);
        assertAutomatic(automaticTsx);
        assertClassic(classicAfter);
    }

    private static void assertClassic(String output) {
        assertTrue(output.contains("globalThis.__nekoJsxFactory("), "expected classic JSX output: " + output);
        assertFalse(output.contains("from 'nekojs/jsx-runtime'"), "classic output must not import the automatic runtime: " + output);
    }

    private static void assertAutomatic(String output) {
        assertTrue(output.contains("from 'nekojs/jsx-runtime'"), "expected automatic JSX runtime import: " + output);
        assertTrue(output.contains("jsx("), "expected automatic JSX call: " + output);
        assertFalse(output.contains("globalThis.__nekoJsxFactory("), "automatic output must not use the classic factory: " + output);
    }
}
