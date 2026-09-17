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
 * 票 16 AC1/AC7 的 inert 候选行为（真实 GraalJS + 事务式 reload + 候选域收集器挂入
 * {@code ScriptManager}）：
 * <ul>
 *   <li>成功 reload 的候选期收集<b>可观察</b>（{@link DynamicRegistryFacadeRuntime#lastCandidateCollection()}）
 *       且只在 commit 点发布；</li>
 *   <li>失败候选既<b>不挂生产 callback</b>（毒化批次的监听器从未上总线）也<b>不写账本</b>
 *       （store 零写入、旧 active 继续服务、修正后可再成功）；</li>
 *   <li>候选期收集器自身崩坏按 {@code domain-plan-collection:<domain>} 归因为候选失败
 *       （不静默降级成空计划）；</li>
 *   <li>域参与范围：SERVER-only（CLIENT 候选跳过）、未使用域零参与。</li>
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
            // 旧 active generation 根本没有本域监听器：失败候选若提前挂上总线可被直接观察到
            harness.writeScript("main.js", "console.log('no dynamic registry usage')");
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            long generationBefore = harness.manager.generationId();
            assertFalse(DynamicRegistryEvents.DYNAMIC_REGISTRY.hasListeners(), "前置：生产总线无本域监听器");

            // 候选：一条合法定义 + 一条非法 id（毒化整批）
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

            // 修正后可再次成功 reload（候选失败不留持久损伤）
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
        // SERVER：脚本不使用本域 → 候选不参与、不挂空计划
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            harness.writeScript("main.js", "console.log('unused')");
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            harness.manager.reloadScripts();
            assertEquals("skipped-unused-domain", harness.facade.lastCandidateCollection().note());
            assertFalse(harness.facade.lastCandidateCollection().participated());
            assertTrue(harness.facade.store().isEmpty());
        }
        // CLIENT：SERVER-only 域绝不被调用（票 39/16 统一接缝后由 reload 管线按收集器声明的
        // scriptType 在调用前过滤——连内部「跳过」记录都不会产生，比原内部守卫更强），
        // 绝不因其它类型 reload 触碰账本
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
            // claim/stale/mode 经 Registry Runtime 观察面可读，且 stale 项仍参与冲突检测
            var claim = harness.facade.store().claimOf(DynamicDefinitionType.ITEM, "mymod:gone");
            assertTrue(claim.stale(), "stale 可观察");
            assertTrue(claim.ownerScriptId().contains("main.js"),
                    "owner（声明脚本 id）可观察: " + claim.ownerScriptId());
            assertTrue(harness.facade.store().trackedClaims().contains("item:mymod:gone"),
                    "tracked 账（stale 含）可观察: " + harness.facade.store().trackedClaims());
        }
    }
}
