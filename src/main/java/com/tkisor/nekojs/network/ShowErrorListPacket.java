//~ mc_legacy_api
package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record ShowErrorListPacket(List<ErrorSummaryDTO> errors, boolean openIfMissing) implements CustomPacketPayload {

    /** Wire strings share one explicit ceiling so long paths/summaries cannot hit UTF's implicit default limit. */
    private static final int STRING_MAX_LENGTH = 262_144;

    public ShowErrorListPacket(List<ErrorSummaryDTO> errors) {
        this(errors, true);
    }

    public static final Type<ShowErrorListPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NekoJS.MODID, "show_error_list"));

    public static final StreamCodec<FriendlyByteBuf, ShowErrorListPacket> STREAM_CODEC = StreamCodec.ofMember(
            ShowErrorListPacket::write,
            ShowErrorListPacket::new
    );

    public ShowErrorListPacket(FriendlyByteBuf buf) {
        this(buf.readList(b -> new ErrorSummaryDTO(
                b.readUtf(STRING_MAX_LENGTH),
                b.readUtf(STRING_MAX_LENGTH),
                b.readInt(),
                b.readInt(),
                b.readUtf(STRING_MAX_LENGTH),
                b.readUtf(STRING_MAX_LENGTH)
        )), buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(this.errors, (b, e) -> {
            b.writeUtf(e.id(), STRING_MAX_LENGTH);
            b.writeUtf(e.path(), STRING_MAX_LENGTH);
            b.writeInt(e.line());
            b.writeInt(e.count());
            b.writeUtf(e.message(), STRING_MAX_LENGTH);
            b.writeUtf(e.fullDetails(), STRING_MAX_LENGTH);
        });
        buf.writeBoolean(this.openIfMissing);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
