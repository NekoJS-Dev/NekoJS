package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.EventCallSignature;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.api.event.CancellableEventBus;
import com.tkisor.nekojs.api.event.EventBus;
import com.tkisor.nekojs.api.event.EventListenerToken;
import com.tkisor.nekojs.api.event.DispatchCancellableEventBus;
import com.tkisor.nekojs.api.event.DispatchEventBus;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.CommonPriority;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * JavaScript event bus handle that wraps {@link EventBus} and implements {@link ProxyExecutable}.
 * Scripts register listeners by directly invoking the bus object (e.g. {@code ServerEvents.tickPre(...)}).
 *
 * <p>Listeners are stored as Graal {@link Value} instances and executed on the posting thread (typically
 * the main game thread). Each listener token is tracked by the registering script's {@link ScriptType} and
 * scriptId for cleanup during script reload. On cancellable buses, returning {@code true} cancels the event.
 * Dispatch buses route events by key.
 *
 * @author ZZZank
 */
public class EventBusJS<EVENT, KEY> implements ProxyExecutable {
    private static Predicate<Class<?>> externalCancellabilityPredicate = c -> false;

    /** Explicit callable declarations; an empty list retains the ordinary event handler signature. */
    public List<EventCallSignature> callSignatures() {
        return List.of();
    }

    /**
     * Sets the external cancellability predicate used by {@link #of(Class)} to decide whether to create
     * a cancellable bus by default. Passing {@code null} treats all events as non-cancellable.
     */
    public static void setExternalCancellabilityPredicate(Predicate<Class<?>> predicate) {
        externalCancellabilityPredicate = predicate == null ? c -> false : predicate;
    }

    /** Creates an event bus whose cancellability is determined by {@link #eventCancellability(Class)}. */
    public static <E, K> EventBusJS<E, K> of(Class<E> eventType) {
        return of(eventType, eventCancellability(eventType));
    }

    /** Creates an event bus with explicit cancellability (non-dispatching). */
    public static <E, K> EventBusJS<E, K> of(Class<E> eventType, boolean cancellable) {
        return of(eventType, cancellable, null);
    }

    /**
     * Creates an event bus with explicit cancellability and an optional dispatch key descriptor.
     * When {@code dispatchKey} is provided, returns a bus that dispatches by key.
     */
    public static <E, K> EventBusJS<E, K> of(
        Class<E> eventType,
        boolean cancellable,
        @Nullable DispatchKey<E, K> dispatchKey
    ) {
        EventBus<E> bus;
        if (cancellable) {
            bus = dispatchKey != null
                ? DispatchCancellableEventBus.create(eventType, dispatchKey)
                : CancellableEventBus.create(eventType);
        } else {
            bus = dispatchKey != null
                ? DispatchEventBus.create(eventType, dispatchKey)
                : EventBus.create(eventType);
        }
        return new EventBusJS<>(bus);
    }

    /** Checks whether an event class is cancellable (returns false if no external predicate is set). */
    public static boolean eventCancellability(Class<?> c) {
        return externalCancellabilityPredicate.test(c);
    }

    private final EventBus<EVENT> bus;
    private final ThreadLocal<Boolean> collectionDispatch = new ThreadLocal<>();
    private final ThreadLocal<boolean[]> checkedDispatch = new ThreadLocal<>();
    /**
     * Script listener tokens bucketed by ScriptType. Uses a ConcurrentHashMap and CopyOnWriteArrayList
     * to safely support concurrent registrations from script loading threads alongside iterations from
     * probe callers.
     */
    private final Map<ScriptType, List<ScriptEventListenerToken<EVENT>>> tokensByType;
    /**
     * Script type this bus was registered for. Used ONLY as an immutability guard
     * (see {@link #scriptType(ScriptType)}) — it is intentionally NOT used for
     * listener filtering or isolation. Listener tokens are bucketed by the
     * registering script's ScriptType in {@link #tokensByType}, not by this field.
     * (DEAD-5: do not repurpose this for filtering without auditing the bucketing.)
     */
    private ScriptType scriptType;
    private String groupName;
    private String eventName;

    /** Constructs a handle wrapping the underlying bus. {@code bus} must not be null. */
    public EventBusJS(EventBus<EVENT> bus) {
        this.bus = Objects.requireNonNull(bus);
        this.tokensByType = new ConcurrentHashMap<>();
    }

    /** Returns whether the underlying bus is cancellable. */
    public boolean canCancel() {
        return bus instanceof CancellableEventBus<?>;
    }

    /** Returns whether the underlying bus supports key-based dispatching. */
    public boolean canDispatch() {
        return bus instanceof DispatchEventBus<?, ?>;
    }

    /**
     * Returns whether at least one listener is registered. Allows probe and other expensive event
     * emitters to skip event object construction and reflection when no listeners exist.
     *
     * <p>Checks both the JavaScript token mirror and the underlying bus to account for direct Java listeners.
     */
    public boolean hasListeners() {
        for (List<ScriptEventListenerToken<EVENT>> list : tokensByType.values()) {
            if (!list.isEmpty()) return true;
        }
        return bus instanceof com.tkisor.nekojs.eventbus.EventBusBase<?, ?> base && !base.isEmpty();
    }

    /** Returns the underlying Java event bus. */
    public EventBus<EVENT> bus() {
        return bus;
    }

    /** Returns the event class handled by this bus. */
    public Class<EVENT> eventType() {
        return bus.eventType();
    }

    /** Sets group and event name metadata. Called during registration; scripts should not call this. */
    public void metadata(String groupName, String eventName) {
        this.groupName = groupName;
        this.eventName = eventName;
    }

    /** Returns the event group name (e.g. "PlayerEvents"). */
    public String groupName() {
        return groupName;
    }

    /** Returns the event name (e.g. "chat"). */
    public String eventName() {
        return eventName;
    }

    /** Returns the bound {@link ScriptType}, or {@code null} if not yet bound. */
    public ScriptType scriptType() {
        return scriptType;
    }

    /**
     * Binds this bus to a {@link ScriptType}. Throws {@link IllegalStateException} if already bound to
     * a different type.
     */
    public void scriptType(ScriptType scriptType) {
        if (this.scriptType != null && this.scriptType != scriptType) {
            throw new IllegalStateException("Event bus script type is already " + this.scriptType + ": " + bus.eventType().getName());
        }
        this.scriptType = Objects.requireNonNull(scriptType, "scriptType");
    }

    /** Unregisters all JavaScript listeners for the specified {@link ScriptType}. */
    public void clearTokens(ScriptType type) {
        List<ScriptEventListenerToken<EVENT>> tokens = tokensByType.remove(type);
        if (tokens == null) return;
        for (var token : tokens) {
            bus.unregister(token.token());
        }
    }

    /** Unregisters the JS listener registered by the specified {@code scriptId} under the given {@link ScriptType}. Does nothing if {@code scriptId} is {@code null} or blank. */
    public void clearTokens(ScriptType type, String scriptId) {
        if (scriptId == null || scriptId.isBlank()) return;
        // Empty bucket removal must be atomic with registration (computeIfAbsent + add) for the same key: ConcurrentHashMap.compute
        // holds the bin lock, preventing concurrent insertions during computeIfAbsent. If we used the previous approach of 'removeIf(isEmpty) then'
        // remove(type)', a concurrent registration could add a new token to a list being removed. The underlying bus.listen would still take effect,
        // but our mirror would lose the entry, making subsequent clearTokens(type) calls unable to unregister it, leading to permanent listener leaks.
        List<ScriptEventListenerToken<EVENT>> removed = new ArrayList<>();
        tokensByType.compute(type, (ignored, tokens) -> {
            if (tokens == null) return null;
            List<ScriptEventListenerToken<EVENT>> kept = null;
            for (ScriptEventListenerToken<EVENT> token : tokens) {
                if (scriptId.equals(token.scriptId())) {
                    removed.add(token);
                } else {
                    if (kept == null) kept = new ArrayList<>();
                    kept.add(token);
                }
            }
            if (removed.isEmpty()) return tokens; // No match: leave original list unchanged.
            // Returning null atomically removes the empty bucket; otherwise, replace with a new CopyOnWriteArrayList (preserving concurrent read semantics).
            return kept == null ? null : new CopyOnWriteArrayList<>(kept);
        });
        // bus.unregister must be performed outside of compute: must not trigger side effects
        // that could re-enter this map (or bus) while holding the map bin lock.
        for (ScriptEventListenerToken<EVENT> token : removed) {
            bus.unregister(token.token());
        }
    }

    /**
     * Unregisters listeners by scriptId prefix: clears all JS listeners registered by scripts whose id starts
     * with {@code scriptIdPrefix}. Used for bulk unloading of script packs (especially WORLD packs)—
     * pack script IDs carry a {@code packs/<id>/} or {@code worldpacks/<id>/} prefix (see ScriptPack#idPathPrefix).
     * Concurrent constraints are the same as {@link #clearTokens(ScriptType, String)} (compute atomic bucketing).
     */
    public void clearTokensByPrefix(ScriptType type, String scriptIdPrefix) {
        if (scriptIdPrefix == null || scriptIdPrefix.isBlank()) return;
        List<ScriptEventListenerToken<EVENT>> removed = new ArrayList<>();
        tokensByType.compute(type, (ignored, tokens) -> {
            if (tokens == null) return null;
            List<ScriptEventListenerToken<EVENT>> kept = null;
            for (ScriptEventListenerToken<EVENT> token : tokens) {
                if (token.scriptId() != null && token.scriptId().startsWith(scriptIdPrefix)) {
                    removed.add(token);
                } else {
                    if (kept == null) kept = new ArrayList<>();
                    kept.add(token);
                }
            }
            if (removed.isEmpty()) return tokens;
            return kept == null ? null : new CopyOnWriteArrayList<>(kept);
        });
        for (ScriptEventListenerToken<EVENT> token : removed) {
            bus.unregister(token.token());
        }
    }

    /** Dispatches active collection callbacks in bus order and propagates failures to the batch owner. */
    public boolean postForCollection(EVENT event) {
        Boolean previous = collectionDispatch.get();
        collectionDispatch.set(true);
        try {
            return this.bus.post(event);
        } finally {
            restoreDispatchMode(previous);
        }
    }

    private void restoreDispatchMode(Boolean previous) {
        if (previous == null) collectionDispatch.remove();
        else collectionDispatch.set(previous);
    }

    /**
     * Dispatches normally and returns success rather than cancellation. Reported listener failures
     * do not stop later listeners; nested checked dispatches have independent outcomes.
     */
    public boolean postChecked(EVENT event) {
        boolean[] previous = checkedDispatch.get();
        boolean[] outcome = {true};
        checkedDispatch.set(outcome);
        try {
            post(event);
            return outcome[0];
        } finally {
            if (previous == null) checkedDispatch.remove();
            else checkedDispatch.set(previous);
        }
    }

    private void markCheckedDispatchFailed() {
        boolean[] outcome = checkedDispatch.get();
        if (outcome != null) outcome[0] = false;
    }

    /** Dispatches a normal event, reports callback failures, and returns its cancellation result. */
    public boolean post(EVENT event) {
        Boolean previous = collectionDispatch.get();
        collectionDispatch.set(false);
        try {
            return this.bus.post(event);
        } catch (Exception e) {
            markCheckedDispatchFailed();
            NekoJS.LOGGER.error("Error during CancellableEventBus execution", e);
            return false;
        } finally {
            restoreDispatchMode(previous);
        }
    }

    // The runtime type of the bus is determined by the of() factory based on dispatchKey; canDispatch()
    // ensures it is a DispatchEventBus. This is just generic narrowing via erasure.
    /**
     * Posts an event to the dispatch bus using a key. Throws {@link IllegalStateException} if the bus
     * does not support directional dispatch. Returns whether the event was cancelled. Listener exceptions
     */
    @SuppressWarnings("unchecked")
    public boolean post(EVENT event, KEY key) {
        if (canDispatch()) {
            Boolean previous = collectionDispatch.get();
            collectionDispatch.set(false);
            try {
                return ((DispatchEventBus<EVENT, KEY>) bus).post(event, key);
            } catch (Exception e) {
                markCheckedDispatchFailed();
                NekoJS.LOGGER.error("Error during EventBus execution", e);
            } finally {
                restoreDispatchMode(previous);
            }
            return false;
        }
        throw new IllegalStateException("This bus is not dispatchable");
    }

    /** Returns the set of registered keys. Returns an empty set for non-dispatch buses. */
    @SuppressWarnings("unchecked")
    public Set<KEY> registeredKeys() {
        if (canDispatch()) {
            return ((DispatchEventBus<EVENT, KEY>) bus).registeredKeys();
        }
        return Set.of();
    }

    /**
     * {@link ProxyExecutable} entry point: scripts register listeners by directly invoking the bus object.
     * Returns {@code true} if registration succeeds. Supported call shapes:
     * <ul>
     *   <li>{@code bus(listener)} — Standard listener, NORMAL priority</li>
     *   <li>{@code bus(priority, listener)} — First argument is the priority name
     *       (HIGHEST/HIGH/NORMAL/LOW/LOWEST, case-insensitive)</li>
     *   <li>{@code bus(key, listener)} / {@code bus(priority, key, listener)} —
     *       Directional registration by key for dispatch buses</li>
     * </ul>
     * For cancellable buses, the listener returns {@code true} to cancel the event. Throws
     * {@link IllegalArgumentException} for invalid arguments (e.g., missing listener). Listeners are
     *
     * <p>Candidate generation: when the registration context is a candidate environment still
     * being built, the registration is <strong>not</strong> applied to the underlying bus right
     * away, so it stays invisible until commit. The {@code ScriptManager} collects it as a
     * {@link PendingListener} instead. Once all candidate stages pass, it is applied at the commit
     * point through {@link PendingListener#activate()}. If candidate generation fails, the pending
     * registrations are discarded and the listeners that are already active stay unaffected.
     */
    @Override
    public Object execute(Value... args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("EventBus requires at least one arg");
        }
        if (canDispatch() && args.length == 1 && parsePriority(args[0]) != null) {
            // listen("HIGH") on a dispatch bus must keep the legacy missing-listener error
            // instead of treating "HIGH" as a key with no listener.
            throw new IllegalArgumentException("EventBus requires a listener after priority");
        }

        // DEFECT-D6: optional priority as the first argument. args[0] is parsed as a
        // priority name (HIGHEST/HIGH/NORMAL/LOW/LOWEST, case-insensitive) only when
        // either the bus is NOT dispatchable, or when a key + listener still follow
        // after the priority (>= 3 args). Call shapes:
        //   listen(listener)                  -> NORMAL
        //   listen("key", listener)           -> NORMAL (dispatch), "key" is NOT a priority name
        //   listen("HIGH", listener)          -> non-dispatch: HIGH priority;
        //                                       dispatch: key "HIGH", NORMAL priority
        //   listen("HIGH", "key", listener)   -> HIGH priority (dispatch)
        byte priority = CommonPriority.NORMAL;
        int offset = priorityArgOffset(args, canDispatch());
        if (offset > 0) {
            priority = parsePriority(args[0]);
            if (args.length <= offset) {
                throw new IllegalArgumentException("EventBus requires a listener after priority");
            }
        }

        Value[] rest = new Value[args.length - offset];
        System.arraycopy(args, offset, rest, 0, rest.length);

        Value listener;
        Value key = null;
        if (canDispatch()) {
            boolean keyed = rest.length > 1;
            listener = keyed ? rest[1] : rest[0];
            if (keyed) {
                key = rest[0];
            }
        } else {
            listener = rest[0];
        }

        ScriptType type = ScriptContextRegistry.scriptTypeOf(listener.getContext());
        String scriptId = ScriptContextRegistry.currentScriptIdOf(listener.getContext());
        PendingListener pending = new PendingListener(this, priority, listener, key, type, scriptId);
        if (ScriptManager.collectPendingListener(listener.getContext(), pending)) {
            // Candidate generation: suspend registration, activate on commit (see class comment)
            return true;
        }
        pending.activate();
        return true;
    }

    /**
     * Decide whether {@code args[0]} should be consumed as a priority name.
     *
     * <p>On a non-dispatch bus any leading priority-name string keeps its legacy
     * meaning. On a dispatch bus a leading string is only a priority when there are
     * still at least two arguments after it (key + listener); otherwise it is the
     * dispatch key itself — so {@code listen("HIGH", listener)} registers key
     * {@code "HIGH"} instead of misreading {@code "HIGH"} as a priority.
     */
    static int priorityArgOffset(Value[] args, boolean dispatchable) {
        if (args.length == 0 || parsePriority(args[0]) == null) {
            return 0;
        }
        if (!dispatchable) {
            return 1;
        }
        return args.length >= 3 ? 1 : 0;
    }

    /**
     * Parse a JS value as a {@link CommonPriority} name. Returns {@code null} when
     * the value is not a string or does not match a priority name, so callers can
     * distinguish "not a priority" (e.g. a dispatch key string) from a valid name.
     * Matching is case-insensitive.
     */
    private static Byte parsePriority(Value value) {
        if (value == null || !value.isString()) return null;
        String name = value.asString().trim().toUpperCase(Locale.ROOT);
        return switch (name) {
            case "HIGHEST" -> CommonPriority.HIGHEST;
            case "HIGH" -> CommonPriority.HIGH;
            case "NORMAL" -> CommonPriority.NORMAL;
            case "LOW" -> CommonPriority.LOW;
            case "LOWEST" -> CommonPriority.LOWEST;
            default -> null;
        };
    }

    /**
     * A complete description of a script listener registration: collected by {@code ScriptManager} during
     * the candidate phase, then activated at the commit point via {@link #activate()}. Pending registrations
     * do not touch the underlying bus or type-bucketing mirror, making candidate listeners completely invisible
     *
     * <p>Review A1: The commit point must not fail—tasks that might throw during {@link #activate()} (e.g.,
     * {@code Value.as(keyType)} conversion for a dispatch key) are moved to the candidate phase completion
     * via {@link #prepareForActivation()}. If the candidate phase fails, it follows the candidate discard path
     */
    public static final class PendingListener {
        private final EventBusJS<?, ?> owner;
        private final byte priority;
        private final Value listener;
        private final Value key;
        private final ScriptType type;
        private final String scriptId;

        /** Key parsed by {@link #prepareForActivation()} that can be mounted directly (null if not prepared). */
        private Object resolvedKey;
        /** Whether prepared: when true, {@link #activate()} performs no further Value → Java key conversion. */
        private boolean prepared;
        /** The token is retained so a failed commit can remove a partially activated listener. */
        private EventListenerToken<?> activatedToken;

        PendingListener(EventBusJS<?, ?> owner, byte priority, Value listener, Value key,
                        ScriptType type, String scriptId) {
            this.owner = owner;
            this.priority = priority;
            this.listener = listener;
            this.key = key;
            this.type = type;
            this.scriptId = scriptId;
        }

        /** Associated bus. The candidate collector uses it to pick the pending registrations that belong
         * to this bus. */
        public EventBusJS<?, ?> owner() {
            return owner;
        }

        /** Registration priority (candidate collector sorts stably by priority, following the EventBusBase compilation snapshot order). */
        public byte priority() {
            return priority;
        }

        /**
         * Runs the listener for candidate collection. The listener callback runs directly on the
         * owner thread, with the same scriptId switch and callback depth marking as production
         * dispatch, but exceptions <b>propagate upwards</b>, so the candidate fails or the error is
         * recorded in the domain plan. This bypasses the production rule of "log the error and keep
         * going". The underlying bus is not touched; the listener is only called.
         */
        public void executeForCollection(Object event) {
            Context context = listener.getContext();
            String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
            ScriptManager.noteCallbackEnter();
            try {
                listener.executeVoid(event);
            } finally {
                try { ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId); }
                finally { ScriptManager.noteCallbackExit(); }
            }
        }

        /** {@link ScriptType} of the registration source (dispatch mirror bucketing key). */
        public ScriptType type() {
            return type;
        }

        /** Registration source script id (used for mirror bookkeeping and script-based cleanup; also the source attribution key for candidate failure results). */
        public String scriptId() {
            return scriptId;
        }

        /** The bus this listener registers on. Pending listeners are filtered per bus when they are collected. */
        public EventBusJS<?, ?> ownerBus() {
            return owner;
        }

        /** The listener guest function. Collection callbacks run it on the owner thread. */
        public Value listenerValue() {
            return listener;
        }

        /**
         * Candidate phase preparation: Moves work that might throw at the commit point (dispatch key → Java key conversion) forward.
         *
         * <p>Must be called before commit and <strong>not within a JS call frame</strong> (in this repository, called by the
         * {@code ScriptManager} during the candidate EVENT_PLAN phase): exceptions during conversion bubble directly to the
         * transactional reload's candidate failure path, bypassing the "script-level error does not fail reload" semantics
         * of {@code ScriptExecutor.executeEntry}. Idempotent; not called on non-candidate registration paths to maintain
         * in-place conversion within {@link #activate()} (behavior unchanged).
         */
        public void prepareForActivation() {
            owner.preparePending(this);
        }

        /**
         * Activation: Builds the dispatch closure, attaches to the underlying bus, and records it in the type-bucketing mirror.
         * Called only at the commit point (or on non-candidate registration paths); repeated activate calls will cause double registration.
         *
         * <p>Pending registrations already prepared via {@link #prepareForActivation()} will <strong>not</strong>
         * throw here: key conversion is already completed in the candidate phase, and the remaining operations are only
         * {@code bus.listen(...)} (CopyOnWriteArrayList addition + compilation snapshot invalidation) and the mirror's
         * {@code ConcurrentHashMap.compute}, both of which have no conditional throwing paths.
         */
        public void activate() {
            owner.activatePending(this);
        }

        /** Roll back this listener if commit activated it before a later step failed. */
        public void deactivate() {
            owner.deactivatePending(this);
        }
    }

    /**
     * Replace one generation's listeners without exposing a half-committed route.
     * Candidate listeners are activated while the old tokens are still installed; if any
     * activation throws, only the listeners activated by this call are removed. The old token
     * snapshot is removed only after every candidate activation succeeds.
     */
    public PendingCommit preparePendingListeners(ScriptType type, List<PendingListener> pending) {
        return preparePendingListeners(type, pending, context -> true);
    }

    /** Select old activation owners rather than a source name reused by a newer activation. */
    public PendingCommit preparePendingListeners(ScriptType type, List<PendingListener> pending,
            java.util.function.Predicate<Context> replaced) {
        if (type == null) return null;
        List<ScriptEventListenerToken<EVENT>> old = tokensByType.get(type) == null
                ? List.of() : tokensByType.get(type).stream().filter(token -> replaced.test(token.context())).toList();
        List<PendingListener> owned = pending == null ? List.of() : pending.stream()
                .filter(candidate -> candidate != null && candidate.owner() == this
                        && candidate.type() == type)
                .toList();
        List<PendingListener> activated = new ArrayList<>();
        try {
            for (PendingListener candidate : owned) {
                candidate.activate();
                activated.add(candidate);
            }
        } catch (Throwable failure) {
            for (PendingListener candidate : activated) {
                try {
                    candidate.deactivate();
                } catch (Throwable cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (failure instanceof Error errorFailure) throw errorFailure;
            throw new IllegalStateException("Failed to commit pending listeners", failure);
        }

        return new PendingCommit(this, type, old, activated);
    }

    /** Counts active listeners retained by an owner-selective replacement. */
    protected final long retainedListenerCount(ScriptType type, java.util.function.Predicate<Context> replaced) {
        var tokens = tokensByType.get(type);
        return tokens == null ? 0 : tokens.stream().filter(token -> !replaced.test(token.context())).count();
    }

    /** Complete a previously prepared listener replacement. */
    public void commitPendingListeners(ScriptType type, List<PendingListener> pending) {
        PendingCommit commit = preparePendingListeners(type, pending);
        commit.finish();
    }

    /** A prepared listener replacement that can be finished or rolled back as part of a bridge batch. */
    public static final class PendingCommit {
        private final EventBusJS<?, ?> owner;
        private final ScriptType type;
        private final List<?> old;
        private final List<PendingListener> activated;

        private PendingCommit(EventBusJS<?, ?> owner, ScriptType type, List<?> old,
                              List<PendingListener> activated) {
            this.owner = owner;
            this.type = type;
            this.old = old;
            this.activated = List.copyOf(activated);
        }

        public void finish() {
            owner.finishPending(this);
        }

        public void rollback() {
            for (PendingListener listener : activated) {
                listener.deactivate();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void finishPending(PendingCommit commit) {
        List<ScriptEventListenerToken<EVENT>> old = (List<ScriptEventListenerToken<EVENT>>) (List<?>) commit.old;

        // The activation phase is complete, so this final route switch cannot expose a partial
        // candidate. EventBusBase.unregister is the only operation left and is non-throwing for
        // its own token implementation.
        tokensByType.computeIfPresent(commit.type, (ignored, tokens) -> {
            tokens.removeIf(old::contains);
            return tokens.isEmpty() ? null : tokens;
        });
        for (ScriptEventListenerToken<EVENT> token : old) {
            bus.unregister(token.token());
        }
    }

    /**
     * Candidate phase preparation (see {@link PendingListener#prepareForActivation()}): only parses the dispatch key,
     * does not touch the underlying bus or mirror (candidate listeners remain completely invisible to the production route before commit).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void preparePending(PendingListener pending) {
        if (pending.prepared) return;
        Object resolved = null;
        if (canDispatch() && pending.key != null) {
            var dispatchBus = (DispatchEventBus<EVENT, KEY>) this.bus;
            // Invalid/unconvertible keys throw here (candidate phase): ClassCastException / conversion failure.
            resolved = pending.key.as(dispatchBus.dispatchKey().keyType());
        }
        pending.resolvedKey = resolved;
        pending.prepared = true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void activatePending(PendingListener pending) {
        EventBusJS<EVENT, KEY> self = (EventBusJS<EVENT, KEY>) this;
        byte priority = pending.priority;
        Value listener = pending.listener;
        boolean cancellable = canCancel();
        boolean dispatch = canDispatch();
        EventListenerToken<EVENT> token = null;
        try {
            if (dispatch) {
                var dispatchBus = (DispatchEventBus<EVENT, KEY>) this.bus;
                // Already prepared: use the key parsed during the candidate phase (cannot throw at commit point, Review A1);
                // if not prepared (non-candidate registration path): keep in-place conversion, behavior identical to before refactoring.
                KEY dispatchKey = pending.prepared
                        ? (KEY) pending.resolvedKey
                        : (pending.key == null ? null : pending.key.as(dispatchBus.dispatchKey().keyType()));
                if (cancellable) {
                    token = dispatchKey != null
                            ? self.registerDispatchCancellable(priority, listener, dispatchKey)
                            : self.registerCancellable(priority, listener);
                } else {
                    token = dispatchKey != null
                            ? self.registerDispatch(priority, listener, dispatchKey)
                            : self.register(priority, listener);
                }
            } else if (cancellable) {
                token = self.registerCancellable(priority, listener);
            } else {
                token = self.register(priority, listener);
            }
            EventListenerToken<EVENT> activated = token;
            // The entire registration is placed within compute to avoid orphan tokens between cleanup and mirror addition.
            this.tokensByType.compute(pending.type, (ignored, tokens) -> {
                List<ScriptEventListenerToken<EVENT>> list =
                        tokens == null ? new CopyOnWriteArrayList<>() : tokens;
                list.add(new ScriptEventListenerToken<>(activated, pending.scriptId, pending.listener.getContext()));
                return list;
            });
            pending.activatedToken = token;
        } catch (Throwable failure) {
            if (token != null) {
                try {
                    self.bus.unregister(token);
                } catch (Throwable cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (failure instanceof Error errorFailure) throw errorFailure;
            throw new IllegalStateException("Failed to activate pending listener", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private void deactivatePending(PendingListener pending) {
        EventListenerToken<?> token = pending.activatedToken;
        if (token == null) return;
        try {
            bus.unregister((EventListenerToken<EVENT>) token);
        } finally {
            tokensByType.computeIfPresent(pending.type, (ignored, tokens) -> {
                tokens.removeIf(entry -> entry.token() == token);
                return tokens.isEmpty() ? null : tokens;
            });
            pending.activatedToken = null;
        }
    }

    private boolean skipDeadContext(Context context) {
        if (ScriptManager.isCandidateContext(context) && !Boolean.TRUE.equals(collectionDispatch.get())) return true;
        if (!ScriptManager.isContextDead(context)) return false;
        markCheckedDispatchFailed();
        if (Boolean.TRUE.equals(collectionDispatch.get())) throw new IllegalStateException();
        return true;
    }

    private void propagateCollectionFailure(Throwable failure) {
        Throwable hostFailure = failure;
        if (failure instanceof graal.graalvm.polyglot.PolyglotException polyglotFailure
                && polyglotFailure.isHostException()) {
            hostFailure = polyglotFailure.asHostException();
        }
        if (hostFailure instanceof InterruptedException) Thread.currentThread().interrupt();
        if (hostFailure instanceof Error errorFailure) throw errorFailure;
        if (!Boolean.TRUE.equals(collectionDispatch.get())) return;
        if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
        throw new RuntimeException(failure);
    }

    private EventListenerToken<EVENT> register(byte priority, Value listener) {
        Context context = listener.getContext();
        ScriptType type = ScriptContextRegistry.scriptTypeOf(context);
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);
        // Owner-thread dispatch is serialized with lifecycle work. Graal itself rejects
        // direct concurrent access; ScriptContextCancellationTest covers that constraint.

        return this.bus.listen(priority, event -> {
            if (skipDeadContext(context)) {
                // A callback captured this terminated generation; do not enter it again.
                return;
            }
            try {
                // Dispatch happens only on the owner thread (SERVER = tick, CLIENT = render; a network
                // receiver hands the event over first, then dispatches), so dispatch and lifecycle changes
                // on that thread are serialized naturally. Cross-thread concurrency is covered by Graal's
                // single-thread rule: a concurrent eval is rejected. Listener callbacks record their
                // nesting depth, and a lifecycle request on the same thread is rejected outright - no
                // recursion, no waiting on the current thread.
                String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
                ScriptManager.noteCallbackEnter();
                try {
                    listener.executeVoid(event);
                } finally {
                    try { ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId); }
                    finally { ScriptManager.noteCallbackExit(); }
                }
            } catch (Throwable e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                if (e instanceof Error) throw (Error) e;
                // When a Context is closed by a statement limit, the kill can only be discovered from the callback path
                // (as the entry point has already finished): report to the ScriptManager so it can automatically rebuild
                ScriptManager.reportContextKilled(context, e);
                propagateCollectionFailure(e);
                recordListenerError(context, type, scriptId, "normal", null, event, e);
            }
        });
    }

    private EventListenerToken<EVENT> registerCancellable(byte priority, Value listener) {
        Context context = listener.getContext();
        ScriptType type = ScriptContextRegistry.scriptTypeOf(context);
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);
        var bus = (CancellableEventBus<EVENT>) this.bus;

        return bus.listen(priority, event -> {
            if (skipDeadContext(context)) {
                // Context already closed by Graal (e.g., statement limit): skip dispatch to avoid repetitive error logs on a dead environment.
                return false;
            }
            try {
                // Threading rules are the same as in register(): dispatch on the owner thread, plus
                // callback depth marking.
                String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
                ScriptManager.noteCallbackEnter();
                try {
                    Value result = listener.execute(event);
                    return result.isBoolean() && result.asBoolean();
                } finally {
                    try { ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId); }
                    finally { ScriptManager.noteCallbackExit(); }
                }
            } catch (Throwable e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                if (e instanceof Error) throw (Error) e;
                ScriptManager.reportContextKilled(context, e);
                propagateCollectionFailure(e);
                recordListenerError(context, type, scriptId, "cancellable", null, event, e);
            }
            return false; // Default: do not cancel event on error
        });
    }

    private <K> EventListenerToken<EVENT> registerDispatch(byte priority, Value listener, K key) {
        Context context = listener.getContext();
        ScriptType type = ScriptContextRegistry.scriptTypeOf(context);
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);
        var bus = (DispatchEventBus<EVENT, K>) this.bus;

        return bus.listen(
                key,
                priority,
                event -> {
                    if (skipDeadContext(context)) {
                        // Context already closed by Graal (e.g., statement limit): skip dispatch to avoid repetitive error logs on a dead environment.
                        return;
                    }
                    try {
                        // Threading rules are the same as in register(): dispatch on the owner thread, plus
                        // callback depth marking.
                        String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
                        ScriptManager.noteCallbackEnter();
                        try {
                            if (listener.canExecute()) {
                                listener.executeVoid(event);
                            }
                        } finally {
                            try { ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId); }
                            finally { ScriptManager.noteCallbackExit(); }
                        }
                    } catch (Throwable e) {
                        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                        if (e instanceof Error) throw (Error) e;
                        ScriptManager.reportContextKilled(context, e);
                        propagateCollectionFailure(e);
                        recordListenerError(context, type, scriptId, "dispatch", key, event, e);
                    }
                }
        );
    }

    private <K> EventListenerToken<EVENT> registerDispatchCancellable(byte priority, Value listener, K key) {
        Context context = listener.getContext();
        ScriptType type = ScriptContextRegistry.scriptTypeOf(context);
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);
        var bus = (DispatchCancellableEventBus<EVENT, K>) this.bus;

        return bus.listen(
                key,
                priority,
                event -> {
                    if (skipDeadContext(context)) {
                        // Context already closed by Graal (e.g., statement limit): skip dispatch to avoid repetitive error logs on a dead environment.
                        return false;
                    }
                    try {
                        // Threading rules are the same as in register(): dispatch on the owner thread, plus
                        // callback depth marking.
                        String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
                        ScriptManager.noteCallbackEnter();
                        try {
                            if (listener.canExecute()) {
                                Value result = listener.execute(event);
                                return result.isBoolean() && result.asBoolean();
                            }
                        } finally {
                            try { ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId); }
                            finally { ScriptManager.noteCallbackExit(); }
                        }
                    } catch (Throwable e) {
                        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                        if (e instanceof Error) throw (Error) e;
                        ScriptManager.reportContextKilled(context, e);
                        propagateCollectionFailure(e);
                        recordListenerError(context, type, scriptId, "dispatchCancellable", key, event, e);
                    }
                    return false; // Default: do not cancel event on error
                }
        );
    }

    private void recordListenerError(Context context, ScriptType type, String scriptId, String mode,
                                     Object dispatchKey, EVENT event, Throwable throwable) {
        markCheckedDispatchFailed();
        String eventClass = event == null ? "null" : event.getClass().getName();
        String keyText = dispatchKey == null ? "" : " key=" + dispatchKey;
        String kind = "event mode=" + mode
                + " bus=" + bus.eventType().getName()
                + " event=" + eventClass
                + " script=" + (scriptId == null || scriptId.isBlank() ? "unknown" : scriptId)
                + " thread=" + Thread.currentThread().getName()
                + keyText;
        ScriptErrorReporter.recordCallbackError(context, type, kind, throwable);
    }

    private record ScriptEventListenerToken<EVENT>(EventListenerToken<EVENT> token, String scriptId, Context context) {}
}
