package com.tkisor.nekojs.probe;

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
        // 不应被 "net.m" 前缀误命中：net.minecraftforgecraft 不该匹配 net.minecraft
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
        // 与 engine.toml 一样，probe.toml 缺失时 load 应自动落盘默认值（autosave），
        // 保证首次启动/清理目录后 nekojs/config/probe.toml 自动出现。
        // 前置条件与真实游戏一致：initFolders 已建好 nekojs/config/ 目录，但 probe.toml 缺失。
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

    /* ================= B3：per-language 配置（languages.<lang>.backend / .outputDir） ================= */

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
        // 缺 languages 表 → setup 写入固定默认集（typescript/python 的 outputDir = 语言 id），与 defaultConfig 一致
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

    /* ================= re: 正则包规则 ================= */

    @Test
    void regexIncludeRuleMatchesByFullMatch() {
        ProbeConfig cfg = withScan(new ProbeConfig.ScanConfig(
                List.of("re:com\\.acme\\..*"), List.of(), List.of(), List.of(), 5, "SMART"));
        assertTrue(cfg.isRelevantClass("com.acme.api.Foo", NEOFORGE_PLATFORM));
        assertTrue(cfg.isRelevantClass("com.acme.Foo", NEOFORGE_PLATFORM));
        // 正则全匹配的精确性：前缀意外延续（com.acmeextra）不应命中
        assertFalse(cfg.isRelevantClass("com.acmeextra.Foo", NEOFORGE_PLATFORM));
        // 覆盖语义：正则白名单非空时默认白名单（java 等）不再生效
        assertFalse(cfg.isRelevantClass("java.lang.String", NEOFORGE_PLATFORM));
    }

    @Test
    void regexExcludeWinsOverInclude() {
        // 默认白名单含 java；正则排除掉 java.lang.reflect 后，该子包整体失活
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
        // 非法正则不抛异常、视为永不命中
        assertFalse(cfg.isExcluded("java.lang.String"));
        assertTrue(cfg.isRelevantClass("java.lang.String", NEOFORGE_PLATFORM));
        // 重复调用命中缓存路径，同样安全
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
        // 回归：无 re: 前缀的条目仍为 startsWith(pkg + ".")，package 自身（无子包）不命中
        assertTrue(ProbeConfig.matchesPackageRule("java.util", "java.util.List"));
        assertFalse(ProbeConfig.matchesPackageRule("java.util", "java.utilx.X"));
        assertFalse(ProbeConfig.matchesPackageRule("java.util", "java.util"));
    }

    private ProbeConfig withScan(ProbeConfig.ScanConfig scan) {
        return new ProbeConfig(true, ".neko_probe", scan);
    }
}
