package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.network.dto.ErrorSummaryDTO;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record ShowErrorListPacket(List<ErrorSummaryDTO> errors) implements CustomPacketPayload {

    public static final Type<ShowErrorListPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NekoJS.MODID, "show_error_list"));

    public static final StreamCodec<FriendlyByteBuf, ShowErrorListPacket> STREAM_CODEC = StreamCodec.ofMember(
            ShowErrorListPacket::write,
            ShowErrorListPacket::new
    );

    public ShowErrorListPacket(FriendlyByteBuf buf) {
        this(buf.readList(b -> new ErrorSummaryDTO(
                b.readUtf(),
                b.readUtf(),
                b.readInt(),
                b.readInt(),
                b.readUtf(),
                b.readUtf(262144)
        )));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(this.errors, (b, e) -> {
            b.writeUtf(e.id());
            b.writeUtf(e.path());
            b.writeInt(e.line());
            b.writeInt(e.count());
            b.writeUtf(e.message());
            b.writeUtf(e.fullDetails(), 262144);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}