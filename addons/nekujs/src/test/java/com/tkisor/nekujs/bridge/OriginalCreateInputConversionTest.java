package com.tkisor.nekujs.bridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.js.type_adapter.*;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekujs.recipe.value.LegacyRecipeComponents;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaStorage;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises unchanged Create wrapper registration through managed native conversion, not a DTO substitute. */
@Tag("expanded-profile")
class OriginalCreateInputConversionTest {
    @BeforeAll
    static void bootstrap() {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        net.neoforged.neoforge.registries.NekuJSRecipeRegistryTestFixture.registerFluidIngredientTypes();
    }

    @Test
    void originalRegisteredProcessingOutputFactoryAcceptsCanonicalItemInputs(@TempDir Path directory) throws Exception {
        try (var fixture = fixture(directory); var context = context(fixture.adapters())) {
            managed(context, () -> {
                try {
                    var output = fixture.components().resolve(json("\"create:processing_output\""));
                    assertOutput(fixture, output.encoder().encode(context.asValue("2x minecraft:stone"), fixture.host()), 2, 1F);
                    assertOutput(fixture, output.encoder().encode(context.eval("js", "({id:'minecraft:stone',count:2})"), fixture.host()), 2, 1F);
                    assertOutput(fixture, output.encoder().encode(context.eval("js", "({id:'minecraft:stone',count:2,chance:0.25})"), fixture.host()), 2, 0.25F);
                    assertOutput(fixture, output.encoder().encode(context.eval("js", "({output:{id:'minecraft:stone',count:3},chance:0.5})"), fixture.host()), 3, 0.5F);
                    assertOutput(fixture, output.encoder().encode(context.eval("js", "({output:'minecraft:stone',chance:'0.25'})"), fixture.host()), 1, 0.25F);
                    assertThrows(RuntimeException.class, () -> output.encoder().encode(
                            context.eval("js", "({output:'minecraft:stone'})"), fixture.host()));
                    assertOutput(fixture, output.encoder().encode(context.eval("js", "({output:'minecraft:stone',chance:2})"), fixture.host()), 1, 1F);
                    // The original factory clamps to zero; the genuine component codec still requires a positive chance.
                    for (String chance : List.of("-1", "0")) {
                        var failure = assertThrows(IllegalArgumentException.class, () -> output.encoder().encode(
                                context.eval("js", "({output:'minecraft:stone',chance:" + chance + "})"), fixture.host()));
                        assertTrue(failure.getMessage().contains("NEKO-5294"), failure.getMessage());
                        assertTrue(failure.getMessage().contains("positive"), failure.getMessage());
                    }
                    assertThrows(IllegalArgumentException.class, () -> output.encoder().encode(
                            context.eval("js", "({id:'minecraft:stone',count:1.5})"), fixture.host()));
                    var either = fixture.components().resolve(json("{\"type\":\"either\",\"left\":\"fluid_stack\",\"right\":\"create:processing_output\"}"));
                    assertOutput(fixture, either.encoder().encode(context.asValue("minecraft:stone"), fixture.host()), 1, 1F);
                    assertEquals("java:" + List.class.getName(), fixture.components().resolve(json(
                            "{\"type\":\"list\",\"component\":\"create:processing_output\"}")).inputType().name());
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            });
        }
    }

    @Test
    void nativeFluidDefaultsAndIngredientCallbacksReachTheCanonicalConverters(@TempDir Path directory) throws Exception {
        try (var fixture = fixture(directory); var context = context(fixture.adapters())) {
            managed(context, () -> {
                var fluid = fixture.components().resolve(json("\"fluid_stack\""));
                JsonElement encoded = fluid.encoder().encode(context.eval("js", "({fluid:'minecraft:water'})"), fixture.host());
                assertEquals(1000, FluidStack.CODEC.parse(fixture.registries().json(), encoded).getOrThrow().getAmount());
                assertThrows(IllegalArgumentException.class, () -> fluid.encoder().encode(
                        context.eval("js", "({fluid:'minecraft:water',amount:0.5})"), fixture.host()));
                context.getBindings("js").putMember("selected", Items.STONE);
                var ingredient = fixture.components().resolve(json("\"ingredient\""));
                String selected = ingredient.encoder().encode(context.eval("js", "({filter: stack => stack.is(selected)})"), fixture.host()).toString();
                assertTrue(selected.contains("minecraft:stone"), selected);
                assertFalse(selected.contains("minecraft:dirt"), selected);
                assertEquals(60, fixture.components().resolve(json("\"ticks\"")).encoder()
                        .encode(context.asValue("3s"), fixture.host()).getAsLong());
                var sized = fixture.components().resolve(json("\"create:sized_fluid_ingredient\""));
                JsonElement sizedJson = sized.encoder().encode(context.eval("js", "({fluid:'minecraft:water'})"), fixture.host());
                assertEquals(1000, net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient.FLAT_CODEC
                        .parse(fixture.registries().json(), sizedJson).getOrThrow().amount());
            });
        }
    }

    private static void assertOutput(Fixture fixture, JsonElement encoded, int count, float chance) throws ReflectiveOperationException {
        Object output = fixture.output().codec().parse(fixture.registries().json(), encoded).getOrThrow();
        ItemStack stack = (ItemStack) output.getClass().getMethod("getStack").invoke(output);
        assertTrue(stack.is(Items.STONE));
        assertEquals(count, stack.getCount());
        assertEquals(chance, ((Number) output.getClass().getMethod("getChance").invoke(output)).floatValue());
    }

    private static Context context(JSTypeAdapterRegistry adapters) {
        return Context.newBuilder("js").allowHostAccess(new NekoSharedHostAccess(List.copyOf(adapters.view())).get())
                .option("engine.WarnInterpreterOnly", "false").build();
    }

    private static void managed(Context context, Runnable action) {
        ScriptContextRegistry.bind(context, ScriptType.SERVER);
        context.enter();
        try { action.run(); }
        finally { context.leave(); ScriptContextRegistry.unbind(context); }
    }

    private record Fixture(URLClassLoader loader, RegistryAccessContainer registries, RecipeComponent<?> output,
                           LegacyRecipeComponents components, JSTypeAdapterRegistry adapters, RecipeSchemaHost host) implements AutoCloseable {
        @Override public void close() throws java.io.IOException { loader.close(); }
    }

    private static Fixture fixture(Path directory) throws Exception {
        Path fixtures = Path.of(System.getProperty("nekujs.fixtures.dir"));
        var urls = new ArrayList<URL>();
        try (var files = Files.list(fixtures)) {
            for (Path jar : files.filter(path -> path.toString().endsWith(".jar")).sorted().toList()) urls.add(jar.toUri().toURL());
        }
        try (var jar = new JarFile(fixtures.resolve("create-1.21.1-6.0.10.jar").toFile())) {
            for (var entry : jar.stream().filter(value -> value.getName().startsWith("META-INF/jarjar/") && value.getName().endsWith(".jar")).toList()) {
                Path library = directory.resolve(Path.of(entry.getName()).getFileName().toString());
                try (var input = jar.getInputStream(entry)) { Files.copy(input, library); }
                urls.add(library.toUri().toURL());
            }
        }
        var loader = new URLClassLoader(urls.toArray(URL[]::new), OriginalCreateInputConversionTest.class.getClassLoader());
        Class<?> pluginClass = Class.forName("dev.latvian.mods.kubejs.create.KubeJSCreatePlugin", true, loader);
        assertSame(loader, pluginClass.getClassLoader());
        assertEquals(fixtures.resolve("kubejs-create-neoforge-2101.3.1-build.18.jar").toUri().toURL(),
                pluginClass.getProtectionDomain().getCodeSource().getLocation());
        var plugin = (KubeJSPlugin) pluginClass.getConstructor().newInstance();
        var registries = new RegistryAccessContainer(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var storage = new RecipeSchemaStorage(registries);
        plugin.registerRecipeComponents(storage);
        storage.freezeComponents();
        RecipeComponent<?> output = storage.recipeComponentCodec.parse(registries.json(), json("\"create:processing_output\"")).getOrThrow();
        JSTypeAdapterRegistry adapters = OriginalRecipeInputFixture.adapters(plugin);
        var registeredOutput = adapters.view().stream().filter(adapter -> adapter.getTargetClass() == output.typeInfo().asClass())
                .findFirst().orElseThrow();
        assertTrue(registeredOutput.inputShapes().isEmpty(),
                "Opaque registered converters retain nominal types instead of inferred input descriptions");
        RecipeEventJS event = new RecipeEventJS(Map.of(), registries.access());
        RecipeSchemaHost host = new RecipeSchemaHost() {
            @Override public RecipeBuilder builder(String type, String prefix) { throw new AssertionError("No recipe publication"); }
            @Override public Object custom(com.google.gson.JsonObject json) { throw new AssertionError("No recipe publication"); }
            @Override public JsonElement toJson(Value value) { throw new AssertionError("No untyped fallback"); }
            @Override public JsonElement encodeField(RecipeFieldKind kind, Value value) {
                return switch (kind) {
                    case INGREDIENT -> event.serializeIngredient(new IngredientAdapter().apply(value));
                    case FLUID_STACK -> event.serializeFluidStack(new FluidStackAdapter().apply(value));
                    default -> throw new AssertionError("Unexpected native field kind: " + kind);
                };
            }
        };
        return new Fixture(loader, registries, output, new LegacyRecipeComponents(storage, registries), adapters, host);
    }

    private static JsonElement json(String value) { return JsonParser.parseString(value); }
}
