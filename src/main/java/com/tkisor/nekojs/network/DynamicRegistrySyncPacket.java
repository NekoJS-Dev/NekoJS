//? if neoforge {
//? if >=26 {
// No version divergence: payload and codec shape are identical on 26.1/26.2; 1.21.1 and
// fabric carry no dynamic registry face (ticket 16/21 baseline capability table keeps the
// not-verified wording), and registration only happens through
// McPlatformCompat.registerDynamicSyncPayload on the >=26 NeoForge impls.
package com.tkisor.nekojs.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Dynamic Registry batch-transaction sync payload (ticket 21 platform wiring, member of
 * the ticket 17 register-once channel family): bidirectional, its body is a single JSON
 * string (schema and encoding live in common's {@code DynamicSyncWireCodec} — server-to-
 * client PREPARE/STATE_SYNC/COMMIT/ABORT and client-to-server ACK/ACTIVATION_REPORT share
 * this one type; no second channel and no second model).
 *
 * <p>Registration happens only at the existing {@code RegisterPayloadHandlersEvent} entry
 * ({@code NekoJSNetwork}, channel {@code "1"}) through
 * {@code McPlatformCompat.Impl#registerDynamicSyncPayload}; this class is never
 * registered from a reload/command path (ticket 17 AC1 discipline).
 */
public record DynamicRegistrySyncPacket(String json) implements CustomPacketPayload {

    public DynamicRegistrySyncPacket {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Dynamic registry sync payload body must be non-blank JSON");
        }
    }

    public static final Type<DynamicRegistrySyncPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("nekojs", "dynamic_registry_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DynamicRegistrySyncPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, DynamicRegistrySyncPacket::json,
                    DynamicRegistrySyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
//?}
//?}
