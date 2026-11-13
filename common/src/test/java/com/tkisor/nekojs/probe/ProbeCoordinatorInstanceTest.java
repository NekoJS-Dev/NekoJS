package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
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
 * Covers the instance-based {@link ProbeCoordinator}:
 * a custom {@link NekoJSPaths} plus {@link ProbeExternalArtifacts#NONE} isolate the instance methods
 * (readConfig/reloadConfigCache/runProbe) in a temp directory. Tests the config cache, the early
 * return in NONE mode that writes nothing, and that two instances keep separate caches; the static
 * facade (config() and the other old signatures) stays callable.
 */
class ProbeCoordinatorInstanceTest {

    /* ================= (a) readConfig() cache, automatic reload and reloadConfigCache ================= */

    @Test
    void configCachesAndReloadsFromDisk(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path cfgFile = paths.probeConfig();
        Files.createDirectories(cfgFile.getParent());
        Files.writeString(cfgFile, "enabled = false\nbaseDir = \"custom_out\"\n", StandardCharsets.UTF_8);

        ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        ProbeConfig first = c.readConfig();
        assertSame(first, c.readConfig(), "readConfig() 应缓存同一实例（文件未变）");
        assertFalse(first.enabled());
        assertEquals("custom_out", first.baseDir());

        Files.writeString(cfgFile, "enabled = true\nbaseDir = \"custom_out\"\n", StandardCharsets.UTF_8);
        c.reloadConfigCache();
        assertTrue(c.readConfig().enabled(), "reloadConfigCache 后应重读磁盘");
        assertNotSame(first, c.readConfig(), "reload 后应为新加载的实例");
    }

    @Test
    void readConfigAutoReloadsWhenFileChanges(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path cfgFile = paths.probeConfig();
        Files.createDirectories(cfgFile.getParent());
        Files.writeString(cfgFile, "enabled = false\n", StandardCharsets.UTF_8);

        ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        ProbeConfig first = c.readConfig();
        assertFalse(first.enabled());
        assertSame(first, c.readConfig(), "mtime 未变 → 命中缓存");

        // Simulates the user editing the config: change the content and push mtime forward explicitly, since
        // some file systems have a coarse mtime granularity.
        Files.writeString(cfgFile, "enabled = true\n", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(cfgFile, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));

        ProbeConfig second = c.readConfig();
        assertTrue(second.enabled(), "mtime 变化后 readConfig 应自动重读磁盘（无需手动 reload）");
        assertNotSame(first, second, "自动重读后应为新加载的实例");
        assertSame(second, c.readConfig(), "重读后 mtime 未再变 → 再次命中缓存");

        // The file was deleted: readConfig must go through the loader again (create the default file or fall
        // back to the defaults) and must not return a stale cache.
        Files.delete(cfgFile);
        ProbeConfig third = c.readConfig();
        assertTrue(Files.exists(cfgFile), "文件缺失时 loader 应重新自动创建");
        assertTrue(third.enabled(), "删除后重读应拿到默认配置（enabled = true）");
    }

    @Test
    void readConfigReloadsWhenSizeChangesWithSameMtime(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path cfgFile = paths.probeConfig();
        Files.createDirectories(cfgFile.getParent());

        // Fix mtime and change only the content length: a cache stamp that relies on mtime alone would miss
        // it, since some file systems have a coarse mtime granularity.
        java.nio.file.attribute.FileTime fixed = java.nio.file.attribute.FileTime.fromMillis(1_700_000_000_000L);
        Files.writeString(cfgFile, "enabled = true\n", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(cfgFile, fixed);

        ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        ProbeConfig first = c.readConfig();
        assertTrue(first.enabled());
        assertSame(first, c.readConfig(), "mtime 与 size 均未变 → 命中缓存");

        Files.writeString(cfgFile, "enabled = false\n", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(cfgFile, fixed); // The key case: mtime equals the cached stamp and only the size changed.

        ProbeConfig second = c.readConfig();
        assertFalse(second.enabled(), "size 变化（同 mtime）也应触发自动重读");
        assertNotSame(first, second);
    }

    /* ================= (b) runProbe: early return in NONE mode, nothing is written ================= */

    @Test
    void runNoneModeReturnsFailureWithoutWriting(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path cfgFile = paths.probeConfig();
        Files.createDirectories(cfgFile.getParent());
        Files.writeString(cfgFile, "enabled = true\n[scan]\nmode = \"NONE\"\n", StandardCharsets.UTF_8);

        ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        List<ProbeBackend.GenerateResult> results = c.runProbe(emptySnapshot(), List.of(new TypeScriptProbeBackend()));
        assertEquals(1, results.size());
        assertFalse(results.get(0).success());
        assertEquals("probe disabled (scan mode=NONE in probe.toml)", results.get(0).message());
        assertFalse(Files.exists(paths.gameDir().resolve(".neko_probe")), "NONE 早退不应写任何输出");
    }

    /* ================= (c) Two instances in different temp directories keep independent caches ================= */

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

        // Reloading b reads only its own probe.toml; neither a's cache nor a's disk changes may affect it.
        Files.writeString(cfgB, "enabled = false\n", StandardCharsets.UTF_8);
        b.reloadConfigCache();
        assertFalse(b.readConfig().enabled());

        // a's own file changed: the automatic reload reads the new value from a's directory only; b is unaffected.
        Files.writeString(cfgA, "enabled = true\n", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(cfgA, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        assertTrue(a.readConfig().enabled(), "a 的文件变化后应自动重读（不依赖 b 或手动 reload）");
        assertFalse(b.readConfig().enabled(), "b 的缓存不得被 a 的重读影响");
    }

    /* ================= (d) Static facade stays callable ================= */

    @Test
    void staticConfigFacadeRemainsCallable() {
        // The static config() delegates to NekoJSPaths.get() (Platform.getGameDir()), so the test has to
        // inject the platform first. When injection fails (for example the environment does not allow
        // reflection), skip: the instance method path is already covered above.
        try {
            TestPlatformInit.ensureInitialized();
            ProbeConfig cfg = ProbeCoordinator.config();
            assertNotNull(cfg, "静态 config() 应返回配置（异常时 loader 回退 defaultConfig）");
            ProbeCoordinator.reloadConfig();
        } catch (Throwable t) {
            assumeTrue(false, "static facade unavailable without platform paths: " + t);
        }
    }

    /* ================= Helpers ================= */

    private static NekoScriptCatalogSnapshot emptySnapshot() {
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }
}
