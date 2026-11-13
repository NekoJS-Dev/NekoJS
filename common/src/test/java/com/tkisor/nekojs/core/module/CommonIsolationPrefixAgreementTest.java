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
 * The rule that common imports no Minecraft or loader class is enforced in three places, and all
 * three prefix lists must hold the same set. Running only one of the gates is weaker than the
 * combined check when the lists disagree:
 *
 * <ol>
 *   <li>{@code checkCommonIsolation.forbiddenPrefixes} in {@code common/build.gradle}
 *       (:common:check)</li>
 *   <li>{@code mcLoaderImport} in {@code guardLint} in {@code stonecutter.gradle.kts}
 *       (:guardLint)</li>
 *   <li>the forbidden list in {@code scan()} in {@link ModulePipelineIsolationTest} (the test in
 *       this package)</li>
 * </ol>
 *
 * <p>The lists did not agree: the Gradle gate had 3 prefixes (missing {@code net.fabricmc} and
 * {@code com.mojang}), the second had 4 (missing {@code com.mojang}) and the third had 5, so a new
 * file in common that imported only {@code net.fabricmc} passed {@code :common:check}.
 *
 * <p>This test parses the prefix list out of the actual text of all three files and asserts they
 * are the same set, so narrowing any one of them fails.
 */
class CommonIsolationPrefixAgreementTest {

    /** The single source of truth for the expected set: the result parsed from each of the three
    files must equal it. Sorted alphabetically to match the normalization the parsers apply. */
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

    // ---- Text parsing for the three enforcement points ----

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
        // Locate the declaration by literal text, not by a regex: the text being matched is full of
        // escapes, and nesting another regex inside it is easy to get wrong and fails silently. Cutting
        // out the first "(...)" group is enough to get the prefix list.
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
