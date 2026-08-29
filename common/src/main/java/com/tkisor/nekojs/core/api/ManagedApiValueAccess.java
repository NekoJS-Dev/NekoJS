package com.tkisor.nekojs.core.api;

public final class ManagedApiValueAccess {
    private ManagedApiValueAccess() {
    }

    public static boolean is(Object proxy, Class<?> valueType) {
        return proxy instanceof ApiFacadeProxy facade && valueType.isInstance(facade.implementation());
    }

    public static <T> T unwrap(Object proxy, Class<T> valueType) {
        if (!(proxy instanceof ApiFacadeProxy facade)) {
            return null;
        }
        return valueType.isInstance(facade.implementation())
                ? valueType.cast(facade.implementation())
                : null;
    }
}
