package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.events.GlobalDecl;
import com.tkisor.nekojs.probe.events.ProbeAddGlobalEventJS;
import com.tkisor.nekojs.probe.events.ProbeAssignTypeEventJS;
import com.tkisor.nekojs.probe.events.ProbeEvents;
import com.tkisor.nekojs.probe.events.ProbeModifyTypeEventJS;
import com.tkisor.nekojs.probe.events.ProbeOverrides;
import com.tkisor.nekojs.probe.events.ProbeSnippetEventJS;
import com.tkisor.nekojs.probe.events.Snippet;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Probe 协调器：跑一次共享类收集，再把结果派发给选中的 {@link ProbeBackend} 各自渲染。
 *
 * <p>Phase 1：共享收集 = 旧 {@code ProbeOrchestrator.collectClasses} 的 BFS（搬到此处并改为配置驱动）。
 * 每个 backend 自管输出目录与 staging/swap；外部副作用（agent 模板 + workspace 配置）在本类统一执行一次。
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
    private final ProbeConfigLoader configLoader = new ProbeConfigLoader();
    private volatile ProbeConfig cachedConfig;

    /** 以给定游戏目录路径构造；外部副作用走 {@link ProbeExternalArtifacts#DEFAULT}。 */
    public ProbeCoordinator(NekoJSPaths paths) {
        this(paths, ProbeExternalArtifacts.DEFAULT);
    }

    /** 以给定游戏目录路径与外部副作用实现构造（测试可用 {@link ProbeExternalArtifacts#NONE} 隔离写盘）。 */
    public ProbeCoordinator(NekoJSPaths paths, ProbeExternalArtifacts artifacts) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.externalArtifacts = Objects.requireNonNull(artifacts, "artifacts");
    }

    /** 读取（首次加载并缓存）probe 配置（实例版 {@code config()}，绑定本实例的 {@link NekoJSPaths}）。 */
    public ProbeConfig readConfig() {
        ProbeConfig c = cachedConfig;
        if (c == null) {
            c = configLoader.load(paths.probeConfig());
            cachedConfig = c;
        }
        return c;
    }

    /** 丢弃配置缓存，下次 {@link #readConfig()} 重新从盘读取（实例版 {@code reloadConfig()}，供 {@code /nekojs probe reload}）。 */
    public void reloadConfigCache() {
        cachedConfig = null;
    }

    /** 把 probe.toml 的 {@code enabled} 写盘并重载缓存（实例版 {@code setEnabled}，供 {@code /nekojs probe enable|disable} 命令）。写盘失败时告警：命令看似成功但配置未持久化。 */
    public void applyEnabled(boolean enabled) {
        try {
            ProbeConfigLoader.setEnabled(paths.probeConfig(), enabled);
        } catch (Throwable e) {
            NekoJS.LOGGER.warn("Failed to persist probe enabled={} into {}; the setting will be lost on next reload",
                    enabled, paths.probeConfig(), e);
        }
        reloadConfigCache();
    }

    /** 当前 probe 总开关（实例版 {@code isEnabled()}，{@code == readConfig().enabled()}）。 */
    public boolean isProbeEnabled() {
        return readConfig().enabled();
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

    /** 静态 facade：委托全局默认协调器丢弃配置缓存。 */
    public static void reloadConfig() {
        defaultCoordinator().reloadConfigCache();
    }

    /** 静态 facade：委托全局默认协调器持久化 {@code enabled} 并重载。 */
    public static void setEnabled(boolean enabled) {
        defaultCoordinator().applyEnabled(enabled);
    }

    /** 静态 facade：全局默认协调器的 probe 总开关。 */
    public static boolean isEnabled() {
        return defaultCoordinator().isProbeEnabled();
    }

    /**
     * 共享类收集：从事件/绑定种子出发做轻量 BFS（仅收集类，不生成声明），按 {@link ProbeConfig} 包过滤。
     * 适配器目标类、适配器别名引用的 host 类型等 backend 特定的后续增补留给各 backend 自行处理。
     */
    public static LinkedHashSet<Class<?>> collectClasses(NekoScriptCatalogSnapshot snapshot, ProbeConfig cfg) {
        List<String> platformPkgs = ProbeConfigLoader.platformDefaultPackages();
        Set<String> forcedPkgs = cfg.forcedPackages();
        LinkedHashSet<Class<?>> visited = new LinkedHashSet<>();
        Queue<Object[]> queue = new LinkedList<>();

        // 种子类：事件类型和绑定类型（depth 0）
        for (EventCatalogEntry event : snapshot.events()) {
            if (event.eventType() != null) queue.add(new Object[]{event.eventType(), 0});
            if (event.dispatchKeyType() != null) queue.add(new Object[]{event.dispatchKeyType(), 0});
        }
        for (BindingCatalogEntry binding : snapshot.bindings()) {
            if (binding.javaType() != null) queue.add(new Object[]{binding.javaType(), 0});
            // 代理绑定（如 Item）的 extraDocTypes（委托目标 MC 类）也作为种子
            for (Class<?> extra : binding.extraDocTypes()) {
                queue.add(new Object[]{extra, 0});
            }
        }

        int maxDepth = cfg.scan().maxDepth() <= 0 ? 5 : cfg.scan().maxDepth();

        while (!queue.isEmpty()) {
            Object[] entry = queue.poll();
            Class<?> cls = (Class<?>) entry[0];
            int depth = (int) entry[1];

            if (depth > maxDepth) continue;
            if (cls == null || cls.isPrimitive() || cls == Object.class) continue;
            if (visited.contains(cls)) continue;
            if (!passesScanFilter(cfg, cls.getName(), platformPkgs, forcedPkgs)) continue;

            visited.add(cls);

            int nextDepth = depth + 1;
            if (nextDepth > maxDepth) continue;

            if (cls.getSuperclass() != null) queue.add(new Object[]{cls.getSuperclass(), nextDepth});
            for (Class<?> iface : cls.getInterfaces()) queue.add(new Object[]{iface, nextDepth});

            for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
                if (Modifier.isPublic(ctor.getModifiers())) {
                    for (Type p : ctor.getGenericParameterTypes()) collectTypeToQueue(p, queue, nextDepth);
                }
            }
            for (Method method : cls.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    collectTypeToQueue(method.getGenericReturnType(), queue, nextDepth);
                    for (Type p : method.getGenericParameterTypes()) collectTypeToQueue(p, queue, nextDepth);
                }
            }
            for (Field field : cls.getDeclaredFields()) {
                if (Modifier.isPublic(field.getModifiers())) collectTypeToQueue(field.getGenericType(), queue, nextDepth);
            }
        }

        // 确定性：BFS 访问顺序依赖 getDeclaredMethods/getInterfaces 等反射顺序（JVM 规范不保证，
        // 跨 JDK 版本/平台可能不同）。返回前按全限定名字典序排序，保证 probe 产物可复现。
        List<Class<?>> sorted = new ArrayList<>(visited);
        sorted.sort(Comparator.comparing(Class::getName));
        return new LinkedHashSet<>(sorted);
    }

    /**
     * collectClasses 的过滤判定（exclude 始终生效；FULL 直通 include；SMART 走白名单 + forceScanMods 补充）：
     * <ul>
     *   <li>命中 {@code excludePackages} → 跳过（FULL 模式同样生效）</li>
     *   <li>mode == FULL → 收（跳过 include 白名单；闭包体积由 maxDepth 护栏）</li>
     *   <li>其余取值（SMART 等）→ {@link ProbeConfig#isRelevantClass}；另命中 forceScanMods 前缀也收</li>
     * </ul>
     */
    private static boolean passesScanFilter(ProbeConfig cfg, String fqn, List<String> platformPkgs, Set<String> forcedPkgs) {
        if (cfg.isExcluded(fqn)) return false;
        if (cfg.scan().mode() == ProbeConfig.ScanConfig.ScanMode.FULL) return true;
        if (cfg.isRelevantClass(fqn, platformPkgs)) return true;
        for (String pkg : forcedPkgs) {
            if (ProbeConfig.matchesPackageRule(pkg, fqn)) return true;
        }
        return false;
    }

    private static void collectTypeToQueue(Type type, Queue<Object[]> queue, int depth) {
        if (type instanceof Class<?> cls) {
            queue.add(new Object[]{cls, depth});
        } else if (type instanceof ParameterizedType pt) {
            if (pt.getRawType() instanceof Class<?> rawCls) queue.add(new Object[]{rawCls, depth});
            for (Type arg : pt.getActualTypeArguments()) collectTypeToQueue(arg, queue, depth);
        } else if (type instanceof GenericArrayType gat) {
            collectTypeToQueue(gat.getGenericComponentType(), queue, depth);
        }
        // 刻意不跟随 TypeVariable 上界与 WildcardType 上/下界：跟随它们（尤其在 java.* 内）会触发
        // 级联爆炸——例如 File 的签名拉入 URI/URL/Path/Charset/Locale…，5 层 BFS 穿过 java.io/java.util
        // 产出海量类。原行为（不跟随）是有意的范围控制，保持 probe 输出有界。
    }

    /**
     * 构建共享 IR（TypeReflector 反射每个收集到的类），依次：应用 {@code probe.assign_type}（全局类型重定向，
     * 标记受影响类 mutated）→ 触发 {@code probe.modify_type}（参数级编辑）。反射失败的类被跳过。
     * 返回不可变 IR 列表（已含编辑）。{@code assignMap} 为空时跳过应用步骤。
     *
     * <p>反射阶段并行执行（线程数 = min(可用核数, 8)，每类一个任务），随后按**原始收集顺序**（BFS 序）
     * 汇总进 LinkedHashMap，保证 {@code List.copyOf(ir.values())} 与串行版本顺序一致；
     * assign_type / modify_type 仍在 map 建完后串行执行（事件触发顺序不变）。
     */
    private static List<TypeDecl> buildAndMutateIr(Collection<Class<?>> collected, Map<String, ApiTypeRef> assignMap,
                                                     ExecutorService pool) {
        List<Class<?>> ordered = new ArrayList<>(collected);
        if (ordered.isEmpty()) return List.of();

        Map<String, TypeDecl> ir = new LinkedHashMap<>();
        {
            // 每类一个反射任务；TypeReflector 无实例状态，各任务新建实例（线程内独立）
            List<Future<TypeDecl>> futures = new ArrayList<>(ordered.size());
            for (Class<?> c : ordered) {
                futures.add(pool.submit(() -> new TypeReflector().reflect(c)));
            }
            // 按原始收集顺序汇总：LinkedHashMap 插入序即 collectClasses 的 BFS 序，后续 List.copyOf 稳定
            for (int i = 0; i < futures.size(); i++) {
                String fqn = ordered.get(i).getName();
                try {
                    ir.put(fqn, futures.get(i).get());
                } catch (InterruptedException e) {
                    // 中断不能当「反射失败」吞掉：恢复中断标志并向上传播，probe 立刻终止
                    // （携带半截 IR 继续渲染会把残缺产物提交到磁盘）
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while building shared probe IR", e);
                } catch (Throwable t) {
                    // 无法反射的类跳过：各 backend 自行按需补反射；Python 不产出其 stub。
                    // 逐类 debug 日志（含类名）便于排查缺失类型
                    NekoJS.LOGGER.debug("Probe: failed to reflect class {} into shared IR (skipped)", fqn, t);
                }
            }
        }
        // 1. assign_type：先重定向反射产出的类型（标记受影响类 mutated，TS 会重渲染）
        if (assignMap != null && !assignMap.isEmpty()) {
            for (TypeDecl d : ir.values()) ProbeAssignTypeEventJS.applyTo(d, assignMap);
        }
        // 2. modify_type：参数级编辑（在 assign 之后；modify_type 显式设置的类型不被 assign 二次覆盖）
        if (!ir.isEmpty() && ProbeEvents.MODIFY_TYPE.hasListeners()) {
            try {
                ProbeEvents.MODIFY_TYPE.post(new ProbeModifyTypeEventJS(ir));
            } catch (Throwable t) {
                NekoJS.LOGGER.error("probe.modify_type event threw; edits may be incomplete", t);
            }
        }
        return List.copyOf(ir.values());
    }

    /** 触发 {@code probe.assign_type} 一次，返回收集到的「Java FQN → 自定义类型」映射。 */
    private static Map<String, ApiTypeRef> fireAssignType() {
        ProbeAssignTypeEventJS ev = new ProbeAssignTypeEventJS();
        try {
            ProbeEvents.ASSIGN_TYPE.post(ev);
        } catch (Throwable t) {
            NekoJS.LOGGER.error("probe.assign_type event threw; assignments may be incomplete", t);
        }
        return ev.assignments();
    }

    /** 触发 {@code probe.add_global} 与 {@code probe.snippets}（各有监听器才触发），返回捆绑结果。 */
    private static ProbeOverrides fireOverrides() {
        List<GlobalDecl> globals = List.of();
        List<Snippet> snippets = List.of();
        if (ProbeEvents.ADD_GLOBAL.hasListeners()) {
            ProbeAddGlobalEventJS ev = new ProbeAddGlobalEventJS();
            try {
                ProbeEvents.ADD_GLOBAL.post(ev);
            } catch (Throwable t) {
                NekoJS.LOGGER.error("probe.add_global event threw", t);
            }
            globals = ev.globals();
        }
        if (ProbeEvents.SNIPPETS.hasListeners()) {
            ProbeSnippetEventJS ev = new ProbeSnippetEventJS();
            try {
                ProbeEvents.SNIPPETS.post(ev);
            } catch (Throwable t) {
                NekoJS.LOGGER.error("probe.snippets event threw", t);
            }
            snippets = ev.snippets();
        }
        return new ProbeOverrides(globals, snippets);
    }

    /**
     * 对选中的 backend 集合执行一次 probe：共享收集 → 各 backend 渲染 → 外部副作用一次。
     * 实例版 {@code run(...)}：路径全部取自本实例的 {@link #paths}，外部副作用走 {@link #externalArtifacts}。
     *
     * @param snapshot 当前 catalog 快照
     * @param backends 命令解析出的、本次要跑的 backend（已按 priority/名字解析）
     * @return 每个 backend 的生成结果（顺序与输入一致）
     */
    public List<ProbeGenerator.GenerateResult> runProbe(NekoScriptCatalogSnapshot snapshot, List<ProbeBackend> backends) {
        if (backends.isEmpty()) {
            return List.of(ProbeGenerator.GenerateResult.failure("no probe backend selected"));
        }
        ProbeConfig cfg = readConfig();
        if (!cfg.enabled()) {
            return backends.stream()
                    .map(b -> ProbeGenerator.GenerateResult.failure("probe disabled in probe.toml"))
                    .toList();
        }
        // mode=NONE：整体关闭类扫描（在共享收集与 paths 获取之前尽早返回，不做任何写盘）
        if (cfg.scan().mode() == ProbeConfig.ScanConfig.ScanMode.NONE) {
            return backends.stream()
                    .map(b -> ProbeGenerator.GenerateResult.failure("probe disabled (scan mode=NONE in probe.toml)"))
                    .toList();
        }

        LinkedHashSet<Class<?>> collected = collectClasses(snapshot, cfg);
        NekoJSPaths paths = this.paths;

        // 共享 IR：当 modify_type/assign_type 有监听器，或某 backend 需要 IR（TS 与 Python 内置
        // backend 都声明 requiresIr=true，IR 是唯一渲染源）时构建一次。
        boolean needIr = ProbeEvents.MODIFY_TYPE.hasListeners()
                || ProbeEvents.ASSIGN_TYPE.hasListeners()
                || backends.stream().anyMatch(ProbeBackend::requiresIr);
        Map<String, ApiTypeRef> assignMap = ProbeEvents.ASSIGN_TYPE.hasListeners() ? fireAssignType() : Map.of();

        // 单一共享线程池：整个 probe 运行（IR 反射构建 + 各 backend 并行生成）只有这一个池
        ExecutorService sharedPool = Executors.newFixedThreadPool(
                Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 8)));
        try {
            List<TypeDecl> sharedIr = needIr ? buildAndMutateIr(collected, assignMap, sharedPool) : null;

            // add_global / snippets：每次 probe 收集一次（各有监听器才触发）
            ProbeOverrides overrides = fireOverrides();

            // 外部基础配置（WorkspaceGenerator 写 jsconfig 等）先执行一次，供 backend 的 contributeEditorConfig 合并
            try {
                Path baseDir = paths.gameDir().resolve(cfg.baseDir());
                externalArtifacts.generate(baseDir);
            } catch (Exception e) {
                NekoJS.LOGGER.error("Probe external artifacts failed", e);
            }

            EditorConfigContributor editorConfigs = new FileEditorConfigContributor();
            List<ProbeGenerator.GenerateResult> results = new ArrayList<>(backends.size());
            for (ProbeBackend backend : backends) {
                Path langDir = backend.outputDir(paths, cfg);
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
                try {
                    results.add(backend.generate(ctx));
                } catch (Exception e) {
                    NekoJS.LOGGER.error("Probe backend {}:{} failed", backend.languageId(), backend.name(), e);
                    results.add(ProbeGenerator.GenerateResult.failure(e.getMessage()));
                    continue;
                }
                // 生成成功后：向编辑器配置贡献本 backend 的片段（paths/extraPaths，幂等合并，指向真实输出目录）
                try {
                    backend.contributeEditorConfig(editorConfigs, ctx);
                } catch (Exception e) {
                    NekoJS.LOGGER.debug("Probe backend {}:{} editor-config contribution failed",
                            backend.languageId(), backend.name(), e);
                }
            }

            return results;
        } finally {
            sharedPool.shutdown();
        }
    }

    /** 静态 facade：委托全局默认协调器执行一次 probe（命令层旧调用点不变）。 */
    public static List<ProbeGenerator.GenerateResult> run(NekoScriptCatalogSnapshot snapshot, List<ProbeBackend> backends) {
        return defaultCoordinator().runProbe(snapshot, backends);
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
