package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.EntitySpec;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import com.tkisor.nekojs.wrapper.pdata.PersistentDataJS;
import net.minecraft.core.registries.BuiltInRegistries;
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
//? if >=26 {
        return self().entityTags().contains(tag);
//?} else {
        /*return self().getTags().contains(tag);
*///?}
    }

    @Override
    default boolean neko$kill() {
        if (self().level() instanceof ServerLevel serverLevel) {
//? if >=26 {
            self().kill(serverLevel);
//?} else {
            /*self().kill();
*///?}
            return true;
        }
        return false;
    }

    /** The entity type registry id. @Remap avoids colliding with the native zero-argument int getId(). */
    @Remap("getRegistryId")
    default String neko$getId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(self().getType()).toString();
    }

    // neko$getX/Y/Z was removed: NF's native Entity has zero-argument getX()/getY()/getZ(), so JS
    // should use the native methods.

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
                () -> EntityPDataStore.getPDataTag(self(), NEKO_PDATA_KEY),
                tag -> EntityPDataStore.setPDataTag(self(), NEKO_PDATA_KEY, tag),
                () -> PDataSyncService.markDirty(self()),
                () -> PDataSyncService.syncNow(self())
        );
    }

}
