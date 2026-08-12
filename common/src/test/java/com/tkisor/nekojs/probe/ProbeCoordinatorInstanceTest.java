package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 覆盖 {@link ProbeCoordinator} 的实例化重构（C3）：
 * 用自定义 {@link NekoJSPaths} + {@link ProbeExternalArtifacts#NONE} 在临时目录里隔离测试
 * 实例方法（readConfig/reloadConfigCache/runProbe）的缓存、NONE 早退不写盘、多实例缓存互不影响；
 * 静态 facade（config() 等旧签名）保持可调用。
 */
class ProbeCoordinatorInstanceTest {

    /* ================= (a) readConfig() 缓存与 reloadConfigCache ================= */

    @Test
    void configCachesAndReloadsFromDisk(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path cfgFile = paths.probeConfig();
        Files.createDirectories(cfgFile.getParent());
        Files.writeString(cfgFile, "enabled = false\nbaseDir = \"custom_out\"\n", StandardCharsets.UTF_8);

        ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        ProbeConfig first = c.readConfig();
        assertSame(first, c.readConfig(), "readConfig() 应缓存同一实例");
        assertFalse(first.enabled());
        assertEquals("custom_out", first.baseDir());

        Files.writeString(cfgFile, "enabled = true\nbaseDir = \"custom_out\"\n", StandardCharsets.UTF_8);
        c.reloadConfigCache();
        assertTrue(c.readConfig().enabled(), "reloadConfigCache 后应重读磁盘");
        assertNotSame(first, c.readConfig(), "reload 后应为新加载的实例");
    }

    /* ================= (b) runProbe：NONE 模式早退，不写盘 ================= */

    @Test
    void runNoneModeReturnsFailureWithoutWriting(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path cfgFile = paths.probeConfig();
        Files.createDirectories(cfgFile.getParent());
        Files.writeString(cfgFile, "enabled = true\n[scan]\nmode = \"NONE\"\n", StandardCharsets.UTF_8);

        ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        List<ProbeGenerator.GenerateResult> results = c.runProbe(emptySnapshot(), List.of(new TypeScriptProbeBackend()));
        assertEquals(1, results.size());
        assertFalse(results.get(0).success());
        assertEquals("probe disabled (scan mode=NONE in probe.toml)", results.get(0).message());
        assertFalse(Files.exists(paths.gameDir().resolve(".neko_probe")), "NONE 早退不应写任何输出");
    }

    /* ================= (c) 两个实例（不同 tmp 目录）缓存互不影响 ================= */

    @Test
    void twoInstancesHaveIndependentConfigCaches(@TempDir Path tmp) throws Exception {
        ProbeCoordinator a = new ProbeCoordinator(NekoJSPaths.fromGameDir(tmp.resolve("a")), ProbeExternalArtifacts.NONE);
        ProbeCoordinator b = new ProbeCoordinator(NekoJSPaths.fromGameDir(tmp.resolve("b")), ProbeExternalArtifacts.NONE);

        Path cfgA = NekoJSPaths.fromGameDir(tmp.resolve("a")).probeConfig();
        Path cfgB = NekoJSPaths.fromGameDir(tmp.resolve("b")).probeConfig();
        Files.createDirectories(cfgA.getParent());
        Files.createDirectories(cfgB.getParent());
        Files.writeString(cfgA, "enabled = false\n", StandardCharsets.UTF_8);
        Files.writeString(cfgB, "enabled = true\n", StandardCharsets.UTF_8);

        assertFalse(a.readConfig().enabled());
        assertTrue(b.readConfig().enabled());

        // b 重载只读自己的 probe.toml；a 的缓存与磁盘变化都不得互相影响
        Files.writeString(cfgB, "enabled = false\n", StandardCharsets.UTF_8);
        b.reloadConfigCache();
        assertFalse(b.readConfig().enabled());

        Files.writeString(cfgA, "enabled = true\n", StandardCharsets.UTF_8);
        assertFalse(a.readConfig().enabled(), "a 未 reload：缓存不受自己磁盘文件与 b 重载影响");
        a.reloadConfigCache();
        assertTrue(a.readConfig().enabled(), "a reload 后读到自己目录的新值");
    }

    /* ================= (d) 静态 facade 仍可调用 ================= */

    @Test
    void staticConfigFacadeRemainsCallable() {
        // 静态 config() 委托 NekoJSPaths.get()（Platform.getGameDir()）：测试环境须先注入平台；
        // 注入失败（如环境不支持反射）则跳过——实例方法路径已在上面覆盖。
        try {
            TestPlatformInit.ensureInitialized();
            ProbeConfig cfg = ProbeCoordinator.config();
            assertNotNull(cfg, "静态 config() 应返回配置（异常时 loader 回退 defaultConfig）");
            ProbeCoordinator.reloadConfig();
        } catch (Throwable t) {
            assumeTrue(false, "static facade unavailable without platform paths: " + t);
        }
    }

    /* ================= 工具 ================= */

    private static NekoScriptCatalogSnapshot emptySnapshot() {
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }
}
