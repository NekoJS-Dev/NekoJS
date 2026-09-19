package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Runtime-owner mutable binding-schema store. Package-private on purpose:
 * {@link NekoModulePipelineCache} is the only component that may install active
 * views or drive candidate transactions. Everything crossing the API boundary is
 * an immutable {@link ScriptBindingSchema.View} / {@link ScriptBindingSchema.Snapshot}.
 */
final class BindingSchemaStore {
    private final Map<ScriptType, ScriptBindingSchema.View> active = new ConcurrentHashMap<>();
    private final Map<Object, Candidate> candidates = new ConcurrentHashMap<>();
    private volatile boolean closed;

    /** Install the active view owned by this runtime root. */
    void installActive(ScriptType type, Map<String, ScriptBindingSchema.BindingMembers> nameToMembers,
                       Set<String> globals) {
        ensureOpen();
        if (type == null) return;
        active.put(type, new ScriptBindingSchema.View(nameToMembers, globals));
    }

    void clear(ScriptType type) {
        if (type != null) active.remove(type);
    }

    /** Close the runtime owner and drop active/candidate views together. */
    void close() {
        closed = true;
        active.clear();
        candidates.clear();
    }

    Map<String, ScriptBindingSchema.BindingMembers> lookup(ScriptType type) {
        return activeView(type).lookup();
    }

    /** Snapshot the active view without exposing mutable maps to a generation. */
    ScriptBindingSchema.View activeView(ScriptType type) {
        if (type == null) return ScriptBindingSchema.emptyView();
        return active.getOrDefault(type, ScriptBindingSchema.emptyView());
    }

    /** Capture the active schema/global values before a candidate starts mutating its own view. */
    ScriptBindingSchema.Snapshot snapshot(ScriptType type) {
        return new ScriptBindingSchema.Snapshot(activeView(type));
    }

    ScriptBindingSchema.View beginCandidate(Object ownerToken, ScriptType type,
            Map<String, ScriptBindingSchema.BindingMembers> schemas, Set<String> globals) {
        return beginCandidate(ownerToken, type, schemas, globals,
                (Consumer<ScriptBindingSchema.Diagnostic>) null);
    }

    /** Install candidate values under one generation/session token. */
    ScriptBindingSchema.View beginCandidate(Object ownerToken, ScriptType type,
            Map<String, ScriptBindingSchema.BindingMembers> schemas, Set<String> globals,
            Consumer<ScriptBindingSchema.Diagnostic> reporter) {
        ensureOpen();
        if (ownerToken == null || type == null) throw new NullPointerException("candidate schema owner/type");
        ScriptBindingSchema.View view = new ScriptBindingSchema.View(schemas, globals, reporter);
        candidates.put(ownerToken, new Candidate(type, view, snapshot(type)));
        return view;
    }

    /** Publish exactly one candidate view at the generation commit point. */
    ScriptBindingSchema.View commitCandidate(Object ownerToken) {
        Candidate candidate = ownerToken == null ? null : candidates.remove(ownerToken);
        if (candidate == null) return null;
        active.put(candidate.type(), candidate.view());
        return candidate.view();
    }

    /** Discard a candidate schema after any preparation/binding/execution failure. */
    void discardCandidate(Object ownerToken) {
        if (ownerToken == null) return;
        Candidate candidate = candidates.remove(ownerToken);
        if (candidate != null) {
            // Schema transactions are serialized with generation commit by the owning manager;
            // restore the captured active view only if no newer same-type generation published.
            // This keeps a late failure from an older candidate from rolling back a committed one.
            if (activeView(candidate.type()).equals(candidate.activeBefore().view())) {
                active.put(candidate.type(), candidate.activeBefore().view());
            }
        }
    }

    /** Restore an active schema/global snapshot for an owning reload transaction. */
    void restore(ScriptType type, ScriptBindingSchema.Snapshot snapshot) {
        if (type == null || snapshot == null) return;
        active.put(type, snapshot.view());
    }

    /** Resolve a generation view for preparation/validation. */
    ScriptBindingSchema.View view(Object ownerToken, ScriptType type) {
        if (ownerToken == null) return activeView(type);
        Candidate candidate = candidates.get(ownerToken);
        return candidate != null && candidate.type() == type
                ? candidate.view() : ScriptBindingSchema.emptyView();
    }

    private record Candidate(ScriptType type, ScriptBindingSchema.View view,
                             ScriptBindingSchema.Snapshot activeBefore) {}

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("ScriptBindingSchema owner is closed");
    }
}
