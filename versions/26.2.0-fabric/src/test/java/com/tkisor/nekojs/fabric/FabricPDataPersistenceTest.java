package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.inject.EntityExtension;
import com.tkisor.nekojs.api.inject.EntityPDataStore;
import com.tkisor.nekojs.api.inject.NekoEntityPData;
import com.tkisor.nekojs.fabric.mixin.NekoEntityPDataMixin;
import com.tkisor.nekojs.network.PDataSyncPacket;
import com.tkisor.nekojs.network.PlayPacketDispatcher;
import com.tkisor.nekojs.network.PlayPacketDispatchers;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the production Fabric store through script access, persistence, and synchronization. */
class FabricPDataPersistenceTest {
    private EntityPDataStore.Access previousStore;
    private PlayPacketDispatcher previousDispatcher;
    private final List<PDataSyncPacket> sent = new ArrayList<>();

    @BeforeEach
    void installFabricStore() throws Exception {
        assertTrue(VanillaRegistryProbe.registryMetadataAvailable(), "entity fixture requires vanilla registries");
        previousStore = EntityPDataStore.get();
        previousDispatcher = PlayPacketDispatchers.get();
        Method access = FabricPDataSync.class.getDeclaredMethod("fullAccess");
        access.setAccessible(true);
        EntityPDataStore.install((EntityPDataStore.Access) access.invoke(null));
        PDataSyncService.resetServerState();
        PDataSyncService.clearClientMirrors();
        PlayPacketDispatchers.install(new PlayPacketDispatcher() {
            @Override public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) { capture(payload); }
            @Override public void sendToAllPlayers(CustomPacketPayload payload) { capture(payload); }
            @Override public void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) { capture(payload); }
            private void capture(CustomPacketPayload payload) { sent.add((PDataSyncPacket) payload); }
        });
    }

    @AfterEach
    void releaseFabricStore() {
        if (previousStore != null) EntityPDataStore.install(previousStore);
        if (previousDispatcher != null) PlayPacketDispatchers.install(previousDispatcher);
        PDataSyncService.resetServerState();
        PDataSyncService.clearClientMirrors();
    }

    @Test
    void neoForgeSaveIsVisibleToScriptsAndSyncWithoutAnExtraPayloadKey() throws Exception {
        CompoundTag payload = new CompoundTag();
        payload.putInt("mana", 303);
        payload.putString("name", "neko");
        FixtureEntity entity = entity(10);
        load(entity, saved(payload));

        assertEquals(303, entity.neko$pdata().getInt("mana"), "the Fabric bridge must read the loaded payload once");
        assertEquals("neko", entity.neko$pdata().getString("name"));
        PDataSyncService.syncTo(entity, null);
        assertEquals(payload, sent.getFirst().data(), "tracking sync must contain the script payload, not an extra container");
        assertEquals(saved(payload), save(entity), "reading and saving must retain the canonical cross-loader format");
        assertFalse(sent.getFirst().data().contains(EntityExtension.NEKO_PDATA_KEY));
    }

    @Test
    void scriptWritesSaveAndReloadAtTheCanonicalDepthBeforeEntityIndexing() throws Exception {
        FixtureEntity entity = entity(20);
        entity.neko$pdata().putInt("mana", 42).putString("name", "fresh");
        CompoundTag payload = entity.neko$pdata().copyTag();
        assertEquals(saved(payload), save(entity), "reference access must work without an ID lookup");
        assertFalse(payload.contains(EntityExtension.NEKO_PDATA_KEY));

        FixtureEntity reloaded = entity(21);
        load(reloaded, save(entity));
        assertEquals(payload, reloaded.neko$pdata().copyTag());
        PDataSyncService.flush(null);
        assertEquals(payload, sent.getFirst().data());
        PDataSyncService.acceptClientSync(sent.getFirst());
        assertEquals(payload, PDataSyncService.clientMirrorById(entity.getId()));
    }

    @Test
    void scriptWritesAndClearRetainUnrelatedPersistenceFields() throws Exception {
        CompoundTag payload = new CompoundTag();
        payload.putInt("mana", 3);
        CompoundTag original = saved(payload);
        CompoundTag container = original.getCompound("NeoForgeData").orElseThrow();
        container.putInt("SomeOtherModsKey", 7);
        CompoundTag unrelated = new CompoundTag();
        unrelated.putByteArray("bytes", new byte[]{1, 2, 3});
        unrelated.putString("owner", "other");
        container.put("OtherModData", unrelated);
        FixtureEntity entity = entity(30);
        load(entity, original);
        assertEquals(original, save(entity), "loading must not discard unrelated container keys");

        entity.neko$pdata().putInt("mana", 4);
        CompoundTag updated = save(entity).getCompound("NeoForgeData").orElseThrow();
        assertEquals(7, updated.getIntOr("SomeOtherModsKey", -1));
        assertEquals(unrelated, updated.getCompound("OtherModData").orElseThrow());
        assertEquals(4, updated.getCompound(EntityExtension.NEKO_PDATA_KEY).orElseThrow().getIntOr("mana", -1));
        entity.neko$pdata().clear();
        CompoundTag cleared = save(entity).getCompound("NeoForgeData").orElseThrow();
        assertFalse(cleared.contains(EntityExtension.NEKO_PDATA_KEY));
        assertEquals(7, cleared.getIntOr("SomeOtherModsKey", -1));
        assertEquals(unrelated, cleared.getCompound("OtherModData").orElseThrow());
        PDataSyncService.flush(null);
        assertTrue(sent.getFirst().data().isEmpty(), "cleared script data must synchronize as empty");
    }

    @Test
    void historicalNestedDataRoundTripsWithoutGuessingOrFlattening() throws Exception {
        CompoundTag historicalData = new CompoundTag();
        historicalData.putInt("mana", 303);
        CompoundTag nestedPayload = new CompoundTag();
        nestedPayload.put(EntityExtension.NEKO_PDATA_KEY, historicalData.copy());
        CompoundTag historicalSave = saved(nestedPayload);
        historicalSave.getCompound("NeoForgeData").orElseThrow().putString("OtherMod", "retained");
        FixtureEntity entity = entity(40);
        load(entity, historicalSave);

        assertEquals(nestedPayload, entity.neko$pdata().copyTag());
        assertEquals(0, entity.neko$pdata().getInt("mana"), "nested historical data is not silently migrated");
        assertEquals(historicalData, entity.neko$pdata().getCompound(EntityExtension.NEKO_PDATA_KEY));
        assertEquals(historicalSave, save(entity));
        PDataSyncService.syncTo(entity, null);
        assertEquals(nestedPayload, sent.getFirst().data(), "the packet must preserve the same nested script view");
        FixtureEntity reloaded = entity(41);
        load(reloaded, save(entity));
        assertEquals(historicalSave, save(reloaded));
        assertEquals(nestedPayload, reloaded.neko$pdata().copyTag());
    }

    @Test
    void playerCopyUsesThePayloadOnceAndDoesNotReplaceOtherModData() throws Exception {
        FixtureEntity original = entity(50);
        FixtureEntity replacement = entity(51);
        original.neko$pdata().putInt("mana", 8);
        replacement.neko$getPDataRoot().putString("OtherMod", "replacement-only");
        PDataSyncService.resetServerState();
        PDataSyncService.copyPlayerData(original, replacement);
        assertEquals(8, replacement.neko$pdata().getInt("mana"));
        assertEquals("replacement-only", replacement.neko$getPDataRoot().getStringOr("OtherMod", ""));
        original.neko$pdata().putInt("mana", 99);
        assertEquals(8, replacement.neko$pdata().getInt("mana"), "player copies must not alias the original");
        PDataSyncService.flush(null);
        assertTrue(sent.stream().anyMatch(packet -> packet.entityId() == replacement.getId()
                && packet.data().getIntOr("mana", -1) == 8));
        CompoundTag persisted = save(replacement).getCompound("NeoForgeData").orElseThrow();
        assertEquals(replacement.neko$pdata().copyTag(), persisted.getCompound(EntityExtension.NEKO_PDATA_KEY).orElseThrow());
    }

    @Test
    void persistenceAndStoreBoundariesKeepDefensiveCopiesAndResetOnReload() throws Exception {
        CompoundTag payload = new CompoundTag();
        payload.putInt("mana", 12);
        CompoundTag source = saved(payload);
        FixtureEntity entity = entity(60);
        load(entity, source);
        source.getCompound("NeoForgeData").orElseThrow()
                .getCompound(EntityExtension.NEKO_PDATA_KEY).orElseThrow().putInt("mana", 100);
        assertEquals(12, entity.neko$pdata().getInt("mana"));
        CompoundTag read = EntityPDataStore.getPDataTag(entity, EntityExtension.NEKO_PDATA_KEY);
        read.putInt("mana", 101);
        assertEquals(12, entity.neko$pdata().getInt("mana"));
        CompoundTag saved = save(entity);
        entity.neko$pdata().putInt("mana", 13);
        assertEquals(12, saved.getCompound("NeoForgeData").orElseThrow()
                .getCompound(EntityExtension.NEKO_PDATA_KEY).orElseThrow().getIntOr("mana", -1));
        load(entity, new CompoundTag());
        assertTrue(entity.neko$pdata().copyTag().isEmpty(), "loading an absent container must reset old entity data");
        assertFalse(save(entity).contains("NeoForgeData"));
    }

    private static CompoundTag saved(CompoundTag payload) {
        CompoundTag container = new CompoundTag();
        container.put(EntityExtension.NEKO_PDATA_KEY, payload.copy());
        CompoundTag save = new CompoundTag();
        save.put("NeoForgeData", container);
        return save;
    }

    private static void load(FixtureEntity entity, CompoundTag save) throws Exception {
        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, save);
        Method load = NekoEntityPDataMixin.class.getDeclaredMethod("neko$loadPData", ValueInput.class, CallbackInfo.class);
        load.setAccessible(true);
        load.invoke(entity.persistence, input, null);
    }

    private static CompoundTag save(FixtureEntity entity) throws Exception {
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        Method save = NekoEntityPDataMixin.class.getDeclaredMethod("neko$savePData", ValueOutput.class, CallbackInfo.class);
        save.setAccessible(true);
        save.invoke(entity.persistence, output, null);
        return output.buildResult();
    }

    private static FixtureEntity entity(int id) throws Exception {
        FixtureEntity entity = allocate(FixtureEntity.class);
        entity.fixtureId = id;
        entity.fixtureLevel = allocate(ServerLevel.class);
        entity.persistence = new DrivenMixin();
        return entity;
    }

    private static final class DrivenMixin extends NekoEntityPDataMixin {}

    // The fixture substitutes entity/world allocation only, not the bridge or persistence bodies.
    private static final class FixtureEntity extends Entity implements EntityExtension, NekoEntityPData {
        private int fixtureId;
        private Level fixtureLevel;
        private DrivenMixin persistence;
        private FixtureEntity() { super(null, null); }
        @Override public int getId() { return fixtureId; }
        @Override public Level level() { return fixtureLevel; }
        @Override public CompoundTag neko$getPDataRoot() { return persistence.neko$getPDataRoot(); }
        @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
        @Override public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) { return false; }
        @Override protected void readAdditionalSaveData(ValueInput input) {}
        @Override protected void addAdditionalSaveData(ValueOutput output) {}
    }

    @SuppressWarnings("removal")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
}
