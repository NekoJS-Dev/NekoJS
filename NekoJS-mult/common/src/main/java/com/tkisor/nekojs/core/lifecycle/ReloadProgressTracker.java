package com.tkisor.nekojs.core.lifecycle;

import org.jetbrains.annotations.VisibleForTesting;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Static, thread-safe progress tracker for script (re)load operations, consumed by the
 * client reload progress HUD (platform side, e.g. {@code NekoReloadProgressHud}).
 *
 * <p>Mirrors Katton's {@code ReloadProgressState}/{@code ReloadProgressTracker}: the HUD
 * reads an immutable {@link Snapshot} from an {@link AtomicReference}, so producer threads
 * (server reload, client reload) never block the render thread. Sessions are keyed by
 * script type name ({@code startup}/{@code server}/{@code client}/{@code test}).
 *
 * <p>Display rules:
 * <ul>
 *   <li>while a session is {@link Snapshot#active() active} the snapshot is always visible;</li>
 *   <li>{@link #begin}/{@link #step}/{@link #update} refresh {@code visibleUntilMillis} to
 *       now + 5s so the HUD lingers briefly if the producer stalls;</li>
 *   <li>{@link #finish} shows 100% ({@code active = false}) for ~1.5s, then the HUD hides it.</li>
 * </ul>
 *
 * <p>The tracker tolerates misuse: {@link #step}/{@link #update}/{@link #finish} for a type
 * without an active session are no-ops, and unknown type names never throw. {@link #begin}
 * returns whether a new session was started, so callers that may run nested (initial
 * {@code loadScripts()} inside a full reload) only finish sessions they own.
 */
public final class ReloadProgressTracker {

    /** Visibility window (ms) kept after begin/step/update while a session is active. */
    public static final long UPDATE_LINGER_MILLIS = 5_000L;
    /** Visibility window (ms) kept after finish, showing 100% before hiding. */
    public static final long FINISH_LINGER_MILLIS = 1_500L;
    /** Progress cap while active — finish is the only transition to 1.0. */
    public static final float ACTIVE_PROGRESS_CAP = 0.99f;

    private record Session(int estimatedSteps, int currentStep) {
        Session advance() {
            return new Session(estimatedSteps, currentStep + 1);
        }
    }

    private static final AtomicReference<Snapshot> STATE = new AtomicReference<>(Snapshot.HIDDEN);

    /** Sessions per script type name; only mutated by begin/step/finish callers. */
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    /** Injectable clock (millis) so unit tests drive visibility windows deterministically. */
    private static volatile LongSupplier clock = System::currentTimeMillis;

    private ReloadProgressTracker() {
    }

    @VisibleForTesting
    static void setClock(LongSupplier clock) {
        ReloadProgressTracker.clock = clock;
    }

    @VisibleForTesting
    static void resetClock() {
        ReloadProgressTracker.clock = System::currentTimeMillis;
    }

    /**
     * Starts a progress session for the given script type.
     *
     * @param scriptType script type name (e.g. {@code ScriptType.name}); unknown names are fine
     * @param estimatedSteps total step count used to compute progress; clamped to >= 1
     * @return {@code true} if a new session was started; {@code false} if the type already had
     *         an active session (kept as-is — lets nested loads stay on the outer session)
     */
    public static boolean begin(String scriptType, int estimatedSteps) {
        if (scriptType == null) return false;
        if (SESSIONS.putIfAbsent(scriptType, new Session(Math.max(1, estimatedSteps), 0)) != null) {
            return false; // already active: outer reload session wins
        }
        STATE.set(new Snapshot(scriptType, true, 0.0f, "", clock() + UPDATE_LINGER_MILLIS));
        return true;
    }

    /**
     * Advances to the next step and updates the displayed message.
     * No-op when the type has no active session (tolerates missing {@link #begin}).
     */
    public static void step(String scriptType, String message) {
        if (scriptType == null) return;
        Session updated = SESSIONS.computeIfPresent(scriptType, (k, s) -> s.advance());
        if (updated == null) return;
        STATE.set(new Snapshot(scriptType, true, progressOf(updated), message, clock() + UPDATE_LINGER_MILLIS));
    }

    /**
     * Refreshes the displayed message without advancing the step counter.
     * No-op when the type has no active session.
     */
    public static void update(String scriptType, String message) {
        if (scriptType == null) return;
        Session session = SESSIONS.get(scriptType);
        if (session == null) return;
        STATE.set(new Snapshot(scriptType, true, progressOf(session), message, clock() + UPDATE_LINGER_MILLIS));
    }

    /**
     * Ends the session: shows 100% with a done/failed message for {@link #FINISH_LINGER_MILLIS},
     * then the HUD hides the snapshot. No-op when the type has no active session.
     *
     * @param success {@code true} for a "ready" message, {@code false} for a "failed" message
     */
    public static void finish(String scriptType, boolean success) {
        if (scriptType == null || SESSIONS.remove(scriptType) == null) return;
        String message = success ? scriptType + " scripts ready" : scriptType + " scripts reload failed";
        STATE.set(new Snapshot(scriptType, false, 1.0f, message, clock() + FINISH_LINGER_MILLIS));
    }

    /** Immediately hides any snapshot and forgets all sessions (HUD uninstall / reset). */
    public static void hideAll() {
        SESSIONS.clear();
        STATE.set(Snapshot.HIDDEN);
    }

    /** Current snapshot for the HUD; never null. */
    public static Snapshot snapshot() {
        return STATE.get();
    }

    @VisibleForTesting
    static void resetForTest() {
        SESSIONS.clear();
        STATE.set(Snapshot.HIDDEN);
    }

    private static long clock() {
        return clock.getAsLong();
    }

    private static float progressOf(Session session) {
        return Math.min(session.currentStep() / (float) session.estimatedSteps(), ACTIVE_PROGRESS_CAP);
    }

    /**
     * Immutable HUD snapshot: script type name, plain-text message, progress in 0..1
     * (capped at {@link #ACTIVE_PROGRESS_CAP} while active) and a visibility deadline
     * compared against the injected clock.
     */
    public static final class Snapshot {
        static final Snapshot HIDDEN = new Snapshot("", false, 0.0f, "", 0L);

        private final String scriptType;
        private final boolean active;
        private final float progress;
        private final String message;
        private final long visibleUntilMillis;

        Snapshot(String scriptType, boolean active, float progress, String message, long visibleUntilMillis) {
            this.scriptType = scriptType;
            this.active = active;
            this.progress = Math.max(0.0f, Math.min(1.0f, progress));
            this.message = message == null ? "" : message;
            this.visibleUntilMillis = visibleUntilMillis;
        }

        public String scriptType() {
            return scriptType;
        }

        public boolean active() {
            return active;
        }

        public float progress() {
            return progress;
        }

        public String message() {
            return message;
        }

        public long visibleUntilMillis() {
            return visibleUntilMillis;
        }

        /** HUD visibility rule: active snapshots always show, finished ones linger briefly. */
        public boolean visibleAt(long nowMillis) {
            return active || nowMillis <= visibleUntilMillis;
        }
    }
}
