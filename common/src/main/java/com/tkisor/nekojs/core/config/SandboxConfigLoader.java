package com.tkisor.nekojs.core.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class SandboxConfigLoader {
    /** Reads the startup opt-in without creating files, writing defaults, or acquiring runtime/logging owners. */
    public static boolean readMixinJsEnabled(Path gameDir) {
        try {
            NekoJSPaths paths = NekoJSPaths.fromGameDir(gameDir);
            Path canonical = paths.engineConfig();
            Path selected = Files.notExists(canonical, LinkOption.NOFOLLOW_LINKS)
                    ? paths.legacyEngineConfig() : canonical;
            if (!Files.isRegularFile(selected)) return false;
            var config = new TomlParser().parse(Files.readString(selected, StandardCharsets.UTF_8));
            return booleanValue(config, "mixinjs.enabled", false);
        } catch (Exception unreadableOrMalformed) {
            return false;
        }
    }

    public SandboxConfig load(Path engineConfig) {
        return load(engineConfig, true);
    }

    /**
     * Reads the engine config. {@code writable = false} is the read-only fallback for the legacy
     * location {@code <gamedir>/nekojs/config/engine.toml}: added default keys and removed obsolete
     * keys only change the in-memory config and are never written back, because
     * {@code ClassFilter#loadEngineConfig} honours the legacy file read-only and makes no changes.
     */
    public SandboxConfig load(Path engineConfig, boolean writable) {
        var builder = CommentedFileConfig.builder(engineConfig)
                .preserveInsertionOrder();
        if (writable) {
            builder.sync().autosave();
        }
        try (CommentedFileConfig config = builder.build()) {

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

            setupConfigEntry(config, "jsxAutomaticRuntime", false,
                    " Uses the automatic JSX runtime for .jsx/.tsx scripts: emits jsx()/Fragment() calls and imports from 'nekojs/jsx-runtime' instead of the classic globalThis.__nekoJsxFactory. Requires a jsx-runtime module (place one at nekojs/node_modules/nekojs/jsx-runtime.js).");

            setupConfigEntry(config, "scriptMemberValidation", true,
                    " Enables compile-time validation of global-binding and event-callback member accesses. Reports typos and missing members to the in-game error panel. Disable to skip all AST parsing overhead in production modpacks.");

            removeConfigEntry(config, "scriptEvaluationTimeoutSeconds");
            removeConfigEntry(config, "scriptStatementLimit");
            removeConfigEntry(config, "scriptRunawayTimeoutSeconds");

            setupConfigEntry(config, "packSync.mode", SandboxConfig.PACK_SYNC_OFF,
                    " Multiplayer script pack distribution (server -> client). off (default) = disabled; hashOnly = server sends pack hashes only, clients never execute remote packs; all = server pushes packs (manifest + files) during the configuration phase, verified and executed on the client BEFORE vanilla registry validation. Only packs with clientSync != false in their manifest are synced.");

            setupConfigEntry(config, "packSync.allowUnsigned", false,
                    " Accept server script packs without an Ed25519 signature. false (default) rejects unsigned packs and disconnects with a prompt.");

            setupConfigEntry(config, "dynamicRegistry.enabled", false,
                    " Allow scripts to register NEW game content (items, etc.) AFTER the server has started, via the DynamicRegistry binding (WORLD/RELOADABLE modes with snapshot-stale tracking). Experimental; keep disabled unless you need it.");

            setupConfigEntry(config, "mixinjs.enabled", false,
                    " Enables native MixinJS startup integration. Disabled by default; changes require a full process restart. Does not enable script reflection or ASM access.");

            return new SandboxConfig(
                    config.get("allowThreads"),
                    config.get("allowReflection"),
                    config.get("allowAsm"),
                    config.get("allowFsWriteOutsideNekojs"),
                    config.get("enableEsmAuthoring"),
                    config.get("conciseScriptErrorLogs"),
                    config.get("jsxAutomaticRuntime"),
                    config.get("scriptMemberValidation"),
                    stringValue(config, "packSync.mode", SandboxConfig.PACK_SYNC_OFF),
                    booleanValue(config, "packSync.allowUnsigned", false),
                    booleanValue(config, "dynamicRegistry.enabled", false),
                    booleanValue(config, "mixinjs.enabled", false)
            );
        } catch (Throwable e) {
            NekoJS.LOGGER.warn("Failed to load nekojs/config/engine.toml, using default sandbox config", e);
            return SandboxConfig.defaultConfig();
        }
    }

    private static String stringValue(CommentedFileConfig config, String path, String fallback) {
        Object value = config.get(path);
        return value instanceof String s ? s : fallback;
    }

    private static boolean booleanValue(UnmodifiableConfig config, String path, boolean fallback) {
        Object value = config.get(path);
        return value instanceof Boolean b ? b : fallback;
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
