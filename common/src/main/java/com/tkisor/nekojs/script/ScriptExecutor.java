package com.tkisor.nekojs.script;

import com.tkisor.nekojs.core.compiler.GlobalBindingMemberValidator;
import com.tkisor.nekojs.core.JavaClassLoadTelemetry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private final Runnable onContextKilled;

    public ScriptExecutor(ErrorTracker errorTracker, NekoJSPaths paths, SandboxConfig sandboxConfig, Runnable onContextKilled) {
        this.errorTracker = errorTracker;
        this.paths = paths;
        this.sandboxConfig = sandboxConfig;
        this.onContextKilled = onContextKilled;
    }

    public void executeEntry(Context ctx, ScriptContainer script, NekoNodeRuntime nodeRuntime) {
        try {
            synchronized (ctx) {
                Path relativePath = paths.root().relativize(script.path);
                String requirePath = "./" + relativePath.toString().replace("\\", "/");

                errorTracker.clear(script.id);
                errorTracker.clearByScriptPath(script.type, relativePath.toString().replace("\\", "/"));

                validateGlobalBindings(script);

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
            if (isContextKilledByResourceLimits(t)) {
                // Graal 因语句上限关闭了 Context：通知 ScriptManager 下次取用时重建环境
                onContextKilled.run();
            }
            script.disabled = true;
            script.lastError = t;

            ScriptError scriptError = errorTracker.record(script, t);
            script.type.logger().error("脚本执行失败: {}\n{}", script.id.toString(), scriptError.getLogDetailText(sandboxConfig.conciseScriptErrorLogs()));
        }
    }

    /**
     * 沿异常链识别「Context 已被 Graal 资源上限关闭」：eval 抛 cancelled / resourceExhausted。
     *
     * <p>公共静态：入口执行路径（{@link #executeEntry}）与 JS 回调 catch 路径
     * （{@code EventBusJS} 监听器 / {@code NekoNodeTimers.execute}，经
     * {@link ScriptManager#reportContextKilled}）共享同一判定逻辑，避免复制。
     */
    public static boolean isContextKilledByResourceLimits(Throwable t) {
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
    private void validateGlobalBindings(ScriptContainer script) {
        try {
            String source = Files.readString(script.path);
            GlobalBindingMemberValidator.validate(script.path, source);
        } catch (Throwable t) {
            // 校验只报告错误，绝不阻塞脚本执行；但校验器自身崩了不能无声吞掉——
            // 否则所有成员校验静默消失且无人察觉（文件读不出来除外，那条已由
            // preload 失败路径上报错误面板）
            com.tkisor.nekojs.NekoJS.LOGGER.warn(
                    "Global binding preflight failed for {}: {}", script.path, t.toString(), t);
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
        long timeoutSeconds = sandboxConfig.scriptEvaluationTimeoutSeconds();
        // 有界等待：顶层 await / native ESM 求值永不完成时，超过总时限即抛出超时错误，
        // 走 executeEntry 的既有失败路径（errorTracker 记录 + 日志），不再无限忙等挂死服务器线程。
        // 注意：不在此线程 close Graal Context（可能中断其它脚本的执行）—— 只停止等待并上报失败。
        long deadlineNanos = timeoutSeconds > 0
                ? System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
                : Long.MAX_VALUE;
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
            if (timeoutSeconds > 0 && System.nanoTime() - deadlineNanos >= 0) {
                throw new TimeoutException("脚本求值超时（超过 " + timeoutSeconds
                        + " 秒，可在 nekojs/config/engine.toml 中调整 scriptEvaluationTimeoutSeconds）：入口脚本的顶层 await 或模块加载可能永不完成");
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
