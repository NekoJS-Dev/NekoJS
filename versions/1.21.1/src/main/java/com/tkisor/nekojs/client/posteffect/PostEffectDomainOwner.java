// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
// 脚本面的 @Doc/@Param 文案两侧本就不同，探针类型 golden 会校验，别照抄主干的措辞。
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.posteffect.PostEffectApplier;
import com.tkisor.nekojs.core.posteffect.PostEffectCandidatePlan;
import com.tkisor.nekojs.core.posteffect.PostEffectDeclaration;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Post-effect declaration lifecycle owner and client-side Adapter (ticket 28): a
 * root-authorized {@link CandidateDomainCollector} that separates the <b>declaration</b>
 * lifecycle from the runtime binding.
 *
 * <p><b>Collection (candidate, {@code ReloadPhase.DOMAIN_PLAN}).</b> {@link #collect}
 * dispatches {@code ClientEvents.postEffects} into the <b>candidate's</b> pending listeners,
 * collects {@code register}/{@code unregister} declarations into an inert
 * {@link PostEffectCandidatePlan} and joins it to the candidate's joint preflight/publish
 * boundary. Nothing here mounts a production listener or touches the current client frame.
 *
 * <p><b>Commit (single point).</b> {@link PostEffectCandidatePlan#publish()} calls
 * {@link #apply} exactly once on the owner thread; here the whole new generation is
 * installed and the previous generation's definitions are released, so no old-generation
 * definition survives and no double registration or half update can happen. A failed or
 * interrupted candidate never reaches {@code apply} — the previous active generation keeps
 * serving.
 *
 * <p><b>Runtime binding parity.</b> {@code PostEffects.set/clear/toggle/current} keep their
 * previous caller-visible behaviour and are not replaced by this event.
 */
public final class PostEffectDomainOwner implements CandidateDomainCollector, PostEffectApplier {

    /** Collector identity (enters the structured reload failure as {@code domain-collect:<id>}). */
    public static final String DOMAIN = PostEffectCandidatePlan.DOMAIN;

    private volatile long activeGeneration = -1L;
    private volatile Set<String> activeIds = Set.of();
    private volatile Diagnostics lastDiagnostics =
            new Diagnostics(Outcome.INITIAL, "none", -1L, 0, 0, List.of(), null);

    public PostEffectDomainOwner() {
    }

    // ---- CandidateDomainCollector（DOMAIN_PLAN 阶段） ----

    @Override
    public String domain() {
        return DOMAIN;
    }

    /** CLIENT-only domain: the collector never runs in SERVER/STARTUP/TEST candidates. */
    @Override
    public com.tkisor.nekojs.api.ScriptType scriptType() {
        return com.tkisor.nekojs.api.ScriptType.CLIENT;
    }

    @Override
    public void collect(CandidateDomainCollector.Handle handle) {
        // 批次 generation 是领域账本自己的序号（初始收集点与事务 commit 共用同一条递增线）：
        // 领域计划在 commit 前不可见，用它当诊断/指纹标签而不是 ScriptManager 的 generation。
        PostEffectCandidatePlan plan = PostEffectCandidatePlan.beginBatch(
                this, activeGeneration + 1L, activeIds);
        try {
            handle.dispatch(ClientEvents.POST_EFFECTS, new PostEffectEventJS(plan));
        } finally {
            // 即使某个监听器回调抛出（候选失败），计划也已冻结，失败的候选不再累积声明。
            plan.finish();
        }
        handle.registerPlan(plan);
    }

    // ---- 初始 generation（客户端脚本初次加载后的收集点） ----

    /**
     * Initial CLIENT generation collection point: the non-transactional
     * {@code loadScripts()} path has no candidate/commit, so the declarations are dispatched
     * to the <b>active</b> bus and applied in this owner after a passing preflight. A
     * rejected batch ({@link Outcome#BLOCKED}) keeps the previous active generation and never
     * half-applies. Unlike the candidate path it has no joint boundary to join, so the whole
     * batch is validated here and installed in one step.
     */
    public void applyInitialPlan() {
        PostEffectCandidatePlan plan = PostEffectCandidatePlan.beginBatch(
                this, activeGeneration + 1L, activeIds);
        try {
            ClientEvents.POST_EFFECTS.post(new PostEffectEventJS(plan));
        } catch (Throwable dispatchFailure) {
            plan.finish();
            lastDiagnostics = new Diagnostics(Outcome.RECOVERY_FAILED, "initial-dispatch",
                    activeGeneration, 0, 0, List.of(), String.valueOf(dispatchFailure));
            NekoJS.LOGGER.error("NekoJS post-effect initial dispatch failed; keeping the previous"
                    + " active generation", dispatchFailure);
            return;
        }
        plan.finish();
        try {
            plan.preflight();
        } catch (Throwable rejected) {
            lastDiagnostics = new Diagnostics(Outcome.BLOCKED, "initial", activeGeneration,
                    plan.installCount(), 0, List.of(), String.valueOf(rejected));
            NekoJS.LOGGER.error("NekoJS post-effect initial batch rejected (keeping the previous"
                    + " active generation): {}", rejected.toString());
            return;
        }
        apply(plan.declarations());
    }

    // ---- PostEffectApplier：联合预检 / commit 点应用 ----

    @Override
    public String adapterId() {
        return "1.21.1-client";
    }

    /**
     * Candidate preflight: parses every declared chain (the same validation the old
     * {@code register} binding performed) and rejects the whole batch on the first invalid
     * payload. No live state is touched.
     */
    /**
     * Candidate preflight: parses every declared chain and rejects the whole batch on the first
     * invalid payload.
     *
     * <p><b>Publishes nothing.</b> A candidate preflight that passes is not an outcome: another
     * domain's preflight, a joint STATE_PLAN conflict or a close preemption can still discard
     * the whole candidate, and {@link #lastDiagnostics()} is a public read face for what the
     * <b>active</b> generation is. Publishing "preflight passed" here would leave a
     * "preflight passed but never committed" observation behind. The commit point re-parses and
     * publishes {@link Outcome#APPLIED}.
     */
    @Override
    public void preflight(List<PostEffectDeclaration> declarations) {
        if (declarations == null || declarations.isEmpty()) return;
        for (PostEffectDeclaration declaration : declarations) {
            if (declaration.kind() != PostEffectDeclaration.Kind.INSTALL) continue;
            ResourceLocation id = ResourceLocation.tryParse(declaration.id());
            if (id == null) {
                throw new IllegalArgumentException("Invalid post-effect id: " + declaration.id());
            }
            if (PostEffectManager.parseDefinition(id, declaration.chainJson()) == null) {
                throw new IllegalArgumentException(
                        "Invalid post-effect chain JSON for " + declaration.id());
            }
        }
    }

    /**
     * Commit point (called by the joint publish): installs the full new generation and
     * retires everything the batch did not declare. Runs on the owner thread.
     */
    @Override
    public void apply(List<PostEffectDeclaration> declarations) {
        List<PostEffectDeclaration> batch = declarations == null ? List.of() : declarations;
        Map<ResourceLocation, PostEffectManager.Definition> installed = new LinkedHashMap<>();
        Set<ResourceLocation> retired = new LinkedHashSet<>();
        List<String> released = new ArrayList<>();
        long generation = activeGeneration + 1L;
        try {
            for (PostEffectDeclaration declaration : batch) {
                ResourceLocation id = ResourceLocation.tryParse(declaration.id());
                if (id == null) {
                    throw new IllegalArgumentException("Invalid post-effect id: " + declaration.id());
                }
                if (declaration.kind() == PostEffectDeclaration.Kind.RETIRE) {
                    retired.add(id);
                    released.add(declaration.id());
                    continue;
                }
                PostEffectManager.Definition definition =
                        PostEffectManager.parseDefinition(id, declaration.chainJson());
                if (definition == null) {
                    throw new IllegalArgumentException(
                            "Invalid post-effect chain JSON for " + declaration.id());
                }
                installed.put(id, definition);
            }
            // 旧 generation 未再声明的 id 也随新批次退役（声明移除语义）。
            for (String previous : activeIds) {
                ResourceLocation id = ResourceLocation.tryParse(previous);
                if (id != null && !installed.containsKey(id)) {
                    retired.add(id);
                    released.add(previous);
                }
            }
            PostEffectManager.installGeneration(generation, installed, retired);
            activeGeneration = generation;
            activeIds = installed.keySet().stream().map(ResourceLocation::toString)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            lastDiagnostics = new Diagnostics(Outcome.APPLIED, "commit", generation,
                    installed.size(), retired.size(), List.copyOf(released), null);
        } catch (Throwable failure) {
            // candidate 契约：preflight 通过后 apply 不得抛；真抛了如实记录，不宣称成功。
            lastDiagnostics = new Diagnostics(Outcome.RECOVERY_FAILED, "commit", activeGeneration,
                    installed.size(), retired.size(), List.copyOf(released), String.valueOf(failure));
            throw failure;
        }
    }

    // ---- 观察面（generation / stale 只读查询） ----

    /** Active declaration generation number ({@code -1} before the first commit). */
    public long activeGeneration() {
        return activeGeneration;
    }

    /** Whether the active declaration generation owns a definition for {@code id}. */
    public boolean hasActiveDefinition(ResourceLocation id) {
        return id != null && activeIds.contains(id.toString());
    }

    /** Last collection/apply outcome (diagnostics read face). */
    public Diagnostics lastDiagnostics() {
        return lastDiagnostics;
    }

    /**
     * Post-effect declaration domain outcome (diagnostics distinguish active / blocked / failed).
     * Only outcomes the owner actually publishes are listed: a passing candidate preflight is
     * deliberately <b>not</b> one of them (see {@link #preflight}).
     */
    public enum Outcome {
        /** No declaration batch has been processed yet. */
        INITIAL,
        /** New generation installed at the commit point. */
        APPLIED,
        /** The batch was rejected: the previous active generation keeps serving. */
        BLOCKED,
        /** Apply failed after a passing preflight (never claims success). */
        RECOVERY_FAILED
    }

    /** Diagnostics of the most recent declaration batch. */
    public record Diagnostics(Outcome outcome, String source, long generation, int installed,
                              int retired, List<String> releasedIds, String detail) {
    }
}
