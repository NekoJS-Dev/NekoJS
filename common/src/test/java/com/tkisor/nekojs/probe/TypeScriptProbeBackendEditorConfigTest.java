package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TypeScriptProbeBackend#contributeEditorConfig} 用 recording 贡献器断言：注入的路径别名
 * 指向 backend 真实的 {@code typescript/} 输出目录（修复既有 stale 路径）。
 */
class TypeScriptProbeBackendEditorConfigTest {

    /** ScriptType 静态初始化需要 Platform（NekoJSPaths.get），独立跑该类也必须先初始化。 */
    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void contribute_pointsAllAliasesAtTypescriptOutput(@TempDir Path temp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(temp);
        Path tsOut = temp.resolve(".neko_probe/typescript");
        Files.createDirectories(tsOut);
        ProbeContext ctx = new ProbeContext.Of(emptySnapshot(), List.of(),
                new ProbeConfig(true, ".neko_probe",
                        new ProbeConfig.ScanConfig(List.of(), List.of(), List.of(), List.of(), 5, "SMART")),
                paths, "typescript", tsOut, null);

        Recording rec = new Recording();
        new TypeScriptProbeBackend().contributeEditorConfig(rec, ctx);

        // 4 脚本目录 + 1 probe 目录
        assertEquals(5, rec.jsCalls.size(), "should contribute to 4 script dirs + probe dir");

        var serverCall = rec.jsCalls.stream()
                .filter(e -> posix(e.getKey()).endsWith("server_scripts/jsconfig.json"))
                .findFirst().orElseThrow(() -> new AssertionError("server_scripts contribution missing"));
        Map<String, List<String>> aliases = serverCall.getValue();

        // server_scripts → .neko_probe/typescript 的相对路径 = ../../.neko_probe/typescript
        assertEquals(List.of("../../.neko_probe/typescript/@package/*"), aliases.get("java:*"),
                "java:* must point at typescript/ output (stale fix)");
        assertEquals(List.of("../../.neko_probe/typescript/@side-only/server"), aliases.get("@side-only/server"));
        assertEquals(List.of("../../.neko_probe/typescript/@side-only/server/*"), aliases.get("@side-only/server/*"));
        assertEquals(List.of("../../.neko_probe/typescript/@special"), aliases.get("@special"));
        assertEquals(List.of("../../.neko_probe/typescript/@special/*"), aliases.get("@special/*"));
        // 仅本 env 的 @side-only（不泄露其他 side）
        assertNull(aliases.get("@side-only/client"), "must not inject other env's side-only");

        // server_scripts 的 include：脚本文件 globs（jsconfig 项目必须包含脚本自身，否则 IDE 无补全）
        // + probe 声明 globs（相对写法照抄 WorkspaceGenerator，base 换成 tsOut）
        var serverIncludeCall = rec.includeCalls.stream()
                .filter(e -> posix(e.getKey()).endsWith("server_scripts/jsconfig.json"))
                .findFirst().orElseThrow(() -> new AssertionError("server_scripts include contribution missing"));
        assertEquals(List.of(
                "./**/*.js", "./**/*.mjs", "./**/*.cjs",
                "./**/*.ts", "./**/*.jsx", "./**/*.tsx",
                "../../.neko_probe/typescript/@package/**/*.d.ts",
                "../../.neko_probe/typescript/@manual/**/*.d.ts",
                "../../.neko_probe/typescript/@side-only/server/**/*.d.ts",
                "../../.neko_probe/typescript/@nekojs/managed/server/**/*.d.ts"), serverIncludeCall.getValue(),
                "server_scripts include must cover script files and typescript/ output");

        // server_scripts 的 typeRoots：@package 指向 tsOut，node_modules 相对写法不变
        var serverTypeRootCall = rec.typeRootCalls.stream()
                .filter(e -> posix(e.getKey()).endsWith("server_scripts/jsconfig.json"))
                .findFirst().orElseThrow(() -> new AssertionError("server_scripts typeRoots contribution missing"));
        assertEquals(List.of("../../.neko_probe/typescript/@package", "../node_modules/@types"),
                serverTypeRootCall.getValue());

        // include/typeRoots 只贡献给脚本目录（4 个），不涉及 probe 目录自身的 jsconfig
        assertEquals(4, rec.includeCalls.size(), "include contributed to 4 script dirs only");
        assertEquals(4, rec.typeRootCalls.size(), "typeRoots contributed to 4 script dirs only");

        // probe 目录的 jsconfig：相对路径 = "./typescript"（未设 baseUrl 时 paths 映射值须为相对路径）
        var probeCall = rec.jsCalls.stream()
                .filter(e -> posix(e.getKey()).endsWith(".neko_probe/jsconfig.json"))
                .findFirst().orElseThrow();
        Map<String, List<String>> probeAliases = probeCall.getValue();
        assertEquals(List.of("./typescript/@package/*"), probeAliases.get("java:*"));
        // probe 目录含所有 side
        assertNotNull(probeAliases.get("@side-only/server"));
        assertNotNull(probeAliases.get("@side-only/client"));
    }

    private static String posix(Path p) {
        return p.toString().replace('\\', '/');
    }

    private static NekoScriptCatalogSnapshot emptySnapshot() {
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, java.util.Map.of(), List.of());
    }

    /** 记录式贡献器：捕获所有 merge 调用，不触碰磁盘。 */
    static final class Recording implements EditorConfigContributor {
        final List<Map.Entry<Path, Map<String, List<String>>>> jsCalls = new ArrayList<>();
        final List<Map.Entry<Path, List<String>>> includeCalls = new ArrayList<>();
        final List<Map.Entry<Path, List<String>>> typeRootCalls = new ArrayList<>();
        final List<Map.Entry<Path, List<String>>> pyCalls = new ArrayList<>();

        @Override
        public void mergeJsConfigPaths(Path file, Map<String, List<String>> aliases) {
            jsCalls.add(new AbstractMap.SimpleEntry<>(file, aliases));
        }

        @Override
        public void mergeJsConfigIncludes(Path file, List<String> includeGlobs) {
            includeCalls.add(new AbstractMap.SimpleEntry<>(file, includeGlobs));
        }

        @Override
        public void mergeJsConfigTypeRoots(Path file, List<String> typeRoots) {
            typeRootCalls.add(new AbstractMap.SimpleEntry<>(file, typeRoots));
        }

        @Override
        public void mergePyrightExtraPaths(Path file, List<String> extraPaths) {
            pyCalls.add(new AbstractMap.SimpleEntry<>(file, extraPaths));
        }

        @Override
        public void mergeVscodeSnippets(Path file, List<com.tkisor.nekojs.probe.events.Snippet> snippets) {
            // TS editor-config 测试不涉及片段；no-op
        }
    }
}
