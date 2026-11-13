// Characterization of the old path, now replayed deterministically against the refactor.
// Facts measured on the old code (the direct modification path):
//   1) Within one replay, several declarations for the same target: the later declaration
//      replaces the earlier one entirely (restore-before-modify returns to the baseline each time).
//   2) Block replays restore the whole snapshot first; item restores only on the next modify,
//      so an item whose declaration disappeared kept a stale modification.
//   3) A GraalJS property write on a plain host object was silently dropped (probe result
//      assigned,read=undefined,pending=null, never reaching the setter, so the javadoc example
//      never worked on the old path; the probe test in this file reproduces it today).
// After the refactor (candidate plan + Adapter): (1) declaration order and whole-replacement
// semantics are pinned both at the plan layer and end to end (this file plus the common-layer
// Ticket39DomainCollectionTest); (2) item and block both restore the baseline first and then
// apply the complete new plan, a behavior change recorded in the migration table; (3) property
// writes reach the same setter through ModificationViewSurface
// (ModificationSetterPropertyParityTest, including the production delivery form).
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
 * Replays the old observable order deterministically after the refactor: several declarations
 * for the same target keep the whole-replacement semantics verbatim (same-key declarations are
 * not rejected and no undecided merge policy is added). Item declarations that are removed no
 * longer stay stale.
 *
 * <p>registry-free part (plan layer, run on five nodes): declaration order is preserved,
 * declarations are applied in declaration order, and a later declaration replaces an earlier
 * one entirely (the view emits only explicitly set properties, and the Adapter rule is
 * "restore the baseline first, then apply in declaration order"). The registry-gated part
 * (end to end, vanilla registries) checks the same semantics on real Item components.
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

    /** Collects one plan and runs it through the Adapter's preflight and apply. */
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
        // Deterministic replay of fact 1: the first declaration sets maxStackSize and rarity, the
        // second sets only maxStackSize, so the result is the baseline plus the second declaration
        // (rarity does not survive; the declaration replaces the earlier one, it does not merge
        // per property).
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

        // Replay after the declaration disappears: applying an empty plan restores the baseline. The
        // old item path stayed stale; that is closed now, the behavior change is recorded in the
        // migration table, and the old block path already restored, so both sides now behave the same.
        collect(event -> {});

        assertEquals(64, Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1),
                "声明移除后恢复 NekoJS 持有基线（不再静默 stale）");
        assertEquals(ModificationDomainOwner.Outcome.RESTORED, owner.lastDiagnostics().outcome(),
                "诊断区分 restored（不把静默 stale 当成功）");
    }

    // ============ registry-free: plan layer, declaration order and whole replacement ============

    /**
     * Deterministic replay of several declarations for the same target at the <b>plan layer</b>:
     * declarations are kept in registration order (same-key declarations are not rejected), the
     * Adapter receives all of them in declaration order, and "the view declaration holds only
     * explicitly set properties" combined with "the Adapter restores the baseline first, then
     * applies in declaration order" produces the old observable result: a later declaration
     * replaces an earlier one entirely, so the earlier declaration's other properties do not
     * survive.
     *
     * <p>Declarations come from a real {@link ItemModificationJS} (Java setters, no Graal
     * needed). The target storage and the "restore baseline, then apply in declaration order"
     * rule live in a minimal {@link ModificationApplier}; the same semantics on real Item
     * components are covered by the registry-gated test above and the common-layer
     * {@code Ticket39DomainCollectionTest}.
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
     * Registry-free probe of the old fact 3, the characterization premise:
     * <ol>
     *   <li>A <b>plain host object</b> (private field plus a public setter, no ProxyObject) has
     *       its property write silently dropped by GraalJS. The view looked like this before the
     *       refactor, which is why the javadoc example never worked;</li>
     *   <li>The reworked view implements ProxyObject, so the same write lands on the same setter
     *       through the putMember hook.</li>
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

        // Current behavior: the view implements ProxyObject itself, so the same write on a bare view
        // reaches the same setter
        ItemModificationJS view = new ItemModificationJS();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("item", view);
            context.eval("js", "item.maxStackSize = 16; item.setRarity('epic');");
        }
        assertEquals(16, view.getMaxStackSize().intValue(),
                "视图实现 ProxyObject 后，宿主形态的 property 写必须到达同一 setter");
        assertEquals("epic", view.getRarity());
    }

    /** A stand-in plain host object: private field plus a public setter, no ProxyObject. */
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
     * Minimal Adapter: implements {@code ModificationApplier} on an in-memory target. It
     * restores the baseline first, then applies the declarations in order; each declaration is
     * a complete set holding only explicitly set properties, so a later declaration for the
     * same target replaces the earlier one entirely.
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
            // Plan-layer fixture: it does not resolve targets (registry resolution belongs to the real Adapter)
        }

        @Override
        public void apply(List<ModificationDeclaration> declarations) {
            target.clear();
            target.putAll(baseline); // (1) Restore the baseline NekoJS holds
            for (ModificationDeclaration declaration : declarations) {
                applied.add(declaration);
                appliedScriptIds.add(declaration.scriptId());
                // (2) Evaluate each declaration independently from the baseline and write the whole map to the
                // live target, the same shape as the real ModificationDomainOwner#applyItem
                // (builder = addAll(base) + declared properties, then write the whole map back). So a
                // later declaration for the same target covers the earlier one entirely and the earlier
                // declaration's other properties do not survive.
                Map<String, Object> evaluated = new LinkedHashMap<>(baseline);
                evaluated.putAll(declaration.properties());
                target.clear();
                target.putAll(evaluated);
            }
        }
    }
}
