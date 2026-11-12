//? if neoforge {
package com.tkisor.nekojs.js.type_adapter;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.testfixture.RecipeFilterPlatformFixture;
import com.tkisor.nekojs.testfixture.NativeLoadingServicesTestFixture;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import graal.graalvm.polyglot.Context;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises existing object conversion with real native components in the isolated codec profile. */
@Tag("native-recipe-probe")
class ItemStackAdapterComponentsTest {
    private static RecipeFilterPlatformFixture platformScope;

    @BeforeAll
    static void bootstrapNativeRegistries() {
        NativeLoadingServicesTestFixture.initialize();
        boolean metadata = VanillaRegistryProbe.registryMetadataAvailable();
        String failure = "Native item component metadata bootstrap failed: " + VanillaRegistryProbe.metadataFailure();
        if (NativeLoadingServicesTestFixture.requiresIsolatedRegistryMetadata()) assertTrue(metadata, failure);
        else org.junit.jupiter.api.Assumptions.assumeTrue(metadata, failure);
        platformScope = RecipeFilterPlatformFixture.open();
    }

    @org.junit.jupiter.api.AfterAll
    static void restoreOwnedPlatform() {
        if (platformScope != null) platformScope.close();
    }

    @Test
    void objectComponentsApplyBeforeCountWithoutMutatingTheBaseStack() {
        ItemStack base = new ItemStack(Items.STONE, 3);
        base.set(DataComponents.CUSTOM_NAME, Component.literal("Original"));
        try (Context context = Context.newBuilder("js").build()) {
            context.getBindings("js").putMember("base", base);
            ItemStack converted = new ItemStackAdapter().apply(context.eval("js", """
                    ({item:base,count:5,components:{
                      'minecraft:max_stack_size':16,
                      'minecraft:custom_name':'Patched'
                    }})
                    """));
            assertNotSame(base, converted);
            assertEquals(5, converted.getCount());
            assertEquals(16, converted.getMaxStackSize());
            assertEquals("Patched", converted.get(DataComponents.CUSTOM_NAME).getString());
            assertEquals(3, base.getCount());
            assertEquals("Original", base.get(DataComponents.CUSTOM_NAME).getString());
            assertEquals(64, base.getMaxStackSize());
        }
    }

    @Test
    void objectComponentsDecodeNestedRegistryValuesThroughTheNativeCodec() {
        JsonElement patchJson = JsonParser.parseString("""
                {"minecraft:can_break":{"predicates":[{"blocks":["minecraft:stone"]}]}}
                """);
        var ops = ItemStackStringRegistries.current().createSerializationContext(JsonOps.INSTANCE);
        DataComponentPatch expected = DataComponentPatch.CODEC.parse(ops, patchJson).getOrThrow();
        try (Context context = Context.newBuilder("js").build()) {
            ItemStack converted = new ItemStackAdapter().apply(context.eval("js", """
                    ({id:'minecraft:stone',count:2,components:{
                      'minecraft:can_break':{predicates:[{blocks:['minecraft:stone']}]}
                    }})
                    """));
            assertEquals(2, converted.getCount());
            assertNotNull(converted.get(DataComponents.CAN_BREAK));
            assertEquals(DataComponentPatch.CODEC.encodeStart(ops, expected).getOrThrow(),
                    DataComponentPatch.CODEC.encodeStart(ops, converted.getComponentsPatch()).getOrThrow());
            JsonElement encodedStack = ItemStack.CODEC.encodeStart(ops, converted).getOrThrow();
            ItemStack decoded = ItemStack.CODEC.parse(ops, encodedStack).getOrThrow();
            assertTrue(ItemStack.isSameItemSameComponents(converted, decoded));
            assertEquals(2, decoded.getCount());
        }
    }

    @Test
    void explicitComponentRemovalAppliesToTheCopyOnly() {
        ItemStack base = new ItemStack(Items.STONE, 4);
        base.set(DataComponents.CUSTOM_NAME, Component.literal("Original"));
        var ops = ItemStackStringRegistries.current().createSerializationContext(JsonOps.INSTANCE);
        DataComponentPatch removal = DataComponentPatch.CODEC.parse(ops,
                JsonParser.parseString("{\"!minecraft:custom_name\":{}}")).getOrThrow();
        assertTrue(removal.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == DataComponents.CUSTOM_NAME && entry.getValue().isEmpty()));
        ItemStack expected = base.copy();
        expected.applyComponents(removal);
        try (Context context = Context.newBuilder("js").build()) {
            context.getBindings("js").putMember("base", base);
            ItemStack converted = new ItemStackAdapter().apply(context.eval("js", """
                    ({item:base,components:{'!minecraft:custom_name':{}}})
                    """));
            assertNotSame(base, converted);
            assertEquals(4, converted.getCount());
            assertFalse(converted.has(DataComponents.CUSTOM_NAME));
            assertTrue(base.has(DataComponents.CUSTOM_NAME));
            assertTrue(ItemStack.isSameItemSameComponents(expected, converted));
            // Native patch maps normalize removal of a component absent from the item prototype.
            assertEquals(DataComponentPatch.CODEC.encodeStart(ops, expected.getComponentsPatch()).getOrThrow(),
                    DataComponentPatch.CODEC.encodeStart(ops, converted.getComponentsPatch()).getOrThrow());
        }
    }

    @Test
    void nestedRegistryReferencesFollowTheNativeCodecAcceptanceContract() {
        JsonElement nativeInput = JsonParser.parseString("""
                {"minecraft:can_break":{"predicates":[{"blocks":["nekojs:no_such_block"]}]}}
                """);
        var ops = ItemStackStringRegistries.current().createSerializationContext(JsonOps.INSTANCE);
        // AdventureModePredicate permits a simpler alternative when the full predicate shape fails.
        DataComponentPatch expected = DataComponentPatch.CODEC.parse(ops, nativeInput).getOrThrow();
        try (Context context = Context.newBuilder("js").build()) {
            ItemStack converted = new ItemStackAdapter().apply(context.eval("js", """
                    ({id:'minecraft:stone',components:{
                      'minecraft:can_break':{predicates:[{blocks:['nekojs:no_such_block']}]}
                    }})
                    """));
            assertNotNull(converted.get(DataComponents.CAN_BREAK));
            assertEquals(DataComponentPatch.CODEC.encodeStart(ops, expected).getOrThrow(),
                    DataComponentPatch.CODEC.encodeStart(ops, converted.getComponentsPatch()).getOrThrow());
        }
    }

    @Test
    void invalidObjectComponentValuesFailInsteadOfBeingIgnored() {
        try (Context context = Context.newBuilder("js").build()) {
            for (String components : List.of(
                    "{'minecraft:max_stack_size':'not-an-integer'}",
                    "{'nekojs:no_such_component':1}",
                    "{'minecraft:container':[{slot:'not-an-integer',item:{id:'minecraft:stone'}}]}")) {
                var ops = ItemStackStringRegistries.current().createSerializationContext(JsonOps.INSTANCE);
                JsonElement nativeInput = JsonParser.parseString(components.replace('\'', '"'));
                assertTrue(DataComponentPatch.CODEC.parse(ops, nativeInput).error().isPresent(),
                        "The native codec must independently reject this negative fixture: " + components);
                ValueConversionException failure = assertThrows(ValueConversionException.class,
                        () -> new ItemStackAdapter().apply(context.eval("js",
                                "({id:'minecraft:stone',components:" + components + "})")), components);
                assertNotNull(failure.getCause(), "Native codec failure must be retained as the conversion cause");
                assertTrue(failure.getMessage().contains("NEKO-4054"), failure.getMessage());
            }
        }
    }
}
//?}
