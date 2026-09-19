package com.tkisor.nekojs.core.module;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.fs.ScriptPathProvider;

/**
 * 语言模块管线的阶段错误：Preparation / Resolution-Cache / Execution 三个逻辑 Module
 * 与 Pack Trust 授权门在各自 seam 抛出的可区分错误。
 *
 * <p>票据 11（语言模块管线）要求准备失败、resolve/link 失败、缓存失败和执行失败可区分
 * owner 与阶段，且错误不延迟成无来源的 Graal 异常。本类型是该区分的载体：
 * <ul>
 *   <li>{@link Stage#PREPARE}：{@code Script Preparation}（含 {@code Pack Trust} 授权拒绝，
 *       见 {@link #OWNER_PACK_TRUST}）；</li>
 *   <li>{@link Stage#RESOLVE}：{@code Module Resolution/Cache} 的路径解析失败；</li>
 *   <li>{@link Stage#LINK}：ESM link 失败统一为本类型，原始
 *      {@link com.tkisor.nekojs.core.module.esm.NekoEsmLinkException} 保留为 cause；</li>
 *   <li>{@link Stage#CACHE}：prepared 缓存自身的源码快照/读写失败；</li>
 *   <li>{@link Stage#EXECUTE}：{@code Script Execution Environment} 侧的宿主装载失败，
 *       包括 guest 运行时异常（原异常保留为 cause）。</li>
 * </ul>
 *
 * <p>继承 {@link IOException} 以保持既有调用者（{@code throws IOException}）的兼容性；
 * {@link #getMessage()} 保持原始消息文本（既有 {@code contains/startsWith} 断言不受影响），
 * 阶段归因经 {@link #stage()} / {@link #owner()} / {@link #detail()} 观察。
 */
public class NekoModuleError extends IOException {
    private static final long serialVersionUID = 1L;

    /** 失败阶段。 */
    public enum Stage {
        PREPARE,
        RESOLVE,
        LINK,
        CACHE,
        EXECUTE
    }

    /** Preparation 的 owner 名（规格 06 逻辑 Module 名）。 */
    public static final String OWNER_PREPARATION = "Script Preparation";
    /** Resolution/Cache 的 owner 名（规格 06 逻辑 Module 名）。 */
    public static final String OWNER_RESOLUTION_CACHE = "Module Resolution/Cache";
    /** Execution Environment 的 owner 名（规格 06 逻辑 Module 名）。 */
    public static final String OWNER_EXECUTION = "Script Execution Environment";
    /** Pack Trust 授权门的 owner 名：拒绝发生在准备门禁处，阶段仍记为 PREPARE。 */
    public static final String OWNER_PACK_TRUST = "Pack Trust";

    private final Stage stage;
    private final String owner;
    private final String sourcePath;
    private final String moduleId;
    private final int sourceLine;
    private final int sourceColumn;

    public NekoModuleError(Stage stage, String owner, String sourcePath, String moduleId,
                           String message, Throwable cause) {
        this(stage, owner, sourcePath, moduleId, -1, -1, message, cause);
    }

    private NekoModuleError(Stage stage, String owner, String sourcePath, String moduleId,
                            int sourceLine, int sourceColumn, String message, Throwable cause) {
        super(message, cause);
        this.stage = Objects.requireNonNull(stage, "stage");
        this.owner = owner == null || owner.isBlank() ? OWNER_RESOLUTION_CACHE : owner;
        this.sourcePath = sourcePath;
        this.moduleId = moduleId;
        this.sourceLine = sourceLine;
        this.sourceColumn = sourceColumn;
    }

    public NekoModuleError(Stage stage, String owner, String sourcePath, String moduleId, String message) {
        this(stage, owner, sourcePath, moduleId, message, null);
    }

    /** 准备失败（编译/转译/语言插件错误，原始位置保留在 cause 链与消息中）。 */
    public static NekoModuleError prepare(String sourcePath, String languageId,
                                          com.tkisor.nekojs.core.compiler.NekoModuleMode mode,
                                          String message, Throwable cause) {
        String detail = message == null ? "" : message;
        return new NekoModuleError(Stage.PREPARE, OWNER_PREPARATION, sourcePath, null,
                detail + " [language=" + languageId + ", mode=" + mode + "]", cause);
    }

    /**
     * 准备失败（带 authored 行列）：语言前端已知精确位置时发布为可观察字段，
     * 不再只留在格式化消息里。行列为 {@code -1} 表示未知。
     */
    public static NekoModuleError prepare(String sourcePath, String languageId,
                                          com.tkisor.nekojs.core.compiler.NekoModuleMode mode,
                                          int sourceLine, int sourceColumn, String message, Throwable cause) {
        String detail = message == null ? "" : message;
        return new NekoModuleError(Stage.PREPARE, OWNER_PREPARATION, sourcePath, null,
                sourceLine, sourceColumn, detail + " [language=" + languageId + ", mode=" + mode + "]", cause);
    }

    /** 授权拒绝：远端未显式授权或凭证与文件不匹配，语言边界仍可见（携带 language/mode）。 */
    public static NekoModuleError denied(String sourcePath, String languageId,
                                         com.tkisor.nekojs.core.compiler.NekoModuleMode mode,
                                         NekoTrustApprovedSource approval, String reason) {
        String credential = approval == null ? "<none>" : approval.describe();
        return new NekoModuleError(Stage.PREPARE, OWNER_PACK_TRUST, sourcePath, null,
                "Untrusted module source " + sourcePath + ": " + reason
                        + " [credential=" + credential
                        + ", language=" + languageId + ", mode=" + mode + "]");
    }

    /** 路径解析失败（包装 resolver 的原始错误，消息文本保持不变）。 */
    public static NekoModuleError resolve(String parentPath, String specifier, IOException cause) {
        String message = cause == null ? "Cannot resolve module: " + specifier : cause.getMessage();
        return new NekoModuleError(Stage.RESOLVE, OWNER_RESOLUTION_CACHE, parentPath, specifier,
                message, cause);
    }

    /** 缓存自身失败（源码快照读取等；管线抛出的 PREPARE 错误原样透传，不重标为 CACHE）。 */
    public static NekoModuleError cache(String sourcePath, String message, Throwable cause) {
        return new NekoModuleError(Stage.CACHE, OWNER_RESOLUTION_CACHE, sourcePath, null, message, cause);
    }

    /** 执行环境宿主失败（executor/factory 不可用、模块装载宿主错误；guest 异常不经此包装）。 */
    public static NekoModuleError execute(String moduleId, String message, Throwable cause) {
        return new NekoModuleError(Stage.EXECUTE, OWNER_EXECUTION, null, moduleId, message, cause);
    }

    /** Execution failure with the authored source location resolved at the host boundary. */
    public static NekoModuleError execute(String moduleId, String sourcePath, int sourceLine,
                                          int sourceColumn, String message, Throwable cause) {
        return new NekoModuleError(Stage.EXECUTE, OWNER_EXECUTION, sourcePath, moduleId,
                sourceLine, sourceColumn, message, cause);
    }

    /** Link diagnostics retain the original file/line/column exception as the cause. */
    public static NekoModuleError link(NekoEsmLinkException cause) {
        NekoEsmDiagnostic diagnostic = cause == null ? null : cause.diagnostic();
        String sourcePath = diagnostic == null || diagnostic.file() == null
                ? null : displayPath(diagnostic.file());
        String message = diagnostic == null ? rootMessage(cause) : diagnostic.toString();
        return new NekoModuleError(Stage.LINK, OWNER_RESOLUTION_CACHE, sourcePath, null, message, cause);
    }

    public Stage stage() {
        return stage;
    }

    public String owner() {
        return owner;
    }

    /** 失败关联的源码位置（prepare/cache 为文件路径；resolve 为父模块路径；execute 为 null）。 */
    public String sourcePath() {
        return sourcePath;
    }

    /** 失败关联的模块身份（resolve 为 specifier；execute 为模块 id；其余为 null）。 */
    public String moduleId() {
        return moduleId;
    }

    /** Authored source line, or {@code -1} when no source location was available. */
    public int sourceLine() {
        return sourceLine;
    }

    /** Authored source column, or {@code -1} when no source location was available. */
    public int sourceColumn() {
        return sourceColumn;
    }

    /** 含阶段/owner/位置/身份的完整归因行（日志与 Closure 证据用；getMessage 保持原始文本）。 */
    public String detail() {
        StringBuilder detail = new StringBuilder("[").append(stage).append('/').append(owner).append("] ");
        detail.append(getMessage());
        if (sourcePath != null) {
            detail.append(" (source: ").append(sourcePath).append(')');
        }
        if (moduleId != null) {
            detail.append(" (module: ").append(moduleId).append(')');
        }
        if (sourceLine > 0 && sourceColumn > 0) {
            detail.append(" (line: ").append(sourceLine).append(", column: ").append(sourceColumn).append(')');
        }
        return detail.toString();
    }

    static String displayPath(Path path) {
        return path == null ? "<unknown>" : ScriptPathProvider.authoredPath(path);
    }

    static String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown module failure";
        }
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.toString() : message;
    }
}
