package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.fs.JSConfigModel;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 30 reload/validation boundary: workspace configs the user edited in their external
 * IDE are never overwritten or deleted by regeneration; missing configs are created once.
 */
class WorkspaceGeneratorPreserveTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @TempDir
    Path dir;

    @Test
    void missingConfigIsWrittenOnceAndExistingConfigIsNeverOverwritten() throws Exception {
        Path configPath = dir.resolve("fresh/jsconfig.json");
        Files.createDirectories(configPath.getParent());
        JSConfigModel model = WorkspaceGenerator.buildConfigForEnv(
                ScriptType.SERVER, dir.resolve("fresh"), dir.resolve("probe"));
        assertTrue(WorkspaceGenerator.writeConfigIfMissing(configPath, model),
                "a missing config is written");
        String generated = Files.readString(configPath);

        // Regeneration of the same directory (as reload does) must be a no-op for the file.
        JSConfigModel regenerated = WorkspaceGenerator.buildConfigForEnv(
                ScriptType.SERVER, dir.resolve("fresh"), dir.resolve("probe"));
        assertFalse(WorkspaceGenerator.writeConfigIfMissing(configPath, regenerated),
                "an existing config is never overwritten by regeneration");
        assertEquals(generated, Files.readString(configPath),
                "the file must still hold the first generation's content");
    }

    @Test
    void userEditedConfigBytesArePreservedExactly() throws Exception {
        Path edited = dir.resolve("edited/jsconfig.json");
        Files.createDirectories(edited.getParent());
        Files.writeString(edited, "{\n  \"user\": \"edited\"\n}\n");

        JSConfigModel model = WorkspaceGenerator.buildConfigForEnv(
                ScriptType.CLIENT, dir.resolve("edited"), dir.resolve("probe"));
        assertFalse(WorkspaceGenerator.writeConfigIfMissing(edited, model));
        assertEquals("{\n  \"user\": \"edited\"\n}\n", Files.readString(edited),
                "the exact user-authored bytes must be preserved");
    }
}
