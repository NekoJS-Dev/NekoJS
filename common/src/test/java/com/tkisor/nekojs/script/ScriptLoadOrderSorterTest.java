package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ScriptLoadOrderSorter}: they build {@link ScriptContainer} instances directly,
 * without a Graal Context, and cover after topological ordering, priority as the primary order,
 * lenient reference resolution, and the deterministic fallback.
 */
class ScriptLoadOrderSorterTest {

    private static ScriptPropertyRegistry registry;
    private static Path root;

    @BeforeAll
    static void init() {
        TestPlatformInit.ensureInitialized();
        var impl = new ScriptPropertyRegistry.Impl();
        // Same registration order as NekoJSCorePlugin, so ordinals mean the same thing
        impl.register(ScriptProperty.AFTER);
        impl.register(ScriptProperty.MODLOADED);
        impl.register(ScriptProperty.DISABLE);
        impl.register(ScriptProperty.PRIORITY);
        impl.freeze();
        registry = impl;
        root = com.tkisor.nekojs.script.ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
    }

    private static ScriptContainer script(String rel, int priority, String... after) {
        Path path = root.resolve(rel);
        ScriptContainer container = new ScriptContainer(com.tkisor.nekojs.script.ScriptTypeEnv.makeId(ScriptType.SERVER, path), ScriptType.SERVER, path, registry);
        if (priority != 0) {
            container.properties.put(ScriptProperty.PRIORITY, priority);
        }
        if (after.length > 0) {
            container.properties.put(ScriptProperty.AFTER, List.of(after));
        }
        return container;
    }

    /**
     * Creates a pack script in the GLOBAL layout {@code <root>/packs/<id>/<type>_scripts/<rel>}. The
     * {@code ScriptId} path carries the {@code packs/<id>/} prefix, spelled the same way as
     * {@link com.tkisor.nekojs.core.ScriptLocator#packContainer}. The path does not have to exist:
     * the sorter only reads the id and the properties.
     */
    private static ScriptContainer packScript(String packId, String rel, int priority, String... after) {
        Path packScriptsDir = com.tkisor.nekojs.core.fs.NekoJSPaths.get().root()
                .resolve("packs").resolve(packId).resolve("server_scripts");
        Path path = packScriptsDir.resolve(rel);
        ScriptId id = ScriptId.of("nekojs", ScriptType.SERVER.name + "/packs/" + packId + "/" + rel);
        ScriptContainer container = new ScriptContainer(id, ScriptType.SERVER, path, registry, packId,
                com.tkisor.nekojs.core.pack.ScriptPackScope.GLOBAL);
        if (priority != 0) {
            container.properties.put(ScriptProperty.PRIORITY, priority);
        }
        if (after.length > 0) {
            container.properties.put(ScriptProperty.AFTER, List.of(after));
        }
        return container;
    }

    private static ScriptLoadOrderSorter.Result sort(List<ScriptContainer> scripts) {
        return ScriptLoadOrderSorter.applyAfterOrder(scripts, ScriptContainer::shouldRun);
    }

    /** Derives keys the same way the production keyOf does: the ScriptId path without its type
     * segment (flat {@code foo.js}, pack {@code packs/<id>/foo.js}). */
    private static List<String> keys(List<ScriptContainer> scripts) {
        return scripts.stream().map(s -> {
            String path = s.id.path();
            String prefix = s.type.name + "/";
            return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
        }).toList();
    }

    private static List<String> sortedKeys(ScriptContainer... scripts) {
        List<ScriptContainer> batch = new ArrayList<>(List.of(scripts));
        ScriptLoadOrderSorter.Result result = sort(batch);
        assertFalse(result.hasProblems(), "unexpected problems: " + result.describe());
        return keys(batch);
    }

    // ---- (a) after reverses lexicographic order within the same priority ----

    @Test
    void afterReversesOrderWithinSamePriority() {
        // Discovery order (lexicographic) is a.js -> z.js; after: z.js makes z.js run before a.js
        assertIterableEquals(
                List.of("z.js", "a.js"),
                sortedKeys(script("a.js", 0, "z.js"), script("z.js", 0)));
    }

    // ---- (b) after is ignored when priorities differ ----

    @Test
    void afterIgnoredWhenPrioritiesDiffer() {
        // b.js has a higher priority and declares after: a.js, but no edge is built across priorities,
        // so b.js still loads first
        assertIterableEquals(
                List.of("b.js", "a.js"),
                sortedKeys(script("a.js", 0), script("b.js", 10, "a.js")));
    }

    // ---- (c) A missing reference falls back without throwing ----

    @Test
    void missingAfterReferenceFallsBackWithoutError() {
        List<ScriptContainer> batch = new ArrayList<>(List.of(
                script("a.js", 0, "nope/missing.js"),
                script("b.js", 0)));

        ScriptLoadOrderSorter.Result result = sort(batch);

        assertIterableEquals(List.of("a.js", "b.js"), keys(batch));
        assertTrue(result.hasProblems());
        assertTrue(result.describe().contains("nope/missing.js"),
                "warning should mention the unresolved reference: " + result.describe());
    }

    // ---- (d) A cycle throws nothing and keeps a deterministic order ----

    @Test
    void afterCycleDoesNotThrowAndKeepsStableOrder() {
        List<ScriptContainer> batch = new ArrayList<>(List.of(
                script("a.js", 0, "b.js"),
                script("b.js", 0, "a.js")));

        ScriptLoadOrderSorter.Result result = sort(batch);

        assertIterableEquals(List.of("a.js", "b.js"), keys(batch));
        assertTrue(result.hasProblems());
        assertTrue(result.describe().contains("循环"), "warning should mention the cycle: " + result.describe());

        // Determinism: sorting an equivalent batch again gives the same result
        List<ScriptContainer> again = new ArrayList<>(List.of(
                script("a.js", 0, "b.js"),
                script("b.js", 0, "a.js")));
        sort(again);
        assertIterableEquals(keys(batch), keys(again));
    }

    // ---- Partial cycle: the whole group falls back to the original stable order ----

    @Test
    void partialCycleRestoresWholeGroupToOriginalOrder() {
        // Original order a b c d; a after b and b after a form a cycle; c after b depends on a member
        // of the cycle. The documented promise that a cyclic dependency falls back to the original
        // stable order applies to the whole group, not by appending the cycle members after the
        // sorted nodes (that would push the unrelated d to the front and break the after semantics).
        List<ScriptContainer> batch = new ArrayList<>(List.of(
                script("a.js", 0, "b.js"),
                script("b.js", 0, "a.js"),
                script("c.js", 0, "b.js"),
                script("d.js", 0)));

        ScriptLoadOrderSorter.Result result = sort(batch);

        assertIterableEquals(List.of("a.js", "b.js", "c.js", "d.js"), keys(batch));
        assertTrue(result.hasProblems());
        assertTrue(result.describe().contains("循环"),
                "warning should mention the cycle: " + result.describe());
    }

    // ---- Lenient reference resolution ----

    @Test
    void afterDotSlashResolvesRelativeToDeclaringDirectory() {
        // ./init.js resolves against the directory of sub/main.js -> sub/init.js
        assertIterableEquals(
                List.of("sub/init.js", "sub/main.js"),
                sortedKeys(script("sub/main.js", 0, "./init.js"), script("sub/init.js", 0)));
    }

    @Test
    void afterAcceptsNekojsAndTypePrefixes() {
        // Both nekojs/server/b.js and server/b.js resolve to b.js in the root directory
        assertIterableEquals(
                List.of("b.js", "a.js"),
                sortedKeys(script("a.js", 0, "nekojs/server/b.js"), script("b.js", 0)));
        assertIterableEquals(
                List.of("b.js", "a.js"),
                sortedKeys(script("a.js", 0, "server/b.js"), script("b.js", 0)));
    }

    @Test
    void afterGlobMatchesAllFilesInDirectory() {
        assertIterableEquals(
                List.of("lib/x.js", "lib/y.js", "main.js"),
                sortedKeys(
                        script("lib/x.js", 0),
                        script("lib/y.js", 0),
                        script("main.js", 0, "lib/*")));
    }

    @Test
    void afterReferenceToDisabledScriptIsSilentlyIgnored() {
        ScriptContainer disabled = script("lib/init.js", 0);
        disabled.properties.put(ScriptProperty.DISABLE, true);
        // The reference is known but the target will not run -> no edge, no warning
        assertIterableEquals(
                List.of("main.js", "lib/init.js"),
                sortedKeys(script("main.js", 0, "lib/init.js"), disabled));
    }

    // ---- Stability ----

    @Test
    void unrelatedScriptsKeepDiscoveryOrder() {
        assertIterableEquals(
                List.of("a.js", "b.js", "c.js"),
                sortedKeys(script("a.js", 0), script("b.js", 0), script("c.js", 0)));
    }

    // ---- Pack scripts: the key comes from the ScriptId, so no ../packs/... prefix ----

    @Test
    void afterReferenceIntoPackScriptResolves() {
        // A flat script referencing a pack script: after: packs/mylib/init.js must hit the pack script's
        // key.
        List<ScriptContainer> batch = new ArrayList<>(List.of(
                script("main.js", 0, "packs/mylib/init.js"),
                packScript("mylib", "init.js", 0)));
        ScriptLoadOrderSorter.Result result = sort(batch);
        assertFalse(result.hasProblems(), "pack reference must resolve: " + result.describe());
        assertIterableEquals(List.of("packs/mylib/init.js", "main.js"), keys(batch));
    }

    @Test
    void dotSlashInsidePackResolvesRelativeToPackDirectory() {
        // Inside a pack, ./init.js resolves against the declaring script's directory under packScriptsDir
        List<ScriptContainer> batch = new ArrayList<>(List.of(
                packScript("mylib", "sub/main.js", 0, "./init.js"),
                packScript("mylib", "sub/init.js", 0)));
        ScriptLoadOrderSorter.Result result = sort(batch);
        assertFalse(result.hasProblems(), "pack ./ reference must resolve: " + result.describe());
        assertIterableEquals(
                List.of("packs/mylib/sub/init.js", "packs/mylib/sub/main.js"), keys(batch));
    }

    @Test
    void afterGlobCoversWholePack() {
        List<ScriptContainer> batch = new ArrayList<>(List.of(
                packScript("mylib", "x.js", 0),
                packScript("mylib", "y.js", 0),
                script("main.js", 0, "packs/mylib/*")));
        ScriptLoadOrderSorter.Result result = sort(batch);
        assertFalse(result.hasProblems(), "pack glob must resolve: " + result.describe());
        assertIterableEquals(
                List.of("packs/mylib/x.js", "packs/mylib/y.js", "main.js"), keys(batch));
    }
}
