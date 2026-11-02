package com.tkisor.nekojs.core.pack;

import java.util.Objects;

/** Tracks desired pack content separately from the last successfully loaded content. */
public final class ScriptPackResourceState {
    public record Revision(Object repository, String signature) {}

    private Object repository;
    private String desired = "";
    private String applied = "";

    public synchronized boolean select(Object repository, String signature) {
        Objects.requireNonNull(repository);
        Objects.requireNonNull(signature);
        if (this.repository != repository) {
            // A new empty repository needs no reload unless the previous selection had packs.
            applied = signature.isEmpty() && desired.isEmpty() ? "" : null;
            this.repository = repository;
        }
        desired = signature;
        return !desired.equals(applied);
    }

    /** Retires the repository so late completions cannot acknowledge a later server. */
    public synchronized void reset() {
        repository = null;
        desired = "";
        applied = "";
    }

    public synchronized Revision capture() {
        return new Revision(repository, desired);
    }

    public synchronized Revision capture(Object repository) {
        select(repository, desired);
        return capture();
    }

    /** Old repositories cannot clear pending work for a new server. */
    public synchronized boolean applied(Revision revision) {
        if (repository != revision.repository()) return false;
        applied = revision.signature();
        return desired.equals(applied);
    }
}
