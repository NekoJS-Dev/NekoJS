package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.context.NekoCoreContext;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.log.LoggerStream;
import com.tkisor.nekojs.core.module.NekoModuleResolver;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;

import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 实例化沙盒工厂：使用注入的 {@link NekoCoreContext}（paths/engine/sandboxConfig/classFilter）+
 * {@link ScriptCompilerRegistry}（require extension alias）创建 per-{@link ScriptType} GraalVM {@link Context}。
 */
public final class NekoSandboxFactory {
    // Prefix console.warn with [NekoJS_WARN] and console.debug with [NekoJS_DEBUG]
    // for easy grep-filtering of script output in server logs.
    static final String CONSOLE_PATCH_JS = """
            (function() {
                const originalWarn = console.warn;
                console.warn = function(...args) {
                    if (args.length > 0 && typeof args[0] === 'string') {
                        args[0] = '[NekoJS_WARN] ' + args[0];
                        originalWarn.apply(console, args);
                    } else {
                        originalWarn.apply(console, ['[NekoJS_WARN]', ...args]);
                    }
                };
            
                const originalDebug = console.debug;
                console.debug = function(...args) {
                    if (args.length > 0 && typeof args[0] === 'string') {
                        args[0] = '[NekoJS_DEBUG] ' + args[0];
                        console.log.apply(console, args);
                    } else {
                        console.log.apply(console, ['[NekoJS_DEBUG]', ...args]);
                    }
                };
            })();
            """;

    public record Sandbox(Context context, NekoNodeRuntime nodeRuntime) {}

    private final NekoCoreContext core;
    private final ScriptCompilerRegistry compilers;

    public NekoSandboxFactory(NekoCoreContext core, ScriptCompilerRegistry compilers) {
        this.core = core;
        this.compilers = compilers;
    }

    public NekoCoreContext core() {
        return core;
    }

    public ScriptCompilerRegistry compilers() {
        return compilers;
    }

    public Sandbox build(ScriptType type) {
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = core.sandboxConfig();
        ClassFilter classFilter = core.classFilter();

        long t0 = System.nanoTime();
        Logger logger = type.logger();
        OutputStream outStream = new LoggerStream(logger, false);
        OutputStream errStream = new LoggerStream(logger, true);

        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root()))
                .build();

        Context ctx = Context.newBuilder("js")
                .engine(core.engine())
                .allowExperimentalOptions(true)
                .out(outStream)
                .err(errStream)
                .allowHostAccess(NekoSharedHostAccess.get())
                .allowIO(ioAccess)
                .allowCreateThread(config.allowThreads())
                .allowHostClassLookup(classFilter)
                .allowCreateProcess(false)
                .allowValueSharing(true)
                .option("js.foreign-object-prototype", "true")
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .option("js.commonjs-require", "true")
                .option("js.commonjs-require-cwd", paths.root().toAbsolutePath().toString())
                .option("js.interop-complete-promises", "true")
                .option("js.strict", "true")
                .option("js.v8-compat", "true")
                .option("js.unhandled-rejections", "throw")
                .build();
        long t1 = System.nanoTime();

        ctx.eval("js", CONSOLE_PATCH_JS);
        ctx.eval("js", "Java.loadClass = Java.type;");
        long t2 = System.nanoTime();
        NekoNodeRuntime nodeRuntime = NekoNodeModuleInstaller.install(ctx, type,
                new NekoModuleResolver(paths, new ScriptFilePolicy(compilers), compilers),
                paths,
                core.errorTracker(),
                config);
        long t3 = System.nanoTime();

        Set<String> registeredExtensions = new LinkedHashSet<>(compilers.supportedExtensions());
        registeredExtensions.remove(".js");
        registeredExtensions.remove(".mjs");
        registeredExtensions.remove(".cjs");

        if (!registeredExtensions.isEmpty()) {
            StringBuilder js = new StringBuilder("if(typeof require!=='undefined'&&require.extensions){");
            for (String ext : registeredExtensions) {
                js.append("require.extensions['").append(ext).append("']=require.extensions['.js'];");
            }
            js.append('}');
            try {
                ctx.eval("js", js.toString());
            } catch (Exception e) {
                type.logger().warn("Failed to register require extension aliases: {}", registeredExtensions, e);
            }
        }

        long t4 = System.nanoTime();
        logger.info("Sandbox build: context={}ms console={}ms node_modules={}ms extensions={}ms total={}ms",
                (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000,
                (t3 - t2) / 1_000_000, (t4 - t3) / 1_000_000,
                (t4 - t0) / 1_000_000);
        return new Sandbox(ctx, nodeRuntime);
    }
}
