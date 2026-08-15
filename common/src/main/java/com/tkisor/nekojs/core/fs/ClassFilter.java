package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.JavaClassLoadTelemetry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.config.SandboxConfigLoader;

import java.util.Set;
import java.nio.file.Files;
import java.util.function.Predicate;

/**
 * 负责过滤 GraalVM 沙盒中的 Java 类访问权限，支持细粒度权限控制。
 *
 * <p>通过 {@link #INSTANCE} 访问全局实例，配置通过 {@link #config()} 获取。
 *
 * <p><b>安全边界（重要）</b>：本过滤器只拦截宿主类<b>查找</b>（{@code Java.type}/{@code Java.loadClass}），
 * 不治理已返回对象的成员图——宿主对象返回给脚本后的可访问成员由 {@code HostAccess} 决定。
 * 因此本黑名单无法单独构成完全沙盒，脚本仍是 semi-trusted（半受信）：
 * <ul>
 *   <li>{@code com.tkisor.nekojs.core} 前缀：NekoJS 自身内部实现（fs/config/engine/node/compiler 等，
 *       含本类与 {@link NekoJSPaths}）。脚本绝不应直接加载这些类——否则可通过
 *       {@code NekoJSPaths.gameDir().resolve(...).toFile()} 拿到完整文件 API，或在运行时调用
 *       {@link #updateConfig} 翻转 allowReflection 等高危开关。</li>
 *   <li>{@code java.awt}/{@code javax.swing}/{@code javax.imageio}：客户端桌面 API
 *       （Robot 按键/截屏、剪贴板）。</li>
 *   <li>{@code javax.naming}/{@code java.rmi}：JNDI/RMI 远程类加载通道。</li>
 *   <li>{@code java.sql}/{@code javax.sql}：JDBC 数据源连接。</li>
 *   <li>{@code org.graalvm}/{@code com.oracle.truffle}：Graal/Truffle 引擎与 polyglot 内部。</li>
 *   <li>{@code java.lang.Module}：模块系统反射（与 {@code java.lang.Class} 同级列入）。</li>
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
            // 线程/并发原语：allowThreads=false 的设计意图是脚本不得自行创建线程，
            // 前缀黑名单必须把可构造线程池的包也挡在 host 类查找之外
            "java.util.concurrent", "java.lang.management", "java.util.prefs",
            "java.beans", "javax.management", "java.security",
            // 客户端桌面 API（Robot 按键/截屏/剪贴板）与 JNDI/RMI 远程加载通道、JDBC
            "java.awt", "javax.swing", "javax.imageio", "javax.naming", "java.rmi",
            "java.sql", "javax.sql",
            // NekoJS 自身内部实现（fs/config/engine/node/compiler 等，含 ClassFilter/NekoJSPaths 本身）：
            // 脚本一旦能 lookup 到这些类即可绕过文件策略、或在运行时翻转高危开关
            "com.tkisor.nekojs.core",
            // Graal/Truffle 引擎内部：脚本不应触碰 polyglot 引擎自身的运行时结构
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
     * 运行时更新沙盒配置。
     *
     * <p><b>安全警告</b>：本方法会整体替换黑名单生效的开关集合，只允许宿主侧（mod 初始化/
     * {@link #loadEngineConfig}）调用。绝不可暴露给脚本——脚本一旦可达即可先开启
     * {@code allowReflection} 再借 {@code java.lang.invoke.MethodHandles} 逃逸沙盒。
     * {@code com.tkisor.nekojs.core} 已列入 {@link #GENERAL_BLACKLIST 常规黑名单}，
     * 因此脚本无法通过 {@code Java.type} 拿到本类与 {@link #INSTANCE}；新增对外绑定时
     * 也严禁把 {@code ClassFilter}/{@code SandboxConfig} 的可变引用传入脚本环境。
     */
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

    /**
     * 加载引擎配置：规范位置 {@code <gamedir>/nekojs/config/engine.toml}（与 probe.toml 同目录）。
     * 该文件不存在而旧位置 {@code <gamedir>/config/nekojs-engine.toml} 存在时，以只读方式回退
     * 读取旧文件并警告提示迁移——<b>不会</b>自动搬移或写出新文件，需用户手动迁移。
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
