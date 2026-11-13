package com.tkisor.nekojs.js.type_adapter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * {@link BlockEntityType} adapter: pure id lookup, delegated to
 * {@link SimpleRegistryBasedAdapter}. Accepts a string id (with {@code minecraft:} added when
 * missing), {@code NekoId}, {@code Identifier}, or a host of its own type.
 */
public final class BlockEntityTypeAdapter extends SimpleRegistryBasedAdapter<BlockEntityType<?>> {
    @SuppressWarnings("unchecked")
    public BlockEntityTypeAdapter() {
        super(BuiltInRegistries.BLOCK_ENTITY_TYPE, (Class<BlockEntityType<?>>) (Class<?>) BlockEntityType.class, "BlockEntityType");
    }
}
