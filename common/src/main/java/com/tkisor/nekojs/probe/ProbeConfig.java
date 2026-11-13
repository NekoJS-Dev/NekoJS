package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Configuration for probe behavior ({@code <game>/nekojs/config/probe.toml}).
 *
 * <p>It holds the master switch, the output base directory and the class scan include and exclude
 * lists. The default include list reproduces the five prefixes of
 * {@code ProbeOrchestrator.isRelevantClass}: {@code java} and {@code com.tkisor.nekojs}, which this
 * class always appends, plus the MC and loader packages that
 * {@code IPlatform.defaultScanPackages()} injects. Use {@code extraIncludePackages} to add packages
 * such as {@code com.mojang}. The default exclude list mirrors the whole-package prefixes of the
 * {@code ClassFilter} class lookup blacklist ({@code com.tkisor.nekojs.core},
 * {@code java.awt}/{@code javax.swing}/{@code javax.imageio}, {@code javax.naming}/{@code java.rmi},
 * {@code java.sql}/{@code javax.sql}, {@code org.graalvm}/{@code com.oracle.truffle}). Scripts can
 * never load classes under those prefixes with {@code Java.type}, so declarations for them would only
 * point at unusable types.
 *
 * <p>Scan mode ({@link ScanConfig#mode()}): {@code SMART} is the default and uses the whitelist plus
 * the {@code forceScanMods} additions; {@code FULL} skips the include whitelist and is limited only by
 * the excludes and maxDepth; {@code NONE} skips scanning entirely and probe returns a failure result.
 *
 * <p>{@link #languages()} holds the per-language configuration ({@code [languages.<languageId>]}).
 * Each language can name the backend to prefer and a custom output subdirectory. Both may be absent
 * (null falls back to the default behavior).
 */
public record ProbeConfig(boolean enabled, boolean runAtStartup, String baseDir, ScanConfig scan, Map<String, LanguageConfig> languages) {

    /**
     * Configuration for one language (the {@code [languages.<languageId>]} table):
     * {@code backend} is the backend name the command prefers (null uses the registry default for that
     * language); {@code outputDir} is the output subdirectory under {@code baseDir} (null uses the
     * language id itself).
     */
    public record LanguageConfig(String backend, String outputDir) {
    }

    /** Compatibility constructor that keeps the four-argument form, with {@code runAtStartup} set to false,
     *  so existing call sites still compile. */
    public ProbeConfig(boolean enabled, String baseDir, ScanConfig scan, Map<String, LanguageConfig> languages) {
        this(enabled, false, baseDir, scan, languages);
    }

    /** Three-argument convenience constructor, equivalent to {@code (enabled, false, baseDir, scan, Map.of())};
     *  it keeps existing call sites source-compatible. */
    public ProbeConfig(boolean enabled, String baseDir, ScanConfig scan) {
        this(enabled, false, baseDir, scan, Map.of());
    }

    /** Primary scan filters; TypeScript completes emitted dependencies separately while retaining excludes. */
    public record ScanConfig(
            List<String> includePackages,
            List<String> extraIncludePackages,
            List<String> excludePackages,
            List<String> forceScanMods,
            int maxDepth,
            ScanMode mode          // SMART | FULL | NONE
    ) {
        /**
         * Default exclude prefixes: the whole-package entries of {@code GENERAL_BLACKLIST} in
         * {@code core/fs/ClassFilter}. Scripts cannot load classes under these prefixes with
         * {@code Java.type}, so declaring them would only mislead. They apply to the defaults only
         * ({@link #defaultScan()} and {@code ProbeConfig.defaultConfig()}). A ScanConfig built explicitly,
         * including golden-test configs, is unaffected, and {@code scan.excludePackages} in probe.toml still
         * overrides the list.
         */
        private static final List<String> DEFAULT_EXCLUDE_PACKAGES = List.of(
                "com.tkisor.nekojs.core",
                // Desktop client APIs (Robot input, screenshots, clipboard), JNDI/RMI remote loading and JDBC
                "java.awt", "javax.swing", "javax.imageio", "javax.naming", "java.rmi",
                "java.sql", "javax.sql",
                // Graal/Truffle engine internals
                "org.graalvm", "com.oracle.truffle");

        public static ScanConfig defaultScan() {
            return new ScanConfig(List.of(), List.of(), DEFAULT_EXCLUDE_PACKAGES, List.of("minecraft"), 5, ScanMode.SMART);
        }

        public ScanConfig {
            includePackages = includePackages == null ? List.of() : List.copyOf(includePackages);
            extraIncludePackages = extraIncludePackages == null ? List.of() : List.copyOf(extraIncludePackages);
            excludePackages = excludePackages == null ? List.of() : List.copyOf(excludePackages);
            forceScanMods = forceScanMods == null ? List.of() : List.copyOf(forceScanMods);
            mode = mode == null ? ScanMode.SMART : mode;
        }

        /** String-based convenience constructor for the string values in probe.toml. Parsing is
         *  case-insensitive; an unknown value falls back to SMART, see {@link ScanMode#parse}. */
        public ScanConfig(List<String> includePackages, List<String> extraIncludePackages,
                          List<String> excludePackages, List<String> forceScanMods,
                          int maxDepth, String mode) {
            this(includePackages, extraIncludePackages, excludePackages, forceScanMods, maxDepth,
                    ScanMode.parse(mode));
        }

        /** Scan mode, from {@code scan.mode} in {@code probe.toml}. */
        public enum ScanMode {
            SMART, FULL, NONE;

            /**
             * Parses a mode name case-insensitively. A {@code null} or blank value returns {@link #SMART}. An
             * unknown value logs a warning and falls back to {@link #SMART}, which keeps the whitelist filter that
             * is the default.
             */
            public static ScanMode parse(String raw) {
                if (raw == null || raw.isBlank()) return SMART;
                try {
                    return valueOf(raw.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException unknown) {
                    NekoJS.LOGGER.warn(
                            "probe.toml scan.mode: unknown mode '{}' (expected SMART | FULL | NONE); falling back to SMART",
                            raw);
                    return SMART;
                }
            }
        }
    }

    public static ProbeConfig defaultConfig() {
        return new ProbeConfig(true, ".neko_probe", ScanConfig.defaultScan(),
                Map.of(
                        "typescript", new LanguageConfig(null, "typescript"),
                        "python", new LanguageConfig(null, "python")));
    }

    public ProbeConfig {
        baseDir = (baseDir == null || baseDir.isBlank()) ? ".neko_probe" : baseDir;
        scan = scan == null ? ScanConfig.defaultScan() : scan;
        languages = languages == null || languages.isEmpty() ? Map.of() : Map.copyOf(languages);
    }

    /** Returns the per-language configuration for the language id, or an empty Optional when the language
     *  is not configured. */
    public Optional<LanguageConfig> language(String languageId) {
        return Optional.ofNullable(languages.get(languageId));
    }

    /* ================= Package rule matching (literal prefix / re: regex) ================= */

    /** Prefix of a regex rule entry: {@code re:<regex>}, for example {@code re:com\.example\..*\.api\..*}. */
    public static final String REGEX_RULE_PREFIX = "re:";

    /**
     * Caches compiled regex rules, including a negative entry for rules that fail to compile. A scan runs
     * every rule against every candidate class name, and the cache saves one {@link Pattern#compile} per
     * class.
     */
    private static final Map<String, Optional<Pattern>> PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * Tests one package rule against a fully qualified class name. A rule entry takes one of two forms:
     * <ul>
     *   <li>literal package prefix (the default): {@code fqn.startsWith(rule + ".")}</li>
     *   <li>regex, when the entry starts with {@code re:} (for example {@code re:com\.example\.(mod[12])\..*}):
     *       a <b>full match</b> against the fqn ({@link java.util.regex.Matcher#matches()}). Compilation
     *       results are cached. An invalid regex logs one warning and that entry then never matches</li>
     * </ul>
     */
    public static boolean matchesPackageRule(String rule, String fqn) {
        if (rule == null || rule.isBlank() || fqn == null || fqn.isEmpty()) return false;
        if (!rule.startsWith(REGEX_RULE_PREFIX)) {
            return fqn.startsWith(rule + ".");
        }
        return PATTERN_CACHE.computeIfAbsent(rule, ProbeConfig::compileRule)
                .map(pattern -> pattern.matcher(fqn).matches())
                .orElse(false);
    }

    private static Optional<Pattern> compileRule(String rule) {
        try {
            return Optional.of(Pattern.compile(rule.substring(REGEX_RULE_PREFIX.length())));
        } catch (PatternSyntaxException e) {
            NekoJS.LOGGER.warn("probe.toml package rule '{}' is not a valid regex; the rule never matches", rule, e);
            return Optional.empty();
        }
    }

    /**
     * Builds the include package prefix set that is actually in effect:
     * <ul>
     *   <li>{@code includePackages} is non-empty -> it fully replaces the defaults</li>
     *   <li>otherwise -> the fixed defaults {@code {java, com.tkisor.nekojs}} plus the packages the
     *       platform injects</li>
     *   <li>{@code extraIncludePackages} is appended last</li>
     * </ul>
     */
    public Set<String> effectiveIncludePackages(List<String> platformDefaultPackages) {
        Set<String> result = new LinkedHashSet<>();
        if (scan.includePackages().isEmpty()) {
            result.add("java");
            result.add("com.tkisor.nekojs");
            if (platformDefaultPackages != null) {
                result.addAll(platformDefaultPackages);
            }
        } else {
            result.addAll(scan.includePackages());
        }
        result.addAll(scan.extraIncludePackages());
        return result;
    }

    /**
     * Decides whether a fully qualified class name passes the package filter: it matches an include rule
     * and no exclude rule. Entry forms are described in {@link #matchesPackageRule}: a literal package
     * prefix ({@code fqn.startsWith(pkg + ".")}, the form {@code isRelevantClass} uses) or a {@code re:}
     * regex that must match the whole name.
     */
    public boolean isRelevantClass(String fqn, List<String> platformDefaultPackages) {
        if (fqn == null || fqn.isEmpty()) return false;
        Set<String> included = effectiveIncludePackages(platformDefaultPackages);
        boolean hit = false;
        for (String pkg : included) {
            if (matchesPackageRule(pkg, fqn)) { hit = true; break; }
        }
        if (!hit) return false;
        return !isExcluded(fqn);
    }

    /**
     * Tests only the exclude rules, split out of {@link #isRelevantClass} for reuse: it returns true when
     * the fully qualified name matches any {@code scan.excludePackages} rule, either a prefix or a
     * {@code re:} regex (see {@link #matchesPackageRule}). FULL mode uses it to keep excludes while
     * skipping the include whitelist, and the {@code ProbeCoordinator} filtering helpers use it too.
     */
    public boolean isExcluded(String fqn) {
        if (fqn == null || fqn.isEmpty()) return false;
        for (String pkg : scan.excludePackages()) {
            if (matchesPackageRule(pkg, fqn)) return true;
        }
        return false;
    }

    /** Built-in modId to package prefix table. Resolving {@code scan.forceScanMods} uses it as a
     *  conservative mapping that covers only known mod ids. */
    private static final Map<String, String> MOD_ID_PACKAGES = Map.of(
            "minecraft", "net.minecraft",
            "neoforge", "net.neoforged",
            "forge", "net.minecraftforge",
            "java", "java");

    /**
     * Resolves {@code scan.forceScanMods} into the set of package rules that are scanned even though the
     * whitelist does not cover them. Excludes always take precedence over a forced include. Each entry is
     * resolved as follows:
     * <ul>
     *   <li>starts with {@code re:} -> kept as a regex rule (matching semantics in {@link #matchesPackageRule})</li>
     *   <li>contains {@code .} -> used directly as a literal package prefix</li>
     *   <li>otherwise -> looked up in {@link #MOD_ID_PACKAGES}; when it is missing there, a debug log
     *       records it and the entry is ignored</li>
     * </ul>
     */
    public Set<String> forcedPackages() {
        Set<String> result = new LinkedHashSet<>();
        for (String entry : scan.forceScanMods()) {
            if (entry == null || entry.isBlank()) continue;
            if (entry.startsWith(REGEX_RULE_PREFIX) || entry.contains(".")) {
                result.add(entry);
                continue;
            }
            String mapped = MOD_ID_PACKAGES.get(entry.trim());
            if (mapped != null) {
                result.add(mapped);
            } else {
                NekoJS.LOGGER.debug("probe.toml scan.forceScanMods: unknown mod id '{}' has no built-in package mapping; ignored", entry);
            }
        }
        return result;
    }
}
