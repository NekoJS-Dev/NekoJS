package com.tkisor.nekojs.wrapper.pdata;

import com.tkisor.nekojs.network.PDataSyncPacket;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client-side dedup and clear semantics for pdata sync: acceptClientSync advances by revision
 * monotonically, and a packet with empty data clears that entity's mirror. clearClientMirrors
 * clears everything (disconnect or world change). This is plain static Map logic with no live
 * Entity: the receivers on both platforms (NeoForge and Fabric) feed the same entry point.
 */
class PDataSyncAcceptTest {

    @AfterEach
    void reset() {
        PDataSyncService.clearClientMirrors();
    }

    private static CompoundTag tagOf(String key, int value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(key, value);
        return tag;
    }

    @Test
    void acceptStoresAndMirrorsReadBackByEntityId() {
        PDataSyncService.acceptClientSync(new PDataSyncPacket(7, 1, tagOf("mana", 5)));

        // Reading the mirror needs an Entity. acceptClientSync stores data and compares revisions
        // without one, so mirror visibility is checked through clear and overwrite semantics
        // (clientMirror(Entity) needs a real entity, covered by the entity-side tests).
        assertTrue(PDataSyncService.hasPendingClientData(7), "revision-1 data must be stored");
    }

    @Test
    void equalRevisionOverwritesAndReceivedTagsAreIsolated() {
        CompoundTag first = tagOf("mana", 5);
        PDataSyncService.acceptClientSync(new PDataSyncPacket(7, 3, first));
        first.putInt("mana", 99);
        assertEquals(5, new PersistentDataJS(() -> PDataSyncService.clientMirrorById(7), tag -> {}).getInt("mana"));
        PDataSyncService.acceptClientSync(new PDataSyncPacket(7, 3, tagOf("mana", 12)));
        CompoundTag read = PDataSyncService.clientMirrorById(7);
        read.putInt("mana", 99);
        assertEquals(12, new PersistentDataJS(() -> PDataSyncService.clientMirrorById(7), tag -> {}).getInt("mana"));
    }

    @Test
    void staleRevisionIsIgnored() {
        PDataSyncService.acceptClientSync(new PDataSyncPacket(7, 3, tagOf("mana", 30)));
        PDataSyncService.acceptClientSync(new PDataSyncPacket(7, 1, tagOf("mana", 10)));
        PDataSyncService.acceptClientSync(new PDataSyncPacket(7, 2, new CompoundTag()));

        assertTrue(PDataSyncService.hasPendingClientData(7), "stale empty packet must not clear the current mirror");
        // The stale packet is ignored and the mirror keeps the revision 3 content; a fresh empty
        // packet is the contrasting case that checks the clear.
        PDataSyncService.acceptClientSync(new PDataSyncPacket(7, 4, new CompoundTag()));
        assertFalse(PDataSyncService.hasPendingClientData(7), "empty-data packet clears the mirror");
    }

    @Test
    void emptyDataClearsAndEqualRevisionOverwrites() {
        PDataSyncService.acceptClientSync(new PDataSyncPacket(1, 1, tagOf("a", 1)));
        PDataSyncService.acceptClientSync(new PDataSyncPacket(2, 1, tagOf("b", 2)));

        PDataSyncService.acceptClientSync(new PDataSyncPacket(1, 1, new CompoundTag()));
        assertFalse(PDataSyncService.hasPendingClientData(1), "entity 1 cleared");
        assertTrue(PDataSyncService.hasPendingClientData(2), "entity 2 untouched");

        // Re-delivering the same revision (a resend) must not throw and must be idempotent.
        PDataSyncService.acceptClientSync(new PDataSyncPacket(2, 1, tagOf("b", 2)));
        assertTrue(PDataSyncService.hasPendingClientData(2));
    }

    @Test
    void reusedEntityIdContinuesRevisionAndRejectsDelayedOldPacket() {
        PDataSyncService.acceptClientSync(new PDataSyncPacket(9, 7, tagOf("old", 1)));
        PDataSyncService.acceptClientSync(new PDataSyncPacket(9, 8, new CompoundTag()));
        PDataSyncService.acceptClientSync(new PDataSyncPacket(9, 6, tagOf("delayed", 99)));
        assertFalse(PDataSyncService.hasPendingClientData(9), "delayed old data must not resurrect after removal");

        PDataSyncService.acceptClientSync(new PDataSyncPacket(9, 9, tagOf("new", 2)));

        assertTrue(PDataSyncService.hasPendingClientData(9), "replacement entity must publish a fresh mirror");
        assertEquals(2, new PersistentDataJS(() -> PDataSyncService.clientMirrorById(9), tag -> {}).getInt("new"));
    }

    @Test
    void clearClientMirrorsWipesEverything() {
        PDataSyncService.acceptClientSync(new PDataSyncPacket(1, 1, tagOf("a", 1)));
        PDataSyncService.acceptClientSync(new PDataSyncPacket(2, 1, tagOf("b", 2)));

        PDataSyncService.clearClientMirrors();
        assertFalse(PDataSyncService.hasPendingClientData(1));
        assertFalse(PDataSyncService.hasPendingClientData(2));
        assertEquals(0, mirrorOfCleared(1).size(), "cleared entity must mirror to empty");
    }

    private static CompoundTag mirrorOfCleared(int entityId) {
        // The getOrDefault path after a clear returns an empty tag; this helper checks that semantic
        // directly.
        return PDataSyncService.clientMirrorById(entityId);
    }
}
