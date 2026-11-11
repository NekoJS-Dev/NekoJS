package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.api.ModuleEventGroupProxy;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModuleEventExposureTest {
    public interface View { int getCount(); }
    public record Payload(int value) { public int secret() { return 99; } }
    public record PublicPayload(int count) implements View { public int getCount() { return count; } }
    @BeforeAll static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test void exposureRequiresAnExplicitStableAllowlistAndKnownEvents() {
        var root = new EventGroupRegistry.Impl();
        var registry = root.scopedTo("example");
        var group = EventGroup.of("MachineEvents");
        group.server("changed", Payload.class);
        assertThrows(IllegalArgumentException.class, () -> registry.register(group, e -> e.stable()));
        assertThrows(IllegalArgumentException.class, () -> registry.register(group, e -> e.stable().events("missing")));
        registry.register(group, e -> e.stable().events("changed"));
        assertTrue(root.view().isEmpty());
        assertEquals(1, root.sources().size());
        assertThrows(IllegalArgumentException.class, () -> registry.register(group, e -> e.stable().events("changed")));
        registry.register(group);
        assertEquals(1, root.sources().size());
        assertThrows(IllegalArgumentException.class, () -> registry.register(group));
    }

    @Test void exactPublicationScopeNarrowsButNeverWidensSourceVisibility() {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var group = EventGroup.of("ScopedEvents");
        group.server("tick", Payload.class);
        assertTrue(group.getBusHolder("tick").canApplyOn(ScriptType.STARTUP));
        assertThrows(IllegalArgumentException.class, () -> registry.register(group, e -> e.stable().events("tick").scriptTypes()));
        assertThrows(IllegalArgumentException.class, () -> registry.register(group, e -> e.stable().events("tick").scriptTypes(ScriptType.CLIENT)));
        assertThrows(IllegalArgumentException.class, () -> registry.register(group, e -> e.stable().events("tick").scriptTypes(ScriptType.SERVER, ScriptType.SERVER)));
        assertThrows(IllegalArgumentException.class, () -> registry.register(group, e -> e.stable().events("tick").scriptTypes(ScriptType.SERVER).scriptTypes(ScriptType.SERVER)));
        registry.register(group, e -> e.stable().events("tick").scriptTypes(ScriptType.SERVER));
        var publication = registry.moduleGroups().getFirst();
        assertEquals(java.util.Set.of(ScriptType.SERVER), publication.scriptTypes());
        assertTrue(publication.available(ScriptType.SERVER));
        assertFalse(publication.available(ScriptType.STARTUP));
        assertFalse(publication.available(ScriptType.TEST));
        assertFalse(publication.available(ScriptType.CLIENT));
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            assertFalse(new ModuleEventGroupProxy(context, publication, ScriptType.STARTUP).hasMember("tick"));
            assertTrue(new ModuleEventGroupProxy(context, publication, ScriptType.SERVER).hasMember("tick"));
        }
    }

    @Test void projectionSharesTheRawBusAndItsListenerCleanup() {
        var registry = new EventGroupRegistry.Impl().scopedTo("example");
        var group = EventGroup.of("MachineEvents");
        var bus = group.server("changed", Payload.class, api -> api.stable(
                View.class, payload -> new PublicPayload(payload.value())));
        registry.register(group);
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("stable", new ModuleEventGroupProxy(context, registry.moduleGroups().getFirst(), ScriptType.SERVER));
                context.getBindings("js").putMember("raw", bus);
                context.eval("js", "globalThis.seen = []; stable.changed(e => seen.push(e.count, typeof e.secret)); raw(e => seen.push(e.secret()));");
                bus.post(new Payload(7));
                assertEquals("[7,\"undefined\",99]", context.eval("js", "JSON.stringify(seen)").asString());
                group.clearListeners(ScriptType.SERVER);
                assertFalse(bus.hasListeners());
                bus.post(new Payload(8));
                assertEquals(3, context.eval("js", "seen.length").asInt());
            } finally { ScriptContextRegistry.unbind(context); }
        }
    }
}
