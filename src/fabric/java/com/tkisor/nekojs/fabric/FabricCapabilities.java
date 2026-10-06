package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.fabric.capability.FabricEnergyHandler;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Locale;

/** Native Fabric capability lookups; storage operations retain Fabric transaction semantics. */
public final class FabricCapabilities {
    private FabricCapabilities() {}

    private static final class EntityLookups {
        private static final EntityApiLookup<Storage<ItemVariant>, Direction> ITEM =
                EntityApiLookup.get(id("item"), Storage.asClass(), Direction.class);
        private static final EntityApiLookup<Storage<FluidVariant>, Direction> FLUID =
                EntityApiLookup.get(id("fluid"), Storage.asClass(), Direction.class);
        private static final EntityApiLookup<FabricEnergyHandler, Direction> ENERGY =
                EntityApiLookup.get(id("energy"), FabricEnergyHandler.class, Direction.class);
    }

    private static final class EnergyLookups {
        private static final BlockApiLookup<FabricEnergyHandler, Direction> BLOCK =
                BlockApiLookup.get(id("energy"), FabricEnergyHandler.class, Direction.class);
        private static final ItemApiLookup<FabricEnergyHandler, ContainerItemContext> ITEM =
                ItemApiLookup.get(id("energy"), FabricEnergyHandler.class, ContainerItemContext.class);
    }

    public static BlockApiLookup<?, Direction> blockLookup(String capability) {
        return switch (normalize(capability)) {
            case "item" -> ItemStorage.SIDED;
            case "fluid" -> FluidStorage.SIDED;
            case "energy" -> EnergyLookups.BLOCK;
            default -> throw new AssertionError();
        };
    }

    public static EntityApiLookup<?, Direction> entityLookup(String capability) {
        return switch (normalize(capability)) {
            case "item" -> EntityLookups.ITEM;
            case "fluid" -> EntityLookups.FLUID;
            case "energy" -> EntityLookups.ENERGY;
            default -> throw new AssertionError();
        };
    }

    public static ItemApiLookup<?, ContainerItemContext> itemLookup(String capability) {
        return switch (normalize(capability)) {
            case "item" -> ItemStorage.ITEM;
            case "fluid" -> FluidStorage.ITEM;
            case "energy" -> EnergyLookups.ITEM;
            default -> throw new AssertionError();
        };
    }

    public static Object getBlock(Level level, BlockPos pos, String capability, Direction side) {
        return findBlockLookup(blockLookup(capability), level, pos, side);
    }

    public static Object getEntity(Entity entity, String capability, Direction side) {
        return findEntityLookup(entityLookup(capability), entity, side);
    }

    public static Object getItem(ItemStack stack, String capability, ContainerItemContext context) {
        return findItemLookup(itemLookup(capability), stack, context);
    }

    public static <A, C> A findBlockLookup(BlockApiLookup<A, C> lookup, Level level, BlockPos pos, C context) {
        require(lookup, "block lookup");
        require(level, "level");
        require(pos, "position");
        validateContext(lookup.contextClass(), context);
        return lookup.find(level, pos, context);
    }

    public static <A, C> A findEntityLookup(EntityApiLookup<A, C> lookup, Entity entity, C context) {
        require(lookup, "entity lookup");
        require(entity, "entity");
        validateContext(lookup.contextClass(), context);
        return lookup.find(entity, context);
    }

    public static <A, C> A findItemLookup(ItemApiLookup<A, C> lookup, ItemStack stack, C context) {
        require(lookup, "item lookup");
        require(stack, "item stack");
        validateContext(lookup.contextClass(), context);
        return lookup.find(stack, context);
    }

    private static String normalize(String capability) {
        if (capability == null || capability.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4007] Capability name must not be blank");
        }
        String normalized = capability.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("item") && !normalized.equals("fluid") && !normalized.equals("energy")) {
            throw new IllegalArgumentException("[NEKO-4007] Unknown capability: " + capability);
        }
        return normalized;
    }

    private static Identifier id(String capability) {
        return Identifier.fromNamespaceAndPath("nekojs", capability);
    }

    private static void validateContext(Class<?> expected, Object context) {
        if (context != null && !expected.isInstance(context)) {
            throw new IllegalArgumentException("[NEKO-4020] Lookup context must be " + expected.getName()
                    + "; received " + context.getClass().getName());
        }
    }

    private static void require(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("[NEKO-4020] Capability " + name + " must not be null");
        }
    }
}
