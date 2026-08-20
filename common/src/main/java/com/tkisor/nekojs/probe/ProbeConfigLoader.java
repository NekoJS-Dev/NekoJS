package com.tkisor.nekojs.probe;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.platform.Platform;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 加载 {@code <game>/nekojs/config/probe.toml}，模式与 {@code SandboxConfigLoader} 一致：
 * {@code CommentedFileConfig} + autosave + 按需写入默认值与注释；任何异常回退 {@link ProbeConfig#defaultConfig()}。
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
            // 默认值与 ProbeConfig.ScanConfig.defaultScan() 共用同一来源（DEFAULT_EXCLUDE_PACKAGES，
            // 镜像 ClassFilter 整包黑名单）：否则新写出的 probe.toml 会拿到空 exclude，绕过默认排除。
            // setup 只在键缺失时写默认值——已有文件里显式的空列表（excludePackages = []）是用户
            // 的覆盖语义，保持为空（exclude 关闭），不会被默认值回填。
            setup(config, "scan.excludePackages", ProbeConfig.ScanConfig.defaultScan().excludePackages(),
                    " Package rules to EXCLUDE even when matched by the whitelist (deny-list, evaluated last; literal prefixes or 're:' regexes). Defaults mirror the ClassFilter package blacklist (nekojs core, java.desktop UI, JNDI/RMI, JDBC, Graal/Truffle) - scripts can never Java.type classes under those prefixes. Set to an explicit empty list [] to disable exclusions.");
            setup(config, "scan.forceScanMods", List.of("minecraft"),
                    " Mod IDs or package rules force-included into scanning ON TOP of the whitelist (excludePackages still wins). Built-in modId table: minecraft=net.minecraft, neoforge=net.neoforged, forge=net.minecraftforge, java=java. Entries containing '.' are literal package prefixes; 're:' entries are regexes; unknown mod IDs are ignored (debug log).");
            setup(config, "scan.maxDepth", 5,
                    " Max BFS depth when walking the type-reachability closure from event/binding/adapter seeds.");
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
                // 文件存在但解析失败 = probe.toml 损坏：给用户明确提示（缺失时静默用默认是正常的）
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
     * 旧 {@code ProbeOrchestrator.isRelevantClass(String)} 的等价默认判定：
     * 用 {@link ProbeConfig#defaultConfig()} + 当前平台默认包做过滤。
     * 供不便拿到 {@link ProbeContext} 的旧代码路径（如 {@code EventDeclarationGenerator}）过渡使用。
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
     * 直接把 {@code enabled} 写进 probe.toml（与 {@link #load} 相同的 builder 写法：sync + preserveInsertionOrder + autosave）。
     * 供 {@code /nekojs probe enable|disable} 命令经 {@link ProbeCoordinator#setEnabled} 调用；异常向上抛（调用方 debug 吞掉）。
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
     * 解析 {@code [languages.<languageId>]} 表：先取 {@code languages} 子表，再遍历其键（每个值应为嵌套 Config）。
     * 键不存在（或值不是表）时返回空 Map——语言级配置全部缺省，回退到「输出目录 = 语言 id / 注册表默认 backend」。
     */
    private static Map<String, ProbeConfig.LanguageConfig> languages(CommentedFileConfig config) {
        Object raw = config.get("languages");
        if (!(raw instanceof Config table)) {
            return Map.of();
        }
        Map<String, ProbeConfig.LanguageConfig> result = new LinkedHashMap<>();
        for (Config.Entry entry : table.entrySet()) {
            if (!(entry.getValue() instanceof Config langTable)) {
                continue; // 值不是表（如误写成字符串）→ 忽略该语言条目
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
