package com.tkisor.nekojs.bindings.static_access;

/**
 * 1.12.2 NativeEventsJS — native Forge event bridge is not implemented on this platform.
 */
public class NativeEventsJS {
    public void onEvent(String eventClass, Object handler) {
        throw new UnsupportedOperationException(
                "NativeEvents.onEvent is not supported on 1.12.2 (native Forge event bridge is unimplemented). "
                + "Use the typed event bindings (PlayerEvents, ServerEvents, BlockEvents, ...) instead.");
    }
}
