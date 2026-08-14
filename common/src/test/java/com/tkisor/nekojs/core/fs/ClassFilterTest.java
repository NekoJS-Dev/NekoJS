package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.core.config.SandboxConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表驱动校验 {@link ClassFilter} 黑名单：新增前缀必须有代表性 FQCN 被拒，
 * 同时确认黑名单没有误伤脚本正常可用的类（脚本仍可加载 MC / JDK 值类型 / nekojs 非 core 包）。
 */
class ClassFilterTest {

    /** 常规黑名单新前缀 -> 代表性 FQCN：全部必须在默认配置下被拒。 */
    private static final List<String[]> NEW_BLACKLIST_PREFIXES = List.of(
            new String[]{"com.tkisor.nekojs.core", "com.tkisor.nekojs.core.fs.ClassFilter"},
            new String[]{"com.tkisor.nekojs.core", "com.tkisor.nekojs.core.fs.NekoJSPaths"},
            new String[]{"com.tkisor.nekojs.core", "com.tkisor.nekojs.core.config.SandboxConfigLoader"},
            new String[]{"com.tkisor.nekojs.core", "com.tkisor.nekojs.core.NekoSandboxFactory"},
            new String[]{"java.awt", "java.awt.Robot"},
            new String[]{"javax.swing", "javax.swing.JFrame"},
            new String[]{"javax.naming", "javax.naming.InitialContext"},
            new String[]{"java.rmi", "java.rmi.registry.LocateRegistry"},
            new String[]{"javax.imageio", "javax.imageio.ImageIO"},
            new String[]{"java.sql", "java.sql.DriverManager"},
            new String[]{"javax.sql", "javax.sql.DataSource"},
            new String[]{"org.graalvm", "org.graalvm.polyglot.Context"},
            new String[]{"com.oracle.truffle", "com.oracle.truffle.api.TruffleLanguage"},
            new String[]{"java.lang.Module", "java.lang.Module"});

    /** 既有黑名单抽样：防止新增条目时意外移除旧的防线。 */
    private static final List<String> LEGACY_DENIED = List.of(
            "java.lang.Runtime", "java.lang.System", "java.lang.Class",
            "java.io.File", "java.nio.file.Path", "java.net.Socket",
            "sun.misc.Unsafe", "com.sun.management.OperatingSystemMXBean");

    /** 默认配置下应放行的类：JDK 值/集合类型、MC 游戏类、nekojs 脚本可见包（core 之外）。 */
    private static final List<String> ALLOWED = List.of(
            "java.lang.String", "java.util.ArrayList", "java.util.HashMap",
            "net.minecraft.world.item.ItemStack",
            "com.tkisor.nekojs.api.NekoJSPlugin",
            "com.tkisor.nekojs.js.DelegatingBinding");

    @Test
    void deniesEveryNewBlacklistPrefixUnderDefaultConfig() {
        ClassFilter filter = new ClassFilter(SandboxConfig.defaultConfig());
        for (String[] entry : NEW_BLACKLIST_PREFIXES) {
            String prefix = entry[0];
            String representative = entry[1];
            assertFalse(filter.test(representative), "should deny " + representative + " (prefix " + prefix + ")");
        }
    }

    @Test
    void keepsDenyingLegacyBlacklistEntries() {
        ClassFilter filter = new ClassFilter(SandboxConfig.defaultConfig());
        for (String className : LEGACY_DENIED) {
            assertFalse(filter.test(className), "should deny " + className);
        }
    }

    @Test
    void allowsCommonValueAndGameClassesByDefault() {
        ClassFilter filter = new ClassFilter(SandboxConfig.defaultConfig());
        for (String className : ALLOWED) {
            assertTrue(filter.test(className), "should allow " + className);
        }
    }

    @Test
    void reflectGatedPrefixDeniedByDefaultAndAllowedWhenReflectionEnabled() {
        // java.lang.invoke.MethodHandles 属于 allowReflection 门控组：默认拒绝，显式开启后放行
        String gated = "java.lang.invoke.MethodHandles";
        assertFalse(new ClassFilter(SandboxConfig.defaultConfig()).test(gated));

        ClassFilter allowReflect = new ClassFilter(withAllowReflection(SandboxConfig.defaultConfig(), true));
        assertTrue(allowReflect.test(gated));

        // 同一门控组前缀：java.lang.reflect.* 随开关切换
        assertFalse(new ClassFilter(SandboxConfig.defaultConfig()).test("java.lang.reflect.Method"));
        assertTrue(allowReflect.test("java.lang.reflect.Method"));
    }

    @Test
    void threadGatedPrefixDeniedByDefaultAndAllowedWhenThreadsEnabled() {
        assertFalse(new ClassFilter(SandboxConfig.defaultConfig()).test("java.lang.Thread"));
        assertTrue(new ClassFilter(withAllowThreads(SandboxConfig.defaultConfig(), true)).test("java.lang.Thread"));
    }

    @Test
    void nekojsCoreStaysDeniedEvenWhenAllGatedGroupsEnabled() {
        // 即使宿主显式开启全部高危开关，core 内部实现也绝不允许脚本 lookup
        SandboxConfig allOn = new SandboxConfig(true, true, true, true, true, true, true, true, 30, 50_000_000L);
        ClassFilter filter = new ClassFilter(allOn);
        assertFalse(filter.test("com.tkisor.nekojs.core.fs.ClassFilter"));
        assertFalse(filter.test("com.tkisor.nekojs.core.fs.NekoJSPaths"));
    }

    private static SandboxConfig withAllowReflection(SandboxConfig base, boolean allowReflection) {
        return new SandboxConfig(base.allowThreads(), allowReflection, base.allowAsm(), base.allowFsWriteOutsideNekojs(),
                base.enableEsmAuthoring(), base.conciseScriptErrorLogs(), base.jsxAutomaticRuntime(),
                base.scriptMemberValidation(), base.scriptEvaluationTimeoutSeconds(), base.scriptStatementLimit());
    }

    private static SandboxConfig withAllowThreads(SandboxConfig base, boolean allowThreads) {
        return new SandboxConfig(allowThreads, base.allowReflection(), base.allowAsm(), base.allowFsWriteOutsideNekojs(),
                base.enableEsmAuthoring(), base.conciseScriptErrorLogs(), base.jsxAutomaticRuntime(),
                base.scriptMemberValidation(), base.scriptEvaluationTimeoutSeconds(), base.scriptStatementLimit());
    }
}
