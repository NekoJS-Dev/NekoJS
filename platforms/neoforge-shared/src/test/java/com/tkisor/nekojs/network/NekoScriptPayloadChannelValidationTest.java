package com.tkisor.nekojs.network;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * S3 regression test: the script payload channel must be non-null, non-blank
 * and at most 64 characters. Constructed directly through the record's compact
 * constructor so the test needs no RegistryFriendlyByteBuf setup.
 */
class NekoScriptPayloadChannelValidationTest {

    @Test
    void validChannelIsAccepted() {
        assertDoesNotThrow(() -> new NekoScriptPayload("server_events", new CompoundTag()));
    }

    @Test
    void nullChannelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new NekoScriptPayload(null, new CompoundTag()));
    }

    @Test
    void blankChannelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new NekoScriptPayload("   ", new CompoundTag()));
    }

    @Test
    void channelOver64CharsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new NekoScriptPayload("a".repeat(65), new CompoundTag()));
    }
}
