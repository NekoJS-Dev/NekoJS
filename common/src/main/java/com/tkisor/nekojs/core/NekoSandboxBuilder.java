package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoJSTypeAdapters;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.log.LoggerStream;
import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;

import java.io.OutputStream;

/**
 * 专门负责构建安全 GraalVM 沙盒的工厂类
 * 共享引擎以降低内存占用
 */
public final class NekoSandboxBuilder {
    private static final Engine SHARED_ENGINE = Engine.newBuilder("js")
            .allowExperimentalOptions(true)
            .option("engine.WarnInterpreterOnly", "false")
            .build();

    private static final HostAccess SHARED_HOST_ACCESS = createSharedHostAccess();

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

    private static HostAccess createSharedHostAccess() {
        HostAccess.Builder hostBuilder = HostAccess.newBuilder(HostAccess.ALL)
                .allowAllClassImplementations(true)
                .allowAllImplementations(true);

        NekoJSTypeAdapters.all().forEach(adapter -> registerTypeAdapter(hostBuilder, adapter));
        return hostBuilder.build();
    }

    public static Context build(ScriptType type) {
        Logger logger = type.logger();
        OutputStream outStream = new LoggerStream(logger, false);
        OutputStream errStream = new LoggerStream(logger, true);

        Context ctx = Context.newBuilder("js")
                .engine(SHARED_ENGINE)
                .allowExperimentalOptions(true)
                .out(outStream)
                .err(errStream)
                .allowHostAccess(SHARED_HOST_ACCESS)
                .allowIO(SHARED_IO_ACCESS)
                .allowCreateThread(true)
                .allowHostClassLookup(ClassFilter.INSTANCE)
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

        return ctx;
    }

    private static <T> void registerTypeAdapter(HostAccess.Builder builder, JSTypeAdapter<T> adapter) {
        builder.targetTypeMapping(
                        Value.class,
                        adapter.getTargetClass(),
                        adapter::canConvert,
                        adapter::convert
                )
                .targetTypeMapping(Number.class, Float.class, n -> true, Number::floatValue)
                .targetTypeMapping(Number.class, Integer.class, n -> true, Number::intValue);
    }
}