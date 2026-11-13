package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.backend.typescript.EventDeclarationGenerator;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.platform.Platform;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code <game>/nekojs/config/probe.toml} the same way {@code SandboxConfigLoader} does:
 * {@code CommentedFileConfig} with autosave, writing default values and their comments when a key is
 * missing. Any exception falls back to {@link ProbeConfig#defaultConfig()}.
 */
public final class ProbeConfigLoader {

    public ProbeConfig load(Path probeConfig) {
        try (CommentedFileConfig config = CommentedFileConfig.builder(probeConfig)
                .sync()
                .preserveInsertionOrder()
                .autosave()
                .build()) {

            config.load();

            setup(config, "enabled", true,
                    " Master switch for probe (.d.ts / .pyi) generation. Use /nekojs probe enable|disable to toggle.");
            setup(config, "runAtStartup", false,
                    " Run the default probe automatically once after each server start (opt-in; equivalent to `/nekojs probe` at ServerStarted). Keeps type declarations fresh without remembering the command.");
            setup(config, "baseDir", ".neko_probe",
                    " Base directory for probe output (relative to the game directory). Each language backend owns its own subdirectory under this.");

            setup(config, "scan.includePackages", List.of(),
                    " Package whitelist applied to Java class scanning. Entries are literal package prefixes (fqn startsWith 'prefix.') or regexes written as 're:<pattern>' (full-match against the fqn, e.g. re:com\\.example\\..*\\.api\\..*). If non-empty, FULLY OVERRIDES the platform defaults. Empty = use defaults (java, com.tkisor.nekojs, plus the platform's MC/loader packages).");
            setup(config, "scan.extraIncludePackages", List.of(),
                    " Extra package rules APPENDED to the effective whitelist (literal prefixes or 're:' regexes). Use this to add e.g. com.mojang or a mod's packages without retyping the defaults.");
            // The defaults come from the same source as ProbeConfig.ScanConfig.defaultScan()
            // (DEFAULT_EXCLUDE_PACKAGES, which mirrors the ClassFilter whole-package blacklist). Otherwise a
            // freshly written probe.toml would get an empty exclude list and bypass the default excludes.
            // setup only writes a default when the key is missing. An explicit empty list in an existing
            // file (excludePackages = []) is the user's override: it stays empty and excludes are off.
            setup(config, "scan.excludePackages", ProbeConfig.ScanConfig.defaultScan().excludePackages(),
                    " Package rules to EXCLUDE even when matched by the whitelist (deny-list, evaluated last; literal prefixes or 're:' regexes). Defaults mirror the ClassFilter package blacklist (nekojs core, java.desktop UI, JNDI/RMI, JDBC, Graal/Truffle) - scripts can never Java.type classes under those prefixes. Set to an explicit empty list [] to disable exclusions.");
            setup(config, "scan.forceScanMods", List.of("minecraft"),
                    " Mod IDs or package rules force-included into scanning ON TOP of the whitelist (excludePackages still wins). Built-in modId table: minecraft=net.minecraft, neoforge=net.neoforged, forge=net.minecraftforge, java=java. Entries containing '.' are literal package prefixes; 're:' entries are regexes; unknown mod IDs are ignored (debug log).");
            setup(config, "scan.maxDepth", 5,
                    " Max BFS depth for the primary scan from event/binding/adapter seeds. TypeScript separately completes referenced declarations beyond include/depth limits, while honoring excludePackages and hidden types; see @special/dependencies.json for unavailable dependencies.");
            setup(config, "scan.mode", "SMART",
                    " Scan mode: SMART = classes reachable from event/binding/adapter seeds, filtered by the whitelist + forceScanMods | FULL = whole reachable closure, whitelist bypassed but excludePackages still honored (bounded by maxDepth) | NONE = no scanning at all (probe returns a failure result).");
            setup(config, "languages.typescript.outputDir", "typescript",
                    " Per-language probe config ([languages.<languageId>]): outputDir = subdirectory under baseDir for this language's output (defaults to the language id, e.g. 'typescript'). backend = name of the preferred backend for /nekojs probe <languageId> (unset = the language's registered default backend).");
            setup(config, "languages.python.outputDir", "python",
                    " outputDir for the python backend (defaults to 'python'; a null/missing value falls back to the language id).");

            boolean enabled = config.getOrElse("enabled", Boolean.TRUE);
            boolean runAtStartup = config.getOrElse("runAtStartup", Boolean.FALSE);
            String baseDir = config.getOrElse("baseDir", ".neko_probe");
            List<String> includePackages = stringList(config, "scan.includePackages");
            List<String> extraIncludePackages = stringList(config, "scan.extraIncludePackages");
            List<String> excludePackages = stringList(config, "scan.excludePackages");
            List<String> forceScanMods = stringList(config, "scan.forceScanMods");
            int maxDepth = config.getOrElse("scan.maxDepth", 5);
            String mode = config.getOrElse("scan.mode", "SMART");
            Map<String, ProbeConfig.LanguageConfig> languages = languages(config);

            return new ProbeConfig(enabled, runAtStartup, baseDir,
                    new ProbeConfig.ScanConfig(includePackages, extraIncludePackages, excludePackages, forceScanMods, maxDepth, mode),
                    languages);
        } catch (Throwable e) {
            if (java.nio.file.Files.exists(probeConfig)) {
                // The file exists but does not parse, so probe.toml is corrupt and the user gets an explicit warning.
                // A missing file falling back to the defaults silently is normal.
                NekoJS.LOGGER.warn("probe.toml is corrupt ({}); falling back to the default probe config. "
                        + "Fix or delete the file to silence this warning.", probeConfig, e);
            }
            return ProbeConfig.defaultConfig();
        }
    }

    /** Live platform-default packages (MC + loader); combined with the fixed common set by {@link ProbeConfig}. */
    public static List<String> platformDefaultPackages() {
        return Platform.defaultScanPackages();
    }

    /**
     * Equivalent default filter of {@code ProbeOrchestrator.isRelevantClass(String)}: it filters a fully
     * qualified class name with {@link ProbeConfig#defaultConfig()} and the current platform default
     * packages. It serves call paths that cannot easily obtain a {@link ProbeContext}, such as
     * {@code EventDeclarationGenerator}.
     */
    public static boolean isRelevantClassDefault(String fqn) {
        return ProbeConfig.defaultConfig().isRelevantClass(fqn, platformDefaultPackages());
    }

    private static void setup(CommentedFileConfig config, String path, Object defaultValue, String comment) {
        if (!config.contains(path)) {
            config.set(path, defaultValue);
            config.setComment(path, comment);
        }
    }

    /**
     * Writes {@code enabled} directly into probe.toml, using the same builder options as {@link #load}
     * (sync, preserveInsertionOrder, autosave). The {@code /nekojs probe enable|disable} command reaches
     * it through {@link ProbeCoordinator#setEnabled}. Exceptions propagate; the caller swallows them at
     * debug level.
     */
    static void setEnabled(Path configFile, boolean enabled) {
        try (CommentedFileConfig config = CommentedFileConfig.builder(configFile)
                .sync()
                .preserveInsertionOrder()
                .autosave()
                .build()) {
            config.load();
            config.set("enabled", enabled);
            config.save();
        }
    }

    private static List<String> stringList(CommentedFileConfig config, String path) {
        Object raw = config.get(path);
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * Parses the {@code [languages.<languageId>]} table: takes the {@code languages} sub-table and walks
     * its keys, each of which should hold a nested Config. Returns an empty Map when the key is missing
     * or its value is not a table, which leaves every language option at its default (output directory =
     * language id, backend = registry default).
     */
    private static Map<String, ProbeConfig.LanguageConfig> languages(CommentedFileConfig config) {
        Object raw = config.get("languages");
        if (!(raw instanceof Config table)) {
            return Map.of();
        }
        Map<String, ProbeConfig.LanguageConfig> result = new LinkedHashMap<>();
        for (Config.Entry entry : table.entrySet()) {
            if (!(entry.getValue() instanceof Config langTable)) {
                continue; // The value is not a table (a string was written by mistake), so this language entry is ignored
            }
            Object backendRaw = langTable.get("backend");
            Object outputDirRaw = langTable.get("outputDir");
            String backend = backendRaw == null ? null : String.valueOf(backendRaw);
            String outputDir = outputDirRaw == null ? null : String.valueOf(outputDirRaw);
            result.put(entry.getKey(), new ProbeConfig.LanguageConfig(backend, outputDir));
        }
        return Map.copyOf(result);
    }
}
