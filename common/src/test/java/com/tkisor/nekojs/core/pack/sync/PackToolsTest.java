package com.tkisor.nekojs.core.pack.sync;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.pack.LoadedModPackSource;
import com.tkisor.nekojs.core.pack.ScriptPackState;
import com.tkisor.nekojs.core.pack.tool.PackTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PackToolsTest {
    @TempDir Path temporary;

    @Test void templateArchiveIsReproducibleInstallationPreservesIntentAndRemovesDeletedContent() throws Exception {
        Path pack = PackTools.create(temporary.resolve("source"), "demopack", List.of(ScriptType.SERVER, ScriptType.CLIENT));
        Files.writeString(pack.resolve("server_scripts/library.js"), "export const answer=42;");
        Path first = PackTools.archive(pack, temporary.resolve("first.nekopack"));
        Path second = PackTools.archive(pack, temporary.resolve("second.nekopack"));
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        Path installed = PackTools.install(first, temporary.resolve("installed"), false);
        assertEquals(PackTools.validate(pack).hash(), PackTools.validate(installed).hash());
        assertTrue(ScriptPackState.save(installed, false));
        Files.delete(pack.resolve("server_scripts/library.js"));
        Path upgraded = PackTools.archive(pack, temporary.resolve("upgrade.nekopack"));
        PackTools.install(upgraded, installed.getParent(), true);
        assertFalse(Files.exists(installed.resolve("server_scripts/library.js")));
        assertFalse(ScriptPackState.load(installed).enabled());
        assertThrows(IOException.class, () -> PackTools.create(temporary.resolve("source"), "demopack", List.of(ScriptType.SERVER)));
    }

    @Test void installationFailureRestoresExactOldFilesAndIntent() throws Exception {
        Path pack = PackTools.create(temporary.resolve("source"), "demopack", List.of(ScriptType.SERVER));
        Path original = PackTools.archive(pack, temporary.resolve("original.nekopack"));
        Path installed = PackTools.install(original, temporary.resolve("installed"), false);
        assertTrue(ScriptPackState.save(installed, false));
        String hash = PackTools.validate(installed).hash();
        Files.writeString(pack.resolve("server_scripts/main.js"), "console.info('upgraded');");
        Path upgraded = PackTools.archive(pack, temporary.resolve("upgrade.nekopack"));
        ServerPackCache.failNextCommitForTests(new IOException("injected replacement failure"));
        assertThrows(IOException.class, () -> PackTools.install(upgraded, installed.getParent(), true));
        assertEquals(hash, PackTools.validate(installed).hash());
        assertFalse(ScriptPackState.load(installed).enabled());
    }

    @Test void archivesRejectTraversalPortableCollisionsAndProtectedCacheTargets() throws Exception {
        Path malicious = temporary.resolve("malicious.nekopack");
        try (var zip = new ZipOutputStream(Files.newOutputStream(malicious))) {
            zip.putNextEntry(new ZipEntry("../escaped.js")); zip.write(new byte[]{1}); zip.closeEntry();
        }
        assertThrows(IOException.class, () -> PackTools.install(malicious, temporary.resolve("installed"), false));
        assertFalse(Files.exists(temporary.resolve("escaped.js")));
        Path source = PackTools.create(temporary.resolve("source"), "demopack", List.of(ScriptType.SERVER));
        Path archive = PackTools.archive(source, temporary.resolve("valid.nekopack"));
        assertThrows(IOException.class, () -> PackTools.install(archive, temporary.resolve("server_packs/cache"), false));
        assertThrows(IOException.class, () -> PackTools.validatePath("client_scripts/NUL.js", false));
        assertThrows(IOException.class, () -> PackTools.validatePath("client_scripts/node_modules/lib.js", false));
    }

    @Test void fiveJarTargetsKeepContentAndLoaderDiscoveryMaterializesReadOnlySource() throws Exception {
        Path pack = PackTools.create(temporary.resolve("source"), "demopack", List.of(ScriptType.SERVER));
        Files.createDirectories(pack.resolve("assets/demopack"));
        Files.writeString(pack.resolve("assets/demopack/fixture.json"), "{}");
        for (String target : List.of("neoforge:1.21.1", "neoforge:26.1.2", "neoforge:26.2", "fabric:26.1.2", "fabric:26.2")) {
            String[] parts = target.split(":"); Path output = temporary.resolve(target.replace(':', '-') + ".jar");
            PackTools.modJar(pack, output, parts[0], parts[1]);
            try (var zip = new ZipFile(output.toFile())) {
                assertNotNull(zip.getEntry("assets/demopack/fixture.json"));
                assertNotNull(zip.getEntry("nekojs/packs/demopack/assets/demopack/fixture.json"));
                String metadata = new String(zip.getInputStream(zip.getEntry(parts[0].equals("fabric") ? "fabric.mod.json" : "META-INF/neoforge.mods.toml")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(metadata.contains("nekojs"));
                if (parts[0].equals("neoforge")) assertEquals(parts[1].equals("1.21.1"), metadata.contains("lowcodefml"));
            }
            try (var fileSystem = FileSystems.newFileSystem(output)) {
                var sources = LoadedModPackSource.discover(Map.of("demopack", fileSystem.getPath("/nekojs/packs")), temporary.resolve("cache"));
                assertEquals(1, sources.size(), target);
                assertEquals("MOD:demopack", sources.getFirst().selector());
                assertEquals(PackTools.validate(pack).hash(), PackTools.validate(sources.getFirst().root()).hash());
            }
        }
        assertThrows(IOException.class, () -> PackTools.modJar(pack, temporary.resolve("forge.jar"), "forge", "1.21.1"));
    }
}
