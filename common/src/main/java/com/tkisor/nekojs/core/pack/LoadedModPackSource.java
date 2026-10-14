package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.pack.tool.PackTools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Admits only loader-discovered content; no jar scanning or context activation occurs here. */
public final class LoadedModPackSource {
    private LoadedModPackSource() {}
    /** A loader supplies the resource URI; close only a filesystem opened by this admission. */
    public static void visitManifestUri(String mod, java.net.URI manifest,
            java.util.function.BiConsumer<String, Path> visitor) {
        java.nio.file.FileSystem opened = null;
        try {
            Path path;
            try { path = Path.of(manifest); }
            catch (java.nio.file.FileSystemNotFoundException missing) {
                String uri = manifest.toString();
                java.net.URI archive = java.net.URI.create(uri.substring(0, uri.indexOf("!/")));
                try { opened = java.nio.file.FileSystems.newFileSystem(archive, Map.of()); }
                catch (java.nio.file.FileSystemAlreadyExistsException existing) { }
                path = Path.of(manifest);
            }
            visitor.accept(mod, path.getParent().getParent());
        } catch (IOException | RuntimeException failure) {
            NekoJS.LOGGER.warn("[NEKO-1018] Embedded package resource admission failed; mod={}", mod, failure);
        } finally {
            if (opened != null) try { opened.close(); }
            catch (IOException failure) { NekoJS.LOGGER.warn("[NEKO-1018] Embedded package filesystem close failed; mod={}", mod, failure); }
        }
    }
    public static List<ScriptPack> discover(Map<String, Path> roots, Path cache) {
        List<ScriptPack> packs = new ArrayList<>();
        for (var mod : roots.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (!mod.getKey().matches("[a-z0-9_-]+")) continue;
            try (var children = Files.list(mod.getValue())) {
                for (Path source : children.filter(Files::isDirectory).sorted().toList()) {
                    try {
                        if (!NekoJSPaths.isInside(source, mod.getValue())) throw new IOException("[NEKO-1018] Embedded package escapes its mod root");
                        var content = PackTools.validate(source);
                        Path directory = cache.resolve(mod.getKey()).resolve(content.hash()).resolve(content.manifest().id());
                        if (!NekoJSPaths.isInside(directory, cache)) throw new IOException("[NEKO-1018] Embedded package cache escapes its root");
                        Files.createDirectories(directory.getParent());
                        if (!Files.exists(directory)) {
                            Path stage = Files.createTempDirectory(directory.getParent(), ".nekojs-mod-");
                            try {
                                Files.write(stage.resolve(ScriptPackManifest.FILE_NAME), content.manifestBytes());
                                for (var file : content.files()) {
                                    Path target = stage.resolve(file.relativePath()); Files.createDirectories(target.getParent()); Files.write(target, file.bytes());
                                }
                                if (!PackTools.validate(stage).hash().equals(content.hash())) throw new IOException("[NEKO-1018] Embedded package content hash changed while materializing");
                                Files.move(stage, directory, StandardCopyOption.ATOMIC_MOVE);
                            } finally {
                                if (Files.exists(stage)) try (var paths = Files.walk(stage)) {
                                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
                                }
                            }
                        }
                        if (!PackTools.validate(directory).hash().equals(content.hash())) throw new IOException("[NEKO-1018] Embedded package cache was modified");
                        var manifest = content.manifest();
                        packs.add(new ScriptPack(manifest.id(), manifest.name(), manifest.version(), ScriptPackScope.MOD,
                                directory.toAbsolutePath().normalize(), manifest.enabledByDefault(), manifest));
                    } catch (IOException | RuntimeException failure) {
                        NekoJS.LOGGER.warn("[NEKO-1018] Embedded package rejected; mod={}, source={}", mod.getKey(), source, failure);
                    }
                }
            } catch (IOException failure) { NekoJS.LOGGER.warn("[NEKO-1018] Embedded package discovery failed; mod={}", mod.getKey(), failure); }
        }
        return List.copyOf(packs);
    }
}
