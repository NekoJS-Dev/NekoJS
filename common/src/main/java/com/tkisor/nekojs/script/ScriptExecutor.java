package com.tkisor.nekojs.script;

import com.tkisor.nekojs.core.compiler.GlobalBindingMemberValidator;
import com.tkisor.nekojs.core.JavaClassLoadTelemetry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.script.context.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;

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
    private final ScriptType scriptType;
    private final ErrorTracker errorTracker;
    private final NekoJSPaths paths;
    private final SandboxConfig sandboxConfig;

    public ScriptExecutor(ScriptType scriptType, ErrorTracker errorTracker, NekoJSPaths paths, SandboxConfig sandboxConfig) {
        this.scriptType = scriptType;
        this.errorTracker = errorTracker;
        this.paths = paths;
        this.sandboxConfig = sandboxConfig;
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
            script.disabled = true;
            script.lastError = t;

            ScriptError scriptError = errorTracker.record(script, t);
            script.type.logger().error("脚本执行失败: {}\n{}", script.id.toString(), scriptError.getLogDetailText(sandboxConfig.conciseScriptErrorLogs()));
        }
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
        } catch (Throwable ignored) {
            // 校验只报告错误，绝不阻塞脚本执行
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
