package com.tkisor.nekojs.api.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiColorTest {
    @Test
    void parsesShortHexWithImpliedAlpha() {
        assertEquals(0xFFFF0000, UiColor.parseString("#F00").orElseThrow().argb());
        assertEquals(0xFF00FF00, UiColor.parseString("#0F0").orElseThrow().argb());
    }

    @Test
    void parsesRgbHexCaseInsensitively() {
        assertEquals(0xFF00FF7F, UiColor.parseString("#00ff7F").orElseThrow().argb());
    }

    @Test
    void parsesArgbHexWithExplicitAlpha() {
        assertEquals(0x80FF0000L, UiColor.parseString("#80FF0000").orElseThrow().argb() & 0xFFFFFFFFL);
    }

    @Test
    void parsesNamedColors() {
        assertEquals(0xFF0000FF, UiColor.parseString("blue").orElseThrow().argb());
        assertEquals(0xFFFF00FF, UiColor.parseString("Fuchsia").orElseThrow().argb());
        assertEquals(0x00000000, UiColor.parseString("transparent").orElseThrow().argb());
    }

    @Test
    void parsesArgbNumbers() {
        assertEquals(0x80FF0000, UiColor.parse(0x80FF0000L).orElseThrow().argb());
        assertEquals(0xFF000000, UiColor.parse(-0x1000000).orElseThrow().argb());
    }

    @Test
    void rejectsNonControlledForms() {
        assertTrue(UiColor.parseString("rgb(1, 2, 3)").isEmpty());
        assertTrue(UiColor.parseString("#12345").isEmpty());
        assertTrue(UiColor.parseString("#GGGGGG").isEmpty());
        assertTrue(UiColor.parseString("not-a-color").isEmpty());
        assertTrue(UiColor.parseString("00ff00").isEmpty());
        assertTrue(UiColor.parseString(null).isEmpty());
        assertTrue(UiColor.parse(1.5).isEmpty());
        assertTrue(UiColor.parse(0x100000000L).isEmpty());
        assertTrue(UiColor.parse(Boolean.TRUE).isEmpty());
    }

    @Test
    void negativeInt32IsReadAsArgbBits() {
        // Script bitwise color math yields negative int32s; the bits are the color.
        assertEquals(0xFFFFFFFF, UiColor.parse(-1).orElseThrow().argb());
        assertEquals(0xFF000000, UiColor.parse(-0x1000000).orElseThrow().argb());
        assertEquals(0x80FF0000, UiColor.parse(0x80FF0000L).orElseThrow().argb());
    }
}
