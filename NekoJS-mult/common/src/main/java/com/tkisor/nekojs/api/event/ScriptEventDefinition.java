package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;

public record ScriptEventDefinition(
        String groupName,
        String eventName,
        ScriptType targetType,
        String eventClassName,
        String sourceScriptId,
        EventBusJS<?, ?> bus,
        Runnable unregisterer
) {
    public boolean canApplyOn(ScriptType type) {
        return targetType.test(type);
    }

    /**
     * Clear listener tokens for this custom event.
     *
     * <p>BUG-B3: {@code ScriptEvents}-registered custom events declare a
     * {@code targetType} of SERVER/CLIENT, but JS listener tokens are bucketed in
     * {@link EventBusJS} by the registering script's ScriptType — and since
     * {@code ScriptEvents} is STARTUP-only, the tokens always land in the STARTUP
     * bucket. So {@code clearListeners(SERVER)} would clear the (empty) SERVER
     * bucket and leave the real tokens orphaned in STARTUP across reloads.
     *
     * <p>Because a custom-event bus is single-purpose, clearing ALL type buckets on
     * any reload is the simplest correct fix: we ignore {@code type} and sweep every
     * ScriptType bucket. The per-script overload {@link #clearListeners(ScriptType,
     * String)} is left intact.
     */
    public void clearListeners(ScriptType type) {
        for (ScriptType t : ScriptType.all()) {
            bus.clearTokens(t);
        }
    }

    public void clearListeners(ScriptType type, String scriptId) {
        bus.clearTokens(type, scriptId);
    }

    public void clearListenersByPrefix(ScriptType type, String scriptIdPrefix) {
        bus.clearTokensByPrefix(type, scriptIdPrefix);
    }

    public void unregister() {
        unregisterer.run();
        for (ScriptType type : ScriptType.all()) {
            bus.clearTokens(type);
        }
    }
}
