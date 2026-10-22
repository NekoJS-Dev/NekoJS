package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptSourcePreflightTest {
    public static class FixtureBinding {
        public void configure(Consumer<FixtureConfig> configure) {}
    }

    public static class FixtureConfig {
        public void valid() {}
    }

    public static class ExtendedBinding extends FixtureBinding {
        public void missing() {}
    }

    @BeforeAll
    static void initializePlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void sharedParsePreservesBothValidatorsDiagnosticsAndLocations() {
        Path file = file("combined");
        for (String source : List.of(
                "PreflightFixture.configure(config => config.valid());",
                "// Unicode: 猫\r\nPreflightFixture.missing();\r\n"
                        + "PreflightFixture.configure(config => { const alias = config; alias.missing(); });",
                "PreflightFixture.configure(function(config) { config['missing'](); });",
                "function nested() { PreflightFixture.configure(config => { config.missing(); }); }")) {
            assertEquals(validate(file, source, false, false), validate(file, source, true, false), source);
        }
        List<Observed> diagnostics = validate(file,
                "PreflightFixture.missing(); PreflightFixture.configure(config => config.missing());", true, false);
        assertTrue(diagnostics.stream().anyMatch(item -> item.kind().startsWith("binding-preflight")));
        assertTrue(diagnostics.stream().anyMatch(item -> item.kind().equals("event-cb-preflight")));
    }

    @Test
    void unchangedSourceUsesTheCurrentSchemaOnEveryCall() {
        Path file = file("schema-change");
        String source = "PreflightFixture.missing();";
        assertFalse(validate(file, source, true, false).isEmpty());
        assertTrue(validate(file, source, true, true).isEmpty());
        assertFalse(validate(file, source, true, false).isEmpty());
    }

    @Test
    void reusableSourceFactsKeepDiagnosticsCurrentAndFileSpecific() {
        String source = "PreflightFixture.missing(); PreflightFixture.configure(config => config.missing());";
        try (var cache = new ScriptSourcePreflight.Cache()) {
            List<Observed> expected = validate(file("cached"), source, true, false);
            assertFalse(expected.isEmpty());
            assertEquals(expected, validate(cache, file("cached"), source, false));
            assertEquals(expected, validate(cache, file("cached"), source, false));
            String bindingOnly = "PreflightFixture.missing();";
            assertFalse(validate(cache, file("current"), bindingOnly, false).isEmpty());
            assertTrue(validate(cache, file("current"), bindingOnly, true).isEmpty());
            assertFalse(validate(cache, file("current"), bindingOnly, false).isEmpty());
            assertEquals(validate(file("other"), source, true, false),
                    validate(cache, file("other"), source, false));
            assertTrue(validate(cache, file("cached"),
                    "PreflightFixture.configure(config => config.valid());", false).isEmpty());
        }
    }

    @Test
    void sourceEvictionInvalidationAndCloseNeverSuppressCurrentDiagnostics() {
        String source = "PreflightFixture.missing();";
        var cache = new ScriptSourcePreflight.Cache(1, source.length());
        List<Observed> expected = validate(file("evicted"), source, true, false);
        assertEquals(expected, validate(cache, file("evicted"), source, false));
        assertFalse(validate(cache, file("replacement"), source, false).isEmpty());
        assertEquals(expected, validate(cache, file("evicted"), source, false));
        cache.invalidate(file("evicted")::equals);
        assertEquals(expected, validate(cache, file("evicted"), source, false));
        assertFalse(validate(cache, file("oversized"), source.repeat(2), false).isEmpty());
        cache.clear();
        assertTrue(validate(cache, file("evicted"), source, true).isEmpty());
        cache.close();
        assertEquals(expected, validate(cache, file("evicted"), source, false));
        cache.close();
    }

    @Test
    void sourceChangesAndDifferentFilesRetainTheirOwnDiagnostics() {
        String source = "PreflightFixture.configure(config => config.missing());";
        List<Observed> first = validate(file("first"), source, true, false);
        List<Observed> second = validate(file("second"), source, true, false);
        assertFalse(first.isEmpty());
        assertFalse(second.isEmpty());
        assertTrue(first.stream().allMatch(item -> item.message().contains("first.js")));
        assertTrue(second.stream().allMatch(item -> item.message().contains("second.js")));
        assertTrue(validate(file("first"),
                "PreflightFixture.configure(config => config.valid());", true, false).isEmpty());
    }

    @Test
    void missingInputAndEmptySchemaKeepValidationOptional() {
        assertDoesNotThrow(() -> ScriptSourcePreflight.validate(null, null, null));
        assertDoesNotThrow(() -> ScriptSourcePreflight.validate(file("empty"), "", null));
        var diagnostics = new ArrayList<ScriptBindingSchema.Diagnostic>();
        var emptyView = new ScriptBindingSchema.View(Map.of(), Set.of(), diagnostics::add);
        ScriptSourcePreflight.validate(file("no-schema"), "PreflightFixture.missing();", emptyView);
        assertTrue(diagnostics.isEmpty());
    }

    private static List<Observed> validate(Path file, String source, boolean combined, boolean allowMissing) {
        return validate(null, file, source, combined, allowMissing);
    }

    private static List<Observed> validate(ScriptSourcePreflight.Cache cache, Path file,
                                           String source, boolean allowMissing) {
        return validate(cache, file, source, true, allowMissing);
    }

    private static List<Observed> validate(ScriptSourcePreflight.Cache cache, Path file,
                                           String source, boolean combined, boolean allowMissing) {
        var diagnostics = new ArrayList<Observed>();
        Set<String> members = allowMissing ? Set.of("configure", "missing") : Set.of("configure");
        var view = new ScriptBindingSchema.View(Map.of("PreflightFixture",
                new ScriptBindingSchema.BindingMembers(members,
                        Set.of(allowMissing ? ExtendedBinding.class : FixtureBinding.class))),
                Set.of("console"), item -> diagnostics.add(new Observed(
                        item.type(), item.callbackKind(), item.throwable().getMessage())));
        if (cache != null) {
            cache.validate(file, source, view);
        } else if (combined) {
            ScriptSourcePreflight.validate(file, source, view);
        } else {
            GlobalBindingMemberValidator.validate(file, source, view);
            EventCallbackSourceValidator.validate(file, source, view);
        }
        return List.copyOf(diagnostics);
    }

    private static Path file(String name) {
        return ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve(name + ".js");
    }

    private record Observed(ScriptType type, String kind, String message) {}
}
