//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinition;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;
import com.tkisor.nekojs.core.dynamic.plan.DynamicItemBuilder;
import com.tkisor.nekojs.core.dynamic.txn.DynamicRegistryClientParticipant;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncMessage;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncWireCodec;
import com.tkisor.nekojs.core.fs.ClassFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NeoForgeDynamicRegistryAdapterValidationTest {
    private SandboxConfig original;
    private final AtomicInteger registryLookups = new AtomicInteger();

    @BeforeEach
    void enableOnlyTheIsolatedTestGate() {
        original = ClassFilter.INSTANCE.config();
        ClassFilter.INSTANCE.updateConfig(new SandboxConfig(false, false, false, false, true, true,
                false, true, SandboxConfig.PACK_SYNC_OFF, false, true));
    }

    @AfterEach
    void restoreGate() { ClassFilter.INSTANCE.updateConfig(original); }

    @ParameterizedTest
    @ValueSource(strings = {"maybe", "TRUE", "1", ""})
    void malformedWireBooleanRejectsPrepareWithoutStagingOrActivation(String value) {
        var participant = participant();
        var decision = participant.onPrepare(message(DynamicSyncMessage.Kind.PREPARE, value));
        assertFalse(decision.accepted(), decision.reason());
        assertTrue(decision.reason().contains("fireResistant"), decision.reason());
        assertEquals(-1, participant.stagedGeneration());
        assertEquals(-1, participant.activatedGeneration());
        assertFalse(participant.onCommit(new DynamicSyncMessage(
                DynamicSyncMessage.Kind.COMMIT, 1, List.of(), null)).activated());
        assertEquals(0, registryLookups.get(), "Rejected input cannot reach registry surgery");
    }

    @Test
    void malformedWireBooleanRejectsStateSyncBeforeRegistrySurgery() {
        var participant = participant();
        var decision = participant.onStateSync(message(DynamicSyncMessage.Kind.STATE_SYNC, "maybe"));
        assertFalse(decision.accepted(), decision.reason());
        assertTrue(decision.reason().contains("fireResistant"), decision.reason());
        assertEquals(-1, participant.activatedGeneration());
        assertEquals(0, registryLookups.get());
    }

    @Test
    void canonicalAndOmittedReadingsStillPrepareWithoutActivating() {
        for (String value : new String[]{"true", "false", null}) {
            var participant = participant();
            var decision = participant.onPrepare(message(DynamicSyncMessage.Kind.PREPARE, value));
            assertTrue(decision.accepted(), decision.reason());
            assertEquals(1, participant.stagedGeneration());
            assertEquals(-1, participant.activatedGeneration());
        }
        assertEquals(0, registryLookups.get(), "Prepare must remain inert");
    }

    private DynamicRegistryClientParticipant participant() {
        return new DynamicRegistryClientParticipant(new NeoForgeDynamicRegistryAdapter("validation-test", () -> {
            registryLookups.incrementAndGet();
            throw new AssertionError("Registry surgery is forbidden during validation");
        }));
    }

    private static DynamicSyncMessage message(DynamicSyncMessage.Kind kind, String value) {
        DynamicDefinition original = DynamicDefinition.of(DynamicDefinitionType.ITEM,
                "neko_validation:boolean_input", new DynamicItemBuilder());
        var readings = new java.util.ArrayList<>(original.readings());
        readings.removeIf(reading -> reading.startsWith("fireResistant="));
        if (value != null) readings.add("fireResistant=" + value);
        var definition = new DynamicDefinition(original.type(), original.id(), original.mode(),
                readings, original.fingerprint());
        var message = new DynamicSyncMessage(kind, 1,
                List.of(new DynamicSyncMessage.Entry(definition, "server_scripts/validation.js")), null);
        return DynamicSyncWireCodec.decodeServerMessage(DynamicSyncWireCodec.encodeServerMessage(message));
    }
}
//?}
//?}
