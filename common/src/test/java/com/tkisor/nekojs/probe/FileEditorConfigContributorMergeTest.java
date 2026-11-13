package com.tkisor.nekojs.probe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for merging array-valued jsconfig keys (include/typeRoots): the probe-managed
 * entries are refreshed and the user entries are kept. Replacing include/typeRoots wholesale
 * dropped every user-added entry on each probe run.
 */
class FileEditorConfigContributorMergeTest {

    @TempDir
    Path dir;

    private FileEditorConfigContributor contributor() {
        return new FileEditorConfigContributor();
    }

    @Test
    void includeMergeKeepsUserEntriesAndRefreshesProbeGlobs() throws Exception {
        Path jsconfig = dir.resolve("jsconfig.json");
        Files.writeString(jsconfig, """
                {
                  "include": [
                    "./typings/custom.d.ts",
                    "../../.neko_probe/@package/**/*.d.ts",
                    "./**/*.js"
                  ]
                }
                """);

        contributor().mergeJsConfigIncludes(jsconfig, List.of(
                "./**/*.js", "./**/*.mjs", "./**/*.cjs", "./**/*.ts",
                "../../.neko_probe/typescript/@package/**/*.d.ts"));

        String json = Files.readString(jsconfig);
        assertTrue(json.contains("\"./typings/custom.d.ts\""), "user include entry must survive");
        assertTrue(json.contains(".neko_probe/typescript/"), "fresh probe glob must be present");
        assertTrue(!json.contains(".neko_probe/@package/"), "stale probe glob (pre-typescript layout) must be replaced");
        assertTrue(json.contains("\"./**/*.mjs\""));
    }

    @Test
    void typeRootsMergeKeepsUserEntriesAndRefreshesProbeRoots() throws Exception {
        Path jsconfig = dir.resolve("jsconfig.json");
        Files.writeString(jsconfig, """
                {
                  "compilerOptions": {
                    "typeRoots": ["./my-types", "../../.neko_probe/@package"]
                  }
                }
                """);

        contributor().mergeJsConfigTypeRoots(jsconfig, List.of(
                "../../.neko_probe/typescript/@package", "../node_modules/@types"));

        String json = Files.readString(jsconfig);
        assertTrue(json.contains("\"./my-types\""), "user typeRoots entry must survive");
        assertTrue(json.contains(".neko_probe/typescript/@package"));
        assertTrue(!json.contains(".neko_probe/@package\""), "stale probe typeRoot must be replaced");
    }

    @Test
    void mergeIsIdempotentAcrossRepeatedProbeRuns() throws Exception {
        Path jsconfig = dir.resolve("jsconfig.json");
        Files.writeString(jsconfig, "{\"include\": [\"./typings/custom.d.ts\"]}");

        List<String> probeGlobs = List.of("./**/*.js", "../../.neko_probe/typescript/@package/**/*.d.ts");
        contributor().mergeJsConfigIncludes(jsconfig, probeGlobs);
        String once = Files.readString(jsconfig);
        contributor().mergeJsConfigIncludes(jsconfig, probeGlobs);
        assertEquals(once, Files.readString(jsconfig), "second merge must not change the file");
    }
}
