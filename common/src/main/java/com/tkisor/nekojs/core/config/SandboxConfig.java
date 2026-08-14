package com.tkisor.nekojs.core.config;

public record SandboxConfig(
        boolean allowThreads,
        boolean allowReflection,
        boolean allowAsm,
        boolean allowFsWriteOutsideNekojs,
        boolean enableEsmAuthoring,
        boolean conciseScriptErrorLogs,
        boolean jsxAutomaticRuntime,
        boolean scriptMemberValidation,
        int scriptEvaluationTimeoutSeconds,
        /** Graal ResourceLimits 语句上限（0 = 禁用）。超过后 Graal 会关闭整个 Context 并中断当前求值，
         * 是终止 while(true){} 死循环的最后手段；ScriptManager 会在下一次取用时自动重建 Context。 */
        long scriptStatementLimit
) {
    /**
     * 语句上限默认值（5e7）：足够长跑服务器累计执行，又能最终终止 while(true){} 死循环。
     * 用户在 config/nekojs-engine.toml 显式写 0 仍表示禁用（NekoSandboxFactory 对 <=0 不设置 ResourceLimits）。
     */
    public static final long DEFAULT_SCRIPT_STATEMENT_LIMIT = 50_000_000L;

    public static SandboxConfig defaultConfig() {
        return new SandboxConfig(false, false, false, false, true, true, false, true, 30, DEFAULT_SCRIPT_STATEMENT_LIMIT);
    }

    /** 任一高危能力开关被打开（含 fs 写越界）：驱动客户端安全警告 toast/chat。 */
    public boolean anyUnsafeFeatureEnabled() {
        return allowThreads || allowReflection || allowAsm || allowFsWriteOutsideNekojs;
    }
}
