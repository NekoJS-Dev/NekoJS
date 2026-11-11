package dev.latvian.mods.kubejs.event;

@FunctionalInterface
public interface EventGroupRegistry {
    void register(EventGroup group);
}
