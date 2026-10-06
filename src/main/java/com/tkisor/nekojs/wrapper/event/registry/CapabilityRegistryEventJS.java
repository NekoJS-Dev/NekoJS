// 26.x implementation; keep this file paired with the 1.21.1 version.
//? if neoforge {
package com.tkisor.nekojs.wrapper.event.registry;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BaseCapability;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Collects standard and mod-defined native capability providers for startup registration.
 * Providers run on native queries, not during collection, and must return owner-backed
 * handlers rather than constructing empty storage for each query. Null declines a query.
 */
public class CapabilityRegistryEventJS {
    private final CapabilityRegistrationPlan plan;

    public CapabilityRegistryEventJS() {
        this(new CapabilityRegistrationPlan());
    }

    CapabilityRegistryEventJS(CapabilityRegistrationPlan plan) {
        this.plan = plan;
    }

    /** Preserves the legacy no-context callback, invoked for each native query. */
    public void registerBlockEntity(String typeId, String capability, Supplier<?> provider) {
        requireProvider(provider);
        registerBlockEntityContext(typeId, capability, (blockEntity, direction) -> provider.get());
    }

    /** Registers a standard block-entity provider with its nullable Direction context. */
    public void registerBlockEntityContext(String typeId, String capability,
                                           BiFunction<BlockEntity, Direction, ?> provider) {
        requireProvider(provider);
        plan.collect(CapabilityRegistrationPlan.Scope.BLOCK_ENTITY, typeId, standardBlock(capability),
                (owner, context) -> provider.apply((BlockEntity) owner, (Direction) context));
    }

    /**
     * Registers a standard entity provider. Item inventory queries have null context;
     * item_automation, energy and fluid queries have nullable Direction context.
     */
    public void registerEntity(String typeId, String capability, BiFunction<Entity, Object, ?> provider) {
        requireProvider(provider);
        plan.collect(CapabilityRegistrationPlan.Scope.ENTITY, typeId, standardEntity(capability),
                (owner, context) -> provider.apply((Entity) owner, context));
    }

    /** Registers a standard item provider with the native ItemAccess context on 26.x. */
    public void registerItem(String itemId, String capability, BiFunction<ItemStack, Object, ?> provider) {
        requireProvider(provider);
        plan.collect(CapabilityRegistrationPlan.Scope.ITEM, itemId, standardItem(capability),
                (owner, context) -> provider.apply((ItemStack) owner, context));
    }

    /** Registers a standard plain-block provider with (level, pos, state, blockEntity, direction). */
    public void registerBlock(String blockId, String capability, IBlockCapabilityProvider<?, Object> provider) {
        requireProvider(provider);
        collectBlock(blockId, standardBlock(capability), provider);
    }

    /** Attaches a mod-defined capability to a plain block with its native five-argument callback. */
    public <T, C> void registerBlockNative(String blockId, BlockCapability<T, C> capability,
                                          IBlockCapabilityProvider<T, C> provider) {
        requireProvider(provider);
        plan.collect(CapabilityRegistrationPlan.Scope.BLOCK, blockId, capability, (owner, context) -> {
            CapabilityRegistrationPlan.BlockQuery query = (CapabilityRegistrationPlan.BlockQuery) owner;
            return provider.getCapability(query.level(), query.pos(), query.state(), query.blockEntity(),
                    castContext(capability, context));
        });
    }

    private void collectBlock(String blockId, BlockCapability<?, ?> capability,
                              IBlockCapabilityProvider<?, Object> provider) {
        plan.collect(CapabilityRegistrationPlan.Scope.BLOCK, blockId, capability, (owner, context) -> {
            CapabilityRegistrationPlan.BlockQuery query = (CapabilityRegistrationPlan.BlockQuery) owner;
            return provider.getCapability(query.level(), query.pos(), query.state(), query.blockEntity(), context);
        });
    }

    /** Attaches a mod-defined block capability without depending on that mod's classes. */
    public <T, C> void registerBlockEntityNative(String typeId, BlockCapability<T, C> capability,
                                                BiFunction<BlockEntity, C, ? extends T> provider) {
        collectNative(CapabilityRegistrationPlan.Scope.BLOCK_ENTITY, typeId, capability, provider, BlockEntity.class);
    }

    /** Attaches a mod-defined entity capability, preserving its native context contract. */
    public <T, C> void registerEntityNative(String typeId, EntityCapability<T, C> capability,
                                           BiFunction<Entity, C, ? extends T> provider) {
        collectNative(CapabilityRegistrationPlan.Scope.ENTITY, typeId, capability, provider, Entity.class);
    }

    /** Attaches a mod-defined item capability, preserving its native context contract. */
    public <T, C> void registerItemNative(String itemId, ItemCapability<T, C> capability,
                                         BiFunction<ItemStack, C, ? extends T> provider) {
        collectNative(CapabilityRegistrationPlan.Scope.ITEM, itemId, capability, provider, ItemStack.class);
    }

    /** Commits this collection once; a failed native commit cannot be retried. */
    public void apply(RegisterCapabilitiesEvent event) {
        plan.apply(event);
    }

    private <O, T, C> void collectNative(CapabilityRegistrationPlan.Scope scope, String id,
                                        BaseCapability<T, C> capability, BiFunction<O, C, ? extends T> provider,
                                        Class<O> ownerType) {
        requireProvider(provider);
        plan.collect(scope, id, capability, (owner, context) ->
                provider.apply(ownerType.cast(owner), castContext(capability, context)));
    }

    private static <T, C> C castContext(BaseCapability<T, C> capability, Object context) {
        return context == null ? null : capability.contextClass().cast(context);
    }

    private static void requireProvider(Object provider) {
        if (provider == null) {
            throw new IllegalArgumentException("[NEKO-4020] Capability provider must not be null");
        }
    }

    private static String normalize(String capability) {
        if (capability == null || capability.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4007] Capability name must not be blank");
        }
        return capability.trim().toLowerCase(Locale.ROOT);
    }

    private static BlockCapability<?, ?> standardBlock(String capability) {
        return switch (normalize(capability)) {
            case "item" -> Capabilities.Item.BLOCK;
            case "energy" -> Capabilities.Energy.BLOCK;
            case "fluid" -> Capabilities.Fluid.BLOCK;
            default -> throw unknownStandard(capability, "block entity");
        };
    }

    private static EntityCapability<?, ?> standardEntity(String capability) {
        return switch (normalize(capability)) {
            case "item" -> Capabilities.Item.ENTITY;
            case "item_automation" -> Capabilities.Item.ENTITY_AUTOMATION;
            case "energy" -> Capabilities.Energy.ENTITY;
            case "fluid" -> Capabilities.Fluid.ENTITY;
            default -> throw unknownStandard(capability, "entity");
        };
    }

    private static ItemCapability<?, ?> standardItem(String capability) {
        return switch (normalize(capability)) {
            case "item" -> Capabilities.Item.ITEM;
            case "energy" -> Capabilities.Energy.ITEM;
            case "fluid" -> Capabilities.Fluid.ITEM;
            default -> throw unknownStandard(capability, "item");
        };
    }

    private static IllegalArgumentException unknownStandard(String capability, String scope) {
        return new IllegalArgumentException("[NEKO-4007] Unknown standard " + scope + " capability: " + capability);
    }
}
//?}
