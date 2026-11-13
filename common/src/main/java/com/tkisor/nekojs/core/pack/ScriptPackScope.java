package com.tkisor.nekojs.core.pack;

/**
 * Script pack scope.
 *
 * <ul>
 *   <li>{@link #GLOBAL}: lives in {@code <gameDir>/nekojs/packs/}, loads with the normal script
 *       lifecycle and is not bound to a save.</li>
 *   <li>{@link #WORLD}: lives in {@code <worldDir>/nekojs_packs/}, activated when the server starts
 *       and unloaded as a whole when it stops (its event listeners are unregistered by scriptId
 *       prefix), which isolates packs per world.</li>
 *   <li>{@link #SERVER_CACHE}: lives in {@code <gameDir>/nekojs/server_packs/<bucket>/}. In
 *       multiplayer the server pushes packs and they are written to disk; the client activates and
 *       runs them after verification and trust pass, and unloads them as a whole on disconnect
 *       (cached files stay and are reused by hash on the next connection). Forced enabled: neither
 *       the state file nor the manifest default applies, because verification and trust already
 *       decided whether the pack runs.</li>
 * </ul>
 */
public enum ScriptPackScope {
    GLOBAL("packs"),
    WORLD("worldpacks"),
    SERVER_CACHE("serverpacks"),
    MOD("modpacks");

    /** Prefix segment in the ScriptId path; see {@link ScriptPack#idPathPrefix()}. */
    private final String idSegment;

    ScriptPackScope(String idSegment) {
        this.idSegment = idSegment;
    }

    public String idSegment() {
        return idSegment;
    }
}
