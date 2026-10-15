//? if neoforge {
package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.core.villager.VillagerTradeQuerySurface;
import com.tkisor.nekojs.core.villager.VillagerTradesFacade;
import graal.graalvm.polyglot.Context;

/** Read-only trade queries for the committed generation; declarations belong to ServerEvents. */
@Doc("Read committed trade declarations with query()/describe(); declare them with ServerEvents.tradeDeclaration.")
public class VillagerTradesJS {
    /**
     * Read-only, generation-bound snapshot of the committed villager trade declarations.
     *
     * <p>Example: {@code const result = VillagerTrades.query();
     * if (result.status === 'ACTIVE') console.info(result.total, result.tradeSetIds)}</p>
     */
    @Doc("Returns a read-only, generation-bound snapshot: status ('ACTIVE' / 'STALE'), generation, adapterId, total, tradeSetIds, unrestoredListingKeys and retiredListingKeys. A stale token answers with empty values instead of another generation's data.")
    @Return("read-only query result (never a live registry view)")
    public VillagerTradeQuerySurface query() {
        return VillagerTradesFacade.query(Context.getCurrent());
    }

    /** Developer-facing summary of {@link #query()} (diagnostics; no structured members needed). */
    @Doc("Developer-facing one-line summary of query(), for diagnostics and logs.")
    @Return("human-readable summary line")
    public String describe() {
        return query().describe();
    }

}
//?}
