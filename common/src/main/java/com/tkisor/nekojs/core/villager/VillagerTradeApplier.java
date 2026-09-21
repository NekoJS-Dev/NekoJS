package com.tkisor.nekojs.core.villager;

import java.util.List;
import java.util.Set;

/**
 * Platform/version adapter seam for villager trade declarations (ticket 22 AC5): the actual
 * registry surgery exists only in the adapter — the shared event surface and this whole
 * package contain no Minecraft or loader type.
 *
 * <p>Contract, aligned with the joint candidate boundary
 * ({@link com.tkisor.nekojs.core.state.CandidateStatePlan}):
 * <ul>
 *   <li>{@link #preflight(List)} runs during the candidate STATE_PLAN phase: it resolves the
 *       trade sets, checks that the node can apply the whole batch and throws for an unknown
 *       trade set or a node that cannot commit. It must not produce externally visible side
 *       effects (read-only resolution). Throwing fails the whole batch and keeps the old
 *       active trades serving.</li>
 *   <li>{@link #apply(List, Set)} runs at the commit point and owns the <b>whole</b> desired
 *       state: it restores the adapter's vanilla baselines, removes the previously injected
 *       entries (and any trade set the reload event marked obsolete), then applies the
 *       complete declaration list in order. After a passed preflight it must not throw.</li>
 * </ul>
 *
 * <p>Recovery only covers what NekoJS owns and the adapter proved it can restore: the trade
 * entries NekoJS injected and the trade sets it replaced. It is not a promise to roll back
 * arbitrary Java objects, other mods, worlds, networks or files (spec 09 external side effects).
 */
public interface VillagerTradeApplier {

    /** Adapter node id (diagnostics/capability records, e.g. {@code "26.x-trade-set"}). */
    String adapterId();

    /**
     * Whether this node can perform villager trade registry mutation at all. {@code false} is
     * the explicit unavailable answer (e.g. Fabric today): callers must report it, never treat
     * it as a successful no-op.
     */
    boolean available();

    /**
     * Candidate preflight: trade set resolution + whole-batch applicability. Throws
     * {@link VillagerTradeUnavailableException} when the node cannot commit right now (for
     * example the reloadable registries are not ready yet); any other throwable is a rejection
     * of the batch content.
     */
    void preflight(List<VillagerTradeDeclaration> declarations);

    /**
     * Commit: restore baselines, drop the previously injected entries and every trade set in
     * {@code obsoleteTradeSets}, then apply the complete declaration list.
     */
    void apply(List<VillagerTradeDeclaration> declarations, Set<String> obsoleteTradeSets);

    /** Snapshot published by the most recent successful {@link #apply}. */
    VillagerTradeSetSnapshot committedSnapshot();
}
