package com.tkisor.nekojs.probe.testfixture;

/** Java contracts used by the generated lambda and authored-builder TypeScript checks. */
public final class CommunityProbeTypes {
    private CommunityProbeTypes() {}

    public interface Mapper<T, R> {
        R apply(T value);
        boolean equals(Object other);
    }

    public interface StringMapper extends Mapper<String, String> {}

    public interface Supplier<T> {
        T get();
    }

    public static final class Payload {
        public String getName() { return "payload"; }
    }

    public static final class Api {
        public void consume(Mapper<Payload, String> callback) {}
        public void produce(Supplier<Payload> callback) {}
        public void nested(Supplier<Supplier<Payload>> callback) {}
        public <T> T infer(Supplier<T> callback) { return callback.get(); }
        public void inherited(StringMapper callback) {}
        public void wildcard(Mapper<? super com.tkisor.nekojs.probe.testfixture.callback.CallbackValue, String> callback) {}
        @SuppressWarnings("rawtypes")
        public void raw(Mapper callback) {}
    }

    public static final class RpcBuilder {
        public RpcBuilder schema(Object schema) { return this; }
        public RpcBuilder returns(String type) { return this; }
        public void fn(Object callback) {}
    }
}
