package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.events.ProbeEvents;
import com.tkisor.nekojs.probe.events.ProbeOverrides;
import com.tkisor.nekojs.probe.ir.TypeDecl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Probe 协调器：一次 probe 运行的**流程编排**——配置 → 共享收集 → 共享 IR → 覆盖事件 →
 * 外部副作用 → 逐 backend 渲染（串行）→ 诊断汇总。具体职责由包内组件承担：
 * <ul>
 *   <li>{@link ProbeConfigService}：probe.toml 读取/缓存（mtime+size 戳）/enabled 持久化</li>
 *   <li>{@link ProbeClassCollector}：种子 BFS 类收集（无状态）</li>
 *   <li>{@link ProbeIrBuilder}：TypeReflector 反射 + assign/modify 事件（无状态）</li>
 *   <li>{@link ProbeOutputCommitter}：staging/backup 目录交换（backend 各自调用）</li>
 * </ul>
 *
 * <p>C3 起支持针对自定义 {@link NekoJSPaths} 实例化（构造传入 paths 与 {@link ProbeExternalArtifacts}），
 * 便于用临时目录隔离测试；实例方法为 {@link #readConfig()}/{@link #reloadConfigCache()}/
 * {@link #applyEnabled(boolean)}/{@link #isProbeEnabled()}/{@link #runProbe}。
 * Java 不允许静态与实例方法同签名共存，故静态兼容层保留与旧版一致的
 * {@code config()/reloadConfig()/setEnabled(boolean)/isEnabled()/run(snapshot, backends)} 签名，
 * 委托惰性单例 {@code defaultCoordinator()}；所有既有静态调用点（命令层与测试）不改也继续编译运行。
 */
public final class ProbeCoordinator {

    /* ================= 实例状态 ================= */
    private final NekoJSPaths paths;
    private final ProbeExternalArtifacts externalArtifacts;
    private final ProbeConfigService configService;
    /**
     * runProbe 互斥：同一协调器实例同一时间只允许一次运行（fail-fast——占用期间的再次调用
     * 立即返回失败结果，不排队）。除输出目录写入外，backend 实例（注册表单例）携带
     * 每轮清理的可变渲染缓存，并发运行会互相破坏；用户命令场景「立即提示已在运行」优于静默排队。
     */
    private final AtomicBoolean running = new AtomicBoolean();

    /** 以给定游戏目录路径构造；外部副作用走 {@link ProbeExternalArtifacts#DEFAULT}。 */
    public ProbeCoordinator(NekoJSPaths paths) {
        this(paths, ProbeExternalArtifacts.DEFAULT);
    }

    /** 以给定游戏目录路径与外部副作用实现构造（测试可用 {@link ProbeExternalArtifacts#NONE} 隔离写盘）。 */
    public ProbeCoordinator(NekoJSPaths paths, ProbeExternalArtifacts artifacts) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.externalArtifacts = Objects.requireNonNull(artifacts, "artifacts");
        this.configService = new ProbeConfigService(paths);
    }

    /** 读取 probe 配置（委托 {@link ProbeConfigService}：mtime+size 戳缓存自动重读）。 */
    public ProbeConfig readConfig() {
        return configService.readConfig();
    }

    /** 丢弃配置缓存，下次 {@link #readConfig()} 重新从盘读取（供 {@code /nekojs probe reload} 强制刷新）。 */
    public void reloadConfigCache() {
        configService.reloadConfigCache();
    }

    /** 把 probe.toml 的 {@code enabled} 写盘并重载缓存（供 {@code /nekojs probe enable|disable} 命令）。 */
    public void applyEnabled(boolean enabled) {
        configService.applyEnabled(enabled);
    }

    /** 当前 probe 总开关（{@code == readConfig().enabled()}）。 */
    public boolean isProbeEnabled() {
        return configService.isProbeEnabled();
    }

    /* ================= 静态兼容层（旧静态调用点不变） ================= */
    private static volatile ProbeCoordinator DEFAULT;

    /** 惰性创建绑定全局 {@link NekoJSPaths#get()} 的默认协调器。 */
    private static ProbeCoordinator defaultCoordinator() {
        ProbeCoordinator d = DEFAULT;
        if (d == null) {
            d = new ProbeCoordinator(NekoJSPaths.get());
            DEFAULT = d;
        }
        return d;
    }

    /** 静态 facade：委托全局默认协调器读取配置。 */
    public static ProbeConfig config() {
        return defaultCoordinator().readConfig();
    }

    /**
     * 确保 {@code <gamedir>/nekojs/config/probe.toml} 存在：缺失时按默认值自动落盘
     * （{@link ProbeConfigLoader} 的 autosave 语义，与 {@code engine.toml} 启动期自动生成对称）。
     * 供 {@code WorkspaceGenerator.setupWorkspace()} 在启动时调用——否则用户首次看到
     * {@code nekojs/config/} 时只有 engine.toml，probe.toml 要等第一次跑 probe 命令才出现。
     * 读取失败（文件损坏等）由 loader 内部回退默认配置，此处不抛异常。
     */
    public static void ensureConfigFile() {
        defaultCoordinator().readConfig();
    }

    /** 静态 facade：委托全局默认协调器丢弃配置缓存。 */
    public static void reloadConfig() {
        defaultCoordinator().reloadConfigCache();
    }

    /** 静态 facade：委托全局默认协调器持久化 {@code enabled} 并重载。 */
    public static void setEnabled(boolean enabled) {
        defaultCoordinator().applyEnabled(enabled);
    }

    /** 静态 facade：委托全局默认协调器重置编辑器配置（{@code /nekojs probe reset_config}）。 */
    public static int resetEditorConfigs() {
        return defaultCoordinator().resetEditorConfigsInternal();
    }

    /**
     * 重置所有 backend 管理的编辑器配置文件：逐 backend 删除其整体拥有的文件
     * （jsconfig/pyrightconfig/snippets，不碰共享的 {@code .vscode/settings.json}），
     * 随后由 {@code WorkspaceGenerator.createWorkspaceConfigs()} 重建出厂基础配置。
     * 配置片段（paths/include 等）由随后的 probe 运行重新合并。
     *
     * @return 成功执行 reset 的 backend 数
     */
    int resetEditorConfigsInternal() {
        int reset = 0;
        for (ProbeBackend backend : ProbeBackendRegistry.get().allBackends()) {
            try {
                backend.resetEditorConfig(paths);
                reset++;
            } catch (Exception e) {
                NekoJS.LOGGER.error("Probe backend {}:{} editor-config reset failed",
                        backend.languageId(), backend.name(), e);
            }
        }
        com.tkisor.nekojs.script.WorkspaceGenerator.createWorkspaceConfigs();
        return reset;
    }

    /** 静态 facade：全局默认协调器的 probe 总开关。 */
    public static boolean isEnabled() {
        return defaultCoordinator().isProbeEnabled();
    }

    /**
     * 共享类收集（委托 {@link ProbeClassCollector#collect}）：从事件/绑定种子出发的 BFS 闭包，
     * 按 {@link ProbeConfig} 包过滤，返回按全限定名排序的确定性集合。
     */
    public static LinkedHashSet<Class<?>> collectClasses(NekoScriptCatalogSnapshot snapshot, ProbeConfig cfg) {
        return ProbeClassCollector.collect(snapshot, cfg);
    }

    /**
     * 对选中的 backend 集合执行一次 probe：共享收集 → 各 backend 渲染 → 外部副作用一次。
     * 实例版 {@code run(...)}：路径全部取自本实例的 {@link #paths}，外部副作用走 {@link #externalArtifacts}。
     *
     * <p>同一协调器实例同一时间只允许一次运行（fail-fast）：占用期间的再次调用立即返回
     * {@code "probe already running"}，不排队。选中集合内重复 outputDir 的后续 backend 被跳过
     * （结果为 failure，就地同步会把先跑者的产物当陈旧文件删掉）。事件/编辑器配置的降级信息进结果 warnings。
     *
     * @param snapshot 当前 catalog 快照
     * @param backends 命令解析出的、本次要跑的 backend（已按 priority/名字解析）
     * @return 每个 backend 的生成结果（顺序与输入一致）
     */
    public List<ProbeBackend.GenerateResult> runProbe(NekoScriptCatalogSnapshot snapshot, List<ProbeBackend> backends) {
        if (backends.isEmpty()) {
            return List.of(ProbeBackend.GenerateResult.failure("no probe backend selected"));
        }
        if (!running.compareAndSet(false, true)) {
            return backends.stream()
                    .map(b -> ProbeBackend.GenerateResult.failure("probe already running"))
                    .toList();
        }
        try {
            return runProbeLocked(snapshot, backends);
        } finally {
            running.set(false);
        }
    }

    private List<ProbeBackend.GenerateResult> runProbeLocked(NekoScriptCatalogSnapshot snapshot, List<ProbeBackend> backends) {
        ProbeConfig cfg = readConfig();
        if (!cfg.enabled()) {
            return backends.stream()
                    .map(b -> ProbeBackend.GenerateResult.failure("probe disabled in probe.toml"))
                    .toList();
        }
        // mode=NONE：整体关闭类扫描（在共享收集与 paths 获取之前尽早返回，不做任何写盘）
        if (cfg.scan().mode() == ProbeConfig.ScanConfig.ScanMode.NONE) {
            return backends.stream()
                    .map(b -> ProbeBackend.GenerateResult.failure("probe disabled (scan mode=NONE in probe.toml)"))
                    .toList();
        }

        LinkedHashSet<Class<?>> collected = ProbeClassCollector.collect(snapshot, cfg);
        NekoJSPaths paths = this.paths;

        // 共享 IR：当 modify_type/assign_type 有监听器，或某 backend 需要 IR（TS 与 Python 内置
        // backend 都声明 requiresIr=true，IR 是唯一渲染源）时构建一次。
        boolean needIr = ProbeEvents.MODIFY_TYPE.hasListeners()
                || ProbeEvents.ASSIGN_TYPE.hasListeners()
                || backends.stream().anyMatch(ProbeBackend::requiresIr);
        // 本次运行级降级信息（事件监听器抛异常等）：追加到每个 backend 的结果 warnings
        List<String> runWarnings = new ArrayList<>();
        Map<String, ApiTypeRef> assignMap = ProbeEvents.ASSIGN_TYPE.hasListeners()
                ? ProbeIrBuilder.fireAssignType(runWarnings)
                : Map.of();

        // 单一共享线程池：整个 probe 运行（IR 反射构建 + 各 backend 内部的并行渲染）只有这一个池
        // （backend 之间由本类串行调度——线程池不承诺 backend 间并行）
        ExecutorService sharedPool = Executors.newFixedThreadPool(
                Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 8)));
        try {
            List<TypeDecl> sharedIr = needIr
                    ? ProbeIrBuilder.buildAndMutateIr(collected, assignMap, sharedPool, runWarnings)
                    : null;

            // add_global / snippets：每次 probe 收集一次（各有监听器才触发）
            ProbeOverrides overrides = ProbeIrBuilder.fireOverrides(runWarnings);

            // 外部基础配置（WorkspaceGenerator 写 jsconfig 等）先执行一次，供 backend 的 contributeEditorConfig 合并
            try {
                Path baseDir = paths.gameDir().resolve(cfg.baseDir());
                externalArtifacts.generate(baseDir);
            } catch (Exception e) {
                NekoJS.LOGGER.error("Probe external artifacts failed", e);
            }

            EditorConfigContributor editorConfigs = new FileEditorConfigContributor();
            List<ProbeBackend.GenerateResult> results = new ArrayList<>(backends.size());
            // 输出目录去重：两个 backend 写同一目录时，后跑者的陈旧文件清理会删掉先跑者的产物
            // （后跑者覆盖先跑者）。每组目录只跑第一个选中的 backend，其余跳过并告警——
            // 同语言多 backend 共存合法，但共享输出目录不合法（要换目录用 outputDir 配置）。
            Set<Path> seenOutputDirs = new HashSet<>();
            for (ProbeBackend backend : backends) {
                Path langDir = backend.outputDir(paths, cfg).normalize();
                if (!seenOutputDirs.add(langDir)) {
                    NekoJS.LOGGER.warn("Probe backend {}:{} skipped: output directory {} is also targeted by another selected backend in this run",
                            backend.languageId(), backend.name(), langDir);
                    results.add(ProbeBackend.GenerateResult.failure(
                            "skipped: duplicate output directory " + langDir + " (already generated by another selected backend)"));
                    continue;
                }
                ProbeContext ctx = new ProbeContext.Of(
                        snapshot,
                        List.copyOf(collected),
                        cfg,
                        paths,
                        backend.languageId(),
                        langDir,
                        sharedIr,
                        overrides,
                        sharedPool);
                ProbeBackend.GenerateResult result;
                try {
                    result = backend.generate(ctx);
                } catch (Exception e) {
                    NekoJS.LOGGER.error("Probe backend {}:{} failed", backend.languageId(), backend.name(), e);
                    result = ProbeBackend.GenerateResult.failureOf(e);
                }
                // 生成成功后：向编辑器配置贡献本 backend 的片段（paths/extraPaths，幂等合并，指向真实输出目录）。
                // 失败只降级（生成产物已提交），但记入该 backend 的 warnings 让命令层可见。
                List<String> backendWarnings = new ArrayList<>();
                if (result.success()) {
                    try {
                        backend.contributeEditorConfig(editorConfigs, ctx);
                    } catch (Exception e) {
                        NekoJS.LOGGER.debug("Probe backend {}:{} editor-config contribution failed",
                                backend.languageId(), backend.name(), e);
                        backendWarnings.add("editor-config contribution failed for " + backend.languageId() + ":"
                                + backend.name() + " (" + e + ")");
                    }
                }
                if (!runWarnings.isEmpty()) {
                    backendWarnings.addAll(0, runWarnings);
                }
                results.add(ProbeBackend.GenerateResult.withWarnings(result, backendWarnings));
            }

            return results;
        } finally {
            sharedPool.shutdown();
        }
    }

    /** 静态 facade：委托全局默认协调器执行一次 probe（命令层旧调用点不变）。 */
    public static List<ProbeBackend.GenerateResult> run(NekoScriptCatalogSnapshot snapshot, List<ProbeBackend> backends) {
        return defaultCoordinator().runProbe(snapshot, backends);
    }

    /**
     * 开服自动 probe（{@code probe.toml runAtStartup = true} 时启用；默认 false，opt-in）：
     * 各平台在 ServerStarted 时调用一次——跑默认 TS backend 并把结果摘要写进日志。
     * 任何失败只记日志，绝不影响开服。{@code enabled = false} 或 {@code scan.mode = NONE} 时跳过。
     *
     * @return 是否真的执行了一次 probe（配置开启且 backend 可用）
     */
    public static boolean runStartupProbeIfConfigured() {
        ProbeConfig cfg = config();
        if (!cfg.enabled() || !cfg.runAtStartup()) return false;
        if (cfg.scan().mode() == ProbeConfig.ScanConfig.ScanMode.NONE) return false;
        List<ProbeBackend> backends = ProbeBackendSelector.defaultTypescript();
        if (backends.isEmpty()) {
            NekoJS.LOGGER.warn("Startup probe skipped: no typescript backend registered");
            return false;
        }
        try {
            var snapshot = NekoScriptCatalog.snapshot(NekoRuntimeAccess.get());
            for (ProbeBackend.GenerateResult r : defaultCoordinator().runProbe(snapshot, backends)) {
                if (r.success()) {
                    NekoJS.LOGGER.info("Startup probe: {} files in {}ms{}",
                            r.filesGenerated(), r.durationMs(), r.outputDir() == null ? "" : " -> " + r.outputDir());
                } else {
                    NekoJS.LOGGER.warn("Startup probe failed: {}", r.message());
                }
                for (String w : r.warnings()) {
                    NekoJS.LOGGER.warn("Startup probe warning: {}", w);
                }
            }
        } catch (Throwable t) {
            NekoJS.LOGGER.error("Startup probe crashed (server start continues)", t);
        }
        return true;
    }

    /** 当前已注册的 backend 总数（诊断用）。 */
    public static int registeredBackendCount() {
        return ProbeBackendRegistry.get().registrars().size();
    }

    /** 仅收集（不渲染），供诊断/测试。 */
    public static Set<Class<?>> collectOnly(NekoScriptCatalogSnapshot snapshot) {
        return collectClasses(snapshot, config());
    }
}
