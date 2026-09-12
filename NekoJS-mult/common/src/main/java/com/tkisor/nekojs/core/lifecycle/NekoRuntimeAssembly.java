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
import com.tkisor.nekojs.core.error.ErrorTrackerReporter;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
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
 *   <li>NekoModulePipeline legacy 静态绑定（读取点在 NekoModulePipelineCache）</li>
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
    public record Assembled(NekoRuntimeRoot root, NekoPluginRuntime pluginRuntime) {}

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
        NekoPluginRuntime pluginRuntime = NekoPluginRuntime.bootstrapOwned(ownedPlugins, scriptProperties);
        NekoRuntimeAccess.get().fireInit();
        pluginWiring.wire(pluginRuntime);

        var compilers = ScriptCompilerRegistry.current();
        SandboxConfig sandboxConfig = ClassFilter.loadEngineConfig();
        // 复用全局单例（NekoSecurityWarningHandler 等读取 ClassFilter.INSTANCE），避免双实例状态分裂
        ClassFilter classFilter = ClassFilter.INSTANCE;
        var errorTracker = new DefaultErrorTracker(NekoJSPaths.get(), sandboxConfig);
        ScriptErrorReporter.set(new ErrorTrackerReporter(errorTracker));
        NekoCoreContext core = new NekoCoreContext(
                NekoSharedEngine.get(),
                sandboxConfig,
                classFilter,
                errorTracker
        );
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, NekoJSPaths.get(), compilers, pluginRuntime);
        NekoModulePipeline.bindLegacyInstance(new NekoModulePipeline(new NekoCompilationPipeline(), compilers, sandboxConfig));
        NekoRuntimeRoot root = new NekoRuntimeRoot(
                core,
                pluginRuntime,
                eventBridge,
                scriptProperties,
                sandboxFactory
        );

        for (ScriptType type : ScriptType.autoLoadTypes()) {
            root.createScriptManager(type).discoverScripts();
        }

        root.scriptManagerOf(ScriptType.STARTUP).loadScripts();
        NekoRuntimeAccess.get().fireInitStartup();
        return new Assembled(root, pluginRuntime);
    }
}
