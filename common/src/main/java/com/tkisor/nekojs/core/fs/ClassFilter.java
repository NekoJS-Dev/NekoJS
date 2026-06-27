package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.JavaClassLoadTelemetry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.config.SandboxConfigLoader;

import java.util.Set;
import java.util.function.Predicate;

/**
 * 负责过滤 GraalVM 沙盒中的 Java 类访问权限，支持细粒度权限控制。
 *
 * <p>通过 {@link #INSTANCE} 访问全局实例，配置通过 {@link #config()} 获取。
 */
public class ClassFilter implements Predicate<String> {

    public static final ClassFilter INSTANCE = new ClassFilter(SandboxConfig.defaultConfig());

    private volatile SandboxConfig config;

    private static final Set<String> THREAD_GROUP = Set.of("java.lang.Thread", "java.lang.ThreadGroup");
    private static final Set<String> REFLECT_GROUP = Set.of("java.lang.reflect", "java.lang.invoke.MethodHandles");
    private static final Set<String> ASM_GROUP = Set.of("org.objectweb.asm", "org.spongepowered.asm");
    private static final Set<String> GENERAL_BLACKLIST = Set.of(
            "java.lang.Class", "java.lang.Runtime", "java.lang.Process", "java.lang.ProcessBuilder",
            "java.lang.ClassLoader", "java.lang.System",
            "java.io", "java.nio", "java.net", "java.util.jar", "java.util.zip",
            "sun", "com.sun",
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

    public void updateConfig(SandboxConfig newConfig) {
        this.config = newConfig;
    }

    @Override
    public boolean test(String className) {
        boolean allowed = isAllowed(className);
        JavaClassLoadTelemetry.recordAttempt(className, allowed);
        return allowed;
    }

    private boolean isAllowed(String className) {
        if (!config.allowThreads() && matchesGroup(className, THREAD_GROUP)) return false;
        if (!config.allowReflection() && matchesGroup(className, REFLECT_GROUP)) return false;
        if (!config.allowAsm() && matchesGroup(className, ASM_GROUP)) return false;
        if (matchesGroup(className, GENERAL_BLACKLIST)) return false;
        return true;
    }

    private boolean matchesGroup(String className, Set<String> group) {
        return group.stream().anyMatch(className::startsWith);
    }

    public static SandboxConfig loadEngineConfig() {
        return loadEngineConfig(NekoJSPaths.get().engineConfig());
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
