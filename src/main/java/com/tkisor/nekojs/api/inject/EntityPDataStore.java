package com.tkisor.nekojs.api.inject;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridge that reads and writes entity persistent data (the {@code NekoJSPersistentData} subkey) in
 * each platform's persistent container.
 *
 * <p>There are two access paths. The <b>entity reference path</b>
 * ({@code get/set(Entity, ...)}) has the entity at the call site, so the platform implementation
 * dereferences the container directly: inside the {@code EntityJoinLevelEvent} window the entity
 * is not yet in the level entity index, and looking it up by id would silently drop the write.
 * The <b>id path</b> ({@code get/set(int, ...)}) is used by the sync/mirror layer to keep books
 * and by tests that do not need a real entity. The platform calls {@link #install} at startup,
 * and {@code EntityExtension#neko$pdata()} only depends on this bridge.
 */
public final class EntityPDataStore {

    /**
     * Access semantics: read the NekoJS subkey from the entity's persistent container (a copy of an
     * empty tag when it is absent), and write it back (an empty tag removes the subkey).
     *
     * <p>A platform implementation should override the entity reference path. The call site already
     * holds the entity, so it can dereference the container directly instead of relying on the
     * "id → level.getEntity(id)" lookup, which always comes back empty inside the joinLevel window
     * and makes the write a silent no-op. The default id-path implementation still keeps books by
     * id for the sync/mirror layer and tests.
     */
    public interface Access {
        CompoundTag get(int entityId, String key);

        void set(int entityId, String key, CompoundTag tag);

        /** Entity reference path (delegates to the id path by default); a platform override fixes the
         * write semantics inside the joinLevel window. */
        default CompoundTag get(Entity entity, String key) {
            return get(entity.getId(), key);
        }

        /** Entity reference path (delegates to the id path by default); a platform override fixes the
         * write semantics inside the joinLevel window. */
        default void set(Entity entity, String key, CompoundTag tag) {
            set(entity.getId(), key, tag);
        }
    }

    private static volatile Access current = fallback();

    private EntityPDataStore() {}

    /** Fallback before assembly: in-memory storage keyed by entity id (test environments and the early
     * dedicated server). It is not persisted with the save. */
    private static Access fallback() {
        Map<Integer, CompoundTag> memory = new ConcurrentHashMap<>();
        return new Access() {
            @Override
            public CompoundTag get(int entityId, String key) {
                CompoundTag container = memory.get(entityId);
                if (container == null) return new CompoundTag();
//? if >=26 {
                return container.getCompound(key).orElseGet(CompoundTag::new).copy();
//?} else {
                /*return container.getCompound(key).copy();
*///?}
            }

            @Override
            public void set(int entityId, String key, CompoundTag tag) {
                if (tag.isEmpty()) {
                    CompoundTag container = memory.get(entityId);
                    if (container != null) {
                        container.remove(key);
                        if (container.isEmpty()) {
                            memory.remove(entityId);
                        }
                    }
                } else {
                    memory.computeIfAbsent(entityId, ignored -> new CompoundTag()).put(key, tag.copy());
                }
            }
        };
    }

    public static void install(Access access) {
        if (access != null) {
            current = access;
        }
    }

    public static Access get() {
        return current;
    }

    /** Script-facing entry: goes through the entity reference path, which does not need an id lookup
     * inside the joinLevel window. */
    public static CompoundTag getPDataTag(Entity entity, String key) {
        return current.get(entity, key);
    }

    /** Script-facing entry: goes through the entity reference path, which does not need an id lookup
     * inside the joinLevel window. */
    public static void setPDataTag(Entity entity, String key, CompoundTag tag) {
        current.set(entity, key, tag);
    }

}
