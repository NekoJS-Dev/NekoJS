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
 * 包哈希（SHA-256）：{@code manifest.json 原始字节 + 0x00} 之后按相对路径排序追加
 * {@code 相对路径 + 0x00 + 文件字节 + 0x00}。服务端gather与客户端落盘重扫使用同一实现，
 * 作为 bundle 完整性自检（写盘后从盘重读重算必须与预期一致）。
 *
 * <p>参与哈希的内容目录：四个脚本目录 + {@code assets/} + {@code data/}
 * （包形态约定，见 katton-adoption-plan §4）。{@code .neko_pack.state.json} 等状态文件
 * 不参与——它们不属于分发内容。
 */
public final class PackHasher {

    /** 包内容目录（哈希与 bundle 分发范围）。 */
    public static final List<String> CONTENT_DIRS = List.of(
        "startup_scripts", "server_scripts", "client_scripts", "test_scripts", "assets", "data");

    private PackHasher() {}

    /** 计算磁盘包目录的哈希：manifest 字节 + 内容文件。目录缺 manifest 时抛出。 */
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

    /** 读取包目录下全部内容文件（相对路径以 {@code /} 分隔、按路径排序）。 */
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

    /** 哈希：manifest 字节 + 按路径排序的文件流，元素间以 0x00 分隔。 */
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
