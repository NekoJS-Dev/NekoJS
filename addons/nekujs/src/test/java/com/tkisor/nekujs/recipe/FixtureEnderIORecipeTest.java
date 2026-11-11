package com.tkisor.nekujs.recipe;

import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.platform.*;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekujs.recipe.schema.ProgrammaticRecipeSchemas;
import dev.latvian.mods.kubejs.recipe.schema.*;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

/** Original EnderIO schemas and fluent targets mutate the native event's recipe JSON. */
@Tag("expanded-profile")
class FixtureEnderIORecipeTest {
    @BeforeAll
    static void bootstrap(@TempDir Path directory) {
        try {
            Platform.instance();
        } catch (IllegalStateException uninitialized) {
            assertEquals("Platform has not been initialized", uninitialized.getMessage());
            FixtureRecipeCodecPipelineTest.initializeNativeServices(directory);
        }
        // Both expanded recipe fixtures borrow the same immutable platform and real registry bootstrap.
        assertFalse(Platform.isClient());
        assertTrue(Platform.isDevelopment());
        assertEquals("1.21.1", Platform.getMcVersion());
        assertEquals("test", Platform.getLoaderId());
        assertEquals("0", Platform.getLoaderVersion());
        assertTrue(Platform.getMods().isEmpty());
        assertTrue(net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_INGREDIENT_TYPES
                .containsKey(ResourceLocation.parse("neoforge:single")));
        assertTrue(net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_INGREDIENT_TYPES
                .containsKey(ResourceLocation.parse("neoforge:tag")));
    }

    @Test
    void originalTankSchemaAndSubtypeUseNativeBuilder(@TempDir Path directory) throws Exception {
        Path fixtures = Path.of(System.getProperty("nekujs.fixtures.dir"));
        try (var loader = loader(fixtures, directory)) {
            Class<?> original = Class.forName("com.almostreliable.kubeio.schema.TankRecipeSchema", true, loader);
            assertSame(loader, original.getClassLoader());
            var schema = (RecipeSchema) original.getField("SCHEMA").get(null);
            var registries = new RegistryAccessContainer(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            var storage = new RecipeSchemaStorage(registries);
            var plugin = (dev.latvian.mods.kubejs.plugin.KubeJSPlugin) Class.forName("com.almostreliable.kubeio.KubePlugin", true, loader).getConstructor().newInstance();
            var groups = new ArrayList<dev.latvian.mods.kubejs.event.EventGroup>();
            plugin.registerEvents(groups::add);
            var nativeEvents = new com.tkisor.nekojs.api.event.EventGroupRegistry.Impl();
            groups.forEach(group -> group.register(nativeEvents, false));
            assertFalse(com.tkisor.nekojs.core.api.EventContractReflector.extractEvents(nativeEvents.view().values()).isEmpty(),
                    "Original public payload signatures must survive the same bootstrap reflection used in game");
            plugin.registerRecipeComponents(storage);
            storage.freezeComponents();
            plugin.registerRecipeFactories(new RecipeFactoryRegistry(storage));
            plugin.registerRecipeSchemas(new RecipeSchemaRegistry(storage));
            assertEquals(9, storage.programmaticSchemas().size());
            assertEquals(3, storage.recipeFactories().size());
            var compiled = ProgrammaticRecipeSchemas.compile(storage);
            var definition = compiled.get("enderio:tank");
            var listShape = (com.tkisor.nekojs.api.AdapterInputShape.ArrayOfValue) compiled.get("enderio:alloy_smelting").fields().get("inputs").valueCodec().inputShape();
            var elementShape = (com.tkisor.nekojs.api.AdapterInputShape.UnionValue) listShape.element();
            assertTrue(elementShape.members().stream().anyMatch(shape -> shape instanceof com.tkisor.nekojs.api.AdapterInputShape.HostValue host
                    && host.cls() == net.minecraft.world.item.ItemStack.class));
            var forced = new RecipeSchemaStorage(registries);
            forced.registerSchema(ResourceLocation.parse("test:forced"), new RecipeSchema(
                    Map.of(schema.keys.getFirst(), RecipeOptional.unit(null)), schema.keys));
            assertTrue(assertThrows(UnsupportedOperationException.class, () -> ProgrammaticRecipeSchemas.compile(forced))
                    .getMessage().contains("NEKO-5347"));
            assertNotNull(definition.executionFactory());
            assertEquals("com.almostreliable.kubeio.recipe.TankKubeRecipe", definition.executionFactory().exposedType().getName());
            assertTrue(definition.fieldFunctions().isEmpty(), "noFunctions must not become automatic setters");
            var definitions = RecipeTypeDefinitionRegistry.builder();
            compiled.values().forEach(definitions::add);
            NekoPluginRuntime.bootstrapOwned(List.of(), new ScriptPropertyRegistry.Impl());
            var event = new RecipeEventJS(new HashMap<>(), registries.access(), definitions.build());
            try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
                context.getBindings("js").putMember("event", event);
                context.eval("js", """
                    const recipes = event.getRecipes();
                    const tank = recipes.enderio.tank('minecraft:bucket', 'minecraft:water_bucket',
                        {type:'neoforge:single', fluid:'minecraft:water', amount:1000});
                    tank.emptying().id('nekujs:original_tank');
                    """);
                var json = JsonParser.parseString(event.getJson("nekujs:original_tank")).getAsJsonObject();
                assertEquals("empty", json.get("mode").getAsString());
                assertEquals("enderio:tank", json.get("type").getAsString());
                assertEquals(1000, json.getAsJsonObject("fluid").get("amount").getAsInt());
                assertEquals(1, event.count());
                assertThrows(PolyglotException.class, () -> context.eval("js", "tank.mode('fill')"));
                assertThrows(PolyglotException.class, () -> context.eval("js", "recipes.enderio.tank('minecraft:bucket','minecraft:water_bucket',{type:'neoforge:single',fluid:'minecraft:water',amount:0})"));
                assertEquals(1, event.count(), "invalid arguments must not publish a recipe");
                context.getBindings("js").putMember("stone", net.minecraft.world.level.block.Blocks.STONE);
                context.eval("js", "recipes.enderio.fire_crafting(['minecraft:coal']).block(stone).id('nekujs:original_fire')");
                event.prepareRecipesForDecode();
                var fire = JsonParser.parseString(event.getJson("nekujs:original_fire")).getAsJsonObject();
                assertEquals("minecraft:stone", fire.getAsJsonArray("base_blocks").get(0).getAsString());
                var invalid = new RecipeEventJS(new HashMap<>(), registries.access(), definitions.build());
                context.getBindings("js").putMember("invalid", invalid);
                context.eval("js", "invalid.getRecipes().enderio.fire_crafting(['minecraft:coal'])");
                var failure = assertThrows(dev.latvian.mods.kubejs.error.KubeRuntimeException.class, invalid::prepareRecipesForDecode);
                assertTrue(failure.getMessage().contains("at least one base block or tag"));
            }
        }
    }

    private static URLClassLoader loader(Path fixtures, Path directory) throws Exception {
        var urls = new ArrayList<URL>();
        for (String name : List.of("kubejs_enderio-neoforge-1.21.1-0.13.0.jar", "enderio-8.2.12-beta.jar")) {
            Path file = fixtures.resolve(name);
            assertTrue(Files.isRegularFile(file), "Missing original fixture: " + file);
            urls.add(file.toUri().toURL());
            try (var jar = new JarFile(file.toFile())) {
                for (var entry : jar.stream().filter(value -> value.getName().startsWith("META-INF/jarjar/") && value.getName().endsWith(".jar")).toList()) {
                    Path target = directory.resolve(Path.of(entry.getName()).getFileName().toString());
                    try (var input = jar.getInputStream(entry)) { Files.copy(input, target); }
                    urls.add(target.toUri().toURL());
                }
            }
        }
        return new URLClassLoader(urls.toArray(URL[]::new), FixtureEnderIORecipeTest.class.getClassLoader());
    }
}
