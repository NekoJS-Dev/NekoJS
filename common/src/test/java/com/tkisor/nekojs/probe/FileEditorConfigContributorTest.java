package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.probe.events.Snippet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Merge behaviour of {@link FileEditorConfigContributor}: keys owned by the probe (the paths keys,
 * include, typeRoots and the JSX runtime keys) are replaced with the latest values, while user and
 * unknown keys are kept. A fresh pyrightconfig gets the defaults, and existing entries are deduped.
 */
class FileEditorConfigContributorTest {

    @Test
    void mergeJsConfigPaths_replacesProbeKeys_preservesUserKeys(@TempDir Path temp) throws IOException {
        Path jsconfig = temp.resolve("jsconfig.json");
        Files.writeString(jsconfig, """
                {
                  "compilerOptions": {
                    "target": "ESNext",
                    "paths": {
                      "java:*": ["../../.neko_probe/@package/*"],
                      "my-alias": ["./my/*"]
                    }
                  },
                  "myExtra": 42
                }""");

        new FileEditorConfigContributor().mergeJsConfigPaths(jsconfig,
                Map.of("java:*", List.of("../../.neko_probe/typescript/@package/*")));

        String out = Files.readString(jsconfig);
        // The probe keys are replaced with the new values
        assertTrue(out.contains("../../.neko_probe/typescript/@package/*"), out);
        assertFalse(out.contains("../../.neko_probe/@package/*"),
                "stale probe value must be replaced: " + out);
        // The user alias, the other compilerOptions keys and the unknown top-level key all survive
        assertTrue(out.contains("my-alias"), out);
        assertTrue(out.contains("ESNext"), out);
        assertTrue(out.contains("myExtra"), out);
    }

    @Test
    void mergeJsConfigPaths_freshFile_createsStructure(@TempDir Path temp) throws IOException {
        Path jsconfig = temp.resolve("a/b/jsconfig.json"); // The file does not exist and its parent directory is missing
        new FileEditorConfigContributor().mergeJsConfigPaths(jsconfig,
                Map.of("java:*", List.of("typescript/@package/*"), "@special/*", List.of("typescript/@special/*")));

        String out = Files.readString(jsconfig);
        assertTrue(out.contains("compilerOptions"), out);
        assertTrue(out.contains("typescript/@package/*"), out);
        assertTrue(out.contains("@special/*"), out);
    }

    @Test
    void mergeJsConfigIncludes_refreshesProbeEntries_preservesUserEntries(@TempDir Path temp) throws IOException {
        Path jsconfig = temp.resolve("jsconfig.json");
        Files.writeString(jsconfig, """
                {
                  "include": ["src", "../../.neko_probe/@package/**/*.d.ts"],
                  "compilerOptions": {
                    "target": "ESNext"
                  },
                  "myExtra": 42
                }""");

        new FileEditorConfigContributor().mergeJsConfigIncludes(jsconfig,
                List.of("../../.neko_probe/typescript/@package/**/*.d.ts",
                        "../../.neko_probe/typescript/@manual/**/*.d.ts"));

        String out = Files.readString(jsconfig);
        // Probe entries are refreshed and user entries are kept
        assertTrue(out.contains("../../.neko_probe/typescript/@package/**/*.d.ts"), out);
        assertTrue(out.contains("../../.neko_probe/typescript/@manual/**/*.d.ts"), out);
        assertFalse(out.contains("../../.neko_probe/@package"), "stale probe value must be replaced: " + out);
        assertTrue(out.contains("\"src\""), "user include entry must survive (refresh-managed merge): " + out);
        // The other keys (compilerOptions and the unknown top-level key) are kept
        assertTrue(out.contains("ESNext"), out);
        assertTrue(out.contains("myExtra"), out);
    }

    @Test
    void mergeJsConfigTypeRoots_refreshesProbeEntries_preservesUserEntries(@TempDir Path temp) throws IOException {
        Path jsconfig = temp.resolve("jsconfig.json");
        Files.writeString(jsconfig, """
                {
                  "compilerOptions": {
                    "target": "ESNext",
                    "typeRoots": ["../../.neko_probe/@package", "./my-types"]
                  },
                  "myExtra": 42
                }""");

        new FileEditorConfigContributor().mergeJsConfigTypeRoots(jsconfig,
                List.of("../../.neko_probe/typescript/@package", "../node_modules/@types"));

        String out = Files.readString(jsconfig);
        // Probe entries are refreshed and user entries are kept
        assertTrue(out.contains("../../.neko_probe/typescript/@package"), out);
        assertTrue(out.contains("../node_modules/@types"), out);
        assertFalse(out.contains("../../.neko_probe/@package"),
                "stale probe value must be replaced: " + out);
        assertTrue(out.contains("./my-types"), "user typeRoots entry must survive (refresh-managed merge): " + out);
        // The other keys are kept
        assertTrue(out.contains("ESNext"), out);
        assertTrue(out.contains("myExtra"), out);
    }

    @Test
    void mergeJsConfig_automaticRuntimeSwitchesJsxKeysAndPreservesUserKeys(@TempDir Path temp) throws IOException {
        Path jsconfig = temp.resolve("jsconfig.json");
        Files.writeString(jsconfig, """
                {
                  "compilerOptions": {
                    "target": "ESNext",
                    "jsx": "react",
                    "jsxFactory": "__nekoJsxFactory",
                    "jsxFragmentFactory": "__nekoJsxFragment",
                    "typeRoots": ["./my-types"]
                  },
                  "myExtra": 42
                }""");

        withJsxAutomaticRuntime(true, () ->
                new FileEditorConfigContributor().mergeJsConfigTypeRoots(jsconfig,
                        List.of("../../.neko_probe/typescript/@package")));

        String out = Files.readString(jsconfig);
        // The jsx runtime keys are set to the TypeScript automatic runtime that the engine configures
        assertTrue(out.contains("\"jsx\": \"react-jsx\""), out);
        assertTrue(out.contains("\"jsxImportSource\": \"nekojs\""), out);
        assertFalse(out.contains("jsxFactory"), "classic factory keys must be removed: " + out);
        assertFalse(out.contains("jsxFragmentFactory"), "classic factory keys must be removed: " + out);
        // User keys are kept
        assertTrue(out.contains("ESNext"), out);
        assertTrue(out.contains("myExtra"), out);
    }

    @Test
    void mergeJsConfig_classicRuntimeRestoresClassicJsxKeys(@TempDir Path temp) throws IOException {
        Path jsconfig = temp.resolve("jsconfig.json");
        // The existing jsconfig is still in automatic runtime mode, while the user has turned the switch
        // off in engine.toml
        Files.writeString(jsconfig, """
                {
                  "compilerOptions": {
                    "jsx": "react-jsx",
                    "jsxImportSource": "nekojs",
                    "paths": { "java:*": ["../../.neko_probe/@package/*"] }
                  }
                }""");

        withJsxAutomaticRuntime(false, () ->
                new FileEditorConfigContributor().mergeJsConfigPaths(jsconfig,
                        Map.of("java:*", List.of("../../.neko_probe/typescript/@package/*"))));

        String out = Files.readString(jsconfig);
        // The jsx runtime keys are set back to the classic global factory mode that the engine configures
        assertTrue(out.contains("\"jsx\": \"react\""), out);
        assertTrue(out.contains("\"jsxFactory\": \"__nekoJsxFactory\""), out);
        assertTrue(out.contains("\"jsxFragmentFactory\": \"__nekoJsxFragment\""), out);
        assertFalse(out.contains("jsxImportSource"), "automatic-runtime key must be removed: " + out);
        // The paths merge itself is unaffected
        assertTrue(out.contains("../../.neko_probe/typescript/@package/*"), out);
    }

    /** Flips {@link ClassFilter#INSTANCE} jsxAutomaticRuntime for one call and restores it at the end
     * (static state, so try/finally is required). */
    private static void withJsxAutomaticRuntime(boolean automatic, Runnable body) {
        SandboxConfig prev = ClassFilter.INSTANCE.config();
        try {
            ClassFilter.INSTANCE.updateConfig(new SandboxConfig(
                    prev.allowThreads(), prev.allowReflection(), prev.allowAsm(),
                    prev.allowFsWriteOutsideNekojs(), prev.enableEsmAuthoring(),
                    prev.conciseScriptErrorLogs(), automatic, prev.scriptMemberValidation(), prev.packSyncMode(), prev.packSyncAllowUnsigned(),
                prev.dynamicRegistryEnabled(), prev.mixinJsEnabled()));
            body.run();
        } finally {
            ClassFilter.INSTANCE.updateConfig(prev);
        }
    }

    @Test
    void mergePyrightExtraPaths_freshAddsDefaultsAndPath(@TempDir Path temp) throws IOException {
        Path cfg = temp.resolve("pyrightconfig.json");
        new FileEditorConfigContributor().mergePyrightExtraPaths(cfg, List.of("../.neko_probe/python"));

        String out = Files.readString(cfg);
        assertTrue(out.contains("extraPaths"), out);
        assertTrue(out.contains("../.neko_probe/python"), out);
        assertTrue(out.contains("typeCheckingMode"), "fresh file should get default typeCheckingMode: " + out);
    }

    @Test
    void mergePyrightExtraPaths_existingDedupsKeepsUserMode(@TempDir Path temp) throws IOException {
        Path cfg = temp.resolve("pyrightconfig.json");
        Files.writeString(cfg,
                "{ \"typeCheckingMode\": \"strict\", \"extraPaths\": [\"../other\", \"../.neko_probe/python\"] }");

        new FileEditorConfigContributor().mergePyrightExtraPaths(cfg, List.of("../.neko_probe/python"));

        String out = Files.readString(cfg);
        assertTrue(out.contains("strict"), "user's typeCheckingMode must be preserved: " + out);
        // Dedup: .neko_probe/python appears only once
        int count = out.split("\\.neko_probe/python", -1).length - 1;
        assertEquals(1, count, "extraPaths must dedup: " + out);
        assertTrue(out.contains("../other"), "existing user extraPath preserved: " + out);
    }

    @Test
    void mergeVscodePythonExtraPaths_freshForcesPylanceAndPath(@TempDir Path temp) throws IOException {
        Path cfg = temp.resolve(".vscode/settings.json");
        new FileEditorConfigContributor().mergeVscodePythonExtraPaths(cfg, List.of("../.neko_probe/python"));

        String out = Files.readString(cfg);
        assertTrue(out.contains("python"), out);
        assertTrue(out.contains("\"languageServer\" : \"Pylance\"")
                        || out.contains("\"languageServer\": \"Pylance\""),
                "default Pylance should be selected: " + out);
        assertTrue(out.contains("python.analysis") || out.contains("analysis"), out);
        assertTrue(out.contains("../.neko_probe/python"), out);
    }

    @Test
    void mergeVscodePythonExtraPaths_respectsUserLanguageServer(@TempDir Path temp) throws IOException {
        Path cfg = temp.resolve(".vscode/settings.json");
        Files.createDirectories(cfg.getParent());
        Files.writeString(cfg, "{ \"python\": { \"languageServer\": \"Jedi\" } }");

        new FileEditorConfigContributor().mergeVscodePythonExtraPaths(cfg, List.of("../.neko_probe/python"));

        String out = Files.readString(cfg);
        assertTrue(out.contains("\"languageServer\": \"Jedi\""), "user's explicit choice must be kept: " + out);
        assertTrue(out.contains("../.neko_probe/python"), out);
    }

    @Test
    void mergeVscodeSettings_mixesStrategies_preservesUserAndOtherKeys(@TempDir Path temp) throws IOException {
        Path cfg = temp.resolve(".vscode/settings.json");
        Files.createDirectories(cfg.getParent());
        Files.writeString(cfg, """
                {
                  "python": {
                    "languageServer": "Jedi",
                    "analysis": {
                      "extraPaths": ["../user-lib"],
                      "diagnosticMode": "workspace"
                    }
                  },
                  "typescript.tsdk": "../my-tsdk",
                  "userTop": 42
                }""");

        new FileEditorConfigContributor().mergeVscodeSettings(cfg, List.of(
                new EditorConfigContributor.VscodeSetting("python.languageServer", "Pylance",
                        EditorConfigContributor.VscodeSettingMerge.SET_IF_ABSENT),
                new EditorConfigContributor.VscodeSetting("python.analysis.extraPaths",
                        List.of("../.neko_probe/python", "../user-lib"),
                        EditorConfigContributor.VscodeSettingMerge.EXTEND_STRING_ARRAY),
                new EditorConfigContributor.VscodeSetting("python.analysis.typeCheckingMode", "basic",
                        EditorConfigContributor.VscodeSettingMerge.SET)));

        String out = Files.readString(cfg);
        // SET_IF_ABSENT: the user's explicit Jedi choice is not overwritten
        assertTrue(out.contains("\"languageServer\": \"Jedi\""), "user languageServer kept: " + out);
        // EXTEND_STRING_ARRAY: user paths are kept and the probe path is appended once
        assertTrue(out.contains("../user-lib"), "user extraPath kept: " + out);
        assertTrue(out.contains("../.neko_probe/python"), "probe extraPath added: " + out);
        assertEquals(1, out.split("\\.neko_probe/python", -1).length - 1,
                "extraPaths must dedup: " + out);
        // SET: the leaf owned by the probe is replaced
        assertTrue(out.contains("\"typeCheckingMode\": \"basic\""), "SET leaf written: " + out);
        // All other existing keys (other backend keys and user keys) are kept
        assertTrue(out.contains("diagnosticMode"), "sibling analysis key kept: " + out);
        assertTrue(out.contains("typescript.tsdk"), "other backend key kept: " + out);
        assertTrue(out.contains("userTop"), "unknown top-level key kept: " + out);
    }

    @Test
    void mergeVscodeSettings_mergeObject_letsTwoBackendsShareOneObject(@TempDir Path temp) throws IOException {
        Path cfg = temp.resolve(".vscode/settings.json");
        Files.createDirectories(cfg.getParent());
        Files.writeString(cfg, "{ \"files.exclude\": { \"**/.git\": true, \"my.log\": false } }");

        var contributor = new FileEditorConfigContributor();
        // Simulates the TS backend and the Python backend each adding its own entry to the same files.exclude
        contributor.mergeVscodeSettings(cfg, List.of(new EditorConfigContributor.VscodeSetting(
                "files.exclude", Map.of("**/.neko_probe/typescript", true),
                EditorConfigContributor.VscodeSettingMerge.MERGE_OBJECT)));
        contributor.mergeVscodeSettings(cfg, List.of(new EditorConfigContributor.VscodeSetting(
                "files.exclude", Map.of("**/.neko_probe/python", true),
                EditorConfigContributor.VscodeSettingMerge.MERGE_OBJECT)));

        String out = Files.readString(cfg);
        assertTrue(out.contains("**/.neko_probe/typescript"), "first backend entry kept: " + out);
        assertTrue(out.contains("**/.neko_probe/python"), "second backend entry merged in: " + out);
        assertTrue(out.contains("**/.git"), "existing object entry kept: " + out);
        assertTrue(out.contains("my.log"), "user object entry kept: " + out);
    }

    @Test
    void mergeVscodeSnippets_freshThenMergePreservesUser(@TempDir Path temp) throws IOException {
        Path file = temp.resolve("nekojs.code-snippets");
        new FileEditorConfigContributor().mergeVscodeSnippets(file, List.of(
                new Snippet("log", "lg", "console.log($1)", "log it")));

        String out = Files.readString(file);
        assertTrue(out.contains("\"log\""), "snippet name written: " + out);
        assertTrue(out.contains("\"lg\""), "prefix written: " + out);
        assertTrue(out.contains("console.log($1)"), "body written: " + out);
        assertTrue(out.contains("log it"), "description written: " + out);

        // Second merge: the new snippet is added and the first one is kept
        new FileEditorConfigContributor().mergeVscodeSnippets(file, List.of(
                new Snippet("if", "iff", "if ($1) {\n  $2\n}", null)));
        String out2 = Files.readString(file);
        assertTrue(out2.contains("\"log\""), "first snippet preserved on merge: " + out2);
        assertTrue(out2.contains("\"iff\""), "new snippet added: " + out2);
        assertTrue(out2.contains("if ($1) {"), "multi-line body split into array entries: " + out2);
    }
}
