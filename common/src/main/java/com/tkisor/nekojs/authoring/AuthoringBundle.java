package com.tkisor.nekojs.authoring;

import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Validates and reads bundled documentation without requiring the game runtime. */
public final class AuthoringBundle {
    private static final String ROOT = "nekojs/authoring/";
    private final JsonObject manifest;
    private final Map<String, String> files;

    private AuthoringBundle(JsonObject manifest, Map<String, String> files) {
        this.manifest = manifest.deepCopy();
        this.files = Map.copyOf(files);
    }

    public static AuthoringBundle load() throws IOException {
        JsonObject manifest = AuthoringJson.parse(resource("manifest.json"));
        Map<String, String> files = new LinkedHashMap<>();
        for (JsonElement element : manifest.getAsJsonArray("files")) {
            JsonObject entry = element.getAsJsonObject();
            String path = AuthoringJson.string(entry, "path", "");
            Path relative = Path.of(path);
            if (relative.isAbsolute() || path.contains("\\") || path.contains(":") || Arrays.asList(path.split("/")).contains("..") || !relative.normalize().toString().replace('\\', '/').equals(path)) throw new IOException("[NEKO-1019] Invalid bundled documentation path.");
            String text = resource(path);
            if (!AuthoringJson.hash(text).equals(AuthoringJson.string(entry, "sha256", "")) || files.put(path, text) != null) throw new IOException("[NEKO-1019] Bundled documentation checksum mismatch.");
        }
        String id = AuthoringJson.string(manifest, "bundleId", "");
        if (!id.matches("[0-9a-f]{64}")) throw new IOException("[NEKO-1019] Invalid documentation bundle identity.");
        return new AuthoringBundle(manifest, files);
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = AuthoringBundle.class.getClassLoader().getResourceAsStream(ROOT + path)) {
            if (input == null) throw new IOException("[NEKO-1019] Missing bundled authoring resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String id() { return manifest.get("bundleId").getAsString(); }
    public JsonObject manifest() { return manifest.deepCopy(); }
    public Set<String> paths() { return new TreeSet<>(files.keySet()); }
    public String read(String path) {
        String text = files.get(path);
        if (text == null) throw new AuthoringException(404, "DOCUMENT_NOT_FOUND", "/uri", "Document is not in the bundled resource catalog.");
        return text;
    }

}
