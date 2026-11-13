package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.ScriptManager;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of script HUD and world renderers: callbacks registered through
 * {@code ClientEvents.hudRender}/{@code worldRender} are recorded by id (owner scriptId,
 * layer, priority and Graal Value), and the platform render hooks ({@link ClientRenderEvents})
 * dispatch them sorted by (layer, priority) every frame.
 *
 * <p>Generation semantics: renderer registrations made while a candidate generation's scripts
 * run do <b>not</b> go into the production tables. They are collected into that candidate's
 * {@link Candidate} batch, which is inert (render hooks cannot see it). At the commit point
 * {@link Candidate#publish()} swaps the whole tables, and if the candidate fails or is
 * cancelled the renderers of the old active generation keep serving. Initial load, kill and
 * rebuild, and single-file reload take the non-transactional path: the first registration from
 * the new generation's Context swaps the whole table, and later registrations from the same
 * Context only replace entries with the same id (see {@link #beginActiveGeneration}).
 *
 * <p>Error handling and cleanup follow the listener execution path of {@code EventBusJS}:
 * <ul>
 *   <li>a throwing callback is recorded through {@link ScriptErrorReporter} and does not stop
 *       the other renderers in the same frame;</li>
 *   <li>when the Graal Context that owns a callback Value is dead (statement limit hit, for
 *       example), dispatch skips the entry and removes it (self-cleanup, so a leak lasts at
 *       most until the next event);</li>
 *   <li>there are only two swap points: the candidate commit point and the first registration
 *       of a new active Context. There is no separate "clear the whole table before scripts
 *       load" step, since that would clear the old active renderers as well.</li>
 * </ul>
 *
 * <p>This class references no Minecraft client type; the version-side listener builds the
 * render context object and passes it through ({@link HudRenderContextJS}/
 * {@link WorldRenderContextJS}).
 */
public final class ClientRenderRegistry {
    /** HUD render layer: BACKGROUND runs before the vanilla HUD (RenderGuiEvent.Pre), NORMAL and FOREGROUND after it. */
    public enum HudLayer {
        BACKGROUND, NORMAL, FOREGROUND
    }

    /** World render layer: EARLY after translucent blocks, NORMAL after weather, LATE at the end of level rendering. */
    public enum WorldLayer {
        EARLY, NORMAL, LATE
    }

    /**
     * id to renderer entry. Reads and writes all happen on the client owner thread (script
     * loading and render dispatch both run on the Render thread); ConcurrentHashMap only
     * tolerates one traversal snapshot while a whole batch is swapped in, and does not mean there
     * is cross-thread write contention.
     */
    private static final Map<String, Entry> HUD_RENDERERS = new ConcurrentHashMap<>();
    private static final Map<String, Entry> WORLD_RENDERERS = new ConcurrentHashMap<>();

    /**
     * Pending batches of candidate generations, keyed by the candidate Context under
     * construction. The batch exists only during candidate construction: it is removed at the
     * commit point through {@link Candidate#publish()}, and on candidate failure or
     * cancellation it is removed right then through {@link CandidateStatePlan#discard()}
     * instead of lingering until the first registration of the next candidate round prunes it.
     */
    private static final Map<Context, Candidate> CANDIDATE_BATCHES = new ConcurrentHashMap<>();

    /** Context of the generation that owns the production tables; the non-transactional path checks
     * it to decide when to swap the tables. */
    private static final java.util.Set<Context> activeContexts = ConcurrentHashMap.newKeySet();

    /** Domain id of the candidate batch, shared by the collector and the candidate plan and used for
     * domain attribution in candidate failures. */
    static final String CANDIDATE_DOMAIN = "client-render-registration";

    private ClientRenderRegistry() {
    }

    /** Registers a HUD renderer, or replaces the one with the same id; the callback shape is
     * {@code (ctx, graphics) => void}. */
    public static void registerHud(String id, String scriptId, Context context, HudLayer layer, int priority, Value callback) {
        register(Kind.HUD, id, scriptId, context, layer.ordinal(), priority, callback);
    }

    /** Registers a world renderer, or replaces the one with the same id; the callback shape is {@code (ctx) => void}. */
    public static void registerWorld(String id, String scriptId, Context context, WorldLayer layer, int priority, Value callback) {
        register(Kind.WORLD, id, scriptId, context, layer.ordinal(), priority, callback);
    }

    /** Removes the HUD renderer with this id; returns {@code true} when it existed and was removed. */
    public static boolean unregisterHud(String id) {
        return HUD_RENDERERS.remove(id) != null;
    }

    /** Removes the world renderer with this id; returns {@code true} when it existed and was removed. */
    public static boolean unregisterWorld(String id) {
        return WORLD_RENDERERS.remove(id) != null;
    }

    /** Returns whether the HUD layer has at least one live renderer. The render event fires every
     * frame, so this keeps the no-listener path cheap. */
    public static boolean hasHud(HudLayer layer) {
        return hasListener(HUD_RENDERERS, layer.ordinal());
    }

    /** Returns whether the world layer has at least one live renderer. */
    public static boolean hasWorld(WorldLayer layer) {
        return hasListener(WORLD_RENDERERS, layer.ordinal());
    }

    /** Clears all HUD and world renderers and releases the generation identity, so the next
     * registration starts a new generation. */
    public static void clearAll() {
        HUD_RENDERERS.clear();
        WORLD_RENDERERS.clear();
        CANDIDATE_BATCHES.clear();
        activeContexts.clear();
    }

    /**
     * Read-only lifecycle query: how many pending candidate registration batches exist right
     * now. A committed, failed or cancelled candidate leaves none behind, because the batch is
     * inert bookkeeping tied to a candidate Context; a nonzero count after a candidate ended
     * means candidate resources were not fully released. This is deliberately only a count: the
     * map, its Context keys and the registered callbacks stay private.
     */
    public static int pendingCandidateRegistrations() {
        return CANDIDATE_BATCHES.size();
    }

    /**
     * Dispatches the renderers of the given HUD layer in ascending priority order, calling each
     * with {@code (ctx, graphics)}. A throwing callback is only recorded and does not stop the
     * others; entries whose Context is dead are skipped and removed.
     */
    public static void dispatchHud(HudLayer layer, Object ctx, Object graphics) {
        dispatch(HUD_RENDERERS, layer.ordinal(), "hudRender", ctx, graphics);
    }

    /** Dispatches the renderers of the given world layer in ascending priority order, calling each with {@code (ctx)}. */
    public static void dispatchWorld(WorldLayer layer, Object ctx) {
        dispatch(WORLD_RENDERERS, layer.ordinal(), "worldRender", ctx, null);
    }

    // ---- Registration routing: candidate batch or production table ----

    private enum Kind {
        HUD, WORLD
    }

    private static Map<String, Entry> live(Kind kind) {
        return kind == Kind.HUD ? HUD_RENDERERS : WORLD_RENDERERS;
    }

    /**
     * Routes one script registration: when the source Context belongs to a candidate generation
     * under construction, the entry goes into the candidate batch. Otherwise the current active
     * generation is claimed or reused and the entry is written to the production table.
     */
    private static void register(Kind kind, String id, String scriptId, Context context,
                                 int layer, int priority, Value callback) {
        Entry entry = new Entry(scriptId, context, layer, priority, callback);
        if (!collectIntoCandidate(kind, id, entry, context)) {
            beginActiveGeneration(context);
            live(kind).put(id, entry);
        }
    }

    /**
     * Candidate collection: when {@code context} is a candidate Context under construction, the
     * entry goes into that candidate's inert batch, so the production tables stay untouched and
     * render hooks cannot see it. The batch hangs off the candidate's {@code CandidateStatePlan},
     * is published as a whole at the commit point and is dropped with the candidate on failure
     * or cancellation. Returns {@code false} when this is not a candidate registration.
     */
    private static boolean collectIntoCandidate(Kind kind, String id, Entry entry, Context context) {
        if (context == null) {
            return false;
        }
        Candidate batch = CANDIDATE_BATCHES.get(context);
        if (batch == null) {
            Candidate fresh = new Candidate(context);
            if (!ScriptManager.registerCandidatePlan(context, fresh)) {
                // Non-candidate Context (initial load, kill and rebuild, single-file reload): production table path
                return false;
            }
            // Each participant owns its batch; teardown releases failed candidates.
            fresh.registered = true;
            CANDIDATE_BATCHES.put(context, fresh);
            batch = fresh;
        }
        batch.add(kind, id, entry);
        return true;
    }

    /**
     * Domain collector entry point (DOMAIN_PLAN phase): attaches this candidate's batch to the
     * candidate's preflight and publish steps.
     *
     * <p>Registration happens <b>unconditionally</b>, even for an empty batch: "the new
     * generation registers no renderer at all" is the only path that retires the old renderers,
     * and only an empty batch that also reaches the commit point turns the production tables
     * into empty tables (the empty-plan rule of
     * {@link com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector}). When the same batch
     * instance was already registered during candidate execution, it is not registered again.
     */
    static void registerCandidateBatch(Context context,
            com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector.Handle handle) {
        if (context == null) {
            return;
        }
        Candidate existing = CANDIDATE_BATCHES.get(context);
        Candidate batch = existing != null ? existing : new Candidate(context);
        if (!batch.registered) {
            // Register the plan first, put the map entry second: if the candidate is already
            // tearing down (registerPlan throws), a fresh batch never enters the map at all.
            batch.registered = true;
            handle.registerPlan(batch);
        }
        Candidate current = batch;
        CANDIDATE_BATCHES.put(context, current);
    }

    /** Whole-table domains publish once for all contexts, including empty participants. */
    static void registerCandidateBatches(com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector.Handle handle) {
        List<Context> contexts = handle.candidateContexts();
        if (contexts.size() == 1) {
            registerCandidateBatch(contexts.getFirst(), handle);
            return;
        }
        List<Candidate> batches = new ArrayList<>();
        for (Context context : contexts) {
            Candidate batch = CANDIDATE_BATCHES.computeIfAbsent(context, Candidate::new);
            batch.delegated = true;
            batches.add(batch);
        }
        handle.registerPlan(new CandidateStatePlan() {
            public String domain() { return CANDIDATE_DOMAIN; }
            public void preflight() {}
            public void publish() {
                HUD_RENDERERS.clear(); WORLD_RENDERERS.clear(); activeContexts.clear();
                for (Candidate batch : batches) {
                    HUD_RENDERERS.putAll(batch.hud); WORLD_RENDERERS.putAll(batch.world);
                    activeContexts.add(batch.context); batch.published = true;
                    CANDIDATE_BATCHES.remove(batch.context, batch);
                }
            }
            public void discard() { batches.forEach(Candidate::discard); }
        });
    }

    /**
     * Claims the generation that owns the production tables: the first registration from a new
     * Context swaps the whole table. Initial load and kill and rebuild each get their own new
     * Context (the scripts rerun from the same set of sources), while a single-file reload
     * reuses the current active Context, so it does not clear the other renderers of the same
     * generation. The candidate commit sets the same identity through
     * {@link Candidate#publish()}.
     *
     * <p>Table swaps and render dispatch both run on the client owner thread (an F3+T resource
     * reload and {@code ClientReloadExecutor} both hand off to the Render thread), so nothing
     * reads the table between clear and put.
     */
    private static void beginActiveGeneration(Context context) {
        if (context == null || activeContexts.contains(context)) {
            return;
        }
        HUD_RENDERERS.clear();
        WORLD_RENDERERS.clear();
        activeContexts.clear(); activeContexts.add(context);
    }

    private static boolean hasListener(Map<String, Entry> map, int layerOrdinal) {
        for (Entry entry : map.values()) {
            if (entry.layer() == layerOrdinal && !ScriptManager.isContextDead(entry.context())) {
                return true;
            }
        }
        return false;
    }

    private static void dispatch(Map<String, Entry> map, int layerOrdinal, String kind, Object ctx, Object graphics) {
        List<Entry> ordered = null;
        for (Map.Entry<String, Entry> e : map.entrySet()) {
            Entry entry = e.getValue();
            if (entry.layer() != layerOrdinal) {
                continue;
            }
            if (ScriptManager.isContextDead(entry.context())) {
                // Self-cleanup: a closure on a dead Context can never run again, so remove it safely (only if
                // the entry under this id is still the same one)
                map.remove(e.getKey(), entry);
                continue;
            }
            if (ordered == null) {
                ordered = new ArrayList<>(4);
            }
            ordered.add(entry);
        }
        if (ordered == null) {
            return;
        }
        if (ordered.size() > 1) {
            ordered.sort(Comparator.comparingInt(Entry::priority));
        }
        for (Entry entry : ordered) {
            invokeEntry(entry, kind, ctx, graphics);
        }
    }

    private static void invokeEntry(Entry entry, String kind, Object ctx, Object graphics) {
        if (ScriptManager.isContextDead(entry.context())) return;
        ScriptManager.noteCallbackEnter();
        try {
            String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(entry.context(), entry.scriptId());
            try {
                if (graphics != null) {
                    entry.callback().executeVoid(ctx, graphics);
                } else {
                    entry.callback().executeVoid(ctx);
                }
            } finally {
                ScriptContextRegistry.restoreCurrentScriptId(entry.context(), previousScriptId);
            }
        } catch (Throwable t) {
            if (t instanceof InterruptedException) Thread.currentThread().interrupt();
            if (t instanceof Error) throw (Error) t;
            ScriptManager.reportContextKilled(entry.context(), t);
            ScriptErrorReporter.recordCallbackError(
                    ScriptType.CLIENT,
                    "renderer kind=" + kind + " script=" + (entry.scriptId() == null ? "unknown" : entry.scriptId()),
                    t);
        } finally {
            ScriptManager.noteCallbackExit();
        }
    }

    private record Entry(String scriptId, Context context, int layer, int priority, Value callback) {
    }

    /**
     * Renderer registrations collected for one candidate generation (inert: it only records ids
     * and never touches the production tables).
     *
     * <p>It only hangs off the candidate's {@code CandidateStatePlan}. Preflight has nothing to
     * reject, because payload validation happens at the collection point in
     * {@code RenderRegistrationBusJS#execute}, where an invalid id, layer or callback fails right
     * away, so this batch only serves the commit point. On failure or cancellation it is dropped
     * with the candidate and the old active renderers are not affected.
     */
    private static final class Candidate implements CandidateStatePlan {

        private final Context context;

        /** Whether the batch is already attached to the candidate's plan: either the first registration
         * during execution or the DOMAIN_PLAN collection attaches it, never both. */
        private boolean registered;
        private boolean delegated;
        private boolean published;
        private final Map<String, Entry> hud = new LinkedHashMap<>();
        private final Map<String, Entry> world = new LinkedHashMap<>();

        Candidate(Context context) {
            this.context = context;
        }

        void add(Kind kind, String id, Entry entry) {
            (kind == Kind.HUD ? hud : world).put(id, entry);
        }

        @Override
        public String domain() {
            return CANDIDATE_DOMAIN;
        }

        /** This batch has nothing to reject at the candidate phase (see the class comment); it exists
         * only for the commit-time swap. */
        @Override
        public void preflight() {
        }

        /**
         * Commit point (owner thread): swaps the whole production tables for this batch. Ids the
         * old generation declared but this batch does not are cleared too, so no stale renderer
         * remains. The plan contract requires publish not to throw, and neither bulk operation
         * does.
         */
        @Override
        public void publish() {
            if (delegated) return;
            HUD_RENDERERS.clear();
            HUD_RENDERERS.putAll(hud);
            WORLD_RENDERERS.clear();
            WORLD_RENDERERS.putAll(world);
            activeContexts.clear(); activeContexts.add(context);
            published = true;
            CANDIDATE_BATCHES.remove(context, this);
        }

        /**
         * Candidate failure or cancellation (the discardCandidate path, owner thread): releases
         * the inert batch right away instead of letting it linger keyed by the dead candidate
         * Context until the next round prunes it. The production renderer tables are untouched
         * because publish never ran, so the old active generation keeps serving. Idempotent and
         * non-throwing per the plan contract.
         */
        @Override
        public void discard() {
            CANDIDATE_BATCHES.remove(context, this);
            if (published) {
                HUD_RENDERERS.values().removeIf(entry -> entry.context() == context);
                WORLD_RENDERERS.values().removeIf(entry -> entry.context() == context);
                activeContexts.remove(context);
            }
        }
    }
}
