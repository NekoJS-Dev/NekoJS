package com.tkisor.nekojs.api.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Parses script-side visual props ({@code color}, {@code background},
 * {@code borderColor}, {@code borderWidth}, {@code radius}, {@code opacity},
 * {@code fontSize}, {@code truncate}, {@code resource}, {@code fit}, {@code crop}, {@code icon}) into a
 * {@link VisualSpec}. Invalid values are skipped and reported as locatable
 * diagnostics instead of throwing, so one bad prop cannot take down a whole tree.
 *
 * <p>Prop names match the common JSX runtime's primitive props (the runtime freezes
 * them onto host nodes; the host paints from the resolved spec). Values arrive as
 * plain Java scalars/maps/lists as produced by the host adapter boundary.
 */
public final class VisualStyleResolver {
    private VisualStyleResolver() {
    }

    /**
     * Resolves visual props without resource existence checks.
     *
     * @param props    raw node props; null values are treated as absent
     * @param location position stamped into diagnostics
     * @return resolved spec with diagnostics, never null
     */
    public static VisualSpec resolve(Map<String, Object> props, UiDiagnostic.Location location) {
        return resolve(props, location, null);
    }

    /**
     * Resolves visual props; when {@code resources} is given, image/icon resource ids
     * are additionally resolved and missing or invalid ids become diagnostics (the
     * id itself still lands in the spec so the host can paint a placeholder).
     *
     * @param props     raw node props; null values are treated as absent
     * @param location  position stamped into diagnostics
     * @param resources optional resolver for resource existence checks
     * @return resolved spec with diagnostics, never null
     */
    public static VisualSpec resolve(Map<String, Object> props, UiDiagnostic.Location location,
            UiResourceResolver resources) {
        List<UiDiagnostic> diagnostics = new ArrayList<>();
        UiColor color = parseColor(props.get("color"), "color", location, diagnostics);
        UiColor background = parseColor(props.get("background"), "background", location, diagnostics);
        UiColor borderColor = parseColor(props.get("borderColor"), "borderColor", location, diagnostics);
        Integer borderWidth = parseNonNegativeInt(props.get("borderWidth"), "borderWidth", location, diagnostics);
        Integer radius = parseNonNegativeInt(props.get("radius"), "radius", location, diagnostics);
        Double opacity = parseOpacity(props.get("opacity"), location, diagnostics);
        Double fontSize = parseFontSize(props.get("fontSize"), location, diagnostics);
        Boolean truncate = parseBoolean(props.get("truncate"), "truncate", location, diagnostics);
        VisualSpec.ImageFit fit = parseFit(props.get("fit"), location, diagnostics);
        VisualSpec.CropRect crop = parseCrop(props.get("crop"), location, diagnostics);
        UiResourceId image = parseResourceId(props.get("resource"), location, diagnostics);
        UiResourceId icon = parseResourceId(props.get("icon"), location, diagnostics);
        if (resources != null) {
            checkResource(image, resources::resolveTexture, "image", location, diagnostics);
            checkResource(icon, resources::resolveTexture, "icon", location, diagnostics);
        }
        return new VisualSpec(color, background, borderColor, borderWidth, radius, opacity, fontSize,
                truncate, image, fit, crop, icon, diagnostics);
    }

    private static UiColor parseColor(Object value, String prop, UiDiagnostic.Location location,
            List<UiDiagnostic> diagnostics) {
        if (value == null) return null;
        Optional<UiColor> parsed = UiColor.parse(value);
        if (parsed.isEmpty()) {
            report(diagnostics, UiErrorCodes.INVALID_COLOR,
                    "invalid UI color for '" + prop + "': " + describe(value), location, null);
        }
        return parsed.orElse(null);
    }

    private static Integer parseNonNegativeInt(Object value, String prop, UiDiagnostic.Location location,
            List<UiDiagnostic> diagnostics) {
        if (value == null) return null;
        if (value instanceof Number number && number.doubleValue() >= 0 && number.doubleValue() == Math.floor(number.doubleValue())
                && number.doubleValue() <= Integer.MAX_VALUE) {
            return number.intValue();
        }
        report(diagnostics, UiErrorCodes.INVALID_VISUAL_VALUE,
                "invalid UI value for '" + prop + "' (expected non-negative integer): " + describe(value), location, null);
        return null;
    }

    private static Double parseOpacity(Object value, UiDiagnostic.Location location,
            List<UiDiagnostic> diagnostics) {
        if (value == null) return null;
        if (value instanceof Number number && number.doubleValue() >= 0.0 && number.doubleValue() <= 1.0) {
            return number.doubleValue();
        }
        report(diagnostics, UiErrorCodes.INVALID_VISUAL_VALUE,
                "invalid UI value for 'opacity' (expected number in [0, 1]): " + describe(value), location, null);
        return null;
    }

    private static Double parseFontSize(Object value, UiDiagnostic.Location location,
            List<UiDiagnostic> diagnostics) {
        if (value == null) return null;
        if (value instanceof Number number && number.doubleValue() > 0.0 && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        report(diagnostics, UiErrorCodes.INVALID_VISUAL_VALUE,
                "invalid UI value for 'fontSize' (expected positive number): " + describe(value), location, null);
        return null;
    }

    private static Boolean parseBoolean(Object value, String prop, UiDiagnostic.Location location,
            List<UiDiagnostic> diagnostics) {
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool;
        report(diagnostics, UiErrorCodes.INVALID_VISUAL_VALUE,
                "invalid UI value for '" + prop + "' (expected boolean): " + describe(value), location, null);
        return null;
    }

    private static VisualSpec.ImageFit parseFit(Object value, UiDiagnostic.Location location,
            List<UiDiagnostic> diagnostics) {
        if (value == null) return null;
        if (value instanceof String string) {
            try {
                return VisualSpec.ImageFit.valueOf(string.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // reported below with the raw value
            }
        }
        report(diagnostics, UiErrorCodes.INVALID_VISUAL_VALUE,
                "invalid UI value for 'fit' (expected 'contain', 'cover', or 'stretch'): " + describe(value), location, null);
        return null;
    }

    private static VisualSpec.CropRect parseCrop(Object value, UiDiagnostic.Location location,
            List<UiDiagnostic> diagnostics) {
        if (value == null) return null;
        Integer[] parts;
        if (value instanceof List<?> list && list.size() == 4) {
            parts = list.stream().map(VisualStyleResolver::asInt).toArray(Integer[]::new);
        } else if (value instanceof Map<?, ?> map) {
            parts = new Integer[]{
                    asInt(map.get("x")), asInt(map.get("y")), asInt(map.get("width")), asInt(map.get("height"))};
        } else {
            parts = null;
        }
        if (parts != null && parts[0] != null && parts[1] != null && parts[2] != null && parts[3] != null
                && parts[0] >= 0 && parts[1] >= 0 && parts[2] > 0 && parts[3] > 0) {
            return new VisualSpec.CropRect(parts[0], parts[1], parts[2], parts[3]);
        }
        report(diagnostics, UiErrorCodes.INVALID_RESOURCE_SIZE,
                "invalid UI value for 'crop' (expected x, y, width, height with positive size): " + describe(value),
                location, null);
        return null;
    }

    private static UiResourceId parseResourceId(Object value, UiDiagnostic.Location location,
            List<UiDiagnostic> diagnostics) {
        if (value == null) return null;
        if (value instanceof String string) {
            Optional<UiResourceId> parsed = UiResourceId.parse(string);
            if (parsed.isPresent()) return parsed.get();
        }
        report(diagnostics, UiErrorCodes.INVALID_RESOURCE_ID,
                "invalid UI resource id: " + describe(value), location,
                value instanceof String string ? string : null);
        return null;
    }

    private static void checkResource(UiResourceId id, java.util.function.Function<String, ResourceStatus> resolve,
            String prop, UiDiagnostic.Location location, List<UiDiagnostic> diagnostics) {
        if (id == null) return;
        ResourceStatus status = resolve.apply(id.toString());
        if (status.state() == ResourceStatus.State.RESOLVED) return;
        report(diagnostics, status.code(),
                "UI " + prop + " resource " + status.state().name().toLowerCase(Locale.ROOT) + ": " + id,
                location, id.toString());
    }

    /** Adds one diagnostic stamped with the resolving location; {@code resourceId} is null for non-resource failures. */
    private static void report(List<UiDiagnostic> diagnostics, String code, String message,
            UiDiagnostic.Location location, String resourceId) {
        diagnostics.add(new UiDiagnostic(code, message,
                location.rootId(), location.nodeType(), location.nodeKey(), resourceId, location.generation()));
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number number && number.doubleValue() == Math.floor(number.doubleValue())
                && number.doubleValue() >= Integer.MIN_VALUE && number.doubleValue() <= Integer.MAX_VALUE) {
            return number.intValue();
        }
        return null;
    }

    /** Short raw-value description for messages; bounded so hostile props cannot flood logs. */
    private static String describe(Object value) {
        String text = String.valueOf(value);
        return text.length() > 60 ? text.substring(0, 60) + "..." : text;
    }
}
