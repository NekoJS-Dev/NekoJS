package com.tkisor.nekujs.legacyrecipe;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.latvian.mods.kubejs.recipe.component.*;
import dev.latvian.mods.kubejs.util.IntBounds;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProgrammaticComponentsTest {
    private static RegistryAccessContainer registries;

    @BeforeAll static void bootstrap() {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = new RegistryAccessContainer(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static JsonElement json(String text) { return JsonParser.parseString(text); }
    private static <T> JsonElement encode(RecipeComponent<T> component, T value) {
        return component.codec().encodeStart(registries.json(), value).getOrThrow();
    }

    @Test void originalEnderIoSingletonFieldsHaveTheirRequiredAbiTypes() throws ReflectiveOperationException {
        for (RecipeComponentType<?> type : List.of(ItemStackComponent.ITEM_STACK, SizedIngredientComponent.FLAT,
                BooleanComponent.BOOLEAN, BlockComponent.OPTIONAL_BLOCK, TagKeyComponent.BLOCK,
                TagKeyComponent.ITEM, TagKeyComponent.ENTITY_TYPE, ResourceKeyComponent.DIMENSION,
                IngredientComponent.INGREDIENT, StringComponent.STRING, FluidStackComponent.FLUID_STACK,
                SizedFluidIngredientComponent.FLAT)) {
            assertSame(type.instance(), type.instance());
            assertSame(type, type.instance().type());
        }
        assertEquals(NumberComponent.IntRange.class, NumberComponent.class.getField("INT").getType());
        assertEquals(NumberComponent.FloatRange.class, NumberComponent.class.getField("FLOAT").getType());
        assertEquals(Integer.class, NumberComponent.INT.otherKey("energy").typeInfo.asClass());
        assertEquals(Float.class, NumberComponent.FLOAT.otherKey("experience").typeInfo.asClass());
        assertEquals(RecipeComponentType.class, ItemStackComponent.class.getField("ITEM_STACK").getType());
        assertSame(ItemStack.STRICT_CODEC, ItemStackComponent.ITEM_STACK.instance().codec());
        assertSame(SizedIngredient.FLAT_CODEC, SizedIngredientComponent.FLAT.instance().codec());
        assertEquals(SizedIngredient.class, dev.latvian.mods.kubejs.plugin.builtin.wrapper.SizedIngredientWrapper.TYPE_INFO.asClass());
        assertEquals(Ingredient.class, dev.latvian.mods.kubejs.plugin.builtin.wrapper.IngredientWrapper.TYPE_INFO.asClass());
        Ingredient plainIngredient = Ingredient.of(Items.IRON_INGOT);
        assertSame(plainIngredient, dev.latvian.mods.kubejs.plugin.builtin.wrapper.IngredientWrapper.wrap(null, plainIngredient));
        assertTrue(dev.latvian.mods.kubejs.plugin.builtin.wrapper.IngredientWrapper.wrap(null, "minecraft:iron_ingot").test(new ItemStack(Items.IRON_INGOT)));
        SizedIngredient nativeIngredient = new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 3);
        assertSame(nativeIngredient, dev.latvian.mods.kubejs.plugin.builtin.wrapper.SizedIngredientWrapper.wrap(null, nativeIngredient));
        assertEquals(2, dev.latvian.mods.kubejs.plugin.builtin.wrapper.SizedIngredientWrapper.wrap(null, new ItemStack(Items.IRON_INGOT, 2)).count());
    }

    @Test void itemAndIngredientWrappingReuseNativeConvertersAndKeepNativeCodecs() {
        var output = ItemStackComponent.ITEM_STACK.instance();
        ItemStack stack = output.wrap(null, "2x minecraft:iron_ingot");
        assertEquals(2, stack.getCount());
        assertEquals(json("{\"id\":\"minecraft:iron_ingot\",\"count\":2}"), encode(output, stack));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Native component patch"));
        ItemStack copied = output.wrap(null, stack);
        assertNotSame(stack, copied);
        assertEquals(stack.get(DataComponents.CUSTOM_NAME), copied.get(DataComponents.CUSTOM_NAME));

        var input = IngredientComponent.INGREDIENT.instance();
        assertTrue(input.wrap(null, "minecraft:iron_ingot").test(stack));
        var sized = SizedIngredientComponent.FLAT.instance();
        assertTrue(assertThrows(IllegalStateException.class, () -> sized.wrap(null, Map.of("item", "minecraft:iron_ingot", "count", 3))).getMessage().contains("NEKO-5367"));
        try (Context context = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).build()) {
            context.enter();
            try {
                SizedIngredient ingredient = sized.wrap(null, Map.of("item", "minecraft:iron_ingot", "count", 3));
                assertEquals(3, ingredient.count());
                assertEquals(json("{\"item\":\"minecraft:iron_ingot\",\"count\":3}"), encode(sized, ingredient));
                assertTrue(output.codec().parse(registries.json(), json("{}")).error().isPresent());
                assertThrows(RuntimeException.class, () -> output.wrap(null, "missing:unknown_item"));
                assertThrows(RuntimeException.class, () -> sized.wrap(null, Map.of("item", "minecraft:iron_ingot", "count", 0)));
                assertEquals(4, sized.wrap(null, context.eval("js", "({item: 'minecraft:iron_ingot', count: 4})")).count());
            } finally {
                context.leave();
            }
        }
    }

    @Test void fluidWrappingUsesNativeResolverAndFlatSizedCodec() {
        var output = FluidStackComponent.FLUID_STACK.instance();
        try (Context context = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).build()) {
            context.enter();
            try {
                var stack = output.wrap(null, Map.of("fluid", "minecraft:water", "amount", 250));
                assertEquals(250, stack.getAmount());
                assertEquals(json("{\"id\":\"minecraft:water\",\"amount\":250}"), encode(output, stack));
                var input = SizedFluidIngredientComponent.FLAT.instance();
                var ingredient = input.wrap(null, Map.of("fluid", "minecraft:water", "amount", 500));
                assertEquals(500, ingredient.amount());
                assertEquals(json("{\"fluid\":\"minecraft:water\",\"amount\":500}"), encode(input, ingredient));
                assertThrows(RuntimeException.class, () -> output.wrap(null, "missing:unknown_fluid"));
                assertEquals(750, output.wrap(null, context.eval("js", "({fluid: 'minecraft:water', amount: 750})")).getAmount());
            } finally {
                context.leave();
            }
        }
    }

    @Test void registryComponentsPreserveIdentifierShapesAndRejectWrongRegistries() {
        var block = BlockComponent.OPTIONAL_BLOCK.instance();
        assertSame(Blocks.STONE, block.wrap(null, new JsonPrimitive("minecraft:stone")));
        assertTrue(block.allowEmpty());
        assertTrue(block.isEmpty(Blocks.AIR));
        assertEquals(new JsonPrimitive("minecraft:air"), encode(block, Blocks.AIR));
        var tags = TagKeyComponent.BLOCK.instance();
        var tag = tags.wrap(null, "#minecraft:logs");
        assertEquals(Registries.BLOCK, tag.registry());
        assertEquals(new JsonPrimitive("minecraft:logs"), encode(tags, tag));
        assertSame(tag, tags.wrap(null, tag));
        assertThrows(IllegalArgumentException.class, () -> tags.wrap(null, TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("logs"))));
        var dimension = ResourceKeyComponent.DIMENSION.instance();
        assertSame(Level.OVERWORLD, dimension.wrap(null, "minecraft:overworld"));
        assertEquals(new JsonPrimitive("minecraft:overworld"), encode(dimension, Level.OVERWORLD));
        assertThrows(IllegalArgumentException.class, () -> dimension.wrap(null, Items.STONE.builtInRegistryHolder().key()));
    }

    @Test void scalarCodecsAndWrappingKeepUpstreamNumericContracts() {
        assertEquals(4, NumberComponent.intRange(0, 4).wrap(null, 9));
        assertTrue(NumberComponent.intRange(0, 4).codec().parse(registries.json(), new JsonPrimitive(9)).error().isPresent());
        assertEquals(2.5F, NumberComponent.FLOAT.wrap(null, "2.5"));
        assertFalse(NumberComponent.INT.isEmpty(0));
        assertThrows(RuntimeException.class, () -> NumberComponent.INT.wrap(null, "not a number"));
        assertFalse(BooleanComponent.BOOLEAN.instance().wrap(null, 0));
        assertTrue(BooleanComponent.BOOLEAN.instance().wrap(null, true));
        assertFalse(BooleanComponent.BOOLEAN.instance().isEmpty(false));
        assertTrue(StringComponent.STRING.instance().codec().parse(registries.json(), new JsonPrimitive("")).error().isPresent());
        assertEquals("label", StringComponent.STRING.instance().wrap(null, new JsonPrimitive("label")));
    }

    @Test void boundedListsWrapJavaAndBorrowedGuestArraysAndEncodeRealLists() {
        var list = NumberComponent.INT.asList().withBounds(IntBounds.of(2, 2));
        assertEquals(List.of(1, 2), list.wrap(null, new int[]{1, 2}));
        assertEquals(json("[1,2]"), encode(list, List.of(1, 2)));
        assertThrows(IllegalArgumentException.class, () -> list.wrap(null, List.of(1)));
        assertThrows(IllegalArgumentException.class, () -> list.wrap(null, List.of(1, 2, 3)));
        assertTrue(list.codec().parse(registries.json(), json("[1]")).error().isPresent());
        try (Context context = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).build()) {
            assertEquals(List.of(1, 2), list.wrap(null, context.eval("js", "[1,2]")));
            assertEquals(3, NumberComponent.INT.wrap(null, context.eval("js", "3")));
        }
        var singleton = StringComponent.STRING.instance().asListOrSelf();
        assertEquals(new JsonPrimitive("one"), encode(singleton, List.of("one")));
        assertEquals(List.of("one"), singleton.wrap(null, "one"));
        assertEquals(List.of(), NumberComponent.INT.asList().withBounds(IntBounds.OPTIONAL).wrap(null, List.of()));
    }
}
