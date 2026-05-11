package com.tkisor.nekojs.core;

import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.log.LoggerStream;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;

import java.io.OutputStream;

/**
 * 专门负责构建安全 GraalVM 沙盒的工厂类
 * 共享引擎以降低内存占用
 */
public final class NekoSandboxBuilder {
    private static final IOAccess SHARED_IO_ACCESS = IOAccess.newBuilder()
            .fileSystem(new NekoJSFileSystem(NekoJSPaths.ROOT))
            .build();

    private static final String CONSOLE_PATCH_JS = """
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

    private NekoSandboxBuilder() {}

    public record Sandbox(Context context, NekoNodeRuntime nodeRuntime) {}

    public static Context build(ScriptType type) {
        return buildSandbox(type).context();
    }

    public static Sandbox buildSandbox(ScriptType type) {
        Logger logger = type.logger();
        OutputStream outStream = new LoggerStream(logger, false);
        OutputStream errStream = new LoggerStream(logger, true);

        Context ctx = Context.newBuilder("js")
                .engine(NekoSharedEngine.get())
                .allowExperimentalOptions(true)
                .out(outStream)
                .err(errStream)
                .allowHostAccess(NekoSharedHostAccess.get())
                .allowIO(SHARED_IO_ACCESS)
                .allowCreateThread(ClassFilter.allowThreads)
                .allowHostClassLookup(ClassFilter.INSTANCE)
                .allowCreateProcess(false)
                .allowValueSharing(true)
                .option("js.foreign-object-prototype", "true")
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .option("js.commonjs-require", "true")
                .option("js.commonjs-require-cwd", NekoJSPaths.ROOT.toAbsolutePath().toString())
                .option("js.interop-complete-promises", "true")
                .option("js.strict", "true")
                .option("js.v8-compat", "true")
                .build();

        ctx.eval("js", CONSOLE_PATCH_JS);
        ctx.eval("js", "Java.loadClass = Java.type;");
        NekoNodeRuntime nodeRuntime = NekoNodeModuleInstaller.install(ctx, type);

        try {
            ctx.eval("js", """
                        if (typeof require !== 'undefined' && require.extensions) {
                            require.extensions['.ts'] = require.extensions['.js'];
                            require.extensions['.tsx'] = require.extensions['.js'];
                            require.extensions['.jsx'] = require.extensions['.js'];
                        }
                    """);
        } catch (Exception e) {
            type.logger().warn("注入 require 扩展名补丁失败", e);
        }

        return new Sandbox(ctx, nodeRuntime);
    }
}