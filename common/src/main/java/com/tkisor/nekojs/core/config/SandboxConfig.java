package com.tkisor.nekojs.core.config;

import java.util.List;
import java.util.Map;

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
        boolean mixinJsEnabled,
        Map<String, LanguagePipeline> languagePipelines
) {
    public static final String PACK_SYNC_OFF = "off";
    public static final String PACK_SYNC_HASH_ONLY = "hashOnly";
    public static final String PACK_SYNC_ALL = "all";

    public SandboxConfig {
        languagePipelines = Map.copyOf(languagePipelines);
    }

    /** A null field leaves the selection unspecified; an empty check list disables that stage. */
    public record LanguagePipeline(String frontend, List<String> sourceChecks, List<String> outputChecks,
                                   ProbeReference probe) {
        public LanguagePipeline {
            sourceChecks = sourceChecks == null ? null : List.copyOf(sourceChecks);
            outputChecks = outputChecks == null ? null : List.copyOf(outputChecks);
        }

        public LanguagePipeline(String frontend, List<String> sourceChecks, List<String> outputChecks) {
            this(frontend, sourceChecks, outputChecks, null);
        }
    }

    /** Identifies a Probe backend by its target language and backend name. */
    public record ProbeReference(String language, String backend) {}

    public SandboxConfig(
            boolean allowThreads, boolean allowReflection, boolean allowAsm, boolean allowFsWriteOutsideNekojs,
            boolean enableEsmAuthoring, boolean conciseScriptErrorLogs, boolean jsxAutomaticRuntime,
            boolean scriptMemberValidation, String packSyncMode, boolean packSyncAllowUnsigned,
            boolean dynamicRegistryEnabled, boolean mixinJsEnabled
    ) {
        this(allowThreads, allowReflection, allowAsm, allowFsWriteOutsideNekojs, enableEsmAuthoring,
                conciseScriptErrorLogs, jsxAutomaticRuntime, scriptMemberValidation, packSyncMode,
                packSyncAllowUnsigned, dynamicRegistryEnabled, mixinJsEnabled, Map.of());
    }

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
