package com.tkisor.nekojs.core.pack.tool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.pack.PackDescriptor;
import com.tkisor.nekojs.core.pack.ScriptPackManifest;
import com.tkisor.nekojs.core.pack.sync.PackContentFile;
import com.tkisor.nekojs.core.pack.sync.PackHasher;
import com.tkisor.nekojs.core.pack.sync.PackSyncClient;
import com.tkisor.nekojs.core.pack.sync.ServerPackCache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Author tooling shares the manifest parser, content hash, limits and transactional installer. */
public final class PackTools {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final ScriptFilePolicy FILES = new ScriptFilePolicy(ScriptCompilerRegistry.createRuntimeRegistry());
    private static final String INVENTORY = "nekojs-pack.json";
    private PackTools() {}

    public record Content(ScriptPackManifest manifest, byte[] manifestBytes, List<PackContentFile> files, String hash) {
        public Content { manifestBytes = manifestBytes.clone(); files = List.copyOf(files); }
        @Override public byte[] manifestBytes() { return manifestBytes.clone(); }
    }

    public static Path create(Path root, String id, List<ScriptType> types) throws IOException {
        if (!id.matches(PackDescriptor.ID_PATTERN) || types.isEmpty() || types.size() != types.stream().distinct().count()) {
            throw new IOException("[NEKO-1018] Package creation requires a valid id and unique script types");
        }
        root = root.toAbsolutePath().normalize(); Files.createDirectories(root);
        Path target = root.resolve(id);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("[NEKO-1018] Package target already exists: " + target);
        Path stage = Files.createTempDirectory(root, ".nekojs-create-");
        try {
            JsonObject manifest = new JsonObject(); manifest.addProperty("formatVersion", 1);
            manifest.addProperty("id", id); manifest.addProperty("name", id); manifest.addProperty("version", "1.0.0");
            JsonObject entries = new JsonObject();
            for (var type : types) {
                var paths = new com.google.gson.JsonArray(); paths.add("main.js"); entries.add(type.name, paths);
                Path directory = stage.resolve(type.scriptsDirectoryName()); Files.createDirectory(directory);
                Files.writeString(directory.resolve("main.js"), type.defaultMainScript(), StandardCharsets.UTF_8);
                try (var declaration = PackTools.class.getResourceAsStream("/nekojs/pack/pack-api.d.ts")) {
                    if (declaration == null) throw new IOException("[NEKO-1018] Package API declaration template is missing");
                    Files.copy(declaration, directory.resolve("pack-api.d.ts"));
                }
            }
            manifest.add("entries", entries);
            Files.writeString(stage.resolve(ScriptPackManifest.FILE_NAME), JSON.toJson(manifest) + "\n", StandardCharsets.UTF_8);
            validate(stage); Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE); return target;
        } finally { if (Files.exists(stage)) deleteStage(stage); }
    }

    public static Content validate(Path root) throws IOException {
        Path manifestPath = root.resolve(ScriptPackManifest.FILE_NAME);
        rejectLink(root, root); rejectLink(manifestPath, root);
        byte[] manifestBytes = boundedRead(manifestPath, PackSyncClient.MAX_MANIFEST_BYTES);
        ScriptPackManifest manifest;
        try {
            manifest = ScriptPackManifest.parse(JsonParser.parseString(new String(manifestBytes, StandardCharsets.UTF_8)).getAsJsonObject(), root.getFileName().toString());
            if (manifest.descriptor() == null) throw new IllegalArgumentException("[NEKO-1018] Versioned author tooling requires formatVersion=1");
            manifest.descriptor().validateFiles(root, FILES);
        } catch (RuntimeException failure) { throw new IOException("[NEKO-1018] Package validation failed: " + root + ": " + failure.getMessage(), failure); }
        List<PackContentFile> files = new ArrayList<>(); long total = manifestBytes.length;
        java.util.Set<String> portablePaths = new java.util.HashSet<>();
        for (String directory : PackHasher.CONTENT_DIRS) {
            Path content = root.resolve(directory);
            if (!Files.exists(content, LinkOption.NOFOLLOW_LINKS)) continue;
            try (var walk = Files.walk(content)) {
                for (Path path : walk.sorted().toList()) {
                    rejectLink(path, root);
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    validatePath(relative, false);
                    if (!portablePaths.add(relative.toLowerCase(Locale.ROOT))) {
                        throw new IOException("[NEKO-1018] Package has paths that collide on portable filesystems: " + relative);
                    }
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                    byte[] bytes = boundedRead(path, PackSyncClient.MAX_FILE_BYTES);
                    total += bytes.length;
                    if (total > PackSyncClient.MAX_TOTAL_BUNDLE_BYTES || files.size() >= PackSyncClient.MAX_FILES_PER_PACK) {
                        throw new IOException("[NEKO-1018] Package exceeds its file count or total size limit");
                    }
                    files.add(new PackContentFile(relative, bytes));
                }
            }
        }
        files.sort(java.util.Comparator.comparing(PackContentFile::relativePath));
        return new Content(manifest, manifestBytes, files, PackHasher.hash(manifestBytes, files));
    }

    public static Path archive(Path root, Path output) throws IOException {
        Content content = validate(root);
        Map<String, byte[]> entries = contentEntries(content, "");
        JsonObject inventory = new JsonObject(); inventory.addProperty("formatVersion", 1);
        inventory.addProperty("id", content.manifest().id()); inventory.addProperty("hash", content.hash());
        var paths = new com.google.gson.JsonArray(); entries.keySet().stream().sorted().forEach(paths::add);
        inventory.add("files", paths); entries.put(INVENTORY, JSON.toJson(inventory).getBytes(StandardCharsets.UTF_8));
        if (entries.get(INVENTORY).length > PackSyncClient.MAX_MANIFEST_BYTES) {
            throw new IOException("[NEKO-1018] Package archive inventory exceeds its size limit");
        }
        return writeZip(output, entries);
    }

    public static Path install(Path archive, Path root, boolean replace) throws IOException {
        Map<String, byte[]> entries = readArchive(archive);
        byte[] inventoryBytes = entries.remove(INVENTORY), manifest = entries.remove(ScriptPackManifest.FILE_NAME);
        if (inventoryBytes == null || manifest == null) throw new IOException("[NEKO-1018] Package archive lacks its manifest or inventory");
        JsonObject inventory;
        try { inventory = JsonParser.parseString(new String(inventoryBytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException failure) { throw new IOException("[NEKO-1018] Package inventory is invalid", failure); }
        List<PackContentFile> files = entries.entrySet().stream().map(entry -> new PackContentFile(entry.getKey(), entry.getValue())).toList();
        String hash = PackHasher.hash(manifest, files);
        if (!inventory.has("formatVersion") || inventory.get("formatVersion").getAsInt() != 1
                || !inventory.has("hash") || !hash.equals(inventory.get("hash").getAsString())) {
            throw new IOException("[NEKO-1018] Package archive failed its content hash check");
        }
        List<String> actual = new ArrayList<>(entries.keySet()); actual.add(ScriptPackManifest.FILE_NAME); actual.sort(String::compareTo);
        List<String> declared = new ArrayList<>();
        try { inventory.getAsJsonArray("files").forEach(value -> declared.add(value.getAsString())); }
        catch (RuntimeException failure) { throw new IOException("[NEKO-1018] Package inventory file list is invalid", failure); }
        if (!actual.equals(declared)) throw new IOException("[NEKO-1018] Package archive content differs from its inventory");
        String id;
        try { id = JsonParser.parseString(new String(manifest, StandardCharsets.UTF_8)).getAsJsonObject().get("id").getAsString(); }
        catch (RuntimeException failure) { throw new IOException("[NEKO-1018] Package archive id is invalid", failure); }
        if (!inventory.has("id") || !id.equals(inventory.get("id").getAsString())) throw new IOException("[NEKO-1018] Package inventory id differs from its manifest");
        return ServerPackCache.installLocal(root, id, new String(manifest, StandardCharsets.UTF_8), files, replace, directory -> {
            try { if (!validate(directory).hash().equals(hash)) throw new IOException("[NEKO-1018] Content hash changed during installation"); }
            catch (IOException failure) { throw new IllegalStateException("[NEKO-1018] Installed package validation failed", failure); }
        });
    }

    /** One content mod for one supported node; NekoJS owns evaluation after loader discovery. */
    public static Path modJar(Path root, Path output, String loader, String minecraft) throws IOException {
        if (!(loader.equals("neoforge") && List.of("1.21.1", "26.1.2", "26.2").contains(minecraft)
                || loader.equals("fabric") && List.of("26.1.2", "26.2").contains(minecraft))) {
            throw new IOException("[NEKO-1018] Unsupported content mod target: " + loader + "/" + minecraft);
        }
        Content content = validate(root); String id = content.manifest().id();
        if (!id.matches("[a-z][a-z0-9_]{1,47}")) throw new IOException("[NEKO-1018] Mod id requires 2-48 lowercase letters, digits or underscores and a leading letter");
        if (List.of("nekojs", "minecraft", "neoforge", "fabricloader", "java").contains(id)) {
            throw new IOException("[NEKO-1018] Content mod id conflicts with a required runtime: " + id);
        }
        var descriptor = content.manifest().descriptor();
        if (!descriptor.loaders().isEmpty() && !descriptor.loaders().contains(loader)
                || !com.tkisor.nekojs.core.pack.PackVersion.satisfies(minecraft, descriptor.minecraft())) {
            throw new IOException("[NEKO-1018] Content mod target conflicts with manifest environment conditions");
        }
        Map<String, byte[]> entries = contentEntries(content, "nekojs/packs/" + id + "/");
        for (var file : content.files()) if (file.relativePath().startsWith("assets/") || file.relativePath().startsWith("data/")) {
            entries.put(file.relativePath(), file.bytes());
        }
        if (loader.equals("fabric")) {
            JsonObject metadata = new JsonObject(); metadata.addProperty("schemaVersion", 1); metadata.addProperty("id", id);
            metadata.addProperty("version", content.manifest().version()); metadata.addProperty("name", content.manifest().name());
            metadata.addProperty("environment", "*");
            JsonObject depends = new JsonObject(); depends.addProperty("fabricloader", ">=0.18.4");
            depends.addProperty("minecraft", minecraft); depends.addProperty("nekojs", "*"); metadata.add("depends", depends);
            entries.put("fabric.mod.json", JSON.toJson(metadata).getBytes(StandardCharsets.UTF_8));
        } else {
            String metadata = """
                    modLoader="lowcodefml"
                    loaderVersion="[1,)"
                    license="All Rights Reserved"
                    [[mods]]
                    modId=%s
                    version=%s
                    displayName=%s
                    [[dependencies.%s]]
                    modId="nekojs"
                    type="required"
                    versionRange="[0,)"
                    ordering="AFTER"
                    side="BOTH"
                    [[dependencies.%s]]
                    modId="minecraft"
                    type="required"
                    versionRange=%s
                    ordering="NONE"
                    side="BOTH"
                    """.formatted(quote(id), quote(content.manifest().version()), quote(content.manifest().name()), id, id, quote("[" + minecraft + "]"));
            if (!minecraft.equals("1.21.1")) metadata = metadata.replace("modLoader=\"lowcodefml\"\nloaderVersion=\"[1,)\"\n", "");
            entries.put("META-INF/neoforge.mods.toml", metadata.getBytes(StandardCharsets.UTF_8));
        }
        return writeZip(output, entries);
    }

    private static String quote(String value) { return new Gson().toJson(value); }

    static Map<String, byte[]> contentEntries(Content content, String prefix) {
        Map<String, byte[]> entries = new LinkedHashMap<>(); entries.put(prefix + ScriptPackManifest.FILE_NAME, content.manifestBytes());
        content.files().forEach(file -> entries.put(prefix + file.relativePath(), file.bytes())); return entries;
    }

    static Path writeZip(Path output, Map<String, byte[]> entries) throws IOException {
        output = output.toAbsolutePath().normalize();
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw new IOException("[NEKO-1018] Output already exists: " + output);
        Files.createDirectories(output.getParent()); Path stage = Files.createTempFile(output.getParent(), ".nekojs-archive-", ".tmp");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(stage), StandardCharsets.UTF_8)) {
                for (var file : entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                    ZipEntry entry = new ZipEntry(file.getKey()); entry.setTime(315532800000L);
                    entry.setMethod(ZipEntry.STORED); entry.setSize(file.getValue().length);
                    CRC32 crc = new CRC32(); crc.update(file.getValue()); entry.setCrc(crc.getValue());
                    zip.putNextEntry(entry); zip.write(file.getValue()); zip.closeEntry();
                }
            }
            Files.move(stage, output, StandardCopyOption.ATOMIC_MOVE); return output;
        } finally { Files.deleteIfExists(stage); }
    }

    private static Map<String, byte[]> readArchive(Path archive) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>(); java.util.Set<String> portableNames = new java.util.HashSet<>(); long total = 0;
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement(); String name = entry.getName();
                if (entry.isDirectory()) throw new IOException("[NEKO-1018] Package archives contain files only");
                validatePath(name, true);
                if (!portableNames.add(name.toLowerCase(Locale.ROOT)) || result.size() >= PackSyncClient.MAX_FILES_PER_PACK + 2) {
                    throw new IOException("[NEKO-1018] Package archive has duplicate paths or too many files");
                }
                int limit = name.equals(ScriptPackManifest.FILE_NAME) || name.equals(INVENTORY)
                        ? PackSyncClient.MAX_MANIFEST_BYTES : PackSyncClient.MAX_FILE_BYTES;
                byte[] bytes;
                try (var input = zip.getInputStream(entry)) { bytes = input.readNBytes(limit + 1); }
                total += bytes.length;
                if (bytes.length > limit || total > PackSyncClient.MAX_TOTAL_BUNDLE_BYTES) throw new IOException("[NEKO-1018] Package archive exceeds its size limit");
                result.put(name, bytes);
            }
        }
        return result;
    }

    public static void validatePath(String relative, boolean metadata) throws IOException {
        if (relative == null || relative.isEmpty() || relative.contains("\\") || relative.contains(":") || relative.startsWith("/")
                || relative.chars().anyMatch(character -> character < 32)) throw new IOException("[NEKO-1018] Unsafe package path: " + relative);
        for (String segment : relative.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.equals("node_modules")
                    || segment.endsWith(".") || segment.endsWith(" ") || segment.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?")) {
                throw new IOException("[NEKO-1018] Unsafe package path segment: " + relative);
            }
        }
        String first = relative.split("/", 2)[0];
        if (!PackHasher.CONTENT_DIRS.contains(first) && !(metadata && (relative.equals(INVENTORY) || relative.equals(ScriptPackManifest.FILE_NAME)))) {
            throw new IOException("[NEKO-1018] Path is outside package content directories: " + relative);
        }
    }

    private static byte[] boundedRead(Path path, int limit) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > limit) throw new IOException("[NEKO-1018] Missing or oversized package file: " + path);
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("[NEKO-1018] Package file grew beyond its limit: " + path);
            return bytes;
        }
    }

    private static void rejectLink(Path path, Path root) throws IOException {
        if (Files.isSymbolicLink(path) || !NekoJSPaths.isInside(path, root)
                || Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther()) {
            throw new IOException("[NEKO-1018] Package link or reparse path rejected: " + path);
        }
    }

    private static void deleteStage(Path stage) throws IOException {
        try (var walk = Files.walk(stage)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
