// 1.21.1 implementation, paired with the file of the same name under the version tree's src/
// that targets 26.x. Its content equals that file as evaluated for this node.
// Update this file when behavior changes on the 26.x side.
//
// This node has no shared VillagerTradeDomainState instance: the 1.21.1 trade registry shape
// (the static VillagerTrades.TRADES pool) differs from the ReloadableRegistries used on 26.x,
// so VillagerTradeDomainOwner under versions/1.21.1 injects the domain state. Before that
// state is injected the code returns NOT_INITIALIZED. That is the explicit result for "this
// node has no snapshot yet", not an empty snapshot pretending to succeed.
package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.core.villager.VillagerTradeDomainState;
import com.tkisor.nekojs.core.villager.VillagerTradeQuerySurface;
import com.tkisor.nekojs.core.villager.VillagerTradesFacade;
import graal.graalvm.polyglot.Context;

/**
 * Static binding {@code VillagerTrades}: a <b>read-only query</b> over the villager and
 * wandering trader declarations that the last committed generation applied.
 *
 * <p>{@code add} lives on the existing {@code ServerEvents} data sub-event
 * ({@code ServerEvents.tradeDeclaration}); this binding holds no staged queue and therefore
 * has no {@code pendingCount} counter.
 */
@Doc("Static binding 'VillagerTrades': read-only query over the trades the last committed generation declared. Declare trades with ServerEvents.tradeDeclaration.")
public class VillagerTradesJS {

    /**
     * Read-only snapshot of the committed villager trade declarations.
     *
     * <p>Example: {@code const result = VillagerTrades.query();
     * if (result.status === 'ACTIVE') console.info(result.total, result.tradeSetIds)}</p>
     */
    @Doc("Returns a read-only, generation-bound snapshot: status ('ACTIVE' / 'STALE' / 'NOT_INITIALIZED'), generation, adapterId, total, tradeSetIds, unrestoredListingKeys and retiredListingKeys. A stale token answers with empty values instead of another generation's data.")
    @Return("read-only query result (never a live registry view)")
    public VillagerTradeQuerySurface query() {
        VillagerTradeDomainState state = VillagerTradesFacade.stateOrNull();
        if (state == null) {
            return VillagerTradeQuerySurface.unavailable(VillagerTradesFacade.unavailableReason());
        }
        return state.query(Context.getCurrent());
    }

    /** Developer-facing summary of {@link #query()} (diagnostics; no structured members needed). */
    @Doc("Developer-facing one-line summary of query(), for diagnostics and logs.")
    @Return("human-readable summary line")
    public String describe() {
        return query().describe();
    }
}
