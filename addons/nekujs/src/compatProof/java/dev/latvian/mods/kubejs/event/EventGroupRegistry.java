package dev.latvian.mods.kubejs.event;

/** Exact interface the supplied Additions plugin calls. */
@FunctionalInterface
public interface EventGroupRegistry {
    void register(EventGroup group);
}
