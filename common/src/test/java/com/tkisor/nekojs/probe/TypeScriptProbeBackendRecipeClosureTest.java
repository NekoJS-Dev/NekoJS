package com.tkisor.nekojs.probe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

class TypeScriptProbeBackendRecipeClosureTest {
    public static class TickDuration { public long ticks; }
    public enum HeatCondition { NONE, HEATED }

    private static final String MODULE = "@package/com/tkisor/nekojs/probe/index.d.ts";
    private static final String DURATION = "$TypeScriptProbeBackendRecipeClosureTest$TickDuration";
    private static final String HEAT = "$TypeScriptProbeBackendRecipeClosureTest$HeatCondition";

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void emittedRecipeOnlyHostsAndEnumsHaveRealDeclarationsWithoutPrimarySeeds(@TempDir Path temp) {
        ProbeContext context = context(temp, List.of(), List.of(), null);
        assertTrue(context.collectedClasses().isEmpty());
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        String recipes = files.get("@side-only/server/events/recipes/index.d.ts");
        assertNotNull(recipes);
        assertTrue(recipes.contains(DURATION), recipes);
        assertTrue(recipes.contains(HEAT), recipes);
        String module = files.get(MODULE);
        assertNotNull(module, "Recipe imports must seed their actual package declarations");
        assertTrue(module.contains("class " + DURATION), module);
        assertTrue(module.contains("ticks: number"), module);
        assertTrue(module.contains(HEAT), module);
        assertTrue(module.contains("HEATED"), module);
        JsonObject report = report(files);
        assertEquals(0, report.get("primaryClassCount").getAsInt());
        assertFalse(report.getAsJsonObject("unavailable").has(TickDuration.class.getName()));
        assertFalse(report.getAsJsonObject("unavailable").has(HeatCondition.class.getName()));
    }

    @Test
    void excludedRecipeDependencyStaysUnavailableInsteadOfReceivingAFakeDeclaration(@TempDir Path temp) {
        ProbeContext context = context(temp,
                List.of("re:" + java.util.regex.Pattern.quote(TickDuration.class.getName())), List.of(), null);
        assertTrue(context.config().isExcluded(TickDuration.class.getName()));
        assertFalse(context.config().isExcluded(HeatCondition.class.getName()));
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        JsonObject report = report(files);
        assertFalse(report.get("complete").getAsBoolean());
        assertEquals("excluded", report.getAsJsonObject("unavailable").get(TickDuration.class.getName()).getAsString());
        assertFalse(files.getOrDefault(MODULE, "").contains("class " + DURATION));
        assertTrue(files.get(MODULE).contains(HEAT), "An excluded dependency must not discard other recipe hosts");
    }

    @Test
    void hiddenRecipeDependencyRetainsTheExistingHiddenTypePolicy(@TempDir Path temp) {
        TypeDecl hidden = new TypeReflector().reflect(TickDuration.class);
        hidden.hidden = true;
        Map<String, String> files = new TypeScriptProbeBackend().render(context(temp, List.of(), List.of(hidden), null));
        JsonObject report = report(files);
        assertFalse(report.get("complete").getAsBoolean());
        assertEquals("hidden", report.getAsJsonObject("unavailable").get(TickDuration.class.getName()).getAsString());
        assertFalse(files.getOrDefault(MODULE, "").contains("class " + DURATION));
    }

    @Test
    void rejectedRecipeProfilesDoNotForceUnusedHostDeclarations(@TempDir Path temp) {
        Map<String, String> files = new TypeScriptProbeBackend().render(context(temp, List.of(), List.of(),
                "Recipe profile is deliberately unsupported"));
        String module = files.getOrDefault(MODULE, "");
        assertFalse(module.contains(DURATION), module);
        assertFalse(module.contains(HEAT), module);
        assertTrue(files.get("@side-only/server/events/recipes/index.d.ts").contains("processing: never"));
    }

    @Test
    void nominalRecipeTypeSeedsItsDeclarationWithoutStructuralHostHints(@TempDir Path temp) {
        var context = context(temp, List.of(), List.of(), null, true);
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        String recipes = files.get("@side-only/server/events/recipes/index.d.ts");
        assertTrue(recipes.contains(DURATION), recipes);
        assertFalse(recipes.contains("fallback-only"), recipes);
        assertTrue(files.get(MODULE).contains("class " + DURATION), files.get(MODULE));
        assertEquals(0, report(files).get("primaryClassCount").getAsInt());
    }

    private static JsonObject report(Map<String, String> files) {
        return JsonParser.parseString(files.get("@special/dependencies.json")).getAsJsonObject();
    }

    private static ProbeContext context(Path temp, List<String> excluded, List<TypeDecl> declarations, String unsupported) {
        return context(temp, excluded, declarations, unsupported, false);
    }

    private static ProbeContext context(Path temp, List<String> excluded, List<TypeDecl> declarations, String unsupported,
                                        boolean nominalOnly) {
        var schema = new RecipeSchemaTypeEntry("fixture", "processing", "processing", List.of(
                new RecipeSchemaTypeEntry.SchemaField("duration", "JSON", true, false, "duration",
                        nominalOnly ? literal("fallback-only") : union(number(), host(TickDuration.class)),
                        nominalOnly ? com.tkisor.nekojs.api.surface.ApiTypeRef.symbol(
                                new com.tkisor.nekojs.api.surface.ApiSymbolId("java", TickDuration.class.getName())) : null),
                new RecipeSchemaTypeEntry.SchemaField("heat", "JSON", true, false, "heat",
                        arrayOf(object(Slot.req("value", union(host(HeatCondition.class), literal("none"))))))),
                List.of(List.of("duration", "heat")), Map.of(), List.of(), unsupported);
        var namespace = new RecipeNamespaceCatalogEntry("fixture", null, List.of("processing"), false,
                List.of(), List.of(), List.of(schema));
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(namespace),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("unrelated.primary"), List.of(), excluded, List.of(), 1, "SMART"));
        return new ProbeContext.Of(snapshot, List.of(), config, NekoJSPaths.fromGameDir(temp), "typescript",
                temp.resolve("typescript"), declarations);
    }
}
