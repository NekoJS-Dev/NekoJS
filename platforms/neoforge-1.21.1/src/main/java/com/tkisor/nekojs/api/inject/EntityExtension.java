package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * @see Entity
 * @author ZZZank
 */
@RemapByPrefix("neko$")
public interface EntityExtension {

    private Entity self() {
        return (Entity) this;
    }

    default boolean neko$hasTag(String tag) {
        // 1.21.1: 实体标签的获取方法在 Mojmap 中为 getTags()
        return self().getTags().contains(tag);
    }

    default boolean neko$kill() {
        if (self().level() instanceof ServerLevel serverLevel) {
            // 1.21.1 中 kill() 必须传入 ServerLevel，你的写法完全正确
            self().kill();
            return true;
        }
        return false;
    }

    default String neko$getId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(self().getType()).toString();
    }
}