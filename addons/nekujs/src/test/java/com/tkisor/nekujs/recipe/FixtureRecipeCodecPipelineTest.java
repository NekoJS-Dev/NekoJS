package com.tkisor.nekujs.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DynamicOps;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekujs.bridge.OriginalRecipeInputFixture;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekujs.plugin.LegacyPluginDiscovery;
import com.tkisor.nekujs.recipe.schema.RecipeSchemaGraphCompiler;
import com.tkisor.nekujs.recipe.value.LegacyRecipeComponents;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaStorage;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Original plugin codecs, source graphs, and the native Graal recipe builder share one borrowed registry view. */
@Tag("fixtures")
class FixtureRecipeCodecPipelineTest {
    private static final String CREATE = "kubejs-create-neoforge-2101.3.1-build.18.jar";
    private static final String MEKANISM = "kubejs-mekanism-neoforge-2101.1.7-build.18.jar";
    private static boolean nativeServicesInitialized;

    @BeforeAll
    static synchronized void initializeNativeServices(@TempDir Path gameDirectory) {
        if (nativeServicesInitialized) return;
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
        // Genuine empty loading services for isolated native codec tests; this does not launch the mod loader.
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        net.neoforged.neoforge.registries.NekuJSRecipeRegistryTestFixture.registerFluidIngredientTypes();
        nativeServicesInitialized = true;
    }

    @Test
    void originalHooksAndCodecsFeedTheNativeRecipePipeline(@TempDir Path directory) throws Exception {
        Path fixtures = Path.of(System.getProperty("nekujs.fixtures.dir"));
        try (var loader = fixtureLoader(fixtures, directory)) {
            var registries = new RegistryAccessContainer(
                    RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            var catalog = new RecipeSchemaStorage(registries);
            var createPlugin = originalPlugin(loader, fixtures.resolve(CREATE),
                    "dev.latvian.mods.kubejs.create.KubeJSCreatePlugin");
            createPlugin.registerRecipeComponents(catalog);
            var adapters = OriginalRecipeInputFixture.adapters(createPlugin);
            originalPlugin(loader, fixtures.resolve(MEKANISM),
                    "dev.latvian.mods.kubejs.mekanism.MekanismKubeJSPlugin").registerRecipeComponents(catalog);
            assertEquals(Set.of("create:processing_output", "create:sized_fluid_ingredient", "create:heat_condition",
                            "mekanism:chemical", "mekanism:chemical_stack", "mekanism:optional_chemical_stack",
                            "mekanism:chemical_ingredient", "mekanism:optional_chemical_ingredient",
                            "mekanism:chemical_stack_ingredient"),
                    catalog.componentTypes().keySet().stream().map(Object::toString).collect(Collectors.toSet()));
            catalog.freezeComponents();

            RecipeComponent<?> output = component(catalog, registries, "create:processing_output");
            assertSame(loader, output.getClass().getClassLoader());
            assertEquals("dev.latvian.mods.kubejs.create.recipe.ProcessingOutputRecipeComponent", output.getClass().getName());
            JsonElement outputJson = JsonParser.parseString("""
                    {"id":"minecraft:iron_ingot","count":2,"chance":0.25}
                    """);
            Object decodedOutput = output.codec().parse(registries.json(), outputJson).getOrThrow();
            assertSame(loader, decodedOutput.getClass().getClassLoader());
            assertEquals(2, ((ItemStack) decodedOutput.getClass().getMethod("getStack").invoke(decodedOutput)).getCount());
            assertEquals(0.25F, ((Float) decodedOutput.getClass().getMethod("getChance").invoke(decodedOutput)).floatValue());
            assertEquals(outputJson, roundTrip(output, registries.json(), outputJson));
            assertTrue(output.codec().parse(registries.json(), JsonParser.parseString(
                    "{\"id\":\"minecraft:iron_ingot\",\"count\":0}")).error().isPresent());
            assertTrue(output.codec().parse(registries.json(), JsonParser.parseString(
                    "{\"id\":\"minecraft:iron_ingot\",\"chance\":-1}")).error().isPresent());

            RecipeComponent<?> fluid = component(catalog, registries, "create:sized_fluid_ingredient");
            JsonElement fluidJson = JsonParser.parseString("{\"type\":\"neoforge:single\",\"fluid\":\"minecraft:water\",\"amount\":250}");
            assertEquals(250, assertInstanceOf(SizedFluidIngredient.class,
                    fluid.codec().parse(registries.json(), fluidJson).getOrThrow()).amount());
            assertEquals(fluidJson, roundTrip(fluid, registries.json(), fluidJson));
            assertTrue(fluid.codec().parse(registries.json(), JsonParser.parseString(
                    "{\"type\":\"neoforge:single\",\"fluid\":\"minecraft:water\",\"amount\":0}")).error().isPresent());

            var sources = List.of(new LegacyPluginDiscovery.Source("kubejs_create", fixtures.resolve(CREATE)),
                    new LegacyPluginDiscovery.Source("kubejs_mekanism", fixtures.resolve(MEKANISM)));
            var documents = LegacyRecipeSchemaSources.read(sources);
            var compilation = new RecipeSchemaGraphCompiler().compile(documents,
                    new LegacyRecipeComponents(catalog, registries));
            assertEquals(62, compilation.definitions().size());
            assertEquals(documents.stream().map(document -> document.id()).collect(Collectors.toSet()),
                    compilation.definitions().keySet(), "Native parents must not become extra owned resource profiles");
            assertEquals(48L, compilation.definitions().values().stream()
                            .filter(definition -> definition.unsupportedReason() == null).count(),
                    () -> compilation.definitions().entrySet().stream()
                            .filter(entry -> entry.getValue().unsupportedReason() != null)
                            .map(entry -> entry.getKey() + ": " + entry.getValue().unsupportedReason())
                            .sorted().collect(Collectors.joining("\n")));
            assertNull(compilation.definitions().get("create:pressing").unsupportedReason());
            assertNull(compilation.definitions().get("create:filling").unsupportedReason());
            assertNull(compilation.definitions().get("mekanism:crushing").unsupportedReason());
            // These seven unchanged resources share the two newly supplied native parents.
            for (String id : Set.of("mekanism:bin_extract", "mekanism:bin_insert", "mekanism:clear_configuration",
                    "mekanism:mek_data", "create:mechanical_crafting", "mekanismtools:paxel", "mekanismtools:banner_shield")) {
                var definition = compilation.definitions().get(id);
                assertNotNull(definition, id);
                assertNull(definition.unsupportedReason(), id);
                assertEquals(id, definition.type(), id);
            }
            assertNotNull(compilation.definitions().get("create:sequenced_assembly").unsupportedReason());
            var createSchemas = compilation.definitions().values().stream()
                    .filter(definition -> definition.namespace().equals("create"))
                    .map(RecipeSchemaTypeEntry::from).toList();
            var createNamespace = new RecipeNamespaceCatalogEntry("create", null,
                    createSchemas.stream().map(RecipeSchemaTypeEntry::type).toList(), true,
                    List.of(), List.of(), createSchemas);
            var aliases = new TypeAliasRegistry();
            var adapterCatalog = OriginalRecipeInputFixture.catalog(adapters);
            new AdapterAliasGenerator(aliases).prepare(adapterCatalog, adapterCatalog.stream()
                    .map(entry -> entry.targetType().getName()).collect(Collectors.toSet()));
            String declarations = new RecipeEventDeclarationGenerator(aliases)
                    .generate(List.of(createNamespace), ScriptType.SERVER);
            var cuttingOverloads = declarations.lines().filter(line -> line.stripLeading().startsWith("cutting(")).toList();
            assertEquals(2, cuttingOverloads.size(), declarations);
            assertTrue(cuttingOverloads.getLast().contains("cutting(options: Create$CuttingOptions)"), declarations);
            assertTrue(cuttingOverloads.stream().allMatch(line -> line.length() < 220), declarations);
            assertTrue(declarations.contains("processingTime?:"), declarations);
            assertTrue(declarations.contains("$List_<"), declarations);
            assertTrue(declarations.contains("$FluidStack_"), declarations);
            assertTrue(declarations.contains("$ProcessingOutput"), declarations);
            assertFalse(declarations.contains("$ProcessingOutput_"), declarations);
            assertTrue(declarations.contains("$SizedFluidIngredient_"), declarations);
            assertTrue(declarations.contains("$Ingredient_"), declarations);
            assertFalse(declarations.contains("SingleOrArray"), declarations);
            assertFalse(declarations.contains("Create$FluidStackInput"), declarations);
            assertFalse(declarations.contains("Create$ProcessingOutputInput"), declarations);
            assertFalse(declarations.contains("export type Create$ResultsInput = "), declarations);
            assertFalse(declarations.contains("export type Create$IngredientsInput = "), declarations);
            Path preview = Path.of("build/reports/compatibility/create-recipe-aliases.d.ts");
            Files.createDirectories(preview.getParent());
            Files.writeString(preview, declarations);
            var definitions = RecipeTypeDefinitionRegistry.builder();
            compilation.definitions().values().forEach(definitions::add);
            NekoPluginRuntime.bootstrapOwned(List.of(), new ScriptPropertyRegistry.Impl());
            var event = new RecipeEventJS(new HashMap<>(), registries.access(), definitions.build());
            try (var context = Context.newBuilder("js")
                    .allowHostAccess(new NekoSharedHostAccess(List.copyOf(adapters.view())).get()).build()) {
                managed(context, () -> {
                    context.getBindings("js").putMember("event", event);
                    context.eval("js", """
                            const recipes = event.getRecipes();
                            recipes.create.pressing(
                                [{id:'minecraft:iron_ingot', count:2, chance:0.25}],
                                [{item:'minecraft:iron_block'}]
                            ).processingTime(25).heated().heatRequirement('heated').id('nekujs:fixture_pressing');
                            recipes.create.filling(
                                [{id:'minecraft:iron_ingot', count:2, chance:0.25}],
                                [{type:'neoforge:single', fluid:'minecraft:water', amount:250}, {item:'minecraft:iron_block'}]
                            ).id('nekujs:fixture_filling');
                            recipes.mekanism.crushing(
                                {id:'minecraft:gravel', count:2},
                                {item:'minecraft:cobblestone', count:3}
                            ).id('nekujs:fixture_crushing');
                            """);
                    assertEquals(3, event.count());
                    JsonElement pressing = JsonParser.parseString(event.getJson("nekujs:fixture_pressing"));
                    assertEquals(outputJson, pressing.getAsJsonObject().getAsJsonArray("results").get(0));
                    assertEquals(25, pressing.getAsJsonObject().get("processing_time").getAsInt());
                    assertEquals("heated", pressing.getAsJsonObject().get("heat_requirement").getAsString());
                    assertEquals(outputJson, roundTrip(output, registries.json(),
                            pressing.getAsJsonObject().getAsJsonArray("results").get(0)));
                    JsonElement filling = JsonParser.parseString(event.getJson("nekujs:fixture_filling"));
                    assertEquals(fluidJson, filling.getAsJsonObject().getAsJsonArray("ingredients").get(0));
                    assertEquals(fluidJson, roundTrip(fluid, registries.json(),
                            filling.getAsJsonObject().getAsJsonArray("ingredients").get(0)));
                    JsonElement crushing = JsonParser.parseString(event.getJson("nekujs:fixture_crushing"));
                    assertEquals(2, ItemStack.CODEC.parse(registries.json(), crushing.getAsJsonObject().get("output"))
                            .getOrThrow().getCount());
                    assertEquals(3, SizedIngredient.FLAT_CODEC.parse(registries.json(), crushing.getAsJsonObject().get("input"))
                            .getOrThrow().count());

                    // Detached semantic conversion rejects these before allocating another recipe.
                    for (String script : List.of(
                            "recipes.create.pressing([{id:'minecraft:iron_ingot',count:0}],[{item:'minecraft:iron_block'}])",
                            "recipes.create.pressing([{id:'minecraft:iron_ingot',chance:-1}],[{item:'minecraft:iron_block'}])",
                            "recipes.create.filling([{id:'minecraft:iron_ingot'}],[{type:'neoforge:single',fluid:'minecraft:water',amount:0}])",
                            "recipes.mekanism.crushing({id:'minecraft:gravel',count:0},{item:'minecraft:cobblestone',count:3})",
                            "recipes.create.sequenced_assembly({})")) {
                        assertThrows(PolyglotException.class, () -> context.eval("js", script), script);
                        assertEquals(3, event.count(), script);
                    }
                });
            }
        }
    }

    private static void managed(Context context, Runnable action) {
        ScriptContextRegistry.bind(context, ScriptType.SERVER);
        context.enter();
        try { action.run(); }
        finally { context.leave(); ScriptContextRegistry.unbind(context); }
    }

    private static KubeJSPlugin originalPlugin(URLClassLoader loader, Path jar, String name) throws Exception {
        Class<?> plugin = Class.forName(name, true, loader);
        assertSame(loader, plugin.getClassLoader());
        assertEquals(jar.toUri().toURL(), plugin.getProtectionDomain().getCodeSource().getLocation());
        return assertInstanceOf(KubeJSPlugin.class, plugin.getConstructor().newInstance());
    }

    private static RecipeComponent<?> component(RecipeSchemaStorage catalog, RegistryAccessContainer registries, String id) {
        var component = catalog.recipeComponentCodec.parse(registries.json(), new JsonPrimitive(id)).getOrThrow();
        assertSame(catalog.componentTypes().get(ResourceLocation.parse(id)).instance(), component);
        assertEquals(new JsonPrimitive(id), catalog.recipeComponentCodec.encodeStart(registries.json(), component).getOrThrow());
        return component;
    }

    private static <T> JsonElement roundTrip(RecipeComponent<T> component, DynamicOps<JsonElement> ops, JsonElement json) {
        return component.codec().encodeStart(ops, component.codec().parse(ops, json).getOrThrow()).getOrThrow();
    }

    private static URLClassLoader fixtureLoader(Path fixtures, Path directory) throws Exception {
        var urls = new ArrayList<URL>();
        try (var files = Files.list(fixtures)) {
            for (Path jar : files.filter(path -> path.toString().endsWith(".jar")).sorted().toList()) {
                urls.add(jar.toUri().toURL());
            }
        }
        try (var jar = new JarFile(fixtures.resolve("create-1.21.1-6.0.10.jar").toFile())) {
            for (var entry : jar.stream().filter(entry -> entry.getName().startsWith("META-INF/jarjar/")
                    && entry.getName().endsWith(".jar")).toList()) {
                Path library = directory.resolve(Path.of(entry.getName()).getFileName().toString());
                try (var input = jar.getInputStream(entry)) {
                    Files.copy(input, library);
                }
                urls.add(library.toUri().toURL());
            }
        }
        return new URLClassLoader(urls.toArray(URL[]::new), FixtureRecipeCodecPipelineTest.class.getClassLoader());
    }
}
