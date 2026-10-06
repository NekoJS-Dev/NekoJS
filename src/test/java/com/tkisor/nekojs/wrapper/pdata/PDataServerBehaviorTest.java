package com.tkisor.nekojs.wrapper.pdata;

import com.tkisor.nekojs.api.inject.EntityExtension;
import com.tkisor.nekojs.api.inject.EntityPDataStore;
import com.tkisor.nekojs.network.PDataSyncPacket;
import com.tkisor.nekojs.network.PlayPacketDispatcher;
import com.tkisor.nekojs.network.PlayPacketDispatchers;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
//? if >=26 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PDataServerBehaviorTest {
    private final Map<Integer, CompoundTag> persisted = new HashMap<>();
    private final List<PDataSyncPacket> sent = new ArrayList<>();
    private PlayPacketDispatcher previousDispatcher;
    private EntityPDataStore.Access previousStore;

    @BeforeEach
    void installNativeFixture() {
        assumeTrue(VanillaRegistryProbe.available(), "native entity fixtures require bootstrapped registries");
        previousDispatcher = PlayPacketDispatchers.get();
        previousStore = EntityPDataStore.get();
        PDataSyncService.resetServerState();
        EntityPDataStore.install(new EntityPDataStore.Access() {
            @Override public CompoundTag get(int id, String key) {
                return persisted.getOrDefault(id, new CompoundTag()).copy();
            }
            @Override public void set(int id, String key, CompoundTag value) {
                persisted.put(id, value.copy());
            }
        });
        installRecorder(null);
    }

    @AfterEach
    void releaseFixture() {
        if (previousDispatcher != null) PlayPacketDispatchers.install(previousDispatcher);
        if (previousStore != null) EntityPDataStore.install(previousStore);
        PDataSyncService.resetServerState();
    }

    @Test
    void newlyTrackingPlayerGetsPersistedDataWithoutADirtyWrite() throws Exception {
        FixtureEntity entity = entity(10);
        CompoundTag stored = new CompoundTag();
        stored.putInt("mana", 42);
        persisted.put(10, stored);
        PDataSyncService.syncTo(entity, null);
        assertEquals(1, sent.size());
        assertEquals(42, new PersistentDataJS(sent.get(0)::data, value -> {}).getInt("mana"));
        stored.putInt("mana", 99);
        assertEquals(42, new PersistentDataJS(sent.get(0)::data, value -> {}).getInt("mana"));
        PDataSyncService.flush(null);
        assertEquals(1, sent.size(), "initial snapshot must not create a dirty write");
    }

    @Test
    void stopTrackingRemovalAndIdReuseKeepMonotonicRevisions() throws Exception {
        FixtureEntity original = entity(20);
        PDataSyncService.syncTo(original, null);
        PDataSyncService.clearFor(original, null);
        assertTrue(sent.get(1).data().isEmpty());
        PDataSyncService.onEntityRemoved(original);
        PDataSyncService.syncTo(entity(20), null);
        for (int index = 0; index < sent.size(); index++) {
            assertEquals(index + 1, sent.get(index).revision());
        }
        PDataSyncService.resetServerState();
        PDataSyncService.syncTo(entity(20), null);
        assertEquals(1, sent.get(4).revision(), "only server stop resets the revision sequence");
    }

    @Test
    void flushIsBoundedAndDoesNotLoseWritesDuringPacketDispatch() throws Exception {
        for (int id = 0; id < 257; id++) PDataSyncService.markDirty(entity(id));
        PDataSyncService.flush(null);
        assertEquals(256, sent.size());
        PDataSyncService.flush(null);
        assertEquals(257, sent.size());

        FixtureEntity changed = entity(300);
        AtomicBoolean first = new AtomicBoolean(true);
        installRecorder(() -> {
            if (first.getAndSet(false)) PDataSyncService.markDirty(changed);
        });
        PDataSyncService.markDirty(changed);
        PDataSyncService.flush(null);
        PDataSyncService.flush(null);
        assertEquals(259, sent.size(), "write occurring during send must survive for the next tick");
    }

    @Test
    void cloneCopiesOnlyNekoDataAndQueuesTheReplacementForSynchronization() throws Exception {
        FixtureEntity original = entity(30);
        FixtureEntity replacement = entity(31);
        original.neko$pdata().putInt("mana", 7);
        PDataSyncService.resetServerState();
        PDataSyncService.copyPlayerData(original, replacement);
        assertEquals(7, replacement.neko$pdata().getInt("mana"));
        original.neko$pdata().putInt("mana", 99);
        assertEquals(7, replacement.neko$pdata().getInt("mana"));
        PDataSyncService.flush(null);
        assertTrue(sent.stream().anyMatch(packet -> packet.entityId() == 31));
    }

    private void installRecorder(Runnable duringSend) {
        PlayPacketDispatchers.install(new PlayPacketDispatcher() {
            @Override public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) { capture(payload); }
            @Override public void sendToAllPlayers(CustomPacketPayload payload) { capture(payload); }
            @Override public void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) { capture(payload); }
            private void capture(CustomPacketPayload payload) {
                sent.add((PDataSyncPacket) payload);
                if (duringSend != null) duringSend.run();
            }
        });
    }

    private FixtureEntity entity(int id) throws Exception {
        FixtureEntity entity = allocate(FixtureEntity.class);
        entity.fixtureId = id;
        entity.fixtureLevel = allocate(ServerLevel.class);
        return entity;
    }

    private static final class FixtureEntity extends Entity implements EntityExtension {
        private int fixtureId;
        private Level fixtureLevel;
        private FixtureEntity() { super(null, null); }
        @Override public int getId() { return fixtureId; }
        @Override public Level level() { return fixtureLevel; }
        @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
//? if >=26 {
        @Override public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) { return false; }
        @Override protected void readAdditionalSaveData(ValueInput input) {}
        @Override protected void addAdditionalSaveData(ValueOutput output) {}
//?} else {
/*        @Override protected void readAdditionalSaveData(CompoundTag input) {}
        @Override protected void addAdditionalSaveData(CompoundTag output) {}
*///?}
    }

    @SuppressWarnings("removal")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
}
