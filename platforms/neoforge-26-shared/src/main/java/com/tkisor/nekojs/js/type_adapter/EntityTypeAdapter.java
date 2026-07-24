package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

/**
 * EntityType 适配器：纯 id 查询，委托 {@link SimpleRegistryBasedAdapter}。
 *
 * <p>历史：原手写 {@link AbstractJSTypeAdapter} 子类（B3/B7 修复 return null /
 * NoSuchElementException），现迁移到通用基类，逻辑等价（补 {@code minecraft:} 前缀 +
 * tryParse + getOptional + NekoId/Identifier host）。
 */
public class EntityTypeAdapter extends SimpleRegistryBasedAdapter<EntityType<?>> {

    @SuppressWarnings("unchecked")
    public EntityTypeAdapter() {
        super(BuiltInRegistries.ENTITY_TYPE, (Class<EntityType<?>>) (Class<?>) EntityType.class, "EntityType");
    }
}