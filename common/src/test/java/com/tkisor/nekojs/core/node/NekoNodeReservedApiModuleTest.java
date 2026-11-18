package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.plugin.OwnedPlugin;
import com.tkisor.nekojs.api.plugin.PluginIdentity;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModuleResolver;
import com.tkisor.nekojs.core.module.NodeModuleRegister;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.core.plugin.NodeModulesPoint;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.CoreContractPreviews;
import com.tkisor.nekojs.testfixture.NekoModuleTestFixtures;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NekoNodeReservedApiModuleTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "nekojs:event", "nekojs:event/stable", "nekojs:event/versioned/namespace-test",
            "nekojs:event/unknown/namespace-test", "nekojs/plugins/namespace-test/api/stable"
    })
    void rejectsReservedPluginModuleBeforeItsSourceCanExecute(String route) throws Exception {
        TestPlatformInit.ensureInitialized();
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        ReservedModulePlugin plugin = new ReservedModulePlugin(route);
        NekoPluginRuntime.bootstrapOwned(List.of(new OwnedPlugin(new PluginIdentity("namespace-test",
                        plugin.getClass().getName(), URI.create("test:namespace-test")), plugin)),
                new ScriptPropertyRegistry.Impl(), CoreContractPreviews.emptyPortablePreview());
        try (var cache = NekoModuleTestFixtures.newCache(paths, compilers, config);
             Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.eval("js", "globalThis.reservedSourceExecutions = 0;");
            RuntimeException failure = assertThrows(RuntimeException.class, () -> NekoNodeModuleInstaller.install(context, ScriptType.SERVER,
                    new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(), new ScriptFilePolicy(compilers)),
                    paths, new DefaultErrorTracker(paths, config), config, cache));
            StringBuilder diagnostic = new StringBuilder();
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) diagnostic.append(cause.getMessage());
            assertTrue(diagnostic.toString().contains("NEKO-4057"), diagnostic::toString);
            assertTrue(diagnostic.toString().contains(route), diagnostic::toString);
            assertEquals(0, context.eval("js", "reservedSourceExecutions").asInt(),
                    "reserved plugin module source must not execute before rejection");
        } finally {
            NekoPluginRuntime.bootstrapOwned(List.of(), new ScriptPropertyRegistry.Impl(),
                    CoreContractPreviews.emptyPortablePreview());
        }
    }

    private record ReservedModulePlugin(String route) implements NodeModulesPoint.Contributor {
        @Override public void registerNodeModules(NodeModuleRegister registry) {
            registry.register(route,
                    "globalThis.reservedSourceExecutions++; module.exports = {}; ");
        }
    }
}
