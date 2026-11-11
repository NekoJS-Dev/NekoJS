package dev.latvian.mods.rhino;

public interface Wrapper {
    Object unwrap();
    static Object unwrapped(Object value) {
        while (value instanceof Wrapper wrapper) value = wrapper.unwrap();
        return value;
    }
}
