package com.tkisor.nekojs.api.ui;

/**
 * UI 诊断汇（唯一诊断 seam）：所有 render/component/layout/event/host-update 失败都经此上报。
 *
 * <p>实现由 root 持有并接入既有 CLIENT 诊断链路；common 侧只定义 seam，不新建第二套
 * 日志/错误总线（AC7）。
 */
@FunctionalInterface
public interface UiDiagnosticSink {

    /** 记录一条诊断。实现不得向外抛出。 */
    void record(UiDiagnostic diagnostic);

    /** 丢弃全部诊断的默认实现（测试/无诊断环境用）。 */
    UiDiagnosticSink NOOP = diagnostic -> { };
}
