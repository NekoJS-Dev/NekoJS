package com.tkisor.nekojs.core.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig.LanguagePipeline;
import com.tkisor.nekojs.core.config.SandboxConfig.ProbeReference;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class SandboxConfigLoader {
    private static final Pattern EXTENSION = Pattern.compile("[.][a-z0-9_-]+");
    private static final Set<String> PIPELINE_FIELDS = Set.of("frontend", "sourceChecks", "outputChecks", "probe");
    private static final Set<String> PROBE_FIELDS = Set.of("language", "backend");

    public record Loaded(Path path, SandboxConfig config) {}

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

    /** Reads settings and, when writable, persists defaults and removes retired keys after validation. */
    public SandboxConfig load(Path engineConfig, boolean writable) {
        try {
            boolean absent = Files.notExists(engineConfig, LinkOption.NOFOLLOW_LINKS);
            if (absent && !writable) return SandboxConfig.defaultConfig();

            var config = TomlFormat.newConfig(LinkedHashMap::new);
            Path existing = null;
            if (!absent) {
                existing = engineConfig.toRealPath();
                if (!Files.readAttributes(existing, BasicFileAttributes.class).isRegularFile()) {
                    throw new EngineConfigException(engineConfig, "Expected a regular TOML file");
                }
                String source = Files.readString(existing, StandardCharsets.UTF_8);
                new TomlParser().parse(new StringReader(source), config, ParsingMode.REPLACE);
            }
            SandboxConfig loaded = readConfig(config, engineConfig);
            if (writable && applyDefaultsAndRemoveRetiredKeys(config)) {
                var writer = new TomlWriter();
                if (existing == null) {
                    Path target = engineConfig.toAbsolutePath();
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, writer.writeToString(config), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                } else {
                    // Replace only after validation, and keep an existing symbolic link intact.
                    writer.write(config, existing, WritingMode.REPLACE_ATOMIC, StandardCharsets.UTF_8);
                }
            }
            return loaded;
        } catch (EngineConfigException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new EngineConfigException(engineConfig, "Could not read, parse, or update the TOML file", e);
        }
    }

    private static SandboxConfig readConfig(UnmodifiableConfig config, Path path) {
        for (String section : List.of("packSync", "dynamicRegistry", "mixinjs")) {
            if (config.contains(section) && !(config.get(section) instanceof UnmodifiableConfig)) {
                throw new EngineConfigException(path, "Expected a TOML table for " + section);
            }
        }
        return new SandboxConfig(
                requiredBoolean(config, path, "allowThreads", false),
                requiredBoolean(config, path, "allowReflection", false),
                requiredBoolean(config, path, "allowAsm", false),
                requiredBoolean(config, path, "allowFsWriteOutsideNekojs", false),
                requiredBoolean(config, path, "enableEsmAuthoring", true),
                requiredBoolean(config, path, "conciseScriptErrorLogs", true),
                requiredBoolean(config, path, "jsxAutomaticRuntime", false),
                requiredBoolean(config, path, "scriptMemberValidation", true),
                config.get("packSync.mode") instanceof String mode ? mode : SandboxConfig.PACK_SYNC_OFF,
                booleanValue(config, "packSync.allowUnsigned", false),
                booleanValue(config, "dynamicRegistry.enabled", false),
                booleanValue(config, "mixinjs.enabled", false),
                readLanguagePipelines(config, path)
        );
    }

    private static Map<String, LanguagePipeline> readLanguagePipelines(UnmodifiableConfig config, Path path) {
        if (config.contains("languageFrontends")) {
            throw new EngineConfigException(path, "Unknown engine setting: languageFrontends");
        }
        if (!config.contains("languagePipelines")) return Map.of();
        if (!(config.get("languagePipelines") instanceof UnmodifiableConfig pipelines)) {
            throw new EngineConfigException(path, "Expected a TOML table for languagePipelines");
        }
        Map<String, LanguagePipeline> selections = new LinkedHashMap<>();
        for (var entry : pipelines.entrySet()) {
            // Entry keys are literal; a quoted extension such as ".js" is not a NightConfig path.
            String key = entry.getKey();
            String setting = "languagePipelines[\"" + key + "\"]";
            String extension;
            try {
                extension = ScriptCompilerRegistry.normalizeExtension(key);
            } catch (IllegalArgumentException e) {
                throw new EngineConfigException(path, "Invalid script extension in " + setting, e);
            }
            if (!EXTENSION.matcher(extension).matches()) {
                throw new EngineConfigException(path, "Invalid script extension in " + setting);
            }
            if (selections.containsKey(extension)) {
                throw new EngineConfigException(path, "Duplicate script extension " + extension + " in " + setting);
            }
            if (!(entry.getValue() instanceof UnmodifiableConfig table)) {
                throw new EngineConfigException(path, "Expected a TOML table for " + setting);
            }
            for (var field : table.entrySet()) {
                if (!PIPELINE_FIELDS.contains(field.getKey())) {
                    throw new EngineConfigException(path, "Unknown pipeline field: " + setting + "." + field.getKey());
                }
            }
            selections.put(extension, new LanguagePipeline(
                    table.contains("frontend") ? providerId(table.get("frontend"), path, setting + ".frontend") : null,
                    readChecks(table, "sourceChecks", path, setting),
                    readChecks(table, "outputChecks", path, setting),
                    readProbe(table, path, setting + ".probe")));
        }
        return selections;
    }

    private static ProbeReference readProbe(UnmodifiableConfig table, Path path, String setting) {
        if (!table.contains("probe")) return null;
        if (!(table.get("probe") instanceof UnmodifiableConfig probe)) {
            throw new EngineConfigException(path, "Expected a TOML table for " + setting);
        }
        for (var field : probe.entrySet()) {
            if (!PROBE_FIELDS.contains(field.getKey())) {
                throw new EngineConfigException(path, "Unknown Probe field: " + setting + "." + field.getKey());
            }
        }
        return new ProbeReference(readProbeField(probe, "language", path, setting),
                readProbeField(probe, "backend", path, setting));
    }

    private static String readProbeField(UnmodifiableConfig probe, String field, Path path, String setting) {
        if (probe.get(field) instanceof String value && !value.isBlank()) return value;
        throw new EngineConfigException(path, "Expected a non-blank string for " + setting + "." + field);
    }

    private static List<String> readChecks(UnmodifiableConfig table, String field, Path path, String setting) {
        if (!table.contains(field)) return null;
        String stage = setting + "." + field;
        if (!(table.get(field) instanceof List<?> values)) {
            throw new EngineConfigException(path, "Expected an array of provider IDs for " + stage);
        }
        List<String> checks = new ArrayList<>(values.size());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            String id = providerId(values.get(i), path, stage + "[" + i + "]");
            if (!seen.add(id)) {
                throw new EngineConfigException(path, "Duplicate provider ID in " + stage + ": " + id);
            }
            checks.add(id);
        }
        return checks;
    }

    private static String providerId(Object value, Path path, String setting) {
        if (value instanceof String id && !id.isBlank()) return id;
        throw new EngineConfigException(path, "Expected a non-blank provider ID for " + setting);
    }

    private static boolean requiredBoolean(UnmodifiableConfig config, Path path, String key, boolean fallback) {
        if (!config.contains(key)) return fallback;
        if (config.get(key) instanceof Boolean value) return value;
        throw new EngineConfigException(path, "Expected a boolean for " + key);
    }

    private static boolean booleanValue(UnmodifiableConfig config, String path, boolean fallback) {
        Object value = config.get(path);
        return value instanceof Boolean b ? b : fallback;
    }

    private static boolean applyDefaultsAndRemoveRetiredKeys(CommentedConfig config) {
        boolean changed = false;
        changed |= setupConfigEntry(config, "allowThreads", false,
                " Allows scripts to create unmanaged background threads. May cause lag or resource leaks.");

        changed |= setupConfigEntry(config, "allowReflection", false,
                " Allows scripts to bypass access controls via reflection and modify private Java data.");

        changed |= setupConfigEntry(config, "allowAsm", false,
                " Allows scripts to directly manipulate Java bytecode. Incorrect usage may cause severe crashes.");

        changed |= setupConfigEntry(config, "allowFsWriteOutsideNekojs", false,
                " Allows Node fs write/delete operations anywhere under the game directory instead of only under nekojs/. Still blocks paths outside .minecraft.");

        changed |= removeConfigEntry(config, "prependRequirePatch");
        changed |= removeConfigEntry(config, "useNekoScriptLoader");
        changed |= removeConfigEntry(config, "useNativeEsmLoader");

        changed |= setupConfigEntry(config, "enableEsmAuthoring", true,
                " Enables ESM authoring support for .js/.mjs/.ts/.jsx/.tsx scripts. When enabled, NekoJS parses each module and transforms ESM syntax into the unified script runtime. Disable only if you need pure CommonJS require compatibility.");

        changed |= setupConfigEntry(config, "conciseScriptErrorLogs", true,
                " Emits direct source-focused script errors by default. Set false to log full verbose diagnostics and stack traces for analysis.");

        changed |= setupConfigEntry(config, "jsxAutomaticRuntime", false,
                " Uses the automatic JSX runtime for .jsx/.tsx scripts: emits jsx()/Fragment() calls and imports from 'nekojs/jsx-runtime' instead of the classic globalThis.__nekoJsxFactory. Requires a jsx-runtime module (place one at nekojs/node_modules/nekojs/jsx-runtime.js).");

        changed |= setupConfigEntry(config, "scriptMemberValidation", true,
                " Enables compile-time validation of global-binding and event-callback member accesses. Reports typos and missing members to the in-game error panel. Disable to skip all AST parsing overhead in production modpacks.");

        changed |= removeConfigEntry(config, "scriptEvaluationTimeoutSeconds");
        changed |= removeConfigEntry(config, "scriptStatementLimit");
        changed |= removeConfigEntry(config, "scriptRunawayTimeoutSeconds");

        changed |= setupConfigEntry(config, "packSync.mode", SandboxConfig.PACK_SYNC_OFF,
                " Multiplayer script pack distribution (server -> client). off (default) = disabled; hashOnly = server sends pack hashes only, clients never execute remote packs; all = server pushes packs (manifest + files) during the configuration phase, verified and executed on the client BEFORE vanilla registry validation. Only packs with clientSync != false in their manifest are synced.");

        changed |= setupConfigEntry(config, "packSync.allowUnsigned", false,
                " Accept server script packs without an Ed25519 signature. false (default) rejects unsigned packs and disconnects with a prompt.");

        changed |= setupConfigEntry(config, "dynamicRegistry.enabled", false,
                " Allow scripts to register NEW game content (items, etc.) AFTER the server has started, via the DynamicRegistry binding (WORLD/RELOADABLE modes with snapshot-stale tracking). Experimental; keep disabled unless you need it.");

        changed |= setupConfigEntry(config, "mixinjs.enabled", false,
                " Enables native MixinJS startup integration. Disabled by default; changes require a full process restart. Does not enable script reflection or ASM access.");
        return changed;
    }

    private static boolean setupConfigEntry(CommentedConfig config, String path, Object defaultValue, String comment) {
        if (config.contains(path)) return false;
        config.set(path, defaultValue);
        config.setComment(path, comment);
        return true;
    }

    private static boolean removeConfigEntry(CommentedConfig config, String path) {
        if (!config.contains(path)) return false;
        config.remove(path);
        config.setComment(path, null);
        return true;
    }
}
