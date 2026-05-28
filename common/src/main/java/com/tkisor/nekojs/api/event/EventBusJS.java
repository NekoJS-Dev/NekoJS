package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.NekoJSScriptManager;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.utils.event.CancellableEventBus;
import com.tkisor.nekojs.utils.event.EventBus;
import com.tkisor.nekojs.utils.event.EventListenerToken;
import com.tkisor.nekojs.utils.event.dispatch.DispatchCancellableEventBus;
import com.tkisor.nekojs.utils.event.dispatch.DispatchEventBus;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author ZZZank
 */
public class EventBusJS<EVENT, KEY> implements ProxyExecutable {
    private static Predicate<Class<?>> externalCancellabilityPredicate = c -> false;

    public static void setExternalCancellabilityPredicate(Predicate<Class<?>> predicate) {
        externalCancellabilityPredicate = predicate == null ? c -> false : predicate;
    }

    public static <E, K> EventBusJS<E, K> of(Class<E> eventType) {
        return of(eventType, eventCancellability(eventType));
    }

    public static <E, K> EventBusJS<E, K> of(Class<E> eventType, boolean cancellable) {
        return of(eventType, cancellable, null);
    }

    public static <E, K> EventBusJS<E, K> of(
        Class<E> eventType,
        boolean cancellable,
        @Nullable DispatchKey<E, K> dispatchKey
    ) {
        EventBus<E> bus;
        if (cancellable) {
            bus = dispatchKey != null
                ? DispatchCancellableEventBus.create(eventType, dispatchKey)
                : CancellableEventBus.create(eventType);
        } else {
            bus = dispatchKey != null
                ? DispatchEventBus.create(eventType, dispatchKey)
                : EventBus.create(eventType);
        }
        return new EventBusJS<>(bus);
    }

    public static boolean eventCancellability(Class<?> c) {
        return NekoCancellableEvent.class.isAssignableFrom(c) || externalCancellabilityPredicate.test(c);
    }

    private final EventBus<EVENT> bus;
    private final Map<ScriptType, List<ScriptEventListenerToken<EVENT>>> tokensByType;
    private ScriptType scriptType;

    public EventBusJS(EventBus<EVENT> bus) {
        this.bus = Objects.requireNonNull(bus);
        this.tokensByType = new EnumMap<>(ScriptType.class);
    }

    public boolean canCancel() {
        return bus instanceof CancellableEventBus<?>;
    }

    public boolean canDispatch() {
        return bus instanceof DispatchEventBus<?, ?>;
    }

    public EventBus<EVENT> bus() {
        return bus;
    }

    public ScriptType scriptType() {
        return scriptType;
    }

    public void scriptType(ScriptType scriptType) {
        if (this.scriptType != null && this.scriptType != scriptType) {
            throw new IllegalStateException("Event bus script type is already " + this.scriptType + ": " + bus.eventType().getName());
        }
        this.scriptType = Objects.requireNonNull(scriptType, "scriptType");
    }

    public List<EventListenerToken<EVENT>> tokens(ScriptType type) {
        return tokensByType.getOrDefault(type, List.of()).stream().map(ScriptEventListenerToken::token).toList();
    }

    public void clearTokens(ScriptType type) {
        List<ScriptEventListenerToken<EVENT>> tokens = tokensByType.remove(type);
        if (tokens == null) return;
        for (var token : tokens) {
            bus.unregister(token.token());
        }
    }

    public void clearTokens(ScriptType type, String scriptId) {
        if (scriptId == null || scriptId.isBlank()) return;
        List<ScriptEventListenerToken<EVENT>> tokens = tokensByType.get(type);
        if (tokens == null) return;
        Iterator<ScriptEventListenerToken<EVENT>> iterator = tokens.iterator();
        while (iterator.hasNext()) {
            ScriptEventListenerToken<EVENT> token = iterator.next();
            if (scriptId.equals(token.scriptId())) {
                bus.unregister(token.token());
                iterator.remove();
            }
        }
        if (tokens.isEmpty()) {
            tokensByType.remove(type);
        }
    }

    public boolean post(EVENT event) {
        try {
            return this.bus.post(event);
        } catch (Exception e) {
            NekoJS.LOGGER.error("Error during CancellableEventBus execution", e);
            return false;
        }
    }

    public boolean post(EVENT event, KEY key) {
        if (canDispatch()) {
            try {
                return ((DispatchEventBus<EVENT, KEY>) bus).post(event, key);
            } catch (Exception e) {
                NekoJS.LOGGER.error("Error during EventBus execution", e);
            }
            return false;
        }
        throw new IllegalStateException("This bus is not dispatchable");
    }

    @Override
    public Object execute(Value... args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("EventBus requires at least one arg");
        }
        EventListenerToken<EVENT> token;
        Value listener = args.length > 1 && canDispatch() ? args[1] : args[0];
        if (canDispatch()) {
            if (canCancel()) {
                token = args.length > 1
                    ? registerDispatchCancellable(args[1], args[0]) // listen("key", (e) => true)
                    : registerCancellable(args[0]); // listen((e) => true)
            } else {
                token = args.length > 1
                    ? registerDispatch(args[1], args[0]) // listen("key", (e) => {})
                    : register(args[0]); // listen((e) => {})
            }
        } else {
            if (canCancel()) {
                token = registerCancellable(args[0]); // listen((e) => true)
            } else {
                token = register(args[0]); // listen((e) => {})
            }
        }
        ScriptType type = NekoJSScriptManager.getTypeFromContext(listener.getContext());
        String scriptId = NekoJSScriptManager.getCurrentScriptId(listener.getContext());
        tokensByType.computeIfAbsent(type, ignored -> new ArrayList<>()).add(new ScriptEventListenerToken<>(token, scriptId));
        return true;
    }

    private EventListenerToken<EVENT> register(Value listener) {
        Context context = listener.getContext();
        ScriptType type = NekoJSScriptManager.getTypeFromContext(context);
        String scriptId = NekoJSScriptManager.getCurrentScriptId(context);

        return this.bus.listen(event -> {
            try {
                synchronized (context) {
                    String previousScriptId = NekoJSScriptManager.switchCurrentScriptId(context, scriptId);
                    try {
                        listener.executeVoid(event);
                    } finally {
                        NekoJSScriptManager.restoreCurrentScriptId(context, previousScriptId);
                    }
                }
            } catch (Throwable e) {
                recordListenerError(type, scriptId, "normal", null, event, e);
            }
        });
    }

    private EventListenerToken<EVENT> registerCancellable(Value listener) {
        Context context = listener.getContext();
        ScriptType type = NekoJSScriptManager.getTypeFromContext(context);
        String scriptId = NekoJSScriptManager.getCurrentScriptId(context);
        var bus = (CancellableEventBus<EVENT>) this.bus;

        return bus.listen(event -> {
            try {
                synchronized (context) {
                    String previousScriptId = NekoJSScriptManager.switchCurrentScriptId(context, scriptId);
                    try {
                        Value result = listener.execute(event);
                        return result.isBoolean() && result.asBoolean();
                    } finally {
                        NekoJSScriptManager.restoreCurrentScriptId(context, previousScriptId);
                    }
                }
            } catch (Throwable e) {
                recordListenerError(type, scriptId, "cancellable", null, event, e);
            }
            return false; // 出错时默认不取消事件
        });
    }

    private EventListenerToken<EVENT> registerDispatch(Value listener, Value key) {
        Context context = listener.getContext();
        ScriptType type = NekoJSScriptManager.getTypeFromContext(context);
        String scriptId = NekoJSScriptManager.getCurrentScriptId(context);
        var bus = (DispatchEventBus<EVENT, KEY>) this.bus;
        KEY dispatchKey = key.as(bus.dispatchKey().keyType());

        return bus.listen(
                dispatchKey,
                event -> {
                    try {
                        synchronized (context) {
                            String previousScriptId = NekoJSScriptManager.switchCurrentScriptId(context, scriptId);
                            try {
                                if (listener.canExecute()) {
                                    listener.executeVoid(event);
                                }
                            } finally {
                                NekoJSScriptManager.restoreCurrentScriptId(context, previousScriptId);
                            }
                        }
                    } catch (Throwable e) {
                        recordListenerError(type, scriptId, "dispatch", dispatchKey, event, e);
                    }
                }
        );
    }

    private EventListenerToken<EVENT> registerDispatchCancellable(Value listener, Value key) {
        Context context = listener.getContext();
        ScriptType type = NekoJSScriptManager.getTypeFromContext(context);
        String scriptId = NekoJSScriptManager.getCurrentScriptId(context);
        var bus = (DispatchCancellableEventBus<EVENT, KEY>) this.bus;
        KEY dispatchKey = key.as(bus.dispatchKey().keyType());

        return bus.listen(
                dispatchKey,
                event -> {
                    try {
                        synchronized (context) {
                            String previousScriptId = NekoJSScriptManager.switchCurrentScriptId(context, scriptId);
                            try {
                                if (listener.canExecute()) {
                                    Value result = listener.execute(event);
                                    return result.isBoolean() && result.asBoolean();
                                }
                            } finally {
                                NekoJSScriptManager.restoreCurrentScriptId(context, previousScriptId);
                            }
                        }
                    } catch (Throwable e) {
                        recordListenerError(type, scriptId, "dispatchCancellable", dispatchKey, event, e);
                    }
                    return false; // 出错时默认不取消事件
                }
        );
    }

    private void recordListenerError(ScriptType type, String scriptId, String mode, Object dispatchKey, EVENT event, Throwable throwable) {
        String eventClass = event == null ? "null" : event.getClass().getName();
        String keyText = dispatchKey == null ? "" : " key=" + dispatchKey;
        String kind = "event mode=" + mode
                + " bus=" + bus.eventType().getName()
                + " event=" + eventClass
                + " script=" + (scriptId == null || scriptId.isBlank() ? "unknown" : scriptId)
                + " thread=" + Thread.currentThread().getName()
                + keyText;
        NekoErrorTracker.recordCallbackError(type, kind, throwable);
    }

    private record ScriptEventListenerToken<EVENT>(EventListenerToken<EVENT> token, String scriptId) {}
}
