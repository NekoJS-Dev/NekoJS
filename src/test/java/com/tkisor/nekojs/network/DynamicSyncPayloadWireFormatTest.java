//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.network;

import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinition;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;
import com.tkisor.nekojs.core.dynamic.plan.DynamicItemBuilder;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncMessage;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncReply;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncWireCodec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 21 platform wiring wire golden (an addition under the ticket 17 AC2 discipline,
 * not a change to it): pins the id, codec and wire format of the dynamic registry
 * batch-transaction payload {@code nekojs:dynamic_registry_sync} from day one — the body
 * is a single UTF-8 JSON string (varint length + bytes), with the JSON itself produced by
 * common's {@code DynamicSyncWireCodec} (bodies asserted separately).
 *
 * <p>The golden constants are measured encode output from the 26.1.2 active node (the
 * capture run is archived in this ticket's evidence directory). The test compiles and
 * runs only on nodes with the dynamic registry face (26.x NeoForge); 1.21.1/fabric nodes
 * have no such payload class (explicit-subset discipline). Any change to the payload id,
 * field order or JSON shape turns this test red — a managed change must go through the
 * wire migration table and the fixture gate.
 */
class DynamicSyncPayloadWireFormatTest {

    private static final String DYNAMIC_SYNC_ID = "nekojs:dynamic_registry_sync";

    /** varint(52) + ASCII: {"v":1,"kind":"COMMIT","generation":12,"entries":[]} */
    private static final String COMMIT_HEX =
            "347b2276223a312c226b696e64223a22434f4d4d4954222c2267656e65726174696f6e223a3132"
                    + "2c22656e7472696573223a5b5d7d";

    /** varint(182) + ASCII: single-entry PREPARE (fireResistant/maxStackSize/mode/rarity readings + sha256 fingerprint). */
    private static final String PREPARE_HEX =
            "b6027b2276223a312c226b696e64223a2250524550415245222c2267656e65726174696f6e223a3132"
                    + "2c22656e7472696573223a5b7b22646566696e6974696f6e223a7b2274797065223a224954454d222c"
                    + "226964223a226d796d6f643a72756279222c226d6f6465223a22574f524c44222c2272656164696e67"
                    + "73223a5b2266697265526573697374616e743d66616c7365222c226d6178537461636b53697a653d"
                    + "3136222c226d6f64653d776f726c64222c227261726974793d65706963225d2c2266696e6765727072"
                    + "696e74223a22613734363931353533386137356135613664346465303631643937336139376335"
                    + "63633736316261343939386139386331336134616234316562326433376561227d2c226f776e6572"
                    + "223a227365727665725f736372697074732f6d61696e2e6a73227d5d7d";

    /** varint(51) + ASCII: {"v":1,"kind":"ACK","generation":12,"accepted":true} */
    private static final String ACK_HEX =
            "347b2276223a312c226b696e64223a2241434b222c2267656e65726174696f6e223a31322c2261"
                    + "63636570746564223a747275657d";

    // ---- sample construction (identical to the capture run) ----

    private static DynamicDefinition sampleItem() {
        DynamicItemBuilder builder = new DynamicItemBuilder();
        builder.setMaxStackSize(16);
        builder.setRarity("epic");
        return DynamicDefinition.of(DynamicDefinitionType.ITEM, "mymod:ruby", builder);
    }

    private static String commitBody() {
        return DynamicSyncWireCodec.encodeServerMessage(
                new DynamicSyncMessage(DynamicSyncMessage.Kind.COMMIT, 12L, List.of(), null));
    }

    private static String prepareBody() {
        return DynamicSyncWireCodec.encodeServerMessage(new DynamicSyncMessage(
                DynamicSyncMessage.Kind.PREPARE, 12L,
                List.of(new DynamicSyncMessage.Entry(sampleItem(), "server_scripts/main.js")), null));
    }

    private static String ackBody() {
        return DynamicSyncWireCodec.encodeReply(DynamicSyncReply.ack(12L, true, null));
    }

    // ---- codec helpers (decode single-arg; encode is buf-first, value-after across the dependency set) ----

    private static String encodeHex(String body) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            DynamicRegistrySyncPacket.CODEC.encode(buf, new DynamicRegistrySyncPacket(body));
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } finally {
            buf.release();
        }
    }

    private static RegistryFriendlyByteBuf bufOf(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(out), RegistryAccess.EMPTY);
    }

    // ---- id pin ----

    @Test
    void payloadIdIsPinnedFromDayOne() {
        assertEquals(DYNAMIC_SYNC_ID, DynamicRegistrySyncPacket.TYPE.id().toString());
    }

    // ---- encode side: byte-identical to the golden hex ----

    @Test
    void commitEncodesToGoldenWireBytes() {
        assertEquals(COMMIT_HEX, encodeHex(commitBody()));
    }

    @Test
    void prepareEncodesToGoldenWireBytes() {
        assertEquals(PREPARE_HEX, encodeHex(prepareBody()));
    }

    @Test
    void ackEncodesToGoldenWireBytes() {
        assertEquals(ACK_HEX, encodeHex(ackBody()));
    }

    @Test
    void configurationCodecPreservesGoldenBytesWithoutRegistryAccess() {
        for (String[] pair : List.of(new String[] {COMMIT_HEX, commitBody()},
                new String[] {PREPARE_HEX, prepareBody()}, new String[] {ACK_HEX, ackBody()})) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                DynamicRegistrySyncPacket.COMMON_CODEC.encode(buffer, new DynamicRegistrySyncPacket(pair[1]));
                assertEquals(pair[0], java.util.HexFormat.of().formatHex(io.netty.buffer.ByteBufUtil.getBytes(buffer)));
                assertEquals(pair[1], DynamicRegistrySyncPacket.COMMON_CODEC.decode(buffer).json());
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    // ---- decode side: golden bytes decode to the equivalent payload ----

    @Test
    void goldenBytesDecodeToEquivalentPayloadsAndBodies() {
        for (String[] pair : List.of(new String[] {COMMIT_HEX, commitBody()},
                new String[] {PREPARE_HEX, prepareBody()}, new String[] {ACK_HEX, ackBody()})) {
            RegistryFriendlyByteBuf buf = bufOf(pair[0]);
            try {
                DynamicRegistrySyncPacket decoded = DynamicRegistrySyncPacket.CODEC.decode(buf);
                assertEquals(pair[1], decoded.json());
                assertEquals(0, buf.readableBytes(), "golden bytes must be fully consumed");
            } finally {
                buf.release();
            }
        }
    }

    // ---- round trip through the common protocol model ----

    @Test
    void goldenBodiesDecodeThroughTheCommonCodec() {
        DynamicSyncMessage prepared = DynamicSyncWireCodec.decodeServerMessage(prepareBody());
        assertEquals(DynamicSyncMessage.Kind.PREPARE, prepared.kind());
        assertEquals(12L, prepared.generation());
        assertEquals(1, prepared.entries().size());
        assertEquals("minecraft:item|mymod:ruby", prepared.entries().get(0).key());
        assertTrue(prepared.entries().get(0).definition().readings().contains("maxStackSize=16"));

        DynamicSyncMessage commit = DynamicSyncWireCodec.decodeServerMessage(commitBody());
        assertEquals(DynamicSyncMessage.Kind.COMMIT, commit.kind());

        DynamicSyncReply ack = DynamicSyncWireCodec.decodeReply(ackBody());
        assertEquals(Boolean.TRUE, ack.accepted());
    }
}
//?}
//?}
