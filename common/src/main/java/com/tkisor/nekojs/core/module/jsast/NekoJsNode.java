package com.tkisor.nekojs.core.module.jsast;

import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;

public sealed interface NekoJsNode permits NekoJsProgram, NekoJsStatement, NekoJsExpression, NekoJsBinding, NekoJsScope, NekoJsBlockBody, NekoJsFunctionLike, NekoJsClassBody, NekoJsClassElement {
    NekoEsmSpan span();
}
