package com.tkisor.nekojs.core.modification;

import java.util.List;

/**
 * Platform and version adapter for modification plans. Minecraft application lives only in
 * adapters: the 26.x and 1.21.1 differences in item default components, block/state properties,
 * registration timing and syncing are implemented and recorded by each node adapter. common does
 * not know any MC type.
 *
 * <p>Contract, aligned with the joint preflight and joint publish of
 * {@link ModificationCandidatePlan}:
 * <ul>
 *   <li>{@link #preflight(List)} is called in the candidate STATE_PLAN stage: it resolves each
 *       target, validates the property invariants against the <b>baseline</b> (the original values
 *       NekoJS holds), and confirms the adapter can apply the whole batch. It may throw freely; a
 *       throw fails the whole batch and the old active state stays. It must not produce visible
 *       side effects; it only resolves and validates.</li>
 *   <li>{@link #apply(List)} is called at the commit point: it <b>restores every baseline NekoJS
 *       holds, then applies the complete new plan in declaration order</b>. Once preflight passed,
 *       apply must not throw; when it violates the {@code CandidateStatePlan#publish} contract, the
 *       global/shared write set is not published and partial side effects of the plan itself are
 *       not rolled back. A field the platform cannot prove restorable must make preflight reject
 *       the whole batch; silent stale state is not success.</li>
 * </ul>
 *
 * <p>Restore and apply only cover fields NekoJS owns and the adapter proved restorable. They do not
 * roll back the internal state of arbitrary Java objects, other mods, the world, the network or
 * file side effects.
 */
public interface ModificationApplier {

    /** Adapter identifier used for diagnostics, such as {@code "26.x-shared"}. */
    String adapterId();

    /**
     * Preflights candidate declarations: resolves targets, validates baseline invariants,
     * and confirms rollback safety. Throwing fails the entire batch.
     */
    void preflight(List<ModificationDeclaration> declarations);

    /**
     * Applies modifications at commit: restores the baseline held by NekoJS, then applies
     * the new plan in declaration order. Must not throw once preflight passes.
     */
    void apply(List<ModificationDeclaration> declarations);
}
