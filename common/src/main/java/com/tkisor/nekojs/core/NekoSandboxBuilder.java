package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
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
import java.util.Set;

/**
 * Builds a sandboxed GraalVM {@link Context} per {@link ScriptType}.
 * All contexts share a single engine ({@link NekoSharedEngine}) to reduce memory.
 *
 * <h2>Security</h2>
 * <ul>
 *   <li>{@code allowCreateProcess(false)} — no shell/process access from scripts</li>
 *   <li>{@code allowHostClassLookup(ClassFilter.INSTANCE)} — restricts which Java classes
 *        are visible via {@code Java.type()}; blacklists {@code java.io}, {@code java.nio},
 *        {@code java.net}, {@code java.lang.System}, etc.</li>
 *   <li>{@link NekoJSFileSystem} — restricts file I/O to the game directory;
 *        writes default to {@code .minecraft/nekojs/} unless overridden in config</li>
 *   <li>{@code allowValueSharing(true)} — enables sharing host objects between
 *        contexts on the same engine. Combined with the permissive
 *        {@link NekoSharedHostAccess#get} (built on {@code HostAccess.ALL}),
 *        the primary gate is {@link ClassFilter}.</li>
 * </ul>
 */
public final class NekoSandboxBuilder {
    private static final IOAccess SHARED_IO_ACCESS = IOAccess.newBuilder()
            .fileSystem(new NekoJSFileSystem(NekoJSPaths.ROOT))
            .build();

    // Prefix console.warn with [NekoJS_WARN] and console.debug with [NekoJS_DEBUG]
    // for easy grep-filtering of script output in server logs.
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
        long t0 = System.nanoTime();
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
                .option("js.unhandled-rejections", "throw")
                .build();
        long t1 = System.nanoTime();

        ctx.eval("js", CONSOLE_PATCH_JS);
        ctx.eval("js", "Java.loadClass = Java.type;");
        long t2 = System.nanoTime();
        NekoNodeRuntime nodeRuntime = NekoNodeModuleInstaller.install(ctx, type);
        long t3 = System.nanoTime();

        // Register all known non-native extensions with CommonJS require().
        // GraalVM's native require only handles .js / .mjs / .cjs by default.
        // Every language plugin registered via ScriptCompilerRegistry gets its
        // extensions aliased to the .js handler so `require('./helper.ts')` works.
        Set<String> registeredExtensions = new java.util.LinkedHashSet<>(
                ScriptCompilerRegistry.current().supportedExtensions());
        // Remove native GraalVM extensions — they're already handled
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