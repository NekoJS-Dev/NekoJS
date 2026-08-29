package com.tkisor.nekojs.core.api;

import java.util.List;
import java.util.Objects;

@FunctionalInterface
public interface ApiInvoker {
    Object invoke(Object receiver, List<Object> args) throws Exception;

    static ApiInvoker of(ApiCallAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        return (receiver, args) -> adapter.invoke(receiver, args);
    }

    @FunctionalInterface
    interface ApiCallAdapter {
        Object invoke(Object receiver, List<Object> args) throws Exception;
    }
}
