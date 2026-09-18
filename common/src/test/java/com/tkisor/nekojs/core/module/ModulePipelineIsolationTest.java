package com.tkisor.nekojs.core.module;

import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.compiler.NekoSourceMapBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票据 11 AC10：Preparation 与 Resolution/Cache 不创建 Graal Context、不决定 HostAccess、
 * 不读取 Minecraft/loader；Context、HostAccess、bindings 与执行关闭继续由
 * Script Execution Environment 负责（本约束不改变 common 允许 GraalJS 的既有规则）。
 *
 * <p>负断言（准备/解析层源码级扫描）+ 正断言（执行层仍承担 Context/HostAccess）双面验证。
 * 扫描口径取精确 token（{@code Context.newBuilder} 等构造点、{@code HostAccess.} 决策点、
 * 平台包 import），避免注释提及造成误报；import 行全行匹配。
 */
class ModulePipelineIsolationTest {

    /** Preparation 纯层：不得出现 Context/HostAccess/平台 import（含注入持有）。 */
    private static final List<String> PREPARATION_FILES = List.of(
            "core/compiler/NekoCompilationPipeline.java",
            "core/compiler/NekoSourceMapBuilder.java",
            "core/module/NekoPreparedModule.java",
            "core/module/NekoModulePipeline.java",
            "core/module/NekoModulePipelineCache.java",
            "core/module/NekoTrustApprovedSource.java",
            "core/module/NekoModuleError.java",
            "core/compiler/NekoLegacyLanguagePlugin.java");

    /** Resolution/Cache 纯层：同上（host 与 ESM lifecycle 属执行委托面，另行断言）。 */
    private static final List<String> RESOLUTION_FILES = List.of(
            "core/module/NekoModuleResolver.java",
            "core/module/NekoModuleDependencyGraph.java",
            "core/module/ModuleReloadCoordinator.java",
            "core/module/esm/NekoEsmLinker.java",
            "core/module/esm/NekoNativeEsmSourceRewriter.java");

    /** 执行委托面：持有注入的 Context，但不得创建 Context、不得决定 HostAccess。 */
    private static final List<String> DELEGATION_FILES = List.of(
            "core/module/NekoScriptModuleLoaderHost.java",
            "core/module/EsmModuleLifecycle.java");

    @Test
    void preparationCreatesNoContextDecidesNoHostAccessAndReadsNoPlatform() throws IOException {
        List<String> violations = scan(PREPARATION_FILES, true);
        assertTrue(violations.isEmpty(), "Preparation 不得创建 Context/决定 HostAccess/读平台:\n"
                + String.join("\n", violations));
    }

    @Test
    void sourceMapBuilderUsesOnlyTheSuppliedPath() {
        Path file = Path.of(System.getProperty("java.io.tmpdir"), "nekojs-source-map", "absolute.js")
                .toAbsolutePath().normalize();

        String map = NekoSourceMapBuilder.identity(file, "const value = 1;\n", "const value = 1;\n");

        String source = JsonParser.parseString(map).getAsJsonObject()
                .getAsJsonArray("sources").get(0).getAsString();
        assertTrue(source.equals(file.toString().replace('\\', '/')),
                "source-map display name must be derived from the supplied path, not a platform root");
    }

    @Test
    void resolutionCacheCreatesNoContextDecidesNoHostAccessAndReadsNoPlatform() throws IOException {
        List<String> violations = scan(RESOLUTION_FILES, true);
        assertTrue(violations.isEmpty(), "Resolution/Cache 不得创建 Context/决定 HostAccess/读平台:\n"
                + String.join("\n", violations));
    }

    @Test
    void delegationHoldsInjectedContextButCreatesNoneAndDecidesNoHostAccess() throws IOException {
        List<String> violations = scan(DELEGATION_FILES, false);
        assertTrue(violations.isEmpty(), "执行委托面不得创建 Context/决定 HostAccess/读平台:\n"
                + String.join("\n", violations));
    }

    /** 纯层签名级断言：字段/方法/构造器签名一律不出现 Context/HostAccess 类型。 */
    @Test
    void pureSignaturesHoldNoContextOrHostAccess() throws Exception {
        Class<?> contextType = Class.forName("graal.graalvm.polyglot.Context");
        Class<?> hostAccessType = Class.forName("graal.graalvm.polyglot.HostAccess");
        List<String> pureClasses = List.of(
                "com.tkisor.nekojs.core.compiler.NekoCompilationPipeline",
                "com.tkisor.nekojs.core.compiler.NekoSourceMapBuilder",
                "com.tkisor.nekojs.core.module.NekoPreparedModule",
                "com.tkisor.nekojs.core.module.NekoModulePipeline",
                "com.tkisor.nekojs.core.module.NekoModulePipelineCache",
                "com.tkisor.nekojs.core.module.NekoTrustApprovedSource",
                "com.tkisor.nekojs.core.module.NekoModuleError",
                "com.tkisor.nekojs.core.compiler.NekoLegacyLanguagePlugin",
                "com.tkisor.nekojs.core.module.NekoModuleResolver",
                "com.tkisor.nekojs.core.module.NekoModuleDependencyGraph",
                "com.tkisor.nekojs.core.module.ModuleReloadCoordinator",
                "com.tkisor.nekojs.core.module.esm.NekoEsmLinker",
                "com.tkisor.nekojs.core.module.esm.NekoNativeEsmSourceRewriter");
        List<String> violations = new ArrayList<>();
        for (String className : pureClasses) {
            Class<?> type = Class.forName(className);
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (!field.isSynthetic() && hits(field.getType(), contextType, hostAccessType)) {
                    violations.add(className + " field " + field.getName() + ": " + field.getType());
                }
            }
            for (java.lang.reflect.Constructor<?> ctor : type.getDeclaredConstructors()) {
                for (Class<?> parameter : ctor.getParameterTypes()) {
                    if (hits(parameter, contextType, hostAccessType)) {
                        violations.add(className + " ctor param: " + parameter);
                    }
                }
            }
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (hits(method.getReturnType(), contextType, hostAccessType)) {
                    violations.add(className + "#" + method.getName() + " return: " + method.getReturnType());
                }
                for (Class<?> parameter : method.getParameterTypes()) {
                    if (hits(parameter, contextType, hostAccessType)) {
                        violations.add(className + "#" + method.getName() + " param: " + parameter);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "纯层签名不得出现 Context/HostAccess:\n" + String.join("\n", violations));
    }

    private static boolean hits(Class<?> candidate, Class<?>... forbidden) {
        for (Class<?> ban : forbidden) {
            if (candidate == ban) {
                return true;
            }
        }
        return false;
    }

    @Test
    void executionEnvironmentStillOwnsContextAndHostAccess() throws IOException {
        // 正断言：Context 创建与 HostAccess 决策仍在 Script Execution Environment。
        String sandboxFactory = read("core/NekoSandboxFactory.java");
        assertTrue(sandboxFactory.contains("Context.newBuilder(\"js\")"),
                "执行环境必须继续创建 Graal Context");
        String sharedHostAccess = read("core/NekoSharedHostAccess.java");
        assertTrue(sharedHostAccess.contains("HostAccess.newBuilder") || sharedHostAccess.contains("HostAccess.ALL"),
                "执行环境必须继续决定 HostAccess");
    }

    /**
     * @param forbidContextHolding 纯层额外禁止持有 Context 类型（import/字段/签名一律不得出现）。
     */
    private static List<String> scan(List<String> files, boolean forbidContextHolding) throws IOException {
        List<String> violations = new ArrayList<>();
        for (String file : files) {
            String source = stripCommentsAndStrings(read(file));
            for (String line : source.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                // import 行：全行精确匹配平台/Graal-Context 禁止项。
                if (trimmed.startsWith("import ")) {
                    if (trimmed.contains("net.minecraft.")
                            || trimmed.contains("net.minecraftforge.")
                            || trimmed.contains("net.neoforged.")
                            || trimmed.contains("net.fabricmc.")
                            || trimmed.contains("com.mojang.")
                            || (forbidContextHolding && trimmed.contains("graalvm.polyglot.Context;"))
                            || trimmed.contains("graalvm.polyglot.HostAccess")) {
                        violations.add(file + ": forbidden import: " + trimmed);
                    }
                    continue;
                }
                // 代码行：构造/决策精确 token；Context 持有按词边界判定
                //（RewriteContext 之类的内类名不算；注释与字符串已剥离）。
                if (line.contains("Context.newBuilder") || line.contains("Context.create")
                        || line.contains("HostAccess.")
                        || line.contains("net.minecraft.") || line.contains("net.minecraftforge.")
                        || line.contains("net.neoforged.") || line.contains("net.fabricmc.")
                        || (forbidContextHolding && hasContextWord(line))) {
                    violations.add(file + ": forbidden usage: " + trimmed);
                }
            }
        }
        return violations;
    }

    private static boolean hasContextWord(String line) {
        int index = 0;
        while ((index = line.indexOf("Context", index)) >= 0) {
            boolean leftOk = index == 0 || !Character.isJavaIdentifierPart(line.charAt(index - 1));
            int end = index + "Context".length();
            boolean rightOk = end >= line.length() || !Character.isJavaIdentifierPart(line.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            index = end;
        }
        return false;
    }

    /** 剥离行/块注释与字符串字面量：注释里的“不创建 Context”式表述不得计入。 */
    private static String stripCommentsAndStrings(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        boolean lineComment = false;
        boolean blockComment = false;
        char stringQuote = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (lineComment) {
                if (c == '\n') {
                    lineComment = false;
                    out.append(c);
                }
                i++;
                continue;
            }
            if (blockComment) {
                if (c == '\n') {
                    out.append(c);
                }
                if (c == '*' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                    blockComment = false;
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }
            if (stringQuote != 0) {
                if (c == '\\' && i + 1 < source.length()) {
                    i += 2;
                    continue;
                }
                if (c == stringQuote) {
                    stringQuote = 0;
                }
                i++;
                continue;
            }
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                lineComment = true;
                i += 2;
                continue;
            }
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                blockComment = true;
                i += 2;
                continue;
            }
            if (c == '"' || c == '\'') {
                stringQuote = c;
                i++;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String read(String relative) throws IOException {
        Path base = Path.of("src/main/java/com/tkisor/nekojs");
        if (!Files.isDirectory(base)) {
            base = Path.of("common/src/main/java/com/tkisor/nekojs");
        }
        Path file = base.resolve(relative);
        assertTrue(Files.isRegularFile(file), "source file must exist: " + file.toAbsolutePath());
        return Files.readString(file);
    }
}
