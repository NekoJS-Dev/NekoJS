package com.tkisor.nekojs.network;

import com.tkisor.nekojs.wrapper.network.NetworkJS;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture for bad input on the receiving side of the network thread: an illegal channel, bad
 * NBT, an oversized payload or an empty key is rejected at wire decode as an exception (the
 * platform disconnects on it). Decode never silently no-ops, and the exception never leaks into
 * the platform network thread's remaining work. On the sending side {@link NetworkJS} keeps its
 * existing behavior: it normalizes data and assembles the packet through
 * {@link PlayPacketDispatchers}.
 *
 * <p>The loader decides whether the platform network thread survives (NeoForge and Fabric turn a
 * decode failure into a disconnect). The JVM level pins what this repository feeds it: decode
 * must throw, and the post core must swallow listener exceptions (see
 * NetworkGenerationRoutingTest.listenerExceptionIsContainedAndDoesNotAbortDispatch).
 */
class NetworkPayloadDefenseLineTest {

    @AfterEach
    void restoreNoopDispatcher() {
        PlayPacketDispatchers.install(PlayPacketDispatchers.NOOP);
    }

    private static FriendlyByteBuf vanillaBuf() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    // ---- Decode side: bad input must throw, not silently no-op ----

    @Test
    void illegalChannelOnWireIsRejectedAtDecode() {
        // UTF8(65 chars) + empty CompoundTag(0a 00): the string reads out, then the compact
        // constructor rejects it
        StringBuilder channel = new StringBuilder();
        for (int i = 0; i < 65; i++) channel.append('a');
        FriendlyByteBuf buf = vanillaBuf();
        try {
            buf.writeUtf(channel.toString());
            buf.writeByte(0x0a);
            buf.writeByte(0x00);
            assertThrows(IllegalArgumentException.class,
                    () -> NekoScriptPayload.CODEC.decode(
                            new net.minecraft.network.RegistryFriendlyByteBuf(buf, net.minecraft.core.RegistryAccess.EMPTY)),
                    "over-long channel from the wire must be rejected at decode, not silently accepted");
        } finally {
            buf.release();
        }
    }

    @Test
    void blankChannelOnWireIsRejectedAtDecode() {
        FriendlyByteBuf buf = vanillaBuf();
        try {
            buf.writeUtf("");
            buf.writeByte(0x0a);
            buf.writeByte(0x00);
            assertThrows(IllegalArgumentException.class,
                    () -> NekoScriptPayload.CODEC.decode(
                            new net.minecraft.network.RegistryFriendlyByteBuf(buf, net.minecraft.core.RegistryAccess.EMPTY)),
                    "blank channel from the wire must be rejected at decode");
        } finally {
            buf.release();
        }
    }

    @Test
    void malformedNbtOnWireIsRejectedAtDecode() {
        // A legal channel plus truncated NBT (a TAG_Int name with its 4 payload bytes missing): decode
        // must fail. The assertion accepts any Throwable because bad NBT goes through
        // NbtIo.readTagSafe into a CrashReport: inside the game that is a DecoderException (the
        // platform turns it into a disconnect), while in bare JUnit the CrashReport static
        // initialization (SystemReport) is itself unavailable and surfaces as an Error. Neither
        // form is a silent no-op; the pinned rule is that decode never produces a payload.
        FriendlyByteBuf buf = vanillaBuf();
        try {
            buf.writeUtf("ch");
            buf.writeByte(0x0a);
            buf.writeByte(0x03);
            buf.writeShort(4);
            buf.writeBytes(new byte[] {'m', 'a'});
            Throwable thrown = assertThrows(Throwable.class,
                    () -> NekoScriptPayload.CODEC.decode(
                            new net.minecraft.network.RegistryFriendlyByteBuf(buf, net.minecraft.core.RegistryAccess.EMPTY)),
                    "malformed NBT must fail decode loudly (platform disconnects), never decode into a payload");
            // Lower bound on what the Throwable assertion can tell apart: it rules out mistaking an
            // AssertionError from the setup or the assertion itself for a decode failure. The real
            // form is either a DecoderException inside the game or an Error from CrashReport
            // initialization failure in bare JUnit; both come from the NBT decode path.
            assertFalse(thrown instanceof AssertionError,
                    "decode failure must originate from the NBT path, not from a test assertion");
        } finally {
            buf.release();
        }
    }

    @Test
    void oversizedPackHashListIsRejectedAtDecode() {
        FriendlyByteBuf buf = vanillaBuf();
        try {
            buf.writeVarInt(257);
            assertThrows(IllegalArgumentException.class,
                    () -> PackHashListPayload.STREAM_CODEC.decode(buf),
                    "hash entry count above the wire ceiling must be rejected");
        } finally {
            buf.release();
        }
    }

    @Test
    void oversizedPackBundleIsRejectedAtDecode() {
        FriendlyByteBuf buf = vanillaBuf();
        try {
            buf.writeVarInt(257);
            assertThrows(IllegalArgumentException.class,
                    () -> PackBundlePayload.STREAM_CODEC.decode(buf),
                    "pack count above the wire ceiling must be rejected");
        } finally {
            buf.release();
        }
    }

    @Test
    void overlongShowErrorListStringIsRejectedAtDecode() {
        FriendlyByteBuf buf = vanillaBuf();
        try {
            buf.writeVarInt(1);
            buf.writeVarInt(300_000);
            assertThrows(Exception.class,
                    () -> ShowErrorListPacket.STREAM_CODEC.decode(buf),
                    "string longer than the packet ceiling must fail decode");
        } finally {
            buf.release();
        }
    }

    @Test
    void blankClientDataKeyOnWireIsRejectedAtDecode() {
        FriendlyByteBuf buf = vanillaBuf();
        try {
            buf.writeUtf("");
            buf.writeUtf("{}");
            assertThrows(IllegalArgumentException.class,
                    () -> ClientDataSyncPacket.STREAM_CODEC.decode(
                            new net.minecraft.network.RegistryFriendlyByteBuf(buf, net.minecraft.core.RegistryAccess.EMPTY)),
                    "blank client-data key from the wire must be rejected at decode");
        } finally {
            buf.release();
        }
    }

    // ---- Send side: NetworkJS behavior is unchanged and the packet goes through PlayPacketDispatcher ----

    /** Recording dispatcher: pins the send route and the payload shape of NetworkJS. */
    private static final class RecordingDispatcher implements PlayPacketDispatcher {
        final List<Sent> sent = new ArrayList<>();

        record Sent(String method, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {}

        @Override
        public void sendToPlayer(net.minecraft.server.level.ServerPlayer player,
                                 net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
            sent.add(new Sent("player", payload));
        }

        @Override
        public void sendToAllPlayers(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
            sent.add(new Sent("all", payload));
        }

        @Override
        public void sendToPlayersTrackingEntityAndSelf(net.minecraft.world.entity.Entity entity,
                                                       net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
            sent.add(new Sent("tracking", payload));
        }
    }

    @Test
    void networkSendRoutesThroughInstalledDispatcherWithLegacyPayloadShape() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        PlayPacketDispatchers.install(dispatcher);
        assertSame(dispatcher, PlayPacketDispatchers.get(), "installed dispatcher must be the one in use");

        CompoundTag tag = new CompoundTag();
        tag.putInt("v", 1);
        NetworkJS.sendToPlayer(null, "ch", tag);
        NetworkJS.sendToAll("ch", null); // null data is normalized to an empty CompoundTag, as before

        assertEquals(2, dispatcher.sent.size());
        NekoScriptPayload toPlayer = (NekoScriptPayload) dispatcher.sent.get(0).payload();
        assertEquals("ch", toPlayer.channel());
        assertEquals(tag, toPlayer.data());
        NekoScriptPayload toAll = (NekoScriptPayload) dispatcher.sent.get(1).payload();
        assertEquals("ch", toAll.channel());
        assertEquals(new CompoundTag(), toAll.data(), "null data must be normalized to an empty tag");
        assertEquals("player", dispatcher.sent.get(0).method());
        assertEquals("all", dispatcher.sent.get(1).method());
    }

    /**
     * Wiring: a failed send never breaks the script. With no dispatcher installed, or on an error
     * path, the send is dropped silently (the NOOP fallback warns once).
     */
    @Test
    void noopDispatcherDropsSendsWithoutThrowing() {
        PlayPacketDispatchers.install(PlayPacketDispatchers.NOOP);
        assertDoesNotThrow(() -> NetworkJS.sendToPlayer(null, "ch", new CompoundTag()));
        assertDoesNotThrow(() -> NetworkJS.sendToAll("ch", new CompoundTag()));
    }
}
