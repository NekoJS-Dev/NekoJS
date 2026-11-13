package com.tkisor.nekojs.core.dynamic.facade;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inert candidate behavior with real GraalJS, a transactional reload, and the candidate domain collector
 * plugged into {@code ScriptManager}:
 * <ul>
 *   <li>A successful reload makes the candidate collection <b>observable</b> through
 *       {@link DynamicRegistryFacadeRuntime#lastCandidateCollection()}, and it is published only at the
 *       commit point.</li>
 *   <li>A failed candidate <b>never attaches its production callback</b> (the listeners of a poisoned batch
 *       never reach the bus) and <b>never writes the ledger</b>: the store is untouched, the previous active
 *       state keeps serving, and a corrected reload succeeds again.</li>
 *   <li>A crash inside the candidate collector is attributed to the candidate failure as
 *       {@code domain-plan-collection:<domain>} instead of silently degrading to an empty plan.</li>
 *   <li>Domain participation is SERVER-only (CLIENT candidates are skipped), and an unused domain takes no
 *       part.</li>
 * </ul>
 */
class DynamicRegistryCandidateInertnessTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized(
                TestPlatformInit.uniqueGameDir("nekojs-dynamic-registry-inertness"));
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

    @Test
    void successfulReloadCollectsInTheCandidatePhaseAndPublishesOnlyAtCommit() throws Exception {
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16 });
                    });
                    """);
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            var initial = harness.facade.collectInitial("server-registry-ready");
            assertTrue(initial.published(), "初次候选发布: " + initial.failureDetail());
            assertNull(harness.facade.lastCandidateCollection(),
                    "初次收集不是 reload 候选期收集（两条收集入口的记录分开）");

            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16 });
                        event.soundEvent('mymod:boom', b => { b.setFixedRange(16) });
                    });
                    """);
            harness.manager.reloadScripts();

            var record = harness.facade.lastCandidateCollection();
            assertNotNull(record, "reload 候选期收集可观察");
            assertTrue(record.participated(), "本域实际收集并挂入计划");
            assertEquals("collected", record.note());
            assertEquals(2, record.collectedDefinitions(), "候选期收到两条定义（不是从 live 总线收的）");
            assertFalse(record.poisoned());
            assertEquals(2L, harness.facade.store().committedGeneration(),
                    "成功 commit 才发布：批次从初次（1）推进到本次候选（2）");
            assertTrue(DynamicRegistryEvents.DYNAMIC_REGISTRY.hasListeners(),
                    "commit 后本批监听器成为新 generation 的 live callback");
            assertNotNull(harness.facade.store().exposedEntry("minecraft:sound_event|mymod:boom"));
        }
    }

    @Test
    void poisonedCandidateNeverAttachesItsListenerAndNeverWritesTheLedger() throws Exception {
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            // The previous active generation has no listener for this domain, so a failed candidate that reached the bus
            // early would be directly observable.
            harness.writeScript("main.js", "console.log('no dynamic registry usage')");
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            long generationBefore = harness.manager.generationId();
            assertFalse(DynamicRegistryEvents.DYNAMIC_REGISTRY.hasListeners(), "前置：生产总线无本域监听器");

            // Candidate: one valid definition plus one invalid id, which poisons the whole batch.
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:good', b => { b.maxStackSize = 16 });
                        event.item('MyMod:Bad', b => { b.maxStackSize = 16 });
                    });
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class, harness.manager::reloadScripts);
            assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase());
            assertEquals("dynamic-registry-collection", failure.report().domain());

            assertFalse(DynamicRegistryEvents.DYNAMIC_REGISTRY.hasListeners(),
                    "失败候选的监听器从未挂上生产总线（不挂生产 callback，AC7）");
            assertEquals(generationBefore, harness.manager.generationId(), "失败候选不切换 active generation");
            assertTrue(harness.facade.store().isEmpty(), "失败候选零写入：账本空");
            assertEquals(-1L, harness.facade.store().committedGeneration(), "从未发布任何批次");
            var record = harness.facade.lastCandidateCollection();
            assertTrue(record.participated() && record.poisoned(),
                    "毒化批次仍被观察到（失败清理＝计划不可达，不是静默丢弃）");
            assertTrue(record.failureDetail().contains("MyMod:Bad"), record.failureDetail());

            // A corrected script reloads successfully again; a candidate failure leaves no lasting damage.
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:good', b => { b.maxStackSize = 16 });
                    });
                    """);
            harness.manager.reloadScripts();
            assertEquals(1, harness.facade.store().lastCommittedBatch().size(), "修正后成功发布");
            assertTrue(DynamicRegistryEvents.DYNAMIC_REGISTRY.hasListeners(), "新 generation 接管 live callback");
        }
    }

    @Test
    void collectorCrashIsAttributedToTheDomainCollectionPhaseAndDiscardsTheCandidate() throws Exception {
        CandidateDomainCollector exploding = new CandidateDomainCollector() {
            @Override
            public String domain() {
                return "test-domain";
            }

            @Override
            public ScriptType scriptType() {
                return ScriptType.SERVER;
            }

            @Override
            public void collect(Handle handle) {
                throw new IllegalStateException("collector exploded");
            }
        };
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER, List.of(exploding))) {
            harness.writeScript("main.js", "console.log('fail the candidate via collector crash')");
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            long generationBefore = harness.manager.generationId();

            NekoReloadException failure = assertThrows(NekoReloadException.class, harness.manager::reloadScripts);
            assertEquals(ReloadPhase.DOMAIN_PLAN, failure.report().phase(),
                    "收集器崩坏发生在 DOMAIN_PLAN 阶段（候选期）");
            assertEquals("domain-collect:test-domain", failure.report().domain(),
                    "收集器自身失败按域归因，不降级为静默空计划");
            assertEquals(generationBefore, harness.manager.generationId(), "候选失败不切换 active generation");
            assertTrue(harness.facade.store().isEmpty(), "失败候选零写入");
        }
    }

    @Test
    void domainParticipationIsServerOnlyAndSkippedWhenUnused() throws Exception {
        // SERVER: the script does not use this domain, so the candidate does not participate and no empty plan is
        // attached.
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            harness.writeScript("main.js", "console.log('unused')");
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            harness.manager.reloadScripts();
            assertEquals("skipped-unused-domain", harness.facade.lastCandidateCollection().note());
            assertFalse(harness.facade.lastCandidateCollection().participated());
            assertTrue(harness.facade.store().isEmpty());
        }
        // CLIENT: a SERVER-only domain is never called. The reload pipeline filters by the scriptType the collector
        // declares before the call, so not even an internal "skipped" record is produced. The ledger is never
        // touched by a reload of another type.
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.CLIENT)) {
            harness.writeScript("client.js", "console.log('client reload')");
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            harness.manager.reloadScripts();
            assertNull(harness.facade.lastCandidateCollection(),
                    "非 SERVER 候选上收集器不被调用（管线按声明 scriptType 过滤）");
            assertTrue(harness.facade.store().isEmpty());
        }
    }

    @Test
    void staleMarkingIsNotAPhysicalDeletionAndKeepsTheConflictLedger() throws Exception {
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16 });
                        event.item('mymod:gone', b => { b.maxStackSize = 32 });
                    });
                    """);
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            harness.facade.collectInitial("server-registry-ready");
            String goneFingerprint = harness.facade.store()
                    .exposedEntry("minecraft:item|mymod:gone").definition().fingerprint();

            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16 });
                    });
                    """);
            harness.manager.reloadScripts();

            assertEquals(List.of("mymod:gone"), harness.facade.store().staleIds(DynamicDefinitionType.ITEM),
                    "脚本不再声明的项标记 stale/retired");
            assertEquals(List.of("minecraft:item|mymod:gone"), harness.facade.store().retiredKeys());
            assertEquals(goneFingerprint, harness.facade.store()
                            .exposedEntry("minecraft:item|mymod:gone").definition().fingerprint(),
                    "普通 reload 不物理删除：定义与指纹保留");
            // claim, stale, and mode are readable through the Registry Runtime view, and a stale entry still takes part
            // in conflict detection.
            var claim = harness.facade.store().claimOf(DynamicDefinitionType.ITEM, "mymod:gone");
            assertTrue(claim.stale(), "stale 可观察");
            assertTrue(claim.ownerScriptId().contains("main.js"),
                    "owner（声明脚本 id）可观察: " + claim.ownerScriptId());
            assertTrue(harness.facade.store().trackedClaims().contains("item:mymod:gone"),
                    "tracked 账（stale 含）可观察: " + harness.facade.store().trackedClaims());
        }
    }
}
