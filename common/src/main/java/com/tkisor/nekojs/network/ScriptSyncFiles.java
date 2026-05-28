package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class ScriptSyncFiles {
    private ScriptSyncFiles() {}

    public static Map<String, String> collectAllValidScripts(Path rootDir) {
        Map<String, String> files = new HashMap<>();
        if (!Files.exists(rootDir) || !Files.isDirectory(rootDir)) return files;

        try (Stream<Path> stream = Files.walk(rootDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> {
                        String relPath = rootDir.relativize(path).toString().replace('\\', '/');
                        try {
                            NekoJSPaths.verifyScriptSyncPath(relPath);
                            files.put(relPath, Files.readString(path));
                        } catch (Exception ignored) {}
                    });
        } catch (Exception e) {
            NekoJS.LOGGER.error("Failed to collect scripts from " + rootDir, e);
        }
        return files;
    }

    public static void validateContentSize(String content, int maxSize) {
        if (content.getBytes(StandardCharsets.UTF_8).length > maxSize) {
            throw new IllegalArgumentException("脚本内容超过限制");
        }
    }

    public static Map<String, Path> validateBatch(Map<String, String> files, int maxFiles, int maxSingleFileSize, int maxTotalSize) throws Exception {
        if (files.size() > maxFiles) {
            throw new IllegalArgumentException("脚本数量超过限制: " + files.size());
        }

        int totalSize = 0;
        Map<String, Path> targets = new HashMap<>();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path target = NekoJSPaths.verifyScriptSyncPath(entry.getKey());
            int size = entry.getValue().getBytes(StandardCharsets.UTF_8).length;
            if (size > maxSingleFileSize) {
                throw new IllegalArgumentException("脚本文件过大: " + entry.getKey());
            }
            totalSize += size;
            if (totalSize > maxTotalSize) {
                throw new IllegalArgumentException("脚本总大小超过限制");
            }
            targets.put(entry.getKey(), target);
        }
        return targets;
    }
}

