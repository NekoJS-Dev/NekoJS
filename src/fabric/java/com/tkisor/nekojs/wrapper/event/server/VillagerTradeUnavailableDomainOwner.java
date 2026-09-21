package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.villager.VillagerTradeApplier;
import com.tkisor.nekojs.core.villager.VillagerTradeCandidatePlan;
import com.tkisor.nekojs.core.villager.VillagerTradeDeclaration;
import com.tkisor.nekojs.core.villager.VillagerTradeDeclarationEventJS;
import com.tkisor.nekojs.core.villager.VillagerTradeDomainState;
import com.tkisor.nekojs.core.villager.VillagerTradeReloadEventJS;
import com.tkisor.nekojs.core.villager.VillagerTradeSetSnapshot;
import com.tkisor.nekojs.core.villager.VillagerTradeUnavailableException;

import java.util.List;
import java.util.Set;

/**
 * Explicitly unavailable villager trade domain owner on Fabric (ticket 22 AC9).
 *
 * <p>Fabric has no {@code RegisterVillagerTradesEvent} equivalent and this port has no trade
 * registry mutation adapter, so the honest answer is <b>unavailable</b> — not a silent no-op.
 * This collector keeps the event surface and the joint candidate boundary intact: a script that
 * declares a trade collects listeners normally, and the batch fails its joint preflight with a
 * {@link VillagerTradeUnavailableException} naming the node, so the reload reports a structured
 * failure and the old active trades keep serving.
 *
 * <p>The preflight failure reason is recorded once per batch: a script that calls
 * {@code add} repeatedly produces one reason, not a log flood.
 */
public final class VillagerTradeUnavailableDomainOwner implements CandidateDomainCollector, AutoCloseable {

    /** Collector identity (aligned with {@code VillagerTradeCandidatePlan.DOMAIN}). */
    public static final String DOMAIN = VillagerTradeCandidatePlan.DOMAIN;

    private final VillagerTradeDomainState state = new VillagerTradeDomainState("fabric-unavailable", false);
    private final VillagerTradeApplier applier = new VillagerTradeApplier() {
        @Override
        public String adapterId() {
            return "fabric-unavailable";
        }

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public void preflight(List<VillagerTradeDeclaration> declarations) {
            if (declarations.isEmpty()) {
                // Nothing requested: do not fail reloads that never touch the trade surface.
                return;
            }
            throw new VillagerTradeUnavailableException(
                    "villager trade registry mutation has no Fabric implementation in this port"
                            + " (no adapter is registered; the declaration is rejected, not ignored)");
        }

        @Override
        public void apply(List<VillagerTradeDeclaration> declarations, Set<String> obsoleteTradeSets) {
            throw new VillagerTradeUnavailableException(
                    "villager trade registry mutation has no Fabric implementation in this port");
        }

        @Override
        public VillagerTradeSetSnapshot committedSnapshot() {
            return VillagerTradeSetSnapshot.EMPTY;
        }
    };

    /** Domain bookkeeping (installed into the query facade; always reports node-unavailable). */
    public VillagerTradeDomainState state() {
        return state;
    }

    @Override
    public String domain() {
        return DOMAIN;
    }

    @Override
    public ScriptType scriptType() {
        return ScriptType.SERVER;
    }

    @Override
    public void collect(Handle handle) {
        VillagerTradeCandidatePlan plan = new VillagerTradeCandidatePlan(applier, state);
        // Same payload types and the same joint boundary as the other nodes: the batch collects
        // normally and is rejected as a whole by the adapter's explicit unavailable preflight.
        handle.dispatch(ServerEvents.TRADE_DECLARATION, new VillagerTradeDeclarationEventJS(plan));
        handle.dispatch(ServerEvents.TRADE_RELOAD,
                new VillagerTradeReloadEventJS(plan, state.committedSnapshot()));
        if (plan.isEmpty()) {
            // No script declared anything (the dispatch reached no listener): the domain does not
            // participate at all, so an untouched reload behaves exactly as before this ticket.
            return;
        }
        handle.registerPlan(plan);
    }

    @Override
    public void close() {
        state.reset();
        com.tkisor.nekojs.core.villager.VillagerTradesFacade.uninstall(state);
    }
}
