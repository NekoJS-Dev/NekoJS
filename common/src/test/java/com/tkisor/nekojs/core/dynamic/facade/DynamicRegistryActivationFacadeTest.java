package com.tkisor.nekojs.core.dynamic.facade;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.dynamic.plan.DynamicAdapterRequest;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;
import com.tkisor.nekojs.core.dynamic.txn.DynamicRegistryAdapter;
import com.tkisor.nekojs.core.dynamic.txn.DynamicRegistryTransactionCoordinator;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncMessage;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncTransport;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ticket 21 facade integration (real {@link ScriptManager} transactional reload +
 * real GraalJS, same harness as ticket 16): the activation engine bound to
 * {@link DynamicRegistryFacadeRuntime} turns committed declaration batches into
 * batch transactions, while every candidate failure keeps the ticket 16 joint
 * boundary semantics — nothing is staged, the previously activated state keeps
 * serving. Candidate script failure (thrown guest exception, and watchdog kills
 * via the same {@code noteCollectionError} poison path) never publishes, hence
 * never stages.
 */
class DynamicRegistryActivationFacadeTest {

    /** Adapter double: the only "surgery" observable from the common layer. */
    private static final class RecordingAdapter implements DynamicRegistryAdapter {
        final List<List<DynamicAdapterRequest>> activateCalls = new ArrayList<>();
        final Map<String, String> live = new LinkedHashMap<>();

        @Override
        public void prepareActivation(List<DynamicAdapterRequest> requests) {}

        @Override
        public void activate(List<DynamicAdapterRequest> requests) {
            activateCalls.add(List.copyOf(requests));
            requests.forEach(request -> live.put(request.registryKey() + "|" + request.id(), request.fingerprint()));
        }

        @Override
        public void rollbackActivation(List<DynamicAdapterRequest> requests) {}
    }

    /** Transport double with a fixed participant list; records every message. */
    private static final class RecordingTransport implements DynamicSyncTransport {
        final List<String> participants;
        final List<DynamicSyncMessage> sent = new ArrayList<>();

        RecordingTransport(String... participants) {
            this.participants = List.of(participants);
        }

        @Override
        public List<String> participants() {
            return participants;
        }

        @Override
        public void send(String participantId, DynamicSyncMessage message) {
            sent.add(message);
        }

        @Override
        public void broadcast(DynamicSyncMessage message) {
            sent.add(message);
        }
    }

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized(TestPlatformInit.uniqueGameDir("nekojs-dynamic-registry-activation"));
    }

    @BeforeEach
    @AfterEach
    void cleanScriptDir() throws Exception {
        for (ScriptType type : List.of(ScriptType.SERVER, ScriptType.CLIENT)) {
            Path dir = com.tkisor.nekojs.script.ScriptTypeEnv.scriptsDir(type);
            if (dir == null) continue;
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                for (Path path : stream.toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private RecordingAdapter adapter;
    private RecordingTransport transport;

    private void bind(FacadeTestHarness harness) {
        harness.facade.bindActivationEngine(adapter, transport, 10_000L, () -> 0L);
    }

    @Test
    void boundEngineActivatesCommittedBatchesAndKeepsTicket16LedgerSemantics() throws Exception {
        adapter = new RecordingAdapter();
        transport = new RecordingTransport();
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            bind(harness);
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16; b.rarity = 'epic' });
                        event.soundEvent('mymod:boom', b => { b.setFixedRange(16) });
                    });
                    """);
            harness.manager.discoverScripts();
            harness.manager.loadScripts();

            var initial = harness.facade.collectInitial("server-registry-ready");
            assertTrue(initial.published(), initial.failureDetail());
            DynamicRegistryTransactionCoordinator engine = harness.facade.activationEngine();
            assertNotNull(engine);
            assertEquals(1, engine.activatedGeneration(), "initial batch activates (no participants)");
            assertEquals(2, adapter.live.size());
            String rubyFingerprint = adapter.live.get("minecraft:item|mymod:ruby");

            // reload without the sound event: new batch commits, stale marking stays
            // ledger-only (no physical delete), ruby's activation is idempotent
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16; b.rarity = 'epic' });
                    });
                    """);
            harness.manager.reloadScripts();
            harness.facade.pumpActivation();

            assertEquals(2, engine.activatedGeneration(), "the reload batch activates after its commit");
            assertEquals(2, adapter.activateCalls.size(), "each committed batch activates exactly once");
            assertEquals(List.of("mymod:boom"),
                    harness.facade.store().staleIds(DynamicDefinitionType.SOUND_EVENT),
                    "ticket 16 stale marking still holds with the engine bound");
            assertEquals(rubyFingerprint, adapter.live.get("minecraft:item|mymod:ruby"),
                    "same definition re-declaration keeps the same live fingerprint");
            assertTrue(adapter.live.containsKey("minecraft:sound_event|mymod:boom"),
                    "stale entries are not physically deleted from the live state");
        }
    }

    @Test
    void poisonedCandidateReloadStagesNothingAndOldActiveKeepsServing() throws Exception {
        adapter = new RecordingAdapter();
        transport = new RecordingTransport();
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            bind(harness);
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16 });
                    });
                    """);
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            harness.facade.collectInitial("server-registry-ready");
            DynamicRegistryTransactionCoordinator engine = harness.facade.activationEngine();
            assertEquals(1, engine.activatedGeneration());
            Map<String, String> oldLive = Map.copyOf(adapter.live);
            int sentBeforeFailure = transport.sent.size();

            // candidate script failure: the joint reload boundary fails, the batch is
            // never published, hence never staged — the old active state keeps serving
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:sapphire', b => { b.setMaxStackSize(8) });
                        throw new Error('collection boom');
                    });
                    """);
            assertThrows(NekoReloadException.class, harness.manager::reloadScripts);

            assertEquals(0, engine.queuedBatchCount(), "a failed candidate stages nothing");
            assertEquals(1, engine.activatedGeneration(), "the activated watermark does not advance");
            assertEquals(oldLive, adapter.live, "old active state keeps serving");
            assertEquals(DynamicRegistryTransactionCoordinator.Status.COMMITTED, engine.status());
            assertEquals(sentBeforeFailure, transport.sent.size(),
                    "no protocol message leaves the server for a failed candidate");
        }
    }

    @Test
    void sameKeyChangedDefinitionFailsReloadAndOldActiveKeepsServing() throws Exception {
        adapter = new RecordingAdapter();
        transport = new RecordingTransport();
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            bind(harness);
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16 });
                    });
                    """);
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            harness.facade.collectInitial("server-registry-ready");
            DynamicRegistryTransactionCoordinator engine = harness.facade.activationEngine();
            assertEquals(1, engine.activatedGeneration());
            Map<String, String> oldLive = Map.copyOf(adapter.live);
            int sentBeforeConflict = transport.sent.size();

            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 32 });
                    });
                    """);
            assertThrows(NekoReloadException.class, harness.manager::reloadScripts);

            assertEquals(0, engine.queuedBatchCount(), "the conflicted batch is never staged");
            assertEquals(1, engine.activatedGeneration(), "no mixed generation: the watermark does not advance");
            assertEquals(oldLive, adapter.live, "the old definition keeps serving — no silent overwrite");
            assertEquals(sentBeforeConflict, transport.sent.size());
        }
    }

    @Test
    void clearActivationEngineAbortsInFlightWorkAtCloseBoundary() throws Exception {
        adapter = new RecordingAdapter();
        transport = new RecordingTransport("alice");
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            bind(harness);
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16 });
                    });
                    """);
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            harness.facade.collectInitial("server-registry-ready");

            DynamicRegistryTransactionCoordinator engine = harness.facade.activationEngine();
            assertEquals(DynamicRegistryTransactionCoordinator.Status.AWAITING_ACK, engine.status(),
                    "with a remote participant the initial batch waits for the ack");
            assertEquals(0, adapter.activateCalls.size(), "nothing is live while the ack is pending");

            harness.facade.clearActivationEngine("server-stopping");
            assertEquals(DynamicRegistryTransactionCoordinator.Status.ABORTED, engine.status());
            assertNull(harness.facade.activationEngine(), "the engine is dropped (server stopped)");
            assertEquals(0, adapter.activateCalls.size(), "nothing commits across a close boundary");
            assertEquals(1, transport.sent.stream()
                    .filter(m -> m.kind() == DynamicSyncMessage.Kind.ABORT).count(),
                    "participants are told to discard their staged prepare");
        }
    }

    @Test
    void unboundEngineKeepsTheInertTicket16Surface() throws Exception {
        adapter = new RecordingAdapter();
        transport = new RecordingTransport();
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16 });
                    });
                    """);
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            var initial = harness.facade.collectInitial("server-registry-ready");

            assertTrue(initial.published(), initial.failureDetail());
            assertNull(harness.facade.activationEngine(), "unbound = inert local plans only");
            assertEquals(1, harness.facade.store().committedGeneration(),
                    "the declaration ledger still commits (ticket 16 behavior unchanged)");
            assertTrue(adapter.live.isEmpty(), "no activation happens without a bound engine");
        }
    }
}
