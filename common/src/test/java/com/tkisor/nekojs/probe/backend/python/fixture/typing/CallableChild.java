package com.tkisor.nekojs.probe.backend.python.fixture.typing;

public class CallableChild implements Callable {
    public Object call() { return "value"; }

    public Object apply(java.util.function.Function<Object, Object> callback) {
        return callback.apply("value");
    }
}
