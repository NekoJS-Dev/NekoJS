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
        /** Graal ResourceLimits 语句累计上限（0 = 禁用，默认）。Context 生命周期内语句总量达到上限即关闭
         * 整个 Context 并中断当前求值。注意这是「终身总量」语义：忙碌服务器上长驻环境终会触顶（触发后
         * 下次取用时自动重建，但已注册的 listeners/timers 会丢失）——常规失控保护请用
         * {@link #scriptRunawayTimeoutSeconds}，本上限仅作为整合包作者需要的硬性预算兜底。 */
        long scriptStatementLimit,
        /** 同步执行失控看门狗（秒，0 = 禁用，默认关闭）。基于语句检查点的滑动窗口：只要宿主代码持续执行
          未让出（检查点间隙 ≤ 250ms，含事件间隙/长宿主调用后的返回），窗口累加；超过该秒数即判定失控
          循环并关闭 Context——这是终止同步 while(true){} 的唯一可靠手段。每次让出都会重置窗口，
          长驻环境无论累计执行多少语句都不会被误杀。不消耗语句的阻塞（宿主调用内部 IO/sleep）不可见。 */
        int scriptRunawayTimeoutSeconds,
        /** 多人脚本包分发模式（engine.toml [packSync] mode）："off"（默认，禁用）/ "hashOnly"（仅同步哈希不执行）
         * / "all"（配置阶段推送包并在客户端执行）。客户端执行仍走 ClassFilter/Watchdog 沙箱。 */
        String packSyncMode,
        /** [packSync] allowUnsigned：是否接受无签名的服务器脚本包（默认 false——未签名包拒绝并断连提示）。 */
        boolean packSyncAllowUnsigned,
        /** [dynamicRegistry] enabled：服务器启动后的运行时注册（WORLD/RELOADABLE 物品等），默认 false 关闭。 */
        boolean dynamicRegistryEnabled
) {
    /** 失控看门狗默认窗口：0（禁用）——按项目要求，保护机制默认全部关闭，需要者显式开启。 */
    public static final int DEFAULT_SCRIPT_RUNAWAY_TIMEOUT_SECONDS = 0;

    public static final String PACK_SYNC_OFF = "off";
    public static final String PACK_SYNC_HASH_ONLY = "hashOnly";
    public static final String PACK_SYNC_ALL = "all";

    /** 兼容构造器：新键取默认值（包分发关闭、动态注册关闭）——既有调用点零改动。 */
    public SandboxConfig(
            boolean allowThreads, boolean allowReflection, boolean allowAsm, boolean allowFsWriteOutsideNekojs,
            boolean enableEsmAuthoring, boolean conciseScriptErrorLogs, boolean jsxAutomaticRuntime,
            boolean scriptMemberValidation, int scriptEvaluationTimeoutSeconds, long scriptStatementLimit,
            int scriptRunawayTimeoutSeconds
    ) {
        this(allowThreads, allowReflection, allowAsm, allowFsWriteOutsideNekojs, enableEsmAuthoring,
                conciseScriptErrorLogs, jsxAutomaticRuntime, scriptMemberValidation, scriptEvaluationTimeoutSeconds,
                scriptStatementLimit, scriptRunawayTimeoutSeconds, PACK_SYNC_OFF, false, false);
    }

    public static SandboxConfig defaultConfig() {
        return new SandboxConfig(false, false, false, false, true, true, false, true, 30, 0,
                DEFAULT_SCRIPT_RUNAWAY_TIMEOUT_SECONDS, PACK_SYNC_OFF, false, false);
    }

    /** 包分发是否启用（mode != off）。 */
    public boolean packSyncEnabled() {
        return !PACK_SYNC_OFF.equalsIgnoreCase(packSyncMode);
    }

    /** 任一高危能力开关被打开（含 fs 写越界）：驱动客户端安全警告 toast/chat。 */
    public boolean anyUnsafeFeatureEnabled() {
        return allowThreads || allowReflection || allowFsWriteOutsideNekojs;
    }
}
