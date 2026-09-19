package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.NekoSharedEngine;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.plugin.NodeModulesPoint;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NekoSandboxFactoryResourceTest {
    @Test
    void failedNodeInstallerClosesPartialRuntimeAndSandboxResources() {
        TestPlatformInit.ensureInitialized();
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        SourceMapRegistry sourceMaps = new SourceMapRegistry(paths.root());
        NekoEsmVirtualModuleRegistry virtualModules = new NekoEsmVirtualModuleRegistry(paths.root());
        NekoModulePipelineCache owner = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                sourceMaps, virtualModules, NekoTrustContext.local());
        NekoModulePipelineCache session = owner.openSession();
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        NekoPluginRuntime.bootstrap(List.of(new BrokenNodeModulePlugin()), new ScriptPropertyRegistry.Impl());
        NekoSandboxFactory factory = new NekoSandboxFactory(
                new NekoCoreContext(NekoSharedEngine.get(), config, ClassFilter.INSTANCE, tracker),
                paths, compilers, NekoPluginRuntime.current(), owner);

        try {
            assertThrows(RuntimeException.class, () -> factory.build(ScriptType.SERVER, session),
                    "a plugin evaluation failure must fail sandbox construction");
            assertEquals(0, session.preparationObserverCount(),
                    "a failed build must unregister the partial module-host observer");
        } finally {
            session.closeSession();
            owner.closeOwner();
            NekoPluginRuntime.bootstrap(List.of(), new ScriptPropertyRegistry.Impl());
        }
    }

    private static final class BrokenNodeModulePlugin implements NekoJSPlugin, NodeModulesPoint.Contributor {
        @Override
        public void registerNodeModules(com.tkisor.nekojs.core.module.NodeModuleRegister registry) {
            registry.register("broken:installer", "throw new Error('forced sandbox build failure');");
        }
    }
}
