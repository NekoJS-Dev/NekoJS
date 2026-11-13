package com.tkisor.nekojs.core.dynamic.facade;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Candidate plan fixture that runs on the isolated test harness: it demonstrates the typed entry
 * points, setter/property parity, same-key conflicts and stale queries.
 *
 * <p>The example script runs through real GraalJS and the transactional reload pipeline. It only
 * produces local inert plans and is not active in public, so it is not a guide for production
 * scripts; production examples and migration material ship after the transaction and sync gate
 * passes.
 */
class DynamicRegistryInertPlanExampleTest {

    /**
     * Matches the script section of the baseline examples/dynamic-registry-inert-plan.js, without
     * its comment header. Kept in sync by hand; see the sync notes in the class Javadoc.
     */
    static final String EXAMPLE_SCRIPT = """
            DynamicRegistryEvents.dynamicRegistry(event => {
              event.item('mymod:ruby', b => { b.maxStackSize = 16; b.rarity = 'epic' });
              event.item('mymod:sapphire', b => { b.setMaxStackSize(16).setRarity('rare') });
              event.soundEvent('mymod:boom', b => { b.setFixedRange(16) });
              event.mobEffect('mymod:wither_touch', b => { b.setCategory('harmful').setColor(0x8B0000) });
            });
            """;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized(
                TestPlatformInit.uniqueGameDir("nekojs-dynamic-registry-example"));
    }

    @BeforeEach
    @AfterEach
    void cleanScriptDir() throws Exception {
        Path dir = com.tkisor.nekojs.script.ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
        if (dir == null) return;
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void exampleRunsThroughTheIsolatedHarnessAndDemosAllFourScenarios() throws Exception {
        try (FacadeTestHarness harness = new FacadeTestHarness(ScriptType.SERVER)) {
            // ---- Scenarios 1 and 2: typed entry points and setter/property parity ----
            harness.writeScript("dynamic_registry.js", EXAMPLE_SCRIPT);
            harness.manager.discoverScripts();
            harness.manager.loadScripts();
            var initial = harness.facade.collectInitial("server-registry-ready");
            assertTrue(initial.published(), "示例初次收集发布: " + initial.failureDetail());
            assertEquals(4, harness.facade.store().lastCommittedBatch().size(), "四条声明全部入账");

            var ruby = harness.facade.store().exposedEntry("minecraft:item|mymod:ruby").definition();
            var sapphire = harness.facade.store().exposedEntry("minecraft:item|mymod:sapphire").definition();
            assertTrue(ruby.hasReading("maxStackSize", "16") && ruby.hasReading("rarity", "epic"));
            assertTrue(sapphire.hasReading("maxStackSize", "16") && sapphire.hasReading("rarity", "rare"),
                    "property 写入与显式 setter 读数一致（parity 经同一 setter）");
            assertTrue(harness.facade.store().exposedEntry("minecraft:mob_effect|mymod:wither_touch")
                    .definition().hasReading("color", "9109504"), "0x8B0000 = 9109504（ARGB int）");

            // Reloading the same definitions again gives the same fingerprints, so claims are renewed without conflict.
            harness.manager.reloadScripts();
            assertEquals(4, harness.facade.store().lastCommittedBatch().size(), "同定义重复 reload 幂等");
            assertEquals(2, harness.facade.store().committedGeneration(),
                    "store 批次 generation 随每次成功 commit 递增（初次=1，本次 reload=2）");

            // ---- Scenario 2b: setter/property parity (same id, same definition, different syntax) ----
            List<String> rubyPropertyReadings = ruby.readings();
            harness.writeScript("dynamic_registry.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                      event.item('mymod:ruby', b => { b.setMaxStackSize(16).setRarity('epic') });
                      event.item('mymod:sapphire', b => { b.maxStackSize = 16; b.rarity = 'rare' });
                      event.soundEvent('mymod:boom', b => { b.fixedRange = 16 });
                      event.mobEffect('mymod:wither_touch', b => { b.category = 'harmful'; b.color = 0x8B0000 });
                    });
                    """);
            harness.manager.reloadScripts();
            var rubyAfterSwap = harness.facade.store().exposedEntry("minecraft:item|mymod:ruby").definition();
            assertEquals(rubyPropertyReadings, rubyAfterSwap.readings(),
                    "两种写法产生同一份规范化读数（AC4；ruby 上轮 property、本轮显式 setter）");
            assertEquals(ruby.fingerprint(), rubyAfterSwap.fingerprint(),
                    "同 id 同定义换写法 ⇒ fingerprint 不变（重 claim，不冲突）");
            assertEquals(3, harness.facade.store().committedGeneration(), "parity 轮成功 commit");

            // ---- Scenario 3: same-key conflict (the whole batch fails, the old active definition keeps serving) ----
            harness.writeScript("dynamic_registry.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                      event.item('mymod:ruby', b => { b.maxStackSize = 32; b.rarity = 'epic' });
                      event.soundEvent('mymod:boom', b => { b.setFixedRange(16) });
                      event.item('mymod:sapphire', b => { b.setMaxStackSize(16).setRarity('rare') });
                      event.mobEffect('mymod:wither_touch', b => { b.setCategory('harmful').setColor(0x8B0000) });
                    });
                    """);
            NekoReloadException conflict = assertThrows(NekoReloadException.class, harness.manager::reloadScripts);
            assertEquals(ReloadPhase.STATE_PLAN, conflict.report().phase());
            assertEquals("dynamic-registry-conflict", conflict.report().domain());
            assertTrue(harness.facade.store().exposedEntry("minecraft:item|mymod:ruby")
                    .definition().hasReading("maxStackSize", "16"), "旧 active（16）继续服务");
            assertEquals(3, harness.facade.store().committedGeneration(), "冲突轮只耗批次号不提交");

            // ---- Scenario 4: stale query (the script no longer declares it, so it becomes stale/retired, not deleted) ----
            harness.writeScript("dynamic_registry.js", """
                    DynamicRegistryEvents.dynamicRegistry(event => {
                      event.item('mymod:ruby', b => { b.maxStackSize = 16; b.rarity = 'epic' });
                      event.item('mymod:sapphire', b => { b.setMaxStackSize(16).setRarity('rare') });
                      event.mobEffect('mymod:wither_touch', b => { b.setCategory('harmful').setColor(0x8B0000) });
                    });
                    """);
            harness.manager.reloadScripts();
            assertEquals(List.of("mymod:boom"), harness.facade.store().staleIds(DynamicDefinitionType.SOUND_EVENT),
                    "stale 查询：boom 不再声明");
            assertEquals(List.of("minecraft:sound_event|mymod:boom"), harness.facade.store().retiredKeys(),
                    "retired 查询可观察");
            assertFalse(harness.facade.store().claimOf(DynamicDefinitionType.MOB_EFFECT, "mymod:wither_touch")
                    .stale(), "仍声明的项 claim 刷新");
            // Batch numbers: initial 1, idempotent 2, parity 3, conflict round 4 (not committed), this round 5.
            assertEquals(5, harness.facade.store().committedGeneration(),
                    "store 批次 generation 只随成功 commit 推进（冲突轮未推进 committed）");
        }
    }
}
