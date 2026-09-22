package com.tkisor.nekojs.bindings.recipe;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 23 phase-ordering and single-pipeline trace (pure file reads, runs on every node).
 *
 * <p>Pins three platform wiring facts that a bare JVM cannot exercise against a real
 * {@code RecipeManager}:
 * <ol>
 *   <li><b>AC3</b> — in both {@code RecipeManagerMixin} twins (26.x shared tree and 1.21.1
 *       node tree), {@code afterRecipes} (script event + plugin lifecycle hook) fires only
 *       AFTER the fully parsed recipe batch is committed through the existing lifecycle
 *       (single reference swap of the recipe map); {@code recipes} fires before it.</li>
 *   <li><b>AC4</b> — both {@code ServerEventListener} twins run plugin {@code generateData}
 *       and script {@code ServerEvents.generateData} through the ONE shared batch path
 *       ({@code PluginGenerationHooks.runGenerateData}); no listener constructs a
 *       {@code DataGeneratorJS} against the active root or fires the plugin hook itself.
 *       Relative order inside the reload listener: {@code lootTables} posted first, then the
 *       generateData batch.</li>
 *   <li><b>AC9</b> — the recipe viewer ({@code RecipeViewerEvents}) surface is registered
 *       ONLY by the JEI-gated plugin ({@code clientOnly + requiredMods="jei"}), so nodes or
 *       environments without JEI never expose a viewer event that would silently never fire;
 *       the only poster of the viewer buses is the JEI plugin, which lives behind a
 *       NeoForge whole-file guard (fabric compiles none of it).</li>
 * </ol>
 *
 * <p>Also pins the fabric capability gaps explicitly (not as silent no-ops): the fabric
 * {@code ServerEvents} twin declares no {@code generateData} bus, and the fabric raw root has
 * no recipe-viewer wiring.
 */
class Ticket23RecipeDataPhaseTraceTest {

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("stonecutter.gradle.kts"))
                    && Files.isDirectory(dir.resolve("src"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("cannot locate repo root from " + Path.of("").toAbsolutePath());
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    /** Code lines only (drop javadoc/line comments): a symbol inside a comment is not wiring. */
    private static String code(String relative) {
        StringBuilder out = new StringBuilder();
        for (String line : read(repoRoot().resolve(relative)).split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static int count(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }

    // -------------------------------------------------- AC3: afterRecipes ordering

    @Test
    void afterRecipesFiresOnlyAfterTheParsedBatchIsCommitted_26x() {
        String mixin = code("src/main/java/com/tkisor/nekojs/mixin/RecipeManagerMixin.java");
        assertAfterRecipesPostCommit(mixin, "RecipeMap.create(newHolders)");
    }

    @Test
    void afterRecipesFiresOnlyAfterTheParsedBatchIsCommitted_1211() {
        String mixin = code("versions/1.21.1/src/main/java/com/tkisor/nekojs/mixin/RecipeManagerMixin.java");
        assertAfterRecipesPostCommit(mixin, "replaceRecipes(newHolders)");
    }

    private static void assertAfterRecipesPostCommit(String mixin, String commitMarker) {
        int commit = mixin.indexOf(commitMarker);
        int recipes = mixin.indexOf("ServerEvents.RECIPES.post(eventJS)");
        int afterScript = mixin.indexOf("ServerEvents.AFTER_RECIPES.post(eventJS)");
        int afterHook = mixin.indexOf(".afterRecipes(eventJS)");

        assertTrue(commit >= 0, "recipe batch commit point disappeared: " + commitMarker);
        assertTrue(recipes >= 0 && recipes < commit,
                "recipes must fire before the commit (modification phase)");
        assertTrue(afterScript >= 0 && afterScript > commit,
                "afterRecipes script event must fire only after the batch is committed");
        assertTrue(afterHook >= 0 && afterHook > commit,
                "afterRecipes plugin lifecycle hook must fire only after the batch is committed");
        assertEquals(1, count(mixin, "AFTER_RECIPES.post"),
                "afterRecipes must be posted exactly once per lifecycle pass");
        assertEquals(1, count(mixin, "afterRecipes(eventJS)"),
                "the plugin afterRecipes hook must be invoked exactly once per lifecycle pass");
    }

    @Test
    void recipeLifecycleNeverTouchesTheGenerateDataPipeline() {
        // The recipe lifecycle must not leak into data generation (and vice versa): two
        // pipelines, one per domain, neither bypassing the other.
        String mixin26 = code("src/main/java/com/tkisor/nekojs/mixin/RecipeManagerMixin.java");
        String mixin1211 = code("versions/1.21.1/src/main/java/com/tkisor/nekojs/mixin/RecipeManagerMixin.java");
        for (String mixin : List.of(mixin26, mixin1211)) {
            assertFalse(mixin.contains("GENERATE_DATA"), "recipe lifecycle must not post generateData");
            assertFalse(mixin.contains("DataGeneratorJS"), "recipe lifecycle must not write generated data");
        }
    }

    // ------------------------------------------- AC4: one generateData pipeline

    @Test
    void generateDataRunsThroughTheSingleBatchPipeline_26x() {
        assertSinglePipeline(code("src/main/java/com/tkisor/nekojs/listener/ServerEventListener.java"));
    }

    @Test
    void generateDataRunsThroughTheSingleBatchPipeline_1211() {
        assertSinglePipeline(code("versions/1.21.1/src/main/java/com/tkisor/nekojs/listener/ServerEventListener.java"));
    }

    private static void assertSinglePipeline(String listener) {
        assertEquals(1, count(listener, "PluginGenerationHooks.runGenerateData"),
                "the reload listener must run generateData through the single shared batch path");
        assertEquals(1, count(listener, "GENERATE_DATA.post"),
                "the script event is posted exactly once, inside the shared batch path");
        assertEquals(0, count(listener, "new DataGeneratorJS"),
                "no listener-side generator construction: no second direct-write pipeline");
        assertEquals(0, count(listener, "fireGenerateData"),
                "the plugin hook fires only inside the shared batch path");
        assertTrue(listener.contains("\"after_mods\""),
                "the stage key of the script dispatch and the batch must stay aligned");
    }

    @Test
    void lootTablesPhasePrecedesGenerateDataInBothReloadListeners() {
        String listener26 = code("src/main/java/com/tkisor/nekojs/listener/ServerEventListener.java");
        String listener1211 = code("versions/1.21.1/src/main/java/com/tkisor/nekojs/listener/ServerEventListener.java");
        for (String listener : List.of(listener26, listener1211)) {
            int loot = listener.indexOf("ServerEvents.LOOT_TABLES.post");
            int data = listener.indexOf("PluginGenerationHooks.runGenerateData");
            assertTrue(loot >= 0 && data >= 0 && loot < data,
                    "relative phase order pinned: lootTables JSON management runs before the generateData batch");
        }
    }

    // ----------------------------------- AC9: recipe viewer conditional capability

    @Test
    void viewerSurfaceIsRegisteredOnlyThroughTheJeiGatedPlugin() {
        String gated = code("src/main/java/com/tkisor/nekojs/integration/jei/RecipeViewerEventsPlugin.java");
        assertTrue(gated.contains("@RegisterNekoJSPlugin(clientOnly = true, requiredMods = \"jei\")"),
                "the viewer event group is registered only on client nodes with JEI loaded");
        assertEquals(1, count(gated, "registry.register(RecipeViewerEvents.GROUP)"),
                "exactly one registration of the viewer group");

        // No fabric-side registration of the group exists (the fabric node compiles neither
        // the JEI plugin nor any viewer wiring).
        for (String candidate : fabricMainTrees()) {
            String source = code(candidate);
            assertEquals(0, count(source, "RecipeViewerEvents.GROUP"),
                    "viewer group must not be registered outside the JEI-gated plugin: " + candidate);
        }
    }

    @Test
    void viewerBusesArePostedOnlyByTheJeiPluginBehindTheNeoforgeGuard() {
        String jeiPluginSource = read(repoRoot()
                .resolve("src/main/java/com/tkisor/nekojs/integration/jei/RecipeViewerJeiPlugin.java"));
        assertTrue(jeiPluginSource.startsWith("//? if neoforge {"),
                "the JEI wiring is NeoForge-only: fabric nodes compile none of it (explicit unavailable)");
        String jeiPlugin = code("src/main/java/com/tkisor/nekojs/integration/jei/RecipeViewerJeiPlugin.java");
        assertTrue(jeiPlugin.contains("@JeiPlugin"),
                "the poster is a JEI runtime plugin, so buses fire only when JEI is actually present");

        // Every bus post of the viewer group lives in the JEI plugin; the group declaration
        // itself exists exactly once (single declaration chain).
        for (String candidate : fabricMainTrees()) {
            String source = code(candidate);
            assertEquals(0, count(source, "RecipeViewerEvents.ADD_INFORMATION.post"),
                    "viewer buses must only be posted by the JEI plugin: " + candidate);
            assertEquals(0, count(source, "RecipeViewerEvents.REMOVE_CATEGORIES.post"),
                    "viewer buses must only be posted by the JEI plugin: " + candidate);
        }
        assertEquals(1, count(code("src/main/java/com/tkisor/nekojs/bindings/event/RecipeViewerEvents.java"),
                "EventGroup.of(\"RecipeViewerEvents\")"),
                "the viewer group is declared exactly once (no parallel declaration)");
    }

    /** Fabric main-tree files that must stay free of viewer wiring/registration. */
    private static List<String> fabricMainTrees() {
        List<String> files = new ArrayList<>();
        files.add("src/fabric/java/com/tkisor/nekojs/bindings/event/ServerEvents.java");
        files.add("src/fabric/java/com/tkisor/nekojs/fabric/event/FabricServerEventBindings.java");
        return files;
    }

    // ------------------------------- fabric capability gaps are explicit, not silent

    @Test
    void fabricNodesHaveNoGenerateDataBusAndNoViewerWiring() {
        String fabricServerEvents = code("src/fabric/java/com/tkisor/nekojs/bindings/event/ServerEvents.java");
        assertEquals(0, count(fabricServerEvents, "generateData"),
                "fabric declares no generateData bus: ServerEvents.generateData is an explicit "
                        + "unknown member there, never a silently swallowing no-op");
        String fabricBindings = code("src/fabric/java/com/tkisor/nekojs/fabric/event/FabricServerEventBindings.java");
        assertEquals(0, count(fabricBindings, "RecipeViewer"),
                "fabric has no recipe-viewer wiring (capability recorded as unavailable in the ticket 23 report)");
    }
}
