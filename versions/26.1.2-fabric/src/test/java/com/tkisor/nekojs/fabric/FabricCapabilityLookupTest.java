package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.fabric.capability.FabricEnergyHandler;
import com.tkisor.nekojs.wrapper.event.registry.CapabilityRegistryEventJS;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real native Fabric lookup registration and provider execution without a running level. */
class FabricCapabilityLookupTest {
    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Identifier id() {
        return Identifier.fromNamespaceAndPath("nekojs_test", UUID.randomUUID().toString());
    }

    private static ItemStack stack(net.minecraft.world.item.Item item) {
        return new ItemStack(net.minecraft.core.Holder.direct(item,
                net.minecraft.core.component.DataComponentMap.EMPTY), 1);
    }

    @Test
    void standardStorageLookupsAreTheNativeFabricSingletons() {
        assertSame(ItemStorage.SIDED, FabricCapabilities.blockLookup(" ITEM "));
        assertSame(FluidStorage.SIDED, FabricCapabilities.blockLookup("fluid"));
        assertSame(ItemStorage.ITEM, FabricCapabilities.itemLookup("item"));
        assertSame(FluidStorage.ITEM, FabricCapabilities.itemLookup("fluid"));
        assertEquals(FabricEnergyHandler.class, FabricCapabilities.blockLookup("energy").apiClass());
        assertEquals(FabricEnergyHandler.class, FabricCapabilities.entityLookup("energy").apiClass());
        assertEquals(FabricEnergyHandler.class, FabricCapabilities.itemLookup("energy").apiClass());
        assertEquals(Direction.class, FabricCapabilities.entityLookup("item").contextClass());
        assertThrows(IllegalArgumentException.class, () -> FabricCapabilities.blockLookup("unknown"));
    }

    @Test
    void itemNativeLookupReceivesStackAndCustomContextAndCanRejectQueries() {
        ItemApiLookup<String, String> lookup = ItemApiLookup.get(id(), String.class, String.class);
        AtomicReference<ItemStack> seen = new AtomicReference<>();
        CapabilityRegistryEventJS event = new CapabilityRegistryEventJS();
        event.registerItemLookup("minecraft:stone", lookup, (stack, context) -> {
            seen.set(stack);
            return "read".equals(context) ? "stone-storage" : null;
        });
        assertNull(lookup.getProvider(Items.STONE), "collection must not mutate native lookups");
        event.apply();
        ItemStack stack = stack(Items.STONE);
        assertEquals("stone-storage", FabricCapabilities.findItemLookup(lookup, stack, "read"));
        assertSame(stack, seen.get());
        assertNull(FabricCapabilities.findItemLookup(lookup, stack, null));
        assertThrows(IllegalStateException.class, event::apply);
        assertThrows(IllegalStateException.class,
                () -> event.registerItemLookup("minecraft:dirt", lookup, (item, context) -> "late"));
    }

    @Test
    void blockEntityNativeLookupReceivesInstanceAndNullableSide() {
        BlockApiLookup<String, Direction> lookup = BlockApiLookup.get(id(), String.class, Direction.class);
        AtomicReference<Object> seen = new AtomicReference<>();
        CapabilityRegistryEventJS event = new CapabilityRegistryEventJS();
        event.registerBlockEntityLookup("minecraft:chest", lookup, (blockEntity, side) -> {
            seen.set(blockEntity);
            return side == Direction.NORTH || side == null ? "inventory" : null;
        });
        event.apply();
        ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        var provider = lookup.getProvider(Blocks.CHEST);
        assertNotNull(provider);
        assertEquals("inventory", provider.find(null, BlockPos.ZERO, chest.getBlockState(), chest, Direction.NORTH));
        assertSame(chest, seen.get());
        assertEquals("inventory", provider.find(null, BlockPos.ZERO, chest.getBlockState(), chest, null));
        assertNull(provider.find(null, BlockPos.ZERO, chest.getBlockState(), chest, Direction.SOUTH));
    }

    @Test
    void entityAndBlockCustomLookupsKeepArbitraryNativeContexts() {
        EntityApiLookup<String, Integer> entityLookup = EntityApiLookup.get(id(), String.class, Integer.class);
        BlockApiLookup<String, String> blockLookup = BlockApiLookup.get(id(), String.class, String.class);
        CapabilityRegistryEventJS event = new CapabilityRegistryEventJS();
        event.registerEntityLookup("minecraft:pig", entityLookup, (entity, context) -> "entity-" + context);
        event.registerBlockLookup("minecraft:stone", blockLookup,
                (level, position, state, blockEntity, context) -> "block-" + context);
        event.apply();
        assertEquals("entity-3", entityLookup.getProvider(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse("minecraft:pig")).orElseThrow()).find(null, 3));
        assertEquals("block-custom", blockLookup.getProvider(Blocks.STONE)
                .find(null, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null, "custom"));
    }

    @Test
    void duplicateBatchAndNativeProvidersAreRejectedBeforeAnyMutation() {
        ItemApiLookup<String, Void> first = ItemApiLookup.get(id(), String.class, Void.class);
        ItemApiLookup<String, Void> occupied = ItemApiLookup.get(id(), String.class, Void.class);
        CapabilityRegistryEventJS duplicate = new CapabilityRegistryEventJS();
        duplicate.registerItemLookup("minecraft:stone", first, (stack, context) -> "first");
        assertThrows(IllegalArgumentException.class,
                () -> duplicate.registerItemLookup("minecraft:stone", first, (stack, context) -> "duplicate"));
        occupied.registerForItems((stack, context) -> "native", Items.DIRT);
        CapabilityRegistryEventJS rejected = new CapabilityRegistryEventJS();
        rejected.registerItemLookup("minecraft:stone", first, (stack, context) -> "must-not-publish");
        rejected.registerItemLookup("minecraft:dirt", occupied, (stack, context) -> "must-not-overwrite");
        assertThrows(IllegalStateException.class, rejected::apply);
        assertNull(first.getProvider(Items.STONE), "preflight failure must not install earlier entries");
        assertEquals("native", occupied.find(stack(Items.DIRT), null));
        assertThrows(IllegalStateException.class, rejected::apply);
    }

    @Test
    void invalidTargetsProviderExceptionsAndWrongReturnTypesRemainFailures() {
        ItemApiLookup<String, Void> lookup = ItemApiLookup.get(id(), String.class, Void.class);
        CapabilityRegistryEventJS invalid = new CapabilityRegistryEventJS();
        invalid.registerItemLookup("minecraft:stone", lookup, (stack, context) -> "never-publish");
        assertThrows(IllegalArgumentException.class,
                () -> invalid.registerItemLookup("missing:unknown", lookup, (stack, context) -> "bad"));
        assertThrows(IllegalStateException.class, invalid::apply);
        assertNull(lookup.getProvider(Items.STONE), "invalid collection must not publish earlier entries");
        CapabilityRegistryEventJS nullProvider = new CapabilityRegistryEventJS();
        assertThrows(IllegalArgumentException.class,
                () -> nullProvider.registerItemLookup("minecraft:stone", lookup, null));
        assertThrows(IllegalStateException.class, nullProvider::apply);
        CapabilityRegistryEventJS event = new CapabilityRegistryEventJS();
        event.registerItemLookup("minecraft:stone", lookup, (stack, context) -> 42);
        event.registerItemLookup("minecraft:dirt", lookup, (stack, context) -> {
            throw new IllegalArgumentException("provider-fixture");
        });
        event.apply();
        IllegalStateException wrongType = assertThrows(IllegalStateException.class,
                () -> lookup.find(stack(Items.STONE), null));
        assertTrue(wrongType.getMessage().startsWith("[NEKO-4010]"));
        IllegalStateException providerFailure = assertThrows(IllegalStateException.class,
                () -> lookup.find(stack(Items.DIRT), null));
        assertEquals("provider-fixture", providerFailure.getCause().getMessage());
        assertTrue(providerFailure.getMessage().startsWith("[NEKO-4009]"));
    }

    @Test
    void standardRegistrationSupportsLegacySupplierAndNativeContexts() {
        var storage = new com.tkisor.nekojs.bindings.static_access.CapabilitiesJS().energyStorage(100, 10, 10);
        AtomicReference<Object> seenItemContext = new AtomicReference<>();
        CapabilityRegistryEventJS event = new CapabilityRegistryEventJS();
        event.registerBlockEntity("minecraft:furnace", "energy", () -> storage);
        event.registerEntityContext("minecraft:pig", "energy",
                (entity, side) -> side == Direction.NORTH ? storage : null);
        event.registerItemContext("minecraft:stick", "energy", (stack, context) -> {
            seenItemContext.set(context);
            return storage;
        });
        event.apply();
        var furnace = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(
                BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
        var blockProvider = FabricCapabilities.blockLookup("energy").getProvider(Blocks.FURNACE);
        assertSame(storage, blockProvider.find(null, BlockPos.ZERO, furnace.getBlockState(), furnace, null));
        var entityProvider = FabricCapabilities.entityLookup("energy").getProvider(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse("minecraft:pig")).orElseThrow());
        assertSame(storage, entityProvider.find(null, Direction.NORTH));
        assertNull(entityProvider.find(null, Direction.SOUTH));
        ItemStack stack = stack(Items.STICK);
        var context = (net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext)
                java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] {net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext.class},
                        (proxy, method, arguments) -> {
                            throw new AssertionError("Lookup must forward the context without reading its slots");
                        });
        assertSame(storage, FabricCapabilities.getItem(stack, "energy", context));
        assertSame(context, seenItemContext.get());
    }

    @Test
    void coreBindingEventAndBootstrapCommitAreActuallyWired() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle.kts"))) {
            root = root.getParent();
        }
        assertNotNull(root);
        String plugin = Files.readString(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricCorePlugin.java"));
        String entry = Files.readString(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/NekoJSFabricMod.java"));
        assertTrue(plugin.contains("registry.register(\"Capabilities\", new com.tkisor.nekojs.bindings.static_access.CapabilitiesJS())"));
        assertTrue(plugin.contains("registry.register(com.tkisor.nekojs.bindings.event.CapabilityEvents.GROUP)"));
        assertTrue(entry.indexOf("FabricRegistryAdapter.onInitialize();") < entry.indexOf("CapabilityEvents.postAndApply();"));
    }
}
