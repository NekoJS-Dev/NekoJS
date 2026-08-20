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
 * Probe 行为配置（{@code <game>/nekojs/config/probe.toml}）。
 *
 * <p>Phase 1：控制总开关、输出基目录、类扫描白/黑名单。默认 include 经过严格选取以**逐字复现**
 * 旧 {@code ProbeOrchestrator.isRelevantClass} 的 5 个前缀行为（{@code java} /
 * {@code com.tkisor.nekojs} 由本类固定追加，MC/loader 包由 {@code IPlatform.defaultScanPackages()} 注入）。
 * 如需扫描 {@code com.mojang} 等，用 {@code extraIncludePackages} 追加。默认 exclude 镜像
 * {@code ClassFilter} 类查找黑名单中的整包前缀（{@code com.tkisor.nekojs.core}、
 * {@code java.awt}/{@code javax.swing}/{@code javax.imageio}、{@code javax.naming}/{@code java.rmi}、
 * {@code java.sql}/{@code javax.sql}、{@code org.graalvm}/{@code com.oracle.truffle}）——
 * 这些前缀下的类脚本永远无法 {@code Java.type} 加载，为它们生成声明只会指向不可用类型。
 *
 * <p>扫描模式 {@link ScanConfig#mode()}：{@code SMART}（默认，白名单 + {@code forceScanMods} 补充）、
 * {@code FULL}（跳过 include 白名单、仅受 exclude 与 maxDepth 约束）、{@code NONE}（整体跳过扫描，probe 返回失败结果）。
 *
 * <p>B3：{@link #languages()} 承载 per-language 配置（{@code [languages.<languageId>]}）——
 * 每语言可指定优先使用的 backend 名与自定义输出子目录；两者皆可缺省（null → 沿用默认行为）。
 */
public record ProbeConfig(boolean enabled, boolean runAtStartup, String baseDir, ScanConfig scan, Map<String, LanguageConfig> languages) {

    /**
     * 单语言 probe 配置（{@code [languages.<languageId>]} 表）：
     * {@code backend} = 命令默认优先选用的 backend 名（null → 用该语言注册表默认）；
     * {@code outputDir} = {@code baseDir} 下该语言的输出子目录（null → 用语言 id 本身）。
     */
    public record LanguageConfig(String backend, String outputDir) {
    }

    /** 旧 4 参兼容构造（runAtStartup = false），保持既有调用点源码兼容。 */
    public ProbeConfig(boolean enabled, String baseDir, ScanConfig scan, Map<String, LanguageConfig> languages) {
        this(enabled, false, baseDir, scan, languages);
    }

    /** 3 参便捷构造：等价 {@code (enabled, false, baseDir, scan, Map.of())}，保持既有调用点源码兼容。 */
    public ProbeConfig(boolean enabled, String baseDir, ScanConfig scan) {
        this(enabled, false, baseDir, scan, Map.of());
    }

    /** 类扫描过滤配置。 */
    public record ScanConfig(
            List<String> includePackages,
            List<String> extraIncludePackages,
            List<String> excludePackages,
            List<String> forceScanMods,
            int maxDepth,
            ScanMode mode          // SMART | FULL | NONE
    ) {
        /**
         * 默认排除前缀：镜像 {@code core/fs/ClassFilter} {@code GENERAL_BLACKLIST} 中的整包条目
         * （脚本无法 {@code Java.type} 加载这些前缀下的类，生成声明只会误导）。仅作用于默认值
         * （{@link #defaultScan()} / {@code ProbeConfig.defaultConfig()}）；显式构造的 ScanConfig
         * （含 golden 测试配置）不受影响，probe.toml 的 {@code scan.excludePackages} 仍为覆盖语义。
         */
        private static final List<String> DEFAULT_EXCLUDE_PACKAGES = List.of(
                "com.tkisor.nekojs.core",
                // 客户端桌面 API（Robot 按键/截屏/剪贴板）与 JNDI/RMI 远程加载通道、JDBC
                "java.awt", "javax.swing", "javax.imageio", "javax.naming", "java.rmi",
                "java.sql", "javax.sql",
                // Graal/Truffle 引擎内部
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

        /** 字符串便捷构造：兼容 probe.toml 的字符串取值（大小写不敏感；未知取值兜底 SMART，见 {@link ScanMode#parse}）。 */
        public ScanConfig(List<String> includePackages, List<String> extraIncludePackages,
                          List<String> excludePackages, List<String> forceScanMods,
                          int maxDepth, String mode) {
            this(includePackages, extraIncludePackages, excludePackages, forceScanMods, maxDepth,
                    ScanMode.parse(mode));
        }

        /** 扫描模式（{@code probe.toml} 的 {@code scan.mode}）。 */
        public enum ScanMode {
            SMART, FULL, NONE;

            /**
             * 大小写不敏感解析：{@code null}/空串 → 默认 {@link #SMART}；未知取值 warn 并兜底
             * {@link #SMART}（与旧行为等价：默认走白名单过滤）。
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

    /** 取某语言 id 的 per-language 配置（未配置返回空 Optional）。 */
    public Optional<LanguageConfig> language(String languageId) {
        return Optional.ofNullable(languages.get(languageId));
    }

    /* ================= 包规则匹配（字面前缀 / re: 正则） ================= */

    /** 正则规则条目前缀：{@code re:<regex>}，如 {@code re:com\.example\..*\.api\..*}。 */
    public static final String REGEX_RULE_PREFIX = "re:";

    /**
     * 已编译正则规则的缓存（含编译失败的负缓存）。扫描会对每个候选类名遍历全部规则，
     * 缓存避免每个类重复 {@link Pattern#compile}。
     */
    private static final Map<String, Optional<Pattern>> PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * 单条包规则是否命中全限定类名。两种条目形态：
     * <ul>
     *   <li>字面包前缀（默认）：{@code fqn.startsWith(rule + ".")}——与历史语义完全一致</li>
     *   <li>正则：以 {@code re:} 开头（例 {@code re:com\.example\.(mod[12])\..*}），
     *       对 fqn 做<b>全匹配</b>（{@link java.util.regex.Matcher#matches()}）。编译结果缓存；
     *       非法正则 warn 一次，该条目视为永不命中</li>
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
     * 合成实际生效的包含包前缀集合：
     * <ul>
     *   <li>{@code includePackages} 非空 → 完全覆盖默认（覆盖语义）</li>
     *   <li>否则 → 固定默认 {@code {java, com.tkisor.nekojs}} ∪ 平台注入包</li>
     *   <li>最后追加 {@code extraIncludePackages}（追加语义）</li>
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
     * 判定一个全限定类名是否通过包过滤：命中某条包含规则、且不命中任何排除规则。
     * 条目形态见 {@link #matchesPackageRule}：字面包前缀（{@code fqn.startsWith(pkg + ".")}，
     * 与旧 {@code isRelevantClass} 一致）或 {@code re:} 正则全匹配。
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
     * 仅判定排除（从 {@link #isRelevantClass} 的 exclude 段拆分复用）：
     * 命中任一 {@code scan.excludePackages} 规则即返回 true（前缀或 {@code re:} 正则，
     * 见 {@link #matchesPackageRule}）。供 FULL 模式（跳过 include 白名单但保留 exclude）与
     * {@code ProbeCoordinator} 过滤工具使用。
     */
    public boolean isExcluded(String fqn) {
        if (fqn == null || fqn.isEmpty()) return false;
        for (String pkg : scan.excludePackages()) {
            if (matchesPackageRule(pkg, fqn)) return true;
        }
        return false;
    }

    /** 内置 modId → 包前缀映射表（{@code scan.forceScanMods} 的保守解析，仅覆盖已知 mod id）。 */
    private static final Map<String, String> MOD_ID_PACKAGES = Map.of(
            "minecraft", "net.minecraft",
            "neoforge", "net.neoforged",
            "forge", "net.minecraftforge",
            "java", "java");

    /**
     * 解析 {@code scan.forceScanMods} 为「强制包含的包规则」集合：在白名单之外被强制纳入扫描
     * （exclude 始终优先于强制包含）。解析规则：
     * <ul>
     *   <li>条目以 {@code re:} 开头 → 正则规则原样保留（匹配语义见 {@link #matchesPackageRule}）</li>
     *   <li>条目含 {@code .} → 视为字面包前缀直接使用</li>
     *   <li>否则查 {@link #MOD_ID_PACKAGES} 内置表；查不到则 debug 日志记录并忽略</li>
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
