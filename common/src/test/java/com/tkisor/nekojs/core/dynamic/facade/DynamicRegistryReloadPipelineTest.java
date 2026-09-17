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
 * 票 16 AC1/AC7 的 reload 管线 fixture（真实 {@link ScriptManager} 事务式 reload +
 * 真实 GraalJS + 生产同款事件组绑定 + 候选域收集器挂入 {@code ScriptManager}）：
 * <ul>
 *   <li>初次候选（collectInitial，生产＝server registry ready 触发）发布计划；</li>
 *   <li>reload 在<b>候选阶段</b>重新收集（收集器消费候选挂起监听器，不上生产总线），
 *       preflight 通过、commit 点联合发布——成功 commit 才发布计划；</li>
 *   <li>同 key 定义变化 → 整批冲突失败（ReloadPhase.STATE_PLAN / domain=
 *       dynamic-registry-conflict），旧 active 继续服务、store 零写入；</li>
 *   <li>收集错误 → 同一失败路径；CLIENT 类型 reload 不触碰 SERVER 域账本。</li>
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
            // 初次候选：server registry ready 触发（生产＝ServerEventListener.onServerAboutToStart）
            var initial = harness.facade.collectInitial("server-registry-ready");
            assertTrue(initial.published(), "初次候选发布: " + initial.failureDetail());
            assertEquals(2, harness.facade.store().lastCommittedBatch().size());
            String rubyFingerprint =
                    harness.facade.store().exposedEntry("minecraft:item|mymod:ruby").definition().fingerprint();

            // reload：候选阶段重新收集（收集器消费候选监听器）→ commit 点联合发布
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

            // 变更定义：reload 必须以 STATE_PLAN / dynamic-registry-conflict 失败
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 32 });
                    });
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class, harness.manager::reloadScripts);
            assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(), "冲突在候选 STATE_PLAN 阶段失败");
            assertEquals("dynamic-registry-conflict", failure.report().domain(), "domain 精确归因");

            // 旧 active 继续服务：generation 不变、store 零写入、claim 不被动
            assertEquals(generationBefore, harness.manager.generationId(), "失败 reload 不切换 active generation");
            assertEquals(committedBefore, harness.facade.store().committedGeneration(), "失败批次不发布");
            assertEquals(oldFingerprint,
                    harness.facade.store().exposedEntry("minecraft:item|mymod:ruby").definition().fingerprint(),
                    "旧 active 定义继续服务");
            assertFalse(harness.facade.store().claimOf(DynamicDefinitionType.ITEM, "mymod:ruby").stale(),
                    "失败不把旧 active 标 stale");

            // 修正脚本后可以再次成功 reload（旧 active 仍可用，候选失败不留持久损伤）
            harness.writeScript("main.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                        event.item('mymod:ruby', b => { b.maxStackSize = 16 });
                    });
                    """);
            harness.manager.reloadScripts();
            // 失败轮 beginBatch 消耗了批次号 2（未提交）；修正轮提交批次 3
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

            // 候选脚本带非法定义（category 越界）：候选收集毒化 → STATE_PLAN 失败 → 零写入
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

            // CLIENT 类型的 reload：收集器必须跳过（SERVER-only 域），账本不被触碰
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
        // facade 从未被使用（无监听器、账本为空）：reload 不挂空计划，也不失败
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
    void collectorRegistryIsObservableAndSymmetric() {
        // seam 自身的可观察面：注册/注销幂等对称（测试隔离与后续域停用用）
        DynamicRegistryFacadeRuntime isolated = new DynamicRegistryFacadeRuntime();
        try {
            ScriptManager.registerCandidateDomainCollector(isolated);
            ScriptManager.registerCandidateDomainCollector(isolated);
            assertEquals(1, ScriptManager.candidateDomainCollectors().stream()
                    .filter(collector -> collector == isolated).count(), "幂等注册");
        } finally {
            ScriptManager.unregisterCandidateDomainCollector(isolated);
            assertTrue(ScriptManager.candidateDomainCollectors().stream()
                    .noneMatch(collector -> collector == isolated), "注销对称");
        }
    }
}
