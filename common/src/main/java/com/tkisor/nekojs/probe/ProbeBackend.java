package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 单语言的 probe 产物生成器。每个 backend 负责把共享的 catalog + 收集到的类渲染成**一种**目标语言
 * （如 {@code "typescript"} → {@code .d.ts}，{@code "python"} → {@code .pyi}）。
 *
 * <p>多个 backend 共享 {@link ProbeCoordinator} 跑的一次反射收集与（Phase 2 起）IR；每个 backend
 * 自管自己的输出目录（{@link #outputDir}），互不干扰。
 *
 * <p>实现要求：线程安全、幂等、不阻塞主线程过久（与旧 {@link ProbeGenerator} 一致）。
 */
public interface ProbeBackend {

    /** 目标语言 id，如 {@code "typescript"} / {@code "python"}。应与 {@code ScriptCompilerRegistry} 注册的语言 id 对齐。 */
    String languageId();

    /** backend 名字（同一语言内唯一），如 {@code "builtin"}；用于命令指定与冲突报告。 */
    String name();

    /** 同语言多 backend 时的默认优先级（高者先；默认 0）。 */
    default int priority() {
        return 0;
    }

    /**
     * 是否需要共享的类型声明 IR（{@code List<TypeDecl>}）。TS 与 Python 内置 backend 均返回
     * true（IR 是唯一渲染源，由 {@link ProbeCoordinator} 统一反射构建）；第三方 backend 若
     * 有独立的类声明来源可返回 false。默认 false。
     */
    default boolean requiresIr() {
        return false;
    }

    /**
     * 生成产物后，向各脚本目录的编辑器配置贡献/合并片段（TS→jsconfig 的 {@code paths}、
     * Python→pyrightconfig 的 {@code extraPaths}）。由 {@link ProbeCoordinator} 在所有 backend
     * 生成完毕、外部基础配置（WorkspaceGenerator）写入后统一调用一次。默认空。
     *
     * <p>实现应通过 {@code contributor} 的 {@code merge*} 方法贡献，用 backend 自己的
     * {@link ProbeContext#languageDir()}（真实输出目录）计算相对路径，确保编辑器配置始终指向最新产物。
     */
    default void contributeEditorConfig(EditorConfigContributor contributor, ProbeContext ctx) {
    }

    /**
     * 本 backend 的输出目录：优先取 {@code [languages.<languageId>].outputDir}，缺省回退 {@code <baseDir>/<languageId>}；
     * backend 可整体覆盖以自定义布局。backend 自己负责在 {@link #generate(ProbeContext)} 内清理/原子替换该目录。
     */
    default Path outputDir(NekoJSPaths paths, ProbeConfig config) {
        String dir = config.language(languageId())
                .flatMap(l -> Optional.ofNullable(l.outputDir()))
                .orElse(languageId());
        return paths.gameDir().resolve(config.baseDir()).resolve(dir);
    }

    /**
     * 生成产物。{@code ctx} 已携带共享的 catalog 快照、收集到的类、配置、预解析的输出目录。
     *
     * @return 生成结果（文件数、耗时、成败消息）
     */
    ProbeGenerator.GenerateResult generate(ProbeContext ctx);
}
