package com.tkisor.nekojs.core.state;

/**
 * global/shared 候选写集或联合计划的失败（票 10）。
 *
 * <p>携带结构化 domain（如 {@code "global-write-conflict"}、{@code "state-plan-preflight:test"}），
 * 由 {@code ScriptManager} 转换为 {@code ReloadPhase.STATE_PLAN} 阶段的
 * {@code NekoReloadException}（owner/domain 可观察，spec 09 失败结果契约）。
 */
public class GlobalStateException extends RuntimeException {

    private final String domain;

    public GlobalStateException(String domain, String message) {
        super(message);
        this.domain = domain;
    }

    public GlobalStateException(String domain, String message, Throwable cause) {
        super(message, cause);
        this.domain = domain;
    }

    /** 结构化失败 domain（进入 {@code ReloadFailureReport.domain()}）。 */
    public String domain() {
        return domain;
    }
}
