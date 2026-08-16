package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Return;

/**
 * JS 包装对象的统一解包入口：实现者可暴露被包装的原生对象。
 */
@Doc("Implemented by JS wrappers that can expose their wrapped native object.")
public interface NekoWrapper<T> {
    /**
     * 褪去 JS 包装，获取底层原生对象
     */
    @Doc("Unwraps the JS wrapper and returns the underlying native object.")
    @Return("the wrapped native object, never null")
    T unwrap();
}