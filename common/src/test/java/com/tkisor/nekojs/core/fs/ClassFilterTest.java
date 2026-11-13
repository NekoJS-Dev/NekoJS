package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.core.config.SandboxConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Table-driven checks of the {@link ClassFilter} blacklist. Each new prefix must reject a
 * representative FQCN, and the blacklist must not catch classes that scripts legitimately use
 * (scripts still load MC classes, JDK value types and nekojs packages outside core).
 */
class ClassFilterTest {

    /** A new blacklist prefix -> a representative FQCN. Every pair must be rejected under the default config. */
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

    /** A sample of existing blacklist entries: adding entries must not accidentally drop an older defence line. */
    private static final List<String> LEGACY_DENIED = List.of(
            "java.lang.Runtime", "java.lang.System", "java.lang.Class",
            "java.io.File", "java.nio.file.Path", "java.net.Socket",
            "sun.misc.Unsafe", "com.sun.management.OperatingSystemMXBean");

    /** Classes that must pass under the default config: JDK value and collection types, MC game classes,
     * and nekojs packages scripts can see (outside core). */
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
        // java.lang.invoke.MethodHandles belongs to the allowReflection group: denied by default,
        // allowed once reflection is enabled.
        String gated = "java.lang.invoke.MethodHandles";
        assertFalse(new ClassFilter(SandboxConfig.defaultConfig()).test(gated));

        ClassFilter allowReflect = new ClassFilter(withAllowReflection(SandboxConfig.defaultConfig(), true));
        assertTrue(allowReflect.test(gated));

        // Same group prefix: java.lang.reflect.* follows the switch as well.
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
        // Even with every high-risk flag enabled by the host, scripts may never look up core internals.
        SandboxConfig allOn = new SandboxConfig(true, true, true, true, true, true, true, true);
        ClassFilter filter = new ClassFilter(allOn);
        assertFalse(filter.test("com.tkisor.nekojs.core.fs.ClassFilter"));
        assertFalse(filter.test("com.tkisor.nekojs.core.fs.NekoJSPaths"));
    }

    private static SandboxConfig withAllowReflection(SandboxConfig base, boolean allowReflection) {
        return new SandboxConfig(base.allowThreads(), allowReflection, base.allowAsm(), base.allowFsWriteOutsideNekojs(),
                base.enableEsmAuthoring(), base.conciseScriptErrorLogs(), base.jsxAutomaticRuntime(),
                base.scriptMemberValidation(), base.packSyncMode(), base.packSyncAllowUnsigned(),
                base.dynamicRegistryEnabled(), base.mixinJsEnabled());
    }

    private static SandboxConfig withAllowThreads(SandboxConfig base, boolean allowThreads) {
        return new SandboxConfig(allowThreads, base.allowReflection(), base.allowAsm(), base.allowFsWriteOutsideNekojs(),
                base.enableEsmAuthoring(), base.conciseScriptErrorLogs(), base.jsxAutomaticRuntime(),
                base.scriptMemberValidation(), base.packSyncMode(), base.packSyncAllowUnsigned(),
                base.dynamicRegistryEnabled(), base.mixinJsEnabled());
    }
}
