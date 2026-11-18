package com.tkisor.nekojs.script;

import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.JavaClassLoadTelemetry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Runs scripts: sets and restores the current script id, enters and leaves the Java class-load
 * telemetry scope, asks the module loader host to load the entry, waits for the native ESM
 * evaluation / TLA to finish, and hands execution errors to {@link ErrorTracker}.
 *
 * <p>{@link ScriptManager} keeps the top-level discover/load/reload/close lifecycle and does not
 * handle execution details itself.
 */
public final class ScriptExecutor {
    private final ErrorTracker errorTracker;
    private final NekoJSPaths paths;
    private final SandboxConfig sandboxConfig;
    /** The kill report carries the Context where evaluation happened, which distinguishes active
     * from candidate generations. */
    private final Consumer<Context> onContextKilled;

    public ScriptExecutor(ErrorTracker errorTracker, NekoJSPaths paths, SandboxConfig sandboxConfig, Consumer<Context> onContextKilled) {
        this.errorTracker = errorTracker;
        this.paths = paths;
        this.sandboxConfig = sandboxConfig;
        this.onContextKilled = onContextKilled;
    }

    public void executeEntry(Context ctx, ScriptContainer script, NekoNodeRuntime nodeRuntime,
                             NekoModulePipelineCache moduleSession) {
        try {
            synchronized (ctx) {
                // Pack scripts may carry platform-relative paths (WORLD packs before the registry normalized
                // them; see ScriptPackRegistry#activateWorldPacks) while the nekojs root is always
                // absolute — relativize over mixed inputs throws IAE, so normalize first.
                Path relativePath = paths.root().relativize(script.path.toAbsolutePath().normalize());
                String requirePath = "./" + relativePath.toString().replace("\\", "/");

                errorTracker.clear(ctx, script.id);
                errorTracker.clearByScriptPath(ctx, script.type, relativePath.toString().replace("\\", "/"));

                if (nodeRuntime == null || nodeRuntime.moduleLoaderHost() == null) {
                    moduleSession.validateEntryBindings(script.path);
                }

                JavaClassLoadTelemetry.enter(script.type, script.id.toString());
                ScriptContextRegistry.switchCurrentScriptId(ctx, script.id.toString());
                try {
                    waitForEntryModule(ctx, script, requirePath, nodeRuntime);
                } finally {
                    ScriptContextRegistry.switchCurrentScriptId(ctx, null);
                    JavaClassLoadTelemetry.exit();
                }

                script.disabled = false;
                script.lastError = null;
            }
        } catch (Throwable t) {
            if (isContextTerminated(t)) {
                // Host cancellation still invalidates the owning active or candidate generation.
                // Cleanup remains on the owner thread; termination does not request a new runtime.
                onContextKilled.accept(ctx);
            }
            script.disabled = true;
            script.lastError = t;

            ScriptError scriptError = errorTracker.record(ctx, script, t);
            // The log line reuses the same frozen record: the attribution line is appended after the existing
            // detail text, so the body format stays unchanged.
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(script.type).error("[NEKO-1003] 脚本执行失败: {}\n{}\n{} — script execution failed",
                    script.id.toString(), scriptError.getLogDetailText(sandboxConfig.conciseScriptErrorLogs()),
                    scriptError.diagnostic().describe());
        }
    }

    /** Identifies host/Graal termination through wrapped causes without configuring execution limits. */
    public static boolean isContextTerminated(Throwable t) {
        for (Throwable current = t; current != null; current = current.getCause()) {
            if (current instanceof PolyglotException pe && (pe.isCancelled() || pe.isResourceExhausted())) {
                return true;
            }
        }
        return false;
    }

    private void waitForEntryModule(Context ctx, ScriptContainer script, String requirePath, NekoNodeRuntime nodeRuntime) throws Exception {
        if (nodeRuntime == null || nodeRuntime.moduleLoaderHost() == null) {
            ctx.eval("js", "globalThis.__nekoScriptLoader.loadEntry").execute(requirePath);
            return;
        }
        CompletableFuture<?> evaluation = nodeRuntime.moduleLoaderHost().loadEntryAsync(requirePath);
        waitForEvaluation(evaluation, nodeRuntime);
    }

    private void waitForEvaluation(CompletableFuture<?> evaluation, NekoNodeRuntime nodeRuntime) throws Exception {
        // Short future polls let ready timers advance top-level await; they impose no evaluation deadline.
        while (!evaluation.isDone()) {
            if (nodeRuntime != null) {
                nodeRuntime.flushReadyTimers();
            }
            try {
                evaluation.get(1L, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (ExecutionException e) {
                throw unwrapExecutionException(e);
            }
        }
        try {
            evaluation.get();
        } catch (ExecutionException e) {
            throw unwrapExecutionException(e);
        }
    }

    private Exception unwrapExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof Exception exception) {
            return exception;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new RuntimeException(cause);
    }
}
