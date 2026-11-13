package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Reads and caches probe.toml, split out of {@link ProbeCoordinator}. It reloads when the cache stamp
 * changes, supports dropping the cache, and persists {@code enabled}. It keeps no shared state: an
 * instance holds one {@link NekoJSPaths} binding and therefore owns an independent cache domain, so
 * instances do not affect each other.
 */
final class ProbeConfigService {

    private final NekoJSPaths paths;
    private final ProbeConfigLoader configLoader = new ProbeConfigLoader();
    private volatile ProbeConfig cachedConfig;
    /** Cache stamp for probe.toml (mtime plus size). {@link ConfigStamp#MISSING} means no cache, or a file
     *  that is missing or unreadable. */
    private volatile ConfigStamp cachedConfigStamp = ConfigStamp.MISSING;

    /** Cache stamp for the config. An mtime alone misses writes that keep the same timestamp but change the
     *  content, because some file systems have coarse mtime granularity, so size is the second factor. */
    private record ConfigStamp(long mtime, long size) {
        static final ConfigStamp MISSING = new ConfigStamp(Long.MIN_VALUE, -1L);
    }

    ProbeConfigService(NekoJSPaths paths) {
        this.paths = paths;
    }

    /**
     * Reads the probe config, re-reading it automatically from the cache. Each call compares the mtime
     * and size stamp of probe.toml and reloads when the file was modified, appeared or went missing, so
     * no manual reload is needed. Under concurrency two threads may reload the same file at once; load is
     * idempotent and the later write wins, so the result is the same.
     */
    ProbeConfig readConfig() {
        ProbeConfig c = cachedConfig;
        ConfigStamp stamp = configStamp();
        if (c == null || !stamp.equals(cachedConfigStamp)) {
            c = configLoader.load(paths.probeConfig());
            cachedConfig = c;
            // load() may create the file when it is missing (autosave writes the defaults), which changes the
            // stamp on disk. Take the stamp again so the next read does not mistake that for a change and
            // reload a second time.
            cachedConfigStamp = configStamp();
        }
        return c;
    }

    /**
     * Modification time and size of probe.toml. A missing or unreadable file returns
     * {@link ConfigStamp#MISSING}, which always counts as changed, so every call reloads.
     */
    private ConfigStamp configStamp() {
        try {
            BasicFileAttributes attrs = Files.readAttributes(paths.probeConfig(), BasicFileAttributes.class);
            return new ConfigStamp(attrs.lastModifiedTime().toMillis(), attrs.size());
        } catch (IOException e) {
            return ConfigStamp.MISSING;
        }
    }

    /**
     * Drops the cached config so the next {@link #readConfig()} reads from disk again.
     * {@code /nekojs probe reload} uses it to force a refresh; a normal config edit is already
     * noticed by readConfig, so callers do not need this.
     */
    void reloadConfigCache() {
        cachedConfig = null;
        cachedConfigStamp = ConfigStamp.MISSING;
    }

    /**
     * Writes {@code enabled} to probe.toml and reloads the cache. Used by
     * {@code /nekojs probe enable|disable}. A write failure only logs a warning: the command looks
     * successful but the setting is not persisted.
     */
    void applyEnabled(boolean enabled) {
        try {
            ProbeConfigLoader.setEnabled(paths.probeConfig(), enabled);
        } catch (Throwable e) {
            NekoJS.LOGGER.warn("Failed to persist probe enabled={} into {}; the setting will be lost on next reload",
                    enabled, paths.probeConfig(), e);
        }
        reloadConfigCache();
    }

    /** Current probe master switch ({@code == readConfig().enabled()}). */
    boolean isProbeEnabled() {
        return readConfig().enabled();
    }
}
