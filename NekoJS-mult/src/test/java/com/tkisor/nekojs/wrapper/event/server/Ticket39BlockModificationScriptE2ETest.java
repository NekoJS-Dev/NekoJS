// 26.x 面：BlockEvents.modification 总线与 BlockModificationJS 属性面是 26.x API
//（1.21.1 无此总线，见票 39 五节点差异表）。
//? if >=26 {
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 票 39 AC13 贯穿 fixture（block 半边，26.x）：真实脚本经
 * {@code BlockEvents.modification} 收集声明，commit 点由平台
 * {@link ModificationDomainOwner} Adapter 写入 {@code BlockBehaviour.Properties} +
 * {@code Block} 副本 + 每个 {@code BlockState} 副本（状态相关光照函数走基线恢复）。
 *
 * <p>覆盖：应用（多状态方块）、候选期 inert（probe 收集器读到的 live 值仍是旧 active）、
 * 声明移除恢复基线（含原始 per-state 光照函数）、预检拒绝整批保持旧 active。
 * 环境门同 {@code Ticket39ModificationScriptE2ETest}。
 */
class Ticket39BlockModificationScriptE2ETest {

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
        harness = new Ticket39ModificationScriptHarness(Map.of("BlockEvents", BlockEvents.GROUP));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) {
            harness.close();
            harness = null;
        }
        Ticket39ModificationScriptHarness.clearServerScripts();
    }

    private void loadServerScripts() {
        harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
    }

    @SuppressWarnings("deprecation") // 读无上下文当前值：Block 副本的 destroyTime
    private static float stoneHardness() {
        return Blocks.STONE.defaultBlockState().getDestroySpeed(null, null);
    }

    private static Set<Integer> lightValuesOf(net.minecraft.world.level.block.Block block) {
        return block.getStateDefinition().getPossibleStates().stream()
                .map(net.minecraft.world.level.block.state.BlockState::getLightEmission)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Test
    void scriptDeclarationAppliesToLiveBlockAndItsStates() throws Exception {
        float original = stoneHardness();
        harness.writeServerScript("mod.js", """
                BlockEvents.modification(event => {
                  event.modify('minecraft:stone', block => {
                    block.hardness = 2.0
                    block.resistance = 8.0
                  })
                })
                """);
        loadServerScripts();
        assertEquals(original, stoneHardness(), 0.0f, "initial load does not apply (collection is platform-triggered)");

        harness.applyInitialPlan();

        assertEquals(2.0f, stoneHardness(), 0.0f, "startup collection point applies the block declaration");
        assertEquals(2.0f, Blocks.STONE.defaultDestroyTime(), 0.0f,
                "the Properties declaration source is updated too (not only the Block copy)");
        assertEquals(ModificationDomainOwner.Outcome.APPLIED, harness.owner.lastDiagnostics().outcome());
    }

    @Test
    void candidateStaysInertForBlocksAndEmptyPlanRestoresBaseline() throws Exception {
        harness.writeServerScript("mod.js", """
                BlockEvents.modification(event => event.modify('minecraft:redstone_lamp', block => block.setLightLevel(7)))
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        assertEquals(Set.of(7), lightValuesOf(Blocks.REDSTONE_LAMP));

        List<Set<Integer>> seenDuringCandidate = new ArrayList<>();
        harness.root.registerDomainCollector(new com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector() {
            @Override public String domain() { return "e2e-block-probe"; }
            @Override public ScriptType scriptType() { return ScriptType.SERVER; }
            @Override public void collect(Handle handle) {
                seenDuringCandidate.add(lightValuesOf(Blocks.REDSTONE_LAMP));
            }
        });
        harness.writeServerScript("mod.js", """
                BlockEvents.modification(event => event.modify('minecraft:redstone_lamp', block => block.setLightLevel(12)))
                """);

        harness.root.reload(ScriptType.SERVER);

        assertEquals(List.of(Set.of(7)), seenDuringCandidate,
                "candidate collection must not touch live BlockState copies (inert until commit)");
        assertEquals(Set.of(12), lightValuesOf(Blocks.REDSTONE_LAMP), "commit applies the new plan");

        // 声明移除：成功 reload 后恢复原始 per-state 光照函数（不是常量）
        harness.writeServerScript("mod.js", "global.noModification = true\n");
        harness.root.reload(ScriptType.SERVER);

        assertEquals(Set.of(0, 15), lightValuesOf(Blocks.REDSTONE_LAMP),
                "removed declaration restores the NekoJS-held baseline (original per-state light function)");
        assertEquals(ModificationDomainOwner.Outcome.RESTORED, harness.owner.lastDiagnostics().outcome());
    }

    @Test
    void rejectedBlockBatchKeepsOldActivePlan() throws Exception {
        harness.writeServerScript("mod.js", """
                BlockEvents.modification(event => event.modify('minecraft:stone', block => block.setHardness(3.0)))
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        assertEquals(3.0f, stoneHardness(), 0.0f);

        ModificationDomainOwner.Diagnostics before = harness.owner.lastDiagnostics();
        harness.writeServerScript("mod.js", """
                BlockEvents.modification(event => event.modify('minecraft:stone', block => block.setLightLevel(16)))
                """);
        NekoReloadException failure = assertThrows(NekoReloadException.class,
                () -> harness.root.reload(ScriptType.SERVER));

        assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(), failure.report().toString());
        assertEquals(3.0f, stoneHardness(), 0.0f, "blocked batch keeps the old active modification");
        assertEquals(before, harness.owner.lastDiagnostics(), "no partial apply for the blocked batch");
    }

    @Test
    void multipleBlocksInOneScriptCommitTogetherOrNotAtAll() throws Exception {
        harness.writeServerScript("mod.js", """
                BlockEvents.modification(event => {
                  event.modify('minecraft:stone', block => block.setHardness(4.0))
                  event.modify('minecraft:oak_fence', block => block.setRequiresTool(true))
                })
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        assertEquals(4.0f, stoneHardness(), 0.0f);
        assertEquals(true, Blocks.OAK_FENCE.defaultBlockState().requiresCorrectToolForDrops());

        // 第二条声明非法（friction 越界）：整批（含第一条）不提交，旧 active 保留
        harness.writeServerScript("mod.js", """
                BlockEvents.modification(event => {
                  event.modify('minecraft:stone', block => block.setHardness(9.0))
                  event.modify('minecraft:oak_fence', block => block.setFriction(1.5))
                })
                """);
        NekoReloadException failure = assertThrows(NekoReloadException.class,
                () -> harness.root.reload(ScriptType.SERVER));

        assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(), failure.report().toString());
        assertEquals(4.0f, stoneHardness(), 0.0f, "first declaration of the rejected batch must not apply either");
        assertEquals(true, Blocks.OAK_FENCE.defaultBlockState().requiresCorrectToolForDrops());
    }
}
//?}
