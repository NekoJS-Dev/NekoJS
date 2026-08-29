package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * probe.toml 的读取与缓存（包内组件，从 {@link ProbeCoordinator} 抽出）：mtime+size 双因子
 * 缓存戳的自动重读、强制丢弃缓存、{@code enabled} 持久化。无状态耦合——持有一份
 * {@link NekoJSPaths} 绑定即一个独立缓存域（多实例互不影响）。
 */
final class ProbeConfigService {

    private final NekoJSPaths paths;
    private final ProbeConfigLoader configLoader = new ProbeConfigLoader();
    private volatile ProbeConfig cachedConfig;
    /** probe.toml 缓存戳（mtime+size 双因子）；{@link ConfigStamp#MISSING} = 无缓存/文件缺失不可读。 */
    private volatile ConfigStamp cachedConfigStamp = ConfigStamp.MISSING;

    /** 配置缓存戳：仅凭 mtime 会漏检同戳不同内容的写入（部分文件系统 mtime 粒度粗），补 size 因子。 */
    private record ConfigStamp(long mtime, long size) {
        static final ConfigStamp MISSING = new ConfigStamp(Long.MIN_VALUE, -1L);
    }

    ProbeConfigService(NekoJSPaths paths) {
        this.paths = paths;
    }

    /**
     * 读取 probe 配置：带缓存的自动重读——每次调用比对 probe.toml 的 mtime+size 戳，
     * 文件被修改（或缺失/新建）时自动重新加载，无需手动 reload。
     * 并发下两个线程可能同时重载同一文件，load 幂等、后写覆盖，结果等价。
     */
    ProbeConfig readConfig() {
        ProbeConfig c = cachedConfig;
        ConfigStamp stamp = configStamp();
        if (c == null || !stamp.equals(cachedConfigStamp)) {
            c = configLoader.load(paths.probeConfig());
            cachedConfig = c;
            // load() 可能在文件缺失时自动创建（autosave 写默认值），落盘后戳已变化；
            // 重新取一次戳，避免下次读取误判为变更而重复加载。
            cachedConfigStamp = configStamp();
        }
        return c;
    }

    /** probe.toml 的 mtime+size；缺失/不可读返回 {@link ConfigStamp#MISSING}（恒视为已变更，每次重载）。 */
    private ConfigStamp configStamp() {
        try {
            BasicFileAttributes attrs = Files.readAttributes(paths.probeConfig(), BasicFileAttributes.class);
            return new ConfigStamp(attrs.lastModifiedTime().toMillis(), attrs.size());
        } catch (IOException e) {
            return ConfigStamp.MISSING;
        }
    }

    /**
     * 丢弃配置缓存，下次 {@link #readConfig()} 重新从盘读取（供 {@code /nekojs probe reload}
     * 强制刷新——正常修改配置后 readConfig 已自动感知，无需手动调用）。
     */
    void reloadConfigCache() {
        cachedConfig = null;
        cachedConfigStamp = ConfigStamp.MISSING;
    }

    /** 把 probe.toml 的 {@code enabled} 写盘并重载缓存（供 {@code /nekojs probe enable|disable}）。写盘失败时告警：命令看似成功但配置未持久化。 */
    void applyEnabled(boolean enabled) {
        try {
            ProbeConfigLoader.setEnabled(paths.probeConfig(), enabled);
        } catch (Throwable e) {
            NekoJS.LOGGER.warn("Failed to persist probe enabled={} into {}; the setting will be lost on next reload",
                    enabled, paths.probeConfig(), e);
        }
        reloadConfigCache();
    }

    /** 当前 probe 总开关（{@code == readConfig().enabled()}）。 */
    boolean isProbeEnabled() {
        return readConfig().enabled();
    }
}
