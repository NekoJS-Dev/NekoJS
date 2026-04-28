package com.tkisor.nekojs.core.fs;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.tkisor.nekojs.NekoJSCommon;

import java.util.Set;
import java.util.function.Predicate;

import static com.tkisor.nekojs.core.fs.NekoJSPaths.ENGINE_CONFIG;

/**
 * 负责过滤 GraalVM 沙盒中的 Java 类访问权限，支持细粒度权限控制
 */
public class ClassFilter implements Predicate<String> {

    public static final ClassFilter INSTANCE = new ClassFilter();

    public static boolean allowThreads = false;
    public static boolean allowReflection = false;
    public static boolean allowAsm = false;

    private static final Set<String> THREAD_GROUP = Set.of("java.lang.Thread", "java.lang.ThreadGroup");
    private static final Set<String> REFLECT_GROUP = Set.of("java.lang.reflect", "java.lang.invoke.MethodHandles");
    private static final Set<String> ASM_GROUP = Set.of("org.objectweb.asm", "org.spongepowered.asm");
    private static final Set<String> GENERAL_BLACKLIST = Set.of(
            "java.lang.Runtime", "java.lang.Process", "java.lang.ProcessBuilder",
            "java.lang.ClassLoader", "java.lang.System",
            "java.io", "java.nio", "java.net", "java.util.jar", "java.util.zip",
            "sun", "com.sun",
            "io.netty", "org.openjdk.nashorn", "jdk.nashorn", "org.lwjgl.system",
            "javax.script", "graal.graalvm.polyglot",
            "net.neoforged.fml", "net.neoforged.accesstransformer", "net.neoforged.coremod",
            "cpw.mods.modlauncher", "cpw.mods.gross"
    );

    private ClassFilter() {}

    @Override
    public boolean test(String className) {
        if (!allowThreads && matchesGroup(className, THREAD_GROUP)) return false;
        if (!allowReflection && matchesGroup(className, REFLECT_GROUP)) return false;
        if (!allowAsm && matchesGroup(className, ASM_GROUP)) return false;
        if (matchesGroup(className, GENERAL_BLACKLIST)) return false;
        return true;
    }

    private boolean matchesGroup(String className, Set<String> group) {
        return group.stream().anyMatch(className::startsWith);
    }

    public static boolean isAnyUnsafeFeatureEnabled() {
        return allowThreads || allowReflection || allowAsm;
    }

    public static void loadEngineConfig() {
        try (CommentedFileConfig config = CommentedFileConfig.builder(ENGINE_CONFIG)
                .sync()
                .preserveInsertionOrder()
                .autosave()
                .build()) {

            config.load();

            setupConfigEntry(config, "allowThreads", false,
                    " Allows scripts to create unmanaged background threads. May cause lag or resource leaks.");

            setupConfigEntry(config, "allowReflection", false,
                    " Allows scripts to bypass access controls via reflection and modify private Java data.");

            setupConfigEntry(config, "allowAsm", false,
                    " Allows scripts to directly manipulate Java bytecode. Incorrect usage may cause severe crashes.");

            ClassFilter.allowThreads = config.get("allowThreads");
            ClassFilter.allowReflection = config.get("allowReflection");
            ClassFilter.allowAsm = config.get("allowAsm");

            NekoJSCommon.LOGGER.info(
                    "[NekoJS] Engine config loaded. Unsafe features enabled: {}",
                    ClassFilter.isAnyUnsafeFeatureEnabled()
            );

        } catch (Exception e) {
            NekoJSCommon.LOGGER.error("[NekoJS] Failed to load engine.toml", e);
        }
    }

    private static void setupConfigEntry(CommentedFileConfig config, String path, Object defaultValue, String comment) {
        if (!config.contains(path)) {
            config.set(path, defaultValue);
            config.setComment(path, comment);
        }
    }
}