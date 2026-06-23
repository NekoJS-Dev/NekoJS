package com.tkisor.nekojs.core.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.tkisor.nekojs.NekoJS;

import java.nio.file.Path;

public final class SandboxConfigLoader {
    public SandboxConfig load(Path engineConfig) {
        try (CommentedFileConfig config = CommentedFileConfig.builder(engineConfig)
                .sync()
                .preserveInsertionOrder()
                .autosave()
                .build()) {

            config.load();

            setupConfigEntry(config, "allowThreads", false,
                    " Allows scripts to create unmanaged background threads. May cause lag or resource leaks.");

            setupConfigEntry(config, "allowReflection", false,
                    " Allows scripts to bypass access controls via reflection and modify private Java data.");

            setupConfigEntry(config, "allowAsm", false,
                    " Allows scripts to directly manipulate Java bytecode. Incorrect usage may cause severe crashes.");

            setupConfigEntry(config, "allowFsWriteOutsideNekojs", false,
                    " Allows Node fs write/delete operations anywhere under the game directory instead of only under nekojs/. Still blocks paths outside .minecraft.");

            removeConfigEntry(config, "prependRequirePatch");
            removeConfigEntry(config, "useNekoScriptLoader");
            removeConfigEntry(config, "useNativeEsmLoader");

            setupConfigEntry(config, "enableEsmAuthoring", true,
                    " Enables ESM authoring support for .js/.mjs/.ts/.jsx/.tsx scripts. When enabled, NekoJS parses each module and transforms ESM syntax into the unified script runtime. Disable only if you need pure CommonJS require compatibility.");

            setupConfigEntry(config, "conciseScriptErrorLogs", true,
                    " Emits direct source-focused script errors by default. Set false to log full verbose diagnostics and stack traces for analysis.");

            return new SandboxConfig(
                    config.get("allowThreads"),
                    config.get("allowReflection"),
                    config.get("allowAsm"),
                    config.get("allowFsWriteOutsideNekojs"),
                    config.get("enableEsmAuthoring"),
                    config.get("conciseScriptErrorLogs")
            );
        } catch (Exception e) {
            NekoJS.LOGGER.warn("Failed to load engine.toml, using default sandbox config", e);
            return SandboxConfig.defaultConfig();
        }
    }

    private static void setupConfigEntry(CommentedFileConfig config, String path, Object defaultValue, String comment) {
        if (!config.contains(path)) {
            config.set(path, defaultValue);
            config.setComment(path, comment);
        }
    }

    private static void removeConfigEntry(CommentedFileConfig config, String path) {
        if (config.contains(path)) {
            config.remove(path);
            config.setComment(path, null);
        }
    }
}
