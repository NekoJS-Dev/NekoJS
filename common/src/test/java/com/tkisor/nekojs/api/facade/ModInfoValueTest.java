package com.tkisor.nekojs.api.facade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModInfoValueTest {
    @Test
    void preservesImmutableModMetadata() {
        ModInfoValue value = new ModInfoValue("nekojs", "NekoJS", "1.1.0");
        assertEquals("nekojs", value.id());
        assertEquals("NekoJS", value.name());
        assertEquals("1.1.0", value.version());
    }

    @Test
    void rejectsBlankMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new ModInfoValue("", "NekoJS", "1.1.0"));
        assertThrows(IllegalArgumentException.class, () -> new ModInfoValue("nekojs", "", "1.1.0"));
        assertThrows(IllegalArgumentException.class, () -> new ModInfoValue("nekojs", "NekoJS", ""));
    }
}
