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

/** Internal state for one event publication; plugin authors configure EventPublication instead. */
@ApiStatus.Internal
public final class EventExposure {
    public record View(Class<?> type, Function<Object, ?> converter) {}

    private final String event;
    private final BindingExposure.Category category;
    private View view;
    private Set<ScriptType> scriptTypes = Set.copyOf(ScriptType.all());
    private boolean scopeSelected;
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

    Declaration declaration() {
        checkMutable();
        frozen = true;
        return new Declaration(category, event, view, scriptTypes);
    }

    /** Immutable owner-independent declaration captured when the event definition callback returns. */
    record Declaration(BindingExposure.Category category, String event, View view, Set<ScriptType> scriptTypes) {
        Declaration { scriptTypes = Set.copyOf(scriptTypes); }

        ModuleEventGroup bind(String owner, EventGroup group) {
            var holder = group.getBusHolder(event);
            if (holder == null) throw invalid("Unknown event: " + event);
            var bus = ScriptType.all().stream().map(holder::getBus).filter(Objects::nonNull).findFirst().orElseThrow();
            if (view != null && bus.canCancel()) {
                throw invalid("Cancellable event projections require a control mapping: " + event);
            }
            if (category == BindingExposure.Category.STABLE) {
                Class<?> payload = view == null ? bus.eventType() : view.type();
                if (payload != null) ModulePublicTypes.validateStable(payload, false);
                if (bus.bus() instanceof DispatchEventBus<?, ?> dispatch) {
                    ModulePublicTypes.validateStable(dispatch.dispatchKey().keyType(), false);
                }
            }
            if (scriptTypes.stream().noneMatch(holder::canApplyOn)) {
                throw invalid("Event is unavailable in the selected script types: " + event);
            }
            return new ModuleEventGroup(owner, category, group, Set.of(event),
                    view == null ? Map.of() : Map.of(event, view), scriptTypes);
        }
    }

    private void checkMutable() { if (frozen) throw invalid("Event exposure is frozen"); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException("[NEKO-4057] " + message); }
}
