package com.tkisor.nekojs.testfixture;

import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoModulePreparation;

/**
 * 测试辅助：构造<strong>独立实例</strong>的 {@link NekoModulePreparation}（工单 11）。
 *
 * <p>生产路径由 {@code NekoRuntimeRoot} 持有模块 session cache；测试各自构造独立实例，
 * 不共享任何 process-wide 状态，也不再需要用反射去戳 static 缓存。
 */
public final class TestModulePreparation {

    private TestModulePreparation() {}

    public static NekoModulePreparation create(NekoJSPaths paths, ScriptCompilerRegistry compilers, SandboxConfig config) {
        return new NekoModulePreparation(new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config), paths);
    }

    public static NekoModulePreparation create(NekoJSPaths paths, SandboxConfig config) {
        return create(paths, ScriptCompilerRegistry.current(), config);
    }

    public static NekoModulePreparation create() {
        return create(NekoJSPaths.get(), SandboxConfig.defaultConfig());
    }
}
