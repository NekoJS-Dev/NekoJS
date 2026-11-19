package com.tkisor.nekojs.core.preflight;

import com.tkisor.nekojs.api.preflight.ScriptPreflight.Diagnostic;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Position;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Request;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Scope;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Stage;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Worker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Runs explicitly selected checks. Workers belong to the runtime root, not to a generation. */
public final class ScriptPreflightRunner implements AutoCloseable {
    private final ScriptPreflightCatalog catalog;
    private final SourceMapRegistry sourceMaps;
    private final Map<String, Worker> workers = new LinkedHashMap<>();
    private boolean closed;

    /** Creates a runner backed by the given provider catalog and source map registry. */
    public ScriptPreflightRunner(ScriptPreflightCatalog catalog, SourceMapRegistry sourceMaps) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.sourceMaps = Objects.requireNonNull(sourceMaps, "sourceMaps");
    }

    /** Returns findings only after this provider has finished successfully. */
    public List<Report> run(String id, Request request) throws ProviderFailure {
        var entry = catalog.require(id);
        var descriptor = entry.descriptor();
        if (!descriptor.supports(request.stage(), request.scope(), request.input(), request.capabilities())) {
            throw new IllegalArgumentException("[NEKO-1028] Preflight provider " + id + " does not support "
                    + request.stage() + "/" + request.scope() + " for " + request.input()
                    + " with capabilities " + request.capabilities() + " at " + request.path());
        }
        List<Diagnostic> diagnostics;
        synchronized (this) {
            Worker worker = worker(entry, request);
            try {
                diagnostics = List.copyOf(worker.check(request));
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                throw providerFailure(id, request, "run", failure);
            }
        }
        if (diagnostics.isEmpty()) return List.of();

        var positions = request.stage() == Stage.OUTPUT && request.sourceMap() != null
                && diagnostics.stream().anyMatch(diagnostic -> diagnostic.position() != null)
                ? sourceMaps.artifactPositions(request.path(), request.sourceMap()) : null;
        return diagnostics.stream().map(diagnostic -> new Report(id, descriptor.revision(), request.stage(),
                request.scope(), diagnostic, authoredPosition(request, diagnostic.position(), positions))).toList();
    }

    private static AuthoredPosition authoredPosition(Request request, Position position,
                                                      SourceMapRegistry.ArtifactPositions positions) {
        if (position == null) return null;
        if (request.stage() == Stage.SOURCE) return new AuthoredPosition(request.path().toString(), position);
        if (positions == null) return null;
        var mapped = positions.map(position.line(), position.column());
        return mapped.path == null ? null : new AuthoredPosition(mapped.path, new Position(mapped.line, mapped.column, -1));
    }

    private synchronized Worker worker(ScriptPreflightCatalog.Entry entry, Request request) throws ProviderFailure {
        if (closed) throw new IllegalStateException("[NEKO-1028] Preflight runner is closed");
        String id = entry.descriptor().id();
        Worker existing = workers.get(id);
        if (existing != null) return existing;
        try {
            Worker opened = Objects.requireNonNull(entry.provider().open(), "preflight worker");
            workers.put(id, opened);
            return opened;
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            throw providerFailure(id, request, "open", failure);
        }
    }

    /** Called by the root after its script sessions have stopped. */
    @Override
    public synchronized void close() throws ProviderFailure {
        Throwable first = null;
        boolean interrupted = false;
        try {
            for (var entry : detachWorkers().entrySet()) {
                try {
                    entry.getValue().close();
                } catch (VirtualMachineError | ThreadDeath fatal) {
                    // Keep the original fatal throwable primary, even if an earlier worker failed.
                    if (first instanceof Error) {
                        if (first != fatal) first.addSuppressed(fatal);
                    } else {
                        if (first != null) fatal.addSuppressed(first);
                        first = fatal;
                    }
                } catch (Throwable failure) {
                    interrupted |= failure instanceof InterruptedException;
                    var attributed = new ProviderFailure(entry.getKey(), null, "close", failure);
                    if (first == null) first = attributed;
                    else first.addSuppressed(attributed);
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (first instanceof Error fatal) throw fatal;
        if (first instanceof ProviderFailure failure) throw failure;
    }

    private synchronized Map<String, Worker> detachWorkers() {
        if (closed) return Map.of();
        var closing = new LinkedHashMap<>(workers);
        closed = true;
        workers.clear();
        return closing;
    }

    private static ProviderFailure providerFailure(String id, Request request, String operation, Throwable cause) {
        if (cause instanceof InterruptedException) Thread.currentThread().interrupt();
        return new ProviderFailure(id, request, operation, cause);
    }

    /** Authored location, absent when the current artifact cannot map a generated position. */
    public record AuthoredPosition(String path, Position position) {}

    /** A single preflight diagnostic paired with its provider metadata and optional authored source position. */
    public record Report(String providerId, String providerRevision, Stage stage, Scope scope,
                         Diagnostic diagnostic, AuthoredPosition authoredPosition) {}

    /** Provider failure, separate from normal findings and frontend compilation errors. */
    public static final class ProviderFailure extends Exception {
        private final String providerId;
        private final Stage stage;
        private final Scope scope;

        private ProviderFailure(String id, Request request, String operation, Throwable cause) {
            super("[NEKO-1029] Preflight provider " + id + " failed to " + operation
                    + (request == null ? "" : " at " + request.path() + " (" + request.stage() + "/" + request.scope() + ")"), cause);
            this.providerId = id;
            this.stage = request == null ? null : request.stage();
            this.scope = request == null ? null : request.scope();
        }

        public String providerId() { return providerId; }
        public Stage stage() { return stage; }
        public Scope scope() { return scope; }
    }
}
