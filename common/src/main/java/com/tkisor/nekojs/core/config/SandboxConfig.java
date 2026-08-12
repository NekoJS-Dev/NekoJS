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
        int scriptEvaluationTimeoutSeconds
) {
    public static SandboxConfig defaultConfig() {
        return new SandboxConfig(false, false, false, false, true, true, false, true, 30);
    }

    public boolean anyUnsafeFeatureEnabled() {
        return allowThreads || allowReflection || allowAsm;
    }
}
