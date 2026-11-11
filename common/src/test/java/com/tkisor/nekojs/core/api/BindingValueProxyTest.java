package com.tkisor.nekojs.core.api;

import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BindingValueProxyTest {
    public interface PublicView {
        String greet(String name);
        int getCount();
        void setCount(int count);
        PublicView child();
    }
    public static final class Implementation implements PublicView {
        private int count = 1;
        public String greet(String name) { return "hello " + name; }
        public String greet(int internal) { return "hidden overload"; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public PublicView child() { return this; }
        public String secret() { return "hidden"; }
    }

    @Test void publicTypeControlsMembersPropertiesAndReturnedInterfaces() {
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.enter();
            try {
                var value = new Implementation();
                context.getBindings("js").putMember("api", new BindingValueProxy(context.asValue(value), PublicView.class, false));
                assertEquals("hello world", context.eval("js", "api.greet('world')").asString());
                assertEquals(1, context.eval("js", "api.count").asInt());
                context.eval("js", "api.count = 7");
                assertEquals(7, value.getCount());
                assertEquals("undefined", context.eval("js", "typeof api.secret").asString());
                assertEquals("undefined", context.eval("js", "typeof api.child().secret").asString());
                assertFalse(context.eval("js", "Object.keys(api).includes('getClass')").asBoolean());
                assertThrows(RuntimeException.class, () -> context.eval("js", "api.secret()"));
            } finally { context.leave(); }
        }
    }
}
