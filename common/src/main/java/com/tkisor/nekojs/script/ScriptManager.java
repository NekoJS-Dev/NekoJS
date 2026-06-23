package com.tkisor.nekojs.script;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.catalog.JavaClassLoadTelemetrySink;
import com.tkisor.nekojs.api.event.ScriptEventRegistrar;
import com.tkisor.nekojs.api.event.ScriptEvents;
import com.tkisor.nekojs.core.JavaClassLoadTelemetry;
import com.tkisor.nekojs.core.NekoSandboxBuilder;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.ScriptLocator;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePreparationCache;
import com.tkisor.nekojs.core.module.NekoScriptModuleLoaderHost;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.script.context.ScriptContextRegistry;
import com.tkisor.nekojs.script.context.ScriptContextSeam;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.tkisor.nekojs.api.annotation.CalledByDynamicCode;

/**
 * NekoJS 脚本引擎核心生命周期调度器。
 * <p>
 * 每个实例管理一种 {@link ScriptType}（STARTUP / SERVER / CLIENT / TEST）的完整脚本生命周期。
 * 通过 {@link NekoJS#scriptManagers} 按类型持有，采用依赖注入模式。
 * <p>
 * 全局对象（{@link ScriptEventBridge}、{@link com.tkisor.nekojs.script.prop.ScriptPropertyRegistry}）
 * 均通过 {@link #parent} 访问，本实例不重复持有。
 */
public final class ScriptManager {

    // ---- 最小静态：Context → ScriptManager 反向查找 ----
    private static final Map<Context, ScriptManager> CONTEXT_TO_MANAGER =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * 从 GraalVM Context 反向查找所属的 ScriptManager 实例。
     * 用于 EventBusJS / NekoNodeTimers 等 JS 回调场景，这些场景仅有 Context 引用。
     */
    public static ScriptManager from(Context context) {
        ScriptManager sm = CONTEXT_TO_MANAGER.get(context);
        if (sm == null) {
            throw new IllegalStateException("No ScriptManager registered for Context: " + context);
        }
        return sm;
    }

    // ---- 实例字段 ----

    /**
     * 拥有此 ScriptManager 的 NekoJS 实例（legacy 过渡）。
     * Phase 4E 后通过 {@link #scriptEventBridge} / {@link #scriptProperties} / {@link #errorTracker} 访问具体协作者。
     * Phase 6 删除。
     */
    @Deprecated
    public final NekoJS parent;

    private final ScriptEventBridge scriptEventBridge;
    private final ScriptPropertyRegistry scriptProperties;
    private final ErrorTracker errorTracker;
    private final NekoJSPaths paths;
    private final SandboxConfig sandboxConfig;

    /**
     * 本实例管理的脚本类型
     */
    public final ScriptType scriptType;

    private Context context;
    private NekoNodeRuntime nodeRuntime;
    private List<ScriptContainer> scripts;

    // ---- 构造函数 ----

    /**
     * @param parent    拥有此 manager 的 NekoJS 实例
     * @param scriptType 本实例管理的脚本类型
     * @deprecated 使用 {@link #ScriptManager(ScriptType, ScriptEventBridge, ScriptPropertyRegistry, ErrorTracker, NekoJSPaths)}
     */
    @Deprecated
    public ScriptManager(NekoJS parent, ScriptType scriptType) {
        this.parent = parent;
        this.scriptType = scriptType;
        this.scriptEventBridge = parent.scriptEventBridge;
        this.scriptProperties = parent.scriptProperties;
        this.errorTracker = NekoErrorTracker.legacyInstance();
        this.paths = NekoJSPaths.legacy();
        this.sandboxConfig = ClassFilter.INSTANCE.config();
    }

    public ScriptManager(ScriptType scriptType, ScriptEventBridge scriptEventBridge, ScriptPropertyRegistry scriptProperties, ErrorTracker errorTracker, NekoJSPaths paths, SandboxConfig sandboxConfig) {
        this.parent = null;
        this.scriptType = scriptType;
        this.scriptEventBridge = scriptEventBridge;
        this.scriptProperties = scriptProperties;
        this.errorTracker = errorTracker;
        this.paths = paths;
        this.sandboxConfig = sandboxConfig;
    }

    // ---- 配置 ----

    public void setJavaClassLoadTelemetrySink(JavaClassLoadTelemetrySink sink) {
        JavaClassLoadTelemetry.setSink(sink);
    }

    // ---- Context 访问（懒初始化） ----

    private Context getOrCreateContext() {
        if (context == null) {
            NekoSandboxBuilder.Sandbox sandbox = NekoSandboxBuilder.buildSandbox(scriptType);
            this.context = sandbox.context();
            this.nodeRuntime = sandbox.nodeRuntime();
            CONTEXT_TO_MANAGER.put(context, this);
            ScriptContextSeam.bind(context, scriptType);
            context.getBindings("js").putMember("__nekoCurrentScriptId", null);

            var bindings = context.getBindings("js");
            scriptEventBridge.bindEvents(bindings, scriptType);

            var environmentBindings = NekoPluginRuntime.current().bindings(scriptType);
            environmentBindings.forEach((name, binding) -> {
                Object obj = binding.value();
                if (obj instanceof Class<?>) {
                    Value javaType = bindings.getMember("Java").invokeMember("type", ((Class<?>) obj).getName());
                    bindings.putMember(name, javaType);
                } else {
                    bindings.putMember(name, obj);
                }
            });

            installJavaClassLoadTelemetry(context, scriptType);
        }
        return context;
    }

    private void installJavaClassLoadTelemetry(Context ctx, ScriptType type) {
        if (!JavaClassLoadTelemetry.isEnabled()) return;

        var bindings = ctx.getBindings("js");
        bindings.putMember("__nekoJavaClassLoadTelemetry", new JavaClassLoadTelemetry());
        bindings.putMember("__nekoScriptType", type.name());
        bindings.putMember("__nekoCurrentScriptId", null);
        ctx.eval("js", """
                (function() {
                    if (Java.__nekoTypeTelemetryInstalled) return;
                    const rawType = Java.type.bind(Java);
                    Java.type = function(className) {
                        const result = rawType(className);
                        if (typeof __nekoCurrentScriptId === 'string') {
                            __nekoJavaClassLoadTelemetry.recordLoad(__nekoScriptType, __nekoCurrentScriptId, String(className));
                        }
                        return result;
                    };
                    Java.loadClass = Java.type;
                    Object.defineProperty(Java, '__nekoTypeTelemetryInstalled', { value: true, enumerable: false });
                })();
                """);
    }

    // ---- 脚本发现 ----

    /**
     * 发现本类型对应的脚本文件
     */
    public void discoverScripts() {
        List<ScriptContainer> discovered = ScriptLocator.discover(scriptType, scriptProperties);
        this.scripts = discovered;
        scriptType.logger().info("发现了 {} 个 {} 脚本。", discovered.size(), scriptType.name());
    }

    // ---- 脚本加载与执行 ----

    /**
     * 加载并顺序执行所有脚本
     */
    public void loadScripts() {
        if (scripts == null || scripts.isEmpty()) {
            scriptType.logger().info("没有需要加载的 {} 脚本。", scriptType.name());
            return;
        }

        Context ctx = getOrCreateContext();

        for (var script : scripts) {
            script.preload();
        }

        scripts.sort((s1, s2) -> {
            int p1 = s1.properties.getOrDefault(ScriptProperty.PRIORITY);
            int p2 = s2.properties.getOrDefault(ScriptProperty.PRIORITY);
            return Integer.compare(p2, p1);
        });

        for (ScriptContainer script : scripts) {
            if (script.shouldRun()) {
                runScript(ctx, script);
            }
        }

        if (scriptType == ScriptType.STARTUP) {
            flushReadyNodeTimers();
            ScriptEvents.post(getScriptEventRegistrar());
        }
    }

    private void runScript(Context ctx, ScriptContainer script) {
        try {
            synchronized (ctx) {
                Path relativePath = paths.root().relativize(script.path);
                String requirePath = "./" + relativePath.toString().replace("\\", "/");

                JavaClassLoadTelemetry.enter(script.type, script.id.toString());
                ScriptContextSeam.switchCurrentScriptId(ctx, script.id.toString());
                try {
                    waitForEntryModule(ctx, script, requirePath);
                } finally {
                    ScriptContextSeam.switchCurrentScriptId(ctx, null);
                    JavaClassLoadTelemetry.exit();
                }

                errorTracker.clear(script.id);
                errorTracker.clearByScriptPath(script.type, relativePath.toString().replace("\\", "/"));
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

    private void waitForEntryModule(Context ctx, ScriptContainer script, String requirePath) throws Exception {
        if (nodeRuntime == null || nodeRuntime.moduleLoaderHost() == null) {
            ctx.eval("js", "globalThis.__nekoScriptLoader.loadEntry").execute(requirePath);
            return;
        }
        CompletableFuture<?> evaluation = nodeRuntime.moduleLoaderHost().loadEntryAsync(requirePath);
        waitForEvaluation(evaluation);
    }

    private void waitForEvaluation(CompletableFuture<?> evaluation) throws Exception {
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

    // ---- 重载 ----

    public void reloadScripts() {
        scriptType.logger().info("正在重载 {} 脚本...", scriptType.name());
        resetEnvironment();
        discoverScripts();
        loadScripts();
        scriptType.logger().info("{} 脚本重载完毕。", scriptType.name());
    }

    public List<ScriptContainer> reloadScriptFile(String filePath) throws IOException {
        discoverScripts();
        Path target = resolveScriptPath(filePath);
        List<ScriptContainer> targets = reloadTargets(target);
        if (targets.isEmpty()) {
            throw new IOException("No loaded entry depends on " + displayScriptPath(target) + ". Reload the whole " + scriptType.name() + " environment first if this dependency has not been loaded yet.");
        }
        scriptType.logger().info("正在重载 {} 脚本文件 {}，受影响入口 {} 个...", scriptType.name(), displayScriptPath(target), targets.size());

        NekoModulePreparationCache.invalidate(target);
        Context ctx = getOrCreateContext();
        String modulePath = "./" + paths.root().relativize(target).toString().replace('\\', '/');

        boolean directEntry = scripts.stream()
                .anyMatch(s -> s.path.normalize().toAbsolutePath().equals(target));
        if (!directEntry) {
            if (nodeRuntime != null && nodeRuntime.moduleLoaderHost() != null) {
                try {
                    NekoScriptModuleLoaderHost.HotReloadResult result = nodeRuntime.moduleLoaderHost().hotReloadModule(modulePath);
                    if (result.success()) {
                        scriptType.logger().info("{} hot-reloaded module {} ({} relinked, {} failed)",
                                scriptType.name(), displayScriptPath(target),
                                result.relinked().size(), result.failed().size());
                        return List.of();
                    }
                    scriptType.logger().warn("Hot-reload rolled back for {}, falling back to entry re-run. Failed: {}",
                            modulePath, result.failed());
                } catch (Exception e) {
                    scriptType.logger().warn("Hot-reload failed for {}, falling back to entry re-run: {}",
                            modulePath, e.getMessage());
                }
            }
        }

        synchronized (ctx) {
            for (ScriptContainer script : targets) {
                String entryPath = "./" + paths.root().relativize(script.path).toString().replace('\\', '/');
                ctx.eval("js", "globalThis.__nekoScriptLoader.invalidateModuleTree").execute(entryPath);
            }
            ctx.eval("js", "globalThis.__nekoScriptLoader.invalidateAffectedModules").execute(modulePath);
        }

        for (ScriptContainer script : targets) {
            reloadEntryScript(ctx, script);
        }

        scriptType.logger().info("{} 脚本文件 {} 重载完毕。", scriptType.name(), displayScriptPath(target));
        return targets;
    }

    public Optional<ScriptContainer> resolveScriptFile(String filePath) throws IOException {
        discoverScripts();
        Path target = resolveScriptPath(filePath);
        return scripts.stream()
                .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                .findFirst();
    }

    private List<ScriptContainer> reloadTargets(Path target) {
        Optional<ScriptContainer> directEntry = scripts.stream()
                .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                .findFirst();
        if (directEntry.isPresent()) {
            return List.of(directEntry.get());
        }
        Context ctx = getOrCreateContext();
        String modulePath = "./" + paths.root().relativize(target).toString().replace('\\', '/');
        List<String> affectedIds = new ArrayList<>();
        synchronized (ctx) {
            Value affected = ctx.eval("js", "globalThis.__nekoScriptLoader.affectedEntries").execute(modulePath);
            if (affected.hasArrayElements()) {
                for (long i = 0; i < affected.getArraySize(); i++) {
                    affectedIds.add(affected.getArrayElement(i).asString());
                }
            }
        }
        return scripts.stream()
                .filter(script -> affectedIds.contains(paths.root().relativize(script.path).toString().replace('\\', '/')))
                .toList();
    }

    private void reloadEntryScript(Context ctx, ScriptContainer script) {
        scriptEventBridge.clearListeners(scriptType, script.id.toString());
        if (nodeRuntime != null) {
            nodeRuntime.timers().cancelScript(script.id.toString());
        }
        errorTracker.clear(script.id);
        errorTracker.clearByScriptPath(scriptType, paths.root().relativize(script.path).toString().replace('\\', '/'));
        script.preload();
        if (script.shouldRun()) {
            runScript(ctx, script);
        }
    }

    // ---- 路径解析 ----

    private Path resolveScriptPath(String filePath) throws IOException {
        if (scriptType.path == null) {
            throw new IOException("Script type has no script directory: " + scriptType.name());
        }
        if (filePath == null || filePath.isBlank()) {
            throw new IOException("Script file path is empty.");
        }
        String normalizedText = filePath.replace('\\', '/');
        String rootPrefix = scriptType.name + "/";
        if (normalizedText.startsWith(rootPrefix)) {
            normalizedText = normalizedText.substring(rootPrefix.length());
        }
        Path relative = Path.of(normalizedText).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IOException("Invalid script file path: " + filePath);
        }
        Path target = scriptType.path.resolve(relative).normalize().toAbsolutePath();
        Path root = scriptType.path.normalize().toAbsolutePath();
        if (!target.startsWith(root)) {
            throw new IOException("Script file is outside " + scriptType.name() + " scripts: " + filePath);
        }
        if (!Files.isRegularFile(target) || !ScriptFilePolicy.legacyRuntime().isSupportedScriptFile(target)) {
            throw new IOException("Unsupported or missing script file: " + filePath);
        }
        return target;
    }

    private String displayScriptPath(Path path) {
        return scriptType.name + "/" + scriptType.path.relativize(path).toString().replace('\\', '/');
    }

    // ---- 测试脚本 ----

    public void runTestScripts() {
        if (scriptType != ScriptType.TEST) {
            throw new IllegalStateException("runTestScripts() can only be called on TEST ScriptManager");
        }
        scriptType.logger().info("正在运行 TEST 脚本...");

        resetEnvironment();
        discoverScripts();
        loadScripts();
        flushTestTimers();

        scriptType.logger().info("TEST 脚本运行完毕。");
    }

    private void flushTestTimers() {
        if (nodeRuntime == null || context == null) return;
        for (int i = 0; i < 20 && nodeRuntime.hasPendingTimers(); i++) {
            synchronized (context) {
                nodeRuntime.flushReadyTimers();
            }
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        synchronized (context) {
            nodeRuntime.flushReadyTimers();
        }
    }

    // ---- 环境重置 ----

    private void resetEnvironment() {
        scriptEventBridge.clearListeners(scriptType);
        errorTracker.clearByType(scriptType);
        for (var binding : NekoPluginRuntime.current().bindings(scriptType).values()) {
            binding.close(scriptType);
        }

        Context oldContext = this.context;
        NekoNodeRuntime oldRuntime = this.nodeRuntime;
        this.context = null;
        this.nodeRuntime = null;

        if (oldRuntime != null) {
            try {
                oldRuntime.close();
            } catch (Exception e) {
                scriptType.logger().warn("关闭旧 Node runtime 时发生异常", e);
            }
        }
        if (oldContext != null) {
            ScriptContextSeam.unbind(oldContext);
            CONTEXT_TO_MANAGER.remove(oldContext);
            try {
                oldContext.close();
            } catch (Exception e) {
                scriptType.logger().warn("关闭旧上下文时发生异常", e);
            }
        }
    }

    // ---- 查询 ----

    public boolean hasScripts() {
        return scripts != null && !scripts.isEmpty();
    }

    private ScriptEventRegistrar getScriptEventRegistrar() {
        return scriptEventBridge.scriptEventRegistrar();
    }

    public void flushReadyNodeTimers() {
        if (nodeRuntime != null && context != null) {
            synchronized (context) {
                nodeRuntime.flushReadyTimers();
            }
        }
    }

    // ---- Context 身份管理（委托 ScriptContextSeam，Phase 4B 后由 ScriptEnvironmentFactory 持有 registry） ----

    /**
     * 从上下文获取对应的脚本类型
     */
    public static ScriptType getTypeFromContext(Context context) {
        return ScriptContextSeam.scriptTypeOf(context);
    }

    public static String switchCurrentScriptId(Context context, String scriptId) {
        return ScriptContextSeam.switchCurrentScriptId(context, scriptId);
    }

    public static void restoreCurrentScriptId(Context context, String scriptId) {
        ScriptContextSeam.restoreCurrentScriptId(context, scriptId);
    }

    public static String getCurrentScriptId(Context context) {
        return ScriptContextSeam.currentScriptIdOf(context);
    }
}
