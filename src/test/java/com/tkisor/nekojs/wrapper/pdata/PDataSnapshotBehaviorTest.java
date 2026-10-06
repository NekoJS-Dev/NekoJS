package com.tkisor.nekojs.wrapper.pdata;

import com.tkisor.nekojs.network.PDataSyncPacket;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PDataSnapshotBehaviorTest {
    @AfterEach
    void reset() {
        PDataSyncService.resetServerState();
        PDataSyncService.clearClientMirrors();
    }

    @Test
    void snapshotsCopyStoredDataAndRemovalReusesTheSameMonotonicSequence() throws Exception {
        CompoundTag stored = new CompoundTag();
        stored.putInt("mana", 7);
        PDataSyncPacket initial = snapshot(3, stored);
        assertEquals(1, initial.revision());
        stored.putInt("mana", 99);
        assertEquals(7, new PersistentDataJS(initial::data, tag -> {}).getInt("mana"));
        PDataSyncService.acceptClientSync(initial);
        PDataSyncPacket stoppedTracking = clear(3);
        assertEquals(2, stoppedTracking.revision());
        PDataSyncService.acceptClientSync(stoppedTracking);
        PDataSyncService.acceptClientSync(initial);
        assertFalse(PDataSyncService.hasPendingClientData(3), "a delayed packet cannot resurrect data after stop tracking");
        PDataSyncPacket removed = clear(3);
        assertEquals(3, removed.revision());
        PDataSyncPacket reused = snapshot(3, stored);
        assertEquals(4, reused.revision());
        PDataSyncService.acceptClientSync(reused);
        assertEquals(99, new PersistentDataJS(() -> PDataSyncService.clientMirrorById(3), tag -> {}).getInt("mana"));
    }

    @Test
    void exactSizeLimitPassesButOversizedSnapshotsDoNotConsumeARevision() throws Exception {
        CompoundTag boundary = new CompoundTag();
        boundary.putString("value", "x");
        int overhead = boundary.toString().length() - 1;
        boundary.putString("value", "x".repeat(32768 - overhead));
        assertEquals(32768, boundary.toString().length());
        assertNotNull(snapshot(8, boundary));
        boundary.putString("value", "x".repeat(32769 - overhead));
        assertNull(snapshot(8, boundary));
        assertEquals(2, clear(8).revision());
    }

    @Test
    void serverStopResetsTheSequenceForTheNextServerInstance() throws Exception {
        snapshot(9, new CompoundTag());
        clear(9);
        PDataSyncService.resetServerState();
        assertEquals(1, snapshot(9, new CompoundTag()).revision());
    }

    private static PDataSyncPacket snapshot(int id, CompoundTag data) throws Exception {
        Method snapshot = PDataSyncService.class.getDeclaredMethod("snapshot", int.class, CompoundTag.class);
        snapshot.setAccessible(true);
        return (PDataSyncPacket) snapshot.invoke(null, id, data);
    }

    private static PDataSyncPacket clear(int id) throws Exception {
        Method snapshot = PDataSyncService.class.getDeclaredMethod("clearSnapshot", int.class);
        snapshot.setAccessible(true);
        return (PDataSyncPacket) snapshot.invoke(null, id);
    }
}
