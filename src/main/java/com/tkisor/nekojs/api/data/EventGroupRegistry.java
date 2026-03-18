package com.tkisor.nekojs.api.data;

@FunctionalInterface
public interface EventGroupRegistry {
    void register(EventGroup group);
}