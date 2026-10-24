package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NativeJavaPackageImportTest {
    @TempDir Path gameDir;

    @Test
    void aNamedJavaPackageImportLoadsThroughTheNativeEsmRuntime() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        NekoJSPaths paths = NekoJSPaths.fromGameDir(gameDir);
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        Files.createDirectories(paths.serverScripts());
        Files.writeString(paths.serverScripts().resolve("java-import.mjs"), """
                import { $Integer } from 'java:java/lang';
                export const answer = $Integer.parseInt('42');
                """);
        try (NekoModulePipelineCache cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()), NekoTrustContext.local())) {
            IOAccess io = IOAccess.newBuilder().fileSystem(new NekoJSFileSystem(paths.root(),
                    new SandboxPolicy(config, paths), paths, cache)).build();
            try (Context context = Context.newBuilder("js").allowAllAccess(true).allowIO(io).build();
                 var runtime = NekoNodeModuleInstaller.install(context, ScriptType.SERVER,
                         new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(), new ScriptFilePolicy(compilers)),
                         paths, new DefaultErrorTracker(paths, config), config, cache)) {
                Value exports = assertInstanceOf(Value.class, runtime.moduleLoaderHost().loadEntry("server_scripts/java-import.mjs"));
                assertEquals(42, exports.getMember("answer").asInt());
            }
        }
    }
}
