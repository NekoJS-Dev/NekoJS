package com.tkisor.nekojs.probe.testfixture.samebase;

public class PersistentData<T> {
    private final T value;

    public PersistentData(T value) { this.value = value; }

    public T baseValue() { return value; }
}
