package com.tkisor.nekujs.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.js.type_adapter.IngredientAdapter;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekujs.plugin.LegacyPluginDiscovery;
import com.tkisor.nekujs.recipe.schema.RecipeSchemaGraphCompiler;
import com.tkisor.nekujs.recipe.schema.SchemaDocument;
import com.tkisor.nekujs.recipe.value.LegacyRecipeComponents;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaStorage;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Real addon resources use shared native parents, the native Graal boundary and genuine vanilla codecs. */
@Tag("fixtures")
class NativeParentRecipePipelineTest {
    private static final List<String> SPECIAL = List.of("bin_extract", "bin_insert", "clear_configuration");
    private static final Set<String> IDS = Set.of("mekanism:bin_extract", "mekanism:bin_insert",
            "mekanism:clear_configuration", "mekanism:mek_data");

    @BeforeAll
    static void initialize(@TempDir Path directory) {
        FixtureRecipeCodecPipelineTest.initializeNativeServices(directory);
    }

    @Test
    void originalInheritedAliasesAreCallableAndEmitNativeCodecValidJson() throws Exception {
        Path jar = Path.of(System.getProperty("nekujs.fixtures.dir"))
                .resolve("kubejs-mekanism-neoforge-2101.1.7-build.18.jar");
        assertTrue(Files.isRegularFile(jar), jar.toString());
        var documents = LegacyRecipeSchemaSources.read(List.of(new LegacyPluginDiscovery.Source("kubejs_mekanism", jar)))
                .stream().filter(document -> IDS.contains(document.id())).toList();
        assertEquals(IDS, documents.stream().map(SchemaDocument::id).collect(Collectors.toSet()));
        for (var document : documents) {
            assertEquals("kubejs_mekanism", document.source());
            assertEquals("data/mekanism/kubejs/recipe_schema/" + document.id().substring(9) + ".json", document.resourcePath());
            assertEquals(1, document.json().size(), "These original resources must stay pure parent aliases");
            assertEquals(document.id().equals("mekanism:mek_data") ? "shaped" : "special",
                    document.json().get("parent").getAsString());
        }
        var registries = new RegistryAccessContainer(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var components = new RecipeSchemaStorage(registries);
        components.freezeComponents();
        var inputs = new ArrayList<>(documents);
        inputs.add(new SchemaDocument("negative_fixture", "test:unknown_factory", "unknown_factory.json",
                JsonParser.parseString("{\"recipe_factory\":\"test:unimplemented\",\"keys\":[]}").getAsJsonObject()));
        var compilation = new RecipeSchemaGraphCompiler().compile(inputs, new LegacyRecipeComponents(components, registries));
        for (String id : IDS) {
            var definition = compilation.definitions().get(id);
            assertNotNull(definition, id);
            assertNull(definition.unsupportedReason(), () -> id + ": " + definition.unsupportedReason());
            assertEquals(id, definition.type(), "An inherited alias must preserve its child serializer identity");
        }
        String factoryRejection = compilation.definitions().get("test:unknown_factory").unsupportedReason();
        assertNotNull(factoryRejection);
        assertTrue(factoryRejection.contains("NEKO-5250"), factoryRejection);
        var schemas = compilation.definitions().values().stream().map(RecipeSchemaTypeEntry::from).toList();
        String declarations = new RecipeEventDeclarationGenerator(new TypeAliasRegistry()).generate(List.of(
                new RecipeNamespaceCatalogEntry("mekanism", null, IDS.stream().sorted().toList(), true, List.of(),
                        List.of(), schemas.stream().filter(schema -> schema.namespace().equals("mekanism")).toList()),
                new RecipeNamespaceCatalogEntry("test", null, List.of("unknown_factory"), true, List.of(),
                        List.of(), schemas.stream().filter(schema -> schema.namespace().equals("test")).toList())), ScriptType.SERVER);
        for (String id : IDS) {
            String name = id.substring(9);
            assertFalse(declarations.contains(name + ": never;"), declarations);
            assertTrue(declarations.contains(name + "("), declarations);
        }
        assertTrue(declarations.contains("unknown_factory: never;"), declarations);
        assertTrue(declarations.contains("\"building\" | \"redstone\" | \"equipment\" | \"misc\""), declarations);
        assertFalse(declarations.contains("category?: $CraftingBookCategory_"), declarations);
        assertFalse(declarations.contains("category(category: $CraftingBookCategory_)"), declarations);

        var definitions = RecipeTypeDefinitionRegistry.builder();
        compilation.definitions().values().forEach(definitions::add);
        var adapters = new JSTypeAdapterRegistry.Impl();
        adapters.register(new ItemStackAdapter());
        adapters.register(new IngredientAdapter());
        NekoPluginRuntime.bootstrapOwned(List.of(), new ScriptPropertyRegistry.Impl());
        var event = new RecipeEventJS(new HashMap<>(), registries.access(), definitions.build());
        try (var context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.copyOf(adapters.view())).get()).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            context.enter();
            try {
                context.getBindings("js").putMember("event", event);
                context.getBindings("js").putMember("hostResult", new ItemStack(Items.STICK, 3));
                context.getBindings("js").putMember("hostPattern", List.of(" A ", " B "));
                context.getBindings("js").putMember("hostKeys", Map.of("A", Ingredient.of(Items.STONE), "B", Ingredient.EMPTY));
                context.eval("js", "const recipes = event.getRecipes();");
                for (String name : SPECIAL) {
                    context.eval("js", "recipes.mekanism." + name + "().id('nekujs:" + name + "_default');");
                    context.eval("js", "recipes.mekanism." + name + "({category:'building'}).id('nekujs:" + name + "_named');");
                    JsonObject empty = json(event, "nekujs:" + name + "_default");
                    JsonObject named = json(event, "nekujs:" + name + "_named");
                    assertEquals("mekanism:" + name, empty.get("type").getAsString());
                    assertEquals("mekanism:" + name, named.get("type").getAsString());
                    assertEquals(CraftingBookCategory.BUILDING,
                            CraftingBookCategory.CODEC.parse(registries.json(), named.get("category")).getOrThrow());
                }
                context.eval("js", """
                        recipes.mekanism.mek_data({id:'minecraft:stick',count:2}, [' A ',' B '],
                            {A:'minecraft:stone',B:[]}).id('nekujs:mek_data_guest');
                        recipes.mekanism.mek_data({result:hostResult,pattern:hostPattern,key:hostKeys})
                            .id('nekujs:mek_data_host');
                        """);
                event.prepareRecipesForDecode();
                for (String kind : List.of("guest", "host")) {
                    JsonObject shaped = json(event, "nekujs:mek_data_" + kind);
                    assertEquals("mekanism:mek_data", shaped.get("type").getAsString());
                    // This validates the native shaped contract, not an unloaded Mekanism-specific serializer.
                    ShapedRecipe decoded = new ShapedRecipe.Serializer().codec().codec().parse(registries.json(), shaped).getOrThrow();
                    assertEquals(1, decoded.getWidth());
                    assertEquals(1, decoded.getHeight());
                    assertTrue(decoded.getIngredients().getFirst().test(new ItemStack(Items.STONE)));
                    ItemStack result = decoded.getResultItem(registries.access());
                    assertSame(Items.STICK, result.getItem());
                    assertEquals(kind.equals("guest") ? 2 : 3, result.getCount());
                }
                assertEquals(8, event.count());
                for (String script : List.of(
                        "recipes.mekanism.mek_data('minecraft:stick',['AB','A'],{A:'minecraft:stone',B:'minecraft:dirt'})",
                        "recipes.mekanism.mek_data('minecraft:stick',['Z'],{A:'minecraft:stone'})",
                        "recipes.mekanism.mek_data('minecraft:stick',['A'],{A:5})",
                        "recipes.test.unknown_factory({})")) {
                    assertThrows(PolyglotException.class, () -> context.eval("js", script), script);
                    assertEquals(8, event.count(), "Rejected input must not allocate another recipe: " + script);
                }
            } finally {
                context.leave();
                ScriptContextRegistry.unbind(context);
            }
        }
        Path report = Path.of("build/reports/native-recipe-parents");
        Files.createDirectories(report);
        Files.writeString(report.resolve("index.d.ts"), declarations);
        Files.writeString(report.resolve("runtime-status.txt"),
                "Original four Mekanism aliases are callable; child types retained. Native shaped codec and category checks passed.\n");
    }

    private static JsonObject json(RecipeEventJS event, String id) {
        return JsonParser.parseString(event.getJson(id)).getAsJsonObject();
    }
}
