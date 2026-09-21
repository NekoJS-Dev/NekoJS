//? if neoforge {
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 28 AC 贯穿 fixture（五节点共享同一份代码）：真实 CLIENT 脚本经
 * {@code ClientEvents.postEffects} 声明 register/unregister，候选期 inert，commit 点由平台
 * {@link PostEffectDomainOwner} Adapter 安装新 generation 并退役旧 generation。
 *
 * <p>覆盖：候选期 live 定义不变（AC2）、成功 commit 后新 generation 可回读（AC3）、
 * 旧 generation 的声明被退役且不再可见（AC3）、声明 JSON 无效整批失败保留旧 active
 * （AC4）、显式 unregister 与声明移除（AC4/AC3）、generation/stale 只读查询（AC3/AC7）、
 * 运行时 binding 未被声明事件替代（AC5）。
 *
 * <p>断言只走公开 seam：脚本、reload 成败、{@code owner.lastDiagnostics()}、
 * {@code PostEffectManager} 的只读查询面；不断言私有静态 Map。
 */
class PostEffectDeclarationLifecycleTest {

    private PostEffectDeclarationHarness harness;

    @BeforeAll
    static void initPlatform() {
        PostEffectDeclarationHarness.ensurePlatformInitialized();
    }

    @BeforeEach
    void setUp() throws Exception {
        PostEffectDeclarationHarness.clearClientScripts();
        PostEffectDeclarationHarness.resetDeclarations();
        harness = new PostEffectDeclarationHarness();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) {
            harness.close();
            harness = null;
        }
        PostEffectDeclarationHarness.resetDeclarations();
        PostEffectDeclarationHarness.clearClientScripts();
    }

    /**
     * A chain JSON payload valid for the running node: 26.x uses the modern
     * {@code PostChainConfig} shape (bare shader ids), 1.21.1 uses the legacy
     * {@code shaders/post} shape (program names). Both come from the production generator
     * ({@code PostEffectChainJson}), so the fixture exercises the real payload contract.
     */
    private static String chainJson(String shaderOrProgram) {
//? if >=26 {
        return com.tkisor.nekojs.core.posteffect.PostEffectChainJson
                .simpleBlitChainModern(shaderOrProgram, null);
//?} else {
/*        return com.tkisor.nekojs.core.posteffect.PostEffectChainJson
                .simpleBlitChainLegacy(shaderOrProgram);
*///?}
    }

    @Test
    void declaredEffectsInstallOnlyAtTheCommitPoint() throws Exception {
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:gray', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/invert")));

        harness.loadAndApplyInitialPlan();

        assertEquals(List.of("nekojs:gray"), PostEffectDeclarationHarness.installedIds(),
                "the initial generation collection point installs the declared definition");
        assertEquals(PostEffectDomainOwner.Outcome.APPLIED, harness.owner.lastDiagnostics().outcome());
        long installed = harness.owner.activeGeneration();
        assertTrue(installed >= 0, "the first install advances the declaration generation");
        assertEquals(installed, PostEffectManager.activeGeneration(),
                "the runtime binding's generation query reports the installed generation");
        assertTrue(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:gray")));
    }

    @Test
    void candidateCollectionStaysInertUntilCommitThenSwapsTheGeneration() throws Exception {
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:gray', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/invert")));
        harness.loadAndApplyInitialPlan();
        assertEquals(List.of("nekojs:gray"), PostEffectDeclarationHarness.installedIds());
        long beforeReload = harness.owner.activeGeneration();

        // 候选构建期（DOMAIN_PLAN 已收集、commit 之前）live 定义必须仍是旧 generation。
        // probe 收集器在真实 owner 之后注册 → 观察到的是「同轮声明已收集但尚未安装」的 live 面。
        List<List<String>> seenDuringCandidate = new ArrayList<>();
        harness.root.registerDomainCollector(new com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector() {
            @Override public String domain() { return "post-effect-e2e-probe"; }
            @Override public ScriptType scriptType() { return ScriptType.CLIENT; }
            @Override public void collect(Handle handle) {
                seenDuringCandidate.add(PostEffectDeclarationHarness.installedIds());
            }
        });

        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:sepia', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/blur")));
        harness.reloadClientScripts();

        assertEquals(List.of(List.of("nekojs:gray")), seenDuringCandidate,
                "candidate collection must not touch the installed definitions (inert until commit)");
        assertEquals(List.of("nekojs:sepia"), PostEffectDeclarationHarness.installedIds(),
                "the commit point installs the new generation and retires the previous one");
        assertEquals(beforeReload + 1L, harness.owner.activeGeneration(),
                "the committed generation advances exactly once per successful batch");
        assertEquals(beforeReload + 1L, PostEffectManager.activeGeneration());
        assertFalse(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:gray")),
                "the previous generation's declaration is no longer active");
        assertTrue(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:sepia")));
        assertEquals(1, harness.owner.lastDiagnostics().retired(),
                "the release order is observable: exactly one previous definition retired");
    }

    @Test
    void invalidChainJsonFailsTheWholeBatchAndKeepsTheOldActiveGeneration() throws Exception {
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:gray', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/invert")));
        harness.loadAndApplyInitialPlan();
        assertEquals(List.of("nekojs:gray"), PostEffectDeclarationHarness.installedIds());
        long generationBefore = harness.owner.activeGeneration();

        // 候选 JSON 无效：STATE_PLAN 联合预检拒绝整批，旧 generation 继续可用。
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => {
                  event.register('nekojs:broken', { chainJson: '{not json' })
                })
                """);
        NekoReloadException failure = assertThrows(NekoReloadException.class, harness::reloadClientScripts);

        assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(),
                "invalid chain JSON joins the joint preflight: " + failure.report());
        assertEquals(List.of("nekojs:gray"), PostEffectDeclarationHarness.installedIds(),
                "the previous active generation keeps serving after a blocked batch");
        assertEquals(generationBefore, harness.owner.activeGeneration(),
                "a blocked batch does not advance the active declaration generation");
        assertEquals(PostEffectDomainOwner.Outcome.APPLIED, harness.owner.lastDiagnostics().outcome(),
                "a blocked batch leaves the last applied outcome untouched");
    }

    @Test
    void collectionErrorFailsTheWholeBatchInDomainPlanAndALaterCandidateStillCommits() throws Exception {
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:gray', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/invert")));
        harness.loadAndApplyInitialPlan();

        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => {
                  event.register('nekojs:sepia', { chainJson: '%s' })
                  event.register('nekojs:bad id', { chainJson: '{}' })
                })
                """.formatted(chainJson("minecraft:post/blur")));
        NekoReloadException failure = assertThrows(NekoReloadException.class, harness::reloadClientScripts);

        assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(),
                "a collection error poisons the batch and is rejected at joint preflight: " + failure.report());
        assertTrue(failure.report().domain() != null && failure.report().domain().contains("post-effects"),
                "domain=" + failure.report().domain());
        assertEquals(List.of("nekojs:gray"), PostEffectDeclarationHarness.installedIds(),
                "neither declaration of the failed batch is installed (whole batch fails)");

        // 候选资源随失败丢弃：下一轮正常 reload 仍能提交。
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:sepia', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/blur")));
        harness.reloadClientScripts();
        assertEquals(List.of("nekojs:sepia"), PostEffectDeclarationHarness.installedIds(),
                "a later candidate commits normally (no pending listener leak)");
    }

    @Test
    void removedDeclarationRetiresInsteadOfStayingStale() throws Exception {
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:gray', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/invert")));
        harness.loadAndApplyInitialPlan();
        assertTrue(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:gray")));

        // 脚本不再声明：成功 reload 后旧 id 被退役，不再对本 generation 可见。
        harness.writeClientScript("fx.js", "global.noEffects = true\n");
        harness.reloadClientScripts();

        assertTrue(PostEffectDeclarationHarness.installedIds().isEmpty(),
                "an undeclared id is retired instead of staying silently stale");
        assertFalse(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:gray")));
        assertEquals(1, harness.owner.lastDiagnostics().retired());
    }

    @Test
    void explicitUnregisterRetiresAnIdDeclaredEarlierInTheSameBatch() throws Exception {
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => {
                  event.register('nekojs:gray', { chainJson: '%s' })
                  event.register('nekojs:sepia', { chainJson: '%s' })
                })
                """.formatted(chainJson("minecraft:post/invert"), chainJson("minecraft:post/blur")));
        harness.loadAndApplyInitialPlan();
        assertEquals(List.of("nekojs:gray", "nekojs:sepia"), PostEffectDeclarationHarness.installedIds());

        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => {
                  event.register('nekojs:gray', { chainJson: '%s' })
                  event.unregister('nekojs:sepia')
                })
                """.formatted(chainJson("minecraft:post/invert")));
        harness.reloadClientScripts();

        assertEquals(List.of("nekojs:gray"), PostEffectDeclarationHarness.installedIds(),
                "unregister retires exactly the id it names");
        assertFalse(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:sepia")));
    }

    @Test
    void generationAndStaleQueriesReportTheActiveDeclarationGeneration() throws Exception {
        // 无声明：查询面明确报告空 generation（无 id），而不是靠静默 no-op 表达「没声明」。
        harness.writeClientScript("fx.js", "global.idle = true\n");
        harness.loadAndApplyInitialPlan();

        assertTrue(PostEffectDeclarationHarness.installedIds().isEmpty(),
                "an empty declaration batch installs no definition");
        assertFalse(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:gray")),
                "an undeclared id is never reported as active");
        assertEquals(harness.owner.activeGeneration(), PostEffectManager.activeGeneration());
        long emptyBatchGeneration = harness.owner.activeGeneration();

        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:gray', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/invert")));
        harness.reloadClientScripts();

        assertEquals(emptyBatchGeneration + 1L, harness.owner.activeGeneration(),
                "the query face advances exactly once with the committed generation");
        assertEquals(harness.owner.activeGeneration(), PostEffectManager.activeGeneration());
        assertTrue(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:gray")));
        assertTrue(harness.owner.activeIds().contains("nekojs:gray"));
    }

    @Test
    void runtimeBindingMembersStayAvailableAndAreNotReplacedByTheDeclarationEvent() {
        // AC5：set/clear/toggle/current 仍是运行时 binding；声明事件不提供它们。
        assertTrue(Set.of("set", "clear", "toggle", "current", "isActive", "hasDefinition",
                        "installed", "activeGeneration", "isAvailable", "presets")
                        .containsAll(List.of("set", "clear", "toggle", "current")),
                "the runtime binding keeps its caller-visible members");
        for (var method : PostEffectsJS.class.getMethods()) {
            if (method.getDeclaringClass() == Object.class) continue;
            assertFalse(Set.of("register", "unregister").contains(method.getName()),
                    "the declaration entry points must not stay on the runtime binding: " + method.getName());
        }
        for (var method : PostEffectEventJS.class.getMethods()) {
            assertFalse(Set.of("set", "clear", "toggle", "current").contains(method.getName()),
                    "runtime actions must not move onto the declaration event: " + method.getName());
        }
    }
}
//?}
