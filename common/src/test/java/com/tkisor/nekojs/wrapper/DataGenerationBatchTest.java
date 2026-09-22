package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 23 AC6/AC7 fixtures for the non-Assets data generation batch: candidate staging,
 * validation (path inclusion / duplicate-key+overwrite policy / JSON structure / read-back),
 * atomic publish with rollback, failure retention and user-file protection.
 *
 * <p>All assertions observe the public file tree and the generator's read-back — no private
 * state is asserted beyond the documented {@link DataGenerationBatch.PublishResult}.
 */
class DataGenerationBatchTest {

    private static Path base;
    private Path activeRoot;
    private Path stateDir;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
        base = Platform.getGameDir().resolve("nekojs").resolve("test-datagen-" + System.nanoTime());
    }

    @AfterAll
    static void cleanupBase() throws IOException {
        deleteRecursivelyImpl(base);
    }

    @BeforeEach
    void setUp() throws IOException {
        activeRoot = Files.createDirectories(base.resolve("active-" + System.nanoTime()));
        stateDir = activeRoot.getParent().resolve(DataGenerationBatch.STATE_DIR_NAME);
        deleteRecursively(stateDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(stateDir);
    }

    @Test
    void writesStageIntoCandidateThenPublishAtomicallyAndReadsBack() throws Exception {
        DataGenerationBatch batch = DataGenerationBatch.open(activeRoot, "after_mods");
        DataGeneratorJS generator = batch.generator();

        generator.json("minecraft/loot_tables/blocks/stone.json", "{\"type\":\"minecraft:block\"}");
        generator.text("nekojs/readme.txt", "hello");

        // Before publish: candidate area holds the writes, the active pack root stays untouched.
        assertTrue(Files.isRegularFile(batch.stateCandidate().resolve("minecraft/loot_tables/blocks/stone.json")),
                "writes during collection must land in the candidate area, not the active root");
        assertFalse(Files.exists(activeRoot.resolve("minecraft")),
                "active pack root must not be touched before publish");
        // Read-back during collection goes through the same generator path (candidate root).
        assertEquals("minecraft:block", generator.getJson("minecraft/loot_tables/blocks/stone.json")
                .getAsJsonObject().get("type").getAsString());

        DataGenerationBatch.PublishResult result = batch.publish();

        assertTrue(result.published().contains("minecraft/loot_tables/blocks/stone.json"));
        assertEquals("{\"type\":\"minecraft:block\"}",
                Files.readString(activeRoot.resolve("minecraft/loot_tables/blocks/stone.json")));
        assertEquals("hello", Files.readString(activeRoot.resolve("nekojs/readme.txt")));
        assertFalse(Files.exists(batch.stateCandidate()), "candidate area must be removed after publish");
        // Read-back after publish still resolves through the same generator (sealed to the active root).
        assertEquals("minecraft:block", generator.getJson("minecraft/loot_tables/blocks/stone.json")
                .getAsJsonObject().get("type").getAsString());
        assertEquals("", result.owners().get("nekojs/readme.txt"),
                "writes without a contributor label are attributed to the empty owner");
    }

    @Test
    void duplicateKeyPolicyIsLastContributorWinsAndCrossOverwritesAreRecorded() throws Exception {
        DataGenerationBatch batch = DataGenerationBatch.open(activeRoot, "after_mods");
        DataGeneratorJS generator = batch.generator();

        batch.setContributor("plugin:com.example.PluginA");
        generator.json("shared/dupe.json", "{\"v\":1}");
        batch.setContributor("script");
        generator.json("shared/dupe.json", "{\"v\":2}");
        batch.setContributor(null);

        DataGenerationBatch.PublishResult result = batch.publish();

        assertEquals("{\"v\":2}", Files.readString(activeRoot.resolve("shared/dupe.json")),
                "within one batch the last contributor wins (script may override plugin output)");
        assertEquals("script", result.owners().get("shared/dupe.json"));
        assertEquals(1, result.overwriteTrace().size());
        assertTrue(result.overwriteTrace().get(0).contains("plugin:com.example.PluginA"),
                "overwrite trace must name the previous owner: " + result.overwriteTrace());
        assertTrue(result.overwriteTrace().get(0).contains("script"));
    }

    @Test
    void invalidJsonCandidateFailsValidationAndRetainsPreviousActiveData() throws Exception {
        DataGenerationBatch first = DataGenerationBatch.open(activeRoot, "after_mods");
        first.generator().json("gen/keep.json", "{\"kept\":true}");
        first.publish();

        DataGenerationBatch second = DataGenerationBatch.open(activeRoot, "after_mods");
        second.generator().json("gen/new.json", "{\"ok\":true}");
        // A file appears in the candidate area outside DataGeneratorJS (bad hand-written JSON).
        Files.createDirectories(second.stateCandidate().resolve("gen"));
        Files.writeString(second.stateCandidate().resolve("gen/broken.json"), "{not json",
                StandardCharsets.UTF_8);

        IllegalStateException error = assertThrows(IllegalStateException.class, second::publish);

        assertTrue(error.getMessage().contains("validation failed"), error.getMessage());
        assertTrue(error.getMessage().contains("gen/broken.json"), error.getMessage());
        assertEquals("{\"kept\":true}", Files.readString(activeRoot.resolve("gen/keep.json")),
                "previous active data must be retained on validation failure");
        assertFalse(Files.exists(activeRoot.resolve("gen/new.json")),
                "no partial publish may happen when validation fails");
        assertFalse(Files.exists(activeRoot.resolve("gen/broken.json")));
    }

    @Test
    void midPublishFailureRollsBackEverythingAndRetainsOldActive() throws Exception {
        // Seed the active root with generator-owned files via one successful batch.
        DataGenerationBatch seed = DataGenerationBatch.open(activeRoot, "after_mods");
        seed.setContributor("plugin:com.example.Seed");
        seed.generator().json("gen/first.json", "{\"first\":\"old\"}");
        seed.generator().json("gen/second.json", "{\"second\":\"old\"}");
        seed.publish();

        DataGenerationBatch batch = DataGenerationBatch.open(activeRoot, "after_mods");
        batch.setContributor("script");
        batch.generator().json("gen/first.json", "{\"first\":\"new\"}");
        batch.generator().json("gen/second.json", "{\"second\":\"new\"}");
        batch.generator().json("gen/third.json", "{\"third\":\"new\"}");
        // Fail the publish when the second file is about to be replaced.
        batch.setMoveFailureForTest(target -> target.getFileName().toString().equals("second.json"));

        IllegalStateException error = assertThrows(IllegalStateException.class, batch::publish);

        assertTrue(error.getMessage().contains("rolled back"), error.getMessage());
        // Rollback: both replaced files restored to their previous content, third never landed.
        assertEquals("{\"first\":\"old\"}", Files.readString(activeRoot.resolve("gen/first.json")));
        assertEquals("{\"second\":\"old\"}", Files.readString(activeRoot.resolve("gen/second.json")));
        assertFalse(Files.exists(activeRoot.resolve("gen/third.json")));
        // The first file's candidate copy was restored by the rollback so nothing is lost.
        assertEquals("{\"first\":\"new\"}",
                Files.readString(batch.stateCandidate().resolve("gen/first.json")));
    }

    @Test
    void userOwnedFilesAreNeverOverwritten() throws Exception {
        DataGenerationBatch first = DataGenerationBatch.open(activeRoot, "after_mods");
        first.generator().json("u/tracked.json", "{\"generator\":\"v1\"}");
        first.publish();
        // The user hand-edits a generator-owned file after the batch.
        Files.writeString(activeRoot.resolve("u/tracked.json"), "{\"user\":\"edited\"}",
                StandardCharsets.UTF_8);
        // The user also owns a file no batch ever produced.
        Files.createDirectories(activeRoot.resolve("u"));
        Files.writeString(activeRoot.resolve("u/foreign.json"), "{\"user\":\"own\"}",
                StandardCharsets.UTF_8);

        DataGenerationBatch second = DataGenerationBatch.open(activeRoot, "after_mods");
        second.generator().json("u/tracked.json", "{\"generator\":\"v2\"}");
        second.generator().json("u/foreign.json", "{\"generator\":\"intrudes\"}");
        second.generator().json("u/fresh.json", "{\"generator\":\"new\"}");
        DataGenerationBatch.PublishResult result = second.publish();

        assertEquals("{\"user\":\"edited\"}", Files.readString(activeRoot.resolve("u/tracked.json")),
                "a user-edited file must never be overwritten by generated output");
        assertEquals("{\"user\":\"own\"}", Files.readString(activeRoot.resolve("u/foreign.json")),
                "a user-owned file unknown to every previous manifest must never be overwritten");
        assertEquals("{\"generator\":\"new\"}", Files.readString(activeRoot.resolve("u/fresh.json")));
        assertEquals(2, result.skippedUserFiles().size());
        assertTrue(result.skippedUserFiles().contains("u/tracked.json"));
        assertTrue(result.skippedUserFiles().contains("u/foreign.json"));
    }

    @Test
    void outputsOfEarlierBatchesAbsentFromCurrentBatchAreRetained() throws Exception {
        DataGenerationBatch first = DataGenerationBatch.open(activeRoot, "after_mods");
        first.generator().json("gen/a.json", "{}");
        first.generator().json("gen/b.json", "{}");
        first.publish();

        DataGenerationBatch second = DataGenerationBatch.open(activeRoot, "after_mods");
        second.generator().json("gen/c.json", "{}");
        second.publish();

        assertTrue(Files.isRegularFile(activeRoot.resolve("gen/a.json")),
                "script-generated data is not unconditionally regenerable; earlier outputs are kept");
        assertTrue(Files.isRegularFile(activeRoot.resolve("gen/b.json")));
        assertTrue(Files.isRegularFile(activeRoot.resolve("gen/c.json")));
        // A later batch may still regenerate files that remain manifest-owned.
        DataGenerationBatch third = DataGenerationBatch.open(activeRoot, "after_mods");
        third.generator().json("gen/a.json", "{\"regenerated\":true}");
        third.publish();
        assertEquals("{\"regenerated\":true}", Files.readString(activeRoot.resolve("gen/a.json")));
    }

    @Test
    void sealedGeneratorRejectsFurtherWritesButKeepsReadBack() throws Exception {
        DataGenerationBatch batch = DataGenerationBatch.open(activeRoot, "after_mods");
        batch.generator().json("gen/one.json", "{\"one\":1}");
        batch.publish();

        assertNotNull(batch.generator().getJson("gen/one.json"));
        IllegalStateException sealed = assertThrows(IllegalStateException.class,
                () -> batch.generator().json("gen/two.json", "{}"));
        assertTrue(sealed.getMessage().contains("already published"), sealed.getMessage());
        assertFalse(Files.exists(activeRoot.resolve("gen/two.json")));
        assertThrows(IllegalStateException.class, batch::publish,
                "publishing the same batch twice must be rejected");
    }

    @Test
    void openWipesStaleScratchOfTheSameStage() throws Exception {
        DataGenerationBatch stale = DataGenerationBatch.open(activeRoot, "after_mods");
        stale.generator().json("gen/stale.json", "{}");
        Files.writeString(stale.stateCandidate().resolve("leftover.txt"), "leftover",
                StandardCharsets.UTF_8);

        DataGenerationBatch fresh = DataGenerationBatch.open(activeRoot, "after_mods");

        assertFalse(Files.exists(fresh.stateCandidate().resolve("gen/stale.json")),
                "a new batch for the same stage must start from a clean candidate area");
        assertFalse(Files.exists(fresh.stateCandidate().resolve("leftover.txt")));
    }

    // ---- helpers ----

    private void deleteRecursively(Path path) throws IOException {
        deleteRecursivelyImpl(path);
    }

    static void deleteRecursivelyImpl(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        }
    }
}
