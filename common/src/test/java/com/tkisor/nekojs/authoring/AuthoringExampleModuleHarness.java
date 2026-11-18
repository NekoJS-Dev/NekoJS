package com.tkisor.nekojs.authoring;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonTranspilerPlugin;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoModuleResolver;
import com.tkisor.nekojs.core.module.NekoScriptModuleLoaderHost;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Runs unchanged authoring sources through the actual compiler and reserved-module loader. */
final class AuthoringExampleModuleHarness implements AutoCloseable {
    static final String VERSIONED_EVENTS = "nekojs:event/versioned";

    final NekoJSPaths paths;
    final Context context;
    final NekoNodeRuntime runtime;
    final NekoScriptModuleLoaderHost loader;
    private final ScriptType type;

    AuthoringExampleModuleHarness(Path gameDir, ScriptType type, boolean nativeBeanInterop) throws Exception {
        // Platform paths and loggers belong to the JVM, never to a JUnit temporary workspace.
        TestPlatformInit.ensureInitialized();
        this.type = type;
        paths = NekoJSPaths.fromGameDir(gameDir);
        paths.initFolders();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoJsxLanguagePlugin.INSTANCE);
        compilers.register(NekoTsxLanguagePlugin.INSTANCE);
        new PythonTranspilerPlugin().registerScriptCompilers(compilers);
        SandboxConfig config = SandboxConfig.defaultConfig();
        NekoModulePipelineCache cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(), new SandboxPolicy(config, paths), paths, cache))
                .build();
        Context.Builder builder = Context.newBuilder("js").allowAllAccess(true).allowIO(ioAccess);
        if (nativeBeanInterop) {
            builder.allowExperimentalOptions(true)
                    .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                    .option("js.foreign-object-prototype", "true")
                    .option("js.nashorn-compat", "true")
                    .option("js.ecmascript-version", "latest");
        }
        context = builder.build();
        try {
            runtime = NekoNodeModuleInstaller.install(context, type,
                    new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(), new ScriptFilePolicy(compilers)),
                    paths, new DefaultErrorTracker(paths, config), config, cache);
            loader = runtime.moduleLoaderHost();
        } catch (Throwable failure) {
            try {
                context.close();
            } catch (Throwable cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    void defineVersionedEvents(String exportsExpression) {
        defineVersionedEvents(context.eval("js", exportsExpression));
    }

    void defineVersionedEvents(Value exports) {
        Value namespace = context.eval("js", "exports => Object.freeze(Object.assign(Object.create(null), exports))")
                .execute(exports);
        context.getBindings("js").getMember("__nekoNodeDefineApi").execute(VERSIONED_EVENTS, namespace);
        loader.registerApiModule(VERSIONED_EVENTS, namespace.getMemberKeys().toArray(String[]::new));
        for (String name : namespace.getMemberKeys()) {
            assertFalse(context.getBindings("js").hasMember(name), "event groups must only be available through imports");
        }
    }

    void runExample(String name) throws Exception {
        runSource(name, AuthoringBundle.load().read("examples/" + name));
    }

    void runSource(String name, String source) throws Exception {
        Path scripts = switch (type) {
            case STARTUP -> paths.startupScripts();
            case SERVER -> paths.serverScripts();
            case CLIENT -> paths.clientScripts();
            case TEST -> paths.testScripts();
        };
        Path entry = scripts.resolve("examples/" + name);
        Files.createDirectories(entry.getParent());
        Files.writeString(entry, source);
        loader.loadEntry(entry.toString());
    }

    @Override
    public void close() {
        try {
            runtime.close();
        } finally {
            context.close();
        }
    }
}
