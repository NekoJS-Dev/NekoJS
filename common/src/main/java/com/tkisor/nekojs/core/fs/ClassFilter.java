package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.JavaClassLoadTelemetry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.config.SandboxConfigLoader;

import java.util.Set;
import java.nio.file.Files;
import java.util.function.Predicate;

/**
 * Filters Java class access in the GraalVM sandbox and supports fine-grained permissions.
 *
 * <p>Use the shared instance through {@link #INSTANCE}; read the configuration through
 * {@link #config()}.
 *
 * <p><b>Security limit (important)</b>: this filter only intercepts host class <b>lookup</b>
 * ({@code Java.type}/{@code Java.loadClass}). It does not govern the member graph of objects that
 * were already returned; what a script may reach on a returned host object is decided by
 * {@code HostAccess}. The blacklist alone is therefore not a complete sandbox, and scripts stay
 * semi-trusted:
 * <ul>
 *   <li>{@code com.tkisor.nekojs.core} prefix: NekoJS's own implementation (fs/config/engine/node/compiler
 *       and so on, including this class and {@link NekoJSPaths}). Scripts should never load these
 *       classes directly; {@code NekoJSPaths.gameDir().resolve(...).toFile()} would hand them the
 *       full file API, and {@link #updateConfig} could flip high-risk switches such as
 *       allowReflection at runtime.</li>
 *   <li>{@code java.awt}/{@code javax.swing}/{@code javax.imageio}: client desktop APIs
 *       (Robot key presses and screenshots, the clipboard).</li>
 *   <li>{@code javax.naming}/{@code java.rmi}: JNDI/RMI remote class-loading channels.</li>
 *   <li>{@code java.sql}/{@code javax.sql}: JDBC data source connections.</li>
 *   <li>{@code org.graalvm}/{@code com.oracle.truffle}: Graal/Truffle engine and polyglot
 *       internals.</li>
 *   <li>{@code java.lang.Module}: module system reflection, listed alongside {@code java.lang.Class}.</li>
 * </ul>
 */
public class ClassFilter implements Predicate<String> {

    public static final ClassFilter INSTANCE = new ClassFilter(SandboxConfig.defaultConfig());

    private volatile SandboxConfig config;

    private static final Set<String> THREAD_GROUP = Set.of("java.lang.Thread", "java.lang.ThreadGroup");
    private static final Set<String> REFLECT_GROUP = Set.of("java.lang.reflect", "java.lang.invoke.MethodHandles");
    private static final Set<String> ASM_GROUP = Set.of("org.objectweb.asm", "org.spongepowered.asm");
    private static final Set<String> GENERAL_BLACKLIST = Set.of(
            "java.lang.Class", "java.lang.Runtime", "java.lang.Process", "java.lang.ProcessBuilder",
            "java.lang.ClassLoader", "java.lang.System", "java.lang.Module",
            "java.io", "java.nio", "java.net", "java.util.jar", "java.util.zip",
            // Thread and concurrency primitives: allowThreads=false means scripts must not create threads
            // themselves, so the prefix blacklist must also keep packages that can build thread pools out of
            // host class lookup
            "java.util.concurrent", "java.lang.management", "java.util.prefs",
            "java.beans", "javax.management", "java.security",
            // Client desktop APIs (Robot key presses, screenshots, clipboard), JNDI/RMI remote loading
            // channels, and JDBC
            "java.awt", "javax.swing", "javax.imageio", "javax.naming", "java.rmi",
            "java.sql", "javax.sql",
            // NekoJS's own implementation (fs/config/engine/node/compiler and so on, including ClassFilter and
            // NekoJSPaths): once a script can look these classes up, it can bypass the file policy or
            // flip high-risk switches at runtime
            "com.tkisor.nekojs.core",
            // Graal/Truffle engine internals: scripts must not reach into the polyglot engine's own runtime
            // structures
            "org.graalvm", "com.oracle.truffle",
            "sun", "com.sun", "jdk",
            "io.netty", "org.openjdk.nashorn", "jdk.nashorn", "org.lwjgl.system",
            "javax.script", "graal.graalvm.polyglot",
            "net.neoforged.fml", "net.neoforged.accesstransformer", "net.neoforged.coremod",
            "cpw.mods.modlauncher", "cpw.mods.gross"
    );

    public ClassFilter(SandboxConfig config) {
        this.config = config;
    }

    public SandboxConfig config() {
        return config;
    }

    /**
     * Updates the sandbox config at runtime.
     *
     * <p><b>Security warning</b>: this method replaces the whole set of switches the blacklist
     * uses, and only the host side may call it (mod initialization / {@link #loadEngineConfig}).
     * Never expose it to scripts: once a script can reach it, it turns on {@code allowReflection}
     * and escapes the sandbox through {@code java.lang.invoke.MethodHandles}.
     * {@code com.tkisor.nekojs.core} is listed in the {@link #GENERAL_BLACKLIST general blacklist}, so scripts cannot
     * get this class or {@link #INSTANCE} through {@code Java.type}. New bindings must also never
     * pass a mutable {@code ClassFilter}/{@code SandboxConfig} reference into the script
     * environment.
     */
    public void updateConfig(SandboxConfig newConfig) {
        this.config = newConfig;
        decisionCache.clear();
    }

    @Override
    public boolean test(String className) {
        boolean allowed = isAllowed(className);
        JavaClassLoadTelemetry.recordAttempt(className, allowed);
        return allowed;
    }

    /** Caches allow/deny per class name; test() runs on every class load, so 40 prefix streams are too expensive. */
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> decisionCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private boolean isAllowed(String className) {
        Boolean cached = decisionCache.get(className);
        if (cached != null) return cached;
        boolean allowed = computeAllowed(className);
        decisionCache.put(className, allowed);
        return allowed;
    }

    private boolean computeAllowed(String className) {
        if (!config.allowThreads() && matchesGroup(className, THREAD_GROUP)) return false;
        if (!config.allowReflection() && matchesGroup(className, REFLECT_GROUP)) return false;
        if (!config.allowAsm() && matchesGroup(className, ASM_GROUP)) return false;
        if (matchesGroup(className, GENERAL_BLACKLIST)) return false;
        return true;
    }

    /**
     * Package prefix match: a prefix must equal the full class name, or the class name must
     * continue with {@code .} or {@code $} (the binary name of a nested class). A bare
     * {@code startsWith} would wrongly catch {@code sunlight.*} under {@code sun.*} and
     * {@code jdkutil.*} under {@code jdk.*}. The {@code $} branch is required: without it
     * {@code java.lang.invoke.MethodHandles$Lookup} would slip past the {@code MethodHandles}
     * prefix.
     */
    private static boolean matchesGroup(String className, Set<String> group) {
        for (String prefix : group) {
            if (className.equals(prefix)
                    || className.startsWith(prefix + '.')
                    || className.startsWith(prefix + '$')) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loads the engine config from the canonical location
     * {@code <gamedir>/nekojs/config/engine.toml} (the same directory as probe.toml). If that file
     * is absent and the old location {@code <gamedir>/config/nekojs-engine.toml} exists, the old
     * file is read read-only and a warning asks the user to migrate. It does <b>not</b> move the
     * file or write a new one; migration is manual.
     */
    public static SandboxConfig loadEngineConfig() {
        NekoJSPaths paths = NekoJSPaths.get();
        java.nio.file.Path engineConfig = paths.engineConfig();
        java.nio.file.Path legacy = paths.legacyEngineConfig();
        if (!Files.exists(engineConfig) && Files.exists(legacy)) {
            NekoJS.LOGGER.warn(
                    "[NekoJS] Legacy engine config found at {}. Please migrate it to {} "
                            + "(the canonical location is nekojs/config/ alongside probe.toml). "
                            + "The legacy file is still honored read-only and will NOT be moved automatically.",
                    legacy, engineConfig);
            SandboxConfig config = new SandboxConfigLoader().load(legacy, false);
            INSTANCE.updateConfig(config);
            NekoJS.LOGGER.info(
                    "Engine config loaded (legacy read-only). Unsafe features enabled: {}",
                    config.anyUnsafeFeatureEnabled()
            );
            return config;
        }
        return loadEngineConfig(engineConfig);
    }

    public static SandboxConfig loadEngineConfig(java.nio.file.Path engineConfig) {
        SandboxConfig config = new SandboxConfigLoader().load(engineConfig);
        INSTANCE.updateConfig(config);
        NekoJS.LOGGER.info(
                "Engine config loaded. Unsafe features enabled: {}",
                config.anyUnsafeFeatureEnabled()
        );
        return config;
    }
}
