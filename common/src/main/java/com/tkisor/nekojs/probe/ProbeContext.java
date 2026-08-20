package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.events.ProbeOverrides;
import com.tkisor.nekojs.probe.ir.TypeDecl;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 传给 {@link ProbeBackend#generate(ProbeContext)} 的共享上下文。
 *
 * <p>由 {@link ProbeCoordinator} 在跑完一次共享收集后构造，对每个选中的 backend 各传一份
 * （仅 {@link #languageId()} / {@link #languageDir()} 不同）。
 *
 * <p>Phase 2 起会追加 {@code ir()}（{@code List<TypeDecl>}）暴露语言中性的声明 IR；
 * Phase 1 暂不包含。
 */
public interface ProbeContext {

    /** catalog 快照（事件、绑定、适配器、配方命名空间、注册表类型、managed API 等）。 */
    NekoScriptCatalogSnapshot snapshot();

    /** 共享反射闭包收集到的、已按 {@link ProbeConfig} 包过滤的类（Phase 1 仍是 {@code Class<?>} 列表）。 */
    List<Class<?>> collectedClasses();

    /** 已加载的 probe 配置。 */
    ProbeConfig config();

    /** 路径单例。 */
    NekoJSPaths paths();

    /** 当前 backend 的目标语言 id。 */
    String languageId();

    /** 当前 backend 的输出目录（= {@link ProbeBackend#outputDir}）。 */
    Path languageDir();

    /**
     * 共享的类型声明 IR（Phase 2+）。当任一选中 backend 的 {@link ProbeBackend#requiresIr} 为 true、
     * 或 {@code probe.modify_type}/{@code probe.assign_type} 有监听器时，由 {@link ProbeCoordinator}
     * 构建并在触发事件后填入（已含脚本编辑与类型重定向）；否则为 {@code null}（TS 走旧路径）。不可变。
     */
    List<TypeDecl> ir();

    /**
     * {@code probe.add_global} 与 {@code probe.snippets} 收集的结果（Phase 5）。永不为 null（无监听器时为 empty）。
     */
    ProbeOverrides overrides();

    /**
     * 本次 probe 的共享线程池（由 {@link ProbeCoordinator} 创建，IR 反射构建与各 backend **内部**的
     * 并行渲染共用；整个 probe 运行只有这一个池。注意：backend 之间由协调器**串行**调度——线程池
     * 不承诺 backend 间并行）。直接构造 {@code ProbeContext.Of} 的测试为 null，
     * backend 自行创建并负责关闭。默认 null。
     */
    default ExecutorService sharedPool() {
        return null;
    }

    /** 简单不可变实现。 */
    record Of(
            NekoScriptCatalogSnapshot snapshot,
            List<Class<?>> collectedClasses,
            ProbeConfig config,
            NekoJSPaths paths,
            String languageId,
            Path languageDir,
            List<TypeDecl> ir,
            ProbeOverrides overrides,
            ExecutorService sharedPool
    ) implements ProbeContext {
        public Of {
            java.util.Objects.requireNonNull(snapshot, "snapshot");
            java.util.Objects.requireNonNull(config, "config");
            java.util.Objects.requireNonNull(paths, "paths");
            java.util.Objects.requireNonNull(languageId, "languageId");
            java.util.Objects.requireNonNull(languageDir, "languageDir");
            collectedClasses = List.copyOf(collectedClasses == null ? List.of() : collectedClasses);
            ir = ir == null ? null : List.copyOf(ir);
            overrides = overrides == null ? ProbeOverrides.empty() : overrides;
        }

        /** 8 参便利构造（sharedPool = null，兼容旧调用方）。 */
        public Of(NekoScriptCatalogSnapshot snapshot,
                  List<Class<?>> collectedClasses,
                  ProbeConfig config,
                  NekoJSPaths paths,
                  String languageId,
                  Path languageDir,
                  List<TypeDecl> ir,
                  ProbeOverrides overrides) {
            this(snapshot, collectedClasses, config, paths, languageId, languageDir, ir, overrides, null);
        }

        /** 7 参便利构造（overrides = empty）。 */
        public Of(NekoScriptCatalogSnapshot snapshot,
                  List<Class<?>> collectedClasses,
                  ProbeConfig config,
                  NekoJSPaths paths,
                  String languageId,
                  Path languageDir,
                  List<TypeDecl> ir) {
            this(snapshot, collectedClasses, config, paths, languageId, languageDir, ir, ProbeOverrides.empty(), null);
        }

        /** 6 参便利构造（ir = null, overrides = empty），兼容旧调用方。 */
        public Of(NekoScriptCatalogSnapshot snapshot,
                  List<Class<?>> collectedClasses,
                  ProbeConfig config,
                  NekoJSPaths paths,
                  String languageId,
                  Path languageDir) {
            this(snapshot, collectedClasses, config, paths, languageId, languageDir, null, ProbeOverrides.empty(), null);
        }
    }
}
