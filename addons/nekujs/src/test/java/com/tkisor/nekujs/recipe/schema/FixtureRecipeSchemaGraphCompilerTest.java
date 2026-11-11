package com.tkisor.nekujs.recipe.schema;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldValueCodec;
import com.tkisor.nekojs.api.recipe.RecipeExecutionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the unchanged graph resources, not target serializer acceptance or gameplay. */
@Tag("fixtures")
class FixtureRecipeSchemaGraphCompilerTest {
    @Test
    void everyOriginalCreateAndMekanismResourceHasOneExplicitNativeProfile() throws Exception {
        var expressions = new LinkedHashSet<JsonElement>();
        var semantic = graphComponents(expressions);
        var documents = documents();
        var result = new RecipeSchemaGraphCompiler().compile(documents, semantic);
        assertEquals(62, result.definitions().size());
        assertEquals(documents.stream().map(SchemaDocument::id).collect(java.util.stream.Collectors.toSet()),
                result.definitions().keySet(), "Only the unchanged owned resource IDs may be emitted");
        assertEquals(14, result.rejections().size());
        assertEquals(48, result.definitions().values().stream().filter(value -> value.unsupportedReason() == null).count());
        var featureRejections = new LinkedHashSet<String>();
        int abstractDependencies = 0;
        for (var rejection : result.rejections()) {
            assertTrue(rejection.source().endsWith(".jar"));
            assertTrue(rejection.resourcePath().contains("/kubejs/recipe_schema/"));
            assertTrue(rejection.jsonPath().startsWith("$"));
            assertNotNull(result.definitions().get(rejection.schemaId()).unsupportedReason());
            if (rejection.code().equals("NEKO-5251")) abstractDependencies++;
            else featureRejections.add(rejection.schemaId());
        }
        assertEquals(13, abstractDependencies);
        assertEquals(Set.of("create:sequenced_assembly"), featureRejections);
        for (String id : Set.of("mekanism:bin_extract", "mekanism:bin_insert", "mekanism:clear_configuration",
                "mekanism:mek_data", "create:mechanical_crafting", "mekanismtools:paxel", "mekanismtools:banner_shield")) {
            assertNotNull(result.definitions().get(id), id);
            assertNull(result.definitions().get(id).unsupportedReason(), id);
            assertEquals(id, result.definitions().get(id).type(), id);
        }
        var unknownFactory = new SchemaDocument("negative_fixture", "test:unknown_factory", "unknown_factory.json",
                JsonParser.parseString("{\"factory\":\"test:unknown\"}").getAsJsonObject());
        var rejected = new RecipeSchemaGraphCompiler().compile(List.of(unknownFactory), semantic);
        assertEquals(1, rejected.rejections().size());
        assertEquals("NEKO-5250", rejected.rejections().getFirst().code());
        assertNotNull(rejected.definitions().get(unknownFactory.id()).unsupportedReason());
        assertTrue(expressions.contains(JsonParser.parseString("""
                {"type":"list","component":{"type":"either","left":"create:sized_fluid_ingredient","right":"ingredient"},
                 "spread":{"type":"either","left":"ignore","right":"sized_ingredient"}}
                """)));
        result.definitions().values().stream().filter(value -> value.unsupportedReason() == null)
                .forEach(definition -> assertTrue(definition.fields().values().stream().allMatch(field -> field.valueCodec() != null)));
    }

    @Test
    void originalCreateFluentNamesExposeNormalizedNativeFieldAliases() throws Exception {
        var definitions = new RecipeSchemaGraphCompiler().compile(documents(), graphComponents(new LinkedHashSet<>())).definitions();
        for (String id : List.of("create:mixing", "create:pressing")) {
            var aliases = definitions.get(id).fieldAliases();
            assertEquals("processing_time", aliases.get("processingTime"), id);
            assertEquals("heat_requirement", aliases.get("heatRequirement"), id);
        }
    }

    @Test
    void originalDefaultsFunctionsAndConstructorSequencesSurviveInheritance() throws Exception {
        var definitions = new RecipeSchemaGraphCompiler().compile(documents(), graphComponents(new LinkedHashSet<>())).definitions();
        var pressing = definitions.get("create:pressing");
        var cutting = definitions.get("create:cutting");
        assertFalse(pressing.fields().get("processing_time").writeDefault());
        assertEquals(100, pressing.fields().get("processing_time").defaultValue().getAsInt());
        assertTrue(cutting.fields().get("processing_time").writeDefault());
        assertEquals("heated", cutting.functions().get("heated").get("heat_requirement").getAsString());
        assertEquals("superheated", cutting.functions().get("superheated").get("heat_requirement").getAsString());
        assertTrue(definitions.get("create:deploying").functions().get("keepHeldItem").get("keep_held_item").getAsBoolean());
        assertTrue(definitions.get("create:deploying").constructors().stream().noneMatch(keys -> keys.contains("keep_held_item")));
        var reaction = definitions.get("mekanism:reaction");
        assertEquals(List.of(6, 5, 4, 3), reaction.constructors().stream().map(List::size).toList());
        assertEquals(JsonParser.parseString("{}"), reaction.fields().get("item_output").defaultValue());
        assertFalse(reaction.fields().get("item_output").writeDefault());
        assertFalse(reaction.fieldAliases().containsKey("energy"), "Unknown builder_names is not a function_names alias");
        assertEquals(List.of(
                List.of("main_output", "secondary_output", "secondary_chance", "input"),
                List.of("secondary_output", "secondary_chance", "input"),
                List.of("main_output", "input")), definitions.get("mekanism:sawing").constructors());
        assertNull(definitions.get("mekanism:sawing").fields().get("main_output").defaultValue());
        assertEquals(List.of(1, 2, 3, 4), definitions.get("mekanism:rotary").constructors().stream().map(List::size).toList());
        assertFalse(definitions.get("mekanism:dissolution").fields().get("per_tick_usage").defaultValue().getAsBoolean());
        assertTrue(definitions.get("mekanism:dissolution").fields().get("per_tick_usage").writeDefault());
    }

    private static List<SchemaDocument> documents() throws Exception {
        var result = new ArrayList<SchemaDocument>();
        var directory = Path.of(System.getProperty("nekujs.fixtures.dir"));
        for (String name : List.of("kubejs-create-neoforge-2101.3.1-build.18.jar", "kubejs-mekanism-neoforge-2101.1.7-build.18.jar")) {
            try (var archive = new ZipFile(directory.resolve(name).toFile())) {
                var entries = archive.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    String path = entry.getName();
                    String marker = "/kubejs/recipe_schema/";
                    int index = path.indexOf(marker);
                    if (!path.startsWith("data/") || index < 0 || !path.endsWith(".json")) continue;
                    String id = path.substring(5, index) + ":" + path.substring(index + marker.length(), path.length() - 5);
                    try (var stream = archive.getInputStream(entry)) {
                        result.add(new SchemaDocument(name, id, path,
                                JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject()));
                    }
                }
            }
        }
        return result;
    }

    private static SemanticComponents graphComponents(Set<JsonElement> expressions) {
        return new SemanticComponents() {
            @Override
            public RecipeFieldValueCodec resolve(JsonElement expression) {
                expressions.add(expression.deepCopy());
                if (!SUPPORTED_EXPRESSIONS.contains(expression)) {
                    throw new IllegalArgumentException("No fixture semantic binding: " + expression);
                }
                return RecipeSchemaGraphCompilerTest.components().resolve(expression);
            }

            @Override
            public JsonElement encodeConstant(JsonElement expression, JsonElement literal) {
                return literal.deepCopy();
            }

            @Override
            public RecipeExecutionFactory executionFactory(JsonElement expression, List<JsonElement> processors) {
                if (!expression.isJsonPrimitive() || !expression.getAsJsonPrimitive().isString()
                        || !expression.getAsString().equals(NativeRecipeSchemaParents.SHAPED_FACTORY) || !processors.isEmpty()) {
                    throw new IllegalArgumentException("Unsupported graph fixture factory");
                }
                // This fixture checks graph capability selection; the real codec pipeline tests execution.
                return (builder, host, session) -> {
                    throw new AssertionError("The pure compiler must not execute a native recipe factory");
                };
            }
        };
    }

    private static final Set<JsonElement> SUPPORTED_EXPRESSIONS = java.util.stream.Stream.of(
            "\"boolean\"", "\"create:heat_condition\"", "\"create:processing_output\"",
            "\"flat_sized_fluid_ingredient\"", "\"flat_sized_ingredient\"", "\"fluid_stack\"",
            "\"ingredient\"", "\"item_stack\"", "\"mekanism:chemical_stack\"",
            "\"mekanism:chemical_stack_ingredient\"", "\"mekanism:optional_chemical_stack\"",
            "\"non_negative_long\"", "\"optional_fluid_stack\"", "\"optional_item_stack\"",
            "\"positive_long\"", "\"ticks\"", "\"crafting_book_category\"",
            """
            {"type":"list","component":"string"}
            """,
            """
            {"type":"pattern","component":"optional_ingredient"}
            """,
            """
            {"type":"list","component":"create:processing_output"}
            """,
            """
            {"type":"list","component":{"type":"either","left":"create:sized_fluid_ingredient","right":"ingredient"},
             "spread":{"type":"either","left":"ignore","right":"sized_ingredient"}}
            """,
            """
            {"type":"list","component":{"type":"either","left":"fluid_stack","right":"create:processing_output"}}
            """,
            """
            {"type":"double","min":0.00001,"max":1.0}
            """,
            """
            {"type":"int","min":0}
            """
    ).map(JsonParser::parseString).collect(java.util.stream.Collectors.toUnmodifiableSet());
}
