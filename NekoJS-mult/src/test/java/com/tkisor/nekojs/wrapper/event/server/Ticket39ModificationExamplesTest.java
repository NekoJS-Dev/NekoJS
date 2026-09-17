// 26.x 面（block 示例与 block parity 是 26.x API）。
//? if >=26 {
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 票 39 AC12：最小可运行示例链路——示例脚本与
 * {@code docs/architecture-refactor/baseline/2026-09-16-item-block-modification/examples/} 下的
 * {@code item-modification.js} / {@code item-setter-property-parity.js} / {@code block-modification.js} /
 * {@code declaration-removal-recovery.js} 保持一致，按生产序列（初始 generation 收集点 +
 * SERVER 事务 reload）经真实 Graal 管线 + 真实 Adapter 端到端跑通。
 *
 * <p>示例只使用已通过 gate 的能力（`ItemEvents`/`BlockEvents.modification` 的既有事件面与属性目录）；
 * 不触碰 legacy/raw 面，也不依赖未声明 supported 的同步能力（客户端可见性边界见 MIGRATION.md）。
 *
 * <p>环境门同 {@code Ticket39ModificationScriptE2ETest}（vanilla 注册表；裸 JVM 跳过）。
 */
class Ticket39ModificationExamplesTest {

    /** examples/item-modification.js（保持同源）。 */
    private static final String ITEM_EXAMPLE = """
            ItemEvents.modification(event => {
              event.modify('minecraft:diamond', item => {
                item.maxStackSize = 16
                item.rarity = 'epic'
              })
              event.modify('minecraft:golden_apple', item => {
                item.maxStackSize = 1
                item.maxDamage = 32
              })
              event.modify('minecraft:stick', item => {
                item.food = { nutrition: 4, saturation: 0.6, canAlwaysEat: true, eatSeconds: 1.6 }
              })
              event.modify('minecraft:blaze_rod', item => {
                item.tool = { miningSpeed: 6 }
                item.attackDamage = 6
                item.attackSpeed = -2.4
              })
            })
            """;

    /** examples/item-setter-property-parity.js（保持同源）。 */
    private static final String PARITY_EXAMPLE = """
            ItemEvents.modification(event => {
              event.modify('minecraft:diamond', item => {
                item.maxStackSize = 16
                item.rarity = 'epic'
              })
              event.modify('minecraft:emerald', item => {
                item.setMaxStackSize(16)
                item.setRarity('epic')
              })
            })
            """;

    /** examples/block-modification.js（保持同源）。 */
    private static final String BLOCK_EXAMPLE = """
            BlockEvents.modification(event => {
              event.modify('minecraft:stone', block => {
                block.hardness = 2.0
                block.resistance = 8.0
                block.requiresTool = true
              })
              event.modify('minecraft:redstone_lamp', block => {
                block.lightLevel = 7
              })
              event.modify('minecraft:oak_fence', block => {
                block.friction = 0.9
                block.jumpFactor = 1.1
              })
            })
            """;

    /** examples/declaration-removal-recovery.js 的 A 段（保持同源）。 */
    private static final String REMOVAL_EXAMPLE = """
            ItemEvents.modification(event => {
              event.modify('minecraft:diamond', item => item.setMaxStackSize(16))
            })
            """;

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

    private void run(String script) throws Exception {
        harness.writeServerScript("example.js", script);
        harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
        harness.applyInitialPlan();
        assertEquals(ModificationDomainOwner.Outcome.APPLIED, harness.owner.lastDiagnostics().outcome(),
                "example must commit at the startup collection point: " + harness.owner.lastDiagnostics());
    }

    private static int maxStack(net.minecraft.world.item.Item item) {
        return item.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
    }

    @Test
    void itemModificationExampleCommitsThroughTheAdapter() throws Exception {
        run(ITEM_EXAMPLE);
        assertEquals(16, maxStack(Items.DIAMOND));
        assertEquals("epic", Items.DIAMOND.components().get(DataComponents.RARITY).getSerializedName());
        assertEquals(1, maxStack(Items.GOLDEN_APPLE));
        assertEquals(32, Items.GOLDEN_APPLE.components().getOrDefault(DataComponents.MAX_DAMAGE, 0));
        assertEquals(4, Items.STICK.components().get(DataComponents.FOOD).nutrition());
        assertEquals(6.0, Items.BLAZE_ROD.components().get(DataComponents.ATTRIBUTE_MODIFIERS)
                .compute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 0.0,
                        net.minecraft.world.entity.EquipmentSlot.MAINHAND), 1.0e-9,
                "attackDamage declaration reaches the real attribute component");
    }

    @Test
    void setterPropertyParityExampleProducesTheSameAppliedResult() throws Exception {
        run(PARITY_EXAMPLE);
        assertEquals(16, maxStack(Items.DIAMOND));
        assertEquals(16, maxStack(Items.EMERALD),
                "explicit-setter form commits through the same setter/declaration path");
        assertEquals(Items.DIAMOND.components().get(DataComponents.RARITY),
                Items.EMERALD.components().get(DataComponents.RARITY));
    }

    @Test
    void blockModificationExampleCommitsThroughTheAdapter() throws Exception {
        run(BLOCK_EXAMPLE);
        assertEquals(2.0f, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f);
        assertEquals(2.0f, Blocks.STONE.defaultDestroyTime(), 0.0f);
        assertEquals(Set.of(7), lightValuesOf(Blocks.REDSTONE_LAMP));
        assertEquals(0.9f, Blocks.OAK_FENCE.getFriction(), 0.0f);
    }

    @Test
    void declarationRemovalExampleRestoresTheBaseline() throws Exception {
        run(REMOVAL_EXAMPLE);
        assertEquals(16, maxStack(Items.DIAMOND));

        // B 段：脚本不再声明 → 成功 reload 先恢复基线，再应用新的空计划
        harness.writeServerScript("example.js", "global.noModification = true\n");
        harness.root.reload(ScriptType.SERVER);
        assertEquals(64, maxStack(Items.DIAMOND));
        assertEquals(ModificationDomainOwner.Outcome.RESTORED, harness.owner.lastDiagnostics().outcome());
    }

    private static Set<Integer> lightValuesOf(net.minecraft.world.level.block.Block block) {
        return block.getStateDefinition().getPossibleStates().stream()
                .map(net.minecraft.world.level.block.state.BlockState::getLightEmission)
                .collect(Collectors.toUnmodifiableSet());
    }
}
//?}
