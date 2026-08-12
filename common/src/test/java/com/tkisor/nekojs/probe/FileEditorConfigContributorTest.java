package com.tkisor.nekojs.probe;

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
 * {@link FileEditorConfigContributor} 的合并行为：probe 拥有的键（paths 对应键、include、typeRoots）
 * 替换为最新值，用户键/未知键保留；pyrightconfig fresh 附带默认、既有去重。
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
        // probe 键被替换为新值
        assertTrue(out.contains("../../.neko_probe/typescript/@package/*"), out);
        assertFalse(out.contains("../../.neko_probe/@package/*"),
                "stale probe value must be replaced: " + out);
        // 用户别名 + 其他 compilerOptions 键 + 未知顶层键均保留
        assertTrue(out.contains("my-alias"), out);
        assertTrue(out.contains("ESNext"), out);
        assertTrue(out.contains("myExtra"), out);
    }

    @Test
    void mergeJsConfigPaths_freshFile_createsStructure(@TempDir Path temp) throws IOException {
        Path jsconfig = temp.resolve("a/b/jsconfig.json"); // 不存在 + 父目录缺失
        new FileEditorConfigContributor().mergeJsConfigPaths(jsconfig,
                Map.of("java:*", List.of("typescript/@package/*"), "@special/*", List.of("typescript/@special/*")));

        String out = Files.readString(jsconfig);
        assertTrue(out.contains("compilerOptions"), out);
        assertTrue(out.contains("typescript/@package/*"), out);
        assertTrue(out.contains("@special/*"), out);
    }

    @Test
    void mergeJsConfigIncludes_replacesProbeArray_preservesUserKeys(@TempDir Path temp) throws IOException {
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
        // include 整体替换为 probe 贡献值
        assertTrue(out.contains("../../.neko_probe/typescript/@package/**/*.d.ts"), out);
        assertTrue(out.contains("../../.neko_probe/typescript/@manual/**/*.d.ts"), out);
        assertFalse(out.contains("../../.neko_probe/@package"), "stale probe value must be replaced: " + out);
        assertFalse(out.contains("\"src\""), "user include entries are replaced wholesale (probe owns include): " + out);
        // 其他键（compilerOptions + 未知顶层键）保留
        assertTrue(out.contains("ESNext"), out);
        assertTrue(out.contains("myExtra"), out);
    }

    @Test
    void mergeJsConfigTypeRoots_replacesProbeArray_preservesUserKeys(@TempDir Path temp) throws IOException {
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
        // typeRoots 整体替换为 probe 贡献值
        assertTrue(out.contains("../../.neko_probe/typescript/@package"), out);
        assertTrue(out.contains("../node_modules/@types"), out);
        assertFalse(out.contains("../../.neko_probe/@package"),
                "stale probe value must be replaced: " + out);
        assertFalse(out.contains("./my-types"), "user typeRoots entries replaced wholesale: " + out);
        // 其他键保留
        assertTrue(out.contains("ESNext"), out);
        assertTrue(out.contains("myExtra"), out);
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
        // 去重：.neko_probe/python 只出现一次
        int count = out.split("\\.neko_probe/python", -1).length - 1;
        assertEquals(1, count, "extraPaths must dedup: " + out);
        assertTrue(out.contains("../other"), "existing user extraPath preserved: " + out);
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

        // 二次合并：新增片段 + 保留首个
        new FileEditorConfigContributor().mergeVscodeSnippets(file, List.of(
                new Snippet("if", "iff", "if ($1) {\n  $2\n}", null)));
        String out2 = Files.readString(file);
        assertTrue(out2.contains("\"log\""), "first snippet preserved on merge: " + out2);
        assertTrue(out2.contains("\"iff\""), "new snippet added: " + out2);
        assertTrue(out2.contains("if ($1) {"), "multi-line body split into array entries: " + out2);
    }
}
