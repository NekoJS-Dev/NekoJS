package com.tkisor.nekojs.core.module.esm;

public sealed interface NekoEsmStatement permits NekoEsmImportDecl, NekoEsmExportDecl {
    NekoEsmSpan span();

    String raw();
}
