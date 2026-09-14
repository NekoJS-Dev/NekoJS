package com.tkisor.nekojs.api.ui;

import java.util.Objects;

/**
 * 统一 UI 诊断条目（jsx-ui-spec「异常处理」）：阶段、节点路径、UI root、generation 与
 * 脚本来源必须能区分（AC5）。
 *
 * @param stage     失败阶段
 * @param code      稳定错误码（如 {@code DUPLICATE_KEY}、{@code UNKNOWN_PRIMITIVE}）
 * @param message   人类可读描述（普通形式，不携带修复指引）
 * @param nodePath  出错节点的稳定路径（{@code screen/row[2]/button}），可为 {@code null}
 * @param rootId    UI root 标识
 * @param generation UI root 绑定的 CLIENT generation
 * @param cause     原始异常，可为 {@code null}
 */
public record UiDiagnostic(
        UiDiagnosticStage stage,
        String code,
        String message,
        String nodePath,
        String rootId,
        int generation,
        Throwable cause
) {
    public UiDiagnostic {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
