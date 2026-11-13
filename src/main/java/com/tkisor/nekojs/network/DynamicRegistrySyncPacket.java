//? if neoforge {
//? if >=26 {
// No version divergence: payload and codec shape are identical on 26.1/26.2. 1.21.1 and Fabric have
// no dynamic registry support, so their capability entry keeps the not-verified wording, and
// registration only happens through McPlatformCompat.registerDynamicSyncPayload on the >=26
// NeoForge implementations.
package com.tkisor.nekojs.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Dynamic Registry batch transaction sync payload, a member of the register-once channel family. It
 * is bidirectional and its body is a single JSON string; the schema and encoding live in common's
 * {@code DynamicSyncWireCodec}. Server-to-client PREPARE/STATE_SYNC/COMMIT/ABORT and client-to-server
 * ACK/ACTIVATION_REPORT share this one type, so there is no second channel and no second model.
 *
 * <p>Registration happens only at the existing {@code RegisterPayloadHandlersEvent} entry
 * ({@code NekoJSNetwork}, channel {@code "1"}) through
 * {@code McPlatformCompat.Impl#registerDynamicSyncPayload}. It is never registered from a reload or
 * command path.
 */
public record DynamicRegistrySyncPacket(String json) implements CustomPacketPayload {

    public DynamicRegistrySyncPacket {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Dynamic registry sync payload body must be non-blank JSON");
        }
    }

    public static final Type<DynamicRegistrySyncPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("nekojs", "dynamic_registry_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DynamicRegistrySyncPacket> CODEC = codec();

    /** The identical JSON encoding needs no registry access during configuration. */
    public static final StreamCodec<FriendlyByteBuf, DynamicRegistrySyncPacket> COMMON_CODEC = codec();

    private static <B extends FriendlyByteBuf> StreamCodec<B, DynamicRegistrySyncPacket> codec() {
        return StreamCodec.composite(ByteBufCodecs.STRING_UTF8, DynamicRegistrySyncPacket::json,
                DynamicRegistrySyncPacket::new);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
//?}
//?}
