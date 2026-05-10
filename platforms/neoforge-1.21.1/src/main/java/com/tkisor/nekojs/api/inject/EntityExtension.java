package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import com.tkisor.nekojs.wrapper.pdata.PersistentDataJS;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * @see Entity
 * @author ZZZank
 */
@RemapByPrefix("neko$")
public interface EntityExtension {
    String NEKO_PDATA_KEY = "NekoJSPersistentData";

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

    default PersistentDataJS neko$pdata() {
        if (self().level().isClientSide()) {
            return PersistentDataJS.readOnly(() -> PDataSyncService.clientMirror(self()));
        }
        return new PersistentDataJS(
                this::neko$getPDataTag,
                this::neko$setPDataTag,
                () -> PDataSyncService.markDirty(self()),
                () -> PDataSyncService.syncNow(self())
        );
    }

    private CompoundTag neko$getPDataTag() {
        return self().getPersistentData().getCompound(NEKO_PDATA_KEY).copy();
    }

    private void neko$setPDataTag(CompoundTag tag) {
        CompoundTag persistentData = self().getPersistentData();
        if (tag.isEmpty()) {
            persistentData.remove(NEKO_PDATA_KEY);
        } else {
            persistentData.put(NEKO_PDATA_KEY, tag.copy());
        }
    }
}