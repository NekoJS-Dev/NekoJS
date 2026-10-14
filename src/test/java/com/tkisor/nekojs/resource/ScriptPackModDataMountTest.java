package com.tkisor.nekojs.resource;

import com.tkisor.nekojs.core.pack.ScriptPack;
import com.tkisor.nekojs.core.pack.ScriptPackManifest;
import com.tkisor.nekojs.core.pack.ScriptPackScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptPackModDataMountTest {
    @TempDir Path temporary;

    @Test void nativeModDataDoesNotRequestADuplicateDirectoryMount() throws Exception {
        Files.createDirectories(temporary.resolve("data"));
        Files.writeString(temporary.resolve("manifest.json"), "{\"id\":\"content\"}");
        var manifest = ScriptPackManifest.load(temporary, "content");
        assertFalse(ScriptPackDataManager.hasDataPacks(List.of(pack(ScriptPackScope.MOD, manifest))));
        assertTrue(ScriptPackDataManager.hasDataPacks(List.of(pack(ScriptPackScope.GLOBAL, manifest))));
        assertTrue(ScriptPackDataManager.hasDataPacks(List.of(pack(ScriptPackScope.WORLD, manifest))));
        assertTrue(ScriptPackDataManager.hasDataPacks(List.of(pack(ScriptPackScope.MOD, manifest), pack(ScriptPackScope.WORLD, manifest))));
    }

    private ScriptPack pack(ScriptPackScope scope, ScriptPackManifest manifest) {
        return new ScriptPack("content", "content", "1.0.0", scope, temporary, true, manifest);
    }
}
