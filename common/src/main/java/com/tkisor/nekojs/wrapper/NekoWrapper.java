package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Return;

/**
 * Common entry point for unwrapping a JS wrapper. An implementor exposes the native object it
 * wraps.
 */
@Doc("Implemented by JS wrappers that can expose their wrapped native object.")
public interface NekoWrapper<T> {
    /** Unwraps the JS wrapper and returns the underlying native object. */
    @Doc("Unwraps the JS wrapper and returns the underlying native object.")
    @Return("the wrapped native object, never null")
    T unwrap();
}