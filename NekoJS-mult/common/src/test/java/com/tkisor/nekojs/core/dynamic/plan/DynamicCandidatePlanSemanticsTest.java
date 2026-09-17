package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.core.dynamic.DynamicRegisterMode;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import com.tkisor.nekojs.core.state.GlobalStateException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 16 候选计划语义（AC5/AC6/AC7/AC8）：fingerprint 重复 reload 稳定、同 key 定义变化
 * 整批冲突失败且旧 active 继续服务、失败/取消不发布、stale/retired 不物理删除、
 * claim/stale/mode 可观察、Adapter 请求 generation-scoped 且 inert。
 *
 * <p>全部断言走公开观察面（{@link DynamicRegistryPlanStore} 的查询与
 * {@link DynamicCandidateRegistryPlan} 的结果），不断言私有字段。
 */
class DynamicCandidatePlanSemanticsTest {

    private static DynamicDefinition item(String id, int stackSize, String rarity) {
        DynamicItemBuilder builder = new DynamicItemBuilder().setMaxStackSize(stackSize);
        if (rarity != null) {
            builder.setRarity(rarity);
        }
        return DynamicDefinition.of(DynamicDefinitionType.ITEM, id, builder);
    }

    private static DynamicDefinition sound(String id) {
        return DynamicDefinition.of(DynamicDefinitionType.SOUND_EVENT, id, new DynamicSoundEventBuilder());
    }

    /** 经事件 payload 同款入口（plan.add）收集一条 item 定义。 */
    private static void declare(DynamicCandidateRegistryPlan plan, String id, int stackSize, String rarity) {
        DynamicItemBuilder builder = new DynamicItemBuilder().setMaxStackSize(stackSize);
        if (rarity != null) {
            builder.setRarity(rarity);
        }
        plan.add(DynamicDefinitionType.ITEM, id, builder, "server_scripts/packs/test/main.js", "event.item");
    }

    @Test
    void sameDefinitionAcrossReloadsGetsSameFingerprintAndReclaims() {
        DynamicRegistryPlanStore store = new DynamicRegistryPlanStore();

        DynamicCandidateRegistryPlan first = store.beginBatch();
        declare(first, "mymod:ruby", 16, "epic");
        DynamicDefinition firstDefinition = first.definitions().get(0);
        first.preflight();
        first.publish();
        assertTrue(first.isPublished(), "成功 commit 才发布计划");
        assertEquals(1, store.committedGeneration(), "store 记录已发布 generation");

        // 重复 reload：全新 builder 实例、相同声明 → 同 fingerprint → 不冲突、重 claim
        DynamicCandidateRegistryPlan second = store.beginBatch();
        declare(second, "mymod:ruby", 16, "epic");
        assertEquals(firstDefinition.fingerprint(), second.definitions().get(0).fingerprint(),
                "AC5：相同定义在重复 reload 中得到相同 fingerprint");
        second.preflight();
        second.publish();

        var claim = store.claimOf(DynamicDefinitionType.ITEM, "mymod:ruby");
        assertFalse(claim.stale(), "重声明即重 claim，stale 清除");
        assertEquals(DynamicRegisterMode.WORLD, claim.mode(), "claim 的 mode 可观察");
        assertEquals("server_scripts/packs/test/main.js", claim.ownerScriptId(), "claim 的 owner 可观察");
    }

    @Test
    void sameKeyDefinitionChangeFailsTheWholeBatchAndOldActiveKeepsServing() {
        DynamicRegistryPlanStore store = new DynamicRegistryPlanStore();
        DynamicCandidateRegistryPlan active = store.beginBatch();
        declare(active, "mymod:ruby", 16, "epic");
        declare(active, "mymod:boom", 1, null); // 另一条无关定义，验证「整批」失败
        active.preflight();
        active.publish();
        String oldFingerprint = store.exposedEntry("minecraft:item|mymod:ruby").definition().fingerprint();

        // 变更定义（maxStackSize 16 → 32）：preflight 必须以整批冲突失败
        DynamicCandidateRegistryPlan changed = store.beginBatch();
        declare(changed, "mymod:ruby", 32, "epic");
        declare(changed, "mymod:boom", 1, null);
        GlobalStateException conflict = assertThrows(GlobalStateException.class, changed::preflight);
        assertEquals("dynamic-registry-conflict", conflict.domain(), "失败 domain 精确归因");
        assertTrue(conflict.getMessage().contains("minecraft:item|mymod:ruby"), conflict.getMessage());
        assertTrue(conflict.getMessage().contains("the whole batch fails"), conflict.getMessage());

        // 旧 active 继续服务：exposed 指纹不动、claim 不动、批次未发布
        assertEquals(oldFingerprint, store.exposedEntry("minecraft:item|mymod:ruby").definition().fingerprint(),
                "旧 active 定义继续服务（账本未被触碰）");
        assertEquals(1, store.committedGeneration(), "失败批次没有发布");
        assertFalse(changed.isPublished());
        assertFalse(store.claimOf(DynamicDefinitionType.ITEM, "mymod:ruby").stale(),
                "失败的 reload 不把旧 active 标成 stale");
        assertThrows(IllegalStateException.class, changed::publish,
                "未过 preflight 的计划不允许直接 publish（防止绕过联合边界）");
    }

    @Test
    void sameBatchDuplicateWithDifferentDefinitionFailsAtCollection() {
        DynamicRegistryPlanStore store = new DynamicRegistryPlanStore();
        DynamicCandidateRegistryPlan plan = store.beginBatch();
        declare(plan, "mymod:dup", 16, null);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> declare(plan, "mymod:dup", 32, null));
        assertTrue(error.getMessage().contains("Duplicate declaration of 'minecraft:item|mymod:dup'"), error.getMessage());
        assertEquals(1, plan.definitions().size(), "冲突的第二条不进入批次");

        // 同 fingerprint 的重复声明是幂等去重（同批两个脚本声明同一配置）
        DynamicDefinition idempotent = plan.add(DynamicDefinitionType.ITEM, "mymod:dup",
                new DynamicItemBuilder().setMaxStackSize(16), "other.js", "event.item");
        assertSame(plan.definitions().get(0), idempotent, "同 fingerprint 重复声明返回既有条目");
    }

    @Test
    void collectionErrorPoisonsTheWholeBatch() {
        DynamicRegistryPlanStore store = new DynamicRegistryPlanStore();
        DynamicCandidateRegistryPlan good = store.beginBatch();
        declare(good, "mymod:ok", 16, null);
        good.preflight();
        good.publish();

        DynamicCandidateRegistryPlan poisoned = store.beginBatch();
        declare(poisoned, "mymod:ok", 16, null);
        poisoned.noteCollectionError("server_scripts/bad.js",
                new IllegalArgumentException("Invalid id 'NOT:LOWER': namespace and path must be lowercase"));
        assertTrue(poisoned.hasCollectionErrors());
        GlobalStateException failure = assertThrows(GlobalStateException.class, poisoned::preflight);
        assertEquals("dynamic-registry-collection", failure.domain());
        assertTrue(failure.getMessage().contains("Invalid id 'NOT:LOWER'"), failure.getMessage());
        assertFalse(poisoned.isPublished(), "毒化批次不发布（失败不另起写入）");
        assertEquals(good.generation(), store.committedGeneration(), "store 停留在上一个成功批次");
    }

    @Test
    void undeclaredExposedEntriesBecomeStaleButAreNotPhysicallyDeleted() {
        DynamicRegistryPlanStore store = new DynamicRegistryPlanStore();
        DynamicCandidateRegistryPlan first = store.beginBatch();
        declare(first, "mymod:ruby", 16, null);
        DynamicDefinition boom = sound("mymod:boom");
        first.add(DynamicDefinitionType.SOUND_EVENT, "mymod:boom", new DynamicSoundEventBuilder(),
                "server_scripts/packs/test/main.js", "event.soundEvent");
        first.preflight();
        first.publish();

        // 下一轮只声明 ruby：boom 变 stale/retired，但账目与 fingerprint 保留（不物理删除）
        DynamicCandidateRegistryPlan second = store.beginBatch();
        declare(second, "mymod:ruby", 16, null);
        second.preflight();
        second.publish();

        assertEquals(List.of("mymod:boom"), store.staleIds(DynamicDefinitionType.SOUND_EVENT),
                "脚本不再声明的已暴露项标记 stale");
        assertEquals(List.of("minecraft:sound_event|mymod:boom"), store.retiredKeys(), "retired 查询可观察");
        var exposedBoom = store.exposedEntry("minecraft:sound_event|mymod:boom");
        assertEquals(boom.fingerprint(), exposedBoom.definition().fingerprint(),
                "普通 reload 不物理删除：exposed 指纹保留");

        // 对 stale/retired 项的定义变化（replace 尝试）：同样整批冲突——fingerprint 账保留使
        // 「已暴露 key 的定义变化」永远冲突，不因 stale 而放开 replace（第一版无 remove/replace）
        DynamicCandidateRegistryPlan replaceAttempt = store.beginBatch();
        replaceAttempt.add(DynamicDefinitionType.SOUND_EVENT, "mymod:boom",
                new DynamicSoundEventBuilder().setFixedRange(64f), "server_scripts/packs/test/main.js",
                "event.soundEvent");
        GlobalStateException replaceConflict = assertThrows(GlobalStateException.class,
                replaceAttempt::preflight);
        assertEquals("dynamic-registry-conflict", replaceConflict.domain());
        assertTrue(replaceConflict.getMessage().contains("stale/retired"),
                "对 stale 项的 replace 同样整批冲突——replace 不在第一版: "
                        + replaceConflict.getMessage());
        assertFalse(replaceAttempt.isPublished(), "冲突批次不发布");

        // stale 项重新声明：同 fingerprint → 重 claim（stale 清除）
        DynamicCandidateRegistryPlan reclaim = store.beginBatch();
        reclaim.add(DynamicDefinitionType.SOUND_EVENT, "mymod:boom", new DynamicSoundEventBuilder(),
                "server_scripts/packs/test/main.js", "event.soundEvent");
        reclaim.preflight();
        reclaim.publish();
        assertTrue(store.staleIds(DynamicDefinitionType.SOUND_EVENT).isEmpty(), "同定义重声明恢复 claim");
    }

    @Test
    void adapterRequestsAreGenerationScopedAndInertByConstruction() {
        DynamicRegistryPlanStore store = new DynamicRegistryPlanStore();
        DynamicCandidateRegistryPlan plan = store.beginBatch();
        declare(plan, "mymod:ruby", 16, "epic");
        declare(plan, "mymod:boom", 1, null);

        List<DynamicAdapterRequest> requests = plan.adapterRequests();
        assertEquals(2, requests.size());
        for (DynamicAdapterRequest request : requests) {
            assertEquals(plan.generation(), request.generation(), "Adapter 请求 generation-scoped");
            assertEquals(DynamicAdapterRequest.ACTION_REGISTER, request.action(),
                    "动作集合封闭为 register（无 remove/replace/modify）");
            assertTrue(DynamicDefinition.supportedRegistryKeys().contains(request.registryKey()),
                    "候选范围只在 Item/SoundEvent/MobEffect 的注册表键内");
        }
        assertNotEquals(0, plan.generation(), "generation 随批次递增");

        // preflight 失败的批次：请求对象存在但 store 永远收不到（无发布路径）
        DynamicCandidateRegistryPlan failing = store.beginBatch();
        declare(failing, "mymod:conflict", 16, null);
        DynamicCandidateRegistryPlan committed = store.beginBatch();
        declare(committed, "mymod:conflict", 16, null);
        committed.preflight();
        committed.publish();
        failing.noteCollectionError("x.js", new IllegalStateException("poison"));
        assertThrows(GlobalStateException.class, failing::preflight);
        assertEquals(committed.generation(), store.committedGeneration(),
                "失败批次的 generation 不进入 committed 观察");
    }

    @Test
    void planImplementsTheJointCandidateBoundary() {
        DynamicRegistryPlanStore store = new DynamicRegistryPlanStore();
        DynamicCandidateRegistryPlan plan = store.beginBatch();
        declare(plan, "mymod:ruby", 16, null);
        assertInstanceOf2(CandidateStatePlan.class, plan, "计划可挂入票 10 联合边界");
        assertEquals("dynamic-registry", plan.domain());
    }

    private static void assertInstanceOf2(Class<?> expected, Object value, String message) {
        assertTrue(expected.isInstance(value), message);
    }

    @Test
    void fingerprintCoversEveryBuilderInputTypeAndId() {
        // AC5 全规范化：三个类型的全部可写属性 + mode（约定连带声明）+ 类型 + id 都参与指纹
        DynamicDefinition soundDefault = DynamicDefinition.of(DynamicDefinitionType.SOUND_EVENT,
                "mymod:boom", new DynamicSoundEventBuilder());
        DynamicDefinition soundRanged = DynamicDefinition.of(DynamicDefinitionType.SOUND_EVENT,
                "mymod:boom", new DynamicSoundEventBuilder().setFixedRange(16f));
        assertTrue(soundDefault.hasReading("fixedRange", "null"), "抑制态（null）是规范化读数之一");
        assertNotEquals(soundDefault.fingerprint(), soundRanged.fingerprint(),
                "soundEvent 字段（fixedRange）变化可识别");

        DynamicDefinition effect = DynamicDefinition.of(DynamicDefinitionType.MOB_EFFECT,
                "mymod:touch", new DynamicMobEffectBuilder());
        assertNotEquals(effect.fingerprint(), DynamicDefinition.of(DynamicDefinitionType.MOB_EFFECT,
                        "mymod:touch", new DynamicMobEffectBuilder().setColor(0x8B0000)).fingerprint(),
                "mobEffect 字段（color）变化可识别");
        assertNotEquals(effect.fingerprint(), DynamicDefinition.of(DynamicDefinitionType.MOB_EFFECT,
                        "mymod:touch", new DynamicMobEffectBuilder().setCategory("harmful")).fingerprint(),
                "mobEffect 字段（category）变化可识别");
        assertNotEquals(effect.fingerprint(), DynamicDefinition.of(DynamicDefinitionType.MOB_EFFECT,
                        "mymod:touch", new DynamicMobEffectBuilder().setMode("reloadable")).fingerprint(),
                "约定连带声明（mode）变化可识别");

        // 类型与 id 参与指纹/key：同 id 不同类型不碰撞
        DynamicDefinition sameIdItem = DynamicDefinition.of(DynamicDefinitionType.ITEM, "mymod:touch",
                new DynamicItemBuilder());
        assertNotEquals(effect.fingerprint(), sameIdItem.fingerprint(), "类型参与指纹");
        assertNotEquals(effect.key(), sameIdItem.key(), "key 含目标注册表维度");

        // 读数是全量可写属性（不抽部分字段），字典序、规范化默认值
        assertEquals(DynamicBuilderContract.of(DynamicMobEffectBuilder.class).writableNames().size(),
                effect.readings().size(), "全量可写属性参与读数");
        assertEquals(List.of("category=neutral", "color=16777215", "mode=world"), effect.readings());
        assertEquals(64, effect.fingerprint().length(), "SHA-256 hex");
    }

    @Test
    void idNormalizationFollowsVanillaIdentifierSemantics() {
        DynamicRegistryPlanStore store = new DynamicRegistryPlanStore();
        DynamicCandidateRegistryPlan plan = store.beginBatch();
        // 无命名空间 → minecraft:；首尾空白剥除
        DynamicDefinition bare = plan.add(DynamicDefinitionType.ITEM, "  ruby  ",
                new DynamicItemBuilder(), "t.js", "event.item");
        assertEquals("minecraft:ruby", bare.id());
        // trim 后非小写/非法字符拒绝
        assertThrows(IllegalArgumentException.class,
                () -> plan.add(DynamicDefinitionType.ITEM, "MyMod:Ruby", new DynamicItemBuilder(), "t.js", "e"));
        assertThrows(IllegalArgumentException.class,
                () -> plan.add(DynamicDefinitionType.ITEM, "  ", new DynamicItemBuilder(), "t.js", "e"));
        assertThrows(IllegalArgumentException.class,
                () -> plan.add(DynamicDefinitionType.ITEM, "ns:", new DynamicItemBuilder(), "t.js", "e"));
        assertThrows(IllegalArgumentException.class,
                () -> plan.add(DynamicDefinitionType.ITEM, ":path", new DynamicItemBuilder(), "t.js", "e"));
        assertNull(store.exposedEntry("minecraft:item|minecraft:ruby"), "候选期不写入 store");
    }
}
