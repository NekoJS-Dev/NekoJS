package com.tkisor.nekojs.api.ui;

import java.util.List;
import java.util.Optional;

/**
 * Resolved visual props for one UI node: canonical immutable values plus the
 * diagnostics produced while parsing. A prop that fails to parse is absent here
 * and reported in {@link #diagnostics()} — parsing never throws through to callers.
 *
 * @param color       text color, when present and valid
 * @param background  panel background color, when present and valid
 * @param borderColor border color, when present and valid
 * @param borderWidth border width in pixels, when present and valid
 * @param radius      corner radius in pixels, when present and valid
 * @param opacity     opacity in [0, 1], when present and valid
 * @param fontSize    font size in logical pixels, when present and valid
 * @param truncate    whether overlong text is cut with an ellipsis, when present and valid
 * @param image       image resource id, when present and valid
 * @param fit         image fit mode, when present and valid
 * @param crop        image source crop in texture pixels, when present and valid
 * @param icon        icon resource id, when present and valid
 * @param font        logical font id, when present and valid; availability does not prove provider loading
 * @param diagnostics diagnostics gathered while resolving, never null
 */
public record VisualSpec(
        UiColor color,
        UiColor background,
        UiColor borderColor,
        Integer borderWidth,
        Integer radius,
        Double opacity,
        Double fontSize,
        Boolean truncate,
        UiResourceId image,
        ImageFit fit,
        CropRect crop,
        UiResourceId icon,
        UiResourceId font,
        List<UiDiagnostic> diagnostics) {
    public VisualSpec {
        diagnostics = List.copyOf(diagnostics);
    }

    public VisualSpec(UiColor color, UiColor background, UiColor borderColor, Integer borderWidth,
                      Integer radius, Double opacity, Double fontSize, Boolean truncate,
                      UiResourceId image, ImageFit fit, CropRect crop, UiResourceId icon,
                      List<UiDiagnostic> diagnostics) {
        this(color, background, borderColor, borderWidth, radius, opacity, fontSize,
                truncate, image, fit, crop, icon, null, diagnostics);
    }

    /** How an image is fitted into its box. */
    public enum ImageFit { CONTAIN, COVER, STRETCH }

    /** Image source crop rectangle in texture pixels. */
    public record CropRect(int x, int y, int width, int height) {
        public CropRect {
            if (x < 0 || y < 0 || width <= 0 || height <= 0) {
                throw new IllegalArgumentException("CropRect requires non-negative origin and positive size");
            }
        }
    }

    public Optional<UiDiagnostic> firstDiagnostic() {
        return diagnostics.stream().findFirst();
    }
}
