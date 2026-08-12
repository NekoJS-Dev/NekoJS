package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖 {@link ProbeConfig} 的包过滤与默认白名单合成逻辑。
 * 核心回归点：默认配置在 NeoForge 平台包下必须逐字复现旧 {@code isRelevantClass} 的 5 前缀行为。
 */
class ProbeConfigTest {

    private static final List<String> NEOFORGE_PLATFORM = List.of("net.minecraft", "net.neoforged");

    @Test
    void defaultWhitelistReproducesLegacyFivePrefixes() {
        ProbeConfig cfg = ProbeConfig.defaultConfig();
        Set<String> included = cfg.effectiveIncludePackages(NEOFORGE_PLATFORM);
        // 固定 + 平台
        assertTrue(included.contains("java"));
        assertTrue(included.contains("com.tkisor.nekojs"));
        assertTrue(included.contains("net.minecraft"));
        assertTrue(included.contains("net.neoforged"));
        // 旧逻辑里的 net.minecraftforge 不在 NeoForge 平台包里，但旧 isRelevantClass 检查它；
        // 在 NeoForge 运行时无对应类，故行为等价（测试平台层面不包含）。这里断言默认不含。
        assertFalse(included.contains("net.minecraftforge"));
    }

    @Test
    void defaultRelevanceMatchesLegacyBehavior() {
        ProbeConfig cfg = ProbeConfig.defaultConfig();
        // 旧 5 前缀命中的样本
        assertTrue(cfg.isRelevantClass("java.lang.String", NEOFORGE_PLATFORM));
        assertTrue(cfg.isRelevantClass("net.minecraft.world.item.ItemStack", NEOFORGE_PLATFORM));
        assertTrue(cfg.isRelevantClass("net.neoforged.neoforge.event.X", NEOFORGE_PLATFORM));
        assertTrue(cfg.isRelevantClass("com.tkisor.nekojs.api.ScriptType", NEOFORGE_PLATFORM));
        // 不在白名单
        assertFalse(cfg.isRelevantClass("com.google.gson.JsonObject", NEOFORGE_PLATFORM));
        assertFalse(cfg.isRelevantClass("io.netty.buffer.X", NEOFORGE_PLATFORM));
    }

    @Test
    void includePackagesOverridesDefaults() {
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.example", "org.foo"), List.of(), List.of(), List.of(), 5, "SMART"));
        Set<String> included = cfg.effectiveIncludePackages(NEOFORGE_PLATFORM);
        assertEquals(Set.of("com.example", "org.foo"), included,
                "non-empty includePackages must fully override defaults+platform");
        // java/net.minecraft 不再命中
        assertFalse(cfg.isRelevantClass("java.lang.String", NEOFORGE_PLATFORM));
        assertTrue(cfg.isRelevantClass("com.example.Bar", NEOFORGE_PLATFORM));
    }

    @Test
    void extraIncludePackagesAppendedToDefaults() {
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(), List.of("com.mojang", "com.example"), List.of(), List.of(), 5, "SMART"));
        Set<String> included = cfg.effectiveIncludePackages(NEOFORGE_PLATFORM);
        assertTrue(included.contains("java"));            // 默认保留
        assertTrue(included.contains("net.minecraft"));   // 平台保留
        assertTrue(included.contains("com.mojang"));      // 追加
        assertTrue(cfg.isRelevantClass("com.mojang.brigadier.X", NEOFORGE_PLATFORM));
    }

    @Test
    void excludePackagesDenyListed() {
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(), List.of(), List.of("net.minecraft.client"), List.of(), 5, "SMART"));
        assertTrue(cfg.isRelevantClass("net.minecraft.world.item.ItemStack", NEOFORGE_PLATFORM));
        assertFalse(cfg.isRelevantClass("net.minecraft.client.renderer.X", NEOFORGE_PLATFORM),
                "exclude must override include");
    }

    @Test
    void scanConfigNormalizesModeAndNulls() {
        ProbeConfig.ScanConfig s = new ProbeConfig.ScanConfig(null, null, null, null, 0, null);
        assertEquals("SMART", s.mode());
        assertTrue(s.includePackages().isEmpty());
        assertEquals(0, s.maxDepth());
        ProbeConfig cfg = new ProbeConfig(true, null, s);
        assertEquals(".neko_probe", cfg.baseDir());

        ProbeConfig.ScanConfig upper = new ProbeConfig.ScanConfig(List.of(), List.of(), List.of(), List.of(), 5, "full");
        assertEquals("FULL", upper.mode());
    }

    @Test
    void relevancePrefixUsesDotBoundary() {
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("net.minecraft"), List.of(), List.of(), List.of(), 5, "SMART"));
        // 不应被 "net.m" 前缀误命中：net.minecraftforgecraft 不该匹配 net.minecraft
        assertFalse(cfg.isRelevantClass("net.minecraftforgecraft.X", NEOFORGE_PLATFORM));
        assertTrue(cfg.isRelevantClass("net.minecraft.X", NEOFORGE_PLATFORM));
    }

    @Test
    void unknownScanModeFallsBackToSmart() {
        ProbeConfig.ScanConfig garbage = new ProbeConfig.ScanConfig(
                List.of(), List.of(), List.of(), List.of(), 5, "garbage-mode");
        assertEquals("SMART", garbage.mode(), "未知 mode 取值应兜底为 SMART（等价旧默认行为）");
    }

    @Test
    void forcedScanModsResolveBuiltInTableAndLiteralPrefixes() {
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(), List.of(), List.of(),
                List.of("minecraft", "neoforge", "forge", "java", "com.example.api", "unknown_mod"),
                5, "SMART"));
        Set<String> forced = cfg.forcedPackages();
        assertEquals(Set.of("net.minecraft", "net.neoforged", "net.minecraftforge", "java", "com.example.api"),
                forced, "内置表 modId 映射为包前缀；含 '.' 的条目原样保留；未知 mod id 忽略");
        assertFalse(forced.contains("minecraft"), "mod id 应被表值替换，而非原样保留");
    }

    @Test
    void isExcludedOnlyChecksExcludePackages() {
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("net.minecraft"), List.of(), List.of("net.minecraft.client"), List.of(), 5, "SMART"));
        assertTrue(cfg.isExcluded("net.minecraft.client.renderer.X"));
        assertFalse(cfg.isExcluded("net.minecraft.world.item.ItemStack"));
    }

    @Test
    void setEnabledWritesEnabledIntoProbeToml(@TempDir Path tmp) throws Exception {
        Path cfgFile = NekoJSPaths.fromGameDir(tmp).probeConfig();
        Files.createDirectories(cfgFile.getParent());
        Files.writeString(cfgFile, "enabled = true\n[scan]\nmode = \"SMART\"\n", StandardCharsets.UTF_8);

        ProbeConfigLoader.setEnabled(cfgFile, false);

        String content = Files.readString(cfgFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("enabled = false"), "probe.toml 应含 enabled = false，实际内容: " + content);
        assertFalse(new ProbeConfigLoader().load(cfgFile).enabled(), "重载后 enabled 应为 false");
    }
}
