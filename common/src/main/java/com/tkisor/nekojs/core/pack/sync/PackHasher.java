package com.tkisor.nekojs.core.pack.sync;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.pack.ScriptPackManifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Pack hash (SHA-256): the raw {@code manifest.json} bytes and a 0x00, then every file in
 * relative-path order as {@code relative path + 0x00 + file bytes + 0x00}. The server gathering a
 * pack and the client rescanning what it wrote to disk use the same implementation, so the hash
 * works as a bundle integrity self-check: after writing, read the files back from disk, recompute,
 * and the result must match the expected hash.
 *
 * <p>Content directories included in the hash: the four script directories plus {@code assets/} and
 * {@code data/}, as the pack layout defines. State files such as {@code .neko_pack.state.json} are
 * excluded because they are not distributed content.
 */
public final class PackHasher {

    /** Pack content directories (hash and bundle distribution scope). */
    public static final List<String> CONTENT_DIRS = List.of(
        "startup_scripts", "server_scripts", "client_scripts", "test_scripts", "assets", "data");

    private PackHasher() {}

    /**
     * Computes the hash of a pack directory on disk: manifest bytes plus the content files. Throws when
     * the directory has no manifest.
     */
    public static String hashPackDir(Path packDir) {
        return hash(readManifestBytes(packDir), readContentFiles(packDir));
    }

    public static byte[] readManifestBytes(Path packDir) {
        Path manifest = packDir.resolve(ScriptPackManifest.FILE_NAME);
        if (!Files.isRegularFile(manifest)) {
            throw new IllegalArgumentException("Missing manifest.json in pack " + packDir);
        }
        try {
            return Files.readAllBytes(manifest);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read manifest of pack " + packDir, e);
        }
    }

    /**
     * Reads every content file under a pack directory. Relative paths use {@code /} and are sorted by
     * path.
     */
    public static List<PackContentFile> readContentFiles(Path packDir) {
        List<PackContentFile> files = new ArrayList<>();
        for (String dir : CONTENT_DIRS) {
            Path contentDir = packDir.resolve(dir);
            if (!Files.isDirectory(contentDir)) continue;
            try (Stream<Path> stream = Files.walk(contentDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("node_modules"))
                    .forEach(p -> {
                        try {
                            String relative = packDir.relativize(p).toString().replace('\\', '/');
                            files.add(new PackContentFile(relative, Files.readAllBytes(p)));
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to read pack file " + p, e);
                        }
                    });
            } catch (IOException e) {
                NekoJS.LOGGER.warn("Failed to walk pack content directory {}: {}", contentDir, e.toString());
            }
        }
        files.sort(Comparator.comparing(PackContentFile::relativePath));
        return files;
    }

    /** Hash: the manifest bytes then the files in path order, with 0x00 between elements. */
    public static String hash(byte[] manifestBytes, List<PackContentFile> files) {
        MessageDigest digest = sha256();
        digest.update(manifestBytes);
        digest.update((byte) 0);
        for (PackContentFile file : sortedByPath(files)) {
            digest.update(file.relativePath().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(file.bytes());
            digest.update((byte) 0);
        }
        return toHex(digest.digest());
    }

    static List<PackContentFile> sortedByPath(List<PackContentFile> files) {
        return files.stream().sorted(Comparator.comparing(PackContentFile::relativePath)).toList();
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
