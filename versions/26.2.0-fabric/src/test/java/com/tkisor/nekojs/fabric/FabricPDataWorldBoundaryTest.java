package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.network.PDataSyncPacket;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricPDataWorldBoundaryTest {
    @AfterEach
    void resetWorld() throws Exception {
        observe(null);
        PDataSyncService.clearClientMirrors();
    }

    @Test
    void firstLoginSnapshotSurvivesTheFollowingClientTick() throws Exception {
        Object firstWorld = new Object();
        receive(5, 1, "login", firstWorld);
        observe(firstWorld);
        assertEquals("login", PDataSyncService.clientMirrorById(5).getStringOr("state", ""));
    }

    @Test
    void newDimensionSnapshotArrivingBeforeTickClearsOnlyOldWorldData() throws Exception {
        Object oldWorld = new Object();
        Object newWorld = new Object();
        receive(5, 1, "old", oldWorld);
        receive(6, 1, "old-other", oldWorld);
        receive(5, 2, "new", newWorld);
        observe(newWorld);
        assertEquals("new", PDataSyncService.clientMirrorById(5).getStringOr("state", ""));
        assertFalse(PDataSyncService.hasPendingClientData(6), "old-world entities must be cleared");
    }

    @Test
    void disconnectAndReconnectResetRevisionsWithoutDeletingFreshLoginData() throws Exception {
        Object firstWorld = new Object();
        receive(5, 9, "old", firstWorld);
        observe(null);
        assertFalse(PDataSyncService.hasPendingClientData(5));
        Object reconnectedWorld = new Object();
        receive(5, 1, "reconnected", reconnectedWorld);
        observe(reconnectedWorld);
        assertTrue(PDataSyncService.hasPendingClientData(5));
        assertEquals("reconnected", PDataSyncService.clientMirrorById(5).getStringOr("state", ""));
    }

    private static void receive(int id, int revision, String value, Object world) throws Exception {
        CompoundTag tag = new CompoundTag();
        tag.putString("state", value);
        Method receiver = FabricPDataSync.class.getDeclaredMethod("acceptClientSync", PDataSyncPacket.class, Object.class);
        receiver.setAccessible(true);
        receiver.invoke(null, new PDataSyncPacket(id, revision, tag), world);
    }

    private static void observe(Object world) throws Exception {
        Method tick = FabricPDataSync.class.getDeclaredMethod("observeClientLevel", Object.class);
        tick.setAccessible(true);
        tick.invoke(null, world);
    }
}
