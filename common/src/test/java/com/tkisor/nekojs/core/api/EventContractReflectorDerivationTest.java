package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Derives frozen events into the managed contract. {@link EventContractReflector} reflects the
 * event name, payload members, side (tier), dispatch mode and cancellability from each runtime
 * {@link EventGroup}. In production the result is consumed through
 * {@code NekoPluginRuntime.installManagedCallbackSchemas} →
 * {@code ManagedCallbackSchemaRegistry.installContractEvents}.
 *
 * <p>The test pins these rules:
 * <ul>
 *   <li>each bus derives exactly one {@link NormativeApiContract.ContractEvent}, with no
 *       duplicates; server-only, client-only and mixed-side events stay separate entries;</li>
 *   <li>the tier comes from the bus {@code scriptType} (STARTUP/SERVER/CLIENT);</li>
 *   <li>dispatch is PLAIN or BY_ID; a BY_ID event always has dispatchKeyType {@code "string"}
 *       because a script registers with a string id;</li>
 *   <li>cancellable has three states and follows the bus; it is false when no external predicate
 *       is registered;</li>
 *   <li>payload members are the public no-arg getters of the event class (getXxx/isXxx);
 *       {@code neko$} mixin aliases and bridge methods never enter the payload.</li>
 * </ul>
 */
class EventContractReflectorDerivationTest {

    /** Payload sample for reflection: getXxx and isXxx enter the payload, neko$ aliases and
     *  methods with parameters do not. */
    public static final class SamplePayload {
        public String getMessage() {
            return "m";
        }

        public boolean isCancelled() {
            return false;
        }

        public String neko$getAlias() {
            return "alias";
        }

        public String withArg(String arg) {
            return arg;
        }

        public void doThing() {}
    }

    /** Payload with no public no-arg getters: its payload list is empty. */
    public static final class EmptyPayload {
        public void run() {}
    }

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void eachBusDerivesExactlyOneContractEventWithTierDispatchCancelAndPayload() {
        EventGroup group = EventGroup.of("ServerEvents");
        group.server("plain", SamplePayload.class);
        group.server("dispatched", EmptyPayload.class, DispatchKey.string());
        group.add("cancellable", ScriptType.SERVER, EventBusJS.of(SamplePayload.class, true));

        List<NormativeApiContract.ContractEvent> events =
                EventContractReflector.extractEvents(List.of(group));

        assertEquals(3, events.size(), "one ContractEvent per bus, no duplicates");
        Map<String, NormativeApiContract.ContractEvent> byName = events.stream()
                .collect(Collectors.toMap(NormativeApiContract.ContractEvent::name, e -> e));

        for (NormativeApiContract.ContractEvent event : events) {
            assertEquals("ServerEvents", event.group(), "group name is carried");
            assertEquals(NormativeApiContract.EventTier.SERVER, event.tier(),
                    "tier maps the bus's canonical script type");
        }

        NormativeApiContract.ContractEvent plain = byName.get("plain");
        assertEquals(NormativeApiContract.Dispatch.PLAIN, plain.dispatch());
        assertNull(plain.dispatchKeyType(), "PLAIN events must not carry a dispatchKeyType");
        assertEquals(Boolean.FALSE, plain.cancellable(), "no external predicate -> not cancellable");
        Set<String> payloadNames = plain.payload().stream()
                .map(NormativeApiContract.ContractEventField::name)
                .collect(Collectors.toSet());
        assertEquals(Set.of("message", "cancelled"), payloadNames,
                "payload = public zero-arg getters; neko$ aliases and parameterized methods excluded");
        assertTrue(plain.payload().stream().allMatch(
                        f -> f.kind() == NormativeApiContract.FieldKind.NATIVE),
                "reflected fields are declared NATIVE (no portability promise)");

        NormativeApiContract.ContractEvent dispatched = byName.get("dispatched");
        assertEquals(NormativeApiContract.Dispatch.BY_ID, dispatched.dispatch());
        assertEquals("string", dispatched.dispatchKeyType(),
                "script-side dispatch keys are string ids");
        assertTrue(dispatched.payload().isEmpty(),
                "a payload class without public zero-arg getters reflects no fields");

        assertEquals(Boolean.TRUE, byName.get("cancellable").cancellable());
    }

    @Test
    void serverClientAndMixedSidesKeepDistinctEntries() {
        EventGroup serverOnly = EventGroup.of("ServerEvents");
        serverOnly.server("started", EmptyPayload.class);
        EventGroup clientOnly = EventGroup.of("ClientEvents");
        clientOnly.client("render", EmptyPayload.class);
        EventGroup mixed = EventGroup.of("MixedEvents");
        mixed.startup("early", EmptyPayload.class);
        mixed.server("late", EmptyPayload.class);

        List<NormativeApiContract.ContractEvent> events = EventContractReflector.extractEvents(
                List.of(serverOnly, clientOnly, mixed));

        assertEquals(4, events.size(), "each bus one entry: 1 server + 1 client + 2 mixed");
        Map<String, NormativeApiContract.EventTier> tierByQualifiedName = events.stream()
                .collect(Collectors.toMap(e -> e.group() + "." + e.name(),
                        NormativeApiContract.ContractEvent::tier));
        assertEquals(NormativeApiContract.EventTier.SERVER, tierByQualifiedName.get("ServerEvents.started"));
        assertEquals(NormativeApiContract.EventTier.CLIENT, tierByQualifiedName.get("ClientEvents.render"));
        assertEquals(NormativeApiContract.EventTier.STARTUP, tierByQualifiedName.get("MixedEvents.early"));
        assertEquals(NormativeApiContract.EventTier.SERVER, tierByQualifiedName.get("MixedEvents.late"),
                "mixed side keeps a distinct entry per bus instead of merging sides");

        // Side filtering matches the catalog: each entry keeps its own declared side, and the
        // tolerant test(side) never merges entries.
        Set<String> serverSideQualified = events.stream()
                .filter(e -> e.tier() == NormativeApiContract.EventTier.SERVER)
                .map(e -> e.group() + "." + e.name())
                .collect(Collectors.toSet());
        assertEquals(Set.of("ServerEvents.started", "MixedEvents.late"), serverSideQualified);
    }

    @Test
    void nullAndEmptyInputsDeriveEmptyEventLists() {
        assertEquals(List.of(), EventContractReflector.extractEvents(null));
        assertEquals(List.of(), EventContractReflector.extractEvents(List.of()));
    }

    /**
     * Contract event entries reach the callback schema and are consumed by
     * {@code EventCallbackSourceValidator}. {@link NormativeApiContract.ContractEvent} rejects a
     * BY_ID event without a keyType and a PLAIN event with a keyType in its constructor, so the
     * failure is diagnosable at contract construction instead of at runtime dispatch.
     */
    @Test
    void contractEventShapeViolationsFailAtConstruction() {
        assertFails(() -> new NormativeApiContract.ContractEvent(
                "G", "n", NormativeApiContract.EventTier.SERVER,
                NormativeApiContract.Dispatch.BY_ID, null, null, null, null),
                "BY_ID event requires dispatchKeyType");
        assertFails(() -> new NormativeApiContract.ContractEvent(
                "G", "n", NormativeApiContract.EventTier.SERVER,
                NormativeApiContract.Dispatch.PLAIN, "string", null, null, null),
                "PLAIN event must not have dispatchKeyType");
    }

    private static void assertFails(Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
            assertFalse(expected.getMessage().isEmpty());
            return;
        }
        throw new AssertionError("expected IllegalArgumentException: " + message);
    }
}
