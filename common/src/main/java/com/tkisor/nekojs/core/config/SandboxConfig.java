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
    public static SandboxConfig defaultConfig() {
        return new SandboxConfig(false, false, false, false, true, true, false, true, 30, 0L);
    }

    public boolean anyUnsafeFeatureEnabled() {
        return allowThreads || allowReflection || allowAsm;
    }
}
