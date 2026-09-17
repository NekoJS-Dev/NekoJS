// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code BlockEvents.modification} 事件机制（ticket 39 管线：收集 → inert 计划 →
 * Adapter 预检/恢复基线/应用），直接走 Java API，不经 JS）：六属性全量写
 * （Properties 副本 + Block 副本 + 全部 BlockState 副本）、状态相关光照函数的基线恢复、
 * 无声明计划的恢复语义，以及预检/收集期的错误面。
 *
 * <p>每例持有独立 {@link ModificationDomainOwner}（root 授权 domain owner 的测试替身
 * 挂载方式：直接实例化——基线按实例持有，@AfterEach close 恢复并清空，不污染同 JVM
 * 的其它测试）。
 *
 * <p>环境前提：26.x 的 {@code BuiltInRegistries} 注册前置要求
 * {@code Bootstrap#bootStrap()} 先行（其会先置位再触发注册表静态注册，顺序自洽）；
 * 裸 JVM 无 FML Loader 时 vanilla bootstrap 无法完成——用 assumption 跳过而非失败
 * （ModDev 测试环境正常执行；见 HolderAdapterTest 对同类限制的说明）。
 */
class BlockModificationEventJSTest {

    private ModificationDomainOwner owner;

    @BeforeEach
    void requireVanillaRegistriesAndOwner() {
        // 共享探针：Bootstrap.bootStrap() 先置位不抛错，必须再强制 Items 类初始化才能暴露
        // 无 FML Loader 的环境（共享见 VanillaRegistryProbe）
        Assumptions.assumeTrue(com.tkisor.nekojs.testfixture.VanillaRegistryProbe.available(),
                "vanilla registries unavailable in this JVM (no FML loader?)");
        owner = new ModificationDomainOwner();
    }

    @AfterEach
    void restoreAllBlocks() {
        // owner close：恢复其持有基线并清空（独立实例互不污染）
        if (owner != null) {
            owner.close();
        }
    }

    /** 收集一份计划并经 Adapter 预检 + 应用（生产 commit 路径的最小直驱形态）。 */
    private void collectAndApply(java.util.function.Consumer<BlockModificationEventJS> collector) {
        ModificationCandidatePlan plan = new ModificationCandidatePlan(owner);
        BlockModificationEventJS event = new BlockModificationEventJS(plan);
        collector.accept(event);
        plan.preflight();
        plan.publish();
    }

    @Test
    void modifyStoneHardnessThenEmptyPlanRestoresOriginal() {
        float original = Blocks.STONE.defaultBlockState().getDestroySpeed(null, null);

        collectAndApply(event -> event.modify("minecraft:stone", block -> block.setHardness(2.0f)));

        assertEquals(2.0f, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f);
        // Properties 声明源（defaultDestroyTime 读 Properties.destroyTime）同步更新
        assertEquals(2.0f, Blocks.STONE.defaultDestroyTime(), 0.0f);

        // 无声明重放：空计划应用 = 恢复基线 → 回到原值（不再依赖事件内 restore-all-first）
        collectAndApply(event -> {});
        assertEquals(original, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f);
        assertEquals(original, Blocks.STONE.defaultDestroyTime(), 0.0f);
        assertEquals(ModificationDomainOwner.Outcome.RESTORED, owner.lastDiagnostics().outcome(),
                "诊断区分 restored（空计划的成功形态）");
    }

    @Test
    void allSixPropertiesRoundTrip() {
        float originalHardness = Blocks.STONE.defaultBlockState().getDestroySpeed(null, null);
        float originalResistance = Blocks.STONE.getExplosionResistance();
        int originalLight = Blocks.STONE.defaultBlockState().getLightEmission();
        boolean originalRequiresTool = Blocks.STONE.defaultBlockState().requiresCorrectToolForDrops();
        float originalFriction = Blocks.STONE.getFriction();
        float originalJumpFactor = Blocks.STONE.getJumpFactor();

        collectAndApply(event -> event.modify("stone", block -> { // 无命名空间前缀也可解析
            block.setHardness(3.0f);
            block.setResistance(9.0f);
            block.setLightLevel(15);
            block.setRequiresTool(true);
            block.setFriction(0.98f);
            block.setJumpFactor(1.5f);
        }));

        assertEquals(3.0f, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f);
        assertEquals(9.0f, Blocks.STONE.getExplosionResistance(), 0.0f);
        assertEquals(15, Blocks.STONE.defaultBlockState().getLightEmission());
        assertEquals(true, Blocks.STONE.defaultBlockState().requiresCorrectToolForDrops());
        assertEquals(0.98f, Blocks.STONE.getFriction(), 0.0f);
        assertEquals(1.5f, Blocks.STONE.getJumpFactor(), 0.0f);
        assertEquals(ModificationDomainOwner.Outcome.APPLIED, owner.lastDiagnostics().outcome());

        collectAndApply(event -> {});

        assertEquals(originalHardness, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f);
        assertEquals(originalResistance, Blocks.STONE.getExplosionResistance(), 0.0f);
        assertEquals(originalLight, Blocks.STONE.defaultBlockState().getLightEmission());
        assertEquals(originalRequiresTool, Blocks.STONE.defaultBlockState().requiresCorrectToolForDrops());
        assertEquals(originalFriction, Blocks.STONE.getFriction(), 0.0f);
        assertEquals(originalJumpFactor, Blocks.STONE.getJumpFactor(), 0.0f);
    }

    @Test
    @SuppressWarnings("deprecation") // BlockStateBase.getLightEmission：NeoForge 提供带上下文重载，此处读无上下文当前值
    void multiStateWritesHitEveryStateAndRestoreLightFunction() {
        // 红石灯：光照函数按 LIT 状态给 15/0（多状态方块）
        Set<Integer> originalLights = lightValuesOf(Blocks.REDSTONE_LAMP);
        assertEquals(Set.of(0, 15), originalLights);

        collectAndApply(event -> event.modify("minecraft:redstone_lamp", block -> block.setLightLevel(7)));
        assertEquals(Set.of(7), lightValuesOf(Blocks.REDSTONE_LAMP));

        // 栅栏：多状态无光照方块，requiresTool 需写进每个 state
        collectAndApply(event -> event.modify("minecraft:oak_fence", block -> block.setRequiresTool(true)));
        assertTrue(Blocks.OAK_FENCE.getStateDefinition().getPossibleStates().stream()
                .allMatch(BlockState::requiresCorrectToolForDrops), "every state must require the tool");

        collectAndApply(event -> {});
        // 恢复的是原始 per-state 光照函数，而不是常量
        assertEquals(Set.of(0, 15), lightValuesOf(Blocks.REDSTONE_LAMP));
        assertTrue(Blocks.OAK_FENCE.getStateDefinition().getPossibleStates().stream()
                .noneMatch(BlockState::requiresCorrectToolForDrops), "requiresTool must revert on every state");
    }

    @Test
    @SuppressWarnings("deprecation") // 视图 getter 内部走无上下文的 getLightEmission
    void viewGettersReadCurrentValueUntilSet() {
        float[] seen = new float[1];
        collectAndApply(event -> event.modify("minecraft:stone", block -> {
            assertEquals(1.5f, block.getHardness(), 0.0f); // 读原值（stone 硬度 1.5；收集期 live 值可读）
            assertEquals(0, block.getLightLevel());
            block.setLightLevel(12);
            assertEquals(12, block.getLightLevel()); // 设置后读待写入值
            seen[0] = block.getHardness();
        }));
        assertEquals(1.5f, seen[0], 0.0f, "unset properties must read the live value");
    }

    @Test
    void invalidValuesRejectedAtPreflightNotApplied() {
        ModificationCandidatePlan plan = new ModificationCandidatePlan(owner);
        BlockModificationEventJS event = new BlockModificationEventJS(plan);
        event.modify("minecraft:stone", block -> block.setHardness(-1.0f));
        assertThrows(IllegalArgumentException.class, plan::preflight);
        assertEquals(ModificationDomainOwner.Outcome.INITIAL, owner.lastDiagnostics().outcome(),
                "预检拒绝时不得有任何应用（声明已记录，但整批被拒——无部分修改）");
        assertEquals(1.5f, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f,
                "live block untouched when the batch is rejected");

        ModificationCandidatePlan plan2 = new ModificationCandidatePlan(owner);
        BlockModificationEventJS event2 = new BlockModificationEventJS(plan2);
        event2.modify("minecraft:stone", block -> block.setLightLevel(16));
        assertThrows(IllegalArgumentException.class, plan2::preflight);
    }

    @Test
    void unknownBlockIdThrowsAtCollection() {
        ModificationCandidatePlan plan = new ModificationCandidatePlan(owner);
        BlockModificationEventJS event = new BlockModificationEventJS(plan);
        String message = assertThrows(IllegalArgumentException.class,
                () -> event.modify("minecraft:does_not_exist", block -> {})).getMessage();
        assertEquals("Unknown block: minecraft:does_not_exist", message);
    }

    @SuppressWarnings("deprecation")
    private static Set<Integer> lightValuesOf(Block block) {
        return block.getStateDefinition().getPossibleStates().stream()
                .map(BlockState::getLightEmission)
                .collect(Collectors.toUnmodifiableSet());
    }
}
//?}
//?}
