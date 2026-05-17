package com.tkisor.nekojs.core.module.jsast;

public enum NekoJsExpressionKind {
    RAW,
    IDENTIFIER,
    LITERAL,
    CALL,
    MEMBER,
    OBJECT,
    ARRAY,
    FUNCTION,
    ARROW_FUNCTION,
    CLASS,
    AWAIT,
    DYNAMIC_IMPORT,
    IMPORT_META
}
