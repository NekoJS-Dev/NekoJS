// 旧路径 characterization（ticket 39 AC6）：本文件在重构前固定 Item/Block modification 的
// 可观察行为——同一次重放内多声明的组合顺序、item 与 block 在「声明消失」上的不对称、
// 以及 GraalJS 对宿主属性视图 property 写入的真实行为。重构（candidate plan + Adapter）后
// 同一可观察顺序必须确定性重放；行为按 AC7 有意改变的地方在本文件逐段标注并以新断言替换。
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.bindings.event.ItemEvents;
import graal.graalvm.polyglot.Context;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 旧直接修改路径的可观察行为 characterization（重构前基线，ticket 39 AC6）。
 *
 * <p>覆盖三个旧事实：
 * <ol>
 *   <li><b>同重放内同目标多声明 = 后一声明整体替换前一声明</b>（restore-before-modify
 *       每次回到原始基线，前一声明的属性不残留）——item 与 block（26.x）一致；</li>
 *   <li><b>「声明消失」不对称</b>：block 在重放前整体恢复快照（无监听重放即回原值），
 *       item 只在被再次 modify 时恢复——声明的修改删除后 item 保持 stale；</li>
 *   <li><b>GraalJS 宿主视图 property 写</b>：{@code item.maxStackSize = 16} 在宿主对象上
 *       不落 setter（ticket 15 P1 同款事实），真实结果由探针固定（全节点真跑，无注册表依赖）。</li>
 * </ol>
 *
 * <p>环境门（1/2 需要 vanilla 注册表）：1.21.1 裸 JVM 可用；26.x 需要 ModDev 测试环境，
 * 裸 JVM 用 assumption 跳过（与 {@code BlockModificationEventJSTest} 同机制）。
 */
class ModificationLegacyCharacterizationTest {

    /** item 侧 fire(server) 的 null 守卫会直接 return 0——用与 fire 等价的直接 post。 */
    private static void replayItems() {
        ItemEvents.MODIFICATION.post(new ItemModificationEventJS(null));
    }

    @AfterEach
    void restoreEverything() {
        // 快照是进程级静态：每例收尾整体恢复，避免污染同 JVM 的其它测试。
        // （assumption 不能放 @AfterEach——会把这些用例整体标成 skipped；这里用 if 门。）
//? if >=26 {
        if (com.tkisor.nekojs.testfixture.VanillaRegistryProbe.available()) {
            BlockModificationEventJS.fire();
        }
//?}
        replayItems();
    }

    @Test
    void sameTargetMultipleDeclarationsLastOneEntirelyReplacesEarlierOnes() {
        requireItemsRegistry();
        // 第一声明：maxStackSize + rarity；第二声明：只写 maxStackSize。
        // 旧语义：第二声明先恢复原始组件再叠加自己的属性 → rarity 不残留（整体替换，不是按属性合并）。
        ItemModificationEventJS event = new ItemModificationEventJS(null);
        event.modify("minecraft:diamond", item -> {
            item.setMaxStackSize(16);
            item.setRarity("epic");
        });
        assertEquals("epic", Items.DIAMOND.components().get(DataComponents.RARITY).getSerializedName());

        event.modify("minecraft:diamond", item -> item.setMaxStackSize(32));

        assertEquals(32, Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
        assertTrue(Items.DIAMOND.components().get(DataComponents.RARITY) == null
                        || !"epic".equals(Items.DIAMOND.components().get(DataComponents.RARITY).getSerializedName()),
                "第二声明必须整体替换第一声明：rarity 不得残留 epic");
    }

    /**
     * 旧不对称事实：block 重放前整体恢复快照；item 只在再次 modify 时恢复。
     * block 半边由既有 {@code BlockModificationEventJSTest#modifyStoneHardnessThenReFireWithNoModificationsRestoresOriginal}
     * 固定（26.x，ModDev 环境）；本用例固定 item 半边。
     */
    @Test
    void removedItemDeclarationLeavesTheModificationStaleOnTheLegacyPath() {
        requireItemsRegistry();
        new ItemModificationEventJS(null).modify("minecraft:diamond", item -> item.setMaxStackSize(16));
        assertEquals(16, Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));

        // 声明消失后的重放（无监听）
        replayItems();

        // item：旧路径保持 stale——AC7 将有意改为「先恢复基线再应用新完整计划」，
        // 届时此断言翻转为 64 并进迁移表。
        assertEquals(16, Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1),
                "旧事实：item 声明消失后修改保持 stale（本票 AC7 改变该行为）");
    }

    /**
     * GraalJS 探针：宿主属性视图上的 property 写不经过 setter。旧脚本面 javadoc 示例使用
     * {@code item.maxStackSize = 16} 形态，但宿主对象（私有字段 + public setter）的 property
     * 写在 GraalJS 上既不落字段也不落 setter——真实行为由本探针固定（抛 TypeError 或静默
     * 丢弃），为 AC8 的 surface 修复提供 characterization 依据。无 vanilla 注册表依赖。
     */
    @Test
    void graalPropertyWriteOnHostViewDoesNotReachSetter() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ItemModificationJS view = new ItemModificationJS();
            context.getBindings("js").putMember("item", view);
            String outcome = context.eval("js",
                    "(function () {" +
                    "  try { item.maxStackSize = 16; } catch (e) { return 'threw:' + e.constructor.name; }" +
                    "  try { return 'assigned,read=' + item.maxStackSize + ',pending=' + item.getMaxStackSize(); }" +
                    "  catch (e2) { return 'assigned,read-threw:' + e2.constructor.name; }" +
                    "})()").asString();
            // 记录式断言：无论哪种形态都必须证明「setter 没被调用」（pending 保持 null）
            System.out.println("[modification-characterization] property-write probe outcome: " + outcome);
            assertTrue(outcome.startsWith("threw:") || outcome.startsWith("assigned"),
                    "unexpected probe outcome: " + outcome);
            assertEquals(null, view.getMaxStackSize(),
                    "property 写不得到达 setter（旧事实：outcome=" + outcome + "）");
        }
    }

    /** vanilla 注册表可用性门（{@link com.tkisor.nekojs.testfixture.VanillaRegistryProbe}）：
     *  26.x 裸 JVM 跳过（ModDev 真跑）；1.21.1 bootstrap 无 FML 依赖，裸 JVM 通常真跑。 */
    private static void requireItemsRegistry() {
        Assumptions.assumeTrue(com.tkisor.nekojs.testfixture.VanillaRegistryProbe.available(),
                "vanilla registries unavailable in this JVM (no FML loader?)");
    }
}
