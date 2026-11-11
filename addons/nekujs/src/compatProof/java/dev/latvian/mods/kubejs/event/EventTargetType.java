package dev.latvian.mods.kubejs.event;

/** Records target metadata without enabling targeted legacy dispatch. */
public final class EventTargetType<T> {
    public static final EventTargetType<String> STRING = new EventTargetType<>(String.class);
    private final Class<T> type;

    private EventTargetType(Class<T> type) {
        this.type = type;
    }

    public Class<T> type() {
        return type;
    }
}
