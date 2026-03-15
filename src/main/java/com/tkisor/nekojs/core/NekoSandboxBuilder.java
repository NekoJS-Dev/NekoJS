package com.tkisor.nekojs.core;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.bindings.event.RegisterJSTypeAdaptersEvent;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.log.LoggerStream;
import com.tkisor.nekojs.script.ScriptType;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;

import java.io.OutputStream;
import java.util.Set;

/**
 * 专门负责构建安全 GraalVM 沙盒的工厂类
 */
public final class NekoSandboxBuilder {
    private static final Set<String> CLASS_BLACKLIST = Set.of(
            "java.lang.Runtime",
            "java.lang.Process",
            "java.lang.ProcessBuilder",
            "java.lang.Thread",
            "java.lang.ThreadGroup",
            "java.lang.ClassLoader",
            "java.lang.System",
            "java.lang.reflect",
            "java.lang.invoke.MethodHandles",

            "java.io",
            "java.nio",
            "java.net",
            "java.util.jar",
            "java.util.zip",

            "sun",
            "com.sun",
            "org.objectweb.asm",
            "org.spongepowered.asm",

            "io.netty",
            "org.openjdk.nashorn",
            "jdk.nashorn",
            "org.lwjgl.system",
            "javax.script",
            "org.graalvm.polyglot",

            "net.neoforged.fml",
            "net.neoforged.accesstransformer",
            "net.neoforged.coremod",

            "cpw.mods.modlauncher",
            "cpw.mods.gross"
    );

    private static final String CONSOLE_PATCH_JS = """
            (function() {
                const originalWarn = console.warn;
                console.warn = function(...args) {
                    // 如果第一个参数是字符串（可能是格式化文本），直接把暗号拼在前面，防止破坏格式化
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
                        // 注意：debug 走的是标准输出 (log)，所以我们用 originalLog
                        console.log.apply(console, args);
                    } else {
                        console.log.apply(console, ['[NekoJS_DEBUG]', ...args]);
                    }
                };
            })();
            """;

    private NekoSandboxBuilder() {}

    public static Context build(ScriptType type) {
        HostAccess.Builder hostBuilder = HostAccess.newBuilder(HostAccess.ALL);

        RegisterJSTypeAdaptersEvent adaptersEvent = new RegisterJSTypeAdaptersEvent();
        NekoJS.modEventBus.post(adaptersEvent);
        for (JSTypeAdapter<?> adapter : adaptersEvent.getAdapters()) {
            registerTypeAdapter(hostBuilder, adapter);
        }

        Logger logger = type.logger();
        OutputStream outStream = new LoggerStream(logger, false);
        OutputStream errStream = new LoggerStream(logger, true);

        Context ctx = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .out(outStream)
                .err(errStream)
                .allowHostAccess(hostBuilder.build())
                .allowIO(IOAccess.newBuilder().fileSystem(new NekoJSFileSystem(NekoJSPaths.ROOT)).build())
                .allowCreateThread(true)
                .allowHostClassLookup(c -> CLASS_BLACKLIST.stream().noneMatch(c::startsWith))
                .option("engine.WarnInterpreterOnly", "false")
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .option("js.commonjs-require", "true")
                .option("js.commonjs-require-cwd", NekoJSPaths.ROOT.toAbsolutePath().toString())
                .build();

        ctx.eval("js", CONSOLE_PATCH_JS);
        return ctx;
    }

    /**
     * 专门用于解决 Java 泛型通配符捕获问题的辅助方法
     * 它将未知的 <?> 强行绑定为确定的 <T>，满足 GraalVM 的方法签名要求
     */
    private static <T> void registerTypeAdapter(HostAccess.Builder builder, JSTypeAdapter<T> adapter) {
        builder.targetTypeMapping(
                Value.class,
                adapter.getTargetClass(),
                adapter::canConvert,
                adapter::convert
        );
    }
}