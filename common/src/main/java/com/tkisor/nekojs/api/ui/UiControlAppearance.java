package com.tkisor.nekojs.api.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable control colors with base, hover, focused, pressed, and disabled overrides. */
public final class UiControlAppearance {
    private static final Set<String> COLOR_KEYS = Set.of(
            "background", "borderColor", "color", "placeholderColor", "selectionColor", "cursorColor",
            "trackColor", "thumbColor");
    private static final Set<String> STATE_KEYS = Set.of("hover", "focused", "pressed", "disabled");
    private static final UiControlAppearance EMPTY = new UiControlAppearance(Map.of(), Map.of());

    private final List<Map<String, UiColor>> resolvedStates;

    private UiControlAppearance(Map<String, UiColor> base, Map<String, Map<String, UiColor>> overrides) {
        // Resolve the bounded state combinations once, not on the control's render path.
        List<Map<String, UiColor>> resolved = new ArrayList<>(16);
        for (int state = 0; state < 16; state++) {
            Map<String, UiColor> colors = new LinkedHashMap<>(base);
            if ((state & 1) != 0) colors.putAll(overrides.getOrDefault("hover", Map.of()));
            if ((state & 2) != 0) colors.putAll(overrides.getOrDefault("focused", Map.of()));
            if ((state & 4) != 0) colors.putAll(overrides.getOrDefault("pressed", Map.of()));
            if ((state & 8) != 0) colors.putAll(overrides.getOrDefault("disabled", Map.of()));
            resolved.add(Map.copyOf(colors));
        }
        resolvedStates = List.copyOf(resolved);
    }

    /**
     * Snapshots an appearance map; null means no overrides. Colors use exactly
     * the string and number forms accepted by {@link UiColor#parse(Object)}.
     * State maps accept only flat color keys, never further states.
     *
     * @throws IllegalArgumentException when a key, shape, or color is invalid
     */
    public static UiControlAppearance parse(Object raw) {
        if (raw == null) return EMPTY;
        if (!(raw instanceof Map<?, ?> map)) {
            throw invalid("expected a map");
        }
        Map<String, UiColor> base = new LinkedHashMap<>();
        Map<String, Map<String, UiColor>> overrides = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw invalid("keys must be strings");
            }
            if (COLOR_KEYS.contains(key)) {
                base.put(key, parseColor(entry.getValue(), key));
            } else if (STATE_KEYS.contains(key)) {
                overrides.put(key, parseState(entry.getValue(), key));
            } else {
                throw invalid("unknown key '" + key + "'");
            }
        }
        return base.isEmpty() && overrides.isEmpty() ? EMPTY : new UiControlAppearance(base, overrides);
    }

    /**
     * Returns an immutable color snapshot in base → hover → focused → pressed → disabled order.
     * Missing keys stay absent so the native control can retain its default colors.
     */
    public Map<String, UiColor> resolve(boolean hovered, boolean focused, boolean pressed, boolean disabled) {
        int state = (hovered ? 1 : 0) | (focused ? 2 : 0) | (pressed ? 4 : 0) | (disabled ? 8 : 0);
        return resolvedStates.get(state);
    }

    private static Map<String, UiColor> parseState(Object raw, String state) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw invalid("'" + state + "' must be a map");
        }
        Map<String, UiColor> colors = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !COLOR_KEYS.contains(key)) {
                throw invalid("'" + state + "' accepts only flat color keys");
            }
            colors.put(key, parseColor(entry.getValue(), state + "." + key));
        }
        return Map.copyOf(colors);
    }

    private static UiColor parseColor(Object raw, String key) {
        return UiColor.parse(raw).orElseThrow(() -> invalid("'" + key + "' must be a valid color string or number"));
    }

    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("[NEKO-7008] appearance invalid: " + detail);
    }
}
