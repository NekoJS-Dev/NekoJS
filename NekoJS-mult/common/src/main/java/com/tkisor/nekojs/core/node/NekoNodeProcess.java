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
     * 疑似密钥的环境变量名模式（大小写不敏感、包含匹配）。共享服务器上 process.env
     * 不得向脚本泄露 AWS/TOKEN/SECRET 等敏感值；普通变量（OS、TEMP、JAVA_HOME 等）
     * 保留，避免破坏依赖环境变量的整合包脚本。
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
        // getProcessCpuTime：进程启动以来的累计 CPU 时间（纳秒；不可用时为 -1）。
        // Node 语义是 user+system 微秒累计；JVM 无法拆分 user/system，总量记入 user、system 为 0。
        // （此前用 processCpuLoad 百分比×1e6 填充 user，数值无意义。）
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
