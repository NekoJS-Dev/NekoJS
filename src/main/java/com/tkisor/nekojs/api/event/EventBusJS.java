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
import net.neoforged.bus.api.ICancellableEvent;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author ZZZank
 */
public class EventBusJS<EVENT, KEY> implements ProxyExecutable {
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

    /// [NekoCancellableEvent] for custom event, [ICancellableEvent] for redirected forge event
    public static boolean eventCancellability(Class<?> c) {
        return NekoCancellableEvent.class.isAssignableFrom(c) || ICancellableEvent.class.isAssignableFrom(c);
    }

    private final EventBus<EVENT> bus;
    private final List<EventListenerToken<EVENT>> tokens;

    public EventBusJS(EventBus<EVENT> bus) {
        this.bus = Objects.requireNonNull(bus);
        this.tokens = new ArrayList<>();
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

    public List<EventListenerToken<EVENT>> tokens() {
        return tokens;
    }

    public boolean post(EVENT event) {
        // 临时的错误捕获方案，也许后续需要继续优化
        try {
            return this.bus.post(event);
//        } catch (PolyglotException e) {
//            NekoErrorTracker.recordEventError(e);
//            return false;
        } catch (Exception e) {
            NekoJS.LOGGER.error("Error during CancellableEventBus execution", e);
            return false;
        }
    }

    public boolean post(EVENT event, KEY key) {
        if (canDispatch()) {
            // 临时的错误捕获方案，也许后续需要继续优化
            try {
                return ((DispatchEventBus<EVENT, KEY>) bus).post(event, key);
//            } catch (PolyglotException e) {
//                NekoErrorTracker.recordEventError(e);
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
        return this.tokens.add(token);
    }

    private EventListenerToken<EVENT> register(Value listener) {
        ScriptType type = NekoJSScriptManager.getTypeFromContext(listener.getContext());

        return this.bus.listen(event -> {
            try {
                if (listener.canExecute()) {
                    listener.executeVoid(event);
                }
            } catch (PolyglotException e) {
                NekoErrorTracker.recordEventError(type, e);
            }
        });
    }

    private EventListenerToken<EVENT> registerCancellable(Value listener) {
        ScriptType type = NekoJSScriptManager.getTypeFromContext(listener.getContext());
        var bus = (CancellableEventBus<EVENT>) this.bus;

        return bus.listen(event -> {
            try {
                if (listener.canExecute()) {
                    Value result = listener.execute(event);
                    return result.isBoolean() && result.asBoolean();
                }
            } catch (PolyglotException e) {
                NekoErrorTracker.recordEventError(type, e);
            }
            return false; // 出错时默认不取消事件
        });
    }

    private EventListenerToken<EVENT> registerDispatch(Value listener, Value key) {
        ScriptType type = NekoJSScriptManager.getTypeFromContext(listener.getContext());
        var bus = (DispatchEventBus<EVENT, KEY>) this.bus;

        return bus.listen(
                key.as(bus.dispatchKey().keyType()),
                event -> {
                    try {
                        if (listener.canExecute()) {
                            listener.executeVoid(event);
                        }
                    } catch (PolyglotException e) {
                        NekoErrorTracker.recordEventError(type, e);
                    }
                }
        );
    }

    private EventListenerToken<EVENT> registerDispatchCancellable(Value listener, Value key) {
        ScriptType type = NekoJSScriptManager.getTypeFromContext(listener.getContext());
        var bus = (DispatchCancellableEventBus<EVENT, KEY>) this.bus;

        return bus.listen(
                key.as(bus.dispatchKey().keyType()),
                event -> {
                    try {
                        if (listener.canExecute()) {
                            Value result = listener.execute(event);
                            return result.isBoolean() && result.asBoolean();
                        }
                    } catch (PolyglotException e) {
                        NekoErrorTracker.recordEventError(type, e);
                    }
                    return false; // 出错时默认不取消事件
                }
        );
    }
}