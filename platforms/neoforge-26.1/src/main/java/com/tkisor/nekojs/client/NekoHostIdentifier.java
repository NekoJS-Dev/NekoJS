package com.tkisor.nekojs.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tkisor.nekojs.NekoJS;
import net.neoforged.fml.loading.FMLPaths;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public class NekoHostIdentifier {

    private static final Path HOST_CODE_FILE = FMLPaths.GAMEDIR.get().resolve("nekojs_host_code.dat");

    /**
     * 获取硬件指纹并生成 SHA-256 主机码
     */
    public static String generateHostCode() {
        StringBuilder hwInfo = new StringBuilder();

        hwInfo.append(System.getProperty("os.name")).append(" | ");

        String cpuId = System.getenv("PROCESSOR_IDENTIFIER");
        if (cpuId == null) {
            cpuId = System.getProperty("os.arch") + " - " + Runtime.getRuntime().availableProcessors() + " Cores";
        }
        hwInfo.append(cpuId).append(" | ");

        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                long ramMb = sunOsBean.getTotalMemorySize() / (1024 * 1024);
                hwInfo.append(ramMb).append("MB RAM | ");
            } else {
                hwInfo.append(Runtime.getRuntime().maxMemory() / (1024 * 1024)).append("MB JVM | ");
            }
        } catch (Throwable t) {
            hwInfo.append("UnknownRAM | ");
        }

        try {
            hwInfo.append(RenderSystem.getApiDescription()).append(" | ");
        } catch (Throwable t) {
            hwInfo.append("UnknownGPU | ");
        }

        return sha256(hwInfo.toString());
    }

    /**
     * 标准 SHA-256 加密算法
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            NekoJS.LOGGER.debug("[NekoJS] SHA-256 generation failed", e);
            // 极端情况兜底
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * 检查当前电脑是否已经阅读过警告
     */
    public static boolean isHostAcknowledged() {
        try {
            if (Files.exists(HOST_CODE_FILE)) {
                String savedCode = Files.readString(HOST_CODE_FILE).trim();
                return savedCode.equals(generateHostCode());
            }
        } catch (Exception e) {
            NekoJS.LOGGER.debug("[NekoJS] Failed to read host code file", e);
        }
        return false;
    }

    /**
     * 保存当前电脑的主机码
     */
    public static void saveHostCode() {
        try {
            Files.writeString(HOST_CODE_FILE, generateHostCode());
            NekoJS.LOGGER.debug("[NekoJS] Host code saved to: {}", HOST_CODE_FILE.toAbsolutePath());
        } catch (Exception e) {
            NekoJS.LOGGER.debug("[NekoJS] Failed to save host code file", e);
        }
    }
}