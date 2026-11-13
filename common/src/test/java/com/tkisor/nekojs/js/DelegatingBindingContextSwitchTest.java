package com.tkisor.nekojs.js;

import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for reusing a composite binding across Contexts. A binding is a process-level
 * object built once at bootstrap. A transactional reload closes the old Context and switches to
 * the new candidate. A {@code Value} cached for the old Context throws
 * {@code The Context is already closed} on the first access after the switch, so the wrapper is
 * rebuilt against the currently active Context.
 */
class DelegatingBindingContextSwitchTest {

    public static class Helper {
        public String help() {
            return "ok";
        }
    }

    @Test
    void valueCacheRebindsAcrossContextSwitch() {
        DelegatingBinding binding = new DelegatingBinding(new Helper(), Helper.class, Set.of("help"));

        try (Context first = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            first.getBindings("js").putMember("b", binding);
            assertEquals("ok", first.eval("js", "b.help()").asString());
        }

        // The old Context is closed: open a new Context and access the same binding instance
        try (Context second = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            second.getBindings("js").putMember("b", binding);
            assertEquals("ok", second.eval("js", "b.help()").asString());
        }
    }
}
