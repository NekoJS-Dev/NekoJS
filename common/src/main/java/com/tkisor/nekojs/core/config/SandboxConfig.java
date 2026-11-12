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
        String packSyncMode,
        boolean packSyncAllowUnsigned,
        boolean dynamicRegistryEnabled,
        /** Enables native MixinJS startup integration only after an explicit engine opt-in. */
        boolean mixinJsEnabled
) {
    public static final String PACK_SYNC_OFF = "off";
    public static final String PACK_SYNC_HASH_ONLY = "hashOnly";
    public static final String PACK_SYNC_ALL = "all";

    /** Retains pack and dynamic settings while leaving MixinJS disabled by default. */
    public SandboxConfig(
            boolean allowThreads, boolean allowReflection, boolean allowAsm, boolean allowFsWriteOutsideNekojs,
            boolean enableEsmAuthoring, boolean conciseScriptErrorLogs, boolean jsxAutomaticRuntime,
            boolean scriptMemberValidation, String packSyncMode, boolean packSyncAllowUnsigned,
            boolean dynamicRegistryEnabled
    ) {
        this(allowThreads, allowReflection, allowAsm, allowFsWriteOutsideNekojs, enableEsmAuthoring,
                conciseScriptErrorLogs, jsxAutomaticRuntime, scriptMemberValidation, packSyncMode,
                packSyncAllowUnsigned, dynamicRegistryEnabled, false);
    }

    /** Short constructor for the eight independent script permission and authoring settings. */
    public SandboxConfig(
            boolean allowThreads, boolean allowReflection, boolean allowAsm, boolean allowFsWriteOutsideNekojs,
            boolean enableEsmAuthoring, boolean conciseScriptErrorLogs, boolean jsxAutomaticRuntime,
            boolean scriptMemberValidation
    ) {
        this(allowThreads, allowReflection, allowAsm, allowFsWriteOutsideNekojs, enableEsmAuthoring,
                conciseScriptErrorLogs, jsxAutomaticRuntime, scriptMemberValidation, PACK_SYNC_OFF, false, false, false);
    }

    public static SandboxConfig defaultConfig() {
        return new SandboxConfig(false, false, false, false, true, true, false, true);
    }

    public boolean packSyncEnabled() {
        return !PACK_SYNC_OFF.equalsIgnoreCase(packSyncMode);
    }

    public boolean anyUnsafeFeatureEnabled() {
        return allowThreads || allowReflection || allowFsWriteOutsideNekojs;
    }
}
