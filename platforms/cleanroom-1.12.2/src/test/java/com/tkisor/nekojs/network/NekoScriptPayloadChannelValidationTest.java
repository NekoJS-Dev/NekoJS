package com.tkisor.nekojs.network;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cleanroom (1.12.2) mirror of the NeoForge script-payload channel validation:
 * channel must be non-null, non-blank and at most 64 characters.
 */
class NekoScriptPayloadChannelValidationTest {

    @Test
    void validChannelIsAccepted() {
        NekoScriptPayload payload = new NekoScriptPayload("server_events", new NBTTagCompound());

        assertEquals("server_events", payload.channel());
    }

    @Test
    void nullChannelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new NekoScriptPayload(null, new NBTTagCompound()));
    }

    @Test
    void blankChannelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new NekoScriptPayload("   ", new NBTTagCompound()));
    }

    @Test
    void channelOver64CharsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new NekoScriptPayload("a".repeat(65), new NBTTagCompound()));
    }
}
