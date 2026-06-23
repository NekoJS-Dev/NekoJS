package com.tkisor.nekojs.core.config;

public record SandboxConfig(
        boolean allowThreads,
        boolean allowReflection,
        boolean allowAsm,
        boolean allowFsWriteOutsideNekojs,
        boolean enableEsmAuthoring,
        boolean conciseScriptErrorLogs
) {
    public static SandboxConfig defaultConfig() {
        return new SandboxConfig(false, false, false, false, true, true);
    }

    public boolean anyUnsafeFeatureEnabled() {
        return allowThreads || allowReflection || allowAsm;
    }
}
