package com.tkisor.nekujs.recipe;

import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldRole;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.Platform;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MekanismCrushingSchemaTest {
    private static final String CHILD = "data/mekanism/kubejs/recipe_schema/crushing.json";
    private static final String PARENT = "data/mekanism/kubejs/recipe_schema/base/item_to_item.json";
    private static final String CHILD_JSON = """
            {"parent":"mekanism:base/item_to_item"}
            """;
    private static final String PARENT_JSON = """
            {"keys":[{"name":"output","role":"output","type":"item_stack"},
                     {"name":"input","role":"input","type":"flat_sized_ingredient"}],
             "unique":["output"]}
            """;
    @TempDir Path directory;

    @BeforeAll
    static void initializePlatform(@TempDir Path gameDirectory) {
        // This is an evaluator contract test, not an initialized Minecraft/NeoForge server.
        Platform.init(new IPlatform() {
            @Override public boolean isClient() { return false; }
            @Override public boolean isDevelopment() { return true; }
            @Override public String getMcVersion() { return "1.21.1"; }
            @Override public Path getGameDir() { return gameDirectory; }
            @Override public Map<String, IModInfo> getMods() { return Map.of(); }
            @Override public IModInfo getInfo(String modId) { return null; }
            @Override public String getLoaderId() { return "test"; }
            @Override public String getLoaderVersion() { return "0"; }
        });
    }

    @Test
    void importsOnlyTheSupportedProfileAndPreservesFieldOrderAndRoles() throws IOException {
        var schema = MekanismCrushingSchema.read(source(Map.of(CHILD, CHILD_JSON, PARENT, PARENT_JSON)));
        assertEquals("mekanism:crushing", schema.key());
        assertEquals("mekanism:crushing", schema.type());
        assertEquals(List.of(List.of("output", "input")), schema.constructors());
        assertEquals(List.of("output", "input"), List.copyOf(schema.fields().keySet()));
        assertEquals(List.of("output"), schema.unique());
        assertEquals(RecipeFieldRole.INPUT, schema.fields().get("input").role());
        assertEquals(RecipeFieldRole.OUTPUT, schema.fields().get("output").role());
        schema.fields().values().forEach(field -> {
            assertEquals(RecipeFieldKind.JSON, field.kind());
            assertTrue(field.required());
            assertFalse(field.array());
        });
    }

    @Test
    void rawStacksAndFlatSizedIngredientsSurviveTheRealGraalRecipeBuilder() throws IOException {
        var schema = MekanismCrushingSchema.read(source(Map.of(CHILD, CHILD_JSON, PARENT, PARENT_JSON)));
        NekoPluginRuntime.bootstrapOwned(List.of(), new ScriptPropertyRegistry.Impl());
        var registry = RecipeTypeDefinitionRegistry.builder().add(schema).build();
        var event = new RecipeEventJS(new HashMap<>(), null, registry);
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("recipes", event.getRecipes());
            context.eval("js", """
                    recipes.mekanism.crushing(
                        {id: 'minecraft:gravel', count: 2},
                        {item: 'minecraft:cobblestone', count: 3}
                    ).id('nekujs:crushing_contract');
                    """);
        }
        assertEquals(1, event.count());
        assertEquals(JsonParser.parseString("""
                {"type":"mekanism:crushing", "output":{"id":"minecraft:gravel","count":2},
                 "input":{"item":"minecraft:cobblestone","count":3}}
                """), JsonParser.parseString(event.getJson("nekujs:crushing_contract")));
    }

    @Test
    void rejectsChangedSemanticsInsteadOfSilentlyDroppingThem() throws IOException {
        var error = assertThrows(IOException.class, () -> MekanismCrushingSchema.read(source(Map.of(
                CHILD, CHILD_JSON, PARENT, PARENT_JSON.replace("flat_sized_ingredient", "ingredient")))));
        assertTrue(error.getMessage().contains("[NEKO-5191]"));
    }

    @Test
    void rejectsMissingAndMalformedResourcesWithUsefulDiagnostics() throws IOException {
        var missing = assertThrows(IOException.class,
                () -> MekanismCrushingSchema.read(source(Map.of(CHILD, CHILD_JSON))));
        assertTrue(missing.getMessage().contains("[NEKO-5190]"));
        var malformed = assertThrows(IOException.class,
                () -> MekanismCrushingSchema.read(source(Map.of(CHILD, "{", PARENT, PARENT_JSON))));
        assertTrue(malformed.getMessage().contains("[NEKO-5192]"));
        assertNotNull(malformed.getCause());
        assertThrows(IOException.class, () -> MekanismCrushingSchema.read(directory.resolve("absent.jar")));
    }

    private Path source(Map<String, String> resources) throws IOException {
        Path path = Files.createTempFile(directory, "schema-", ".jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(path))) {
            for (var entry : resources.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
        return path;
    }
}
