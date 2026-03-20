package com.tkisor.nekojs.api.event;

/**
 * Marker interface
 * @author ZZZank
 */
public interface NekoCancellableEvent {

    static boolean testType(Class<?> type) {
        return NekoCancellableEvent.class.isAssignableFrom(type);
    }
}
