// 旧路径 characterization → 重构后确定性重放断言（ticket 39 AC6/AC7）。
// 重构前的旧事实（直接修改路径，2026-09-17 在旧代码上实测）：
//   1) 同重放内同目标多声明 = 后一声明整体替换前一声明（restore-before-modify 每次回基线）；
//   2) block 重放前整体恢复快照，item 只在再次 modify 时恢复 → item 声明消失后修改 stale；
//   3) GraalJS 宿主视图 property 写静默无效（探针结果 assigned,read=undefined,pending=null，
//      不落 setter——javadoc 示例形态在旧路径从未生效）。
// 重构后（candidate plan + Adapter）：(1) 同一可观察顺序确定性重放（本文件断言）；
// (2) item/block 统一为「先恢复基线再应用新完整计划」（AC7，迁移表记录行为变化）；
// (3) property 写经 ModificationViewSurface 到达同一 setter（ModificationSetterPropertyParityTest）。
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC6 fixture：重构后按旧可观察顺序确定性重放——同目标多声明的整体替换语义逐字保留
 * （不引入同 key 拒绝，也不新增未裁定的合并政策）；AC7：item 声明移除不再 stale。
 * 环境门同 {@code BlockModificationEventJSTest}（vanilla 注册表；ModDev 真跑）。
 */
class ModificationLegacyCharacterizationTest {

    private ModificationDomainOwner owner;

    private void requireItemsRegistry() {
        Assumptions.assumeTrue(com.tkisor.nekojs.testfixture.VanillaRegistryProbe.available(),
                "vanilla registries unavailable in this JVM (no FML loader?)");
        owner = new ModificationDomainOwner();
    }

    @AfterEach
    void restoreEverything() {
        if (owner != null) {
            owner.close();
        }
    }

    /** 收集一份计划并经 Adapter 预检 + 应用。 */
    private ModificationCandidatePlan collect(java.util.function.Consumer<ItemModificationEventJS> collector) {
        ModificationCandidatePlan plan = new ModificationCandidatePlan(owner);
        ItemModificationEventJS event = new ItemModificationEventJS(plan);
        collector.accept(event);
        plan.preflight();
        plan.publish();
        return plan;
    }

    @Test
    void sameTargetMultipleDeclarationsLastOneEntirelyReplacesEarlierOnes() {
        requireItemsRegistry();
        // 旧事实 1 的确定性重放：第一声明 maxStackSize+rarity，第二声明只写 maxStackSize
        // → 结果 = 基线 + 第二声明（rarity 不残留；整体替换，不是按属性合并）。
        collect(event -> {
            event.modify("minecraft:diamond", item -> {
                item.setMaxStackSize(16);
                item.setRarity("epic");
            });
            event.modify("minecraft:diamond", item -> item.setMaxStackSize(32));
        });

        assertEquals(32, Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
        assertTrue(Items.DIAMOND.components().get(DataComponents.RARITY) == null
                        || !"epic".equals(Items.DIAMOND.components().get(DataComponents.RARITY).getSerializedName()),
                "第二声明必须整体替换第一声明：rarity 不得残留 epic（与旧路径可观察结果一致）");
    }

    @Test
    void removedItemDeclarationNowRestoresBaselineInsteadOfStayingStale() {
        requireItemsRegistry();
        collect(event -> event.modify("minecraft:diamond", item -> item.setMaxStackSize(16)));
        assertEquals(16, Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));

        // 声明消失后的重放：空计划应用 = 恢复基线（旧 item 路径保持 stale——AC7 有意闭合，
        // 行为变化记录于迁移表；block 半边旧路径本就恢复，语义统一）。
        collect(event -> {});

        assertEquals(64, Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1),
                "声明移除后恢复 NekoJS 持有基线（不再静默 stale）");
        assertEquals(ModificationDomainOwner.Outcome.RESTORED, owner.lastDiagnostics().outcome(),
                "诊断区分 restored（不把静默 stale 当成功）");
    }
}
