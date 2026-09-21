package com.tkisor.nekojs.core.villager;

/**
 * Adapter-level unavailability of villager trade mutation (ticket 22 AC9).
 *
 * <p>Two concrete cases: the node has no implementation at all (Fabric today) and the node has
 * one but cannot commit right now (the reloadable trade registries are not ready at this point
 * of the server lifecycle). Both must surface as an explicit unavailable/blocked result — never
 * as a silent no-op or a fake success.
 */
public final class VillagerTradeUnavailableException extends IllegalStateException {

    private final String reason;

    public VillagerTradeUnavailableException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public VillagerTradeUnavailableException(String reason, Throwable cause) {
        super(reason, cause);
        this.reason = reason;
    }

    /** Short machine-readable reason suitable for logs and diagnostics. */
    public String reason() {
        return reason;
    }
}
