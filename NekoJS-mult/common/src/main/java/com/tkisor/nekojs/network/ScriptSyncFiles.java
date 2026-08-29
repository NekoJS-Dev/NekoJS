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

        long[] totalSize = {0L};
        try (Stream<Path> stream = Files.walk(rootDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> {
                        String relPath = rootDir.relativize(path).toString().replace('\\', '/');
                        try {
                            NekoJSPaths.get().verifyScriptSyncPath(relPath);
                        } catch (Exception ignored) {
                            return; // invalid sync path → skip this file (non-script files)
                        }

                        long size;
                        try {
                            size = Files.size(path);
                        } catch (Exception e) {
                            // 服务器→客户端同步静默丢文件是「客户端跑旧脚本/缺脚本」的最隐蔽来源，
                            // 必须可见（W4/A5）；invalid sync path 的跳过仍保持静默（非脚本文件属预期）
                            com.tkisor.nekojs.core.error.Diagnostics.report(
                                    "script-sync",
                                    com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                                    "跳过无法读取大小的脚本文件，该文件不会同步给客户端: " + relPath, e);
                            return;
                        }

                        if (size > ScriptSyncService.MAX_BATCH_SCRIPT_SIZE) {
                            throw new IllegalStateException("脚本文件过大: " + relPath + " (" + size + " bytes, 最大 " + ScriptSyncService.MAX_BATCH_SCRIPT_SIZE + ")");
                        }
                        if (files.size() >= ScriptSyncService.MAX_SYNC_FILES) {
                            throw new IllegalStateException("脚本数量超过限制: " + (files.size() + 1) + " (最大 " + ScriptSyncService.MAX_SYNC_FILES + ")");
                        }
                        if (totalSize[0] + size > ScriptSyncService.MAX_BATCH_TOTAL_SIZE) {
                            throw new IllegalStateException("脚本总大小超过限制: " + (totalSize[0] + size) + " bytes (最大 " + ScriptSyncService.MAX_BATCH_TOTAL_SIZE + ")");
                        }

                        try {
                            String content = Files.readString(path);
                            files.put(relPath, content);
                            totalSize[0] += size;
                        } catch (Exception e) {
                            com.tkisor.nekojs.core.error.Diagnostics.report(
                                    "script-sync",
                                    com.tkisor.nekojs.core.error.Diagnostics.Severity.WARN,
                                    "脚本文件读取失败，该文件不会同步给客户端: " + relPath, e);
                        }
                    });
        } catch (IllegalStateException e) {
            throw e;
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
            Path target = NekoJSPaths.get().verifyScriptSyncPath(entry.getKey());
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

