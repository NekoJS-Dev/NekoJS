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
    void strictResourceConstructionPreservesContentAndDefaultNamespace() {
        assertEquals(NekoId.of("nekojs:stone"), NekoId.resource("stone"));
        assertEquals(NekoId.of("example", "machines/press._-09"),
                NekoId.resource("example", "machines/press._-09"));
        assertEquals(NekoId.resource("minecraft:stone"), NekoId.resource("minecraft", "stone"));
    }

    @Test
    void strictResourcesRejectInvalidInputWithoutChangingLegacyConstruction() {
        for (String value : new String[]{null, "", ":stone", "minecraft:", "a:b:c", "Minecraft:stone",
                "a:Stone", " a:b", "a:b ", "a:b\\c", "a:b?", "a:é"}) {
            var failure = assertThrows(IllegalArgumentException.class, () -> NekoId.resource(value));
            org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().startsWith("[NEKO-4056]"));
        }
        assertThrows(IllegalArgumentException.class, () -> NekoId.resource(null, "stone"));
        assertThrows(IllegalArgumentException.class, () -> NekoId.resource("a", null));
        assertEquals("Legacy:Name:With:Colons", NekoId.of("Legacy:Name:With:Colons").asString());
        assertEquals(" a : b ", NekoId.of(" a ", " b ").asString());
    }

    @Test
    void rendersCanonicalString() {
        assertEquals("minecraft:stone", NekoId.of("minecraft", "stone").asString());
    }
}
