package com.tkisor.nekojs.api.ui;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiControlAppearanceTest {
    private static final List<String> COLOR_KEYS = List.of(
            "background", "borderColor", "color", "placeholderColor", "selectionColor", "cursorColor",
            "trackColor", "thumbColor");
    private static final List<String> STATES = List.of("hover", "focused", "pressed", "disabled");

    @Test
    void nullAndEmptyMapsLeaveNativeColorsUnspecified() {
        for (Object raw : new Object[]{null, Map.of(), Map.of("hover", Map.of())}) {
            UiControlAppearance appearance = UiControlAppearance.parse(raw);
            assertTrue(appearance.resolve(false, false, false, false).isEmpty());
            assertTrue(appearance.resolve(true, true, true, true).isEmpty());
        }
    }

    @Test
    void parsesAllFlatKeysWithCanonicalColorForms() {
        Map<String, Object> raw = Map.of(
                "background", "#F00",
                "borderColor", "#00ff7F",
                "color", "#80FF0000",
                "placeholderColor", " Fuchsia ",
                "selectionColor", "transparent",
                "cursorColor", 0x80FF0000L,
                "trackColor", -0x1000000,
                "thumbColor", 4294967295d);
        Map<String, UiColor> colors = UiControlAppearance.parse(raw).resolve(false, false, false, false);
        assertEquals(raw.keySet(), colors.keySet());
        assertEquals(0xFFFF0000, colors.get("background").argb());
        assertEquals(0xFF00FF7F, colors.get("borderColor").argb());
        assertEquals(0x80FF0000, colors.get("color").argb());
        assertEquals(0xFFFF00FF, colors.get("placeholderColor").argb());
        assertEquals(0, colors.get("selectionColor").argb());
        assertEquals(0x80FF0000, colors.get("cursorColor").argb());
        assertEquals(0xFF000000, colors.get("trackColor").argb());
        assertEquals(0xFFFFFFFF, colors.get("thumbColor").argb());
    }

    @Test
    void everyStateAcceptsEveryFlatColorKey() {
        Map<String, Object> colors = new LinkedHashMap<>();
        for (String key : COLOR_KEYS) colors.put(key, "blue");
        for (int state = 0; state < STATES.size(); state++) {
            UiControlAppearance appearance = UiControlAppearance.parse(Map.of(STATES.get(state), colors));
            Map<String, UiColor> resolved = appearance.resolve(state == 0, state == 1, state == 2, state == 3);
            assertEquals(colors.keySet(), resolved.keySet());
            for (UiColor color : resolved.values()) assertEquals(0xFF0000FF, color.argb());
            assertTrue(appearance.resolve(false, false, false, false).isEmpty());
        }
    }

    @Test
    void cascadesInOrderForAllSixteenStateCombinations() {
        Map<String, Object> raw = Map.of(
                "color", 1,
                "background", "black",
                "hover", Map.of("color", 2, "borderColor", "red"),
                "focused", Map.of("color", 3, "cursorColor", "lime"),
                "pressed", Map.of("color", 4, "thumbColor", "blue"),
                "disabled", Map.of("color", 5, "trackColor", "white"));
        UiControlAppearance appearance = UiControlAppearance.parse(raw);
        for (int state = 0; state < 16; state++) {
            boolean hovered = (state & 1) != 0;
            boolean focused = (state & 2) != 0;
            boolean pressed = (state & 4) != 0;
            boolean disabled = (state & 8) != 0;
            Map<String, UiColor> colors = appearance.resolve(hovered, focused, pressed, disabled);
            int expected = disabled ? 5 : pressed ? 4 : focused ? 3 : hovered ? 2 : 1;
            assertEquals(expected, colors.get("color").argb(), "state " + state);
            assertEquals(0xFF000000, colors.get("background").argb());
            assertEquals(hovered, colors.containsKey("borderColor"));
            assertEquals(focused, colors.containsKey("cursorColor"));
            assertEquals(pressed, colors.containsKey("thumbColor"));
            assertEquals(disabled, colors.containsKey("trackColor"));
        }
    }

    @Test
    void snapshotsBaseStateMapsAndMutableNumbers() {
        AtomicInteger number = new AtomicInteger(7);
        Map<String, Object> hover = new HashMap<>(Map.of("color", "blue"));
        Map<String, Object> raw = new HashMap<>(Map.of("background", number, "hover", hover));
        UiControlAppearance appearance = UiControlAppearance.parse(raw);
        Map<String, UiColor> hovered = appearance.resolve(true, false, false, false);
        number.set(99);
        hover.put("color", "red");
        hover.put("borderColor", "white");
        raw.clear();
        assertEquals(7, hovered.get("background").argb());
        assertEquals(0xFF0000FF, hovered.get("color").argb());
        assertEquals(2, hovered.size());
        assertEquals(7, appearance.resolve(false, false, false, false).get("background").argb());
        assertEquals(0xFF0000FF, appearance.resolve(true, true, true, true).get("color").argb());
    }

    @Test
    void resolvedSnapshotsAreImmutableAndIndependent() {
        UiControlAppearance appearance = UiControlAppearance.parse(Map.of(
                "color", "red", "hover", Map.of("color", "blue")));
        Map<String, UiColor> base = appearance.resolve(false, false, false, false);
        Map<String, UiColor> hovered = appearance.resolve(true, false, false, false);
        assertThrows(UnsupportedOperationException.class, () -> base.put("color", hovered.get("color")));
        assertThrows(UnsupportedOperationException.class, hovered::clear);
        assertThrows(UnsupportedOperationException.class, () -> hovered.entrySet().iterator().next().setValue(base.get("color")));
        assertEquals(0xFFFF0000, base.get("color").argb());
        assertEquals(0xFF0000FF, hovered.get("color").argb());
    }

    @Test
    void rejectsWrongRootShapesAndUnknownOrNonStringKeys() {
        for (Object raw : List.of("red", 1, true, List.of(), new Object[]{"color", "red"})) {
            assertInvalid(raw);
        }
        assertInvalid(Map.of("backgroundColor", "red"));
        assertInvalid(Map.of("hovered", Map.of("color", "red")));
        assertInvalid(Map.of("Color", "red"));
        assertInvalid(Map.of(1, "red"));
        Map<Object, Object> nullKey = new HashMap<>();
        nullKey.put(null, "red");
        assertInvalid(nullKey);
    }

    @Test
    void rejectsNullValuesInvalidColorsAndNestedColorShapesEverywhere() {
        Object[] invalidColors = {null, true, "", "not-a-color", "rgb(1, 2, 3)", "#12345", "#GGGGGG",
                "00ff00", 1.5d, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                -2147483649d, 0x100000000L, List.of("red"), Map.of("color", "red"),
                UiColor.parse("red").orElseThrow()};
        for (String key : COLOR_KEYS) {
            for (Object color : invalidColors) {
                Map<String, Object> colors = new HashMap<>();
                colors.put(key, color);
                assertInvalid(colors);
                for (String state : STATES) assertInvalid(Map.of(state, colors));
            }
        }
    }

    @Test
    void rejectsWrongStateShapesUnknownStatePropertiesAndNestedStates() {
        Object[] invalidStates = {null, "red", 1, true, List.of(), UiColor.parse("red").orElseThrow()};
        for (String state : STATES) {
            for (Object value : invalidStates) {
                Map<String, Object> raw = new HashMap<>();
                raw.put(state, value);
                assertInvalid(raw);
            }
            assertInvalid(Map.of(state, Map.of("opacity", 0.5)));
            assertInvalid(Map.of(state, Map.of(1, "red")));
            Map<Object, Object> nullKey = new HashMap<>();
            nullKey.put(null, "red");
            assertInvalid(Map.of(state, nullKey));
            for (String nested : STATES) assertInvalid(Map.of(state, Map.of(nested, Map.of("color", "red"))));
        }
    }

    @Test
    void invalidOverrideReportsItsColorPath() {
        IllegalArgumentException failure = assertInvalid(Map.of("focused", Map.of("cursorColor", false)));
        assertTrue(failure.getMessage().contains("focused.cursorColor"), failure.getMessage());
    }

    private static IllegalArgumentException assertInvalid(Object raw) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> UiControlAppearance.parse(raw));
        assertTrue(failure.getMessage().startsWith("[NEKO-7008] appearance invalid: "), failure.getMessage());
        return failure;
    }
}
