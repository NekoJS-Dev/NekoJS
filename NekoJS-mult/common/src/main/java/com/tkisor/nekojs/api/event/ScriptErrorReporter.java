package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import graal.graalvm.polyglot.PolyglotException;

/**
 * Static accessor for script error reporting, breaking the api→core dependency
 * that {@code EventBusJS} previously had on {@code DefaultErrorTracker}.
 * <p>
 * Set once during bootstrap（由共享装配函数 {@code NekoRuntimeAssembly} 安装，
 * 包装 root 的 ErrorTracker）; the default no-op implementation silently discards
 * errors until the real reporter is installed.
 *
 * <p>owner 边界（ticket 05 总账 A5）：本类是 root-owned ErrorTracker 的进程级静态报告门面，
 * 只由装配序列 set 一次，不构成第二 runtime owner；供 mixin 等无法注入的静态上下文
 * （如 RecipeManagerMixin）改道读取错误面。
 */
public final class ScriptErrorReporter {
    private static volatile Reporter instance = Reporter.NOOP;

    private ScriptErrorReporter() {}

    public interface Reporter {
        void recordCallbackError(ScriptType type, String callbackKind, Throwable throwable);

        /**
         * 事件回调（配方脚本等）抛出的 Graal 异常上报（语义同 ErrorTracker#recordEventError）。
         * <p>线程约束：可从任意线程调用（recipe 处理在服务端线程，mixin 命中点可能在渲染线程）；
         * 实现须自行保证线程安全。reload 语义：{@code set} 每进程只发生一次（装配期），
         * reload 不重建 reporter——实现不得假设"每次 reload 换实例"。</p> */
        default void recordEventError(ScriptType type, PolyglotException error) {
            recordCallbackError(type, "event", error);
        }

        /** 是否累计有错误（语义同 ErrorTracker#hasErrors）。线程约束同 {@link #recordEventError}。 */
        default boolean hasErrors() {
            return false;
        }

        /** 错误条数（语义同 ErrorTracker 错误集合大小 / ErrorSnapshot#count）。线程约束同 {@link #recordEventError}。 */
        default int errorCount() {
            return 0;
        }

        Reporter NOOP = (type, kind, throwable) -> {};
    }

    public static void set(Reporter reporter) {
        instance = reporter == null ? Reporter.NOOP : reporter;
    }

    public static void recordCallbackError(ScriptType type, String callbackKind, Throwable throwable) {
        instance.recordCallbackError(type, callbackKind, throwable);
    }

    /** 事件回调 Graal 异常上报（经 root 的 tracker；静态上下文的注入替代面）。 */
    public static void recordEventError(ScriptType type, PolyglotException error) {
        instance.recordEventError(type, error);
    }

    public static boolean hasErrors() {
        return instance.hasErrors();
    }

    public static int errorCount() {
        return instance.errorCount();
    }
}
