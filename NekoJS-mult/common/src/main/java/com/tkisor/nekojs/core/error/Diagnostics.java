package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.NekoJS;

/**
 * 宿主侧诊断通道（W4/A5）：把散落在各处的「catch-and-continue」失败从 DEBUG/静默
 * 升级为带作用域标签的可见报告。
 *
 * <p>边界（与 {@link ErrorTracker} 分工）：ErrorTracker 只记录脚本入口执行、事件/timer
 * callback 等<strong>脚本归因</strong>错误并进入游戏内错误面板；Diagnostics 面向宿主侧
 * 「某个东西悄悄不工作了」的工程性失败——发现目录部分失败、预检 schema 降级、同步丢文件、
 * probe/editor 配置未写入等。默认经根 logger 以 {@code [scope] message} 形式输出 WARN/ERROR；
 * 宿主可 {@link #install(Sink)} 换装自己的汇聚端（如未来转发进错误面板）。
 *
 * <p>调用契约：report 永不抛错（诊断通道自身故障只降级为根 logger），可在任意 catch 块
 * 直接使用；scope 用稳定的小写串（如 {@code script-discovery}、{@code script-sync}），
 * 便于用户 grep 与宿主端按 scope 过滤/限流。
 */
public final class Diagnostics {

    public enum Severity { WARN, ERROR }

    /** 一次诊断报告：scope 定位子系统，message 面向用户（中文），throwable 可为 null。 */
    public record Event(String scope, Severity severity, String message, Throwable throwable) {}

    @FunctionalInterface
    public interface Sink {
        void accept(Event event);
    }

    private static volatile Sink sink = Diagnostics::logToRootLogger;

    private Diagnostics() {}

    /** 宿主换装汇聚端；传 null 恢复默认根 logger 行为。 */
    public static void install(Sink newSink) {
        sink = newSink == null ? Diagnostics::logToRootLogger : newSink;
    }

    public static void report(String scope, Severity severity, String message) {
        report(scope, severity, message, null);
    }

    public static void report(String scope, Severity severity, String message, Throwable throwable) {
        if (scope == null || scope.isBlank()) {
            scope = "nekojs";
        }
        Event event = new Event(scope, severity, message, throwable);
        try {
            sink.accept(event);
        } catch (Throwable suppressed) {
            // 诊断通道绝不向调用方抛错；sink 自身故障退回根 logger
            logToRootLogger(event);
        }
    }

    private static void logToRootLogger(Event event) {
        String line = "[" + event.scope() + "] " + event.message();
        if (event.severity() == Severity.ERROR) {
            NekoJS.LOGGER.error(line, event.throwable());
        } else {
            NekoJS.LOGGER.warn(line, event.throwable());
        }
    }
}
