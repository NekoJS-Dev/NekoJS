package com.tkisor.nekojs.core.posteffect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 28 声明生命周期机制 fixture（common 层，零 MC）：候选计划 inert、整批成败、
 * 声明移除退役语义、失败不触达 Adapter、fingerprint 与 parity。
 *
 * <p>本 fixture 只断言 common 接缝（计划 + {@link PostEffectApplier}）；平台 Adapter 的
 * 真实效果贯穿面在版本树 {@code PostEffectDeclarationLifecycleTest}。
 */
class PostEffectCandidatePlanTest {

    /** 记录调用的合成 Adapter（不引用任何 MC 类型）。 */
    private static final class RecordingApplier implements PostEffectApplier {
        final List<List<PostEffectDeclaration>> applied = new ArrayList<>();
        final List<List<PostEffectDeclaration>> preflighted = new ArrayList<>();
        String rejectReason;

        @Override
        public String adapterId() {
            return "test-adapter";
        }

        @Override
        public void preflight(List<PostEffectDeclaration> declarations) {
            preflighted.add(List.copyOf(declarations));
            if (rejectReason != null) {
                throw new IllegalArgumentException(rejectReason);
            }
        }

        @Override
        public void apply(List<PostEffectDeclaration> declarations) {
            applied.add(List.copyOf(declarations));
        }
    }

    private static PostEffectCandidatePlan batch(RecordingApplier applier, Set<String> previous) {
        return PostEffectCandidatePlan.beginBatch(applier, 7L, previous);
    }

    @Test
    void collectingDeclarationsIsInertUntilPublish() {
        RecordingApplier applier = new RecordingApplier();
        PostEffectCandidatePlan plan = batch(applier, Set.of());

        plan.install(PostEffectDeclaration.install("nekojs:gray", "{}", java.util.Map.of(), java.util.Map.of()));
        plan.finish();

        assertTrue(applier.preflighted.isEmpty(), "collection must not preflight on the Adapter");
        assertTrue(applier.applied.isEmpty(), "candidate collection must not apply anything");
        assertEquals(1, plan.installCount());

        plan.preflight();
        assertEquals(1, applier.preflighted.size(), "preflight reaches the Adapter once");
        assertTrue(applier.applied.isEmpty(), "preflight must not apply");

        plan.publish();
        assertEquals(1, applier.applied.size(), "publish applies exactly once");
        assertEquals("nekojs:gray", applier.applied.get(0).get(0).id());
    }

    @Test
    void collectionFailureFailsTheWholeBatchBeforeTheAdapter() {
        RecordingApplier applier = new RecordingApplier();
        PostEffectCandidatePlan plan = batch(applier, Set.of());
        plan.install(PostEffectDeclaration.install("nekojs:gray", "{}", java.util.Map.of(), java.util.Map.of()));
        plan.fail("invalid effect id: not a resource id");
        plan.finish();

        IllegalStateException failure = assertThrows(IllegalStateException.class, plan::preflight);
        assertTrue(failure.getMessage().contains("invalid effect id"), failure.getMessage());
        assertTrue(applier.preflighted.isEmpty(),
                "a poisoned batch never reaches the Adapter (whole batch fails)");
        assertFalse(plan.declarations().isEmpty(), "the collected declaration stays observable for diagnostics");
    }

    @Test
    void adapterPreflightRejectionKeepsTheBatchUnpublished() {
        RecordingApplier applier = new RecordingApplier();
        applier.rejectReason = "Invalid post-effect chain JSON for nekojs:gray";
        PostEffectCandidatePlan plan = batch(applier, Set.of());
        plan.install(PostEffectDeclaration.install("nekojs:gray", "not json", java.util.Map.of(), java.util.Map.of()));
        plan.finish();

        assertThrows(IllegalArgumentException.class, plan::preflight);
        assertTrue(applier.applied.isEmpty(), "a rejected batch is never applied");
    }

    @Test
    void previousGenerationIdsAreRetiredWhenNotRedeclared() {
        RecordingApplier applier = new RecordingApplier();
        PostEffectCandidatePlan plan = batch(applier, Set.of("nekojs:old", "nekojs:kept"));
        plan.install(PostEffectDeclaration.install("nekojs:kept", "{}", java.util.Map.of(), java.util.Map.of()));
        plan.finish();

        List<PostEffectDeclaration> declarations = plan.declarations();
        assertEquals(2, declarations.size());
        assertEquals("nekojs:kept", declarations.get(0).id());
        assertEquals(PostEffectDeclaration.Kind.INSTALL, declarations.get(0).kind());
        assertEquals("nekojs:old", declarations.get(1).id());
        assertEquals(PostEffectDeclaration.Kind.RETIRE, declarations.get(1).kind(),
                "an id the previous generation owned and this batch does not declare is retired");
    }

    @Test
    void explicitUnregisterRetiresWithoutDoubleListing() {
        RecordingApplier applier = new RecordingApplier();
        PostEffectCandidatePlan plan = batch(applier, Set.of("nekojs:old"));
        plan.install(PostEffectDeclaration.install("nekojs:gray", "{}", java.util.Map.of(), java.util.Map.of()));
        plan.retireDeclared("nekojs:gray");
        plan.retireDeclared("nekojs:old");
        plan.finish();

        List<PostEffectDeclaration> declarations = plan.declarations();
        assertEquals(2, declarations.size(),
                "explicit unregister drops the install; the previous id is retired once, not twice");
        assertEquals(PostEffectDeclaration.Kind.RETIRE, declarations.get(0).kind());
        assertEquals("nekojs:gray", declarations.get(0).id());
        assertEquals(PostEffectDeclaration.Kind.RETIRE, declarations.get(1).kind());
        assertEquals("nekojs:old", declarations.get(1).id());
        assertEquals(0, plan.installCount());
    }

    @Test
    void fingerprintIsStableAcrossShaderMapOrderAndChangesWithContent() {
        PostEffectCandidatePlan first = batch(new RecordingApplier(), Set.of());
        first.install(PostEffectDeclaration.install("nekojs:gray", "{}",
                java.util.Map.of("a:one", "x", "a:two", "y"), java.util.Map.of()));
        first.finish();

        java.util.Map<String, String> reordered = new java.util.LinkedHashMap<>();
        reordered.put("a:two", "y");
        reordered.put("a:one", "x");
        PostEffectCandidatePlan second = batch(new RecordingApplier(), Set.of());
        second.install(PostEffectDeclaration.install("nekojs:gray", "{}", reordered, java.util.Map.of()));
        second.finish();

        assertEquals(first.fingerprint(), second.fingerprint(),
                "shader map iteration order must not change the declaration identity");

        PostEffectCandidatePlan third = batch(new RecordingApplier(), Set.of());
        third.install(PostEffectDeclaration.install("nekojs:gray", "{\"a\":1}", java.util.Map.of(), java.util.Map.of()));
        third.finish();
        assertFalse(third.fingerprint().equals(first.fingerprint()), "different chain JSON is a different plan");
    }

    @Test
    void unfrozenBatchCannotPassPreflight() {
        RecordingApplier applier = new RecordingApplier();
        PostEffectCandidatePlan plan = batch(applier, Set.of());
        plan.install(PostEffectDeclaration.install("nekojs:gray", "{}", java.util.Map.of(), java.util.Map.of()));

        IllegalStateException failure = assertThrows(IllegalStateException.class, plan::preflight);
        assertTrue(failure.getMessage().contains("never finished"), failure.getMessage());
    }

    @Test
    void retireDeclarationRejectsBlankAndInstallRejectsRetireKind() {
        assertThrows(IllegalArgumentException.class, () -> PostEffectDeclaration.retire("  "));
        PostEffectCandidatePlan plan = batch(new RecordingApplier(), Set.of());
        assertThrows(IllegalArgumentException.class,
                () -> plan.install(PostEffectDeclaration.retire("nekojs:gray")));
    }

    @Test
    void domainAndScriptTypeIdentifyTheClientDeclarationDomain() {
        assertEquals("post-effects", PostEffectCandidatePlan.DOMAIN);
        PostEffectCandidatePlan plan = batch(new RecordingApplier(), Set.of());
        assertEquals(PostEffectCandidatePlan.DOMAIN, plan.domain());
        assertEquals(7L, plan.generation());
        assertSame(plan, plan);
        assertNull(plan.collectionError());
    }
}
