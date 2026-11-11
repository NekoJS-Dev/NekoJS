package dev.latvian.mods.kubejs.event;

import java.util.Objects;

/** Target metadata retained for binary declarations, not a second dispatch engine. */
public final class EventTargetType<T> {
    public static final EventTargetType<String> STRING = create(String.class);
    public final Class<T> type;

    private EventTargetType(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> EventTargetType<T> create(Class<T> type) {
        return new EventTargetType<>(type);
    }
}
