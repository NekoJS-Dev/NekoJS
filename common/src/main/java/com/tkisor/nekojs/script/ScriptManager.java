package com.tkisor.nekojs.script;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.catalog.JavaClassLoadTelemetrySink;
import com.tkisor.nekojs.api.event.ScriptEventRegistrar;
import com.tkisor.nekojs.api.event.ScriptEvents;
import com.tkisor.nekojs.core.JavaClassLoadTelemetry;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.ScriptLocator;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.ResourceTracker;
import com.tkisor.nekojs.core.module.cache.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoScriptModuleLoaderHost;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.script.context.ScriptContextRegistry;
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

import com.tkisor.nekojs.api.annotation.CalledByDynamicCode;

/**
 * NekoJS 脚本引擎核心生命周期调度器。
 * <p>
 * 每个实例管理一种 {@link ScriptType}（STARTUP / SERVER / CLIENT / TEST）的完整脚本生命周期。
 * 通过构造器注入 {@link ScriptEventBridge}、{@link ErrorTracker}、{@link NekoJSPaths} 等协作者。
 */
public final class ScriptManager implements AutoCloseable {

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

    private final ScriptEventBridge scriptEventBridge;
    private final ScriptPropertyRegistry scriptProperties;
    private final ErrorTracker errorTracker;
    private final NekoJSPaths paths;
    private final SandboxConfig sandboxConfig;
    private final ScriptExecutor scriptExecutor;
    private final ScriptEnvironmentFactory environmentFactory;

    /**
     * 本实例管理的脚本类型
     */
    public final ScriptType scriptType;

    private Context context;
    private NekoNodeRuntime nodeRuntime;
    private List<ScriptContainer> scripts;

    // ---- 构造函数 ----

    public ScriptManager(ScriptType scriptType, ScriptEventBridge scriptEventBridge, ScriptPropertyRegistry scriptProperties, ErrorTracker errorTracker, NekoJSPaths paths, SandboxConfig sandboxConfig, ScriptEnvironmentFactory environmentFactory) {
        this.scriptType = scriptType;
        this.scriptEventBridge = scriptEventBridge;
        this.scriptProperties = scriptProperties;
        this.errorTracker = errorTracker;
        this.paths = paths;
        this.sandboxConfig = sandboxConfig;
        this.scriptExecutor = new ScriptExecutor(scriptType, errorTracker, paths, sandboxConfig);
        this.environmentFactory = environmentFactory;
    }

    // ---- 配置 ----

    public void setJavaClassLoadTelemetrySink(JavaClassLoadTelemetrySink sink) {
        JavaClassLoadTelemetry.setSink(sink);
    }

    // ---- Context 访问（懒初始化） ----

    private Context getOrCreateContext() {
        if (context == null) {
            ScriptEnvironmentFactory.Environment env = environmentFactory.create(scriptType);
            this.context = env.context();
            this.nodeRuntime = env.nodeRuntime();
            CONTEXT_TO_MANAGER.put(context, this);
            ScriptContextRegistry.bind(context, scriptType);
        }
        return context;
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
                scriptExecutor.executeEntry(ctx, script, nodeRuntime);
            }
        }

        if (scriptType == ScriptType.STARTUP) {
            flushReadyNodeTimers();
            ScriptEvents.post(getScriptEventRegistrar());
        }
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

        NekoModulePipelineCache.invalidate(target);
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
        cleanupScriptEntry(script);
        script.preload();
        if (script.shouldRun()) {
            scriptExecutor.executeEntry(ctx, script, nodeRuntime);
        }
    }

    private void cleanupScriptEntry(ScriptContainer script) {
        scriptEventBridge.clearListeners(scriptType, script.id.toString());
        if (nodeRuntime != null) {
            nodeRuntime.timers().cancelScript(script.id.toString());
        }
        errorTracker.clear(script.id);
        errorTracker.clearByScriptPath(scriptType, paths.root().relativize(script.path).toString().replace('\\', '/'));
    }

    private void fullReloadCleanup() {
        scriptEventBridge.clearListeners(scriptType);
        errorTracker.clearByType(scriptType);
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

    // ---- 环境重置 / 关闭 ----

    private void resetEnvironment() {
        fullReloadCleanup();
        for (var binding : NekoRuntimeAccess.get().bindings(scriptType).values()) {
            binding.close(scriptType);
        }

        Context oldContext = this.context;
        NekoNodeRuntime oldRuntime = this.nodeRuntime;
        this.context = null;
        this.nodeRuntime = null;

        closeRuntimeResources(oldRuntime, oldContext);
    }

    private void closeRuntimeResources(NekoNodeRuntime oldRuntime, Context oldContext) {
        if (oldRuntime != null) {
            try {
                oldRuntime.close();
            } catch (Exception e) {
                scriptType.logger().warn("关闭旧 Node runtime 时发生异常", e);
            }
        }
        if (oldContext != null) {
            ScriptContextRegistry.unbind(oldContext);
            CONTEXT_TO_MANAGER.remove(oldContext);
            try {
                oldContext.close();
            } catch (Exception e) {
                scriptType.logger().warn("关闭旧上下文时发生异常", e);
            }
        }
    }

    @Override
    public void close() {
        fullReloadCleanup();
        for (var binding : NekoRuntimeAccess.get().bindings(scriptType).values()) {
            binding.close(scriptType);
        }
        closeRuntimeResources(this.nodeRuntime, this.context);
        this.context = null;
        this.nodeRuntime = null;
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

    // ---- Context 身份管理（委托 ScriptContextRegistry） ----

    /**
     * 从上下文获取对应的脚本类型
     */
    public static ScriptType getTypeFromContext(Context context) {
        return ScriptContextRegistry.scriptTypeOf(context);
    }

    public static String switchCurrentScriptId(Context context, String scriptId) {
        return ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
    }

    public static void restoreCurrentScriptId(Context context, String scriptId) {
        ScriptContextRegistry.restoreCurrentScriptId(context, scriptId);
    }

    public static String getCurrentScriptId(Context context) {
        return ScriptContextRegistry.currentScriptIdOf(context);
    }
}
