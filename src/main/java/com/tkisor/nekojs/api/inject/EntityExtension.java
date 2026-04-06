package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
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
        return self().entityTags().contains(tag);
    }

    default boolean neko$kill() {
        if (self().level() instanceof ServerLevel serverLevel) {
            self().kill(serverLevel);
            return true;
        }
        return false;
    }
}
