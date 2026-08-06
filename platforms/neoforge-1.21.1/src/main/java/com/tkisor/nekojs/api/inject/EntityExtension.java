package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.EntitySpec;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import com.tkisor.nekojs.wrapper.pdata.PersistentDataJS;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * @see Entity
 */
@RemapByPrefix("neko$")
public interface EntityExtension extends EntitySpec {
    String NEKO_PDATA_KEY = "NekoJSPersistentData";

    private Entity self() {
        return (Entity) this;
    }

    default boolean neko$hasTag(String tag) {
        return self().getTags().contains(tag);
    }

    @Override
    default boolean neko$kill() {
        if (self().level() instanceof ServerLevel serverLevel) {
            self().kill();
            return true;
        }
        return false;
    }

    /** 实体类型注册 id。@Remap 避免与原生 int getId() 零参碰撞。 */
    @Remap("getRegistryId")
    default String neko$getId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(self().getType()).toString();
    }

    // neko$getX/Y/Z 已删除——NF 原生 Entity 有零参 getX()/getY()/getZ()，JS 直接用原生

    @Override
    default void neko$teleport(double x, double y, double z) {
        self().teleportTo(x, y, z);
    }

    @Override
    default void neko$remove() {
        self().discard();
    }

    @Override
    default Object neko$getLevel() {
        return self().level();
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