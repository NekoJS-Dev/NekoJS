package com.tkisor.nekojs.network;

import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ScriptSyncService {
    public static final int MAX_SYNC_FILES = 512;
    public static final int MAX_SINGLE_SCRIPT_SIZE = 1048576;
    public static final int MAX_BATCH_SCRIPT_SIZE = 8388608;
    public static final int MAX_BATCH_TOTAL_SIZE = 33554432;

    private ScriptSyncService() {}

    public static String readScript(String relativePath) throws Exception {
        Path targetPath = NekoJSPaths.verifyScriptSyncPath(relativePath);
        return Files.exists(targetPath) ? Files.readString(targetPath) : null;
    }

    public static void saveScript(String relativePath, String content) throws Exception {
        ScriptSyncFiles.validateContentSize(content, MAX_SINGLE_SCRIPT_SIZE);
        Path targetPath = NekoJSPaths.verifyScriptSyncPath(relativePath);
        Files.createDirectories(targetPath.getParent());
        Files.writeString(targetPath, content);
    }

    public static Map<String, String> collectAllScripts() {
        return ScriptSyncFiles.collectAllValidScripts(NekoJSPaths.ROOT);
    }

    public static int writeBatch(Map<String, String> files) throws Exception {
        Map<String, Path> targets = ScriptSyncFiles.validateBatch(files, MAX_SYNC_FILES, MAX_BATCH_SCRIPT_SIZE, MAX_BATCH_TOTAL_SIZE);
        int count = 0;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path targetPath = targets.get(entry.getKey());
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, entry.getValue());
            count++;
        }
        return count;
    }
}
