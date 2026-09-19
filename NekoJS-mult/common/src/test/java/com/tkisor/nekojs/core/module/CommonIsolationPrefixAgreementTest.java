package com.tkisor.nekojs.core.module;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 33 review F1：common 的"零 MC/loader import"不变量有**三个强制点**，
 * 它们的前缀表必须同集合，否则"只跑其中一个门禁"会比聚合门禁弱：
 *
 * <ol>
 *   <li>{@code common/build.gradle} 的 {@code checkCommonIsolation.forbiddenPrefixes}（:common:check）</li>
 *   <li>{@code stonecutter.gradle.kts} 的 {@code guardLint} {@code mcLoaderImport}（:guardLint）</li>
 *   <li>{@link ModulePipelineIsolationTest} 的 {@code scan()} 禁止项（本包测试）</li>
 * </ol>
 *
 * <p>修订前 ① 只有 3 个前缀（漏 {@code net.fabricmc} / {@code com.mojang}），
 * ② 只有 4 个（漏 {@code com.mojang}），③ 有 5 个 —— 即 common 里新增一个只 import
 * {@code net.fabricmc} 的文件时，只跑 {@code :common:check} 不会红。
 *
 * <p>本测试从三个文件的**实际文本**解析前缀表并断言同集合，因此任何一处再被改窄都会红。
 */
class CommonIsolationPrefixAgreementTest {

    /** 期望集合的唯一事实源（三处解析结果都必须等于它）；按字典序排列以匹配解析结果的归一化。 */
    private static final List<String> EXPECTED = List.of(
            "com.mojang", "net.fabricmc", "net.minecraft", "net.minecraftforge", "net.neoforged");

    @Test
    void allThreeIsolationEnforcementPointsUseTheSamePrefixSet() throws IOException {
        List<String> fromGradle = parseGradlePrefixes();
        List<String> fromGuardLint = parseGuardLintPrefixes();
        List<String> fromIsolationTest = parseIsolationTestPrefixes();

        assertEquals(EXPECTED, fromGradle,
                "common/build.gradle 的 checkCommonIsolation 前缀表与期望集合不一致");
        assertEquals(EXPECTED, fromGuardLint,
                "guardLint 的 mcLoaderImport 前缀表与期望集合不一致");
        assertEquals(EXPECTED, fromIsolationTest,
                "ModulePipelineIsolationTest 的禁止前缀集合与期望集合不一致");
    }

    @Test
    void checkCommonIsolationIsStillWiredIntoCheck() throws IOException {
        String gradle = read("common/build.gradle");
        assertTrue(gradle.contains("check.dependsOn checkCommonIsolation"),
                ":common:check 必须仍然挂上 checkCommonIsolation（否则隔离门禁静默失效）");
    }

    // ---- 三个强制点的文本解析 ----

    private static List<String> parseGradlePrefixes() throws IOException {
        String text = read("common/build.gradle");
        Matcher block = Pattern.compile("def forbiddenPrefixes = \\[(.*?)\\]", Pattern.DOTALL).matcher(text);
        assertTrue(block.find(), "common/build.gradle 里找不到 forbiddenPrefixes 块");
        List<String> found = new ArrayList<>();
        Matcher entry = Pattern.compile("'([^']+)'").matcher(block.group(1));
        while (entry.find()) found.add(entry.group(1));
        assertTrue(!found.isEmpty(), "forbiddenPrefixes 解析为空——gate 输入失效");
        return found.stream().sorted().toList();
    }

    private static List<String> parseGuardLintPrefixes() throws IOException {
        String text = read("stonecutter.gradle.kts");
        // 用字面量定位而不是正则：待匹配文本自身含大量转义，正则里再嵌一层极易写错
        // （第一版就在这里静默匹配失败）。这里只切出第一对 "(...)" 里的前缀组。
        int marker = text.indexOf("val mcLoaderImport = Regex(");
        assertTrue(marker >= 0, "stonecutter.gradle.kts 里找不到 mcLoaderImport 声明");
        int open = text.indexOf("(", text.indexOf("\"\"\"", marker));
        int close = text.indexOf(")", open);
        assertTrue(open > 0 && close > open, "mcLoaderImport 的前缀组括号不完整");
        String group = text.substring(open + 1, close);
        List<String> found = new ArrayList<>();
        for (String piece : group.split("\\|")) {
            String cleaned = piece.trim().replace("\\", "");
            if (!cleaned.isEmpty() && !found.contains(cleaned)) found.add(cleaned);
        }
        assertTrue(!found.isEmpty(), "mcLoaderImport 前缀组解析为空——gate 输入失效");
        return found.stream().sorted().toList();
    }

    private static List<String> parseIsolationTestPrefixes() throws IOException {
        String text = read("common/src/test/java/com/tkisor/nekojs/core/module/ModulePipelineIsolationTest.java");
        List<String> found = new ArrayList<>();
        Matcher entry = Pattern.compile("(?:trimmed|line)\\.contains\\(\"([a-z][a-z0-9.]*\\.)\"\\)").matcher(text);
        while (entry.find()) {
            String prefix = entry.group(1);
            if (prefix.endsWith(".")) prefix = prefix.substring(0, prefix.length() - 1);
            if (!found.contains(prefix)) found.add(prefix);
        }
        assertTrue(!found.isEmpty(),
                "ModulePipelineIsolationTest 的禁止前缀解析为空——解析口径失效（不是断言放宽）");
        return found.stream().sorted().toList();
    }

    private static String read(String relative) throws IOException {
        Path base = Path.of("").toAbsolutePath();
        Path root = Files.isRegularFile(base.resolve("settings.gradle.kts")) ? base : base.getParent();
        Path file = root.resolve(relative);
        assertTrue(Files.isRegularFile(file), "源文件必须存在: " + file);
        return Files.readString(file);
    }
}
