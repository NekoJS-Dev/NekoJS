package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.facade.I18nFacade;
import com.tkisor.nekojs.core.state.GenerationGlobals;
import com.tkisor.nekojs.core.state.GlobalStateStores;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Root-owned sessions for bundled UI code, independent of user script generations. */
public final class BuiltinUiRuntime implements AutoCloseable {
    private final Engine engine;
    private final HostAccess hostAccess;
    private final Set<Session> sessions = new LinkedHashSet<>();
    private boolean closed;
    private BuiltinUiModules modules;

    public BuiltinUiRuntime(Engine engine, HostAccess hostAccess) {
        this.engine = java.util.Objects.requireNonNull(engine);
        this.hostAccess = java.util.Objects.requireNonNull(hostAccess);
    }

    public synchronized Session open(Consumer<Runnable> ownerQueue) {
        if (closed) throw new IllegalStateException("[NEKO-7015] Built-in UI runtime is closed");
        java.util.Objects.requireNonNull(ownerQueue);
        BuiltinUiModules prepared = modules();
        Context context = Context.newBuilder("js").engine(engine)
                .allowHostAccess(hostAccess).allowHostClassLookup(name -> false)
                .allowIO(IOAccess.newBuilder().fileSystem(prepared).build())
                .allowExperimentalOptions(true).option("js.esm-eval-returns-exports", "true")
                .option("js.nashorn-compat", "true").option("js.strict", "true")
                .option("js.ecmascript-version", "latest")
                .allowCreateThread(false).allowCreateProcess(false).build();
        try {
            Session session = new Session(context, ownerQueue, prepared);
            sessions.add(session);
            return session;
        } catch (RuntimeException | Error failure) {
            context.close(true);
            throw failure;
        }
    }

    @Override
    public void close() {
        List<Session> current;
        synchronized (this) {
            if (closed) return;
            closed = true;
            current = List.copyOf(sessions);
            modules = null;
        }
        RuntimeException first = null;
        for (Session session : current) {
            try { session.close(); }
            catch (RuntimeException failure) {
                if (first == null) first = failure;
                else first.addSuppressed(failure);
            }
        }
        if (first != null) throw first;
    }

    public final class Session implements AutoCloseable {
        private final Context context;
        private final BuiltinUiModules modules;
        private final Thread owner = Thread.currentThread();
        private final Consumer<Runnable> ownerQueue;
        private final GlobalStateStores stores = new GlobalStateStores();
        private final GenerationGlobals globals = stores.newGeneration(ScriptType.CLIENT, false);
        private volatile boolean closing;
        private boolean released;
        private Runnable beforeRelease = () -> { };

        private Session(Context context, Consumer<Runnable> ownerQueue, BuiltinUiModules modules) {
            this.context = context;
            this.modules = modules;
            this.ownerQueue = java.util.Objects.requireNonNull(ownerQueue);
        }

        public Context context() { requireOwner(); return context; }
        public GenerationGlobals globals() { requireOwner(); return globals; }
        public boolean isClosed() { return closing; }

        public void beforeRelease(Runnable cleanup) { requireOwner(); beforeRelease = java.util.Objects.requireNonNull(cleanup); }

        /** Loads only the canonical bundled UI and existing JSX runtime; no user filesystem or modules. */
        public Value errorDashboardFactory(I18nFacade localization) {
            requireOwner();
            if (closing) throw new IllegalStateException("[NEKO-7015] Built-in UI session is closed");
            context.getBindings("js").putMember("I18n", java.util.Objects.requireNonNull(localization));
            return context.eval(Source.newBuilder("js", modules.dashboardSource(), "nekojs/builtin-ui/error-dashboard.tsx")
                    .uri(modules.dashboardUri()).mimeType("application/javascript+module").buildLiteral()).getMember("default");
        }

        public void requireOwner() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("[NEKO-7004] Built-in UI requires its client owner thread");
            }
        }

        @Override
        public void close() {
            synchronized (this) {
                if (closing) return;
                closing = true;
            }
            if (Thread.currentThread() == owner && !entered()) {
                release();
            } else {
                // A guest callback must unwind before its Context closes. Root shutdown may
                // arrive off-thread; cancel guest work there, then retire native resources on the owner.
                try {
                    if (Thread.currentThread() != owner) context.close(true);
                } finally { ownerQueue.accept(this::release); }
            }
        }

        private boolean entered() {
            try { Context.getCurrent(); return true; }
            catch (IllegalStateException noCurrentContext) { return false; }
        }

        private void release() {
            requireOwner();
            if (released) return;
            released = true;
            try {
                beforeRelease.run();
            } finally {
                try { globals.close(); }
                finally {
                    try { stores.closeAll(); }
                    finally {
                        try { context.close(true); }
                        finally { synchronized (BuiltinUiRuntime.this) { sessions.remove(this); } }
                    }
                }
            }
        }
    }

    private synchronized BuiltinUiModules modules() {
        if (modules == null) {
            String runtime = com.tkisor.nekojs.core.compiler.NekoTypeScriptCompiler.eraseTypescript(
                    java.nio.file.Path.of("jsx-runtime.ts"), resource("/nekojs/node/modules/jsx-runtime.ts"));
            String dashboard = com.tkisor.nekojs.core.compiler.NekoJsxCompiler.compileTsx(
                    java.nio.file.Path.of("error-dashboard.tsx"), resource("/nekojs/builtin-ui/error-dashboard.tsx"), true).code();
            modules = new BuiltinUiModules(runtime, dashboard);
        }
        return modules;
    }

    private static String resource(String name) {
        try (var stream = BuiltinUiRuntime.class.getResourceAsStream(name)) {
            if (stream == null) throw new IllegalStateException("[NEKO-7015] Built-in UI resource is missing: " + name);
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("[NEKO-7015] Built-in UI resource could not be read: " + name, failure);
        }
    }
}
