package com.tkisor.nekojs.api.data;

import lombok.Getter;

public class Binding {
    @Getter
    private final String name;
    @Getter
    private final Object object;

    private Binding(String name, Object object) {
        this.name = name;
        this.object = object;
    }

    public static Binding of(String name, Object object) {
        return new Binding(name, object);
    }

}
