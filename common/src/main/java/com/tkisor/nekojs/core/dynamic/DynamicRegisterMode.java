package com.tkisor.nekojs.core.dynamic;

import java.util.Locale;

/**
 * Registration mode for runtime (post-server-start) registry entries.
 *
 * <p>GLOBAL is deliberately absent: permanent registrations belong to the
 * STARTUP {@code RegistryEvents.*} builders and cannot be created at runtime.
 *
 * <p>Honest v1 semantics (both modes never unregister mid-session):
 * <ul>
 *   <li>{@link #WORLD}: registered on first use during a server session. Survives
 *       {@code /nekojs reload} (stale-retain). Intended to be unregistered when the
 *       world session ends — the cleanup path exists in the platform layer but is
 *       not wired to any lifecycle event yet.</li>
 *   <li>{@link #RELOADABLE}: identical observable behavior in v1 — the entry stays
 *       registered across reloads and is re-claimed by id when scripts re-run.
 *       Unregistering real entries mid-session corrupts numeric ids / saved stacks,
 *       so the mode difference is bookkeeping only for now; a later batch may give
 *       RELOADABLE entries replace-on-reload semantics.</li>
 * </ul>
 */
public enum DynamicRegisterMode {
    WORLD,
    RELOADABLE;

    /**
     * Parses a script-supplied mode name (case-insensitive).
     *
     * @throws IllegalArgumentException with a user-actionable message on
     *         {@code "global"}, unknown names, or null/blank input.
     */
    public static DynamicRegisterMode parse(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing register mode: expected 'world' or 'reloadable' (default is 'world')");
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "world" -> WORLD;
            case "reloadable" -> RELOADABLE;
            case "global" -> throw new IllegalArgumentException(
                    "Register mode 'global' is not available at runtime: register permanent content from "
                            + "STARTUP scripts via RegistryEvents instead");
            default -> throw new IllegalArgumentException(
                    "Unknown register mode '" + name.trim() + "': expected 'world' or 'reloadable'");
        };
    }
}
