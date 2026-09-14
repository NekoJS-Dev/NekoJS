package com.tkisor.nekojs.api.ui;

import java.util.Objects;

/**
 * UI 失败信号：携带失败阶段、稳定错误码与节点路径（AC5）。
 *
 * <p>普通形式错误消息，不携带修复指引（CONTEXT.md 脚本 API 约定）。
 */
public class UiFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 稳定错误码常量。 */
    public static final String UNKNOWN_PRIMITIVE = "UNKNOWN_PRIMITIVE";
    public static final String INVALID_PROP = "INVALID_PROP";
    public static final String INVALID_LAYOUT = "INVALID_LAYOUT";
    public static final String DUPLICATE_KEY = "DUPLICATE_KEY";
    public static final String INVALID_CHILD = "INVALID_CHILD";
    public static final String INVALID_ROOT = "INVALID_ROOT";
    public static final String INVALID_COMPONENT_RESULT = "INVALID_COMPONENT_RESULT";
    public static final String UNRESOLVED_COMPONENT = "UNRESOLVED_COMPONENT";
    public static final String UNSUPPORTED_CAPABILITY = "UNSUPPORTED_CAPABILITY";
    public static final String ROOT_DISPOSED = "ROOT_DISPOSED";
    public static final String OFF_OWNER_THREAD = "OFF_OWNER_THREAD";
    public static final String ADAPTER_FAILURE = "ADAPTER_FAILURE";

    private final UiDiagnosticStage stage;
    private final String code;
    private final String nodePath;

    public UiFailure(UiDiagnosticStage stage, String code, String message, String nodePath, Throwable cause) {
        super(message, cause);
        this.stage = Objects.requireNonNull(stage, "stage");
        this.code = Objects.requireNonNull(code, "code");
        this.nodePath = nodePath;
    }

    public UiFailure(UiDiagnosticStage stage, String code, String message, String nodePath) {
        this(stage, code, message, nodePath, null);
    }

    /** 失败阶段。 */
    public UiDiagnosticStage stage() {
        return stage;
    }

    /** 稳定错误码。 */
    public String code() {
        return code;
    }

    /** 出错节点路径，可为 {@code null}。 */
    public String nodePath() {
        return nodePath;
    }
}
