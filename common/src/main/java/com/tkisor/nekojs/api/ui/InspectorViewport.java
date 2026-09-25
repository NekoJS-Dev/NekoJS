package com.tkisor.nekojs.api.ui;

/**
 * Logical viewport facts for one {@link InspectorSnapshot} frame. Minimal own contract
 * over ticket 43's six-tier profile system: the values derive from the common JSX
 * runtime's public {@code resolveViewport}, so a derivation change there alters only
 * where these values come from, not the inspector fields.
 *
 * @param width         logical viewport width in pixels
 * @param height        logical viewport height in pixels
 * @param safeArea      inset each screen edge requires UI content to avoid
 * @param contentWidth  viewport width minus horizontal safe area
 * @param contentHeight viewport height minus vertical safe area
 * @param profile       selected viewport profile, 1-6
 * @param guiScale      client GUI scale when the host reports one, else null
 * @param designScale   design-to-logical coordinate scale, 1 when unused
 */
public record InspectorViewport(
        int width,
        int height,
        SafeArea safeArea,
        int contentWidth,
        int contentHeight,
        int profile,
        Integer guiScale,
        double designScale) {

    /** Inset from each screen edge that UI content must avoid, in pixels. */
    public record SafeArea(int top, int right, int bottom, int left) {
    }
}
