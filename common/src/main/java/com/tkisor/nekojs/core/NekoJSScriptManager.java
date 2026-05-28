package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.catalog.JavaClassLoadTelemetrySink;
import com.tkisor.nekojs.api.event.ScriptEventRegistrar;
import com.tkisor.nekojs.api.event.ScriptEvents;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePreparationCache;
import com.tkisor.nekojs.core.module.NekoScriptModuleLoaderHost;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.ScriptTypedValue;
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
 * Central script lifecycle manager. One per mod instance, shared via {@code NekoJS.SCRIPT_MANAGER}.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #discoverScripts()} — scan {@code startup_scripts/}, {@code server_scripts/},
 *        {@code client_scripts/}, {@code test_scripts/} directories for supported script files</li>
 *   <li>{@link #loadScripts(ScriptType)} — preload properties, sort by priority, execute scripts
 *        sequentially in a GraalVM {@link Context} synchronised on the context</li>
 *   <li>{@link #reloadScripts(ScriptType)} — close old Context+Runtime, re-discover, re-load</li>
 *   <li>{@link #reloadScriptFile(ScriptType, String)} — single-file hot-reload (tries dependency-slice
 *        re-link via {@link NekoScriptModuleLoaderHost#hotReloadModule} before falling back to entry re-run)</li>
 *   <li>{@link #runTestScripts()} — special lifecycle for test_scripts (no auto-load, triggered by
 *        {@code /nekojs test} command)</li>
 * </ol>
 *
 * <h2>Callers</h2>
 * <table>
 *   <tr><td>{@code NekoJS / NekoJSClient}</td><td>mod init → discoverScripts + loadScripts</td></tr>
 *   <tr><td>{@code ServerEventListener / NekoJSClient}</td><td>server resource reload → reloadScripts</td></tr>
 *   <tr><td>{@code NekoJSCommands}</td><td>{@code /nekojs reload} → reloadScripts / reloadScriptFile / runTestScripts</td></tr>
 *   <tr><td>{@code NekoNodeTimers}</td><td>tick flush → {@link #flushReadyNodeTimers}</td></tr>
 * </table>
 *
 * <h2>Key invariants</h2>
 * <ul>
 *   <li>Each {@link ScriptType} has at most one active {@link Context} and one {@link NekoNodeRuntime}</li>
 *   <li>{@code CONTEXT_TYPE_MAP} / {@code CONTEXT_SCRIPT_ID_MAP} use {@link WeakHashMap} so
 *        entries are auto-cleaned when contexts are GC'd</li>
 *   <li>TIMER callbacks record their owning script via {@link #switchCurrentScriptId} /
 *        {@link #restoreCurrentScriptId}, enabling per-script timer cancellation on reload</li>
 * </ul>
 */
public final class NekoJSScriptManager {
    private static ScriptEventBridge eventBridge = ScriptEventBridge.EMPTY;

    private final ScriptTypedValue<Context> contexts = ScriptTypedValue.ofNullable(this::initContext);
    private final ScriptTypedValue<NekoNodeRuntime> nodeRuntimes = ScriptTypedValue.ofNullable(type -> null);
    private final ScriptTypedValue<List<ScriptContainer>> scripts = ScriptTypedValue.of(type -> new ArrayList<>());
    private final ScriptPropertyRegistry scriptPropertyRegistry;

    /** Weak maps for Context → metadata: entries auto-evict when context is GC'd. */
    private static final Map<Context, ScriptType> CONTEXT_TYPE_MAP = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Context, String> CONTEXT_SCRIPT_ID_MAP = Collections.synchronizedMap(new WeakHashMap<>());

    public NekoJSScriptManager() {
        this(new ScriptPropertyRegistry.Impl());
    }

    public NekoJSScriptManager(ScriptPropertyRegistry scriptPropertyRegistry) {
        this.scriptPropertyRegistry = scriptPropertyRegistry == null ? new ScriptPropertyRegistry.Impl() : scriptPropertyRegistry;
    }

    public static void setEventBridge(ScriptEventBridge bridge) {
        eventBridge = bridge == null ? ScriptEventBridge.EMPTY : bridge;
    }

    public static void setJavaClassLoadTelemetrySink(JavaClassLoadTelemetrySink sink) {
        JavaClassLoadTelemetry.setSink(sink);
    }

    /** Scan all auto-load script directories (startup, server, client) and discover script files. */
    public void discoverScripts() {
        for (ScriptType type : ScriptType.autoLoadTypes()) {
            discoverScripts(type);
        }
    }

    /** Discover scripts for one type. Does not execute them. */
    public void discoverScripts(ScriptType type) {
        List<ScriptContainer> discovered = ScriptLocator.discover(type, scriptPropertyRegistry);
        scripts.set(type, discovered);
        type.logger().info("Discovered {} {} scripts.", discovered.size(), type.name());
    }

    /** Preload properties, sort by priority, execute all scripts for the given type. */
    public void loadScripts(ScriptType type) {
        List<ScriptContainer> scripts = this.scripts.at(type);

        Context ctx = contexts.at(type);

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

        if (type == ScriptType.STARTUP) {
            flushReadyNodeTimers(type);
            ScriptEvents.post(getScriptEventRegistrar());
        }
    }

    private Context initContext(ScriptType type) {
        NekoSandboxBuilder.Sandbox sandbox = NekoSandboxBuilder.buildSandbox(type);
        Context ctx = sandbox.context();
        nodeRuntimes.set(type, sandbox.nodeRuntime());
        CONTEXT_TYPE_MAP.put(ctx, type);

        var bindings = ctx.getBindings("js");

        eventBridge.bindEvents(bindings, type);

        var environmentBindings = NekoPluginRuntime.current().bindings(type);

        environmentBindings.forEach((name, binding) -> {
            Object obj = binding.value();

            if (obj instanceof Class<?>) {
                // Static classes: wrap via Java.type for JS exposure
                Value javaType = bindings.getMember("Java").invokeMember("type", ((Class<?>) obj).getName());
                bindings.putMember(name, javaType);
            } else {
                // Instance objects: inject directly
                bindings.putMember(name, obj);
            }
        });

        installJavaClassLoadTelemetry(ctx, type);

        return ctx;
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

    private void runScript(Context ctx, ScriptContainer script) {
        try {
            synchronized (ctx) {
                Path relativePath = NekoJSPaths.ROOT.relativize(script.path);
                String requirePath = "./" + relativePath.toString().replace("\\", "/");

                JavaClassLoadTelemetry.enter(script.type, script.id.toString());
                setCurrentScriptId(ctx, script.id.toString());
                try {
                    waitForEntryModule(ctx, script, requirePath);
                } finally {
                    setCurrentScriptId(ctx, null);
                    JavaClassLoadTelemetry.exit();
                }

                NekoErrorTracker.clear(script.id);
                NekoErrorTracker.clearByScriptPath(script.type, relativePath.toString().replace("\\", "/"));
                script.disabled = false;
                script.lastError = null;
            }
        } catch (Throwable t) {
            script.disabled = true;
            script.lastError = t;

            ScriptError scriptError = NekoErrorTracker.record(script, t);
            script.type.logger().error("Script execution failed: {}\n{}", script.id.toString(), scriptError.getLogDetailText(ClassFilter.conciseScriptErrorLogs));
        }
    }

    private void waitForEntryModule(Context ctx, ScriptContainer script, String requirePath) throws Exception {
        NekoNodeRuntime runtime = nodeRuntimes.at(script.type);
        if (runtime == null || runtime.moduleLoaderHost() == null) {
            ctx.eval("js", "globalThis.__nekoScriptLoader.loadEntry").execute(requirePath);
            return;
        }
        CompletableFuture<?> evaluation = runtime.moduleLoaderHost().loadEntryAsync(requirePath);
        waitForEvaluation(evaluation, runtime);
    }

    private void waitForEvaluation(CompletableFuture<?> evaluation, NekoNodeRuntime runtime) throws Exception {
        while (!evaluation.isDone()) {
            runtime.flushReadyTimers();
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

    /** Full reload: close old Context+Runtime, re-discover, re-load. */
    public void reloadScripts(ScriptType type) {
        type.logger().info("Reloading {} scripts...", type.name());

        resetEnvironment(type);

        discoverScripts(type);
        loadScripts(type);

        type.logger().info("{} scripts reloaded.", type.name());
    }

    public List<ScriptContainer> reloadScriptFile(ScriptType type, String filePath) throws IOException {
        discoverScripts(type);
        Path target = resolveScriptPath(type, filePath);
        List<ScriptContainer> targets = reloadTargets(type, target);
        if (targets.isEmpty()) {
            throw new IOException("No loaded entry depends on " + displayScriptPath(type, target) + ". Reload the whole " + type.name() + " environment first if this dependency has not been loaded yet.");
        }
        type.logger().info("Reloading {} script file {}, {} affected entries...", type.name(), displayScriptPath(type, target), targets.size());

        NekoModulePreparationCache.invalidate(target);
        Context ctx = contexts.at(type);
        String modulePath = "./" + NekoJSPaths.ROOT.relativize(target).toString().replace('\\', '/');

        // Try hot-reload for dependency changes (not direct entry edits)
        boolean directEntry = scripts.at(type).stream()
                .anyMatch(script -> script.path.normalize().toAbsolutePath().equals(target));
        if (!directEntry) {
            NekoNodeRuntime runtime = nodeRuntimes.at(type);
            if (runtime != null && runtime.moduleLoaderHost() != null) {
                try {
                    NekoScriptModuleLoaderHost.HotReloadResult result = runtime.moduleLoaderHost().hotReloadModule(modulePath);
                    if (result.success()) {
                        type.logger().info("{} hot-reloaded module {} ({} relinked, {} failed)",
                                type.name(), displayScriptPath(type, target),
                                result.relinked().size(), result.failed().size());
                        return List.of();
                    }
                    type.logger().warn("Hot-reload rolled back for {}, falling back to entry re-run. Failed: {}",
                            modulePath, result.failed());
                } catch (Exception e) {
                    type.logger().warn("Hot-reload failed for {}, falling back to entry re-run: {}",
                            modulePath, e.getMessage());
                }
            }
        }

        synchronized (ctx) {
            for (ScriptContainer script : targets) {
                String entryPath = "./" + NekoJSPaths.ROOT.relativize(script.path).toString().replace('\\', '/');
                ctx.eval("js", "globalThis.__nekoScriptLoader.invalidateModuleTree").execute(entryPath);
            }
            ctx.eval("js", "globalThis.__nekoScriptLoader.invalidateAffectedModules").execute(modulePath);
        }

        for (ScriptContainer script : targets) {
            reloadEntryScript(type, ctx, script);
        }

        type.logger().info("{} script file {} reload complete.", type.name(), displayScriptPath(type, target));
        return targets;
    }

    public Optional<ScriptContainer> resolveScriptFile(ScriptType type, String filePath) throws IOException {
        discoverScripts(type);
        Path target = resolveScriptPath(type, filePath);
        return scripts.at(type).stream()
                .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                .findFirst();
    }

    private List<ScriptContainer> reloadTargets(ScriptType type, Path target) {
        Optional<ScriptContainer> directEntry = scripts.at(type).stream()
                .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                .findFirst();
        if (directEntry.isPresent()) {
            return List.of(directEntry.get());
        }
        Context ctx = contexts.at(type);
        String modulePath = "./" + NekoJSPaths.ROOT.relativize(target).toString().replace('\\', '/');
        List<String> affectedIds = new ArrayList<>();
        synchronized (ctx) {
            Value affected = ctx.eval("js", "globalThis.__nekoScriptLoader.affectedEntries").execute(modulePath);
            if (affected.hasArrayElements()) {
                for (long i = 0; i < affected.getArraySize(); i++) {
                    affectedIds.add(affected.getArrayElement(i).asString());
                }
            }
        }
        return scripts.at(type).stream()
                .filter(script -> affectedIds.contains(NekoJSPaths.ROOT.relativize(script.path).toString().replace('\\', '/')))
                .toList();
    }

    private void reloadEntryScript(ScriptType type, Context ctx, ScriptContainer script) {
        eventBridge.clearListeners(type, script.id.toString());
        NekoNodeRuntime runtime = nodeRuntimes.at(type);
        if (runtime != null) {
            runtime.timers().cancelScript(script.id.toString());
        }
        NekoErrorTracker.clear(script.id);
        NekoErrorTracker.clearByScriptPath(type, NekoJSPaths.ROOT.relativize(script.path).toString().replace('\\', '/'));
        script.preload();
        if (script.shouldRun()) {
            runScript(ctx, script);
        }
    }

    private Path resolveScriptPath(ScriptType type, String filePath) throws IOException {
        if (type.path == null) {
            throw new IOException("Script type has no script directory: " + type.name());
        }
        if (filePath == null || filePath.isBlank()) {
            throw new IOException("Script file path is empty.");
        }
        String normalizedText = filePath.replace('\\', '/');
        String rootPrefix = type.name + "/";
        if (normalizedText.startsWith(rootPrefix)) {
            normalizedText = normalizedText.substring(rootPrefix.length());
        }
        Path relative = Path.of(normalizedText).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IOException("Invalid script file path: " + filePath);
        }
        Path target = type.path.resolve(relative).normalize().toAbsolutePath();
        Path root = type.path.normalize().toAbsolutePath();
        if (!target.startsWith(root)) {
            throw new IOException("Script file is outside " + type.name() + " scripts: " + filePath);
        }
        if (!Files.isRegularFile(target) || !NekoJSPaths.isSupportedScriptFile(target)) {
            throw new IOException("Unsupported or missing script file: " + filePath);
        }
        return target;
    }

    private String displayScriptPath(ScriptType type, Path path) {
        return type.name + "/" + type.path.relativize(path).toString().replace('\\', '/');
    }

    public void runTestScripts() {
        ScriptType type = ScriptType.TEST;
        type.logger().info("Running TEST scripts...");

        resetEnvironment(type);
        discoverScripts(type);
        loadScripts(type);
        flushTestTimers();

        type.logger().info("TEST scripts completed.");
    }

    private void flushTestTimers() {
        NekoNodeRuntime runtime = nodeRuntimes.at(ScriptType.TEST);
        Context context = contexts.at(ScriptType.TEST);
        if (runtime == null || context == null) return;
        for (int i = 0; i < 20 && runtime.hasPendingTimers(); i++) {
            synchronized (context) {
                runtime.flushReadyTimers();
            }
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        synchronized (context) {
            runtime.flushReadyTimers();
        }
    }

    private void resetEnvironment(ScriptType type) {
        // TODO: make EventGroupJS a binding, use `binding.close(type)` instead of the bridge
        eventBridge.clearListeners(type);
        NekoErrorTracker.clearByType(type);
        for (var binding : NekoPluginRuntime.current().bindings(type).values()) {
            binding.close(type);
        }

        Context oldContext = contexts.set(type, null);
        NekoNodeRuntime oldRuntime = nodeRuntimes.set(type, null);
        if (oldRuntime != null) {
            try {
                oldRuntime.close();
            } catch (Exception e) {
                type.logger().warn("关闭旧 Node runtime 时发生异常", e);
            }
        }
        if (oldContext != null) {
            CONTEXT_TYPE_MAP.remove(oldContext);
            CONTEXT_SCRIPT_ID_MAP.remove(oldContext);
            try {
                oldContext.close();
            } catch (Exception e) {
                type.logger().warn("关闭旧上下文时发生异常", e);
            }
        }
    }

    public boolean hasScripts(ScriptType type) {
        List<ScriptContainer> typeScripts = scripts.at(type);
        return typeScripts != null && !typeScripts.isEmpty();
    }

    private ScriptEventRegistrar getScriptEventRegistrar() {
        return eventBridge.scriptEventRegistrar();
    }

    public void flushReadyNodeTimers(ScriptType type) {
        NekoNodeRuntime runtime = nodeRuntimes.at(type);
        Context context = contexts.at(type);
        if (runtime != null && context != null) {
            synchronized (context) {
                runtime.flushReadyTimers();
            }
        }
    }

        // ---- Context → ScriptType / ScriptId mapping ----
    // Called by NekoNodeTimers.execute() before/after timer callbacks,
    // ensuring __nekoCurrentScriptId is set during callback execution.

    @CalledByDynamicCode
    public static ScriptType getTypeFromContext(Context context) {
        return CONTEXT_TYPE_MAP.get(context);
    }

    @CalledByDynamicCode
    public static String switchCurrentScriptId(Context context, String scriptId) {
        String previous = CONTEXT_SCRIPT_ID_MAP.get(context);
        setCurrentScriptId(context, scriptId);
        return previous;
    }

    @CalledByDynamicCode
    public static void restoreCurrentScriptId(Context context, String scriptId) {
        setCurrentScriptId(context, scriptId);
    }

    @CalledByDynamicCode
    public static String getCurrentScriptId(Context context) {
        return CONTEXT_SCRIPT_ID_MAP.get(context);
    }

    private static void setCurrentScriptId(Context context, String scriptId) {
        if (context == null) return;
        if (scriptId == null || scriptId.isBlank()) {
            CONTEXT_SCRIPT_ID_MAP.remove(context);
            context.getBindings("js").putMember("__nekoCurrentScriptId", null);
        } else {
            CONTEXT_SCRIPT_ID_MAP.put(context, scriptId);
            context.getBindings("js").putMember("__nekoCurrentScriptId", scriptId);
        }
    }
}
