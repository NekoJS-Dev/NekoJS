package com.tkisor.nekojs.api;

@FunctionalInterface
public interface EventGroupRegistry {
    void register(EventGroup group);
}