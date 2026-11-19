package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptEventDefinitionClearListenersTest {

    @BeforeEach
    void bindPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void clearListenersSweepsAllScriptTypeBuckets() {
        var bus = EventBusJS.of(String.class);
        var definition = new ScriptEventDefinition("grp", "evt", ScriptType.SERVER, "script.js", bus, () -> {});
        try (var startup = Context.create("js"); var server = Context.create("js")) {
            ScriptContextRegistry.bind(startup, ScriptType.STARTUP);
            ScriptContextRegistry.bind(server, ScriptType.SERVER);
            try {
                startup.eval("js", "globalThis.calls = 0");
                server.eval("js", "globalThis.calls = 0");
                registerListener(bus, startup, "script.js", "() => calls++");
                registerListener(bus, server, "script.js", "() => calls++");

                bus.post("before");
                assertEquals(1, startup.eval("js", "calls").asInt());
                assertEquals(1, server.eval("js", "calls").asInt());

                definition.clearListeners(ScriptType.SERVER);

                assertFalse(bus.hasListeners(), "both registering ScriptType buckets must be cleared");
                bus.post("after");
                assertEquals(1, startup.eval("js", "calls").asInt());
                assertEquals(1, server.eval("js", "calls").asInt());
            } finally {
                ScriptContextRegistry.unbind(startup);
                ScriptContextRegistry.unbind(server);
            }
        }
    }

    @Test
    void clearListenersByScriptIdClearsOnlyThatScript() {
        var bus = EventBusJS.of(String.class);
        var definition = new ScriptEventDefinition("grp", "evt", ScriptType.SERVER, "alpha.js", bus, () -> {});
        try (var server = Context.create("js")) {
            ScriptContextRegistry.bind(server, ScriptType.SERVER);
            try {
                server.eval("js", "globalThis.seen = []");
                registerListener(bus, server, "alpha.js", "event => seen.push('alpha:' + event)");
                registerListener(bus, server, "beta.js", "event => seen.push('beta:' + event)");

                bus.post("before");
                assertEquals("alpha:before,beta:before", server.eval("js", "seen.join(',')").asString());

                definition.clearListeners(ScriptType.SERVER, "alpha.js");

                assertTrue(bus.hasListeners(), "the beta.js listener must remain registered");
                bus.post("after");
                assertEquals("alpha:before,beta:before,beta:after", server.eval("js", "seen.join(',')").asString());
            } finally {
                ScriptContextRegistry.unbind(server);
            }
        }
    }

    private static void registerListener(EventBusJS<String, ?> bus, Context context, String scriptId, String callback) {
        String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
        try {
            bus.execute(context.eval("js", callback));
        } finally {
            ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId);
        }
    }
}
