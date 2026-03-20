package com.tkisor.nekojs.api.event;

@FunctionalInterface
public interface EventGroupRegistry {
    void register(EventGroup group);
}