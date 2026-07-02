package com.tkisor.nekojs.api.probe;

import com.tkisor.nekojs.NekoJS;

import java.util.ArrayList;
import java.util.List;

/**
 * 探针生成器注册表：持有当前生效的 {@link ProbeGenerator} 实现。
 *
 * <p>只允许注册一个实现。如果多个插件尝试注册，游戏将在 bootstrap 完成后崩溃，
 * 并显示冲突的 mod 列表。
 *
 * <p>使用方式：
 * <pre>{@code
 * // 在插件中替换内置 probe
 * public class MyProbePlugin implements NekoJSPlugin {
 *     @Override
 *     public void registerProbeGenerator() {
 *         ProbeRegistry.setGenerator(new MyProbeGenerator(), "MyProbeMod");
 *     }
 * }
 * }</pre>
 */
public final class ProbeRegistry {
    private static volatile ProbeGenerator generator;
    private static volatile boolean locked = false;
    private static final List<String> registrars = new ArrayList<>();

    private ProbeRegistry() {}

    /**
     * 注册探针生成器。只能注册一次。
     *
     * <p>如果已注册，将记录冲突信息。冲突会在 {@link #lock()} 时检查并抛出异常。
     *
     * @param newGenerator 新的生成器实现，不能为 null
     * @param source       注册来源描述（如 mod ID、类名），用于冲突报告
     */
    public static synchronized void setGenerator(ProbeGenerator newGenerator, String source) {
        if (newGenerator == null) {
            throw new IllegalArgumentException("ProbeGenerator cannot be null");
        }
        if (locked) {
            throw new IllegalStateException("Cannot register probe generator after bootstrap is complete");
        }

        String entry = newGenerator.name() + " (" + source + ")";
        registrars.add(entry);

        if (generator != null) {
            // 冲突 — 记录但不立即崩溃，等 lock() 时统一报告
            NekoJS.LOGGER.error("Probe generator conflict detected: {}", entry);
        } else {
            generator = newGenerator;
            NekoJS.LOGGER.info("Probe generator registered: {}", entry);
        }
    }

    /**
     * 锁定注册表（bootstrap 完成时调用）。
     *
     * <p>如果检测到多个注册者，抛出异常导致游戏崩溃，并显示冲突信息。
     */
    public static synchronized void lock() {
        locked = true;

        if (registrars.size() > 1) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n╔══════════════════════════════════════════════════════════╗\n");
            sb.append("║            [PROBE CONFLICT] 冲突！                      ║\n");
            sb.append("╚══════════════════════════════════════════════════════════╝\n\n");
            sb.append("多个 Probe 生成器被注册：\n\n");
            for (int i = 0; i < registrars.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(registrars.get(i)).append("\n");
            }
            sb.append("\n只允许一个 Probe 生成器。请移除冲突的 Probe mod 或禁用其中一个。\n");
            sb.append("\nOnly one probe generator is allowed. Remove conflicting probe mods or disable one.\n");

            String message = sb.toString();
            NekoJS.LOGGER.error(message);
            throw new IllegalStateException(message);
        }

        if (generator != null) {
            NekoJS.LOGGER.info("Probe generator locked: {}", generator.name());
        }
    }

    /**
     * 获取当前生效的探针生成器。
     *
     * @return 当前生成器，如果未设置则返回 null
     */
    public static ProbeGenerator getGenerator() {
        return generator;
    }

    /**
     * 检查是否已锁定。
     */
    public static boolean isLocked() {
        return locked;
    }

    /**
     * 获取所有注册者列表（用于诊断）。
     */
    public static List<String> getRegistrars() {
        return List.copyOf(registrars);
    }
}
