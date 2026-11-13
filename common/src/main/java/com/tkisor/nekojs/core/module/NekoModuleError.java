package com.tkisor.nekojs.core.module;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.fs.ScriptPathProvider;

/**
 * Phase errors for the language module pipeline: Preparation, Resolution-Cache, and Execution logical modules
 * and the distinguishable errors thrown by the Pack Trust gate.
 *
 * <p>Preparation, resolve/link, cache, and execution failures must each name their owner and stage, and must
 * not degrade into a source-less Graal exception. This type carries that distinction:
 * <ul>
 *   <li>{@link Stage#PREPARE}: {@code Script Preparation} (including a {@code Pack Trust} authorization rejection,
 *       see {@link #OWNER_PACK_TRUST});</li>
 *   <li>{@link Stage#RESOLVE}: path resolution failure for {@code Module Resolution/Cache};</li>
 *   <li>{@link Stage#LINK}: ESM link failures are reported as this type, with the original
 *      {@link com.tkisor.nekojs.core.module.esm.NekoEsmLinkException} kept as the cause;</li>
 *   <li>{@link Stage#CACHE}: source snapshot read/write failure for the prepared cache;</li>
 *   <li>{@link Stage#EXECUTE}: host loading failure in the {@code Script Execution Environment},
 *       including guest runtime exceptions (original exception kept as the cause).</li>
 * </ul>
 *
 * <p>Extends {@link IOException} so existing callers ({@code throws IOException}) keep working.
 * {@link #getMessage()} keeps the original message text (existing {@code contains}/{@code startsWith} assertions are unaffected);
 * the stage attribution is available through {@link #stage()}, {@link #owner()}, and {@link #detail()}.
 */
public class NekoModuleError extends IOException {
    private static final long serialVersionUID = 1L;

    /** The stage where the failure occurred. */
    public enum Stage {
        PREPARE,
        RESOLVE,
        LINK,
        CACHE,
        EXECUTE
    }

    /** Logical module name for Preparation errors. */
    public static final String OWNER_PREPARATION = "Script Preparation";
    /** Logical module name for Resolution and Cache errors. */
    public static final String OWNER_RESOLUTION_CACHE = "Module Resolution/Cache";
    /** Logical module name for Execution Environment errors. */
    public static final String OWNER_EXECUTION = "Script Execution Environment";
    /** Owner name for Pack Trust authorization errors (failures occur at the preparation gate, but stage remains PREPARE). */
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

    /** Preparation failure (compilation, translation, or language plugin error; original location is in the cause chain). */
    public static NekoModuleError prepare(String sourcePath, String languageId,
                                          com.tkisor.nekojs.core.compiler.NekoModuleMode mode,
                                          String message, Throwable cause) {
        String detail = message == null ? "" : message;
        return new NekoModuleError(Stage.PREPARE, OWNER_PREPARATION, sourcePath, null,
                detail + " [language=" + languageId + ", mode=" + mode + "]", cause);
    }

    /**
     * Preparation failure including authored location (line/column).
     * When the language frontend knows the precise location, it is exposed as a field
     * rather than just inside the message. -1 indicates an unknown location.
     */
    public static NekoModuleError prepare(String sourcePath, String languageId,
                                          com.tkisor.nekojs.core.compiler.NekoModuleMode mode,
                                          int sourceLine, int sourceColumn, String message, Throwable cause) {
        String detail = message == null ? "" : message;
        return new NekoModuleError(Stage.PREPARE, OWNER_PREPARATION, sourcePath, null,
                sourceLine, sourceColumn, detail + " [language=" + languageId + ", mode=" + mode + "]", cause);
    }

    /** Authorization rejected: file not explicitly authorized or credential mismatch; visible at the language boundary. */
    public static NekoModuleError denied(String sourcePath, String languageId,
                                         com.tkisor.nekojs.core.compiler.NekoModuleMode mode,
                                         NekoTrustApprovedSource approval, String reason) {
        String credential = approval == null ? "<none>" : approval.describe();
        return new NekoModuleError(Stage.PREPARE, OWNER_PACK_TRUST, sourcePath, null,
                "Untrusted module source " + sourcePath + ": " + reason
                        + " [credential=" + credential
                        + ", language=" + languageId + ", mode=" + mode + "]");
    }

    /** Path resolution failure (wraps the original resolver error; message remains unchanged). */
    public static NekoModuleError resolve(String parentPath, String specifier, IOException cause) {
        String message = cause == null ? "Cannot resolve module: " + specifier : cause.getMessage();
        return new NekoModuleError(Stage.RESOLVE, OWNER_RESOLUTION_CACHE, parentPath, specifier,
                message, cause);
    }

    /** Cache failure (e.g., source snapshot read); preparation errors from the pipeline pass through, they are not re-marked as CACHE. */
    public static NekoModuleError cache(String sourcePath, String message, Throwable cause) {
        return new NekoModuleError(Stage.CACHE, OWNER_RESOLUTION_CACHE, sourcePath, null, message, cause);
    }

    /** Execution host failure (executor or factory unavailable, module load host error; guest exceptions are not wrapped). */
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

    /** Source location of the failure (file path for prepare/cache; parent module path for resolve; null for execute). */
    public String sourcePath() {
        return sourcePath;
    }

    /** Module identity for the failure (specifier for resolve; module ID for execute; otherwise null). */
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

    /** Full attribution line including stage, owner, location, and identity for logging; {@link #getMessage()} retains the original text. */
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
