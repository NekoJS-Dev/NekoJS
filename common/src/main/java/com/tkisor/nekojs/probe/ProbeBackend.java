package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 单语言的 probe 产物生成器。每个 backend 负责把共享的 catalog + 收集到的类渲染成**一种**目标语言
 * （如 {@code "typescript"} → {@code .d.ts}，{@code "python"} → {@code .pyi}）。
 *
 * <p>多个 backend 共享 {@link ProbeCoordinator} 跑的一次反射收集与 IR；每个 backend 自管自己的
 * 输出目录（{@link #outputDir}），互不干扰。
 *
 * <p><b>实现契约（预发布重构简化）</b>：实现只需提供 {@link #render}——把产物渲染进内存
 * （相对输出目录的路径 → UTF-8 文本），**不触碰磁盘**；staging/原子提交/崩溃恢复/路径越界校验
 * 由 {@link #generate} 默认实现统一负责（经 {@link ProbeOutputCommitter}）。需要完全自管输出的
 * 第三方 backend 仍可覆盖 {@code generate}，但不推荐——提交语义（备份/恢复/残留清理）容易写错。
 *
 * <p>实现要求：线程安全、幂等、不阻塞主线程过久。
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
     * 删除本 backend 管理的编辑器配置文件（{@code /nekojs probe reset_config} 用）。
     * 只删 backend 整体拥有的文件（jsconfig/pyrightconfig/snippets），不碰
     * {@code .vscode/settings.json} 这类与用户共享的文件（其贡献键由下次 contribute 幂等校正）。
     * 删除后由 {@code WorkspaceGenerator.createWorkspaceConfigs()} 重建出厂基础配置，
     * 再经 {@link #contributeEditorConfig} 合并出完整配置。默认空。
     */
    default void resetEditorConfig(NekoJSPaths paths) {
    }

    /**
     * 本 backend 的输出目录：优先取 {@code [languages.<languageId>].outputDir}，缺省回退 {@code <baseDir>/<languageId>}；
     * backend 可整体覆盖以自定义布局。
     */
    default Path outputDir(NekoJSPaths paths, ProbeConfig config) {
        String dir = config.language(languageId())
                .flatMap(l -> Optional.ofNullable(l.outputDir()))
                .orElse(languageId());
        return paths.gameDir().resolve(config.baseDir()).resolve(dir);
    }

    /**
     * 渲染产物到内存：返回「相对输出目录的路径（{@code '/'} 分隔，如 {@code "@package/net/minecraft/index.d.ts"}）
     * → UTF-8 文本内容」。实现不得触碰磁盘——落盘与原子提交由 {@link #generate} 默认实现统一负责。
     *
     * <p>前置条件不满足（如缺少共享 IR）时直接抛异常，默认 generate 会转为失败结果（旧输出保持不变）。
     */
    Map<String, String> render(ProbeContext ctx);

    /**
     * {@link #render} + 落盘 + 原子提交（默认实现）：恢复崩溃残留 → 逐文件写入 staging →
     * {@link ProbeOutputCommitter#commit} 整体替换输出目录。渲染失败（未触盘）或提交失败
     * （staging 丢弃、旧输出保留）分别转为失败结果。
     *
     * @return 生成结果（文件数、耗时、输出目录、warnings）
     */
    default GenerateResult generate(ProbeContext ctx) {
        long start = System.currentTimeMillis();
        Path outputDir = ctx.languageDir();
        Path staging = ProbeOutputCommitter.stagingDir(outputDir);
        Path backup = ProbeOutputCommitter.backupDir(outputDir);

        Map<String, String> files;
        try {
            files = render(ctx);
        } catch (Exception e) {
            NekoJS.LOGGER.error("Probe [{}] render failed (no disk changes made)", languageId(), e);
            return GenerateResult.failure(outputDir, GenerateResult.messageOf(e));
        }
        if (files == null) {
            return GenerateResult.failure(outputDir, "render returned no files");
        }
        String illegal = ProbeOutputCommitter.firstIllegalRelativePath(files.keySet());
        if (illegal != null) {
            NekoJS.LOGGER.error("Probe [{}] produced an illegal output path: {}", languageId(), illegal);
            return GenerateResult.failure(outputDir, "illegal output path: " + illegal);
        }

        try {
            ProbeOutputCommitter.recoverStaging(outputDir, staging, backup);
            Files.createDirectories(staging);
            for (Map.Entry<String, String> e : files.entrySet()) {
                Path file = staging.resolve(e.getKey()).normalize();
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                Files.writeString(file, e.getValue(), StandardCharsets.UTF_8);
            }
            ProbeOutputCommitter.commit(staging, outputDir, backup);
            long duration = System.currentTimeMillis() - start;
            NekoJS.LOGGER.info("Probe [{}] generated {} files in {}ms", languageId(), files.size(), duration);
            return GenerateResult.success(files.size(), duration, outputDir);
        } catch (Exception e) {
            NekoJS.LOGGER.error("Probe [{}] commit failed (old output preserved)", languageId(), e);
            try {
                ProbeOutputCommitter.deleteRecursive(staging);
            } catch (Exception cleanup) {
                NekoJS.LOGGER.debug("Probe [{}] staging cleanup after failure also failed", languageId(), cleanup);
            }
            return GenerateResult.failure(outputDir, GenerateResult.messageOf(e));
        }
    }

    /**
     * 生成结果。{@code outputDir} 供命令层/日志直接定位产物位置；{@code warnings} 携带
     * 「生成成功但存在部分降级」的信息（事件监听器抛异常、editor-config 贡献失败等）——
     * 此前这些只进日志，调用方无从感知。
     */
    record GenerateResult(
            boolean success,
            int filesGenerated,
            long durationMs,
            String message,
            Path outputDir,
            List<String> warnings
    ) {
        public GenerateResult {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        /** 兼容旧 4 参调用点（outputDir = null，warnings = 空）。 */
        public GenerateResult(boolean success, int filesGenerated, long durationMs, String message) {
            this(success, filesGenerated, durationMs, message, null, List.of());
        }

        public static GenerateResult success(int filesGenerated, long durationMs) {
            return new GenerateResult(true, filesGenerated, durationMs, "OK", null, List.of());
        }

        public static GenerateResult success(int filesGenerated, long durationMs, Path outputDir) {
            return new GenerateResult(true, filesGenerated, durationMs, "OK", outputDir, List.of());
        }

        public static GenerateResult failure(String message) {
            return new GenerateResult(false, 0, 0, message, null, List.of());
        }

        /** 带输出目录的失败（默认 generate 用——渲染/提交失败时目录仍可知，便于用户定位）。 */
        public static GenerateResult failure(Path outputDir, String message) {
            return new GenerateResult(false, 0, 0, message, outputDir, List.of());
        }

        /** 异常转失败结果；message 为 null/空时回退 {@code toString()}（避免 "backend failed: null"）。 */
        public static GenerateResult failureOf(Throwable t) {
            return failure(messageOf(t));
        }

        static String messageOf(Throwable t) {
            String m = t.getMessage();
            return m == null || m.isBlank() ? t.toString() : m;
        }

        /** 在既有结果上追加 warnings（无新增时原样返回）。 */
        public static GenerateResult withWarnings(GenerateResult base, List<String> extra) {
            if (extra == null || extra.isEmpty()) return base;
            List<String> merged = new ArrayList<>(base.warnings());
            merged.addAll(extra);
            return new GenerateResult(base.success(), base.filesGenerated(), base.durationMs(), base.message(), base.outputDir(), merged);
        }
    }
}
