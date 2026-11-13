package com.tkisor.nekojs.core.state;

/**
 * Reports the failure of a global/shared candidate write set or of a joint plan.
 *
 * <p>It carries a structured domain, such as {@code "global-write-conflict"} or
 * {@code "state-plan-preflight:test"}. {@code ScriptManager} turns it into a
 * {@code NekoReloadException} in the {@code ReloadPhase.STATE_PLAN} phase, so the owner and the
 * domain stay observable.
 */
public class GlobalStateException extends RuntimeException {

    private final String domain;

    public GlobalStateException(String domain, String message) {
        super(message);
        this.domain = domain;
    }

    public GlobalStateException(String domain, String message, Throwable cause) {
        super(message, cause);
        this.domain = domain;
    }

    /** The structured failure domain (reported by {@code ReloadFailureReport.domain()}). */
    public String domain() {
        return domain;
    }
}
