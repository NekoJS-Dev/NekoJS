//? if neoforge {
package com.tkisor.nekojs.api.recipe.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.api.recipe.RecipeJsonBuilder;
import com.tkisor.nekojs.bindings.recipe.MinecraftRecipeHandler;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.js.type_adapter.IngredientAdapter;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.FunctionalInterfaceAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.wrapper.RecipeReflectionUtil;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.mojang.serialization.JsonOps;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import com.tkisor.nekojs.testfixture.NativeLoadingServicesTestFixture;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("native-recipe-probe")
class MinecraftRecipeSchemaScannerTest {
    @BeforeAll
    static void requireVanillaRegistry() {
        NativeLoadingServicesTestFixture.initialize();
        boolean metadata = VanillaRegistryProbe.registryMetadataAvailable();
        String failure = "Vanilla recipe metadata bootstrap failed: " + VanillaRegistryProbe.metadataFailure();
        if (NativeLoadingServicesTestFixture.requiresIsolatedRegistryMetadata()) assertTrue(metadata, failure);
        else org.junit.jupiter.api.Assumptions.assumeTrue(metadata, failure);
    }

    @Test
    void vanillaShapedDiscoversPatternAndKeyInsteadOfGenericIngredients() {
        var definition = shaped();
        assertEquals(List.of("result", "pattern", "key"), definition.constructors().getFirst());
        assertFalse(definition.fields().containsKey("ingredients"));
        assertEquals("java:java.util.List", definition.fields().get("pattern").valueCodec().inputType().name());
        assertEquals("string", definition.fields().get("pattern").valueCodec().inputType().arguments().getFirst().name());
        var key = definition.fields().get("key").valueCodec().inputType();
        assertEquals("java:java.util.Map", key.name());
        assertEquals("string", key.arguments().getFirst().name());
        assertEquals("java:" + Ingredient.class.getName(), key.arguments().get(1).name());
    }

    @Test
    void vanillaShapelessDiscoversIngredientListWithoutPatternOrKey() {
        var definition = RecipeSchemaAutoDiscovery.discover(MinecraftRecipeSchemaScanner::scan)
                .get("minecraft", "crafting_shapeless");
        assertEquals(List.of("result", "ingredients"), definition.constructors().getFirst());
        assertEquals(RecipeFieldKind.INGREDIENT, definition.fields().get("ingredients").kind());
        assertTrue(definition.fields().get("ingredients").array());
        assertFalse(definition.fields().containsKey("pattern"));
        assertFalse(definition.fields().containsKey("key"));
    }

    @Test
    void registeredSerializerIdentityWinsOverMisleadingNames() {
        var shapeless = BuiltInRegistries.RECIPE_SERIALIZER.stream().filter(serializer ->
                BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer).getPath().equals("crafting_shapeless"))
                .findFirst().orElseThrow();
        var keys = MinecraftRecipeSchemaScanner.extractKeys(Identifier.fromNamespaceAndPath("test", "looks_shaped"), shapeless);
        assertEquals(List.of("result", "ingredients"), keys.stream().map(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey::name).toList());
        assertEquals(RecipeFieldKind.INGREDIENT, keys.getLast().kind());
        assertTrue(keys.getLast().isList());

        var cooking = BuiltInRegistries.RECIPE_SERIALIZER.stream().filter(serializer ->
                BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer).getPath().equals("blasting"))
                .findFirst().orElseThrow();
        var unrelated = MinecraftRecipeSchemaScanner.extractKeys(Identifier.fromNamespaceAndPath("test", "modded_shaped"), cooking);
        assertTrue(unrelated.stream().noneMatch(key -> key.name().equals("pattern") || key.name().equals("key")),
                "An unrelated serializer must not inherit the vanilla shaped contract from a substring");
    }

    @Test
    void shapedFieldCodecsEncodeStringRowsAndCanonicalIngredientValues() {
        var definition = shaped();
        var host = new Host();
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            JsonElement pattern = host.convertField(definition.fields().get("pattern"), context.eval("js", "['AA',' A']"));
            assertEquals(JsonParser.parseString("[\"AA\",\" A\"]"), pattern);
            JsonElement key = host.convertField(definition.fields().get("key"), context.eval("js", "({A:'minecraft:stone'})"));
            assertEquals(JsonParser.parseString("{\"A\":{\"item\":\"minecraft:stone\"}}"), key);
            assertEquals(1, host.ingredients.get());
            JsonElement nativeMap = host.convertField(definition.fields().get("key"), context.asValue(Map.of("A", "minecraft:stone")));
            assertEquals(key, nativeMap);
            assertEquals(2, host.ingredients.get());
            assertEquals(pattern, host.convertField(definition.fields().get("pattern"), context.asValue(List.of("AA", " A"))));
        }
    }

    @Test
    void shapedPatternRejectsNonStringRowsAndIngredientFailuresRemainFailures() {
        var definition = shaped();
        var host = new Host();
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            assertThrows(RuntimeException.class, () -> host.convertField(definition.fields().get("pattern"), context.eval("js", "[1]")));
            assertThrows(RuntimeException.class, () -> host.convertField(definition.fields().get("pattern"), context.asValue("AA")));
            assertThrows(RuntimeException.class, () -> host.convertField(definition.fields().get("key"), context.eval("js", "({A:false})")));
        }
    }

    @Test
    void actualNativeHandlerAndDiscoveredSchemasProduceCanonicalDeclarationsAndRecipeJson() throws Exception {
        var discovered = RecipeSchemaAutoDiscovery.discover(MinecraftRecipeSchemaScanner::scan);
        var schemas = discovered.types("minecraft").stream().sorted()
                .map(type -> RecipeSchemaTypeEntry.from(discovered.get("minecraft", type))).toList();
        var namespace = RecipeNamespaceCatalogEntry.withHandlerMethods("minecraft", MinecraftRecipeHandler.class,
                schemas.stream().map(RecipeSchemaTypeEntry::type).toList(), true, List.of()).withSchemaTypes(schemas);
        List<JSTypeAdapter<?>> adapters = List.of(new ItemStackAdapter(), new IngredientAdapter());
        var catalog = adapters.stream().map(adapter -> new AdapterCatalogEntry(adapter.getTargetClass(),
                adapter.inputShapes(), adapter.getPrecedence(), adapter.syntaxDoc())).toList();
        var aliases = new TypeAliasRegistry();
        new AdapterAliasGenerator(aliases).prepare(catalog,
                catalog.stream().map(entry -> entry.targetType().getName()).collect(Collectors.toSet()));
        String declarations = new RecipeEventDeclarationGenerator(aliases).generate(List.of(namespace), ScriptType.SERVER);
        Path preview = Path.of("build/reports/native-recipe-probe/index.d.ts");
        Files.createDirectories(preview.getParent());
        Files.writeString(preview, declarations);

        var methods = Arrays.stream(MinecraftRecipeHandler.class.getMethods())
                .filter(method -> method.getName().equals("crafting_shaped")).toList();
        assertEquals(2, methods.size());
        assertEquals(2, namespace.handlerMethods().stream().filter(method -> method.methodName().equals("crafting_shaped")).count());
        assertEquals(2, declarations.lines().filter(line -> line.stripLeading().startsWith("crafting_shaped(")).count(), declarations);
        boolean group = methods.stream().anyMatch(method -> method.getParameterTypes()[method.getParameterCount() - 1] == Optional.class);
        assertEquals(group, declarations.contains("group?: string"), declarations);
        for (Method method : methods) {
            assertEquals(RecipeJsonBuilder.class, method.getReturnType());
            String result = TypeScriptClassRenderer.renderTypeRef(TypeReflector.toRef(method.getGenericReturnType()), aliases, false);
            assertTrue(declarations.contains("): " + result + ";"), declarations);
            for (int index = 0; index < method.getParameterCount(); index++) {
                if (method.getParameterTypes()[index] == Optional.class) continue;
                String parameter = FunctionalInterfaceAliasGenerator.renderJavaTypeInput(method.getGenericParameterTypes()[index], aliases,
                        new LinkedHashSet<>());
                assertTrue(declarations.contains(parameter), parameter + "\n" + declarations);
            }
        }
        int start = declarations.indexOf("export class Minecraft$CraftingShaped ");
        assertTrue(start >= 0, declarations);
        String schema = declarations.substring(start, declarations.indexOf("\n    }", start));
        assertTrue(schema.contains("pattern(pattern:"), schema);
        assertTrue(schema.contains("key(key:"), schema);
        assertFalse(schema.contains("ingredients("), schema);
        assertFalse(declarations.contains("crafting_shaped(options:"), declarations);

        if (!VanillaRegistryProbe.available()) {
            Files.writeString(preview.resolveSibling("runtime-status.txt"),
                    "Declaration assertions passed; runtime requires vanilla item components unavailable in this bare JVM.\n");
            return;
        }
        // Calls the real handler through the same generic argument-conversion path, without
        // bootstrapping a global namespace service.
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var event = new RecipeEventJS(Map.of(), registries, discovered);
        var handler = new MinecraftRecipeHandler(event);
        Method normal = methods.stream().filter(method -> Arrays.asList(method.getParameterTypes()).contains(Map.class)).findFirst().orElseThrow();
        Method inline = methods.stream().filter(method -> !Arrays.asList(method.getParameterTypes()).contains(Map.class)).findFirst().orElseThrow();
        try (var context = Context.newBuilder("js").allowHostAccess(new NekoSharedHostAccess(adapters).get())
                .option("engine.WarnInterpreterOnly", "false").build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            context.enter();
            try {
                context.getBindings("js").putMember("normal", (ProxyExecutable) values -> invokeNative(normal, handler, values));
                context.getBindings("js").putMember("inline", (ProxyExecutable) values -> invokeNative(inline, handler, values));
                context.eval("js", "normal('minecraft:stick',['A'],{A:'minecraft:stone'}" + (group ? ",'normal-group'" : "")
                        + ").id('nekojs:native_shaped');");
                context.eval("js", "inline('minecraft:stick',[['minecraft:stone','minecraft:dirt']]" + (group ? ",'inline-group'" : "")
                        + ").id('nekojs:native_inline');");
            } finally {
                context.leave();
                ScriptContextRegistry.unbind(context);
            }
        }
        assertEquals(2, event.count());
        var normalJson = JsonParser.parseString(event.getJson("nekojs:native_shaped")).getAsJsonObject();
        var inlineJson = JsonParser.parseString(event.getJson("nekojs:native_inline")).getAsJsonObject();
        assertEquals(JsonParser.parseString("[\"A\"]"), normalJson.get("pattern"));
        assertEquals(JsonParser.parseString("[\"AB\"]"), inlineJson.get("pattern"));
        assertEquals(2, inlineJson.getAsJsonObject("key").size());
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var serializer = BuiltInRegistries.RECIPE_SERIALIZER.stream().filter(value ->
                BuiltInRegistries.RECIPE_SERIALIZER.getKey(value).getPath().equals("crafting_shaped")).findFirst().orElseThrow();
        for (JsonObject recipe : List.of(normalJson, inlineJson)) {
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
            assertFalse(recipe.has("ingredients"));
            assertTrue(ItemStack.CODEC.parse(ops, recipe.get("result")).getOrThrow().is(Items.STICK));
            assertNotNull(serializer.codec().codec().parse(ops, recipe).getOrThrow());
        }
        assertTrue(Ingredient.CODEC.parse(ops, normalJson.getAsJsonObject("key").get("A")).getOrThrow().test(new ItemStack(Items.STONE)));
        if (group) {
            assertEquals("normal-group", normalJson.get("group").getAsString());
            assertEquals("inline-group", inlineJson.get("group").getAsString());
        } else {
            assertFalse(normalJson.has("group"));
            assertFalse(inlineJson.has("group"));
        }
        Files.writeString(preview.resolveSibling("runtime-status.txt"), "Real native shaped and inline handler JSON validated by the vanilla serializer.\n");
    }

    private static Object invokeNative(Method method, MinecraftRecipeHandler handler, Value[] values) {
        Object[] converted = RecipeReflectionUtil.convertArgs(method, values, method.getParameterCount());
        assertNotNull(converted, method.toString());
        try {
            return method.invoke(handler, converted);
        } catch (InvocationTargetException failure) {
            throw new AssertionError("Actual native recipe handler invocation failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static RecipeTypeDefinition shaped() {
        return RecipeSchemaAutoDiscovery.discover(MinecraftRecipeSchemaScanner::scan).get("minecraft", "crafting_shaped");
    }

    private static final class Host implements RecipeSchemaHost {
        private final AtomicInteger ingredients = new AtomicInteger();
        @Override public RecipeBuilder builder(String type, String prefix) { throw new AssertionError("No publication"); }
        @Override public Object custom(JsonObject value) { throw new AssertionError("No publication"); }
        @Override public JsonElement encodeField(RecipeFieldKind kind, Value value) {
            if (kind == RecipeFieldKind.STRING) return new JsonPrimitive(value.asString());
            if (kind == RecipeFieldKind.INGREDIENT) {
                ingredients.incrementAndGet();
                var ingredient = new JsonObject();
                ingredient.addProperty("item", value.asString());
                return ingredient;
            }
            throw new AssertionError("Unexpected field kind: " + kind);
        }
        @Override public JsonElement toJson(Value value) { throw new AssertionError("Structured fields must use canonical element conversion"); }
    }
}
//?}
