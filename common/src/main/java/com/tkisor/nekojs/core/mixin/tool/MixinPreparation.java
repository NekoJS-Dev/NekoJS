package com.tkisor.nekojs.core.mixin.tool;

import com.tkisor.nekojs.core.mixin.MixinPlan;
import com.tkisor.nekojs.core.mixin.MixinLibrarySelection;
import java.nio.file.*;
import java.util.List;
import java.util.jar.JarFile;

/** Publishes immutable game libraries and atomically selects one for the next launch. */
public final class MixinPreparation {
    public static final String CACHE_PREFIX = "nekojs-mixins-auto-";
    private MixinPreparation() {}

    public static Path cache(Path game, String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) throw MixinPlan.invalid("Invalid managed mixin hash");
        return game.resolve("mods").resolve(CACHE_PREFIX + hash + ".jar");
    }

    /** Returns true when this launch must stop before callbacks may be installed. */
    public static boolean prepare(Path gameDirectory, MixinPlan.Profile profile, boolean development,
                                  List<MixinPlan> admitted, MixinCompiler.TargetBytes targets) throws Exception {
        if (!MixinCarrierWriter.gameLibrary(profile)) throw MixinPlan.invalid("Automatic preparation requires a supported modern NeoForge profile");
        Path game = gameDirectory.toAbsolutePath().normalize();
        Path source = game.resolve("nekojs/mixins");
        if (Files.isSymbolicLink(game.resolve("mods"))) throw MixinPlan.invalid("Managed mixin directory must not be a symbolic link");
        String selected = MixinLibrarySelection.selected(game);
        MixinPlan previous = selected == null ? null : owned(cache(game, selected));
        if (selected != null && previous == null) throw MixinPlan.invalid("Selected managed mixin library is missing");
        boolean declarations = false;
        if (Files.exists(source)) {
            if (!Files.isDirectory(source)) throw MixinPlan.invalid("Mixin declaration root is not a directory");
            try (var paths = Files.walk(source)) {
                declarations = paths.anyMatch(path -> Files.isRegularFile(path)
                        && (path.toString().endsWith(".mixin.js") || path.toString().endsWith(".mixin.ts")));
            }
        }
        if (!declarations) {
            if (previous == null) return false;
            MixinLibrarySelection.select(game, null);
            return admitted.stream().anyMatch(plan -> plan.hash().equals(previous.hash()));
        }
        if (!development) throw MixinPlan.invalid("Automatic MixinJS preparation is development-only until production mapping acceptance is completed");
        for (var plan : admitted) {
            if (previous == null || !plan.hash().equals(previous.hash())) {
                throw MixinPlan.invalid("Remove manually installed MixinJS artifacts before using automatic workspace preparation");
            }
        }
        var compiled = MixinCompiler.compile(source, profile, targets);
        if (previous != null && previous.equals(compiled.plan())) {
            if (admitted.stream().noneMatch(plan -> plan.equals(previous))) {
                throw MixinPlan.invalid("The managed mixin library was not admitted by the loader; verify the mods directory and restart");
            }
            return false;
        }
        Path output = cache(game, compiled.plan().hash());
        Files.createDirectories(output.getParent());
        MixinPlan existing = owned(output);
        if (existing != null && !existing.equals(compiled.plan())) throw MixinPlan.invalid("Managed mixin library hash collision");
        if (existing == null) {
            Path staging = Files.createTempFile(output.getParent(), CACHE_PREFIX, ".pending");
            try {
                Files.delete(staging); // The writer is create-new; staging never holds user data.
                MixinCarrierWriter.write(staging, compiled.plan(), compiled.sources(), true);
                Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(staging);
            }
        }
        // Loaded archives can be locked on Windows. Old hashes stay inert, never overwritten.
        MixinLibrarySelection.select(game, compiled.plan().hash());
        return true;
    }

    private static MixinPlan owned(Path cache) throws Exception {
        if (!Files.exists(cache, LinkOption.NOFOLLOW_LINKS)) return null;
        if (Files.isSymbolicLink(cache) || !Files.isRegularFile(cache)) throw MixinPlan.invalid("Managed mixin cache is not a regular file");
        try (var jar = new JarFile(cache.toFile())) {
            var manifest = jar.getManifest();
            var entry = jar.getJarEntry("META-INF/nekojs/mixin-plan.json");
            if (manifest == null || entry == null || entry.getSize() < 0 || entry.getSize() > 1_048_576
                    || !MixinCarrierWriter.FORMAT.equals(manifest.getMainAttributes().getValue("NekoJS-Mixin-Format"))
                    || !"GAMELIBRARY".equals(manifest.getMainAttributes().getValue("FMLModType"))) {
                throw MixinPlan.invalid("Refusing to use an unowned mixin cache file");
            }
            MixinPlan plan;
            try (var input = jar.getInputStream(entry)) {
                plan = MixinCarrierWriter.JSON.fromJson(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), MixinPlan.class);
            }
            if (plan == null || !("nekojs_mixins_" + plan.hash().substring(0, 16))
                    .equals(manifest.getMainAttributes().getValue("NekoJS-Mixin-Owner"))
                    || !plan.hash().equals(manifest.getMainAttributes().getValue("NekoJS-Mixin-Managed"))
                    || !cache.getFileName().toString().equals(CACHE_PREFIX + plan.hash() + ".jar")) {
                throw MixinPlan.invalid("Managed mixin cache owner/hash mismatch");
            }
            return plan;
        }
    }
}
