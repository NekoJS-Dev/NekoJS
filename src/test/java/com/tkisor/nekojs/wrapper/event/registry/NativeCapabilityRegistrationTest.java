//? if neoforge {
package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.NekoCapabilityTestEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeCapabilityRegistrationTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    private static Identifier capabilityId() {
        return Identifier.fromNamespaceAndPath("nekojs_native_test", "capability_" + IDS.incrementAndGet());
    }

    @Test
    void nativeItemRegistrationQueriesTheExactStackRatherThanSharedFreshStorage() {
        assumeTrue(VanillaRegistryProbe.available(), "native registry bootstrap requires a loader runtime");
        ItemCapability<ItemStack, Void> capability = ItemCapability.createVoid(capabilityId(), ItemStack.class);
        var nativeEvent = NekoCapabilityTestEvent.create();
        CapabilityRegistryEventJS event = new CapabilityRegistryEventJS();
        AtomicInteger queries = new AtomicInteger();
        event.registerItemNative("minecraft:stick", capability, (stack, context) -> {
            queries.incrementAndGet();
            return stack;
        });
        assertEquals(0, queries.get());
        assertTrue(!nativeEvent.isItemRegistered(capability, Items.STICK));
        event.apply(nativeEvent);
        assertTrue(nativeEvent.isItemRegistered(capability, Items.STICK));
        ItemStack first = new ItemStack(Items.STICK);
        ItemStack second = new ItemStack(Items.STICK);
        assertSame(first, capability.getCapability(first, null));
        assertSame(first, capability.getCapability(first, null));
        assertSame(second, capability.getCapability(second, null));
        assertNull(capability.getCapability(new ItemStack(Items.STONE), null));
        assertEquals(3, queries.get());
        assertThrows(IllegalStateException.class, () -> event.apply(nativeEvent));
    }

    @Test
    void nativeBlockRegistrationPassesDirectionAndDeclinesSides() {
        assumeTrue(VanillaRegistryProbe.available(), "native registry bootstrap requires a loader runtime");
        BlockCapability<String, Direction> capability = BlockCapability.createSided(capabilityId(), String.class);
        var nativeEvent = NekoCapabilityTestEvent.create();
        CapabilityRegistryEventJS event = new CapabilityRegistryEventJS();
        event.registerBlockEntityNative("minecraft:chest", capability,
                (blockEntity, side) -> side == null ? "unsided" : side == Direction.NORTH ? "north" : null);
        event.apply(nativeEvent);
        assertTrue(nativeEvent.isBlockRegistered(capability, Blocks.CHEST));
        ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        assertEquals("north", capability.getCapability(null, BlockPos.ZERO, chest.getBlockState(), chest, Direction.NORTH));
        assertEquals("unsided", capability.getCapability(null, BlockPos.ZERO, chest.getBlockState(), chest, null));
        assertNull(capability.getCapability(null, BlockPos.ZERO, chest.getBlockState(), chest, Direction.SOUTH));
    }

    @Test
    void plainBlockNativeProviderDoesNotRequireABlockEntity() {
        assumeTrue(VanillaRegistryProbe.available(), "native registry bootstrap requires a loader runtime");
        BlockCapability<String, Direction> capability = BlockCapability.createSided(capabilityId(), String.class);
        var nativeEvent = NekoCapabilityTestEvent.create();
        CapabilityRegistryEventJS event = new CapabilityRegistryEventJS();
        event.registerBlockNative("minecraft:stone", capability,
                (level, pos, state, blockEntity, side) -> side == null ? "stone" : null);
        event.apply(nativeEvent);
        assertTrue(nativeEvent.isBlockRegistered(capability, Blocks.STONE));
        assertEquals("stone", capability.getCapability(null, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null, null));
        assertNull(capability.getCapability(null, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null, Direction.NORTH));
    }

    @Test
    void nativeEntityRegistrationInstallsOnlyForTheSelectedType() {
        assumeTrue(VanillaRegistryProbe.available(), "native registry bootstrap requires a loader runtime");
        EntityCapability<Entity, Void> capability = EntityCapability.createVoid(capabilityId(), Entity.class);
        var nativeEvent = NekoCapabilityTestEvent.create();
        CapabilityRegistryEventJS event = new CapabilityRegistryEventJS();
        event.registerEntityNative("minecraft:player", capability, (entity, context) -> entity);
        event.apply(nativeEvent);
        assertTrue(nativeEvent.isEntityRegistered(capability,
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse("minecraft:player")).orElseThrow()));
        assertTrue(!nativeEvent.isEntityRegistered(capability,
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse("minecraft:pig")).orElseThrow()));
    }
}
//?}
