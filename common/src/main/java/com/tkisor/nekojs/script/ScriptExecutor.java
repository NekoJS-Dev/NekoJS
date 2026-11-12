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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * 脚本执行器：负责设置/恢复 current script id、进入/退出 Java class-load telemetry scope、
 * 调用 module loader host 加载 entry、等待 native ESM evaluation / TLA 完成、把执行异常交给 {@link ErrorTracker}。
 *
 * <p>从 {@link ScriptManager} 的 {@code runScript} / {@code waitForEntryModule} /
 * {@code waitForEvaluation} 下沉而来。{@code ScriptManager} 保留 discover/load/reload/close
 * 顶层生命周期协调，不直接承担执行细节。
 */
public final class ScriptExecutor {
    private final ErrorTracker errorTracker;
    private final NekoJSPaths paths;
    private final SandboxConfig sandboxConfig;
    /** kill 上报携带发生求值的 Context：ticket 06 起区分 active / candidate generation。 */
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
                // Pack scripts may carry platform-relative paths (WORLD packs before the
                // registry normalized them; see ScriptPackRegistry#activateWorldPacks) while
                // the nekojs root is always absolute — relativize over mixed inputs throws
                // IAE (ticket 03 §3-1 / ticket 07 G4), so normalize first.
                Path relativePath = paths.root().relativize(script.path.toAbsolutePath().normalize());
                String requirePath = "./" + relativePath.toString().replace("\\", "/");

                errorTracker.clear(ctx, script.id);
                errorTracker.clearByScriptPath(ctx, script.type, relativePath.toString().replace("\\", "/"));

                validateGlobalBindings(ctx, script, moduleSession);

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
            // Log history consumes the same frozen record (ticket 30): the attribution line is
            // appended after the existing detail text, keeping the legacy body unchanged.
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

    /**
     * 加载时校验入口脚本对全局绑定（Utils/Platform/Items 等）的成员访问。
     *
     * <p>每次执行/重载都跑（而非只在编译时），保证游戏内错误面板在完整重载（源码未改、模块缓存命中）
     * 时仍准确反映当前脚本状态 —— 编译时校验（{@code NekoModulePipeline}）受静态缓存限制，这里补足入口脚本。
     */
    private void validateGlobalBindings(Context context, ScriptContainer script,
                                        NekoModulePipelineCache moduleSession) {
        String source;
        try {
            source = Files.readString(script.path);
        } catch (IOException e) {
            // 读取失败已由 preload 失败路径上报错误面板（ScriptManager.reportPreloadFailure），不重复
            return;
        }
        try {
            moduleSession.validateEntryBindings(script.path, source);
        } catch (Throwable t) {
            // 校验只报告错误，绝不阻塞脚本执行；但校验器自身崩了不能无声吞掉——
            // 进错误面板（recordCallbackError 含里程碑节流），否则该类型的成员校验
            // 静默消失且无人察觉
            errorTracker.recordCallbackError(context, script.type, "preflight-validator", t);
        }
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
