package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 39 AC13 贯穿 fixture（item 半边，五节点共享）：真实脚本经
 * {@code ItemEvents.modification} 事件面收集声明，commit 点由平台
 * {@link ModificationDomainOwner} Adapter 应用到真实 {@code Item}——从脚本事件到平台
 * 可观察结果，不断言私有静态 Map。
 *
 * <p>覆盖：初始 generation 收集点（about-to-start）、候选期 inert（live 组件在 commit 前
 * 保持旧 active）、成功 reload 应用新完整计划、声明移除恢复 NekoJS 基线（RESTORED）、
 * 预检拒绝整批保持旧 active（STATE_PLAN）、收集期错误整批失败（DOMAIN_PLAN）、
 * property 写与显式 setter 两种脚本写法结果等价（AC8 的脚本面）。
 *
 * <p>环境门：需要 vanilla 注册表（{@link VanillaRegistryProbe}）——裸 JVM 跳过（报告为
 * skipped），ModDev/开发环境真跑。
 */
class Ticket39ModificationScriptE2ETest {

    private Ticket39ModificationScriptHarness harness;

    @BeforeAll
    static void initPlatform() {
        Ticket39ModificationScriptHarness.ensurePlatformInitialized();
    }

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(VanillaRegistryProbe.available(),
                "vanilla registries unavailable in this JVM (no FML loader?)");
        Ticket39ModificationScriptHarness.clearServerScripts();
        harness = new Ticket39ModificationScriptHarness(Map.of());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) {
            harness.close();
            harness = null;
        }
        Ticket39ModificationScriptHarness.clearServerScripts();
    }

    private static int maxStackSizeOfDiamond() {
        return Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
    }

    private void loadServerScripts() {
        harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
    }

    @Test
    void startupCollectionPointAppliesScriptDeclarationOnceForTheInitialGeneration() throws Exception {
        assertTrue(VanillaRegistryProbe.available());
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => {
                  event.modify('minecraft:diamond', item => {
                    item.maxStackSize = 16
                  })
                })
                """);
        // 初始 load 是非事务路径：只注册 active 监听器，不做领域收集（收集点是平台触发的）
        loadServerScripts();
        assertEquals(64, maxStackSizeOfDiamond(), "initial non-transactional load must not apply modifications");

        harness.applyInitialPlan();

        assertEquals(16, maxStackSizeOfDiamond(), "startup collection point applies the collected plan");
        assertEquals(ModificationDomainOwner.Outcome.APPLIED, harness.owner.lastDiagnostics().outcome());
        assertEquals("startup", harness.owner.lastDiagnostics().source());
    }

    @Test
    void candidateStaysInertUntilCommitAndReloadAppliesTheNewPlan() throws Exception {
        // 先跑一次成功提交：active 计划 = maxStackSize 16
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => event.modify('minecraft:diamond', item => item.setMaxStackSize(16)))
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        assertEquals(16, maxStackSizeOfDiamond());

        // 第二轮：候选构建期（DOMAIN_PLAN 已收集、commit 之前）live 组件必须仍是旧 active 值。
        // probe 收集器在真实 owner 之后注册 → 观察到的是「同轮声明已收集但尚未应用」的 live 值。
        List<Integer> seenDuringCandidate = new ArrayList<>();
        harness.root.registerDomainCollector(new com.tkisor.nekojs.core.modification.CandidateDomainCollector() {
            @Override public String domain() { return "e2e-probe"; }
            @Override public ScriptType scriptType() { return ScriptType.SERVER; }
            @Override public void collect(Handle handle) {
                seenDuringCandidate.add(maxStackSizeOfDiamond());
            }
        });
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => event.modify('minecraft:diamond', item => item.setMaxStackSize(32)))
                """);

        harness.root.reload(ScriptType.SERVER);

        assertEquals(List.of(16), seenDuringCandidate,
                "candidate collection must not touch the live item components (inert until commit)");
        assertEquals(32, maxStackSizeOfDiamond(), "commit applies the new plan");
        assertEquals(ModificationDomainOwner.Outcome.APPLIED, harness.owner.lastDiagnostics().outcome());
    }

    @Test
    void removedDeclarationRestoresNekoJsBaselineInsteadOfStayingStale() throws Exception {
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => event.modify('minecraft:diamond', item => item.setMaxStackSize(16)))
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        assertEquals(16, maxStackSizeOfDiamond());

        // 脚本不再声明：成功 reload 后先恢复 NekoJS 持有基线，再应用新的完整（空）计划
        harness.writeServerScript("mod.js", "global.noModification = true\n");
        harness.root.reload(ScriptType.SERVER);

        assertEquals(64, maxStackSizeOfDiamond(),
                "removed declaration restores the NekoJS-held baseline (no silent stale)");
        assertEquals(ModificationDomainOwner.Outcome.RESTORED, harness.owner.lastDiagnostics().outcome(),
                "diagnostics distinguish restored from applied");
    }

    @Test
    void rejectedBatchKeepsOldActivePlanWithoutPartialApply() throws Exception {
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => event.modify('minecraft:diamond', item => item.setMaxStackSize(16)))
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        assertEquals(16, maxStackSizeOfDiamond());
        ModificationDomainOwner.Diagnostics before = harness.owner.lastDiagnostics();

        // 预检拒绝（超出 1..99 上限）：整批不提交，旧 active 继续服务
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => {
                  event.modify('minecraft:diamond', item => item.setMaxStackSize(500))
                })
                """);
        NekoReloadException failure = assertThrows(NekoReloadException.class,
                () -> harness.root.reload(ScriptType.SERVER));

        assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(),
                "range validation joins the joint preflight: " + failure.report());
        assertEquals(16, maxStackSizeOfDiamond(), "old active plan keeps serving after a blocked batch");
        assertEquals(before, harness.owner.lastDiagnostics(),
                "a blocked batch leaves the last applied generation untouched (no partial apply)");
    }

    @Test
    void collectionErrorFailsWholeBatchAndNextGoodReloadStillCommits() throws Exception {
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => event.modify('minecraft:diamond', item => item.setMaxStackSize(16)))
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        assertEquals(16, maxStackSizeOfDiamond());

        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => {
                  event.modify('minecraft:diamond', item => item.setMaxStackSize(32))
                  event.modify('minecraft:no_such_item', item => item.setMaxStackSize(1))
                })
                """);
        NekoReloadException failure = assertThrows(NekoReloadException.class,
                () -> harness.root.reload(ScriptType.SERVER));

        assertEquals(ReloadPhase.DOMAIN_PLAN, failure.report().phase(),
                "collection-time error fails the candidate in the collection phase: " + failure.report());
        assertTrue(failure.report().domain().startsWith("domain-collect:" + ModificationDomainOwner.DOMAIN)
                        || failure.report().domain().contains(ModificationDomainOwner.DOMAIN),
                "domain=" + failure.report().domain());
        assertEquals(16, maxStackSizeOfDiamond(),
                "neither declaration of the failed batch is applied (whole batch fails, old active kept)");

        // 候选资源随失败丢弃：下一轮正常 reload 仍能提交
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => event.modify('minecraft:diamond', item => item.setMaxStackSize(48)))
                """);
        harness.root.reload(ScriptType.SERVER);
        assertEquals(48, maxStackSizeOfDiamond(), "a later candidate commits normally (no pending listener leak)");
    }

    @Test
    void propertyWriteAndExplicitSetterScriptsProduceTheSameAppliedResult() throws Exception {
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => {
                  event.modify('minecraft:diamond', item => {
                    item.maxStackSize = 24
                    item.rarity = 'epic'
                  })
                })
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        int viaProperty = maxStackSizeOfDiamond();
        String viaPropertyRarity = Items.DIAMOND.components()
                .getOrDefault(DataComponents.RARITY, net.minecraft.world.item.Rarity.COMMON).getSerializedName();

        // 复位到基线，再以显式 setter 形态重放同一修改
        harness.close();
        harness = new Ticket39ModificationScriptHarness(Map.of());
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => {
                  event.modify('minecraft:diamond', item => {
                    item.setMaxStackSize(24)
                    item.setRarity('epic')
                  })
                })
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        int viaSetter = maxStackSizeOfDiamond();
        String viaSetterRarity = Items.DIAMOND.components()
                .getOrDefault(DataComponents.RARITY, net.minecraft.world.item.Rarity.COMMON).getSerializedName();

        assertEquals(24, viaProperty, "property write form applies through the same setter");
        assertEquals(24, viaSetter, "explicit setter form applies through the same setter");
        assertEquals(viaSetterRarity, viaPropertyRarity,
                "both script forms must produce the same normalized declaration (same Adapter result)");
        assertEquals("epic", viaPropertyRarity);
    }
}
