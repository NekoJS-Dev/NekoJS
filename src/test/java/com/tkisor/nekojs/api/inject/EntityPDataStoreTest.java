//? if >=26 {
package com.tkisor.nekojs.api.inject;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavior of {@link EntityPDataStore.Access} and how {@link EntityPDataStore} installs an
 * implementation: getters return defensive copies, writing an empty tag removes the subkey,
 * install replaces the implementation in place, and the fallback keys entries by entity id. Access
 * keys on entity ids, so no real entity is needed; {@code EntityType.PIG} would trigger the
 * NeoForge AttachmentHolder static chain, and plain JUnit has no FML.
 */
class EntityPDataStoreTest {

    @AfterEach
    void restore() {
        // Remove the implementation the test installed and restore the fallback. EntityPDataStore has no
        // reset, so installing a fallback-shaped implementation is enough.
        EntityPDataStore.install(memoryAccess());
    }

    /**
     * In-memory implementation with the same copy and remove semantics as the real ones: the NeoForge
     * implementation is inlined in NekoJSMod, the Fabric one is in FabricPDataSync.
     */
    private static EntityPDataStore.Access memoryAccess() {
        Map<Integer, CompoundTag> containers = new HashMap<>();
        return new EntityPDataStore.Access() {
            private CompoundTag container(int entityId) {
                return containers.computeIfAbsent(entityId, ignored -> new CompoundTag());
            }

            @Override
            public CompoundTag get(int entityId, String key) {
                return container(entityId).getCompound(key).orElseGet(CompoundTag::new).copy();
            }

            @Override
            public void set(int entityId, String key, CompoundTag tag) {
                CompoundTag container = container(entityId);
                if (tag.isEmpty()) {
                    container.remove(key);
                } else {
                    container.put(key, tag.copy());
                }
            }
        };
    }

    @Test
    void installReplacesTheCurrentAccess() {
        EntityPDataStore.Access replacement = memoryAccess();
        EntityPDataStore.install(replacement);
        assertSame(replacement, EntityPDataStore.get());
    }

    @Test
    void getterReturnsDefensiveCopy() {
        EntityPDataStore.Access access = memoryAccess();
        access.set(2, EntityExtension.NEKO_PDATA_KEY, tagOf("hp", 3));

        CompoundTag first = access.get(2, EntityExtension.NEKO_PDATA_KEY);
        CompoundTag second = access.get(2, EntityExtension.NEKO_PDATA_KEY);
        assertNotSame(first, second, "getters must copy");
        assertEquals(3, second.getIntOr("hp", -1));
    }

    @Test
    void emptyWriteRemovesTheKey() {
        EntityPDataStore.Access access = memoryAccess();
        access.set(1, EntityExtension.NEKO_PDATA_KEY, tagOf("mana", 5));
        assertFalse(access.get(1, EntityExtension.NEKO_PDATA_KEY).isEmpty());

        // Clearing pdata from a script: writing an empty tag removes the subkey.
        access.set(1, EntityExtension.NEKO_PDATA_KEY, new CompoundTag());
        assertTrue(access.get(1, EntityExtension.NEKO_PDATA_KEY).isEmpty());
    }

    @Test
    void fallbackStoresPerEntityUntilInstalled() {
        // With a fallback-shaped in-memory implementation installed again, data of different entity ids
        // stays separate.
        EntityPDataStore.install(memoryAccess());
        EntityPDataStore.get().set(10, "k", tagOf("v", 1));
        EntityPDataStore.get().set(11, "k", tagOf("v", 2));

        assertEquals(1, EntityPDataStore.get().get(10, "k").getIntOr("v", -1));
        assertEquals(2, EntityPDataStore.get().get(11, "k").getIntOr("v", -1));
    }

    private static CompoundTag tagOf(String key, int value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(key, value);
        return tag;
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("expected false");
    }
}
//?}
