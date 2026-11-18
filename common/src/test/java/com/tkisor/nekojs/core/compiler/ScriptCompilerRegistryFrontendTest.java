package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.FrontendSemantics;
import com.tkisor.nekojs.core.compiler.python.PythonTranspilerPlugin;
import com.tkisor.nekojs.core.plugin.NekoCommonBuiltinPlugin;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptCompilerRegistryFrontendTest {
    private static final FrontendSemantics JAVASCRIPT = new FrontendSemantics(
            FrontendSemantics.JAVASCRIPT_SOURCE, FrontendSemantics.JAVASCRIPT_OUTPUT, Set.of());

    @Test
    void namedLegacyImplementationsWithSameLanguageRemainIndividuallySelectable() throws Exception {
        var registry = ScriptCompilerRegistry.createRuntimeRegistry();
        var first = compiler("first");
        var second = compiler("second");
        registry.registerFrontend("example:first", "1", language(first), JAVASCRIPT);
        registry.registerFrontend("example:second", "2", language(second), JAVASCRIPT);
        registry.freeze();

        var one = registry.capture(".JS", "example:first");
        var two = registry.capture("js", "example:second");
        assertEquals(one.language().id(), two.language().id());
        assertEquals(registry.revision(), one.revision());
        assertEquals(one.revision(), two.revision());
        assertNotEquals(one.frontend(), two.frontend());
        assertSame(first, one.compiler());
        assertSame(second, two.compiler());
        assertEquals("first", one.compiler().compile(Path.of("entry.js"), ""));
        assertEquals("second", two.compiler().compile(Path.of("entry.js"), ""));
        assertEquals("2", two.frontend().implementationRevision());
        assertEquals(two, registry.capture(".js"));
        assertEquals(Set.of(".js"), two.frontend().extensions());
        assertThrows(UnsupportedOperationException.class, () -> registry.frontends().clear());
        assertThrows(UnsupportedOperationException.class, () -> two.frontend().extensions().clear());
    }

    @Test
    void replacementChangesOnlyAutomaticSelectionAndKeepsBuiltinCandidates() {
        var registry = ScriptCompilerRegistry.createRuntimeRegistry();
        new NekoCommonBuiltinPlugin().registerScriptCompilers(registry);
        new PythonTranspilerPlugin().registerScriptCompilers(registry);
        assertEquals(List.of("nekojs:javascript", "nekojs:typescript", "nekojs:jsx", "nekojs:tsx", "nekojs:python"),
                registry.frontends().stream().map(ScriptCompilerRegistry.FrontendDescriptor::id).toList());
        assertEquals("nekojs:typescript", registry.capture(".ts").frontend().id());
        var old = registry.capture(".ts");
        var override = compiler("legacy");
        registry.replaceLanguage("typescript", Set.of(".ts"), override);
        registry.register(override);
        registry.freeze();

        assertSame(override, registry.capture(".ts").compiler());
        assertNull(registry.capture(".ts").frontend());
        var builtin = registry.capture(".ts", "nekojs:typescript");
        assertSame(NekoTypeScriptLanguagePlugin.INSTANCE, builtin.plugin());
        assertEquals(old.frontend(), builtin.frontend());
        assertTrue(builtin.revision() > old.revision());
        var python = registry.capture(".py", "nekojs:python");
        assertEquals("nekojs:python", python.frontend().semantics().source().dialect());
        assertEquals(FrontendSemantics.JAVASCRIPT_OUTPUT, python.frontend().semantics().output());
        assertTrue(python.frontend().semantics().capabilities().contains(FrontendSemantics.PYTHON_AUTHORING));
    }

    @Test
    void nativeFallbackIsSelectableWithoutShadowingStandaloneLegacyCompiler() {
        var registry = ScriptCompilerRegistry.createRuntimeRegistry();
        assertNull(registry.getLanguage(".js"));
        assertNull(registry.capture(".js").plugin());
        assertEquals("nekojs:javascript", registry.capture(".js").frontend().id());

        var oldCompiler = compiler("old");
        registry.register(oldCompiler);
        registry.freeze();
        assertSame(oldCompiler, registry.capture(".js").compiler());
        assertNull(registry.capture(".js").frontend());
        var fallback = registry.capture(".js", "nekojs:javascript");
        assertSame(NekoJavaScriptLanguagePlugin.INSTANCE, fallback.plugin());
        assertEquals(JAVASCRIPT.source(), fallback.frontend().semantics().source());
        assertSame(NekoJavaScriptLanguagePlugin.INSTANCE, registry.capture(".mjs", "nekojs:javascript").plugin());
        assertSame(NekoJavaScriptLanguagePlugin.INSTANCE, registry.capture(".cjs", "nekojs:javascript").plugin());
    }

    @Test
    void invalidNamesAndIncompatibleSelectionsDoNotChangeTheBinding() {
        var registry = ScriptCompilerRegistry.createRuntimeRegistry();
        var source = language(compiler("fixture"));
        registry.registerFrontend("example:js", "1", source, JAVASCRIPT);
        long revision = registry.revision();
        assertThrows(IllegalArgumentException.class, () -> registry.registerFrontend("example:js", "2", source, JAVASCRIPT));
        assertThrows(IllegalArgumentException.class, () -> registry.registerFrontend("Example:js", "1", source, JAVASCRIPT));
        assertThrows(IllegalArgumentException.class, () -> registry.registerFrontend("nekojs:javascript", "1", source, JAVASCRIPT));
        assertThrows(IllegalArgumentException.class, () -> registry.registerFrontend("example:unknown", "1", source, null));
        registry.freeze();
        assertThrows(IllegalStateException.class, () -> registry.registerFrontend("example:late", "1", source, JAVASCRIPT));
        assertEquals(revision, registry.revision());

        var missing = assertThrows(IllegalArgumentException.class, () -> registry.capture(".js", "missing:id"));
        assertTrue(missing.getMessage().contains("[NEKO-1027]"));
        assertTrue(missing.getMessage().contains("example:js"));
        var wrongExtension = assertThrows(IllegalArgumentException.class, () -> registry.capture(".cjs", "example:js"));
        assertTrue(wrongExtension.getMessage().contains("does not support"));
        assertTrue(wrongExtension.getMessage().contains(".cjs"));
        assertEquals("example:js", registry.capture(".js").frontend().id());
    }

    private static NekoScriptLanguage language(IScriptCompiler compiler) {
        return new NekoScriptLanguage("javascript", Set.of("JS"), compiler);
    }

    private static IScriptCompiler compiler(String result) {
        return new IScriptCompiler() {
            @Override
            public boolean canCompile(String extension) {
                return extension.equals(".js") || extension.equals(".cjs");
            }

            @Override
            public String compile(Path file, String sourceCode) {
                return result;
            }
        };
    }
}
