package com.tkisor.nekojs.core.dynamic.txn;

import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinition;
import com.tkisor.nekojs.core.dynamic.plan.DynamicMobEffectBuilder;
import com.tkisor.nekojs.core.dynamic.plan.DynamicSoundEventBuilder;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 21 platform wiring: the JSON wire codec must round-trip every protocol
 * message of both directions without a second model — the same string body is what
 * the play-phase payload carries (ticket 17 register-once channel family), so a
 * codec drift is a wire drift. Also pins strict decode: malformed or future-versioned
 * input is rejected with an actionable message instead of silently coerced.
 */
class DynamicSyncWireCodecTest {

    private static DynamicDefinition soundEvent(String id, Float fixedRange) {
        DynamicSoundEventBuilder builder = new DynamicSoundEventBuilder();
        builder.setFixedRange(fixedRange);
        return DynamicDefinition.of(DynamicDefinitionType.SOUND_EVENT, id, builder);
    }

    private static DynamicDefinition mobEffect(String id, String category, int color) {
        DynamicMobEffectBuilder builder = new DynamicMobEffectBuilder();
        builder.setCategory(category);
        builder.setColor(color);
        return DynamicDefinition.of(DynamicDefinitionType.MOB_EFFECT, id, builder);
    }

    private static DynamicDefinition item(String id, int maxStackSize, String rarity) {
        com.tkisor.nekojs.core.dynamic.plan.DynamicItemBuilder builder =
                new com.tkisor.nekojs.core.dynamic.plan.DynamicItemBuilder();
        builder.setMaxStackSize(maxStackSize);
        builder.setRarity(rarity);
        return DynamicDefinition.of(DynamicDefinitionType.ITEM, id, builder);
    }

    // ---- server → client round-trips ----

    @Test
    void prepareMessageRoundTripsWithFullStateEntries() {
        DynamicSyncMessage message = DynamicSyncMessage.prepare(12L, List.of(
                new DynamicSyncMessage.Entry(item("mymod:ruby", 16, "epic"), "server_scripts/main.js"),
                new DynamicSyncMessage.Entry(soundEvent("mymod:boom", 16.0f), "server_scripts/main.js"),
                new DynamicSyncMessage.Entry(mobEffect("mymod:wither_touch", "harmful", 0x8B0000),
                        "server_scripts/other.js")));
        DynamicSyncMessage decoded = DynamicSyncWireCodec.decodeServerMessage(
                DynamicSyncWireCodec.encodeServerMessage(message));
        assertEquals(message, decoded, "the full-state PREPARE must survive the wire unchanged");
        assertEquals(3, decoded.entries().size());
        assertEquals(DynamicDefinitionType.MOB_EFFECT, decoded.entries().get(2).definition().type());
        assertEquals(item("mymod:ruby", 16, "epic").readings(), decoded.entries().get(0).definition().readings(),
                "normalized property readings travel with the definition");
    }

    @Test
    void stateSyncCommitAndAbortRoundTrip() {
        DynamicSyncMessage stateSync = DynamicSyncMessage.stateSync(9L,
                List.of(new DynamicSyncMessage.Entry(soundEvent("mymod:boom", null), "server_scripts/main.js")));
        assertEquals(stateSync, DynamicSyncWireCodec.decodeServerMessage(
                DynamicSyncWireCodec.encodeServerMessage(stateSync)));

        DynamicSyncMessage commit = DynamicSyncMessage.commit(9L);
        assertEquals(commit, DynamicSyncWireCodec.decodeServerMessage(
                DynamicSyncWireCodec.encodeServerMessage(commit)));

        DynamicSyncMessage abort = DynamicSyncMessage.abort(9L, "participant-rejected:p2");
        DynamicSyncMessage decodedAbort = DynamicSyncWireCodec.decodeServerMessage(
                DynamicSyncWireCodec.encodeServerMessage(abort));
        assertEquals(abort.kind(), decodedAbort.kind());
        assertEquals(abort.generation(), decodedAbort.generation());
        assertEquals("participant-rejected:p2", decodedAbort.reason());
        assertTrue(decodedAbort.entries().isEmpty());
    }

    // ---- client → server round-trips ----

    @Test
    void ackAndActivationReportRepliesRoundTrip() {
        DynamicSyncReply accepted = DynamicSyncReply.ack(12L, true, null);
        assertEquals(accepted, DynamicSyncWireCodec.decodeReply(DynamicSyncWireCodec.encodeReply(accepted)));

        DynamicSyncReply rejected = DynamicSyncReply.ack(12L, false, "conflict:minecraft:item|mymod:ruby");
        DynamicSyncReply decodedRejected = DynamicSyncWireCodec.decodeReply(
                DynamicSyncWireCodec.encodeReply(rejected));
        assertEquals(DynamicSyncReply.Kind.ACK, decodedRejected.kind());
        assertEquals(12L, decodedRejected.generation());
        assertEquals(Boolean.FALSE, decodedRejected.accepted());
        assertEquals("conflict:minecraft:item|mymod:ruby", decodedRejected.reason());
        assertNull(decodedRejected.activated());

        DynamicSyncReply failure = DynamicSyncReply.activationReport(12L, false, "activation-failed:tags not bound");
        DynamicSyncReply decodedFailure = DynamicSyncWireCodec.decodeReply(
                DynamicSyncWireCodec.encodeReply(failure));
        assertEquals(DynamicSyncReply.Kind.ACTIVATION_REPORT, decodedFailure.kind());
        assertEquals(Boolean.FALSE, decodedFailure.activated());
        assertEquals("activation-failed:tags not bound", decodedFailure.detail());
        assertNull(decodedFailure.accepted());
    }

    // ---- strict decode (defense line: malformed input never silently coerces) ----

    @Test
    void futureSchemaVersionIsRejected() {
        String v1 = DynamicSyncWireCodec.encodeServerMessage(DynamicSyncMessage.commit(1L));
        String v2 = v1.replace("\"v\":1", "\"v\":2");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DynamicSyncWireCodec.decodeServerMessage(v2));
        assertTrue(e.getMessage().contains("schema version"), e.getMessage());
    }

    @Test
    void unknownKindAndTypeAndModeAreRejected() {
        String unknownKind = "{\"v\":1,\"kind\":\"ROLLBACK\",\"generation\":1,\"entries\":[]}";
        assertThrows(IllegalArgumentException.class,
                () -> DynamicSyncWireCodec.decodeServerMessage(unknownKind));

        String unknownType = "{\"v\":1,\"kind\":\"PREPARE\",\"generation\":1,\"entries\":["
                + "{\"definition\":{\"type\":\"ENTITY_TYPE\",\"id\":\"mymod:golem\",\"mode\":\"WORLD\","
                + "\"readings\":[],\"fingerprint\":\"fp\"},\"owner\":\"s.js\"}]}";
        IllegalArgumentException typeError = assertThrows(IllegalArgumentException.class,
                () -> DynamicSyncWireCodec.decodeServerMessage(unknownType));
        assertTrue(typeError.getMessage().contains("type"), typeError.getMessage());

        String unknownMode = "{\"v\":1,\"kind\":\"PREPARE\",\"generation\":1,\"entries\":["
                + "{\"definition\":{\"type\":\"ITEM\",\"id\":\"mymod:ruby\",\"mode\":\"GLOBAL\","
                + "\"readings\":[],\"fingerprint\":\"fp\"},\"owner\":\"s.js\"}]}";
        assertThrows(IllegalArgumentException.class,
                () -> DynamicSyncWireCodec.decodeServerMessage(unknownMode));

        String unknownReply = "{\"v\":1,\"kind\":\"NEKO\",\"generation\":1}";
        assertThrows(IllegalArgumentException.class, () -> DynamicSyncWireCodec.decodeReply(unknownReply));
    }

    @Test
    void commitCarryingEntriesAndTruncatedBodiesAreRejected() {
        String commitWithEntries = DynamicSyncWireCodec.encodeServerMessage(
                DynamicSyncMessage.prepare(1L, List.of(new DynamicSyncMessage.Entry(
                        item("mymod:ruby", 16, "common"), "s.js"))));
        String rewritten = commitWithEntries.replace("\"PREPARE\"", "\"COMMIT\"");
        assertThrows(IllegalArgumentException.class,
                () -> DynamicSyncWireCodec.decodeServerMessage(rewritten));

        assertThrows(IllegalArgumentException.class,
                () -> DynamicSyncWireCodec.decodeServerMessage("{\"v\":1,\"kind\":\"PREPARE\"}"));
        assertThrows(IllegalArgumentException.class, () -> DynamicSyncWireCodec.decodeReply("not json"));
    }

    @Test
    void encodedBodyIsDeterministic() {
        DynamicSyncMessage message = DynamicSyncMessage.prepare(4L, List.of(
                new DynamicSyncMessage.Entry(soundEvent("mymod:boom", 16.0f), "server_scripts/main.js")));
        assertEquals(DynamicSyncWireCodec.encodeServerMessage(message),
                DynamicSyncWireCodec.encodeServerMessage(message),
                "same message must encode byte-identically (stable wire format)");
        assertTrue(DynamicSyncWireCodec.encodeServerMessage(message).startsWith("{\"v\":1,\"kind\":\"PREPARE\""));
    }
}
