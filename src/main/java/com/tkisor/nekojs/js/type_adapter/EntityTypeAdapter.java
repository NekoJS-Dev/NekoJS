package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

/**
 * EntityType adapter: resolves an entity type purely by id and delegates to {@link SimpleRegistryBasedAdapter}.
 *
 * <p>It used to be a handwritten {@link AbstractJSTypeAdapter} subclass (which could return null or throw
 * NoSuchElementException); it now uses the shared base class with equivalent behavior (adds the {@code minecraft:}
 * prefix, then tryParse + getOptional, with NekoId/Identifier as the host object).
 */
public class EntityTypeAdapter extends SimpleRegistryBasedAdapter<EntityType<?>> {

    @SuppressWarnings("unchecked")
    public EntityTypeAdapter() {
        super(BuiltInRegistries.ENTITY_TYPE, (Class<EntityType<?>>) (Class<?>) EntityType.class, "EntityType");
    }
}
