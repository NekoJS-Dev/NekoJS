package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.core.api.ModulePublicTypes;
import org.jetbrains.annotations.ApiStatus;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Internal state for one event publication; plugin authors configure EventPublication instead. */
@ApiStatus.Internal
public final class EventExposure {
    public record View(Class<?> type, Function<Object, ?> converter) {}
    public record Selector(Class<?> type, Function<Object, ?> converter, boolean required) {
        public Selector(Class<?> type, Function<Object, ?> converter) {
            this(type, converter, false);
        }
    }

    private final String event;
    private final BindingExposure.Category category;
    private View view;
    private Selector selector;
    private Predicate<Object> filter;
    private Set<ScriptType> scriptTypes = Set.copyOf(ScriptType.all());
    private boolean scopeSelected;
    private boolean cancellable;
    private boolean notificationOnly;
    private boolean frozen;

    EventExposure(String event, BindingExposure.Category category) {
        this.event = event;
        this.category = category;
    }

    void scriptTypes(ScriptType... types) {
        checkMutable();
        if (scopeSelected || types.length == 0) throw invalid("Event script types must be selected once and be nonempty");
        Set<ScriptType> selected = new LinkedHashSet<>();
        for (ScriptType type : types) {
            if (type == null || !selected.add(type)) throw invalid("Event script types contain null or duplicates");
        }
        scriptTypes = Set.copyOf(selected);
        scopeSelected = true;
    }

    <E, V> void view(Class<V> type, Function<? super E, ? extends V> converter) {
        checkMutable();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(converter, "converter");
        if (view != null) throw invalid("Duplicate event view: " + event);
        @SuppressWarnings("unchecked") Function<Object, ?> erased = value -> converter.apply((E) value);
        view = new View(type, erased);
    }

    void cancellable() {
        checkMutable();
        if (cancellable || notificationOnly) throw invalid("Duplicate event cancellation control: " + event);
        cancellable = true;
    }

    void notificationOnly() {
        checkMutable();
        if (category != BindingExposure.Category.VERSION) throw invalid("Notification-only override requires a versioned event: " + event);
        if (cancellable || notificationOnly) throw invalid("Duplicate event cancellation control: " + event);
        notificationOnly = true;
    }

    <S> void selector(Class<S> type, Function<? super S, ?> converter, boolean required) {
        checkMutable();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(converter, "converter");
        if (selector != null) throw invalid("Duplicate event selector: " + event);
        @SuppressWarnings("unchecked") Function<Object, ?> erased = value -> converter.apply((S) value);
        selector = new Selector(type, erased, required);
    }

    <E> void when(Predicate<? super E> filter) {
        checkMutable();
        Objects.requireNonNull(filter, "filter");
        if (this.filter != null) throw invalid("Duplicate event delivery filter: " + event);
        @SuppressWarnings("unchecked") Predicate<Object> erased = value -> filter.test((E) value);
        this.filter = erased;
    }

    Declaration declaration() {
        checkMutable();
        frozen = true;
        return new Declaration(category, event, view, selector, scriptTypes, scopeSelected, cancellable, notificationOnly, filter);
    }

    /** Immutable owner-independent declaration captured when the event definition callback returns. */
    record Declaration(BindingExposure.Category category, String event, View view, Selector selector,
                       Set<ScriptType> scriptTypes, boolean scopeSelected, boolean cancellable,
                       boolean notificationOnly, Predicate<Object> filter) {
        Declaration { scriptTypes = Set.copyOf(scriptTypes); }

        ModuleEventGroup bind(String owner, EventGroup group) {
            var holder = group.getBusHolder(event);
            if (holder == null) throw invalid("Unknown event: " + event);
            var bus = ScriptType.all().stream().map(holder::getBus).filter(Objects::nonNull).findFirst().orElseThrow();
            if (view != null && bus.canCancel() && !cancellable && !notificationOnly) {
                throw invalid("Cancellable event projections require a control mapping: " + event);
            }
            if (selector != null && !(bus.bus() instanceof DispatchEventBus<?, ?>)) {
                throw invalid("Event does not support dispatch selectors: " + event);
            }
            if (category == BindingExposure.Category.STABLE) {
                Class<?> payload = view == null ? bus.eventType() : view.type();
                if (payload != null) ModulePublicTypes.validateStable(payload, false);
                if (bus.bus() instanceof DispatchEventBus<?, ?> dispatch) {
                    Class<?> keyType = selector == null ? dispatch.dispatchKey().keyType() : selector.type();
                    ModulePublicTypes.validateStable(keyType, false);
                }
            }
            boolean unavailable = scopeSelected
                    ? !scriptTypes.stream().allMatch(holder::canApplyOn)
                    : scriptTypes.stream().noneMatch(holder::canApplyOn);
            if (unavailable) {
                throw invalid("Event is unavailable in the selected script types: " + event);
            }
            return new ModuleEventGroup(owner, category, group, Set.of(event),
                    view == null ? Map.of() : Map.of(event, view),
                    selector == null ? Map.of() : Map.of(event, selector),
                    scriptTypes, Map.of(), filter == null ? Map.of() : Map.of(event, filter),
                    notificationOnly ? Set.of(event) : Set.of());
        }
    }

    private void checkMutable() { if (frozen) throw invalid("Event exposure is frozen"); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException("[NEKO-4057] " + message); }
}
