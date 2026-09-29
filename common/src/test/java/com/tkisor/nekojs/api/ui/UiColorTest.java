package com.tkisor.nekojs.api.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void argbBitsReadsUint32ScriptLiteralsAsBits() {
        // Defect D6: 0xFF...... literals are unsigned JS numbers ≥ 2³¹; the painter seams
        // receive them as Number and read the low 32 bits.
        assertEquals(-256, UiColor.argbBits(4294967040d), "0xFFFFFF00 (yellow)");
        assertEquals(-1, UiColor.argbBits(4294967295d), "0xFFFFFFFF (opaque white)");
        assertEquals(0x80FF0000, UiColor.argbBits(2164195328d), "0x80FF0000 (translucent red)");
    }

    @Test
    void argbBitsPassesInt32ValuesThroughUnchanged() {
        assertEquals(-256, UiColor.argbBits(-256), "negative int32 from bitwise color math");
        assertEquals(0, UiColor.argbBits(0));
        assertEquals(5, UiColor.argbBits(5));
        assertEquals(0x7FFFFFFF, UiColor.argbBits(Integer.MAX_VALUE));
    }

    @Test
    void argbBitsRejectsBadColorNumbersInEnglish() {
        IllegalArgumentException fromNull = assertThrows(IllegalArgumentException.class,
                () -> UiColor.argbBits(null));
        assertTrue(fromNull.getMessage().contains("color must not be null"), fromNull.getMessage());
        IllegalArgumentException fractional = assertThrows(IllegalArgumentException.class,
                () -> UiColor.argbBits(1.5d));
        assertTrue(fractional.getMessage().contains("int32/uint32 ARGB range"), fractional.getMessage());
        IllegalArgumentException oversized = assertThrows(IllegalArgumentException.class,
                () -> UiColor.argbBits(0x1FFFFFFFFL));
        assertTrue(oversized.getMessage().contains("int32/uint32 ARGB range"), oversized.getMessage());
        IllegalArgumentException undersized = assertThrows(IllegalArgumentException.class,
                () -> UiColor.argbBits(-2147483649d));
        assertTrue(undersized.getMessage().contains("int32/uint32 ARGB range"), undersized.getMessage());
    }
}
