package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiDiagnostic;
import com.tkisor.nekojs.api.ui.UiDiagnosticSink;
import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiFailure;

/**
 * 诊断记录助手：把 {@link UiFailure} 与任意异常归一到 {@link UiDiagnostic}，
 * 统一写入 root 的 {@link UiDiagnosticSink}（AC5 的统一诊断 seam）。
 */
final class UiDiagnostics {

    private UiDiagnostics() {
    }

    static UiDiagnostic of(UiDiagnosticStage stage, String code, String message,
                           String nodePath, String rootId, int generation, Throwable cause) {
        return new UiDiagnostic(stage, code, message, nodePath, rootId, generation, cause);
    }

    static UiDiagnostic fromFailure(UiFailure failure, String fallbackPath,
                                    String rootId, int generation) {
        String path = failure.nodePath() == null ? fallbackPath : failure.nodePath();
        return new UiDiagnostic(failure.stage(), failure.code(), rootMessage(failure),
                path, rootId, generation, failure.getCause() == null ? failure : failure.getCause());
    }

    static UiDiagnostic fromThrowable(UiDiagnosticStage stage, String code, Throwable failure,
                                      String nodePath, String rootId, int generation) {
        return new UiDiagnostic(stage, code, rootMessage(failure), nodePath, rootId, generation, failure);
    }

    static void record(UiDiagnosticSink sink, UiDiagnostic diagnostic) {
        if (sink == null) return;
        try {
            sink.record(diagnostic);
        } catch (RuntimeException ignored) {
            // 诊断实现不得让 root 崩溃；丢弃该条诊断即可。
        }
    }

    private static String rootMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
