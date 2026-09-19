package com.tkisor.nekojs.testfixture;

import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;

/** Explicit cache fixtures for tests that assemble execution objects directly. */
public final class NekoModuleTestFixtures {
    private NekoModuleTestFixtures() {}

    public static NekoModulePipelineCache newCache(NekoJSPaths paths,
                                                    ScriptCompilerRegistry compilers,
                                                    SandboxConfig config) {
        return new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
    }
}
