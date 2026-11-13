package com.tkisor.nekojs.network;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire fixture for {@code NekoScriptPayload} and the basic play/configuration payloads covered
 * here: golden hex from the old wire bytes pins their ids, codecs, field order and wire format.
 * Both loaders share the same payload classes and codecs, so the test compiles and runs on every
 * node.
 *
 * <p>The golden constants are measured encode bytes from baseline eba89230 on the 26.1.2 active
 * node (compiled directly against the shared tree). The test also decodes the golden bytes with
 * this node's codec and requires the same payload back, so both the bytes and the equivalent
 * round trip are pinned. Changing the id, the field order or the codec of any payload turns this
 * test red. That is an intended change: it has to go through the wire migration table and the
 * fixture gate, and no golden may be edited in passing.
 *
 * <p>How the field order reads in the hex (all five nodes of this dependency set encode the
 * buffer first and the value after it in {@code StreamEncoder.encode}):
 * <ul>
 *   <li>script_payload: UTF8(channel) + NBT(data);</li>
 *   <li>pdata_sync: varint(entityId) + varint(revision) + NBT(data);</li>
 *   <li>client_data_sync: UTF8(key) + UTF8(json);</li>
 *   <li>show_error_list: varint(count) + [UTF8(id) UTF8(path) int(line) int(count)
 *       UTF8(message) UTF8(fullDetails)] + boolean(openIfMissing);</li>
 *   <li>pack_hashes: varint(count) + [UTF8(syncId) + UTF8(hash)];</li>
 *   <li>pack_bundle: varint(packs) + [UTF8(syncId) UTF8(scope) byteArray(manifest)
 *       varint(files) + [UTF8(relPath) byteArray(bytes)]]</li>
 * </ul>
 */
class PayloadWireFormatGoldenTest {

    // ---- goldens (measured on eba89230; samples: channel/my_channel, tag={mana:7,name:neko}, etc., see each case) ----

    private static final String SCRIPT_PAYLOAD_ID = "nekojs:script_payload";
    private static final String SCRIPT_PAYLOAD_HEX =
            "0a6d795f6368616e6e656c0a0300046d616e61000000070800046e616d6500046e656b6f00";

    private static final String PDATA_SYNC_ID = "nekojs:pdata_sync";
    private static final String PDATA_SYNC_HEX =
            "d209090a0300046d616e61000000070800046e616d6500046e656b6f00";

    private static final String CLIENT_DATA_SYNC_ID = "nekojs:client_data_sync";
    private static final String CLIENT_DATA_SYNC_HEX = "086875642f6d616e61077b2276223a377d";

    private static final String SHOW_ERROR_LIST_ID = "nekojs:show_error_list";
    private static final String SHOW_ERROR_LIST_HEX =
            "01206e656b6f6a732f72742f7365727665725f736372697074732f64656d6f2e6a73"
                    + "167365727665725f736372697074732f64656d6f2e6a730000000100000003"
                    + "0a64656d6f206572726f720e66726f7a656e2064657461696c7301";

    private static final String PACK_HASHES_ID = "nekojs:pack_hashes";
    private static final String PACK_HASHES_HEX = "01067061636b5f61086465616462656566";

    private static final String PACK_BUNDLE_ID = "nekojs:pack_bundle";
    private static final String PACK_BUNDLE_HEX =
            "01067061636b5f6106474c4f42414c077b2276223a317d010c736372697074732f612e6a7303783d31";

    // ---- Sample construction (the same as at capture time) ----

    private static CompoundTag sampleTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("mana", 7);
        tag.putString("name", "neko");
        return tag;
    }

    private static ErrorSummaryDTO sampleError() {
        return new ErrorSummaryDTO("nekojs/rt/server_scripts/demo.js",
                "server_scripts/demo.js", 1, 3, "demo error", "frozen details");
    }

    private static PackBundlePayload sampleBundle() {
        PackBundlePayload.FileEntry file = new PackBundlePayload.FileEntry("scripts/a.js",
                "x=1".getBytes(StandardCharsets.UTF_8));
        PackBundlePayload.PackEntry pack = new PackBundlePayload.PackEntry("pack_a", "GLOBAL",
                "{\"v\":1}".getBytes(StandardCharsets.UTF_8), List.of(file));
        return new PackBundlePayload(List.of(pack));
    }

    // ---- Codec helpers (decode takes one argument; encode puts the buffer first and the value
    // after it on all five nodes of this dependency set, measured the same on 26.1/26.2/1.21.1) ----

    private static String drainHex(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] unhex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static RegistryFriendlyByteBuf registryBufOf(String hex) {
        return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(unhex(hex)), RegistryAccess.EMPTY);
    }

    private static FriendlyByteBuf vanillaBufOf(String hex) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(unhex(hex)));
    }

    private static String encodeScriptPayloadHex(NekoScriptPayload payload) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            NekoScriptPayload.CODEC.encode(buf, payload);
            return drainHex(buf);
        } finally {
            buf.release();
        }
    }

    private static String encodePDataSyncHex(PDataSyncPacket payload) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PDataSyncPacket.STREAM_CODEC.encode(buf, payload);
            return drainHex(buf);
        } finally {
            buf.release();
        }
    }

    private static String encodeClientDataSyncHex(ClientDataSyncPacket payload) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ClientDataSyncPacket.STREAM_CODEC.encode(buf, payload);
            return drainHex(buf);
        } finally {
            buf.release();
        }
    }

    private static String encodeShowErrorListHex(ShowErrorListPacket payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ShowErrorListPacket.STREAM_CODEC.encode(buf, payload);
            return drainHex(buf);
        } finally {
            buf.release();
        }
    }

    private static String encodePackHashesHex(PackHashListPayload payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PackHashListPayload.STREAM_CODEC.encode(buf, payload);
            return drainHex(buf);
        } finally {
            buf.release();
        }
    }

    private static String encodePackBundleHex(PackBundlePayload payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PackBundlePayload.STREAM_CODEC.encode(buf, payload);
            return drainHex(buf);
        } finally {
            buf.release();
        }
    }

    // ---- Pinned ids ----

    @Test
    void payloadIdsStayOnLegacyWire() {
        assertEquals(SCRIPT_PAYLOAD_ID, NekoScriptPayload.TYPE.id().toString());
        assertEquals(PDATA_SYNC_ID, PDataSyncPacket.TYPE.id().toString());
        assertEquals(CLIENT_DATA_SYNC_ID, ClientDataSyncPacket.TYPE.id().toString());
        assertEquals(SHOW_ERROR_LIST_ID, ShowErrorListPacket.TYPE.id().toString());
        assertEquals(PACK_HASHES_ID, PackHashListPayload.TYPE.id().toString());
        assertEquals(PACK_BUNDLE_ID, PackBundlePayload.TYPE.id().toString());
    }

    // ---- Encoding: byte-for-byte match with the golden hex ----

    @Test
    void scriptPayloadEncodesToGoldenWireBytes() {
        assertEquals(SCRIPT_PAYLOAD_HEX, encodeScriptPayloadHex(
                new NekoScriptPayload("my_channel", sampleTag())));
    }

    @Test
    void pDataSyncEncodesToGoldenWireBytes() {
        assertEquals(PDATA_SYNC_HEX, encodePDataSyncHex(new PDataSyncPacket(1234, 9, sampleTag())));
    }

    @Test
    void clientDataSyncEncodesToGoldenWireBytes() {
        assertEquals(CLIENT_DATA_SYNC_HEX, encodeClientDataSyncHex(
                new ClientDataSyncPacket("hud/mana", "{\"v\":7}")));
    }

    @Test
    void showErrorListEncodesToGoldenWireBytes() {
        assertEquals(SHOW_ERROR_LIST_HEX, encodeShowErrorListHex(
                new ShowErrorListPacket(List.of(sampleError()), true)));
    }

    @Test
    void packHashesEncodesToGoldenWireBytes() {
        assertEquals(PACK_HASHES_HEX, encodePackHashesHex(
                new PackHashListPayload(List.of(new PackHashListPayload.HashEntry("pack_a", "deadbeef")))));
    }

    @Test
    void packBundleEncodesToGoldenWireBytes() {
        assertEquals(PACK_BUNDLE_HEX, encodePackBundleHex(sampleBundle()));
    }

    // ---- Decode side: decode the golden bytes and compare the result with an equivalent payload ----

    @Test
    void goldenScriptPayloadBytesDecodeToEquivalentPayload() {
        RegistryFriendlyByteBuf buf = registryBufOf(SCRIPT_PAYLOAD_HEX);
        try {
            NekoScriptPayload decoded = NekoScriptPayload.CODEC.decode(buf);
            assertEquals("my_channel", decoded.channel());
            assertEquals(sampleTag(), decoded.data());
            assertEquals(0, buf.readableBytes(), "golden bytes must be fully consumed");
        } finally {
            buf.release();
        }
    }

    @Test
    void goldenPDataSyncBytesDecodeToEquivalentPayload() {
        RegistryFriendlyByteBuf buf = registryBufOf(PDATA_SYNC_HEX);
        try {
            PDataSyncPacket decoded = PDataSyncPacket.STREAM_CODEC.decode(buf);
            assertEquals(1234, decoded.entityId());
            assertEquals(9, decoded.revision());
            assertEquals(sampleTag(), decoded.data());
            assertEquals(0, buf.readableBytes(), "golden bytes must be fully consumed");
        } finally {
            buf.release();
        }
    }

    @Test
    void goldenClientDataSyncBytesDecodeToEquivalentPayload() {
        RegistryFriendlyByteBuf buf = registryBufOf(CLIENT_DATA_SYNC_HEX);
        try {
            ClientDataSyncPacket decoded = ClientDataSyncPacket.STREAM_CODEC.decode(buf);
            assertEquals("hud/mana", decoded.key());
            assertEquals("{\"v\":7}", decoded.json());
            assertEquals(0, buf.readableBytes(), "golden bytes must be fully consumed");
        } finally {
            buf.release();
        }
    }

    @Test
    void goldenShowErrorListBytesDecodeToEquivalentPayload() {
        FriendlyByteBuf buf = vanillaBufOf(SHOW_ERROR_LIST_HEX);
        try {
            ShowErrorListPacket decoded = ShowErrorListPacket.STREAM_CODEC.decode(buf);
            assertEquals(List.of(sampleError()), decoded.errors());
            assertTrue(decoded.openIfMissing());
            assertEquals(0, buf.readableBytes(), "golden bytes must be fully consumed");
        } finally {
            buf.release();
        }
    }

    @Test
    void goldenPackHashesBytesDecodeToEquivalentPayload() {
        FriendlyByteBuf buf = vanillaBufOf(PACK_HASHES_HEX);
        try {
            PackHashListPayload decoded = PackHashListPayload.STREAM_CODEC.decode(buf);
            assertEquals(List.of(new PackHashListPayload.HashEntry("pack_a", "deadbeef")), decoded.entries());
            assertEquals(0, buf.readableBytes(), "golden bytes must be fully consumed");
        } finally {
            buf.release();
        }
    }

    @Test
    void goldenPackBundleBytesDecodeToEquivalentPayload() {
        FriendlyByteBuf buf = vanillaBufOf(PACK_BUNDLE_HEX);
        try {
            PackBundlePayload decoded = PackBundlePayload.STREAM_CODEC.decode(buf);
            assertEquals(1, decoded.packs().size());
            PackBundlePayload.PackEntry pack = decoded.packs().get(0);
            assertEquals("pack_a", pack.syncId());
            assertEquals("GLOBAL", pack.scope());
            assertArrayEquals("{\"v\":1}".getBytes(StandardCharsets.UTF_8), pack.manifestJson());
            assertEquals(1, pack.files().size());
            assertEquals("scripts/a.js", pack.files().get(0).relativePath());
            assertArrayEquals("x=1".getBytes(StandardCharsets.UTF_8), pack.files().get(0).bytes());
            assertEquals(0, buf.readableBytes(), "golden bytes must be fully consumed");
        } finally {
            buf.release();
        }
    }
}
