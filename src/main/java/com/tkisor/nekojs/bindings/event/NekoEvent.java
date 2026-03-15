package com.tkisor.nekojs.bindings.event;

public interface NekoEvent {
    default void cancel() {
    }

    default boolean isCancelled() {
        return false;
    }

    /** 事件派发结束后回调 */
    default void afterPosted() {
    }
}
