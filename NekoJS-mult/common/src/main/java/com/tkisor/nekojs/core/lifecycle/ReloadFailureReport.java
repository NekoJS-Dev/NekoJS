package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.api.ScriptType;

/**
 * reload 失败的对外结构化结果（工单 06 AC3）。
 *
 * <p>外部可见字段：generation（失败候选的 generation 序号）、phase（失败阶段）、
 * sourceLocation（失败脚本位置，null 表示阶段级失败与环境无关）、owner（失败环境的
 * owner 标识）、domain（失败所属子系统描述）。错误结果不携带修复指引，也不把内部锁
 * 或私有对象当契约——消费方只读本 record 的字段与 {@link #describe()} 文本。
 *
 * @param generation     失败候选的 generation 序号（单调递增，0 表示首个环境）
 * @param phase          失败发生的阶段
 * @param sourceLocation 失败脚本位置（{@code server_scripts/foo.js} 风格），阶段级失败为 null
 * @param owner          失败环境 owner 标识（如 {@code ScriptManager[SERVER]}）
 * @param domain         失败所属子系统（如 {@code candidate-context} / {@code script-execution}）
 * @param error          原始异常
 */
public record ReloadFailureReport(
        ScriptType type,
        long generation,
        ReloadPhase phase,
        String sourceLocation,
        String owner,
        String domain,
        Throwable error
) {
    public ReloadFailureReport {
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(phase, "phase");
        java.util.Objects.requireNonNull(owner, "owner");
        java.util.Objects.requireNonNull(domain, "domain");
    }

    /** 单行结构化描述（命令/日志外部可见文本；不含修复指引）。 */
    public String describe() {
        return "reload failed: type=" + type.name
                + " generation=" + generation
                + " phase=" + phase
                + (sourceLocation == null ? "" : " source=" + sourceLocation)
                + " owner=" + owner
                + " domain=" + domain
                + " error=" + (error == null ? "unknown" : error);
    }
}
