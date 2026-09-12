package com.tkisor.nekojs.core.lifecycle;

/**
 * 事务式 reload 失败：携带结构化 {@link ReloadFailureReport}（generation / phase /
 * source location / owner / domain，工单 06 AC3）。
 *
 * <p>由 {@code ScriptManager} 的事务式 reload 路径抛出，经 {@code NekoRuntimeRoot.reload}
 * 透传给命令与平台调用方。消息文本与 {@link ReloadFailureReport#describe()} 一致，
 * 不含修复指引。
 */
public final class NekoReloadException extends RuntimeException {
    private final ReloadFailureReport report;

    public NekoReloadException(ReloadFailureReport report) {
        super(report.describe(), report.error());
        this.report = report;
    }

    /** 结构化失败结果；与 {@link #getMessage()} 携带同一信息。 */
    public ReloadFailureReport report() {
        return report;
    }
}
