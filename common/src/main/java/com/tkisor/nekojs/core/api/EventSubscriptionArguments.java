package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.eventbus.CommonPriority;
import graal.graalvm.polyglot.Value;

import java.util.Locale;

/** Selects the same priority, dispatch key and callback for source buses and module views. */
public record EventSubscriptionArguments(byte priority, Value listener, Value key, int listenerIndex) {
    public static EventSubscriptionArguments parse(Value[] args, boolean dispatchable) {
        if (args.length == 0) {
            throw new IllegalArgumentException("EventBus requires at least one arg");
        }
        if (dispatchable && args.length == 1 && parsePriority(args[0]) != null) {
            throw new IllegalArgumentException("EventBus requires a listener after priority");
        }
        int offset = priorityArgOffset(args, dispatchable);
        if (args.length <= offset) {
            throw new IllegalArgumentException("EventBus requires a listener after priority");
        }
        boolean keyed = dispatchable && args.length - offset > 1;
        int listenerIndex = offset + (keyed ? 1 : 0);
        Value listener = args[listenerIndex];
        if (listener == null || !listener.canExecute()) {
            throw new IllegalArgumentException("[NEKO-4057] Event subscription requires a callable callback");
        }
        byte priority = offset == 0 ? CommonPriority.NORMAL : parsePriority(args[0]);
        return new EventSubscriptionArguments(priority, listener, keyed ? args[offset] : null, listenerIndex);
    }

    /** A priority-named dispatch key stays a key unless key and listener arguments both follow it. */
    public static int priorityArgOffset(Value[] args, boolean dispatchable) {
        if (args.length == 0 || parsePriority(args[0]) == null) return 0;
        return !dispatchable || args.length >= 3 ? 1 : 0;
    }

    private static Byte parsePriority(Value value) {
        if (value == null || !value.isString()) return null;
        return switch (value.asString().trim().toUpperCase(Locale.ROOT)) {
            case "HIGHEST" -> CommonPriority.HIGHEST;
            case "HIGH" -> CommonPriority.HIGH;
            case "NORMAL" -> CommonPriority.NORMAL;
            case "LOW" -> CommonPriority.LOW;
            case "LOWEST" -> CommonPriority.LOWEST;
            default -> null;
        };
    }
}
