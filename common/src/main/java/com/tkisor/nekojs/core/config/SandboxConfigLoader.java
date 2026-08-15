package com.tkisor.nekojs.core.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.tkisor.nekojs.NekoJS;

import java.nio.file.Path;

public final class SandboxConfigLoader {
    public SandboxConfig load(Path engineConfig) {
        return load(engineConfig, true);
    }

    /**
     * 读取引擎配置。{@code writable = false} 用于旧位置
     * {@code <gamedir>/nekojs/config/engine.toml} 的只读回退：补默认键 / 清理废弃键
     * 只在内存中生效，不回写文件（{@code ClassFilter#loadEngineConfig} 的契约是
     * 旧文件 honored read-only，不做任何修改）。
     */
    public SandboxConfig load(Path engineConfig, boolean writable) {
        var builder = CommentedFileConfig.builder(engineConfig)
                .preserveInsertionOrder();
        if (writable) {
            builder.sync().autosave();
        }
        try (CommentedFileConfig config = builder.build()) {

            config.load();

            setupConfigEntry(config, "allowThreads", false,
                    " Allows scripts to create unmanaged background threads. May cause lag or resource leaks.");

            setupConfigEntry(config, "allowReflection", false,
                    " Allows scripts to bypass access controls via reflection and modify private Java data.");

            setupConfigEntry(config, "allowAsm", false,
                    " Allows scripts to directly manipulate Java bytecode. Incorrect usage may cause severe crashes.");

            setupConfigEntry(config, "allowFsWriteOutsideNekojs", false,
                    " Allows Node fs write/delete operations anywhere under the game directory instead of only under nekojs/. Still blocks paths outside .minecraft.");

            removeConfigEntry(config, "prependRequirePatch");
            removeConfigEntry(config, "useNekoScriptLoader");
            removeConfigEntry(config, "useNativeEsmLoader");

            setupConfigEntry(config, "enableEsmAuthoring", true,
                    " Enables ESM authoring support for .js/.mjs/.ts/.jsx/.tsx scripts. When enabled, NekoJS parses each module and transforms ESM syntax into the unified script runtime. Disable only if you need pure CommonJS require compatibility.");

            setupConfigEntry(config, "conciseScriptErrorLogs", true,
                    " Emits direct source-focused script errors by default. Set false to log full verbose diagnostics and stack traces for analysis.");

            setupConfigEntry(config, "jsxAutomaticRuntime", false,
                    " Uses the automatic JSX runtime for .jsx/.tsx scripts: emits jsx()/Fragment() calls and imports from 'nekojs/jsx-runtime' instead of the classic globalThis.__nekoJsxFactory. Requires a jsx-runtime module (place one at nekojs/node_modules/nekojs/jsx-runtime.js).");

            setupConfigEntry(config, "scriptMemberValidation", true,
                    " Enables compile-time validation of global-binding and event-callback member accesses. Reports typos and missing members to the in-game error panel. Disable to skip all AST parsing overhead in production modpacks.");

            setupConfigEntry(config, "scriptEvaluationTimeoutSeconds", 30,
                    " Maximum seconds to wait for a script entry to finish evaluating (top-level await / native ESM). On timeout the script is marked as failed and the server thread stops waiting instead of hanging forever. Set 0 or a negative value to disable the timeout.");

            setupConfigEntry(config, "scriptStatementLimit", SandboxConfig.DEFAULT_SCRIPT_STATEMENT_LIMIT,
                    " Graal ResourceLimits statement cap for each script environment Context (0 = disabled). A cumulative cap: when exceeded, Graal closes the Context and aborts the current evaluation, which is the only reliable way to stop a while(true){} infinite loop inside a synchronous entry. The server thread is unblocked and the script is marked failed; subsequent evaluations run in an automatically rebuilt Context, but listeners/timers registered by the dead Context are lost - run /nekojs reload to fully restore the environment. Defaults to 50000000, which is generous enough for long-running servers; set 0 explicitly to disable the cap.");

            return new SandboxConfig(
                    config.get("allowThreads"),
                    config.get("allowReflection"),
                    config.get("allowAsm"),
                    config.get("allowFsWriteOutsideNekojs"),
                    config.get("enableEsmAuthoring"),
                    config.get("conciseScriptErrorLogs"),
                    config.get("jsxAutomaticRuntime"),
                    config.get("scriptMemberValidation"),
                    (int) numberValue(config, "scriptEvaluationTimeoutSeconds", 30),
                    numberValue(config, "scriptStatementLimit", SandboxConfig.DEFAULT_SCRIPT_STATEMENT_LIMIT)
            );
        } catch (Throwable e) {
            NekoJS.LOGGER.warn("Failed to load config/nekojs-engine.toml, using default sandbox config", e);
            return SandboxConfig.defaultConfig();
        }
    }

    /**
     * 按数值类型安全读取整型配置项。NightConfig 的 TOML 解析器会把整数存成
     * Integer/Long 等不同装箱类型，直接以 {@code long} 目标类型调用 {@code get} 会触发
     * 拆箱 ClassCastException，导致整个文件被误判为损坏而回退默认配置（用户显式写的
     * {@code scriptStatementLimit = 0} 曾因此被吞掉）。统一走 {@link Number#longValue()}。
     */
    private static long numberValue(CommentedFileConfig config, String path, long fallback) {
        Object value = config.get(path);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static void setupConfigEntry(CommentedFileConfig config, String path, Object defaultValue, String comment) {
        if (!config.contains(path)) {
            config.set(path, defaultValue);
            config.setComment(path, comment);
        }
    }

    private static void removeConfigEntry(CommentedFileConfig config, String path) {
        if (config.contains(path)) {
            config.remove(path);
            config.setComment(path, null);
        }
    }
}
