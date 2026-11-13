package com.tkisor.nekojs.core.dynamic.facade;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.script.ScriptManager;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reload pipeline fixture with a real {@link ScriptManager} transactional reload, real GraalJS, the
 * production event group binding, and the candidate domain collector wired into
 * {@code ScriptManager}:
 * <ul>
 *   <li>the initial candidate (collectInitial, triggered by server registry ready in production)
 *       publishes the plan;</li>
 *   <li>a reload collects again in the <b>candidate phase</b> (the collector consumes the candidate
 *       pending listeners and never touches the production bus); preflight runs and the plan is
 *       published together at commit, so only a successful commit publishes it;</li>
 *   <li>a changed definition for the same key fails the whole batch (ReloadPhase.STATE_PLAN with
 *       domain=dynamic-registry-conflict); the old active definitions keep serving and the store is
 *       not written;</li>
 *   <li>a collection error takes the same failure path; a CLIENT reload leaves the SERVER domain
 *       ledger untouched.</li>
 * </ul>
 */
class DynamicRegistryReloadPipelineTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized(
                TestPlatformInit.uniqueGameDir("nekojs-dynamic-registry-reload"));
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
    void serverRegistryReadyFiresInitialCandidateAndReloadRecollectsInCandidatePhase() throws Exception {
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16; b.rarity = 'epic' });
                        event.soundEvent('mymod:boom', b => { b.setFixedRange(16) });
                    });
                    """);
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            // Initial candidate, triggered by server registry ready (ServerEventListener.onServerAboutToStart in production).
            var initial = harness.facade.collectInitial("server-registry-ready");
            assertTrue(initial.published(), "初次候选发布: " + initial.failureDetail());
            assertEquals(2, harness.facade.store().lastCommittedBatch().size());
            String rubyFingerprint =
                    harness.facade.store().exposedEntry("minecraft:item|mymod:ruby").definition().fingerprint();

            // Reload: collect again in the candidate phase (the collector consumes the candidate listeners),
            // then publish everything together at commit.
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16; b.rarity = 'epic' });
                    });
                    """);
            harness.manager.reloadScripts();

            assertEquals(2, harness.facade.store().committedGeneration(),
                    "成功 commit 才发布：store 批次从初次（1）推进到本次 reload（2）");
            assertEquals(rubyFingerprint,
                    harness.facade.store().exposedEntry("minecraft:item|mymod:ruby").definition().fingerprint(),
                    "同定义重复 reload：fingerprint 不变（AC5）");
            assertEquals(List.of("mymod:boom"),
                    harness.facade.store().staleIds(DynamicDefinitionType.SOUND_EVENT),
                    "脚本不再声明的 boom 标记 stale（不物理删除）");
            assertFalse(harness.facade.store().claimOf(DynamicDefinitionType.ITEM, "mymod:ruby").stale(),
                    "重声明的 ruby claim 刷新");
        }
    }

    @Test
    void sameKeyChangeFailsTheWholeReloadAndOldActiveKeepsServing() throws Exception {
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16 });
                    });
                    """);
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            harness.facade.collectInitial("server-registry-ready");
            long generationBefore = harness.manager.generationId();
            String oldFingerprint =
                    harness.facade.store().exposedEntry("minecraft:item|mymod:ruby").definition().fingerprint();
            long committedBefore = harness.facade.store().committedGeneration();

            // Changed definition: the reload must fail with STATE_PLAN / dynamic-registry-conflict.
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 32 });
                    });
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class, harness.manager::reloadScripts);
            assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(), "冲突在候选 STATE_PLAN 阶段失败");
            assertEquals("dynamic-registry-conflict", failure.report().domain(), "domain 精确归因");

            // The old active definitions keep serving: the generation stays, the store is not written, claims are untouched.
            assertEquals(generationBefore, harness.manager.generationId(), "失败 reload 不切换 active generation");
            assertEquals(committedBefore, harness.facade.store().committedGeneration(), "失败批次不发布");
            assertEquals(oldFingerprint,
                    harness.facade.store().exposedEntry("minecraft:item|mymod:ruby").definition().fingerprint(),
                    "旧 active 定义继续服务");
            assertFalse(harness.facade.store().claimOf(DynamicDefinitionType.ITEM, "mymod:ruby").stale(),
                    "失败不把旧 active 标 stale");

            // After fixing the script the reload succeeds again: the old active definitions were still usable, so a
            // failed candidate left no lasting damage.
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16 });
                    });
                    """);
            harness.manager.reloadScripts();
            // The failed round consumed batch number 2 with beginBatch but never committed; the fixed round commits batch 3.
            assertEquals(committedBefore + 2, harness.facade.store().committedGeneration(),
                    "修正后 reload 成功并发布（失败轮只耗号不提交）");
            assertEquals(generationBefore + 1, harness.manager.generationId(),
                    "manager generation 恰好推进一位（失败轮未切换 active）");
        }
    }

    @Test
    void collectionErrorFailsTheCandidateWithoutAnyWrite() throws Exception {
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ok', b => { b.maxStackSize = 16 });
                    });
                    """);
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            harness.facade.collectInitial("server-registry-ready");
            long committedBefore = harness.facade.store().committedGeneration();

            // The candidate script holds an invalid definition (category out of range): collecting poisons the
            // candidate, STATE_PLAN fails and nothing is written.
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ok', b => { b.maxStackSize = 16 });
                        event.mobEffect('mymod:bad', b => { b.category('scary') });
                    });
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class, harness.manager::reloadScripts);
            assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase());
            assertEquals("dynamic-registry-collection", failure.report().domain(),
                    "收集错误归因到 collection domain: " + failure.report().describe());
            assertEquals(committedBefore, harness.facade.store().committedGeneration(),
                    "失败不另起写入：store 停留在上一成功批次");
            assertFalse(harness.facade.store().exposedSnapshot().containsKey("minecraft:mob_effect|mymod:bad"),
                    "非法定义没有进入账本");
        }
    }

    @Test
    void clientTypeReloadDoesNotTouchTheServerDomainLedger() throws Exception {
        try (FacadeTestHarness server = new FacadeTestHarness(ScriptType.SERVER);
                FacadeTestHarness client = new FacadeTestHarness(ScriptType.CLIENT)) {
            server.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.soundEvent('mymod:boom', b => { });
                    });
                    """);
            server.manager.discoverScripts();
            server.manager.loadScripts();
            server.facade.collectInitial("server-registry-ready");
            long committed = server.facade.store().committedGeneration();

            // A CLIENT reload: the collector must skip it, because the domain is SERVER-only, and the ledger is untouched.
            client.writeScript("client.js", "console.log('client reload')");
            client.manager.discoverScripts();
            client.manager.loadScripts();
            client.manager.reloadScripts();

            assertEquals(committed, server.facade.store().committedGeneration(),
                    "CLIENT reload 不发布 SERVER 域计划");
            assertFalse(server.facade.store()
                    .claimOf(DynamicDefinitionType.SOUND_EVENT, "mymod:boom").stale(),
                    "CLIENT reload 不把 SERVER 声明标 stale");
            assertNotNull(server.facade.store().exposedEntry("minecraft:sound_event|mymod:boom"));
        }
    }

    @Test
    void unusedFacadeReloadsAttachNoPlan() throws Exception {
        // The facade was never used: no listeners and an empty ledger, so a reload attaches no empty plan and does
        // not fail either.
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            harness.writeScript("main.js", "console.log('no facade usage')");
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            harness.manager.reloadScripts();
            assertEquals(-1, harness.facade.store().committedGeneration(),
                    "未使用域零参与（不发布空计划、不失败）");
            assertTrue(harness.facade.store().isEmpty());
        }
    }

    @Test
    void facadeCollectorParticipatesThroughTheConstructorSuppliedSeam() throws Exception {
        // The collector is the reload hook the root owns, the only form since the two were unified: only a
        // collector passed through the ScriptManager constructor (registered by the root at assembly in
        // production) takes part in collecting candidates. The process-level static registry is gone, so a
        // domain never joins an unrelated manager just because something registered it somewhere.
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('nekojs:seam_probe', b => { b.maxStackSize = 16 });
                    });
                    """);
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            harness.manager.reloadScripts();
            assertEquals(1, harness.facade.store().lastCommittedBatch().size(),
                    "构造器传入的收集器参与候选收集并联合发布");
        }
    }
}
