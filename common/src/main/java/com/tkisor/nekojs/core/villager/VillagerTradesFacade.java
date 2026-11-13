package com.tkisor.nekojs.core.villager;

/**
 * Process-level facade for the {@code VillagerTrades} query binding.
 *
 * <p>Follows the existing facade pattern of this repository (see {@code DynamicRegistryFacade}):
 * the platform assembly creates the domain owner, registers it into {@code NekoRuntimeRoot}
 * through {@code registerDomainCollector} (the root owns its lifecycle and closes it), then
 * installs the owner's {@link VillagerTradeDomainState} here so the script binding can reach it.
 * This is <b>not</b> a second runtime owner: it holds no context, no listeners and no lifecycle.
 * It only looks up the state for a stateless binding.
 *
 * <p>No installed state means the node has no villager trade support (Fabric today). Callers
 * must report that as an explicit unavailable answer, never as a silent no-op.
 */
public final class VillagerTradesFacade {

    private static volatile VillagerTradeDomainState state;

    private VillagerTradesFacade() {
    }

    /** Installs (or replaces) the domain state of the assembling platform. */
    public static void install(VillagerTradeDomainState domainState) {
        if (domainState == null) {
            throw new NullPointerException("domainState");
        }
        state = domainState;
    }

    /** Removes {@code expected} if it is still installed (root close / test teardown). */
    public static void uninstall(VillagerTradeDomainState expected) {
        if (expected != null && state == expected) {
            state = null;
        }
    }

    /** Installed domain state, or {@code null} when this node has no villager trade support. */
    public static VillagerTradeDomainState stateOrNull() {
        return state;
    }

    /**
     * Generation-bound query entry used by the {@code VillagerTrades} binding: routes to the
     * installed domain state, or answers with an explicit unavailable result when this node has
     * no adapter at all. Never returns a live registry view and never silently succeeds.
     */
    public static VillagerTradeQuerySurface query(graal.graalvm.polyglot.Context context) {
        VillagerTradeDomainState installed = state;
        if (installed == null) {
            return VillagerTradeQuerySurface.unavailable(unavailableReason());
        }
        return installed.query(context);
    }

    /** Explicit unavailability message for nodes without a villager trade adapter. */
    public static String unavailableReason() {
        return "villager trade registry mutation is unavailable on this node"
                + " (no platform/version adapter is installed; this is not a silent no-op)";
    }
}
