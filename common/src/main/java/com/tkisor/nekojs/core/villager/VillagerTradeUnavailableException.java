package com.tkisor.nekojs.core.villager;

/**
 * Reports that villager trade mutation is unavailable at the adapter level.
 *
 * <p>Two concrete cases: the node has no implementation at all (Fabric today), or the node has
 * one but cannot commit right now because the reloadable trade registries are not ready at this
 * point of the server lifecycle. Both are reported as an explicit unavailable/blocked result,
 * never as a silent no-op or a fake success.
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
