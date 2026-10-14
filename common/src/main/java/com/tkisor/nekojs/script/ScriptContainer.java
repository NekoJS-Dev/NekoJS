package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.core.pack.ScriptPackScope;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.script.prop.ScriptProperties;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ScriptContainer {
    public final ScriptId id;
    public final ScriptType type;
    public final Path path;
    public final ScriptProperties properties;

    /**
     * 所属脚本包 id；平铺脚本目录（workspace）来源的脚本为 {@code null}。
     * 用于按包归因（probe 展示 / 世界包卸载时按前缀反注册监听器）。
     */
    @Nullable
    public final String packId;
    /** 所属脚本包作用域；平铺脚本为 {@code null}。 */
    @Nullable
    public final ScriptPackScope packScope;
    /** Manifest version captured at discovery; runtime queries must not read newer metadata. */
    public final String packVersion;
    /** Immutable source snapshot attached by package discovery. */
    public com.tkisor.nekojs.core.pack.ScriptPack pack;
    /** Resolved package prerequisites; file priority cannot invert these edges. */
    public java.util.List<String> packDependencies = java.util.List.of();

    public boolean disabled = false;
    public Throwable lastError;

    public ScriptContainer(ScriptId id, ScriptType type, Path path, ScriptPropertyRegistry propertyRegistry) {
        this(id, type, path, propertyRegistry, null, null);
    }

    public ScriptContainer(ScriptId id, ScriptType type, Path path, ScriptPropertyRegistry propertyRegistry,
                           @Nullable String packId, @Nullable ScriptPackScope packScope) {
        this(id, type, path, propertyRegistry, packId, packScope, "unknown");
    }

    public ScriptContainer(ScriptId id, ScriptType type, Path path, ScriptPropertyRegistry propertyRegistry,
                           @Nullable String packId, @Nullable ScriptPackScope packScope, String packVersion) {
        this.id = id;
        this.type = type;
        this.path = path;
        this.properties = new ScriptProperties(propertyRegistry);
        this.packId = packId;
        this.packScope = packScope;
        this.packVersion = packVersion;
    }

    public boolean isType(ScriptType type) {
        return this.type == type;
    }

    public boolean shouldRun() {
        return !disabled
            && !properties.getOrDefault(ScriptProperty.DISABLE)
            && properties.getOrDefault(ScriptProperty.MODLOADED).stream().allMatch(Platform::isLoaded);
    }

    public void preload() {
        var propertyMap = properties.registry.view();

        try (var reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (!line.startsWith("//")) {
                    break;
                }
                line = line.substring("//".length()).trim();

                var parts = line.split(":", 2);
                if (parts.length < 2) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                var prop = (ScriptProperty<Object>) propertyMap.get(parts[0].trim());
                if (prop != null) {
                    try {
                        properties.put(prop, prop.read(parts[1].trim()));
                    } catch (Exception e) {
                        com.tkisor.nekojs.script.ScriptTypeEnv.logger(type).warn("Failed to parse script property '{}' in {}: {}", parts[0].trim(), path, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            disabled = true;
            lastError = e;
        }
    }
}
