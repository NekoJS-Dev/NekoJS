package com.tkisor.nekojs.api.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NekoIdTest {
    @Test
    void parsesQualifiedId() {
        assertEquals(new NekoId("minecraft", "stone"), NekoId.of("minecraft:stone"));
    }

    @Test
    void preservesLegacyDefaultNamespace() {
        assertEquals(new NekoId("nekojs", "script"), NekoId.of("script"));
    }

    @Test
    void rejectsBlankComponents() {
        assertThrows(IllegalArgumentException.class, () -> new NekoId("", "stone"));
        assertThrows(IllegalArgumentException.class, () -> new NekoId("minecraft", ""));
    }

    @Test
    void rendersCanonicalString() {
        assertEquals("minecraft:stone", NekoId.of("minecraft", "stone").asString());
    }
}
