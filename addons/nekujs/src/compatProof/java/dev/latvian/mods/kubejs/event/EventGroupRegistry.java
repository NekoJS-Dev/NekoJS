package dev.latvian.mods.kubejs.event;

/** Exact interface invocation boundary used by the supplied Additions plugin. */
@FunctionalInterface
public interface EventGroupRegistry {
    void register(EventGroup group);
}
