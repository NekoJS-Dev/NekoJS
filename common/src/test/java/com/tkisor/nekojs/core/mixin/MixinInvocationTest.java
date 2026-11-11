package com.tkisor.nekojs.core.mixin;

import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MixinInvocationTest {
    private static MixinPlan.Hook hook(String descriptor, String at, boolean cancellable) {
        return new MixinPlan.Hook("test:hook", "demo.Target", "sample", descriptor, at, cancellable, "SERVER", "SERVER_MAIN", 1, false);
    }
    @Test void controlIsAtomicTypedAndExpiresEvenWhenRetained() {
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            var event = new MixinInvocation(hook("()F", "RETURN", true), getClass().getClassLoader(), new Object(), new Object[]{1}, 2F);
            context.getBindings("js").putMember("event", event);
            context.eval("js", "event.returnValue = 40; try { event.returnValue = NaN; } catch (_) {}");
            event.accepted();
            assertFalse(event.finish().changed(), "A caught invalid operation invalidates earlier valid writes");
            event.expire();
            assertThrows(RuntimeException.class, () -> context.eval("js", "event.self"));
            var valid = new MixinInvocation(hook("()I", "HEAD", true), getClass().getClassLoader(), null, new Object[0], null);
            valid.putMember("returnValue", context.asValue(12)); valid.accepted();
            assertEquals(new MixinNativeBridge.Decision(true, 12), valid.finish()); valid.expire();
            assertEquals('x', MixinInvocation.convert(context.asValue("x"), "C", getClass().getClassLoader()));
            for (Object number : new Object[]{128, -129, 0.5, Double.POSITIVE_INFINITY}) {
                assertThrows(IllegalArgumentException.class, () -> MixinInvocation.convert(context.asValue(number), "B", getClass().getClassLoader()));
            }
        }
    }
    @Test void cancelIsOnlyForVoidHeadAndObservationCannotMutate() {
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            var event = new MixinInvocation(hook("()V", "HEAD", true), getClass().getClassLoader(), null, new Object[0], null);
            context.getBindings("js").putMember("event", event); context.eval("js", "event.cancel()"); event.accepted();
            assertTrue(event.finish().changed()); event.expire();
            var observe = new MixinInvocation(hook("()I", "RETURN", false), getClass().getClassLoader(), null, new Object[]{1}, 7);
            context.getBindings("js").putMember("event", observe);
            assertThrows(RuntimeException.class, () -> context.eval("js", "event.returnValue = 9"));
            assertThrows(RuntimeException.class, () -> context.eval("js", "event.cancel()"));
            observe.accepted(); assertFalse(observe.finish().changed()); observe.expire();
        }
    }
    @Test void descriptorsAndLeaseOwnershipFailClosed() {
        for (String descriptor : new String[]{"()J", "(J)V", "([J)V", "()Vextra", "(V)V", "(I"})
            assertThrows(IllegalArgumentException.class, () -> MixinPlan.Descriptor.parse(descriptor));
        var sink = new MixinNativeBridge.Sink() {
            public boolean enabled(String plan, String id) { return true; }
            public MixinNativeBridge.Decision invoke(String plan, String id, Object self, Object[] args, Object original) { throw new IllegalStateException("fixture"); }
        };
        assertFalse(MixinNativeBridge.enabled("none", "none"));
        var old = MixinNativeBridge.attach(sink);
        assertThrows(IllegalStateException.class, () -> MixinNativeBridge.attach(sink));
        assertFalse(MixinNativeBridge.invoke("p", "id", null, null, null).changed()); old.close();
        try (var current = MixinNativeBridge.attach(sink)) {
            old.close(); assertTrue(MixinNativeBridge.enabled("p", "id"));
        }
        assertFalse(MixinNativeBridge.enabled("p", "id"));
    }
}
