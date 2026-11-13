package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers how {@link ProbeConfig} combines package filters with the default whitelist.
 * The main regression: under the NeoForge platform packages, the default config must reproduce
 * the five prefixes that {@code isRelevantClass} matches, character for character.
 */
class ProbeConfigTest {

    private static final List<String> NEOFORGE_PLATFORM = List.of("net.minecraft", "net.neoforged");

    @Test
    void defaultWhitelistReproducesLegacyFivePrefixes() {
        ProbeConfig cfg = ProbeConfig.defaultConfig();
        Set<String> included = cfg.effectiveIncludePackages(NEOFORGE_PLATFORM);
        // fixed prefixes plus platform packages
        assertTrue(included.contains("java"));
        assertTrue(included.contains("com.tkisor.nekojs"));
        assertTrue(included.contains("net.minecraft"));
        assertTrue(included.contains("net.neoforged"));
        // net.minecraftforge is not a NeoForge platform package, but isRelevantClass checks it. No such
        // class exists when running NeoForge, so the behavior is equivalent. This asserts the default
        // set does not contain it.
        assertFalse(included.contains("net.minecraftforge"));
    }

    @Test
    void defaultRelevanceMatchesLegacyBehavior() {
        ProbeConfig cfg = ProbeConfig.defaultConfig();
        // samples that hit the five original prefixes
        assertTrue(cfg.isRelevantClass("java.lang.String", NEOFORGE_PLATFORM));
        assertTrue(cfg.isRelevantClass("net.minecraft.world.item.ItemStack", NEOFORGE_PLATFORM));
        assertTrue(cfg.isRelevantClass("net.neoforged.neoforge.event.X", NEOFORGE_PLATFORM));
        assertTrue(cfg.isRelevantClass("com.tkisor.nekojs.api.ScriptType", NEOFORGE_PLATFORM));
        // not in the whitelist
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
        // java and net.minecraft no longer match
        assertFalse(cfg.isRelevantClass("java.lang.String", NEOFORGE_PLATFORM));
        assertTrue(cfg.isRelevantClass("com.example.Bar", NEOFORGE_PLATFORM));
    }

    @Test
    void extraIncludePackagesAppendedToDefaults() {
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(), List.of("com.mojang", "com.example"), List.of(), List.of(), 5, "SMART"));
        Set<String> included = cfg.effectiveIncludePackages(NEOFORGE_PLATFORM);
        assertTrue(included.contains("java"));            // kept from the defaults
        assertTrue(included.contains("net.minecraft"));   // kept by the platform
        assertTrue(included.contains("com.mojang"));      // appended from the scan config
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
        ProbeConfig.ScanConfig s = new ProbeConfig.ScanConfig(null, null, null, null, 0, (String) null);
        assertEquals(ProbeConfig.ScanConfig.ScanMode.SMART, s.mode());
        assertTrue(s.includePackages().isEmpty());
        assertEquals(0, s.maxDepth());
        ProbeConfig cfg = new ProbeConfig(true, null, s);
        assertEquals(".neko_probe", cfg.baseDir());

        ProbeConfig.ScanConfig upper = new ProbeConfig.ScanConfig(List.of(), List.of(), List.of(), List.of(), 5, "full");
        assertEquals(ProbeConfig.ScanConfig.ScanMode.FULL, upper.mode());
    }

    @Test
    void relevancePrefixUsesDotBoundary() {
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("net.minecraft"), List.of(), List.of(), List.of(), 5, "SMART"));
        // must not match on a "net.m" prefix: net.minecraftforgecraft does not belong to net.minecraft
        assertFalse(cfg.isRelevantClass("net.minecraftforgecraft.X", NEOFORGE_PLATFORM));
        assertTrue(cfg.isRelevantClass("net.minecraft.X", NEOFORGE_PLATFORM));
    }

    @Test
    void unknownScanModeFallsBackToSmart() {
        ProbeConfig.ScanConfig garbage = new ProbeConfig.ScanConfig(
                List.of(), List.of(), List.of(), List.of(), 5, "garbage-mode");
        assertEquals(ProbeConfig.ScanConfig.ScanMode.SMART, garbage.mode(), "未知 mode 取值应兜底为 SMART（等价旧默认行为）");
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
    void loadCreatesMissingProbeTomlWithDefaults(@TempDir Path tmp) throws Exception {
        // Like engine.toml, load writes the default probe.toml to disk when the file is missing
        // (autosave), so nekojs/config/probe.toml appears on first start and after the directory is
        // cleaned. The setup matches the real game: initFolders created nekojs/config/, probe.toml is absent.
        Path cfgFile = NekoJSPaths.fromGameDir(tmp).probeConfig();
        Files.createDirectories(cfgFile.getParent());
        assertFalse(Files.exists(cfgFile), "前置：文件不存在");

        ProbeConfig cfg = new ProbeConfigLoader().load(cfgFile);

        assertTrue(Files.exists(cfgFile), "load 后文件应被自动创建");
        assertTrue(cfg.enabled(), "默认 enabled = true");
        String content = Files.readString(cfgFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("enabled = true"), "默认值应写入文件，实际内容: " + content);
        assertTrue(content.contains("mode = \"SMART\""), "默认键应有值（TOML 表展开为 [scan]），实际内容: " + content);
        assertEquals(ProbeConfig.defaultConfig().languages(), cfg.languages(),
                "缺失 languages 表时应落入固定默认集");
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

    /* ================= Per-language config (languages.<lang>.backend / .outputDir) ================= */

    @Test
    void loaderParsesPerLanguageConfig(@TempDir Path tmp) throws Exception {
        Path cfgFile = NekoJSPaths.fromGameDir(tmp).probeConfig();
        Files.createDirectories(cfgFile.getParent());
        Files.writeString(cfgFile, """
                enabled = true

                [languages.typescript]
                backend = "builtin"
                outputDir = "tsx"

                [languages.python]
                outputDir = "py"
                """, StandardCharsets.UTF_8);

        ProbeConfig cfg = new ProbeConfigLoader().load(cfgFile);
        assertTrue(cfg.language("typescript").isPresent());
        assertEquals("builtin", cfg.language("typescript").get().backend());
        assertEquals("tsx", cfg.language("typescript").get().outputDir());
        assertTrue(cfg.language("python").isPresent());
        assertNull(cfg.language("python").get().backend(), "未写 backend 键应为 null");
        assertEquals("py", cfg.language("python").get().outputDir());
        assertTrue(cfg.language("java").isEmpty(), "未配置的语言应返回空 Optional");
    }

    @Test
    void loaderMissingLanguagesTableGetsFixedDefaults(@TempDir Path tmp) throws Exception {
        Path cfgFile = NekoJSPaths.fromGameDir(tmp).probeConfig();
        Files.createDirectories(cfgFile.getParent());
        Files.writeString(cfgFile, "enabled = true\n", StandardCharsets.UTF_8);

        ProbeConfig cfg = new ProbeConfigLoader().load(cfgFile);
        // No languages table: setup writes the fixed default set that defaultConfig has, with
        // outputDir = the language id (typescript, python).
        assertEquals(ProbeConfig.defaultConfig().languages(), cfg.languages(),
                "缺 languages 表时应落入固定默认集（与 defaultConfig 一致）");
        assertNull(cfg.language("typescript").get().backend());
        assertEquals("typescript", cfg.language("typescript").get().outputDir());
        assertEquals("python", cfg.language("python").get().outputDir());
    }

    @Test
    void threeArgConstructorKeepsLanguagesEmpty() {
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", ProbeConfig.ScanConfig.defaultScan());
        assertTrue(cfg.languages().isEmpty(), "3 参构造应等价 languages=空 Map");
        assertTrue(cfg.language("typescript").isEmpty());
    }

    @Test
    void backendOutputDirUsesPerLanguageOverride(@TempDir Path tmp) {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", ProbeConfig.ScanConfig.defaultScan(),
                Map.of("typescript", new ProbeConfig.LanguageConfig("builtin", "tsx")));
        Path dir = new TypeScriptProbeBackend().outputDir(paths, cfg);
        assertEquals(paths.gameDir().resolve(".neko_probe").resolve("tsx"), dir,
                "languages.typescript.outputDir=tsx 应覆盖默认输出子目录");
    }

    @Test
    void backendOutputDirFallsBackToLanguageId(@TempDir Path tmp) {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path dir = new TypeScriptProbeBackend().outputDir(paths, ProbeConfig.defaultConfig());
        assertEquals(paths.gameDir().resolve(".neko_probe").resolve("typescript"), dir,
                "无语言级覆盖时输出目录 = baseDir/<languageId>（旧行为）");
    }

    /* ================= re: regex package rules ================= */

    @Test
    void regexIncludeRuleMatchesByFullMatch() {
        ProbeConfig cfg = withScan(new ProbeConfig.ScanConfig(
                List.of("re:com\\.acme\\..*"), List.of(), List.of(), List.of(), 5, "SMART"));
        assertTrue(cfg.isRelevantClass("com.acme.api.Foo", NEOFORGE_PLATFORM));
        assertTrue(cfg.isRelevantClass("com.acme.Foo", NEOFORGE_PLATFORM));
        // full-match regex: an accidental prefix extension (com.acmeextra) must not match
        assertFalse(cfg.isRelevantClass("com.acmeextra.Foo", NEOFORGE_PLATFORM));
        // override: once the regex whitelist is non-empty, the default whitelist (java and others) stops applying
        assertFalse(cfg.isRelevantClass("java.lang.String", NEOFORGE_PLATFORM));
    }

    @Test
    void regexExcludeWinsOverInclude() {
        // the default whitelist contains java; excluding java.lang.reflect with a regex disables that whole subpackage
        ProbeConfig cfg = withScan(new ProbeConfig.ScanConfig(
                List.of(), List.of(), List.of("re:java\\.lang\\.reflect\\..*"), List.of(), 5, "SMART"));
        assertTrue(cfg.isExcluded("java.lang.reflect.Field"));
        assertFalse(cfg.isExcluded("java.lang.String"));
        assertFalse(cfg.isRelevantClass("java.lang.reflect.Field", NEOFORGE_PLATFORM),
                "排除规则优先于白名单命中");
        assertTrue(cfg.isRelevantClass("java.lang.String", NEOFORGE_PLATFORM));
    }

    @Test
    void invalidRegexRuleWarnsOnceAndNeverMatches() {
        ProbeConfig cfg = withScan(new ProbeConfig.ScanConfig(
                List.of(), List.of(), List.of("re:***"), List.of(), 5, "SMART"));
        // an invalid regex does not throw and never matches
        assertFalse(cfg.isExcluded("java.lang.String"));
        assertTrue(cfg.isRelevantClass("java.lang.String", NEOFORGE_PLATFORM));
        // repeated calls go through the cache path and are just as safe
        assertFalse(cfg.isExcluded("net.minecraft.X"));
    }

    @Test
    void forcedPackagesSupportRegexEntries() {
        ProbeConfig cfg = withScan(new ProbeConfig.ScanConfig(
                List.of(), List.of(), List.of(),
                List.of("minecraft", "re:io\\.github\\.(?:alpha|beta)mod\\..*"), 5, "SMART"));
        Set<String> forced = cfg.forcedPackages();
        assertTrue(forced.contains("net.minecraft"), "mod id 解析为字面前缀");
        assertTrue(forced.contains("re:io\\.github\\.(?:alpha|beta)mod\\..*"), "re: 条目原样保留");
        assertTrue(ProbeConfig.matchesPackageRule("re:io\\.github\\.(?:alpha|beta)mod\\..*", "io.github.alphamod.item.X"));
        assertFalse(ProbeConfig.matchesPackageRule("re:io\\.github\\.(?:alpha|beta)mod\\..*", "io.github.gammamod.item.X"));
    }

    @Test
    void literalPrefixSemanticsUnchanged() {
        // regression: entries without the re: prefix still use startsWith(pkg + "."), so the package itself does not match
        assertTrue(ProbeConfig.matchesPackageRule("java.util", "java.util.List"));
        assertFalse(ProbeConfig.matchesPackageRule("java.util", "java.utilx.X"));
        assertFalse(ProbeConfig.matchesPackageRule("java.util", "java.util"));
    }

    private ProbeConfig withScan(ProbeConfig.ScanConfig scan) {
        return new ProbeConfig(true, ".neko_probe", scan);
    }
}
