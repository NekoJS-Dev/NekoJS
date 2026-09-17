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
 * 票 16 AC12：隔离测试 harness 可运行的候选计划 fixture——演示类型直达入口、
 * setter/property parity、同 key 冲突与 stale 查询。
 *
 * <p>示例脚本与
 * {@code docs/architecture-refactor/baseline/2026-09-16-registry-dynamic-local/examples/dynamic-registry-inert-plan.js}
 * 保持一致，经真实 GraalJS + 事务式 reload 管线跑通。<b>仅本地 inert 计划、尚未公开激活</b>
 * ——不作为生产脚本使用指南；生产示例与迁移材料归票 21（事务/同步 gate 通过后发布）。
 */
class DynamicRegistryInertPlanExampleTest {

    /** 与 baseline examples/dynamic-registry-inert-plan.js 的脚本段一致（不含注释头）。 */
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
            // ---- 场景 1+2：类型直达入口 + setter/property parity ----
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

            // 重复 reload 相同定义：同 fingerprint → 重 claim，不冲突
            harness.manager.reloadScripts();
            assertEquals(4, harness.facade.store().lastCommittedBatch().size(), "同定义重复 reload 幂等");
            assertEquals(2, harness.facade.store().committedGeneration(),
                    "store 批次 generation 随每次成功 commit 递增（初次=1，本次 reload=2）");

            // ---- 场景 2b：setter/property parity（同一 id、同一定义、不同写法） ----
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

            // ---- 场景 3：同 key 冲突（第一版整批失败，旧 active 继续服务） ----
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

            // ---- 场景 4：stale 查询（脚本不再声明 → stale/retired，不物理删除） ----
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
            // 批次号：初次 1 → 幂等 2 → parity 3 → 冲突轮 4（未提交）→ 本轮 5
            assertEquals(5, harness.facade.store().committedGeneration(),
                    "store 批次 generation 只随成功 commit 推进（冲突轮未推进 committed）");
        }
    }
}
