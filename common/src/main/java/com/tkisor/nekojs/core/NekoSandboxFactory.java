package com.tkisor.nekojs.core;

import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
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

    /**
     * 携带 out/err {@link LoggerStream} 引用：Graal 关闭 Context 时只 detach 不 close，
     * 由上层销毁路径在 context.close() 之后补 close() 冲刷末行未换行缓冲。
     */
    public record Sandbox(Context context, NekoNodeRuntime nodeRuntime, LoggerStream outStream, LoggerStream errStream) {}

    private final NekoCoreContext core;
    private final NekoJSPaths paths;
    private final ScriptCompilerRegistry compilers;
    private final NekoSharedHostAccess hostAccess;
    /** 每 Engine 共享的失控看门狗（Graal 限制：同一 Engine 的所有 Context 必须共用同一个语句谓词实例）。 */
    private volatile RunawayWatchdog sharedWatchdog;

    public NekoSandboxFactory(NekoCoreContext core, NekoJSPaths paths, ScriptCompilerRegistry compilers, IPluginRuntime pluginRuntime) {
        this.core = core;
        this.paths = paths;
        this.compilers = compilers;
        this.hostAccess = new NekoSharedHostAccess(pluginRuntime.adapters());
    }

    private RunawayWatchdog sharedWatchdog(SandboxConfig config, Logger logger) {
        RunawayWatchdog w = sharedWatchdog;
        if (w == null) {
            synchronized (this) {
                w = sharedWatchdog;
                if (w == null) {
                    w = new RunawayWatchdog(config.scriptRunawayTimeoutSeconds(), config.scriptStatementLimit(),
                            (msg, args) -> logger.warn(msg, args), System::nanoTime);
                    sharedWatchdog = w;
                }
            }
        }
        return w;
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
        LoggerStream outStream = new LoggerStream(logger, false);
        LoggerStream errStream = new LoggerStream(logger, true);

        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(), new SandboxPolicy(config, paths)))
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
        int runawayTimeoutSeconds = config.scriptRunawayTimeoutSeconds();
        if (statementLimit > 0 || runawayTimeoutSeconds > 0) {
            // 两种保护共用一个 statementLimit 回调（ResourceLimits.Builder 只允许一个）；
            // 且 Graal 要求同一 Engine 的所有 Context 共用同一个谓词实例，故工厂级懒缓存共享。
            RunawayWatchdog watchdog = sharedWatchdog(config, logger);
            contextBuilder.resourceLimits(ResourceLimits.newBuilder()
                    .statementLimit(watchdog.checkInterval(), watchdog)
                    .onLimit(event -> logger.warn(
                            "脚本环境 {} 触发 ResourceLimits（失控看门狗 {}s / 语句上限 {}），Graal 已关闭该 Context；"
                                    + "当前求值被中止，下一次取用时会自动重建（/nekojs reload 亦可手动恢复）",
                            type.name(), runawayTimeoutSeconds, statementLimit))
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

        return new Sandbox(ctx, nodeRuntime, outStream, errStream);
    }
}
