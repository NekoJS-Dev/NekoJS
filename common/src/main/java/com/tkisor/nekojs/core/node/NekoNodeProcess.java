package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.platform.Platform;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NekoNodeProcess {
    private final NekoNodeFS fs;

    /**
     * Environment variable name patterns that look like secrets (case-insensitive, substring match).
     * process.env must not leak sensitive values such as AWS/TOKEN/SECRET to scripts on a shared
     * server. Ordinary variables (OS, TEMP, JAVA_HOME and so on) are kept, so modpack scripts that
     * depend on env vars keep working.
     */
    private static final List<String> SECRET_ENV_PATTERNS = List.of(
            "password", "passwd", "secret", "token", "credential",
            "api_key", "apikey", "private_key", "access_key", "client_key");

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
        versions.put("minecraft", Platform.getMcVersion());
        versions.put("java", System.getProperty("java.version", "unknown"));
        versions.put("node", "22.0.0");
        return versions;
    }

    public Map<String, String> env() {
        Map<String, String> env = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            if (isSensitiveKey(entry.getKey())) {
                continue;
            }
            env.put(entry.getKey(), entry.getValue());
        }
        return env;
    }

    private static boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return SECRET_ENV_PATTERNS.stream().anyMatch(lower::contains);
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
        // getProcessCpuTime: CPU time accumulated since process start, in nanoseconds (-1 when unavailable).
        // Node reports accumulated user+system microseconds; the JVM cannot split the two, so the total
        // goes into user and system stays 0.
        long totalNanos = osBean.getProcessCpuTime();
        long totalMicros = totalNanos < 0 ? 0 : totalNanos / 1000L;
        return new CpuUsage(totalMicros, 0, System.nanoTime());
    }

    public long pid() {
        return ProcessHandle.current().pid();
    }

    public record MemoryUsage(long rss, long heapTotal, long heapUsed, long external, long arrayBuffers) {}
    public record CpuUsage(long user, long system, long timestamp) {}
}
