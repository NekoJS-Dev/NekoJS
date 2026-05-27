package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.platform.Platform;

import java.lang.management.ManagementFactory;
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
        versions.put("node", "22.0.0");
        return versions;
    }

    public Map<String, String> env() {
        Map<String, String> env = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            env.put(entry.getKey(), entry.getValue());
        }
        return env;
    }

    public MemoryUsage memoryUsage() {
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory();
        long free = rt.freeMemory();
        long used = total - free;
        return new MemoryUsage(
                used,       // rss (approx heap used)
                total,      // heapTotal
                used,       // heapUsed (same as rss for simplicity)
                0,          // external
                0           // arrayBuffers
        );
    }

    public CpuUsage cpuUsage() {
        com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        double load = osBean.getProcessCpuLoad();
        long nanos = System.nanoTime();
        long userMicros = load < 0 ? 0 : (long) (load * 1000000);
        return new CpuUsage(userMicros, 0, nanos);
    }

    public long pid() {
        return ProcessHandle.current().pid();
    }

    public record MemoryUsage(long rss, long heapTotal, long heapUsed, long external, long arrayBuffers) {}
    public record CpuUsage(long user, long system, long timestamp) {}
}
