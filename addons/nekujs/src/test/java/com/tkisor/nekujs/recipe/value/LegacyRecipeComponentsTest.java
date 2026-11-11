package com.tkisor.nekujs.recipe.value;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import java.util.List;
import dev.latvian.mods.kubejs.recipe.component.SimpleRecipeComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaStorage;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import dev.latvian.mods.kubejs.util.TickDuration;
import dev.latvian.mods.rhino.type.TypeInfo;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyRecipeComponentsTest {
    private static RegistryAccessContainer registries;

    @BeforeAll static void bootstrap() {
        // Genuine empty loading services for isolated native codec tests; this does not launch the mod loader.
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        net.neoforged.neoforge.registries.NekuJSRecipeRegistryTestFixture.registerIngredientTypes();
        registries = new RegistryAccessContainer(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static JsonElement json(String text) { return JsonParser.parseString(text); }
    private static RecipeSchemaStorage storage() {
        RecipeSchemaStorage storage = new RecipeSchemaStorage(registries);
        storage.freezeComponents();
        return storage;
    }
    private static LegacyRecipeComponents components() { return new LegacyRecipeComponents(storage(), registries); }
    private static String code(Runnable action) { return assertThrows(IllegalArgumentException.class, action::run).getMessage(); }

    @Test void exactLongBoundsRejectCoercionAndOverflow() {
        LegacyRecipeComponents components = components();
        assertEquals(json("9223372036854775807"), components.encodeConstant(json("\"positive_long\""), json("9223372036854775807")));
        assertEquals(json("0"), components.encodeConstant(json("\"non_negative_long\""), json("0")));
        assertTrue(code(() -> components.encodeConstant(json("\"positive_long\""), json("0"))).contains("NEKO-5303"));
        assertTrue(code(() -> components.encodeConstant(json("\"positive_long\""), json("1.25"))).contains("NEKO-5302"));
        assertTrue(code(() -> components.encodeConstant(json("\"positive_long\""), json("9223372036854775808"))).contains("NEKO-5302"));
        assertTrue(code(() -> components.encodeConstant(json("\"boolean\""), json("1"))).contains("NEKO-5284"));
        assertTrue(code(() -> components.encodeConstant(json("{\"type\":\"int\",\"min\":0}"), json("2147483648"))).contains("NEKO-5303"));
    }

    @Test void boundedChanceIsFiniteAndChecksBothEnds() {
        LegacyRecipeComponents components = components();
        JsonElement chance = json("{\"type\":\"double\",\"min\":0.00001,\"max\":1}");
        assertEquals(json("1.0"), components.encodeConstant(chance, json("1")));
        assertTrue(code(() -> components.encodeConstant(chance, json("0"))).contains("NEKO-5287"));
        assertTrue(code(() -> components.encodeConstant(chance, json("1.1"))).contains("NEKO-5287"));
        assertTrue(code(() -> components.encodeConstant(chance, new JsonPrimitive(Double.NaN))).contains("NEKO-5301"));
    }

    @Test void ticksUseExactNumericAndNativeDurationValues() {
        LegacyRecipeComponents components = components();
        var ticks = components.resolve(json("\"ticks\""));
        assertEquals(json("3"), ticks.encoder().encode(Value.asValue(TickDuration.of(3)), new Host()));
        assertEquals(json("3"), ticks.encoder().encode(Value.asValue(Duration.ofMillis(199)), new Host()));
        assertTrue(code(() -> ticks.encoder().encode(Value.asValue(1.5), new Host())).contains("NEKO-5290"));
        assertEquals(json("60"), ticks.encoder().encode(Value.asValue("3s"), new Host()));
        assertTrue(code(() -> ticks.encoder().encode(Value.asValue("bad time"), new Host())).contains("NEKO-4284"));
    }

    @Test void runtimeNumbersRejectRoundingButExactConstantsAndTypedHostsRetainLongs() {
        LegacyRecipeComponents components = componentsWithCustoms(new AtomicInteger());
        try (Context context = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).build()) {
            Value unsafe = context.eval("js", "9007199254740993");
            Value safe = context.eval("js", "9007199254740991");
            var positive = components.resolve(json("\"positive_long\""));
            assertEquals(json("9007199254740991"), positive.encoder().encode(safe, new Host()));
            assertTrue(code(() -> positive.encoder().encode(unsafe, new Host())).contains("NEKO-5305"));
            assertTrue(code(() -> components.resolve(json("\"ticks\""))
                    .encoder().encode(unsafe, new Host())).contains("NEKO-5305"));
            Value nested = context.eval("js", "({id:'minecraft:stone', count:9007199254740993})");
            assertTrue(code(() -> components.resolve(json("\"create:processing_output\""))
                    .encoder().encode(nested, new Host())).contains("NEKO-5305"));
        }
        assertEquals(json("9223372036854775807"), components.encodeConstant(json("\"positive_long\""), json("9223372036854775807")));
        assertEquals(json("9223372036854775807"), components.resolve(json("\"ticks\""))
                .encoder().encode(Value.asValue(TickDuration.of(Long.MAX_VALUE)), new Host()));
    }

    @Test void exactTypedItemStackPreservesItsActualDataComponentPatch() {
        LegacyRecipeComponents components = components();
        ItemStack stack = new ItemStack(Items.STONE, 2);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Typed component patch"));
        JsonElement expected = ItemStack.CODEC.encodeStart(registries.json(), stack).getOrThrow();
        JsonElement encoded = components.resolve(json("\"item_stack\""))
                .encoder().encode(Value.asValue(stack), new Host());
        assertEquals(expected, encoded);
        ItemStack decoded = ItemStack.CODEC.parse(registries.json(), encoded).getOrThrow();
        assertEquals("Typed component patch", decoded.get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test void optionalEmptyMapIsRealWhileRequiredItemStackRejectsIt() {
        LegacyRecipeComponents components = components();
        assertEquals(json("{}"), components.encodeConstant(json("\"optional_item_stack\""), json("{}")));
        assertTrue(code(() -> components.encodeConstant(json("\"item_stack\""), json("{}"))).contains("NEKO-5293"));
        assertEquals(json("{}"), components.resolve(json("\"optional_item_stack\""))
                .encoder().encode(Value.asValue(ItemStack.EMPTY), new Host()));
    }

    @Test void flatSizedIngredientAndCountSpreadUseActualNeoForgeCodec() {
        LegacyRecipeComponents components = componentsWithCustoms(new AtomicInteger());
        JsonElement flat = components.encodeConstant(json("\"flat_sized_ingredient\""), json("{\"item\":\"minecraft:stone\",\"count\":3}"));
        SizedIngredient sized = SizedIngredient.FLAT_CODEC.parse(registries.json(), flat).getOrThrow();
        assertEquals(3, sized.count());
        assertTrue(sized.ingredient().test(new ItemStack(Items.STONE)));
        JsonElement expression = json("{\"type\":\"list\",\"component\":{\"type\":\"either\",\"left\":\"create:sized_fluid_ingredient\",\"right\":\"ingredient\"},\"spread\":{\"type\":\"either\",\"left\":\"ignore\",\"right\":\"sized_ingredient\"}}");
        JsonElement result = components.encodeConstant(expression, flat);
        assertEquals(3, result.getAsJsonArray().size());
        for (JsonElement ingredient : result.getAsJsonArray()) {
            assertTrue(Ingredient.CODEC_NONEMPTY.parse(registries.json(), ingredient).getOrThrow().test(new ItemStack(Items.STONE)));
            assertFalse(ingredient.getAsJsonObject().has("count"));
        }
        assertTrue(code(() -> components.encodeConstant(json("\"flat_sized_ingredient\""), json("{\"item\":\"minecraft:stone\",\"count\":0}"))).contains("NEKO-5303"));
    }

    @Test void customRegisteredCodecActuallyRunsForLiteralAndTypedHost() {
        AtomicInteger decoded = new AtomicInteger();
        LegacyRecipeComponents components = componentsWithCustoms(decoded);
        assertEquals(json("\"heated\""), components.encodeConstant(json("\"create:heat_condition\""), json("\"heated\"")));
        assertEquals(1, decoded.get());
        assertTrue(code(() -> components.encodeConstant(json("\"create:heat_condition\""), json("\"bad\""))).contains("NEKO-5298"));
        var output = components.resolve(json("\"create:processing_output\""));
        assertEquals(json("{\"id\":\"minecraft:stone\"}"), output.encoder().encode(Value.asValue(new Output("minecraft:stone")), new Host()));
        assertTrue(output.inputShape() instanceof AdapterInputShape.UnionValue);
        assertTrue(code(() -> components.encodeConstant(json("\"create:processing_output\""), json("{\"id\":\"minecraft:stone\",\"unknown\":1}"))).contains("NEKO-5298"));
    }

    @Test void listsAcceptScalarAndArrayAndNeverProbeTheOtherUnionCodec() {
        AtomicInteger decoded = new AtomicInteger();
        LegacyRecipeComponents components = componentsWithCustoms(decoded);
        JsonElement expression = json("{\"type\":\"list\",\"component\":\"create:processing_output\"}");
        assertEquals(json("[{\"id\":\"minecraft:stone\"}]"), components.encodeConstant(expression, json("{\"id\":\"minecraft:stone\"}")));
        try (Context context = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).build()) {
            Value outputs = context.eval("js", "[{id:'minecraft:stone'}, {id:'minecraft:dirt'}]");
            assertEquals(2, components.resolve(expression).encoder().encode(outputs, new Host()).getAsJsonArray().size());
        }
        JsonElement either = json("{\"type\":\"either\",\"left\":\"fluid_stack\",\"right\":\"create:processing_output\"}");
        int before = decoded.get();
        assertTrue(code(() -> components.encodeConstant(either, json("{\"id\":\"missing:not_a_fluid\",\"amount\":1000}"))).contains("NEKO-5293"));
        assertEquals(before, decoded.get(), "A failed selected fluid branch must not attempt processing output");
    }

    @Test void unsupportedExpressionsAndUnknownMetadataFailBeforeEncoding() {
        LegacyRecipeComponents components = components();
        assertTrue(code(() -> components.resolve(json("{\"type\":\"list\",\"component\":\"nested_recipe\"}"))).contains("NEKO-5285"));
        assertTrue(code(() -> components.resolve(json("\"unknown\""))).contains("NEKO-5286"));
        assertTrue(code(() -> components.resolve(json("{\"type\":\"int\",\"min\":0,\"max\":10}"))).contains("NEKO-5304"));
        assertTrue(code(() -> components.resolve(json("{\"type\":\"either\",\"left\":\"boolean\",\"right\":\"ticks\"}"))).contains("NEKO-5291"));
    }

    @Test void flatSizedIngredientUsesCanonicalStructuredIngredientConversion() {
        assertCanonicalSizedInput(json("\"flat_sized_ingredient\""));
    }

    @Test void createCountSpreadUsesCanonicalStructuredIngredientConversion() {
        assertCanonicalSizedInput(json("{\"type\":\"list\",\"component\":{\"type\":\"either\",\"left\":\"create:sized_fluid_ingredient\",\"right\":\"ingredient\"},\"spread\":{\"type\":\"either\",\"left\":\"ignore\",\"right\":\"sized_ingredient\"}}"));
    }

    private static void assertCanonicalSizedInput(JsonElement expression) {
        LegacyRecipeComponents components = componentsWithCustoms(new AtomicInteger());
        AtomicInteger nativeConversions = new AtomicInteger();
        Host host = new Host() {
            @Override public JsonElement encodeField(RecipeFieldKind kind, Value value) {
                assertEquals(RecipeFieldKind.INGREDIENT, kind);
                nativeConversions.incrementAndGet();
                return Ingredient.CODEC_NONEMPTY.encodeStart(registries.json(), IngredientResolver.fromValue(value)).getOrThrow();
            }
        };
        try (Context context = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).build()) {
            for (String input : List.of("({item:'minecraft:stone',count:2,unsupported:true})",
                    "({ingredient:{any:['minecraft:stone','minecraft:dirt']},count:2})")) {
                JsonElement encoded = components.resolve(expression).encoder().encode(context.eval("js", input), host);
                if (encoded.isJsonArray()) assertEquals(2, encoded.getAsJsonArray().size());
                else assertEquals(2, SizedIngredient.FLAT_CODEC.parse(registries.json(), encoded).getOrThrow().count());
            }
            assertEquals(2, nativeConversions.get(), "Canonical ingredient syntax must reach the native resolver");
            for (String count : List.of("0", "1.5", "4294967297")) {
                assertThrows(IllegalArgumentException.class, () -> components.resolve(expression).encoder().encode(
                        context.eval("js", "({item:'minecraft:stone',count:" + count + "})"), host));
            }
            assertEquals(2, nativeConversions.get(), "Invalid counts must still fail before native conversion");
        }
    }

    @Test void fluidOutputUnionRoutesAdvertisedStringOnlyThroughNativeFluidConverter() {
        assertFluidOutputString(false);
    }

    @Test void fluidOutputListRoutesAdvertisedScalarStringOnlyThroughNativeFluidConverter() {
        assertFluidOutputString(true);
    }

    private static void assertFluidOutputString(boolean list) {
        AtomicInteger outputDecodes = new AtomicInteger();
        AtomicInteger fluidConversions = new AtomicInteger();
        LegacyRecipeComponents components = componentsWithCustoms(outputDecodes);
        JsonElement either = json("{\"type\":\"either\",\"left\":\"fluid_stack\",\"right\":\"create:processing_output\"}");
        JsonElement expression = either;
        if (list) {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("type", "list");
            wrapper.add("component", either);
            expression = wrapper;
        }
        Host fluidOnlyHost = new Host() {
            @Override public JsonElement encodeField(RecipeFieldKind kind, Value value) {
                assertEquals(RecipeFieldKind.FLUID_STACK, kind);
                assertEquals("minecraft:water", value.asString());
                fluidConversions.incrementAndGet();
                return FluidStack.CODEC.encodeStart(registries.json(), new FluidStack(Fluids.WATER, 1000)).getOrThrow();
            }
        };
        JsonElement encoded = components.resolve(expression).encoder().encode(Value.asValue("minecraft:water"), fluidOnlyHost);
        FluidStack actual = FluidStack.CODEC.parse(registries.json(), list ? encoded.getAsJsonArray().get(0) : encoded).getOrThrow();
        assertEquals(Fluids.WATER, actual.getFluid());
        assertEquals(1000, actual.getAmount());
        assertEquals(1, fluidConversions.get());
        assertEquals(0, outputDecodes.get());
    }

    @Test void processingOutputConstantCountRejectsFractionAndWrappingOverflowBeforeCodec() {
        LegacyRecipeComponents components = componentsWithNumericCodecs();
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> components.encodeConstant(json("\"create:processing_output\""), json("{\"id\":\"minecraft:stone\",\"count\":1.5}"))),
                () -> assertThrows(IllegalArgumentException.class, () -> components.encodeConstant(json("\"create:processing_output\""), json("{\"id\":\"minecraft:stone\",\"count\":4294967297}"))));
    }

    @Test void processingOutputRuntimeCountRejectsFractionAndWrappingOverflowBeforeCodec() {
        LegacyRecipeComponents components = componentsWithNumericCodecs();
        var output = components.resolve(json("\"create:processing_output\""));
        try (Context context = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).build()) {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> output.encoder().encode(context.eval("js", "({id:'minecraft:stone',count:1.5})"), new Host())),
                    () -> assertThrows(IllegalArgumentException.class, () -> output.encoder().encode(context.eval("js", "({id:'minecraft:stone',count:4294967297})"), new Host())));
        }
    }

    @Test void chemicalConstantAmountRejectsFractionAndWrappingOverflowBeforeCodec() {
        LegacyRecipeComponents components = componentsWithNumericCodecs();
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> components.encodeConstant(json("\"mekanism:chemical_stack\""), json("{\"id\":\"mekanism:hydrogen\",\"amount\":1.5}"))),
                () -> assertThrows(IllegalArgumentException.class, () -> components.encodeConstant(json("\"mekanism:chemical_stack\""), json("{\"id\":\"mekanism:hydrogen\",\"amount\":18446744073709551617}"))));
    }

    @Test void chemicalRuntimeAmountRejectsFractionBeforeCodecAndUnsafeIntegerBeforeConversion() {
        LegacyRecipeComponents components = componentsWithNumericCodecs();
        var chemical = components.resolve(json("\"mekanism:chemical_stack\""));
        try (Context context = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).build()) {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> chemical.encoder().encode(context.eval("js", "({id:'mekanism:hydrogen',amount:1.5})"), new Host())),
                    () -> assertTrue(code(() -> chemical.encoder().encode(context.eval("js", "({id:'mekanism:hydrogen',amount:18446744073709551617})"), new Host())).contains("NEKO-5305")));
        }
    }

    @Test void chemicalExactSourceConstantsAndTypedHostsRetainFullPositiveLongRange() {
        LegacyRecipeComponents components = componentsWithNumericCodecs();
        JsonElement expression = json("\"mekanism:chemical_stack\"");
        JsonElement expected = json("{\"id\":\"mekanism:hydrogen\",\"amount\":9223372036854775807}");
        assertEquals(expected, components.encodeConstant(expression, expected));
        assertEquals(expected, components.resolve(expression).encoder().encode(Value.asValue(new Chemical("mekanism:hydrogen", Long.MAX_VALUE)), new Host()));
    }

    @Test void nativeConstantCountAndAmountRejectFractionAndWrappingOverflowBeforeCodec() {
        LegacyRecipeComponents components = components();
        String[][] fields = {
                {"item_stack", "{\"id\":\"minecraft:stone\",\"count\":%s}"},
                {"optional_item_stack", "{\"id\":\"minecraft:stone\",\"count\":%s}"},
                {"fluid_stack", "{\"id\":\"minecraft:water\",\"amount\":%s}"},
                {"optional_fluid_stack", "{\"id\":\"minecraft:water\",\"amount\":%s}"},
                {"flat_sized_ingredient", "{\"item\":\"minecraft:stone\",\"count\":%s}"},
                {"flat_sized_fluid_ingredient", "{\"fluid\":\"minecraft:water\",\"amount\":%s}"}
        };
        for (String[] field : fields) {
            JsonElement expression = new JsonPrimitive(field[0]);
            for (String invalid : new String[] {"1.5", "4294967297", "0"}) {
                String message = code(() -> components.encodeConstant(expression, json(field[1].formatted(invalid))));
                assertTrue(message.contains("NEKO-5302") || message.contains("NEKO-5303"), field[0] + ": " + message);
            }
        }
    }

    @Test void countSpreadConstantsRejectCoercionBeforeRepetition() {
        LegacyRecipeComponents components = componentsWithCustoms(new AtomicInteger());
        JsonElement expression = json("{\"type\":\"list\",\"component\":{\"type\":\"either\",\"left\":\"create:sized_fluid_ingredient\",\"right\":\"ingredient\"},\"spread\":{\"type\":\"either\",\"left\":\"ignore\",\"right\":\"sized_ingredient\"}}");
        for (String invalid : new String[] {"1.5", "4294967297", "0"}) {
            String message = code(() -> components.encodeConstant(expression, json("{\"item\":\"minecraft:stone\",\"count\":" + invalid + "}")));
            assertTrue(message.contains("NEKO-5302") || message.contains("NEKO-5303"));
        }
    }

    @Test void invalidNativeRuntimeNumbersNeverReachNativeConverter() {
        LegacyRecipeComponents components = components();
        try (Context context = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).build()) {
            assertAll(
                    () -> assertTrue(code(() -> components.resolve(json("\"item_stack\"")).encoder()
                            .encode(context.eval("js", "({id:'minecraft:stone',count:1.5})"), new Host())).contains("NEKO-5302")),
                    () -> assertTrue(code(() -> components.resolve(json("\"fluid_stack\"")).encoder()
                            .encode(context.eval("js", "({id:'minecraft:water',amount:4294967297})"), new Host())).contains("NEKO-5303")),
                    () -> assertTrue(code(() -> components.resolve(json("\"flat_sized_fluid_ingredient\"")).encoder()
                            .encode(context.eval("js", "({fluid:'minecraft:water',amount:1.5})"), new Host())).contains("NEKO-5302")));
        }
    }

    private record CountedOutput(String id, int count) {}
    private record Chemical(String id, long amount) {}

    private static LegacyRecipeComponents componentsWithNumericCodecs() {
        // Exercise Mojang's actual integer codecs; these local records do not stand in for addon runtime types.
        RecipeSchemaStorage storage = new RecipeSchemaStorage(registries);
        Codec<CountedOutput> output = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(CountedOutput::id),
                Codec.intRange(1, 99).optionalFieldOf("count", 1).forGetter(CountedOutput::count)
        ).apply(instance, CountedOutput::new));
        Codec<Chemical> chemical = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Chemical::id),
                Codec.LONG.validate(amount -> amount > 0 ? DataResult.success(amount)
                        : DataResult.error(() -> "Chemical amount must be positive"))
                        .fieldOf("amount").forGetter(Chemical::amount)
        ).apply(instance, Chemical::new));
        storage.register(RecipeComponentType.<CountedOutput>unit(ResourceLocation.parse("create:processing_output"),
                type -> wrappedComponent(type, output, TypeInfo.of(CountedOutput.class))));
        storage.register(RecipeComponentType.<Chemical>unit(ResourceLocation.parse("mekanism:chemical_stack"),
                type -> new SimpleRecipeComponent<>(type, chemical, TypeInfo.of(Chemical.class))));
        storage.freezeComponents();
        return new LegacyRecipeComponents(storage, registries);
    }

    private record Output(String id) {}

    private static LegacyRecipeComponents componentsWithCustoms(AtomicInteger decoded) {
        RecipeSchemaStorage storage = new RecipeSchemaStorage(registries);
        Codec<String> heat = Codec.STRING.comapFlatMap(value -> {
            decoded.incrementAndGet();
            return DataResult.success(value);
        }, value -> value);
        storage.register(RecipeComponentType.<String>unit(ResourceLocation.parse("create:heat_condition"),
                type -> new SimpleRecipeComponent<>(type, heat, TypeInfo.of(String.class))));
        Codec<Output> output = Codec.STRING.fieldOf("id").xmap(id -> {
            decoded.incrementAndGet();
            return new Output(id);
        }, Output::id).codec();
        storage.register(RecipeComponentType.<Output>unit(ResourceLocation.parse("create:processing_output"),
                type -> wrappedComponent(type, output, TypeInfo.of(Output.class))));
        storage.register(RecipeComponentType.<String>unit(ResourceLocation.parse("create:sized_fluid_ingredient"),
                type -> wrappedComponent(type, Codec.STRING, TypeInfo.of(String.class))));
        storage.freezeComponents();
        return new LegacyRecipeComponents(storage, registries);
    }

    /** Local codec fixtures explicitly implement wrapping; they do not replace the real registered-addon regression. */
    private static <T> RecipeComponent<T> wrappedComponent(RecipeComponentType<?> type, Codec<T> codec, TypeInfo info) {
        return new SimpleRecipeComponent<>(type, codec, info) {
            @Override public T wrap(dev.latvian.mods.kubejs.recipe.RecipeScriptContext context, Object input) {
                Value value = (Value) input;
                String serialized = value.getContext().eval("js", "JSON.stringify").execute(value).asString();
                return codec.parse(registries.json(), json(serialized)).getOrThrow(IllegalArgumentException::new);
            }
        };
    }

    private static class Host implements RecipeSchemaHost {
        @Override public RecipeBuilder builder(String type, String prefix) { throw new AssertionError("Value conversion must not publish a builder"); }
        @Override public Object custom(JsonObject json) { throw new AssertionError("Value conversion must not publish a recipe"); }
        @Override public JsonElement encodeField(RecipeFieldKind kind, Value value) {
            throw new AssertionError("This test requires typed/native codec or canonical literal conversion");
        }
        @Override public JsonElement toJson(Value value) { throw new AssertionError("No raw host conversion fallback"); }
    }
}
