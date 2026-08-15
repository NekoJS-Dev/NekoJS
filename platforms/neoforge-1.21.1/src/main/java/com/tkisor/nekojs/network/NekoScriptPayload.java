package com.tkisor.nekojs.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NekoScriptPayload(String channel, CompoundTag data) implements CustomPacketPayload {

    public NekoScriptPayload {
        if (channel == null || channel.isBlank() || channel.length() > 64) {
            throw new IllegalArgumentException("Script payload channel must be non-blank and at most 64 characters");
        }
    }

    public static final Type<NekoScriptPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("nekojs", "script_payload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NekoScriptPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NekoScriptPayload::channel,
            ByteBufCodecs.COMPOUND_TAG, NekoScriptPayload::data,
            NekoScriptPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}