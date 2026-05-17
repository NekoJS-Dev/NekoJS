package com.tkisor.nekojs.core.module.jsast;

public sealed interface NekoJsStatement extends NekoJsNode permits NekoJsImportDeclaration, NekoJsExportDeclaration, NekoJsDeclarationStatement, NekoJsRuntimeExpressionStatement {
    String raw();
}
