package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.fabric.FabricCapabilities;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Collects typed native Fabric providers and validates the entire batch before registration.
 * Native lookup registrations are permanent. A failed commit is not retryable, and a provider
 * may return null to decline a query without hiding execution or return-type failures.
 */
public class CapabilityRegistryEventJS {
    private final List<Registration> pending = new ArrayList<>();
    private final Set<Key> keys = new HashSet<>();
    private volatile State state = State.COLLECTING;

    public void registerBlockEntity(String typeId, String capability, Supplier<?> provider) {
        collect(() -> {
            requireProvider(provider);
            registerBlockEntityContext(typeId, capability, (blockEntity, side) -> provider.get());
        });
    }

    public void registerBlockEntityContext(String typeId, String capability,
            BiFunction<BlockEntity, Direction, ?> provider) {
        collect(() -> registerBlockEntityLookup(typeId, FabricCapabilities.blockLookup(capability), provider));
    }

    public void registerEntity(String typeId, String capability, Supplier<?> provider) {
        collect(() -> {
            requireProvider(provider);
            registerEntityContext(typeId, capability, (entity, side) -> provider.get());
        });
    }

    public void registerEntityContext(String typeId, String capability,
            BiFunction<Entity, Direction, ?> provider) {
        collect(() -> registerEntityLookup(typeId, FabricCapabilities.entityLookup(capability), provider));
    }

    public void registerItem(String itemId, String capability, Supplier<?> provider) {
        collect(() -> {
            requireProvider(provider);
            registerItemContext(itemId, capability, (stack, context) -> provider.get());
        });
    }

    public void registerItemContext(String itemId, String capability,
            BiFunction<ItemStack, ContainerItemContext, ?> provider) {
        collect(() -> registerItemLookup(itemId, FabricCapabilities.itemLookup(capability), provider));
    }

    public <A, C> void registerBlockLookup(String blockId, BlockApiLookup<A, C> lookup,
            BlockApiLookup.BlockApiProvider<?, C> provider) {
        collect(() -> {
            requireLookup(lookup);
            requireProvider(provider);
            Identifier id = parse(blockId);
            Block block = BuiltInRegistries.BLOCK.getOptional(id).orElseThrow(() -> unknown("block", id));
            add(new Registration(List.of(new Key(lookup, block)),
                    () -> rejectExisting(lookup.getProvider(block), id, lookup.getId()),
                    () -> lookup.registerForBlocks((level, pos, blockState, blockEntity, context) ->
                            invoke(() -> provider.find(level, pos, blockState, blockEntity, context),
                                    lookup.apiClass(), id, lookup.getId()), block)));
        });
    }

    public <A, C> void registerBlockEntityLookup(String typeId, BlockApiLookup<A, C> lookup,
            BiFunction<BlockEntity, C, ?> provider) {
        collect(() -> {
            requireLookup(lookup);
            requireProvider(provider);
            Identifier id = parse(typeId);
            BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(id)
                    .orElseThrow(() -> unknown("block entity type", id));
            List<Block> blocks = BuiltInRegistries.BLOCK.stream()
                    .filter(block -> type.isValid(block.defaultBlockState())).toList();
            if (blocks.isEmpty()) {
                throw new IllegalArgumentException("[NEKO-4020] Block entity type has no valid blocks: " + id);
            }
            List<Key> registrationKeys = blocks.stream().map(block -> new Key(lookup, block)).toList();
            add(new Registration(registrationKeys,
                    () -> blocks.forEach(block -> rejectExisting(lookup.getProvider(block), id, lookup.getId())),
                    () -> lookup.registerForBlocks((level, pos, blockState, blockEntity, context) -> {
                        if (blockEntity == null || blockEntity.getType() != type) return null;
                        return invoke(() -> provider.apply(blockEntity, context), lookup.apiClass(), id, lookup.getId());
                    }, blocks.toArray(Block[]::new))));
        });
    }

    public <A, C> void registerEntityLookup(String typeId, EntityApiLookup<A, C> lookup,
            BiFunction<Entity, C, ?> provider) {
        collect(() -> {
            requireLookup(lookup);
            requireProvider(provider);
            Identifier id = parse(typeId);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                    .orElseThrow(() -> unknown("entity type", id));
            add(new Registration(List.of(new Key(lookup, type)),
                    () -> rejectExisting(lookup.getProvider(type), id, lookup.getId()),
                    () -> lookup.registerForTypes((entity, context) ->
                            invoke(() -> provider.apply(entity, context), lookup.apiClass(), id, lookup.getId()), type)));
        });
    }

    public <A, C> void registerItemLookup(String itemId, ItemApiLookup<A, C> lookup,
            BiFunction<ItemStack, C, ?> provider) {
        collect(() -> {
            requireLookup(lookup);
            requireProvider(provider);
            Identifier id = parse(itemId);
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> unknown("item", id));
            add(new Registration(List.of(new Key(lookup, item)),
                    () -> rejectExisting(lookup.getProvider(item), id, lookup.getId()),
                    () -> lookup.registerForItems((stack, context) ->
                            invoke(() -> provider.apply(stack, context), lookup.apiClass(), id, lookup.getId()), item)));
        });
    }

    public void apply() {
        ensureCollecting();
        state = State.APPLYING;
        try {
            pending.forEach(registration -> registration.validate.run());
            pending.forEach(registration -> registration.install.run());
            state = State.APPLIED;
        } catch (RuntimeException exception) {
            state = State.FAILED;
            throw new IllegalStateException("[NEKO-4011] Fabric capability batch could not be committed", exception);
        } catch (Error failure) {
            state = State.FAILED;
            throw failure;
        } finally {
            pending.clear();
            keys.clear();
        }
    }

    private void collect(Runnable registration) {
        ensureCollecting();
        try {
            registration.run();
        } catch (RuntimeException | Error exception) {
            state = State.FAILED;
            pending.clear();
            keys.clear();
            throw exception;
        }
    }

    private void add(Registration registration) {
        for (Key key : registration.keys) {
            if (keys.contains(key)) {
                throw new IllegalArgumentException("[NEKO-4014] Duplicate capability provider in the startup batch");
            }
        }
        keys.addAll(registration.keys);
        pending.add(registration);
    }

    private void ensureCollecting() {
        if (state != State.COLLECTING) {
            throw new IllegalStateException("[NEKO-4011] Capability batch is no longer collecting: " + state);
        }
    }

    private static void rejectExisting(Object provider, Identifier target, Identifier lookup) {
        if (provider != null) {
            throw new IllegalArgumentException("[NEKO-4014] Capability provider already registered for "
                    + target + " / " + lookup);
        }
    }

    private <A> A invoke(Supplier<?> provider, Class<A> apiClass, Identifier target, Identifier lookup) {
        if (state != State.APPLIED) {
            throw new IllegalStateException("[NEKO-4011] Capability provider batch is not committed: " + state);
        }
        Object value;
        try {
            value = provider.get();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("[NEKO-4009] Capability provider failed for "
                    + target + " / " + lookup, exception);
        }
        if (value == null) return null;
        if (!apiClass.isInstance(value)) {
            throw new IllegalStateException("[NEKO-4010] Capability provider returned "
                    + value.getClass().getName() + "; expected " + apiClass.getName()
                    + " for " + target + " / " + lookup);
        }
        return apiClass.cast(value);
    }

    private static Identifier parse(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4020] Capability target id must not be blank");
        }
        Identifier parsed = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (parsed == null) {
            throw new IllegalArgumentException("[NEKO-4020] Invalid capability target id: " + id);
        }
        return parsed;
    }

    private static IllegalArgumentException unknown(String kind, Identifier id) {
        return new IllegalArgumentException("[NEKO-4006] Unknown capability " + kind + ": " + id);
    }

    private static void requireLookup(Object lookup) {
        if (lookup == null) throw new IllegalArgumentException("[NEKO-4020] Capability lookup must not be null");
    }

    private static void requireProvider(Object provider) {
        if (provider == null) throw new IllegalArgumentException("[NEKO-4020] Capability provider must not be null");
    }

    private enum State { COLLECTING, APPLYING, APPLIED, FAILED }
    private record Key(Object lookup, Object target) {}
    private record Registration(List<Key> keys, Runnable validate, Runnable install) {}
}
