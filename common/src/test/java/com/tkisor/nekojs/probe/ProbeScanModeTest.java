package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖 {@code scan.mode}（SMART/FULL/NONE）与 {@code scan.forceScanMods} 在
 * {@link ProbeCoordinator#collectClasses} / {@link ProbeCoordinator#run} 中的兑现，
 * 以及 {@code /nekojs probe enable|disable} 持久化 API（{@link ProbeCoordinator#setEnabled}）。
 */
class ProbeScanModeTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /* ================= scan.mode ================= */

    @Test
    void smartModeSkipsClassesOutsideWhitelist() {
        NekoScriptCatalogSnapshot snapshot = snapshotWithEvent(java.util.ArrayList.class);
        ProbeConfig smart = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe"), List.of(), List.of(), List.of(), 5, "SMART"));
        Set<Class<?>> collected = ProbeCoordinator.collectClasses(snapshot, smart);
        assertTrue(collected.isEmpty(), "SMART 下种子不命中 include 白名单时 BFS 不应展开任何类");
    }

    @Test
    void fullModeCollectsTypesOutsideWhitelist() {
        NekoScriptCatalogSnapshot snapshot = snapshotWithEvent(java.util.ArrayList.class);
        ProbeConfig full = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe"), List.of(), List.of(), List.of(), 5, "FULL"));
        Set<Class<?>> collected = ProbeCoordinator.collectClasses(snapshot, full);
        assertTrue(collected.contains(java.util.ArrayList.class), "FULL 应收集白名单外的种子类");
        assertTrue(collected.contains(java.util.List.class), "FULL 应沿 BFS 闭包收集到 java.util.List（ArrayList 的直接父接口）");
        assertFalse(collected.contains(Object.class), "Object 始终不收集");
    }

    @Test
    void fullModeStillHonorsExcludePackages() {
        NekoScriptCatalogSnapshot snapshot = snapshotWithEvent(java.util.ArrayList.class);
        ProbeConfig fullExcluded = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe"), List.of(), List.of("java.util"), List.of(), 5, "FULL"));
        Set<Class<?>> collected = ProbeCoordinator.collectClasses(snapshot, fullExcluded);
        assertTrue(collected.isEmpty(), "FULL 下 exclude 必须仍然生效（种子被排除即不再展开）");
    }

    @Test
    void noneModeReturnsFailureForEveryBackend() throws Exception {
        Path cfgFile = NekoJSPaths.get().probeConfig();
        Files.createDirectories(cfgFile.getParent());
        Files.writeString(cfgFile, "enabled = true\n[scan]\nmode = \"NONE\"\n", StandardCharsets.UTF_8);
        ProbeCoordinator.reloadConfig();
        try {
            List<ProbeGenerator.GenerateResult> results =
                    ProbeCoordinator.run(emptySnapshot(), List.of(new TypeScriptProbeBackend()));
            assertEquals(1, results.size());
            assertFalse(results.get(0).success());
            assertEquals("probe disabled (scan mode=NONE in probe.toml)", results.get(0).message());
        } finally {
            Files.deleteIfExists(cfgFile);
            ProbeCoordinator.reloadConfig();
        }
    }

    /* ================= forceScanMods ================= */

    @Test
    void forceScanModsForcesPrefixIntoClosure() {
        NekoScriptCatalogSnapshot snapshot = snapshotWithEvent(ProbeCoordinator.class);
        // include 白名单覆盖为无关前缀；forceScanMods 字面前缀把 probe 包强制纳入
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.example.nothing"), List.of(), List.of(), List.of("com.tkisor.nekojs.probe"), 5, "SMART"));
        Set<Class<?>> collected = ProbeCoordinator.collectClasses(snapshot, cfg);
        assertTrue(collected.contains(ProbeCoordinator.class), "强制前缀应纳入种子类");
        assertTrue(collected.contains(ProbeConfig.class),
                "强制前缀应作用于 BFS 闭包（ProbeCoordinator 公开签名引用 ProbeConfig）");
        assertFalse(collected.contains(com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot.class),
                "未命中白名单/强制前缀的类不得收集");
    }

    @Test
    void forceScanModsEmptyDoesNotForce() {
        NekoScriptCatalogSnapshot snapshot = snapshotWithEvent(ProbeCoordinator.class);
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.example.nothing"), List.of(), List.of(), List.of(), 5, "SMART"));
        assertTrue(ProbeCoordinator.collectClasses(snapshot, cfg).isEmpty(),
                "无强制前缀且白名单不命中时应为空");
    }

    /* ================= enable/disable 持久化 ================= */

    @Test
    void coordinatorSetEnabledPersistsAndReloads() throws Exception {
        Path cfgFile = NekoJSPaths.get().probeConfig();
        Files.createDirectories(cfgFile.getParent());
        Files.writeString(cfgFile, "enabled = true\n", StandardCharsets.UTF_8);
        ProbeCoordinator.reloadConfig();
        try {
            assertTrue(ProbeCoordinator.isEnabled());
            ProbeCoordinator.setEnabled(false);
            assertFalse(ProbeCoordinator.isEnabled(), "setEnabled 后应 reloadConfig 立即生效");
            assertTrue(Files.readString(cfgFile, StandardCharsets.UTF_8).contains("enabled = false"),
                    "probe.toml 应持久化 enabled = false");
        } finally {
            Files.deleteIfExists(cfgFile);
            ProbeCoordinator.reloadConfig();
        }
    }

    /* ================= 工具 ================= */

    private static NekoScriptCatalogSnapshot snapshotWithEvent(Class<?> eventType) {
        EventCatalogEntry event = EventCatalogEntry.of(
                "ProbeScanModeEvents", "test", ScriptType.SERVER, eventType, null, false, false);
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(), List.of(event), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }

    private static NekoScriptCatalogSnapshot emptySnapshot() {
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }
}
