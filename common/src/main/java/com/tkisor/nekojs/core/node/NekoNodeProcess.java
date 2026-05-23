package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.platform.Platform;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NekoNodeProcess {
    private final NekoNodeFS fs;

    public NekoNodeProcess(NekoNodeFS fs) {
        this.fs = fs;
    }

    public String cwd() {
        return fs.cwd();
    }

    public void chdir(String path) throws Exception {
        fs.chdir(path);
    }

    public String platform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "win32";
        if (os.contains("mac") || os.contains("darwin")) return "darwin";
        return "linux";
    }

    public Map<String, String> versions() {
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("nekojs", "1.0.7");
        versions.put("minecraft", Platform.getMcVersion());
        versions.put("java", System.getProperty("java.version", "unknown"));
        return versions;
    }

    public Map<String, String> env() {
        return Map.of();
    }

    public long pid() {
        return ProcessHandle.current().pid();
    }
}
