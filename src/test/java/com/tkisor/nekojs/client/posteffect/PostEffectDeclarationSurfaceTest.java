//? if neoforge {
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.core.NekoJSCorePlugin;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import graal.graalvm.polyglot.proxy.ProxyObject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture for the post-effect declaration surface: it is contributed only by the existing
 * {@code ClientEvents} resource/reload sub-events, its event member is unique in the catalog and
 * golden output, and it adds no second PostEffects event.
 *
 * <p>Assertions only use the production registration path
 * ({@link NekoJSCorePlugin#registerClientEvents}) and real bus metadata; there is no parallel member
 * table.
 */
class PostEffectDeclarationSurfaceTest {

    @Test
    void declarationBusLivesInClientEventsAndIsTheOnlyPostEffectEvent() {
        var registry = new com.tkisor.nekojs.api.event.EventGroupRegistry.Impl().scopedTo("nekojs");
        new NekoJSCorePlugin().registerClientEvents(registry);

        var clientEvents = registry.view().get("ClientEvents");
        assertTrue(clientEvents != null, "NekoJSCorePlugin must register the ClientEvents group");
        assertTrue(clientEvents.viewBuses().containsKey("postEffects"),
                "the declaration face is ClientEvents.postEffects (existing resource/reload sub-events)");

        // No second PostEffects domain exists anywhere in the event set: postEffects appears once, and no
        // group is named PostEffects*.
        Set<String> postEffectBuses = new HashSet<>();
        for (var group : registry.view().values()) {
            for (var entry : group.viewBuses().entrySet()) {
                if (entry.getKey().toLowerCase(java.util.Locale.ROOT).contains("posteffect")) {
                    postEffectBuses.add(group.name() + "." + entry.getKey());
                }
            }
            assertFalse(group.name().startsWith("PostEffect"),
                    "no second PostEffects event group: " + group.name());
        }
        assertEquals(Set.of("ClientEvents.postEffects"), postEffectBuses,
                "the post-effect declaration surface must have exactly one bus, in ClientEvents");

        var bus = ClientEvents.POST_EFFECTS;
        assertSame(bus, clientEvents.getBusHolder("postEffects").getBus(ScriptType.CLIENT),
                "the catalog member is the production bus instance (no duplicate declaration)");
        assertEquals(PostEffectEventJS.class, bus.eventType(),
                "the payload type is the declaration event object");
        assertEquals("ClientEvents", bus.groupName());
        assertEquals("postEffects", bus.eventName());
    }

    @Test
    void declarationBusIsClientOnlyAndIsNotADispatchBus() {
        var bus = ClientEvents.POST_EFFECTS;
        assertEquals(ScriptType.CLIENT, bus.scriptType(),
                "the declaration surface is client_scripts only (no server-side post effects)");
        assertFalse(bus.canDispatch(),
                "the declaration bus is not key-dispatched: candidate collection has no key context");
    }

    @Test
    void declarationEventOffersRegisterAndUnregisterOnly() {
        // Java proxy hooks are public protocol methods, not script-visible declaration operations.
        var declaration = new TypeReflector().reflect(PostEffectEventJS.class);
        Set<String> names = new HashSet<>();
        for (var method : declaration.methods) {
            if (!method.hidden) names.add(method.effectiveName());
        }
        assertEquals(Set.of("register", "unregister", "getDeclaredCount"), names,
                "the script-visible declaration event must collect declarations without runtime actions");
        assertEquals(2L, declaration.methods.stream()
                        .filter(method -> !method.hidden && method.effectiveName().equals("register")).count(),
                "both callback and legacy object declaration overloads must remain available");
        assertTrue(declaration.interfaces.stream().noneMatch(slot -> slot.sourceType == ProxyObject.class),
                "hidden Java proxy hooks must not reappear through authored interface heritage");
    }

    @Test
    void runtimeBindingKeepsItsCallerVisibleMembers() {
        // The caller-visible members of the runtime binding stay available, so script code is unchanged.
        Set<String> names = new HashSet<>();
        for (var method : PostEffectsJS.class.getMethods()) {
            names.add(method.getName());
        }
        assertTrue(names.containsAll(Set.of("set", "clear", "toggle", "current", "isActive",
                        "hasDefinition", "installed", "activeGeneration", "isAvailable", "presets")),
                "runtime binding members changed: " + names);
        assertTrue(names.contains("register") == false,
                "the declaration entry point must not stay on the runtime binding");
        assertTrue(names.contains("unregister") == false,
                "the declaration entry point must not stay on the runtime binding");
    }
}
//?}
