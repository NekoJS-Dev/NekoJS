package com.tkisor.nekojs.core.module.esm;

import java.io.IOException;

public final class NekoEsmLinkException extends IOException {
    private static final long serialVersionUID = 1L;

    // 诊断载荷运行时只读，异常从不跨进程序列化；保留字段（而非 transient）避免序列化时静默丢数据
    @SuppressWarnings("serial")
    private final NekoEsmDiagnostic diagnostic;

    public NekoEsmLinkException(NekoEsmDiagnostic diagnostic) {
        super(diagnostic.toString());
        this.diagnostic = diagnostic;
    }

    public NekoEsmDiagnostic diagnostic() {
        return diagnostic;
    }
}
