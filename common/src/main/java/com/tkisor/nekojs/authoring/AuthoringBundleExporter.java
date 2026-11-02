package com.tkisor.nekojs.authoring;

import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exports bundled documents through the game runtime's verified writable paths. */
public final class AuthoringBundleExporter {
    private AuthoringBundleExporter() {}

    public static void export(AuthoringBundle bundle, NekoJSPaths paths, boolean createAgents) throws IOException {
        export(bundle, AuthoringPaths.resolve(paths.gameDir(), AuthoringPaths.DEFAULT_DIRECTORY), createAgents);
    }

    public static void export(AuthoringBundle bundle, AuthoringPaths paths, boolean createAgents) throws IOException {
        Path destination = paths.verify(paths.bundled().resolve(bundle.id()));
        Map<String, String> outputFiles = new LinkedHashMap<>();
        for (String path : bundle.paths()) outputFiles.put(path, bundle.read(path));
        outputFiles.put("manifest.json", bundle.manifest().toString());
        // Validate every destination first so a modified existing bundle cannot become current.
        for (var entry : outputFiles.entrySet()) {
            Path file = paths.verify(destination.resolve(entry.getKey()));
            if (Files.exists(file) && !Files.readString(file).equals(entry.getValue())) throw new IOException("[NEKO-1019] Modified authoring bundle retained; export was rejected: " + file);
        }
        for (var entry : outputFiles.entrySet()) createMissing(paths.verify(destination.resolve(entry.getKey())), entry.getValue());
        if (createAgents) createMissing(paths.instructions(), bundle.read("templates/AGENTS.md"));
        createMissing(paths.bootstrap(), "# NekoJS authoring\n\nRead current.json for the active English handbook. environment.json locates the API snapshot directory. Open the game directory and install scripts only under nekojs/<type>_scripts. These role routers and AGENTS.md are reference files, not automatically discovered workspace instructions. Existing user files are preserved.\n");
        AuthoringSnapshotFiles.atomicWrite(paths.current(), AuthoringJson.obj("schemaVersion", "1", "bundleId", bundle.id(),
                "bundle", "bundled/" + bundle.id(), "manifest", "bundled/" + bundle.id() + "/manifest.json").toString());
    }

    private static void createMissing(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        try { Files.writeString(path, text, StandardOpenOption.CREATE_NEW); }
        catch (FileAlreadyExistsException ignored) { }
    }
}
