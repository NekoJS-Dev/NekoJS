package com.tkisor.nekojs.core.stage;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StageCustomizeEvent;
import com.tkisor.nekojs.api.stage.StageDefinition;
import com.tkisor.nekojs.api.stage.StageHandle;
import com.tkisor.nekojs.api.stage.StageNotifyOptions;
import com.tkisor.nekojs.api.stage.StagePlayerRef;
import com.tkisor.nekojs.api.stage.StageTickContext;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-common unit tests for {@link StagePersistence}, {@link StageStore}, {@link StageHandle},
 * temporary stage tick countdowns, {@link StageDefinition} lifecycle callbacks, fake-player
 * isolation, and read-only client stage handles.
 */
class StageStorePersistenceTest {

    private static final String PLAYER_UUID = "00000000-0000-0000-0000-0000000000a1";

    @AfterEach
    void tearDown() {
        ClientStageMirror.SHARED.clear();
        StageRuntimeBridge.reset();
    }

    @Test
    void atomicRoundTripPersistsServerAndPlayerStagesWithoutTempLeftovers(@TempDir Path tempDir) throws IOException {
        Path stageDir = StagePersistence.resolveWorldStageDir(tempDir);
        assertEquals(tempDir.resolve("nekojs").resolve("stages"), stageDir);

        assertEquals(StagePersistence.StageData.EMPTY, StagePersistence.readServer(stageDir));
        assertEquals(StagePersistence.StageData.EMPTY, StagePersistence.readPlayer(stageDir, PLAYER_UUID));

        StagePersistence.writeServer(
                stageDir,
                Set.of("nether_unlocked", "iron_age"),
                Map.of("double_xp", 1200L, "iron_age", 400L)
        );
        StagePersistence.StageData serverData = StagePersistence.readServer(stageDir);
        assertEquals(StagePersistence.SCHEMA_VERSION, serverData.schemaVersion());
        assertEquals(Set.of("iron_age", "nether_unlocked"), serverData.permanent());
        assertEquals(Map.of("double_xp", 1200L), serverData.temporary(),
                "Permanent stages must supersede overlapping temporary entries on disk");

        StagePersistence.writePlayer(
                stageDir,
                PLAYER_UUID,
                Set.of("copper_age", "archery"),
                Map.of("haste_trial", 95L)
        );
        StagePersistence.StageData playerData = StagePersistence.readPlayer(stageDir, PLAYER_UUID);
        assertEquals(StagePersistence.SCHEMA_VERSION, playerData.schemaVersion());
        assertEquals(Set.of("copper_age", "archery"), playerData.permanent());
        assertEquals(Map.of("haste_trial", 95L), playerData.temporary());

        try (Stream<Path> rootEntries = Files.list(stageDir);
             Stream<Path> playerEntries = Files.list(stageDir.resolve("players"))) {
            assertTrue(rootEntries.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")),
                    "Atomic server write must leave no temporary files behind");
            assertTrue(playerEntries.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")),
                    "Atomic player write must leave no temporary files behind");
        }

        StageStore reloadedStore = new StageStore(stageDir);
        assertTrue(reloadedStore.serverHandle().has("iron_age"));
        assertEquals(1200L, reloadedStore.serverHandle().remainingTicks("double_xp"));

        StageHandle reloadedPlayer = reloadedStore.playerHandle(StagePlayerRef.of(PLAYER_UUID, "Alex"));
        assertTrue(reloadedPlayer.hasAll(List.of("copper_age", "archery", "haste_trial")));
        assertEquals(-1L, reloadedPlayer.remainingTicks("copper_age"));
        assertEquals(95L, reloadedPlayer.remainingTicks("haste_trial"));

        reloadedPlayer.add("industrial_age");
        reloadedStore.serverHandle().add("end_unlocked");
        reloadedStore.onPlayerLeave(PLAYER_UUID);
        reloadedStore.saveAll();

        StageStore secondReload = new StageStore(stageDir);
        assertTrue(secondReload.serverPermanentSnapshot().contains("end_unlocked"));
        assertTrue(secondReload.playerPermanentSnapshot(PLAYER_UUID).contains("industrial_age"));
        assertEquals(Map.of("haste_trial", 95L), secondReload.playerTemporarySnapshot(PLAYER_UUID));
    }

    @Test
    void corruptJsonFailsLoudlyPreservesDamagedFileForBackupAndRecoversCleanly(@TempDir Path tempDir) throws IOException {
        Path stageDir = StagePersistence.resolveWorldStageDir(tempDir);
        Path serverFile = StagePersistence.serverFile(stageDir);
        Files.createDirectories(serverFile.getParent());

        String damagedServerPayload = "{\"schemaVersion\": 1, \"permanent\": [\"iron_age\"";
        Files.writeString(serverFile, damagedServerPayload, StandardCharsets.UTF_8);

        IllegalStateException readError = assertThrows(
                IllegalStateException.class,
                () -> StagePersistence.readServer(stageDir)
        );
        assertTrue(readError.getMessage().contains("[NEKO-4072]"), readError.getMessage());

        IllegalStateException storeInitError = assertThrows(
                IllegalStateException.class,
                () -> new StageStore(stageDir)
        );
        assertTrue(storeInitError.getMessage().contains("[NEKO-4072]"), storeInitError.getMessage());
        assertEquals(damagedServerPayload, Files.readString(serverFile, StandardCharsets.UTF_8),
                "Corrupt server.json must never be silently overwritten during failed reads");

        Path backupFile = stageDir.resolve("server.json.corrupt.bak");
        Files.move(serverFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        assertEquals(damagedServerPayload, Files.readString(backupFile, StandardCharsets.UTF_8));

        StageStore recoveredStore = new StageStore(stageDir);
        assertTrue(recoveredStore.serverHandle().add("recovered_world"));
        recoveredStore.saveServer();
        assertEquals(Set.of("recovered_world"), StagePersistence.readServer(stageDir).permanent());

        Path playerFile = StagePersistence.playerFile(stageDir, PLAYER_UUID);
        Files.createDirectories(playerFile.getParent());
        Files.writeString(playerFile, "{\"schemaVersion\": 99, \"permanent\": []}", StandardCharsets.UTF_8);
        IllegalStateException unsupportedSchema = assertThrows(
                IllegalStateException.class,
                () -> StagePersistence.readPlayer(stageDir, PLAYER_UUID)
        );
        assertTrue(unsupportedSchema.getMessage().contains("[NEKO-4072]"), unsupportedSchema.getMessage());

        Files.writeString(playerFile, "   \n  ", StandardCharsets.UTF_8);
        IllegalStateException blankFileError = assertThrows(
                IllegalStateException.class,
                () -> StagePersistence.readPlayer(stageDir, PLAYER_UUID)
        );
        assertTrue(blankFileError.getMessage().contains("[NEKO-4072]"), blankFileError.getMessage());
    }

    @Test
    void playerFileSanitizesUuidAndRejectsPathTraversal(@TempDir Path tempDir) {
        Path stageDir = StagePersistence.resolveWorldStageDir(tempDir);

        for (String badKey : new String[]{
                null,
                "",
                "   ",
                "../server",
                "..\\server",
                "nested/player",
                "nested\\player",
                "player..two",
                "bad\0uuid"
        }) {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> StagePersistence.playerFile(stageDir, badKey),
                    "Expected traversal/invalid UUID rejection for: " + badKey
            );
            assertTrue(ex.getMessage().contains("[NEKO-4070]"), ex.getMessage());
        }

        Path sanitized = StagePersistence.playerFile(stageDir, "  " + PLAYER_UUID + "  ");
        assertEquals(stageDir.resolve("players").resolve(PLAYER_UUID + ".json"), sanitized);
    }

    @Test
    void playerAndServerStageHandlesEnforceIdempotentMutationsAndQuerySemantics() {
        RecordingHooks hooks = new RecordingHooks();
        StageStore store = new StageStore(null, hooks);

        exerciseMutableStageHandle(store.playerHandle(StagePlayerRef.of(PLAYER_UUID, "Alex")));
        exerciseMutableStageHandle(store.serverHandle());

        assertFalse(hooks.playerDeltas.isEmpty(), "Player mutations should emit player delta sync calls");
        assertFalse(hooks.serverDeltas.isEmpty(), "Server mutations should emit server delta sync calls");

        store.onPlayerJoin(PLAYER_UUID, "AlexOnline");
        assertEquals(1, hooks.fullSyncCount);
    }

    private static void exerciseMutableStageHandle(StageHandle handle) {
        assertFalse(handle.has("bronze_age"));
        assertEquals(List.of(), handle.getAll());
        assertFalse(handle.hasAny(List.of("bronze_age", "steel_age")));
        assertFalse(handle.hasAny(List.of()));
        assertTrue(handle.hasAll(List.of()));

        assertTrue(handle.add("bronze_age"), "First add must report state change");
        assertFalse(handle.add("bronze_age"), "Second add of an active permanent stage must return false");
        assertTrue(handle.has("bronze_age"));
        assertEquals(-1L, handle.remainingTicks("bronze_age"));
        assertFalse(handle.isTemporary("bronze_age"));

        assertTrue(handle.addAll(List.of("bronze_age", "steel_age", "mythril_age")));
        assertFalse(handle.addAll(List.of("bronze_age", "steel_age")));
        assertFalse(handle.addAll(List.of()));
        assertTrue(handle.hasAny(List.of("unknown_stage", "steel_age")));
        assertTrue(handle.hasAll(List.of("bronze_age", "steel_age", "mythril_age")));
        assertFalse(handle.hasAll(List.of("bronze_age", "missing_stage")));
        assertEquals(List.of("bronze_age", "steel_age", "mythril_age"), handle.getAll());

        assertTrue(handle.remove("steel_age"));
        assertFalse(handle.remove("steel_age"));
        assertFalse(handle.has("steel_age"));

        assertTrue(handle.removeAll(List.of("steel_age", "mythril_age")));
        assertFalse(handle.removeAll(List.of("steel_age", "mythril_age")));
        assertEquals(List.of("bronze_age"), handle.getAll());

        assertFalse(handle.set("bronze_age", true));
        assertTrue(handle.set("bronze_age", false));
        assertFalse(handle.set("bronze_age", false));
        assertTrue(handle.set("arcane_age", true));
        assertTrue(handle.has("arcane_age"));

        assertTrue(handle.toggle("arcane_age"));
        assertFalse(handle.has("arcane_age"));
        assertTrue(handle.toggle("arcane_age"));
        assertTrue(handle.has("arcane_age"));

        assertTrue(handle.replace(List.of("tier_one", "tier_two")));
        assertEquals(List.of("tier_one", "tier_two"), handle.getAll());
        assertFalse(handle.has("arcane_age"));
        assertFalse(handle.replace(List.of("tier_one", "tier_two")),
                "Replacing with identical stage contents must return false");

        assertDoesNotThrow(handle::sync);

        assertTrue(handle.clear());
        assertEquals(List.of(), handle.getAll());
        assertFalse(handle.clear(), "Clearing an already empty handle must return false");
    }

    @Test
    void temporaryStagesCountdownPromoteViaAddAndInvokeLifecycleHooks() {
        List<StageTickContext> grantedContexts = new ArrayList<>();
        List<StageTickContext> tickContexts = new ArrayList<>();
        List<StageTickContext> expiredContexts = new ArrayList<>();

        StageCustomizeEvent customize = new StageCustomizeEvent();
        customize.temporaryStage("frenzy", 3L)
                .displayName("Battle Frenzy")
                .description("Short combat surge")
                .icon(NekoId.of("minecraft", "blaze_powder"))
                .notifyOnGrant(StageNotifyOptions.ALL)
                .grantTitle("Frenzy Activated")
                .revokeMessage("Frenzy Faded")
                .whenGranted(grantedContexts::add)
                .everyTick(tickContexts::add)
                .whenExpired(expiredContexts::add);

        customize.stage("trial_pass")
                .whenGranted(grantedContexts::add)
                .everyTick(tickContexts::add)
                .whenExpired(expiredContexts::add);

        customize.stage("eternal_aura")
                .everyTick(tickContexts::add);

        StageStore store = new StageStore();
        store.setDefinitions(customize.buildDefinitions());

        StageHandle player = store.playerHandle(StagePlayerRef.of(PLAYER_UUID, "Alex"));
        StageHandle server = store.serverHandle();

        IllegalArgumentException zeroTicks = assertThrows(
                IllegalArgumentException.class,
                () -> player.grantTemporary("trial_pass", 0L)
        );
        assertTrue(zeroTicks.getMessage().contains("[NEKO-4070]"), zeroTicks.getMessage());

        // Adding a customized temporaryStage uses its configured defaultTicks (3 ticks).
        assertTrue(player.add("frenzy"));
        assertTrue(player.has("frenzy"));
        assertTrue(player.isTemporary("frenzy"));
        assertEquals(3L, player.remainingTicks("frenzy"));
        assertFalse(player.grantTemporary("frenzy", 3L),
                "Granting the same temporary duration again must be idempotent");

        // Grant trial_pass temporarily (4 ticks) and eternal_aura permanently on the server.
        assertTrue(player.grantTemporary("trial_pass", 4L));
        assertTrue(player.isTemporary("trial_pass"));
        assertEquals(4L, player.remainingTicks("trial_pass"));
        assertTrue(server.add("eternal_aura"));

        assertEquals(2, grantedContexts.size());
        assertEquals("frenzy", grantedContexts.get(0).getStage());
        assertEquals(3L, grantedContexts.get(0).getRemainingTicks());
        assertEquals("player", grantedContexts.get(0).getScope());
        assertEquals(PLAYER_UUID, grantedContexts.get(0).getPlayerUuid());
        assertEquals("trial_pass", grantedContexts.get(1).getStage());
        assertEquals(4L, grantedContexts.get(1).getRemainingTicks());

        // Tick 1: frenzy -> 2, trial_pass -> 3, eternal_aura -> -1
        store.tickServer();
        assertEquals(2L, player.remainingTicks("frenzy"));
        assertEquals(3L, player.remainingTicks("trial_pass"));
        assertTrue(expiredContexts.isEmpty());

        // Promote trial_pass from temporary to permanent via add().
        assertTrue(player.add("trial_pass"));
        assertTrue(player.has("trial_pass"));
        assertFalse(player.isTemporary("trial_pass"));
        assertEquals(-1L, player.remainingTicks("trial_pass"));
        assertFalse(player.grantTemporary("trial_pass", 10L),
                "Temporary grant must not downgrade an already permanent stage");

        // Tick 2: frenzy -> 1, trial_pass stays -1
        store.tickServer();
        assertEquals(1L, player.remainingTicks("frenzy"));
        assertEquals(-1L, player.remainingTicks("trial_pass"));
        assertTrue(expiredContexts.isEmpty());

        // Tick 3: frenzy reaches 0 and expires; trial_pass remains permanent!
        store.tickServer();
        assertFalse(player.has("frenzy"));
        assertFalse(player.isTemporary("frenzy"));
        assertEquals(0L, player.remainingTicks("frenzy"));
        assertTrue(player.has("trial_pass"));
        assertEquals(-1L, player.remainingTicks("trial_pass"));

        assertEquals(1, expiredContexts.size(), "whenExpired must fire once when frenzy expires");
        assertEquals("frenzy", expiredContexts.get(0).getStage());
        assertEquals(0L, expiredContexts.get(0).getRemainingTicks());
        assertEquals(PLAYER_UUID, expiredContexts.get(0).getPlayerUuid());

        assertTrue(tickContexts.stream().anyMatch(
                ctx -> "frenzy".equals(ctx.getStage()) && ctx.getRemainingTicks() == 2L));
        assertTrue(tickContexts.stream().anyMatch(
                ctx -> "frenzy".equals(ctx.getStage()) && ctx.getRemainingTicks() == 1L));
        assertTrue(tickContexts.stream().anyMatch(
                ctx -> "eternal_aura".equals(ctx.getStage())
                        && "server".equals(ctx.getScope())
                        && ctx.getRemainingTicks() == -1L));
        assertTrue(expiredContexts.stream().noneMatch(ctx -> "trial_pass".equals(ctx.getStage())),
                "Promoted permanent stage must never expire");
    }

    @Test
    void emptyHandleIsolatesFakePlayersAndClientReadOnlyHandleRejectsMutations() {
        StageStore store = new StageStore();
        assertSame(StageHandle.EMPTY, store.playerHandle(null));
        assertSame(StageHandle.EMPTY, store.playerHandle(StagePlayerRef.FAKE));
        assertSame(StageHandle.EMPTY, store.playerHandle(StagePlayerRef.ofFake()));
        assertSame(StageHandle.EMPTY, store.playerHandle(StagePlayerRef.fake("Deployer")));

        StageHandle empty = StageHandle.EMPTY;
        assertFalse(empty.add("iron_age"));
        assertFalse(empty.add("iron_age", StageNotifyOptions.SILENT));
        assertFalse(empty.addAll(List.of("iron_age", "gold_age")));
        assertFalse(empty.addAll(List.of("iron_age"), StageNotifyOptions.ALL));
        assertFalse(empty.grantTemporary("temp_stage", 100L));
        assertFalse(empty.grantTemporary("temp_stage", 100L, StageNotifyOptions.SILENT));
        assertFalse(empty.remove("iron_age"));
        assertFalse(empty.remove("iron_age", StageNotifyOptions.SILENT));
        assertFalse(empty.removeAll(List.of("iron_age")));
        assertFalse(empty.removeAll(List.of("iron_age"), StageNotifyOptions.SILENT));
        assertFalse(empty.set("iron_age", true));
        assertFalse(empty.toggle("iron_age"));
        assertFalse(empty.clear());
        assertFalse(empty.replace(List.of("iron_age")));
        assertDoesNotThrow(empty::sync);

        assertFalse(empty.has("iron_age"));
        assertFalse(empty.hasAny(List.of("iron_age")));
        assertFalse(empty.hasAll(List.of("iron_age")));
        assertTrue(empty.hasAll(List.of()));
        assertEquals(List.of(), empty.getAll());
        assertEquals(0L, empty.remainingTicks("iron_age"));
        assertFalse(empty.isTemporary("iron_age"));

        ClientStageMirror.SHARED.applyFullSnapshot(
                Set.of("iron_age"),
                Map.of("client_buff", 45L),
                Set.of("world_tier_1"),
                Map.of()
        );

        StageHandle clientHandle = store.playerHandle(StagePlayerRef.client(PLAYER_UUID, "Alex"));
        assertTrue(clientHandle.has("iron_age"));
        assertTrue(clientHandle.has("client_buff"));
        assertFalse(clientHandle.has("missing_stage"));
        assertTrue(clientHandle.hasAny(List.of("missing_stage", "client_buff")));
        assertTrue(clientHandle.hasAll(List.of("iron_age", "client_buff")));
        assertEquals(List.of("iron_age", "client_buff"), clientHandle.getAll());
        assertEquals(-1L, clientHandle.remainingTicks("iron_age"));
        assertEquals(45L, clientHandle.remainingTicks("client_buff"));
        assertTrue(clientHandle.isTemporary("client_buff"));

        assertClientMutationRejected(() -> clientHandle.add("new_stage"));
        assertClientMutationRejected(() -> clientHandle.add("new_stage", StageNotifyOptions.SILENT));
        assertClientMutationRejected(() -> clientHandle.addAll(List.of("new_stage")));
        assertClientMutationRejected(() -> clientHandle.addAll(List.of("new_stage"), StageNotifyOptions.SILENT));
        assertClientMutationRejected(() -> clientHandle.grantTemporary("new_stage", 20L));
        assertClientMutationRejected(() -> clientHandle.grantTemporary("new_stage", 20L, StageNotifyOptions.SILENT));
        assertClientMutationRejected(() -> clientHandle.remove("iron_age"));
        assertClientMutationRejected(() -> clientHandle.remove("iron_age", StageNotifyOptions.SILENT));
        assertClientMutationRejected(() -> clientHandle.removeAll(List.of("iron_age")));
        assertClientMutationRejected(() -> clientHandle.removeAll(List.of("iron_age"), StageNotifyOptions.SILENT));
        assertClientMutationRejected(() -> clientHandle.set("new_stage", true));
        assertClientMutationRejected(() -> clientHandle.toggle("iron_age"));
        assertClientMutationRejected(clientHandle::clear);
        assertClientMutationRejected(() -> clientHandle.replace(List.of("new_stage")));
        assertClientMutationRejected(clientHandle::sync);

        StagesBinding clientBinding = new StagesBinding(ScriptType.CLIENT);
        assertTrue(clientBinding.clientPlayer().has("iron_age"));
        assertTrue(clientBinding.clientServer().has("world_tier_1"));
        assertClientMutationRejected(() -> clientBinding.server().add("illegal_client_write"));

        StageHandle nullBackedClient = StageHandle.clientReadOnly(null);
        assertFalse(nullBackedClient.has("iron_age"));
        assertEquals(List.of(), nullBackedClient.getAll());
        assertClientMutationRejected(() -> nullBackedClient.add("iron_age"));
    }

    private static void assertClientMutationRejected(Runnable mutation) {
        IllegalStateException ex = assertThrows(IllegalStateException.class, mutation::run);
        assertTrue(ex.getMessage().contains("[NEKO-4070]"), ex.getMessage());
    }

    private static final class RecordingHooks implements StagePlatformHooks {
        int fullSyncCount;
        final List<String> playerDeltas = new ArrayList<>();
        final List<String> serverDeltas = new ArrayList<>();

        @Override
        public void syncFullToPlayer(
                String playerUuid,
                Set<String> playerPermanent,
                Map<String, Long> playerTemporary,
                Set<String> serverPermanent,
                Map<String, Long> serverTemporary
        ) {
            fullSyncCount++;
        }

        @Override
        public void syncPlayerDelta(String playerUuid, String action, @Nullable String stage, long remainingTicks) {
            playerDeltas.add(playerUuid + ":" + action + ":" + stage + ":" + remainingTicks);
        }

        @Override
        public void syncServerDelta(
                String action,
                @Nullable String stage,
                long remainingTicks,
                Set<String> fullServerPermanent,
                Map<String, Long> fullServerTemporary
        ) {
            serverDeltas.add(action + ":" + stage + ":" + remainingTicks);
        }

        @Override
        public void notifyStageChange(
                String scope,
                @Nullable String playerUuid,
                String stage,
                boolean granted,
                @Nullable StageDefinition definition,
                @Nullable StageNotifyOptions options
        ) {
        }

        @Override
        public @Nullable String resolveOnlinePlayerName(String playerUuid) {
            return "Alex";
        }
    }
}
