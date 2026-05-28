package com.tkisor.nekojs.core.node;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NekoNodeOS {
    public String arch() {
        return System.getProperty("os.arch", "x64");
    }

    public String platform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "win32";
        if (os.contains("mac") || os.contains("darwin")) return "darwin";
        return "linux";
    }

    public List<CpuInfo> cpus() {
        int processors = Runtime.getRuntime().availableProcessors();
        String model = System.getProperty("os.arch", "unknown");
        List<CpuInfo> list = new ArrayList<>();
        for (int i = 0; i < processors; i++) {
            long speed = 0;
            try {
                var osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                speed = osBean.getProcessCpuLoad() > 0 ? 1000 : 2000; // rough estimate
            } catch (Exception ignored) {}
            var times = new CpuTimes(0, 0, 0, 0);
            list.add(new CpuInfo(model, speed, times));
        }
        return list;
    }

    public long freemem() {
        return Runtime.getRuntime().freeMemory();
    }

    public long totalmem() {
        return Runtime.getRuntime().maxMemory();
    }

    public String homedir() {
        return System.getProperty("user.home", ".");
    }

    public String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }

    public String tmpdir() {
        return System.getProperty("java.io.tmpdir", "/tmp");
    }

    public double uptime() {
        return ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0;
    }

    public UserInfo userInfo() {
        return new UserInfo(
                System.getProperty("user.name", "unknown"),
                homedir(),
                System.getProperty("user.name", "unknown")
        );
    }

    public Map<String, List<NetworkAddress>> networkInterfaces() {
        Map<String, List<NetworkAddress>> result = new LinkedHashMap<>();
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                var ni = interfaces.nextElement();
                List<NetworkAddress> addresses = new ArrayList<>();
                var inetAddresses = ni.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    var addr = inetAddresses.nextElement();
                    addresses.add(new NetworkAddress(
                            addr.getHostAddress(),
                            addr.getHostAddress().contains(":") ? "IPv6" : "IPv4",
                            "IPv4",
                            addr.getHostAddress().contains(":") ? 16 : 4,
                            addr.getHostAddress()
                    ));
                }
                if (!addresses.isEmpty()) {
                    result.put(ni.getName(), addresses);
                }
            }
        } catch (SocketException ignored) {}
        return result;
    }

    public String endianness() {
        return "LE";
    }

    public double loadavg1() { return -1; }
    public double loadavg5() { return -1; }
    public double loadavg15() { return -1; }

    public record CpuInfo(String model, long speed, CpuTimes times) {}
    public record CpuTimes(long user, long nice, long sys, long idle) {}
    public record UserInfo(String username, String homedir, String shell) {}
    public record NetworkAddress(String address, String family, String type, int prefixLength, String ip) {}
}
