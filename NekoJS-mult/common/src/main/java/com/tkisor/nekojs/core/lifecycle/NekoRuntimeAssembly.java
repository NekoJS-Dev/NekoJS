package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.api.plugin.OwnedPlugin;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSharedEngine;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.log.NekoJSLoggers;
import com.tkisor.nekojs.core.error.ErrorTrackerReporter;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

import java.util.List;

/**
 * 两个 loader（NeoForge {@code NekoJSMod} / Fabric {@code NekoJSFabricMod}）共享的运行时
 * 装配序列——纯构造实现（shared construction function），<b>不是</b>新的 runtime owner：
 * 不缓存任何 static，不提供 get()/current()，产物由调用方（loader entry）私有持有。
 *
 * <p>装配顺序（与两 loader 既有实现逐行对齐，行为保持）：
 * <ol>
 *   <li>{@link NekoPluginRuntime#bootstrapOwned}（loader 侧插件发现在此之前完成）</li>
 *   <li>{@link NekoRuntimeAccess#get()}.fireInit()（initStartup 前置 init 钩子）</li>
 *   <li>loader 特有事件面接线（{@link PluginWiring}：registrar bind / bridge set，顺序随平台保持）</li>
 *   <li>引擎上下文：compilers → SandboxConfig → ClassFilter.INSTANCE → ErrorTracker →
 *       ScriptErrorReporter → NekoCoreContext（NekoSharedEngine）→ NekoSandboxFactory</li>
 *   <li>W3 语言管线显式装配：NekoModulePipeline + NekoModulePipelineCache 各一个实例，sandbox factory 与 root 共享（无 static 绑定、无 legacy instance）</li>
 *   <li>{@link NekoRuntimeRoot} 构造（唯一 lifecycle owner）</li>
 *   <li>逐 {@link ScriptType#autoLoadTypes()} createScriptManager + discoverScripts</li>
 *   <li>STARTUP loadScripts</li>
 *   <li>{@link NekoRuntimeAccess#get()}.fireInitStartup()</li>
 * </ol>
 * 之后各平台的收尾差异（如 {@code GoalEvents.postRegister()}）留在 loader entry。
 *
 * <p>进程级例外（NekoSharedEngine / NekoRuntimeAccess / NekoPluginRuntime publish）只在
 * 本序列的固定点位触碰一次，不因共同装配产生第二套状态（工单 05 AC8）。
 */
public final class NekoRuntimeAssembly {

    private NekoRuntimeAssembly() {}

    /** 装配产物：{@code root} 是唯一 lifecycle 入口，由 loader entry 私有持有。 */
    /** 只带 root：调用方（loader entry）不消费其他产物，避免装配件长出第二个访问面。 */
    public record Assembled(NekoRuntimeRoot root) {}

    /** loader 特有事件面接线：在 fireInit 之后、引擎装配之前执行一次。 */
    @FunctionalInterface
    public interface PluginWiring {
        void wire(NekoPluginRuntime pluginRuntime);
    }

    /**
     * 执行完整装配序列。{@code ownedPlugins} 来自 loader 侧发现
     * （{@code NekoJSBasePluginManager.getOwnedPlugins()}），装配内不再触碰 loader 发现面。
     */
    public static Assembled assemble(
            ScriptEventBridge eventBridge,
            ScriptPropertyRegistry scriptProperties,
            List<OwnedPlugin> ownedPlugins,
            PluginWiring pluginWiring) {
        return assemble(eventBridge, scriptProperties, ownedPlugins, pluginWiring, NekoTrustContext.local());
    }

    /**
     * Assembly boundary for an explicitly authorized remote pack. The default overload above
     * remains the local-trusted production path; the context is carried only by the preparation
     * cache and its resolution collaborators, never by execution APIs.
     */
    public static Assembled assemble(
            ScriptEventBridge eventBridge,
            ScriptPropertyRegistry scriptProperties,
            List<OwnedPlugin> ownedPlugins,
            PluginWiring pluginWiring,
            NekoTrustContext trustContext) {
        if (trustContext == null) {
            throw new NullPointerException("trustContext");
        }
        NekoPluginRuntime pluginRuntime = NekoPluginRuntime.bootstrapOwned(ownedPlugins, scriptProperties);
        // AC8 的计数面：本行每次进程只应出现一次（bootstrapOwned 重复调用会被 publish 拒绝），
        // 烟测以 reload 前后各 grep 一次验证计数不变；Fabric 侧另有 entrypoint/bootstrap-done marker。
        NekoJSLoggers.get("NekoJS").info("NekoJS plugin runtime bootstrapped once (assembly)");
        NekoRuntimeAccess.get().fireInit();
        pluginWiring.wire(pluginRuntime);

        var compilers = ScriptCompilerRegistry.current();
        SandboxConfig sandboxConfig = ClassFilter.loadEngineConfig();
        // 复用全局单例（NekoSecurityWarningHandler 等读取 ClassFilter.INSTANCE），避免双实例状态分裂
        ClassFilter classFilter = ClassFilter.INSTANCE;
        SourceMapRegistry sourceMaps = new SourceMapRegistry(NekoJSPaths.get().root());
        NekoEsmVirtualModuleRegistry virtualModules = new NekoEsmVirtualModuleRegistry(NekoJSPaths.get().root());
        var errorTracker = new DefaultErrorTracker(NekoJSPaths.get(), sandboxConfig, sourceMaps, virtualModules);
        ScriptErrorReporter.set(new ErrorTrackerReporter(errorTracker));
        NekoCoreContext core = new NekoCoreContext(
                NekoSharedEngine.get(),
                sandboxConfig,
                classFilter,
                errorTracker
        );
        // W3 语言模块管线：单 pipeline + 单 prepared 缓存实例，执行环境侧与 root 共享；
        // 模块 cache/session 生命周期由 root 持有（root close 全清），无 static 状态。
        NekoModulePipelineCache modulePreparationCache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, sandboxConfig),
                sourceMaps, virtualModules, trustContext);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, NekoJSPaths.get(), compilers, pluginRuntime, modulePreparationCache);
        NekoRuntimeRoot root = new NekoRuntimeRoot(
                core,
                pluginRuntime,
                eventBridge,
                scriptProperties,
                sandboxFactory,
                modulePreparationCache
        );

        for (ScriptType type : ScriptType.autoLoadTypes()) {
            root.createScriptManager(type).discoverScripts();
        }

        root.scriptManagerOf(ScriptType.STARTUP).loadScripts();
        NekoRuntimeAccess.get().fireInitStartup();
        return new Assembled(root);
    }
}
