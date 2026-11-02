package com.tkisor.nekojs.authoring;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/** Read-only offline discovery and query state for one explicitly selected game directory. */
final class AuthoringSnapshotSession implements AutoCloseable {
    private final AuthoringPaths paths;
    private final boolean legacy;
    private final AuthoringBundle bundle;
    private final AuthoringQuery query;
    private final String environmentId;
    private JsonObject descriptor;
    private String loadedPointer;
    private boolean closed;

    AuthoringSnapshotSession(Path gameDirectory, AuthoringBundle bundle) throws IOException {
        this(gameDirectory, bundle, null);
    }

    AuthoringSnapshotSession(Path gameDirectory, AuthoringBundle bundle, String directory) throws IOException {
        AuthoringLocator.Location location = AuthoringLocator.resolve(gameDirectory, directory);
        this.paths = location.paths();
        this.legacy = location.legacy();
        this.bundle = bundle;
        this.descriptor = readDescriptor();
        this.environmentId = AuthoringJson.string(descriptor, "environmentId", "");
        this.query = new AuthoringQuery(environmentId);
    }

    synchronized JsonObject execute(String operation, JsonObject request) {
        if (closed) throw new AuthoringException(503, "SERVICE_CLOSED", "/", "Authoring service is closed.");
        if (operation.equals("execute_game_command") || operation.equals("reload_scripts")) {
            throw new AuthoringException(409, "LIVE_RUNTIME_REQUIRED", "/", "A live server runtime is required; offline stdio never relays control requests.");
        }
        boolean context = operation.equals("context");
        boolean api = List.of("resolve", "members", "query", "batch").contains(operation);
        if (context) AuthoringJson.keys(request, "");
        if (context || api) {
            try {
                JsonObject next = readDescriptor();
                if (!environmentId.equals(AuthoringJson.string(next, "environmentId", ""))) {
                    throw new IOException("[NEKO-1019] The exported environment changed; restart the stdio process.");
                }
                descriptor = next;
                if (enabled()) refresh();
            } catch (IOException | AuthoringException | IllegalArgumentException failure) {
                if (api) throw new AuthoringException(503, "INDEX_UNAVAILABLE", "/", "The exported environment or current snapshot could not be verified; regenerate Probe or restart stdio after an environment change.");
                JsonObject state = context();
                state.addProperty("indexState", "unavailable");
                return state;
            }
            if (api && !enabled()) throw new AuthoringException(503, "INDEX_DISABLED", "/", "API snapshot queries are disabled in the exported environment.");
        }
        return context ? context() : query.execute(operation, request);
    }

    private boolean enabled() { return descriptor.get("apiEnabled").getAsBoolean(); }

    private JsonObject context() {
        JsonObject state = query.context();
        state.add("instanceId", descriptor.get("instanceId"));
        state.add("environment", descriptor.getAsJsonObject("environment").deepCopy());
        state.addProperty("transport", "stdio");
        state.addProperty("snapshotSource", "last-successful-typescript-probe");
        state.add("documentation", AuthoringJson.obj("bundleId", bundle.id(), "version", bundle.manifest().get("version"), "language", "en"));
        if (!enabled()) state.addProperty("indexState", "disabled");
        return state;
    }

    private JsonObject readDescriptor() throws IOException {
        Path file = paths.environment();
        JsonObject value = AuthoringJson.parse(AuthoringSnapshotFiles.readBounded(file, 64 * 1024));
        if (!"1".equals(AuthoringJson.string(value, "schemaVersion", ""))) throw new IOException("[NEKO-1019] Unsupported exported authoring environment schema.");
        AuthoringJson.string(value, "instanceId", "");
        JsonObject environment = AuthoringJson.object(value, "environment", "");
        String expected = "environment:" + AuthoringJson.hash(AuthoringJson.canonical(environment));
        if (!expected.equals(AuthoringJson.string(value, "environmentId", ""))) throw new IOException("[NEKO-1019] Exported authoring environment identity mismatch.");
        if (!value.has("apiEnabled") || !value.get("apiEnabled").isJsonPrimitive() || !value.getAsJsonPrimitive("apiEnabled").isBoolean()) throw new IOException("[NEKO-1019] Invalid exported API enabled flag.");
        snapshotDirectory(value);
        return value;
    }

    private Path snapshotDirectory(JsonObject value) throws IOException {
        String name = AuthoringJson.string(value, "apiSnapshotDirectory", "");
        Path directory = AuthoringPaths.resolve(paths.gameDirectory(), name).root();
        if (!legacy && !directory.equals(paths.snapshots())) {
            throw new IOException("[NEKO-1019] Snapshot directory does not match the selected authoring output root.");
        }
        return directory;
    }

    private Path inside(Path path) throws IOException {
        if (!legacy) return paths.verify(path);
        String parent = paths.gameDirectory().relativize(path.toAbsolutePath().normalize().getParent()).toString().replace('\\', '/');
        AuthoringPaths snapshotPaths = AuthoringPaths.resolve(paths.gameDirectory(), parent);
        return snapshotPaths.verify(snapshotPaths.root().resolve(path.getFileName()));
    }

    private void refresh() throws IOException {
        Path directory = snapshotDirectory(descriptor);
        Path pointer = inside(directory.resolve("current.json"));
        if (!Files.exists(pointer)) {
            if (loadedPointer != null) throw new IOException("[NEKO-1019] The current authoring snapshot pointer is missing.");
            return;
        }
        String text = AuthoringSnapshotFiles.readBounded(pointer, 4096);
        String identity = directory + "\n" + text;
        if (identity.equals(loadedPointer)) return;
        JsonObject reference = AuthoringJson.parse(text);
        String digest = AuthoringJson.string(reference, "sha256", "");
        if (!digest.matches("[0-9a-f]{64}")) throw new IOException("[NEKO-1019] Invalid authoring snapshot digest.");
        inside(directory.resolve(digest + ".json"));
        JsonObject document = AuthoringSnapshotFiles.load(directory, environmentId, text);
        if (document == null) throw new IOException("[NEKO-1019] Snapshot belongs to another environment.");
        query.publish(new AuthoringSnapshot(document));
        loadedPointer = identity;
    }

    @Override public synchronized void close() { closed = true; query.close(); }
}
