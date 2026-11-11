package com.tkisor.nekujs.proof;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroupJS;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluator/ABI-only bridge, never a production compatibility engine.
 * Definitions stay lazy so absent optional addons are not resolved. Only the
 * non-cancellable CommonAddedEvents.playerRespawn channel can be bound or posted.
 * The caller owns the Graal Context; this scope owns and clears native listener tokens.
 */
public final class NativeEventProofScope implements EventGroupRegistry, AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final Map<String, EventGroup> groups = new LinkedHashMap<>();
    private final com.tkisor.nekojs.api.event.EventGroup nativeGroup =
            com.tkisor.nekojs.api.event.EventGroup.of("CommonAddedEvents");
    private final EventBusJS<KubeEvent, Void> respawn = nativeGroup.server("playerRespawn", KubeEvent.class);
    private boolean closed;

    public NativeEventProofScope() {
        nativeGroup.freeze();
    }

    @Override
    public void register(EventGroup group) {
        checkOpen();
        Objects.requireNonNull(group, "group");
        if (!group.name.equals("CommonAddedEvents")) {
            throw unsupported("register group " + group.name);
        }
        if (groups.containsKey(group.name)) {
            throw new IllegalArgumentException("NEKO-NEKU-PROOF-DUPLICATE: duplicate group " + group.name);
        }
        group.attachToProof(this);
        groups.put(group.name, group);
    }

    public Map<String, EventGroup> groups() {
        checkOpen();
        return Map.copyOf(groups);
    }

    public ProxyObject binding() {
        checkOpen();
        supportedHandler();
        EventGroupJS view = new EventGroupJS(nativeGroup, com.tkisor.nekojs.api.ScriptType.SERVER);
        ProxyExecutable nativeRegistration = (ProxyExecutable) view.getMember("playerRespawn");
        // Even a saved guest callable must reject registration after this owner closes.
        ProxyExecutable scopedRegistration = arguments -> {
            checkOpen();
            return nativeRegistration.execute(arguments);
        };
        return ProxyObject.fromMap(Map.of("playerRespawn", scopedRegistration));
    }

    public boolean hasListeners() {
        checkThread();
        return respawn.hasListeners();
    }

    public EventResult post(EventHandler handler, ScriptType phase, KubeEvent event) {
        checkOpen();
        if (handler != supportedHandler() || handler.proofHasResult() || handler.target != null) {
            throw unsupported("post " + handler.group.name + "." + handler.name);
        }
        if (phase != ScriptType.SERVER || handler.proofPhase() != ScriptType.SERVER) {
            throw unsupported("post respawn in " + phase);
        }
        if (!handler.eventType.get().isInstance(event)) {
            throw new IllegalArgumentException("NEKO-NEKU-PROOF-EVENT: wrong respawn wrapper type");
        }
        if (!respawn.postChecked(event)) {
            throw new IllegalStateException("NEKO-NEKU-PROOF-CALLBACK: native listener dispatch failed");
        }
        return EventResult.PASS;
    }

    private EventHandler supportedHandler() {
        EventGroup group = groups.get("CommonAddedEvents");
        if (group == null || !group.getHandlers().containsKey("playerRespawn")) {
            throw new IllegalStateException("NEKO-NEKU-PROOF-DEFINITION: respawn was not registered");
        }
        return group.getHandlers().get("playerRespawn");
    }

    private static UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException("NEKO-NEKU-PROOF-SCOPE: unsupported " + operation);
    }

    private void checkThread() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("NEKO-NEKU-PROOF-THREAD: proof scope used from another thread");
        }
    }

    private void checkOpen() {
        checkThread();
        if (closed) {
            throw new IllegalStateException("NEKO-NEKU-PROOF-CLOSED: proof scope is closed");
        }
    }

    @Override
    public void close() {
        checkThread();
        if (!closed) {
            nativeGroup.clearListeners(com.tkisor.nekojs.api.ScriptType.SERVER);
            closed = true;
        }
    }
}
