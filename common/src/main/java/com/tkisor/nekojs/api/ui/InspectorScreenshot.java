package com.tkisor.nekojs.api.ui;

/**
 * Auxiliary pixel-evidence metadata for an {@link InspectorSnapshot}: where a frame
 * capture came from and how large it is. The inspector contract deliberately carries no
 * pixels, buffers, or screenshot objects — measurement and behavior assertions are the
 * primary evidence, and this record only names the capture that accompanies them.
 *
 * @param source capture origin, e.g. {@code fake-frame} or {@code neoforge-viewport-meta}
 * @param width  capture width in pixels
 * @param height capture height in pixels
 */
public record InspectorScreenshot(String source, int width, int height) {
}
