package com.tkisor.nekojs.core;

import com.tkisor.nekojs.probe.testfixture.CommunityProbeTypes;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies the host contracts accepted by the generated functional-interface input aliases. */
class FunctionalInterfaceHostAccessTest {
    @Test
    void productionHostAccessAcceptsLambdaAndExistingJavaInstance() {
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .build()) {
            context.getBindings("js").putMember("calls", new Calls());
            CommunityProbeTypes.Mapper<CommunityProbeTypes.Payload, String> hostMapper =
                    value -> "host:" + value.getName();
            context.getBindings("js").putMember("hostMapper", hostMapper);
            assertEquals("payload", context.eval("js", "calls.apply(value => value.getName())").asString());
            assertEquals("host:payload", context.eval("js", "calls.apply(hostMapper)").asString());
            assertEquals("TEXT", context.eval("js", "calls.inherited(value => value.toUpperCase())").asString());
            assertEquals("nested", context.eval("js", "calls.nested(() => () => 'nested')").asString());
            assertEquals("iterator", context.eval("js", "calls.iteratorSupplier(() => calls.hostIterator())").asString());
            assertEquals("array", context.eval("js", "calls.iterable(['array'])").asString());
            assertThrows(graal.graalvm.polyglot.PolyglotException.class,
                    () -> context.eval("js", "calls.iterable(() => calls.hostIterator())"));
        }
    }

    public static final class Calls {
        public String apply(CommunityProbeTypes.Mapper<CommunityProbeTypes.Payload, String> callback) {
            return callback.apply(new CommunityProbeTypes.Payload());
        }
        public String inherited(CommunityProbeTypes.StringMapper callback) {
            return callback.apply("text");
        }
        public String nested(CommunityProbeTypes.Supplier<CommunityProbeTypes.Supplier<String>> callback) {
            return callback.get().get();
        }
        public String iterable(Iterable<String> input) { return input.iterator().next(); }
        public java.util.Iterator<String> hostIterator() { return List.of("iterator").iterator(); }
        public String iteratorSupplier(CommunityProbeTypes.Supplier<java.util.Iterator<String>> callback) {
            return callback.get().next();
        }
    }
}
