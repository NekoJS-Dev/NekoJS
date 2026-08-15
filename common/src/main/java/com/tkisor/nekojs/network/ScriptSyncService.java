package com.tkisor.nekojs.network;

import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.api.ScriptType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ScriptSyncService {
    public static final int MAX_SYNC_FILES = 512;
    public static final int MAX_SINGLE_SCRIPT_SIZE = 1048576;
    public static final int MAX_BATCH_SCRIPT_SIZE = 8388608;
    public static final int MAX_BATCH_TOTAL_SIZE = 33554432;

    private static volatile ErrorTracker errorTracker;

    public static void bindErrorTracker(ErrorTracker tracker) {
        errorTracker = tracker;
    }

    private ScriptSyncService() {}

    public static String readScript(String relativePath) throws Exception {
        Path targetPath = NekoJSPaths.get().verifyScriptSyncPath(relativePath);
        return Files.exists(targetPath) ? Files.readString(targetPath) : null;
    }

    public static void saveScript(String relativePath, String content) throws Exception {
        ScriptSyncFiles.validateContentSize(content, MAX_SINGLE_SCRIPT_SIZE);
        Path targetPath = NekoJSPaths.get().verifyScriptSyncPath(relativePath);
        Files.createDirectories(targetPath.getParent());
        Files.writeString(targetPath, content);
        clearErrorsFor(relativePath);
    }

    public static Map<String, String> collectAllScripts() {
        Map<String, String> files = ScriptSyncFiles.collectAllValidScripts(NekoJSPaths.get().root());
        if (files.size() > MAX_SYNC_FILES) {
            throw new IllegalStateException("脚本数量超过限制: " + files.size() + " (最大 " + MAX_SYNC_FILES + ")");
        }
        int totalSize = 0;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            int size = entry.getValue().getBytes(StandardCharsets.UTF_8).length;
            if (size > MAX_BATCH_SCRIPT_SIZE) {
                throw new IllegalStateException("脚本文件过大: " + entry.getKey() + " (" + size + " bytes, 最大 " + MAX_BATCH_SCRIPT_SIZE + ")");
            }
            totalSize += size;
            if (totalSize > MAX_BATCH_TOTAL_SIZE) {
                throw new IllegalStateException("脚本总大小超过限制: " + totalSize + " bytes (最大 " + MAX_BATCH_TOTAL_SIZE + ")");
            }
        }
        return files;
    }

    public static int writeBatch(Map<String, String> files) throws Exception {
        Map<String, Path> targets = ScriptSyncFiles.validateBatch(files, MAX_SYNC_FILES, MAX_BATCH_SCRIPT_SIZE, MAX_BATCH_TOTAL_SIZE);
        int count = 0;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path targetPath = targets.get(entry.getKey());
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, entry.getValue());
            clearErrorsFor(entry.getKey());
            count++;
        }
        return count;
    }

    private static void clearErrorsFor(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        ErrorTracker tracker = errorTracker;
        if (tracker == null) return;
        for (ScriptType type : ScriptType.all()) {
            if (normalized.startsWith(type.path.getFileName().toString() + "/")) {
                tracker.clearByScriptPath(type, normalized);
                return;
            }
        }
    }
}
