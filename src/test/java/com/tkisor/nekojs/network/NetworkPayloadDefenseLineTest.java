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
 * 票 17 防线等价 fixture：网络线程收包侧的坏输入（非法 channel、坏 NBT、超大 payload、
 * 空 key）在线格式解码边界被拒绝——以异常形式呈现（平台据此断连），不静默 no-op，也不
 * 把异常漏进平台网络线程的后续处理；发送面的 {@link NetworkJS} 语义（data 归一化、经
 * {@link PlayPacketDispatchers} 装配）与既有契约一致。
 *
 * <p>「不炸平台网络线程」的最终仲裁在 loader 侧（NeoForge/Fabric 把解码异常转成断连），
 * JVM 层钉住的是本仓库的防线输入：decode 必须抛、post 核心必须吞掉监听器异常
 * （见 NetworkGenerationRoutingTest.listenerExceptionIsContainedAndDoesNotAbortDispatch）。
 */
class NetworkPayloadDefenseLineTest {

    @AfterEach
    void restoreNoopDispatcher() {
        PlayPacketDispatchers.install(PlayPacketDispatchers.NOOP);
    }

    private static FriendlyByteBuf vanillaBuf() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    // ---- 解码侧防线：坏输入必须抛（不是静默 no-op） ----

    @Test
    void illegalChannelOnWireIsRejectedAtDecode() {
        // UTF8(65 chars) + 空 CompoundTag(0a 00)：字符串读出后经 compact 构造器拒绝
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
        // 合法 channel + 截断的 NBT（TAG_Int 名字后缺 4 字节载荷）：解码必须失败。
        // 断言 Throwable 而非具体异常类型：坏 NBT 走 NbtIo.readTagSafe → CrashReport——
        // 游戏进程内是 DecoderException（平台转断连），裸 JUnit 里 CrashReport 的静态初始化
        // （SystemReport）本身不可用，以 Error 形态冒泡。两种形态都不是静默 no-op，
        // 「解码绝不产出 payload」才是钉住的契约。
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
            // Throwable 级断言的判别力下界：排除把 setup/断言自身的 AssertionError 误当
            // 解码失败——真实形态只能是 DecoderException（游戏内）或 CrashReport 初始化
            // 失败的 Error（裸 JUnit），二者都源于 NBT 解码路径
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

    // ---- 发送面：NetworkJS 语义不变 + 经 PlayPacketDispatcher 装配 ----

    /** 记录型 dispatcher：钉住 NetworkJS 的发送路由与 payload 形状。 */
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
        NetworkJS.sendToAll("ch", null); // null data 归一化为空 CompoundTag（既有语义）

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

    /** 装配契约：发送失败不打断脚本——未装配/异常路径静默丢弃（NOOP 兜底，首次告警）。 */
    @Test
    void noopDispatcherDropsSendsWithoutThrowing() {
        PlayPacketDispatchers.install(PlayPacketDispatchers.NOOP);
        assertDoesNotThrow(() -> NetworkJS.sendToPlayer(null, "ch", new CompoundTag()));
        assertDoesNotThrow(() -> NetworkJS.sendToAll("ch", new CompoundTag()));
    }
}
