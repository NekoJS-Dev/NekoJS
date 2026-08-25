package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code BlockEvents.modification} 事件机制（直接走 Java API，不经 JS）：
 * 修改石头硬度、六属性全量写（Properties 副本 + Block 副本 + 全部 BlockState 副本）、
 * 状态相关光照函数的快照恢复，以及无修改重放（fire）后回到原值的快照恢复语义。
 *
 * <p>环境前提：26.x 的 {@code BuiltInRegistries} 注册前置要求
 * {@link Bootstrap#bootStrap()} 先行（其会先置位再触发注册表静态注册，顺序自洽）；
 * 事件组类初始化链（EventBusJS → ScriptType → NekoJSPaths → Platform）需要
 * {@link Platform#init}（真实 mod 环境由 mod 构造器完成，这里用最小桩）。
 * 裸 JVM 无 FML Loader 时 vanilla bootstrap 无法完成——用 assumption 跳过而非失败
 * （ModDev 测试环境正常执行；见 HolderAdapterTest 对同类限制的说明）。
 */
class BlockModificationEventJSTest {

    @BeforeAll
    static void requireVanillaRegistries() {
        // 共享探针：Bootstrap.bootStrap() 先置位不抛错，必须再强制 Items 类初始化才能暴露
        // 无 FML Loader 的环境（共享见 VanillaRegistryProbe）
        Assumptions.assumeTrue(com.tkisor.nekojs.testfixture.VanillaRegistryProbe.available(),
                "vanilla registries unavailable in this JVM (no FML loader?)");
    }

    @AfterEach
    void restoreAllBlocks() {
        // 快照静态 map 跨测试方法存活：每例收尾整体恢复，避免污染同 JVM 的其它测试
        BlockModificationEventJS.fire();
    }

    @Test
    void modifyStoneHardnessThenReFireWithNoModificationsRestoresOriginal() {
        float original = Blocks.STONE.defaultBlockState().getDestroySpeed(null, null);

        BlockModificationEventJS event = new BlockModificationEventJS();
        event.modify("minecraft:stone", block -> block.setHardness(2.0f));

        assertEquals(1, event.getModifiedCount());
        assertEquals(2.0f, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f);
        // Properties 声明源（defaultDestroyTime 读 Properties.destroyTime）同步更新
        assertEquals(2.0f, Blocks.STONE.defaultDestroyTime(), 0.0f);

        // 无修改重放：fire 先整体恢复快照，post 无监听器 → 回到原值
        int modified = BlockModificationEventJS.fire();
        assertEquals(0, modified);
        assertEquals(original, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f);
        assertEquals(original, Blocks.STONE.defaultDestroyTime(), 0.0f);
    }

    @Test
    @SuppressWarnings("deprecation") // BlockStateBase.getLightEmission：NeoForge 提供带上下文重载，此处读无上下文当前值
    void allSixPropertiesRoundTrip() {
        float originalHardness = Blocks.STONE.defaultBlockState().getDestroySpeed(null, null);
        float originalResistance = Blocks.STONE.getExplosionResistance();
        int originalLight = Blocks.STONE.defaultBlockState().getLightEmission();
        boolean originalRequiresTool = Blocks.STONE.defaultBlockState().requiresCorrectToolForDrops();
        float originalFriction = Blocks.STONE.getFriction();
        float originalJumpFactor = Blocks.STONE.getJumpFactor();

        new BlockModificationEventJS().modify("stone", block -> { // 无命名空间前缀也可解析
            block.setHardness(3.0f);
            block.setResistance(9.0f);
            block.setLightLevel(15);
            block.setRequiresTool(true);
            block.setFriction(0.98f);
            block.setJumpFactor(1.5f);
        });

        assertEquals(3.0f, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f);
        assertEquals(9.0f, Blocks.STONE.getExplosionResistance(), 0.0f);
        assertEquals(15, Blocks.STONE.defaultBlockState().getLightEmission());
        assertEquals(true, Blocks.STONE.defaultBlockState().requiresCorrectToolForDrops());
        assertEquals(0.98f, Blocks.STONE.getFriction(), 0.0f);
        assertEquals(1.5f, Blocks.STONE.getJumpFactor(), 0.0f);

        BlockModificationEventJS.fire();

        assertEquals(originalHardness, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f);
        assertEquals(originalResistance, Blocks.STONE.getExplosionResistance(), 0.0f);
        assertEquals(originalLight, Blocks.STONE.defaultBlockState().getLightEmission());
        assertEquals(originalRequiresTool, Blocks.STONE.defaultBlockState().requiresCorrectToolForDrops());
        assertEquals(originalFriction, Blocks.STONE.getFriction(), 0.0f);
        assertEquals(originalJumpFactor, Blocks.STONE.getJumpFactor(), 0.0f);
    }

    @Test
    @SuppressWarnings("deprecation") // 同上：读 BlockStateBase.getLightEmission 当前值
    void multiStateWritesHitEveryStateAndRestoreLightFunction() {
        // 红石灯：光照函数按 LIT 状态给 15/0（多状态方块）
        Set<Integer> originalLights = lightValuesOf(Blocks.REDSTONE_LAMP);
        assertEquals(Set.of(0, 15), originalLights);

        new BlockModificationEventJS().modify("minecraft:redstone_lamp", block -> block.setLightLevel(7));
        assertEquals(Set.of(7), lightValuesOf(Blocks.REDSTONE_LAMP));

        // 栅栏：多状态无光照方块，requiresTool 需写进每个 state
        new BlockModificationEventJS().modify("minecraft:oak_fence", block -> block.setRequiresTool(true));
        assertTrue(Blocks.OAK_FENCE.getStateDefinition().getPossibleStates().stream()
                .allMatch(BlockState::requiresCorrectToolForDrops), "every state must require the tool");

        BlockModificationEventJS.fire();
        // 恢复的是原始 per-state 光照函数，而不是常量
        assertEquals(Set.of(0, 15), lightValuesOf(Blocks.REDSTONE_LAMP));
        assertTrue(Blocks.OAK_FENCE.getStateDefinition().getPossibleStates().stream()
                .noneMatch(BlockState::requiresCorrectToolForDrops), "requiresTool must revert on every state");
    }

    @Test
    @SuppressWarnings("deprecation") // 视图 getter 内部走无上下文的 getLightEmission
    void viewGettersReadCurrentValueUntilSet() {
        float[] seen = new float[1];
        new BlockModificationEventJS().modify("minecraft:stone", block -> {
            assertEquals(1.5f, block.getHardness(), 0.0f); // 读原值（stone 硬度 1.5）
            assertEquals(0, block.getLightLevel());
            block.setLightLevel(12);
            assertEquals(12, block.getLightLevel()); // 设置后读待写入值
            seen[0] = block.getHardness();
        });
        assertEquals(1.5f, seen[0], 0.0f, "unset properties must read the live value");
    }

    @Test
    void invalidValuesRejectedAtApplyTime() {
        BlockModificationEventJS event = new BlockModificationEventJS();
        assertThrows(IllegalArgumentException.class,
                () -> event.modify("minecraft:stone", block -> block.setHardness(-1.0f)));
        assertThrows(IllegalArgumentException.class,
                () -> event.modify("minecraft:stone", block -> block.setResistance(-0.5f)));
        assertThrows(IllegalArgumentException.class,
                () -> event.modify("minecraft:stone", block -> block.setLightLevel(16)));
        assertThrows(IllegalArgumentException.class,
                () -> event.modify("minecraft:stone", block -> block.setFriction(1.5f)));
        assertEquals(0, event.getModifiedCount());
    }

    @Test
    void unknownBlockIdThrowsActionableError() {
        BlockModificationEventJS event = new BlockModificationEventJS();
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

    /** 最小 IPlatform 桩：仅供事件组类初始化链读到 gameDir / 版本号，不承载行为。 */
    private static final class TestPlatform implements IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "26.1.2"; }
        @Override public Path getGameDir() {
            try {
                return Files.createTempDirectory("nekojs-blockmod-test");
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
        @Override public Map<String, IModInfo> getMods() { return Map.of(); }
        @Override public IModInfo getInfo(String modID) { return null; }
        @Override public String getLoaderId() { return "neoforge"; }
        @Override public String getLoaderVersion() { return "0"; }
    }
}
