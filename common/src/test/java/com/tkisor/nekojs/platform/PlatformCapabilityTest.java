package com.tkisor.nekojs.platform;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlatformCapabilityTest {

    @Test
    void capabilitySetIsImmutable() {
        Set<PlatformCapability> caps = Set.of(PlatformCapability.TAGS, PlatformCapability.NETWORK_CUSTOM_CHANNEL);
        assertThrows(UnsupportedOperationException.class, () -> caps.add(PlatformCapability.DATA_GENERATION));
    }

    @Test
    void enumValuesAreStable() {
        EnumSet<PlatformCapability> all = EnumSet.allOf(PlatformCapability.class);
        assertTrue(all.contains(PlatformCapability.TAGS));
        assertTrue(all.contains(PlatformCapability.RECIPE_HOT_RELOAD));
        assertTrue(all.contains(PlatformCapability.NETWORK_CUSTOM_CHANNEL));
        assertTrue(all.size() >= 20, "expected at least 20 capabilities, got " + all.size());
    }
}
