package com.tkisor.nekojs.probe;

import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.RecipeHandlerMethodEntry;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.api.recipe.RecipeFieldRoles;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldValueCodec;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeCompatibilityDeclarationTest {
    @org.junit.jupiter.api.BeforeAll
    static void initializePlatform() {
        com.tkisor.nekojs.testfixture.TestPlatformInit.ensureInitialized();
    }

    @Test
    void catalogKeepsAuthoringIdentityAndCanonicalNativeMetadata() {
        AdapterInputShape shape = arrayOf(number());
        var fields = new LinkedHashMap<String, RecipeFieldDefinition>();
        fields.put("values", field("values", shape, true, null));
        fields.put("duration", field("duration", number(), false, new JsonPrimitive(100)));
        var definition = definition("create", "pressing_alias", "create:pressing", fields,
                List.of(List.of("values")), Map.of("input", "values"),
                Map.of("heated", Map.of("duration", new JsonPrimitive(200))));
        var entry = RecipeSchemaTypeEntry.from(definition);
        assertEquals("create", entry.namespace());
        assertEquals("pressing_alias", entry.type());
        assertEquals("create_pressing_alias", entry.idPrefix());
        assertSame(shape, entry.fields().getFirst().inputShape());
        assertTrue(entry.fields().getFirst().required());
        assertTrue(entry.fields().getFirst().array());
        assertFalse(entry.fields().get(1).required(), "Native defaults make fields optional to callers");
        assertEquals(definition.constructors(), entry.constructors());
        assertEquals(definition.fieldAliases(), entry.fieldAliases());
        assertEquals(List.of("heated"), entry.functions());
        assertNull(entry.unsupportedReason());
        assertThrows(UnsupportedOperationException.class, () -> entry.fieldAliases().put("other", "values"));
        assertThrows(UnsupportedOperationException.class, () -> entry.functions().add("other"));
    }

    @Test
    void completeCodecShapeDrivesNamedPositionalAndChainInputsWithoutLegacyArrayWrapping() {
        AdapterInputShape shape = union(registry("Item"), arrayOf(object(
                Slot.req("id", registry("Item")), Slot.opt("chance", number()))));
        var definition = definition("create", "pressing_alias", "create:pressing",
                Map.of("values", field("values", shape, true, null)), List.of(List.of("values")),
                Map.of("input", "values"), Map.of("heated", Map.of()));
        TypeAliasRegistry aliases = new TypeAliasRegistry();
        String type = "(RegistryTypes.Item | ({ id: RegistryTypes.Item, chance?: number })[])";
        String output = render(definition, aliases);
        assertTrue(output.contains("import type { RegistryTypes } from \"@special/types\";"), output);
        assertTrue(output.contains("export class Create$PressingAlias extends $RecipeJsonBuilder"), output);
        assertFalse(output.contains("export type Create$ValuesInput = "), output);
        assertTrue(output.contains("values(values: " + type + "): this;"), output);
        assertTrue(output.contains("input(values: " + type + "): this;"), output);
        assertTrue(output.contains("pressing_alias(\n                values: " + type
                + "\n            ): Create$PressingAlias;"), output);
        assertTrue(output.contains("{ values: " + type + ", input?: never }"), output);
        assertTrue(output.contains("{ values?: never, input: " + type + " }"), output);
        assertTrue(output.contains("heated(): this;"), output);
        assertFalse(output.contains(type + "[]"), "The codec already describes the whole field: " + output);
        assertFalse(output.contains("pressing("), "Serializer identity must not replace the authoring alias: " + output);
        assertFalse(output.contains("any"), output);
    }

    @Test
    void defaultedAliasesRemainOptionalAndDuplicateKeysAreNeverAdvertisedTogether() {
        var fields = new LinkedHashMap<String, RecipeFieldDefinition>();
        fields.put("input", field("input", string(), false, null));
        fields.put("duration", field("duration", number(), false, new JsonPrimitive(100)));
        var definition = definition("mekanism", "processing", "mekanism:processing", fields,
                List.of(List.of("input")), Map.of("time", "duration", "ticks", "duration"), Map.of());
        String output = render(definition, new TypeAliasRegistry()).replaceAll("\\s+", " ")
                .replace("( ", "(").replace(" )", ")");
        assertTrue(output.contains("processing(options: { input: string } & ("), output);
        assertTrue(output.contains("duration?: number"), output);
        assertTrue(output.contains("time?: number"), output);
        assertTrue(output.contains("ticks?: number"), output);
        for (String alternative : output.substring(output.indexOf("processing(options:"),
                output.indexOf("): Mekanism$Processing", output.indexOf("processing(options:"))).split(java.util.regex.Pattern.quote(" | "))) {
            assertTrue(alternative.contains("never"), alternative);
            assertEquals(1, count(alternative, "?: number"), alternative);
        }
        assertTrue(output.contains("time(duration: number): this;"), output);
        assertTrue(output.contains("ticks(duration: number): this;"), output);
        assertFalse(output.contains("duration: number, time: number"), output);
    }

    @Test
    void hostShapeImportsUseModuleLocalNamesWithoutMutatingSharedAliases() {
        TypeAliasRegistry aliases = new TypeAliasRegistry();
        aliases.registerClassAlias("java.util.Date", "$Date_");
        aliases.registerClassAlias("java.sql.Date", "$Date_");
        AdapterInputShape shape = union(host(java.util.Date.class), host(java.sql.Date.class));
        var definition = definition("test", "dates", "test:dates",
                Map.of("dates", field("dates", shape, false, null)), List.of(List.of("dates")), Map.of(), Map.of());
        String output = render(definition, aliases);
        var imports = output.lines().filter(line -> line.contains("$Date as ")).toList();
        assertEquals(2, imports.size(), output);
        assertTrue(imports.stream().anyMatch(line -> line.endsWith("from \"java:java/sql\";")), output);
        assertTrue(imports.stream().anyMatch(line -> line.endsWith("from \"java:java/util\";")), output);
        var names = imports.stream().map(line -> line.substring(line.indexOf(" as ") + 4, line.indexOf(" }"))).toList();
        assertNotEquals(names.getFirst(), names.get(1), output);
        assertFalse(output.contains("export type Test$DatesInput = "), output);
        assertTrue(output.contains("dates(dates: (" + names.get(1) + " | " + names.getFirst() + ")): this;")
                || output.contains("dates(dates: (" + names.getFirst() + " | " + names.get(1) + ")): this;"), output);
        assertEquals("$Date_", aliases.getAlias("java.util.Date"));
        assertEquals("$Date_", aliases.getAlias("java.sql.Date"));
        assertEquals(output, render(definition, aliases), "Local allocation must be deterministic");
    }

    @Test
    void knownRejectionWinsOverHandlerAndCannotAdvertiseAnyCallableFallback() {
        var rejected = RecipeTypeDefinition.unsupported("mekanism", "unknown_profile", "mekanism:unknown",
                "The installed profile has no supported input codec");
        var handler = new RecipeHandlerMethodEntry("unknown_profile", List.of(
                new RecipeHandlerMethodEntry.HandlerParam("value", "Date", "java.util.Date", "java.util.Date", false)), 1);
        var entry = RecipeSchemaTypeEntry.from(rejected);
        assertEquals(rejected.unsupportedReason(), entry.unsupportedReason());
        var namespace = new RecipeNamespaceCatalogEntry("mekanism", getClass(), List.of("unknown_profile"),
                true, List.of(), List.of(handler), List.of(entry));
        String output = new RecipeEventDeclarationGenerator(new TypeAliasRegistry()).generate(List.of(namespace), ScriptType.SERVER);
        assertTrue(output.contains("unknown_profile: never;"), output);
        assertFalse(output.contains("unknown_profile("), output);
        assertFalse(output.contains("Mekanism$UnknownProfile"), output);
        assertFalse(output.contains("java:java/util"), output);
        assertFalse(output.contains("[key: string]"), output);
        assertFalse(output.contains("any"), output);
    }

    @Test
    void oldCatalogConstructorsAndNativePrimitiveFieldDeclarationsStayCompatible() {
        var field = new RecipeFieldDefinition("amount", "amount", RecipeFieldKind.INT,
                true, false, null, RecipeFieldRoles.roleOfName("amount"));
        var nativeDefinition = new RecipeTypeDefinition("native", "counted", "native:counted", null,
                List.of(List.of("amount")), Map.of("amount", field), List.of());
        var projected = RecipeSchemaTypeEntry.from(nativeDefinition);
        var oldCatalog = new RecipeSchemaTypeEntry("native", "counted", "native_counted",
                List.of(new RecipeSchemaTypeEntry.SchemaField("amount", "INT", true, true, "amount")),
                List.of(List.of("amount")));
        assertEquals(oldCatalog, projected);
        String output = render(nativeDefinition, new TypeAliasRegistry());
        assertTrue(output.contains("counted(options: { amount: number[] }): Native$Counted;"), output);
        assertTrue(output.contains("counted(amount: number[]): Native$Counted;"), output);
        assertTrue(output.contains("amount(amount: number[]): this;"), output);
        assertEquals(Map.of(), projected.fieldAliases());
        assertEquals(List.of(), projected.functions());
        assertNull(projected.fields().getFirst().inputShape());
    }

    @Test
    void supportedHandlerOverloadsAndTheirExistingSchemaPrecedenceRemainUnchanged() {
        var handler = new RecipeHandlerMethodEntry("counted", List.of(), 0);
        var overloaded = new RecipeHandlerMethodEntry("counted", List.of(
                new RecipeHandlerMethodEntry.HandlerParam("amount", "number", null, "int", false)), 1);
        var definition = definition("native", "counted", "native:counted", Map.of(
                "amount", field("amount", number(), false, null)), List.of(List.of("amount")), Map.of(), Map.of());
        var namespace = new RecipeNamespaceCatalogEntry("native", getClass(), List.of("counted"), true,
                List.of(), List.of(handler, overloaded), List.of(RecipeSchemaTypeEntry.from(definition)));
        String output = new RecipeEventDeclarationGenerator(new TypeAliasRegistry()).generate(List.of(namespace), ScriptType.SERVER);
        assertTrue(output.contains("counted(): $RecipeJsonBuilder;"), output);
        assertTrue(output.contains("counted(amount: number): $RecipeJsonBuilder;"), output);
        assertFalse(output.contains("counted(options:"), output);
    }

    @Test
    void noArgumentFunctionsCanDescribeAFieldlessBuilderAndUnresolvedSelfShapeFailsExplicitly() {
        var empty = definition("native", "empty", "native:empty", Map.of(), List.of(List.of()), Map.of(),
                Map.of("activate", Map.of()));
        String output = render(empty, new TypeAliasRegistry());
        assertTrue(output.contains("export class Native$Empty extends $RecipeJsonBuilder"), output);
        assertTrue(output.contains("activate(): this;"), output);
        assertTrue(output.contains("empty(): Native$Empty;"), output);
        var unresolved = definition("native", "unresolved", "native:unresolved",
                Map.of("value", field("value", self(), false, null)), List.of(List.of("value")), Map.of(), Map.of());
        var failure = assertThrows(NullPointerException.class, () -> render(unresolved, new TypeAliasRegistry()));
        assertEquals("NEKO-4063: Self input shape requires an adapter target", failure.getMessage());
    }

    @Test
    void collectorSeedsRecipeOnlyHostTypesNestedInWholeFieldShapesUsingExistingFilters() {
        var definition = definition("test", "host_only", "test:host_only", Map.of("values",
                field("values", arrayOf(object(Slot.req("value", host(RecipeShapeHost.class)))), false, null)),
                List.of(List.of("values")), Map.of(), Map.of());
        var namespace = new RecipeNamespaceCatalogEntry("test", null, List.of("host_only"), true,
                List.of(), List.of(), List.of(RecipeSchemaTypeEntry.from(definition)));
        var snapshot = new NekoScriptCatalogSnapshot(List.of(ScriptType.SERVER), List.of(), List.of(), List.of(),
                List.of(namespace), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null, Map.of(), List.of());
        ProbeConfig config = ProbeConfig.defaultConfig();
        assertFalse(config.isExcluded(RecipeShapeHost.class.getName()));
        var collected = ProbeClassCollector.collect(snapshot, config);
        assertTrue(collected.contains(RecipeShapeHost.class), collected::toString);
        var empty = new NekoScriptCatalogSnapshot(List.of(ScriptType.SERVER), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null, Map.of(), List.of());
        assertFalse(ProbeClassCollector.collect(empty, config).contains(RecipeShapeHost.class));
    }

    public static final class RecipeShapeHost {}

    private static RecipeFieldDefinition field(String name, AdapterInputShape shape, boolean array,
                                                JsonPrimitive defaultValue) {
        var codec = new RecipeFieldValueCodec(shape, (value, host) -> {
            throw new AssertionError("Declaration generation must not execute a runtime encoder");
        });
        return new RecipeFieldDefinition(name, name, RecipeFieldKind.JSON, array, false, defaultValue,
                RecipeFieldRoles.roleOfName(name), codec, false);
    }

    private static RecipeTypeDefinition definition(String namespace, String name, String serializer,
                                                    Map<String, RecipeFieldDefinition> fields,
                                                    List<List<String>> constructors, Map<String, String> aliases,
                                                    Map<String, Map<String, com.google.gson.JsonElement>> functions) {
        return new RecipeTypeDefinition(namespace, name, serializer, null, constructors, fields, List.of(),
                aliases, functions, Map.of(), null);
    }

    private static String render(RecipeTypeDefinition definition, TypeAliasRegistry aliases) {
        var namespace = new RecipeNamespaceCatalogEntry(definition.namespace(), RecipeCompatibilityDeclarationTest.class,
                List.of(definition.name()), true, List.of(), List.of(), List.of(RecipeSchemaTypeEntry.from(definition)));
        return new RecipeEventDeclarationGenerator(aliases).generate(List.of(namespace), ScriptType.SERVER);
    }

    private static int count(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }
}
