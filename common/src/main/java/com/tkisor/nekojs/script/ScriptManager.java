package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.JavaClassLoadTelemetrySink;
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
import com.tkisor.nekojs.core.log.LoggerStream;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.tkisor.nekojs.api.annotation.CalledByDynamicCode;

/**
 * NekoJS 脚本引擎核心生命周期调度器。
 * <p>
 * 每个实例管理一种 {@link ScriptType}（STARTUP / SERVER / CLIENT / TEST）的完整脚本生命周期。
 * 通过构造器注入 {@link ScriptEventBridge}、{@link ErrorTracker}、{@link NekoJSPaths} 等协作者。
 */
public final class ScriptManager implements AutoCloseable {

    // ---- 最小静态：Context → ScriptManager 反向查找 ----
    // 用 ConcurrentHashMap 而非 synchronizedMap(WeakHashMap)：isContextDead / reportContextKilled
    // 位于每次事件与 timer 回调的热路径，synchronizedMap 会把所有回调在 map 锁上串行化。
    // 键改为强引用，因此不变式：Context 的销毁必须全部经 closeRuntimeResources（其内保证
    // remove）。当前全部销毁路径（kill 重建、事务 reload 成功/失败、resetEnvironment、close）
    // 均满足；若新增绕过 closeRuntimeResources 的销毁路径，将同时泄漏 Context 与 ScriptManager。
    private static final Map<Context, ScriptManager> CONTEXT_TO_MANAGER = new ConcurrentHashMap<>();

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

    /**
     * JS 回调（事件监听器 / timer）catch 路径共享的 kill 上报：异常链表明 Graal 已因
     * 资源上限（语句上限）关闭该 Context 时，标记所属 ScriptManager 于下次取用时重建环境。
     *
     * <p>与 {@link ScriptExecutor#executeEntry} 的入口路径互补：稳态下只有回调在运行，
     * 不经此上报则 kill 永远不会被标记，每次 tick 的事件 / timer 都会在已关闭的
     * Context 上反复抛错，整个脚本环境静默死亡直到手动 reload。未注册的 Context
     * （测试等场景）安全忽略。
     */
    public static void reportContextKilled(Context context, Throwable t) {
        if (context == null || !ScriptExecutor.isContextKilledByResourceLimits(t)) return;
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        if (manager != null) {
            manager.markContextKilled();
        }
    }

    /**
     * JS 回调（事件监听器 / timer）分发短路判定：闭包捕获的 Context 是否已死。
     *
     * <p>本仓库使用的 relocated Graal polyglot API 没有 {@code Context.isOpen()}，
     * 这里以 ScriptManager 侧的状态等价判定：要么环境被语句上限 kill
     * （{@code contextKilled}，见 {@link #reportContextKilled}），要么所属
     * ScriptManager 已切换到新 Context（本闭包指向已被替换的旧环境）。
     * 未注册的 Context（测试等场景）返回 false，走原有 try/catch 路径。
     */
    public static boolean isContextDead(Context context) {
        if (context == null) return false;
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        if (manager == null) return false;
        // Graal 的 Value.getContext() 可能返回与 Context.Builder.build() 引用不同但
        // equals/hashCode 相同的包装对象；必须用 equals 比较，否则正常回调也会被误判为 dead。
        return manager.contextKilled || (manager.context != null && !manager.context.equals(context));
    }

    // ---- 实例字段 ----

    private final ScriptEventBridge scriptEventBridge;
    private final IPluginRuntime pluginRuntime;
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

    /** volatile：context 可能被回调线程（kill 上报后的重建）与加载线程并发读写。 */
    private volatile Context context;
    private NekoNodeRuntime nodeRuntime;
    /**
     * 当前 context 的 out/err 日志流。Graal 关闭 Context 时只 detach 用户流、不 flush/close，
     * 末行未换行输出会滞留在行缓冲中丢失；closeRuntimeResources 在 context.close() 之后
     * 对它们补一次 close()（内部幂等地冲刷残留缓冲）。随 context 一起成对换新。
     */
    private LoggerStream contextOutStream;
    private LoggerStream contextErrStream;
    private List<ScriptContainer> scripts;

    /** 一次性标记：STARTUP reload 的非事务语义只警告一次（每个 ScriptType 一个实例）。 */
    private boolean warnedStartupReloadNonTransactional;

    /** Graal 因语句上限（scriptStatementLimit）关闭了当前 Context；下次取用时重建。 */
    private volatile boolean contextKilled;

    // ---- 构造函数 ----

    public ScriptManager(ScriptType scriptType, ScriptEventBridge scriptEventBridge, IPluginRuntime pluginRuntime, ScriptPropertyRegistry scriptProperties, ErrorTracker errorTracker, NekoJSPaths paths, SandboxConfig sandboxConfig, ScriptEnvironmentFactory environmentFactory) {
        this.scriptType = scriptType;
        this.scriptEventBridge = scriptEventBridge;
        this.pluginRuntime = pluginRuntime;
        this.scriptProperties = scriptProperties;
        this.errorTracker = errorTracker;
        this.paths = paths;
        this.sandboxConfig = sandboxConfig;
        this.scriptExecutor = new ScriptExecutor(errorTracker, paths, sandboxConfig, this::markContextKilled);
        this.environmentFactory = environmentFactory;
    }

    // ---- 配置 ----

    public void setJavaClassLoadTelemetrySink(JavaClassLoadTelemetrySink sink) {
        JavaClassLoadTelemetry.setSink(sink);
    }

    // ---- Context 访问（懒初始化） ----

    /** ScriptExecutor 回调：Graal 因语句上限关闭了当前 Context。 */
    private void markContextKilled() {
        this.contextKilled = true;
    }

    private synchronized Context getOrCreateContext() {
        if (context == null || contextKilled) {
            if (context != null) {
                // 旧 Context 已被 Graal 关闭（语句上限触发）；清理注册与残留资源。
                // 与事务式 reload / fullReloadCleanup 相同的顺序：关闭前先清本 scriptType 的
                // 全部事件监听器——监听器闭包持有指向已死 Context 的 Value，保留只会让每次
                // 事件分发都在死环境上报错。此处无法重跑脚本重建监听器（重建只创建空环境），
                // 对本 scriptType 整体清除是正确的最小修复，恢复需再次 reload。
                scriptEventBridge.clearListeners(scriptType);
                closeRuntimeResources(this.nodeRuntime, this.context, this.contextOutStream, this.contextErrStream);
            }
            ScriptEnvironmentFactory.Environment env = environmentFactory.create(scriptType);
            this.context = env.context();
            this.nodeRuntime = env.nodeRuntime();
            this.contextOutStream = env.outStream();
            this.contextErrStream = env.errStream();
            CONTEXT_TO_MANAGER.put(context, this);
            ScriptContextRegistry.bind(context, scriptType);
            contextKilled = false;
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
     * 加载并顺序执行所有脚本。
     *
     * <p>synchronized：与 {@link #reloadScripts()} / {@link #reloadScriptFile(String)} 同一把
     * 实例锁。首次加载（平台 init / client setup）与命令触发的 reload 可能从不同线程进入，
     * 并发执行会让两个线程各自在 {@link #getOrCreateContext()} 创建候选 Context，输家创建的
     * Context（及其 timer 调度线程）永远不会被关闭。锁可重入：reloadScripts 内部调用本方法
     * 不受影响。
     */
    public synchronized void loadScripts() {
        pluginRuntime.fireBeforeScriptsLoaded(scriptType);
        try {
            loadScriptsInto(scripts);
            if (scriptType == ScriptType.STARTUP) {
                flushReadyNodeTimers();
                ScriptEvents.post(getScriptEventRegistrar());
            }
        } finally {
            pluginRuntime.fireAfterScriptsLoaded(scriptType);
        }
    }

    /**
     * 在指定 Context / Node runtime 中执行给定脚本列表：preload、按 priority 与 after 依赖排序、逐个执行入口。
     *
     * <p>被首次 {@link #loadScripts()} 和事务式 reload 复用。空列表只记录日志，不创建副作用。
     */
    private void loadScriptsInto(List<ScriptContainer> scriptsToLoad) {
        if (!prepareScriptsForLoad(scriptsToLoad)) {
            return;
        }
        for (ScriptContainer script : scriptsToLoad) {
            if (script.shouldRun()) {
                // 逐个脚本取最新 Context：某脚本触发语句上限导致 Context 被 Graal 关闭时，
                // 下一个脚本能在自动重建的环境中继续执行，而不是全军覆没
                scriptExecutor.executeEntry(getOrCreateContext(), script, this.nodeRuntime);
            }
        }
    }

    private void loadScriptsInto(List<ScriptContainer> scriptsToLoad, Context context, NekoNodeRuntime nodeRuntime) {
        if (!prepareScriptsForLoad(scriptsToLoad)) {
            return;
        }
        for (ScriptContainer script : scriptsToLoad) {
            if (script.shouldRun()) {
                scriptExecutor.executeEntry(context, script, nodeRuntime);
            }
        }
    }

    private boolean prepareScriptsForLoad(List<ScriptContainer> scriptsToLoad) {
        if (scriptsToLoad == null || scriptsToLoad.isEmpty()) {
            scriptType.logger().info("没有需要加载的 {} 脚本。", scriptType.name());
            return false;
        }
        for (var script : scriptsToLoad) {
            script.preload();
        }
        ScriptLoadOrderSorter.Result orderResult =
                ScriptLoadOrderSorter.applyAfterOrder(scriptsToLoad, ScriptContainer::shouldRun);
        if (orderResult.hasProblems()) {
            scriptType.logger().warn("{} 脚本 after 依赖排序存在问题：{}", scriptType.name(), orderResult.describe());
        }
        return true;
    }

        // ---- 重载 ----

        public synchronized void reloadScripts () {
            scriptType.logger().info("正在重载 {} 脚本...", scriptType.name());
            if (scriptType == ScriptType.STARTUP) {
                if (!warnedStartupReloadNonTransactional) {
                    warnedStartupReloadNonTransactional = true;
                    scriptType.logger().warn(
                            "{} 脚本重载为非事务式语义（STARTUP 涉及物品/方块/实体等不可逆注册，无法安全回滚）；"
                                    + "若重载期间脚本出错，已注册内容不会回退。",
                            scriptType.name());
                }
                // STARTUP 涉及不可逆注册（物品、方块、实体），无法安全回滚，保持 reset+load 语义。
                resetEnvironment();
                discoverScripts();
                loadScripts();
            } else {
                reloadScriptsTransactional();
            }
            scriptType.logger().info("{} 脚本重载完毕。", scriptType.name());
        }

        /**
         * 事务式完整 reload：先在候选 Context 中加载脚本，成功后再切换并关闭旧 Context。
         *
         * <p>失败时丢弃候选 Context，保留旧 Context，避免旧实现「先销毁旧环境再加载」导致的
         * 半失效状态。由于事件总线和 binding 是进程级共享资源，候选加载前会清空旧 listener /
         * 旧 binding 状态；因此失败时旧 Context 虽然存活，listener 与 binding 状态需要再次
         * reload 恢复——这是共享资源约束下的最佳折中，核心保证是「不崩溃、旧 Context 不被关闭」。
         */
        private void reloadScriptsTransactional () {
            Context oldContext = this.context;
            NekoNodeRuntime oldNodeRuntime = this.nodeRuntime;
            LoggerStream oldOutStream = this.contextOutStream;
            LoggerStream oldErrStream = this.contextErrStream;

            scriptEventBridge.clearListeners(scriptType);
            errorTracker.clearByType(scriptType);
            for (var binding : pluginRuntime.bindings(scriptType).values()) {
                binding.close(scriptType);
            }
            // 清空进程级静态缓存（编译模块、source map、虚拟 ESM URI）中本 scriptType 的条目，
            // 防止删除/改名后的脚本残留旧产物。按类型局部清除：这些缓存原本无 ScriptType 维度，
            // 单机 CLIENT 触发 reload 会误清 SERVER 等其它类型已编译的模块/source map/虚拟 URI。
            NekoModulePipelineCache.clear(scriptType);
            NekoEsmVirtualModuleRegistry.clear(scriptType);

            final ScriptEnvironmentFactory.Environment candidate;
            try {
                candidate = environmentFactory.create(scriptType);
            } catch (Throwable t) {
                scriptType.logger().error("{} 候选环境创建失败，保留旧 Context（listener/binding 已清，需再次 reload 恢复）", scriptType.name(), t);
                throw new RuntimeException(scriptType.name()
                        + " reload failed; previous scripts retained but event listeners/bindings were cleared"
                        + " — run /neko reload again to restore listeners", t);
            }

            Context candidateContext = candidate.context();
            NekoNodeRuntime candidateNode = candidate.nodeRuntime();
            LoggerStream candidateOutStream = candidate.outStream();
            LoggerStream candidateErrStream = candidate.errStream();
            CONTEXT_TO_MANAGER.put(candidateContext, this);
            ScriptContextRegistry.bind(candidateContext, scriptType);

            boolean previousKilled = this.contextKilled;
            // 提前发布候选字段：候选脚本可能注册 timer（setTimeout）并 await 其回调，而
            // isContextDead(candidateContext) 依赖 this.context 判定。必须在最终切换前就让
            // 候选成为当前 live Context，否则候选 timer 回调会被当作 dead 跳过。成功路径
            // 无需重复赋值；失败时在 catch 恢复捕获的旧值。
            this.context = candidateContext;
            this.nodeRuntime = candidateNode;
            this.contextOutStream = candidateOutStream;
            this.contextErrStream = candidateErrStream;

            try {
                List<ScriptContainer> candidateScripts = ScriptLocator.discover(scriptType, scriptProperties);
                scriptType.logger().info("发现了 {} 个 {} 脚本。", candidateScripts.size(), scriptType.name());

                // 候选加载期间只关心「候选 Context 是否被杀」：先清掉旧标记，加载结束后若标记
                // 重新变 true，说明是候选环境触发了语句上限。基于状态而非转移检测，避免
                // previousKilled == true 时漏判并提交一个已死候选。
                this.contextKilled = false;
                pluginRuntime.fireBeforeScriptsLoaded(scriptType);
                try {
                    loadScriptsInto(candidateScripts, candidateContext, candidateNode);
                } finally {
                    pluginRuntime.fireAfterScriptsLoaded(scriptType);
                }

                if (this.contextKilled) {
                    // 候选加载期间有脚本触发语句上限，Graal 已关闭候选 Context。把它按失败
                    // 处理：交给下方 catch 关闭候选并保留旧 Context，而不是切换到一个已死的
                    // 候选环境上。
                    throw new RuntimeException(scriptType.name()
                            + " candidate context was killed by the statement limit during reload");
                }

                this.scripts = candidateScripts;
                // 成功切换到一个健康的新 Context：旧 Context 可能带有的 killed 标记不再有效。
                this.contextKilled = false;

                if (oldContext != null || oldNodeRuntime != null
                        || oldOutStream != null || oldErrStream != null) {
                    closeRuntimeResources(oldNodeRuntime, oldContext, oldOutStream, oldErrStream);
                }
            } catch (Throwable t) {
                this.context = oldContext;
                this.nodeRuntime = oldNodeRuntime;
                this.contextOutStream = oldOutStream;
                this.contextErrStream = oldErrStream;
                this.contextKilled = previousKilled;
                scriptEventBridge.clearListeners(scriptType);
                closeRuntimeResources(candidateNode, candidateContext, candidateOutStream, candidateErrStream);
                scriptType.logger().error("{} 脚本事务重载失败，已保留旧 Context；listener/binding 状态需再次 reload 恢复",
                        scriptType.name(), t);
                // Note: listeners and bindings were cleared before the candidate build (they live
                // on the shared ScriptType bus, so they MUST be cleared before re-loading to avoid
                // duplicate registration). The surviving old Context is therefore partially degraded
                // until the user re-runs /neko reload. We cannot reorder the clear to after success
                // because old and new scripts share the same event bus.
                throw new RuntimeException(scriptType.name()
                        + " reload failed; previous scripts retained but event listeners/bindings were cleared"
                        + " — run /neko reload again to restore listeners", t);
            }
        }

        public synchronized List<ScriptContainer> reloadScriptFile (String filePath) throws IOException {
            discoverScripts();
            Path target = resolveScriptPath(filePath);

            if (scriptType == ScriptType.STARTUP) {
                // STARTUP registrations are irreversible/non-transactional and ScriptEvents
                // definitions are recreated by a full load (resetEnvironment + discoverScripts
                // + loadScripts + ScriptEvents.post). Re-posting only the affected listeners
                // would wipe unaffected custom-event listener tokens, so a targeted STARTUP
                // reload degrades to a full STARTUP reload.
                List<ScriptContainer> matched = scripts.stream()
                        .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                        .toList();
                if (matched.isEmpty()) {
                    throw new IOException("No loaded STARTUP entry matches " + displayScriptPath(target)
                            + ". Reload the whole STARTUP environment first if this file has not been loaded yet.");
                }
                scriptType.logger().info("正在重载 STARTUP 脚本文件 {}：STARTUP 注册不可逆，退化为完整 STARTUP 重载。", displayScriptPath(target));
                reloadScripts();
                List<ScriptContainer> reloadedMatches = scripts.stream()
                        .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                        .toList();
                return reloadedMatches.isEmpty() ? matched : reloadedMatches;
            }

            List<ScriptContainer> targets = reloadTargets(target);
            if (targets.isEmpty()) {
                throw new IOException("No loaded entry depends on " + displayScriptPath(target) + ". Reload the whole " + scriptType.name() + " environment first if this dependency has not been loaded yet.");
            }
            scriptType.logger().info("正在重载 {} 脚本文件 {}，受影响入口 {} 个...", scriptType.name(), displayScriptPath(target), targets.size());

            NekoModulePipelineCache.invalidate(target);
            Context ctx = getOrCreateContext();
            String modulePath = "./" + paths.root().relativize(target).toString().replace('\\', '/');

            // 单文件重载统一走「失效受影响模块 → 重跑受影响入口」路径。曾经存在一个
            // ModuleSliceRelinker 捷径试图只 relink 不重跑入口，但 Graal 对每个虚拟
            // 模块 URI 缓存模块实例，不重跑入口就无法让 import 绑定看到新导出；且其
            // revision 查询实现不匹配（用新 revision 查旧 record）导致静默 no-op 并
            // 返回 success=true。捷径已删除，见 git 历史。
            //
            // 「失效 → 重跑」必须成对完成：try/finally 保证中途失败（如失效 eval 抛出、
            // Context 重建失败）时，未被重跑的入口也补一次 cleanupScriptEntry（幂等），
            // 不会停留在「模块树已失效但旧 listener / timer 仍在运行」的半失效状态；恢复需再次 reload。
            int rerunCount = 0;
            try {
                synchronized (ctx) {
                    for (ScriptContainer script : targets) {
                        String entryPath = "./" + paths.root().relativize(script.path).toString().replace('\\', '/');
                        ctx.eval("js", "globalThis.__nekoScriptLoader.invalidateModuleTree").execute(entryPath);
                    }
                    ctx.eval("js", "globalThis.__nekoScriptLoader.invalidateAffectedModules").execute(modulePath);
                }

                while (rerunCount < targets.size()) {
                    reloadEntryScript(getOrCreateContext(), targets.get(rerunCount));
                    rerunCount++;
                }
            } finally {
                for (int i = rerunCount; i < targets.size(); i++) {
                    cleanupScriptEntry(targets.get(i));
                }
            }

            scriptType.logger().info("{} 脚本文件 {} 重载完毕。", scriptType.name(), displayScriptPath(target));
            return targets;
        }

        public Optional<ScriptContainer> resolveScriptFile (String filePath) throws IOException {
            discoverScripts();
            Path target = resolveScriptPath(filePath);
            return scripts.stream()
                    .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                    .findFirst();
        }

        private List<ScriptContainer> reloadTargets (Path target){
            Optional<ScriptContainer> directEntry = scripts.stream()
                    .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                    .findFirst();
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
            // 依赖图中记录的所有受影响入口（含目标文件本身，若它是入口）都必须重跑：
            // Graal 按虚拟模块 URI 缓存模块实例，只重跑目标模块无法让已加载入口的
            // import 绑定看到新导出。
            List<ScriptContainer> affectedEntries = scripts.stream()
                    .filter(script -> affectedIds.contains(paths.root().relativize(script.path).toString().replace('\\', '/')))
                    .toList();
            if (!affectedEntries.isEmpty()) {
                return affectedEntries;
            }
            // 文件从未被加载（依赖图无记录）但确实是已发现脚本：直接重跑该入口
            return directEntry.map(List::of).orElseGet(List::of);
        }

        private void reloadEntryScript (Context ctx, ScriptContainer script){
            cleanupScriptEntry(script);
            script.preload();
            if (script.shouldRun()) {
                scriptExecutor.executeEntry(ctx, script, nodeRuntime);
            }
        }

        private void cleanupScriptEntry (ScriptContainer script){
            scriptEventBridge.clearListeners(scriptType, script.id.toString());
            if (nodeRuntime != null) {
                nodeRuntime.timers().cancelScript(script.id.toString());
            }
            errorTracker.clear(script.id);
            errorTracker.clearByScriptPath(scriptType, paths.root().relativize(script.path).toString().replace('\\', '/'));
        }

        private void fullReloadCleanup () {
            scriptEventBridge.clearListeners(scriptType);
            errorTracker.clearByType(scriptType);
            // 清空进程级静态缓存中本 scriptType 的条目：NekoModulePipelineCache.clear(ScriptType)
            // 同时按类型清理对应 SourceMapRegistry 条目；NekoEsmVirtualModuleRegistry 持有虚拟 ESM URI。
            // 局部清除避免单机单类型 reset/close 误清其它类型的编译产物（原全局 clear 会跨类型误伤）。
            NekoModulePipelineCache.clear(scriptType);
            NekoEsmVirtualModuleRegistry.clear(scriptType);
        }

        // ---- 路径解析 ----

        private Path resolveScriptPath (String filePath) throws IOException {
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

        private String displayScriptPath (Path path){
            return scriptType.name + "/" + scriptType.path.relativize(path).toString().replace('\\', '/');
        }

        // ---- 测试脚本 ----

        // synchronized：runTestScripts 内部走 reloadScriptsTransactional（会创建候选 Context），
        // 与 loadScripts / reloadScripts / close 共用实例锁，防止命令线程并发触发 TEST 运行
        // 时输家候选 Context 被孤立泄漏（Context + timer 调度线程）。
        public synchronized void runTestScripts () {
            if (scriptType != ScriptType.TEST) {
                throw new IllegalStateException("runTestScripts() can only be called on TEST ScriptManager");
            }
            scriptType.logger().info("正在运行 TEST 脚本...");

            // TEST 也走事务式 reload：失败时保留上一个 TEST Context，而不是销毁后再尝试加载。
            reloadScriptsTransactional();
            flushTestTimers();

            scriptType.logger().info("TEST 脚本运行完毕。");
        }

        private void flushTestTimers () {
            if (nodeRuntime == null || context == null) return;
            // 上限 1000 轮（≈1s）：覆盖常见的 await setTimeout(...) 异步断言；
            // 失控 interval 也会在此截止，不会挂死 /nekojs test
            for (int i = 0; i < 1000 && nodeRuntime.hasPendingTimers(); i++) {
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

        private void resetEnvironment () {
            fullReloadCleanup();
            for (var binding : pluginRuntime.bindings(scriptType).values()) {
                binding.close(scriptType);
            }

            Context oldContext = this.context;
            NekoNodeRuntime oldRuntime = this.nodeRuntime;
            LoggerStream oldOutStream = this.contextOutStream;
            LoggerStream oldErrStream = this.contextErrStream;
            this.context = null;
            this.nodeRuntime = null;
            this.contextOutStream = null;
            this.contextErrStream = null;

            closeRuntimeResources(oldRuntime, oldContext, oldOutStream, oldErrStream);
        }

        private void closeRuntimeResources (NekoNodeRuntime oldRuntime, Context oldContext,
                                            LoggerStream oldOutStream, LoggerStream oldErrStream){
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
            // Graal 关闭 Context 时只 detach out/err 流、不 flush 也不 close，脚本末尾未以
            // 换行结束的输出会滞留在 LoggerStream 行缓冲中丢失。这里在 context.close() 之后
            // 补一次 close()（幂等冲刷残留缓冲）；必须在 Context 关闭之后——先关流再关
            // Context 会把残余写入路由到已关闭的流。
            closeStreamQuietly(oldOutStream);
            closeStreamQuietly(oldErrStream);
        }

        private static void closeStreamQuietly (LoggerStream stream){
            if (stream == null) return;
            try {
                stream.close();
            } catch (Exception ignored) { // 冲刷失败不应中断销毁流程
            }
        }

        // synchronized：与 loadScripts / reloadScripts 共用实例锁，防止 shutdown 与并发
        // reload/test 交错时销毁半初始化的环境（可重入：closeRuntimeResources 无锁）。
        @Override
        public synchronized void close () {
            fullReloadCleanup();
            for (var binding : pluginRuntime.bindings(scriptType).values()) {
                binding.close(scriptType);
            }
            closeRuntimeResources(this.nodeRuntime, this.context, this.contextOutStream, this.contextErrStream);
            this.context = null;
            this.nodeRuntime = null;
            this.contextOutStream = null;
            this.contextErrStream = null;
        }

        // ---- 查询 ----

        public boolean hasScripts () {
            return scripts != null && !scripts.isEmpty();
        }

        private ScriptEventRegistrar getScriptEventRegistrar () {
            return scriptEventBridge.scriptEventRegistrar();
        }

        public void flushReadyNodeTimers () {
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
        public static ScriptType getTypeFromContext (Context context){
            return ScriptContextRegistry.scriptTypeOf(context);
        }

        public static String switchCurrentScriptId (Context context, String scriptId){
            return ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
        }

        public static void restoreCurrentScriptId (Context context, String scriptId){
            ScriptContextRegistry.restoreCurrentScriptId(context, scriptId);
        }

        public static String getCurrentScriptId (Context context){
            return ScriptContextRegistry.currentScriptIdOf(context);
        }
    }
