package com.tkisor.nekojs.core.mixin.tool;

import com.tkisor.nekojs.core.mixin.MixinPlan;
import com.tkisor.nekojs.core.mixin.MixinLibrarySelection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

class MixinPreparationTest {
    @TempDir Path game;
    private static final MixinPlan.Profile PROFILE = new MixinPlan.Profile("26.1.2", "neoforge", "26.1.2.71", "runtime", "client");
    private static final String SOURCE = "import {Mixin} from 'nekojs/mixin'; Mixin.target('demo.Target', target => { target.inject('health()F', 'demo:health', build => { build.at = 'RETURN'; }); });";

    @Test void firstLaunchRestartAdmissionUpdateAndRemovalAreSeparate() throws Exception {
        assertFalse(prepare(List.of()));
        source(SOURCE);
        assertTrue(prepare(List.of()));
        var first = cached();
        assertThrows(IllegalArgumentException.class, () -> prepare(List.of()));
        assertFalse(prepare(List.of(first)));
        source(SOURCE + "\n// Structural source change");
        assertTrue(prepare(List.of(first)));
        var next = cached();
        assertNotEquals(first.hash(), next.hash());
        assertFalse(prepare(List.of(next)));
        Files.delete(game.resolve("nekojs/mixins/demo.mixin.js"));
        assertTrue(prepare(List.of(next)));
        assertNull(MixinLibrarySelection.selected(game));
        assertTrue(Files.exists(MixinPreparation.cache(game, next.hash())));
        assertFalse(prepare(List.of()));
    }

    @Test void failedCompilationAndConflictingManualPlansPreserveTheInstalledCache() throws Exception {
        source(SOURCE); assertTrue(prepare(List.of()));
        var first = cached(); byte[] original = Files.readAllBytes(cache());
        source("Java.type('java.lang.System');");
        assertThrows(Exception.class, () -> prepare(List.of(first)));
        assertArrayEquals(original, Files.readAllBytes(cache()));
        source(SOURCE);
        var manual = new MixinPlan(first.abi(), first.profile(), "a".repeat(64), first.hooks());
        assertThrows(IllegalArgumentException.class, () -> prepare(List.of(manual)));
        assertArrayEquals(original, Files.readAllBytes(cache()));
        assertThrows(IllegalArgumentException.class, () -> MixinPreparation.prepare(game, PROFILE, false, List.of(first), name -> target()));
        assertArrayEquals(original, Files.readAllBytes(cache()));
    }

    @Test void updatePreservesAnOpenLoaderArchiveUntilRestart() throws Exception {
        source(SOURCE); assertTrue(prepare(List.of())); var first = cached();
        try (var archive = new JarFile(cache().toFile()); var filesystem = FileSystems.newFileSystem(cache())) {
            source(SOURCE + "\n// Updated while the loader retains the old archive");
            assertTrue(prepare(List.of(first)));
            assertNotEquals(first.hash(), cached().hash());
            assertNotNull(archive.getEntry("META-INF/nekojs/mixin-plan.json"));
            assertTrue(Files.exists(filesystem.getPath("/META-INF/nekojs/mixin-plan.json")));
        }
    }

    @Test void unownedCacheIsNeverReplacedOrRemoved() throws Exception {
        MixinLibrarySelection.select(game, "a".repeat(64));
        Files.createDirectories(cache().getParent()); Files.writeString(cache(), "user data");
        assertThrows(Exception.class, () -> prepare(List.of()));
        source(SOURCE);
        assertThrows(Exception.class, () -> prepare(List.of()));
        assertEquals("user data", Files.readString(cache()));
    }

    @Test void invalidSelectionIsPreservedAndFailedCompilationCannotChangeSelection() throws Exception {
        source(SOURCE); assertTrue(prepare(List.of())); var first = cached();
        source("throw new Error('invalid');");
        assertThrows(Exception.class, () -> prepare(List.of(first)));
        assertEquals(first.hash(), MixinLibrarySelection.selected(game));
        Path pointer = game.resolve(MixinLibrarySelection.DIRECTORY).resolve("active");
        Files.writeString(pointer, "user data");
        assertThrows(Exception.class, () -> prepare(List.of(first)));
        assertEquals("user data", Files.readString(pointer));
    }

    @Test void profileChangePublishesANewSelectionBeforeRuntimeAdmission() throws Exception {
        source(SOURCE); assertTrue(prepare(List.of())); var first = cached();
        var changed = new MixinPlan.Profile("26.1.2", "neoforge", "26.1.2.72", "runtime", "client");
        assertTrue(MixinPreparation.prepare(game, changed, true, List.of(first), name -> target()));
        var next = cached(); assertNotEquals(first.hash(), next.hash());
        assertFalse(MixinPreparation.prepare(game, changed, true, List.of(next), name -> target()));
    }

    @Test void native262IdentityUsesGameLibrariesWithoutNormalizingTheExactProfile() throws Exception {
        var profile = new MixinPlan.Profile("26.2", "neoforge", "26.2.0.57", "runtime", "server");
        source(SOURCE);
        assertTrue(MixinPreparation.prepare(game, profile, true, List.of(), name -> target()));
        assertEquals(profile, cached().profile());
        try (var archive = new JarFile(cache().toFile())) {
            assertEquals("GAMELIBRARY", archive.getManifest().getMainAttributes().getValue("FMLModType"));
            assertNull(archive.getEntry("META-INF/neoforge.mods.toml"));
        }
    }

    @Test void onlySupportedModernProfilesCanPrepareAutomatically() {
        var legacy = new MixinPlan.Profile("1.21.1", "neoforge", "21.1.227", "runtime", "client");
        assertThrows(IllegalArgumentException.class, () -> MixinPreparation.prepare(game, legacy, true, List.of(), name -> target()));
    }

    private boolean prepare(List<MixinPlan> admitted) throws Exception {
        return MixinPreparation.prepare(game, PROFILE, true, admitted, name -> target());
    }
    private Path cache() throws Exception { return MixinPreparation.cache(game, MixinLibrarySelection.selected(game)); }
    private void source(String text) throws Exception {
        Path path = game.resolve("nekojs/mixins/demo.mixin.js"); Files.createDirectories(path.getParent()); Files.writeString(path, text);
    }
    private MixinPlan cached() throws Exception {
        try (var jar = new JarFile(cache().toFile()); var input = jar.getInputStream(jar.getJarEntry("META-INF/nekojs/mixin-plan.json"))) {
            return MixinCarrierWriter.JSON.fromJson(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), MixinPlan.class);
        }
    }
    static byte[] target() {
        var writer = new ClassWriter(0); writer.visit(V21, ACC_PUBLIC, "demo/Target", null, "java/lang/Object", null);
        writer.visitMethod(ACC_PUBLIC, "health", "()F", null, null).visitEnd(); writer.visitEnd(); return writer.toByteArray();
    }
}
