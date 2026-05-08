package com.tkisor.nekojs.wrapper;

public interface NekoWrapper<T> {
    /**
     * 褪去 JS 包装，获取底层原生对象
     */
    T unwrap();
}