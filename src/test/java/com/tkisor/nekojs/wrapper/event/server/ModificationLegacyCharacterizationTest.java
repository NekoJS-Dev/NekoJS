// 旧路径 characterization → 重构后确定性重放断言（ticket 39 AC6/AC7）。
// 重构前的旧事实（直接修改路径，2026-09-17 在旧代码上实测）：
//   1) 同重放内同目标多声明 = 后一声明整体替换前一声明（restore-before-modify 每次回基线）；
//   2) block 重放前整体恢复快照，item 只在再次 modify 时恢复 → item 声明消失后修改 stale；
//   3) GraalJS 宿主对象 property 写静默无效（探针结果 assigned,read=undefined,pending=null，
//      不落 setter——javadoc 示例形态在旧路径从未生效；现行复现见本文件探针用例）。
// 重构后（candidate plan + Adapter）：(1) 声明顺序 + 整体替换语义在计划层与端到端两层固定
//（本文件 + common 层 Ticket39DomainCollectionTest）；(2) item/block 统一为「先恢复基线再应用
// 完整新计划」（AC7，迁移表记录行为变化）；(3) property 写经 ModificationViewSurface 到达同一
// setter（ModificationSetterPropertyParityTest，含生产投递形态）。
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.core.modification.ModificationApplier;
import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import graal.graalvm.polyglot.Context;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC6 fixture：重构后按旧可观察顺序确定性重放——同目标多声明的整体替换语义逐字保留
 * （不引入同 key 拒绝，也不新增未裁定的合并政策）；AC7：item 声明移除不再 stale。
 *
 * <p>registry-free 部分（计划层，本机实测五节点真跑）：声明顺序保留 + 按声明序应用 + 后声明整体
 * 替换（视图产出的声明只含显式设置属性，Adapter 契约「先恢复基线、再按声明顺序应用」）；
 * registry-gated 部分（端到端，vanilla 注册表）：真实 Item 组件上的同一语义。
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

    // ============ registry-free：计划层固定 AC6（本机实测五节点真跑） ============

    /**
     * 同目标多声明在<b>计划层</b>的确定性重放：声明按注册序保留（不引入同 key 拒绝）、
     * Adapter 按声明序收到全部声明、且「视图声明只含显式设置属性 + Adapter 先回基线再按序
     * 应用」的契约组合产生旧可观察结果——后一声明整体替换前一声明（前声明的其它属性不残留）。
     *
     * <p>这里用真实 {@link ItemModificationJS}（Java setter 形态，不需要 Graal）产出声明，
     * 目标存储与「恢复基线 → 按声明序应用」契约由最小 {@link ModificationApplier} 承载；
     * 真实 Item 组件上的同一语义由上面的 registry-gated 用例与 common 层
     * {@code Ticket39DomainCollectionTest} 覆盖。
     */
    @Test
    void sameTargetDeclarationsKeepOrderAndLastOneWholeReplacesEarlierAtPlanLayer() {
        ItemModificationJS first = new ItemModificationJS();
        first.setMaxStackSize(16);
        first.setRarity("epic");
        ItemModificationJS second = new ItemModificationJS();
        second.setMaxStackSize(32);

        RecordingApplier applier = new RecordingApplier(Map.of("maxStackSize", 64, "maxDamage", 0));
        ModificationCandidatePlan plan = new ModificationCandidatePlan(applier);
        plan.add(new ModificationDeclaration("item", "plan:target", first.normalizedProperties(), "a.js"));
        plan.add(new ModificationDeclaration("item", "plan:target", second.normalizedProperties(), "b.js"));

        assertEquals(2, plan.declarations().size(),
                "同目标多声明全部保留（不引入同 key 拒绝，也不在收集期合并）");
        assertEquals(16, plan.declarations().get(0).properties().get("maxStackSize"));
        assertEquals(32, plan.declarations().get(1).properties().get("maxStackSize"));

        plan.preflight();
        plan.publish();

        assertEquals(2, applier.applied.size(), "Adapter 收到全部声明（不提前合并/丢弃）");
        assertEquals(List.of("a.js", "b.js"), applier.appliedScriptIds, "按声明注册序应用");
        assertEquals(32, applier.target.get("maxStackSize"), "后一声明整体替换前一声明");
        assertNull(applier.target.get("rarity"),
                "第一声明的其它属性不得残留（整体替换，不是按属性合并）");
        assertEquals(0, applier.target.get("maxDamage"), "未声明属性保持基线");
    }

    /**
     * 旧事实 3 的 registry-free 探针（AC8 的 characterization 前提）：
     * <ol>
     *   <li><b>普通宿主对象</b>（私有字段 + public setter，无 ProxyObject）的 property 写在
     *       GraalJS 上被静默丢弃——重构前视图就是这个形态，javadoc 示例因此从未生效；</li>
     *   <li>改造后的视图实现 ProxyObject，同一写法经 putMember seam 落到同一 setter。</li>
     * </ol>
     */
    @Test
    void hostPropertyWriteLegacyFactAndProxyObjectViewContract() {
        PlainHostView plain = new PlainHostView();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("plain", plain);
            String outcome = context.eval("js",
                    "(function () {" +
                    "  try { plain.maxStackSize = 16; } catch (e) { return 'threw:' + e.constructor.name; }" +
                    "  try { return 'assigned,read=' + plain.maxStackSize + ',pending=' + plain.getMaxStackSize(); }" +
                    "  catch (e2) { return 'assigned,read-threw:' + e2.constructor.name; }" +
                    "})()").asString();
            assertTrue(outcome.startsWith("threw:") || outcome.startsWith("assigned"),
                    "unexpected probe outcome: " + outcome);
            assertNull(plain.getMaxStackSize(),
                    "旧事实：普通宿主对象的 property 写不得到达 setter（outcome=" + outcome + "）");
        }

        // 现行契约：视图自己实现 ProxyObject，裸视图上的同一写法到达同一 setter
        ItemModificationJS view = new ItemModificationJS();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("item", view);
            context.eval("js", "item.maxStackSize = 16; item.setRarity('epic');");
        }
        assertEquals(16, view.getMaxStackSize().intValue(),
                "视图实现 ProxyObject 后，宿主形态的 property 写必须到达同一 setter");
        assertEquals("epic", view.getRarity());
    }

    /** 普通宿主对象替身（旧路径视图形态：私有字段 + public setter，无 ProxyObject）。 */
    public static final class PlainHostView {
        private Integer maxStackSize;

        public Integer getMaxStackSize() {
            return maxStackSize;
        }

        public void setMaxStackSize(int maxStackSize) {
            this.maxStackSize = maxStackSize;
        }
    }

    /**
     * 最小 Adapter：在内存目标上执行 {@code ModificationApplier} 契约——先恢复基线，
     * 再按声明顺序应用（每条声明是「只含显式属性」的完整集合，同目标后声明整体替换）。
     */
    private static final class RecordingApplier implements ModificationApplier {
        private final Map<String, Object> baseline;
        private final Map<String, Object> target = new LinkedHashMap<>();
        private final List<ModificationDeclaration> applied = new ArrayList<>();
        private final List<String> appliedScriptIds = new ArrayList<>();

        RecordingApplier(Map<String, Object> baseline) {
            this.baseline = new LinkedHashMap<>(baseline);
        }

        @Override
        public String adapterId() {
            return "plan-layer-test";
        }

        @Override
        public void preflight(List<ModificationDeclaration> declarations) {
            // 计划层 fixture：不解析目标（注册表解析归 Adapter 的真实实现）
        }

        @Override
        public void apply(List<ModificationDeclaration> declarations) {
            target.clear();
            target.putAll(baseline); // (1) 恢复 NekoJS 持有基线
            for (ModificationDeclaration declaration : declarations) {
                applied.add(declaration);
                appliedScriptIds.add(declaration.scriptId());
                // (2) 每条声明从基线独立求值后整表写入 live 目标——与真实
                // ModificationDomainOwner#applyItem 同构（builder = addAll(base) + 声明属性 → 整表写回），
                // 因此同目标后声明整体覆盖前声明，前声明的其它属性不残留。
                Map<String, Object> evaluated = new LinkedHashMap<>(baseline);
                evaluated.putAll(declaration.properties());
                target.clear();
                target.putAll(evaluated);
            }
        }
    }
}
