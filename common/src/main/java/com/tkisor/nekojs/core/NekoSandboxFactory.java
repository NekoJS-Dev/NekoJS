package com.tkisor.nekojs.core;

import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.log.LoggerStream;
import com.tkisor.nekojs.core.module.NekoModuleResolver;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.api.ScriptType;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.ResourceLimits;
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
    private final NekoJSPaths paths;
    private final ScriptCompilerRegistry compilers;
    private final NekoSharedHostAccess hostAccess;

    public NekoSandboxFactory(NekoCoreContext core, NekoJSPaths paths, ScriptCompilerRegistry compilers, IPluginRuntime pluginRuntime) {
        this.core = core;
        this.paths = paths;
        this.compilers = compilers;
        this.hostAccess = new NekoSharedHostAccess(pluginRuntime.adapters());
    }

    public NekoCoreContext core() {
        return core;
    }

    public ScriptCompilerRegistry compilers() {
        return compilers;
    }

    public Sandbox build(ScriptType type) {
        SandboxConfig config = core.sandboxConfig();
        ClassFilter classFilter = core.classFilter();

        Logger logger = type.logger();
        OutputStream outStream = new LoggerStream(logger, false);
        OutputStream errStream = new LoggerStream(logger, true);

        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root()))
                .build();

        Context.Builder contextBuilder = Context.newBuilder("js")
                .engine(core.engine())
                .allowExperimentalOptions(true)
                .out(outStream)
                .err(errStream)
                .allowHostAccess(hostAccess.get())
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
                .option("js.unhandled-rejections", "throw");

        long statementLimit = config.scriptStatementLimit();
        if (statementLimit > 0) {
            contextBuilder.resourceLimits(ResourceLimits.newBuilder()
                    .statementLimit(statementLimit, null)
                    .onLimit(event -> logger.warn(
                            "脚本语句数超过 scriptStatementLimit（{}），Graal 已关闭该 {} 脚本环境；"
                                    + "当前脚本被中止，下一次取用时会自动重建 Context（/nekojs reload 亦可手动恢复）",
                            statementLimit, type.name()))
                    .build());
        }
        Context ctx = contextBuilder.build();

        ctx.eval("js", CONSOLE_PATCH_JS);
        ctx.eval("js", "Java.loadClass = Java.type;");
        NekoNodeRuntime nodeRuntime = NekoNodeModuleInstaller.install(ctx, type,
                new NekoModuleResolver(paths, new ScriptFilePolicy(compilers)),
                paths,
                core.errorTracker(),
                config);

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

        return new Sandbox(ctx, nodeRuntime);
    }
}
