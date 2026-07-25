package com.tkisor.nekojs.core.fs;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class JSConfigModelTest {
    private static final Gson GSON = new Gson();

    @Test
    void defaultsToClassicJsxConfiguration() {
        JSConfigModel.CompilerOptions options = new JSConfigModel().compilerOptions;

        assertEquals("react", options.jsx);
        assertEquals("__nekoJsxFactory", options.jsxFactory);
        assertEquals("__nekoJsxFragment", options.jsxFragmentFactory);
        assertNull(options.jsxImportSource);
        assertFalse(options.experimentalDecorators);
    }

    @Test
    void automaticJsxRuntimeUsesTypeScriptAutomaticRuntimeSettings() {
        JSConfigModel model = new JSConfigModel();

        model.useAutomaticJsxRuntime();

        JSConfigModel.CompilerOptions options = model.compilerOptions;
        assertEquals("react-jsx", options.jsx);
        assertEquals("nekojs", options.jsxImportSource);
        assertNull(options.jsxFactory);
        assertNull(options.jsxFragmentFactory);
        assertFalse(options.experimentalDecorators);

        String json = GSON.toJson(model);
        assertFalse(json.contains("jsxFactory"));
        assertFalse(json.contains("jsxFragmentFactory"));
    }
}
